package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.KYC
import com.termDeposits.contract.TermDeposit
import com.termDeposits.contract.TermDepositOffer
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.finance.USD
import net.corda.finance.contracts.asset.Cash
import java.security.PublicKey
import java.time.LocalDateTime
import java.time.Period
import java.util.*
import javax.swing.plaf.nimbus.State

/** Flow for rolling over a TD. Takes in TermDeposit, calculates the final amount that should have been paid back to the client
 *
 * rolloverTerms is an object containing a new start date, new end date and a withInterest boolean. This with interest boolean
 * value simply identifies whether or not the interest should be rolled over into the new term deposit or paid out
 *
 * Two options - rollover w/ interest (withInterest set to true), regular rollover (withInterest set to false)
 * If rollover w/ interest - new TD created w/ starting amount of interest+old princinple, no cash paid out
 * If regular - new TD created w/ new start and end date, interest cash paid back to the client.
 *

 */

@CordaSerializable
object RolloverTD {

    @CordaSerializable
    @InitiatingFlow
    @StartableByRPC
    open class RolloverInitiator(val dateData: TermDeposit.DateData,val interestPercent: Float, val issuingInstitue: Party,
                            val depositAmount: Amount<Currency>, val rolloverTerms: TermDeposit.RolloverTerms, val kycNameData: KYC.KYCNameData) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            println("Rollover TD")
            //STEP 1: Gather KYC Data and TD. Send these to other party with rollover instructions
            val flowSession = initiateFlow(issuingInstitue)
            val clientID = subFlow(KYCRetrievalFlow(kycNameData.firstName, kycNameData.lastName, kycNameData.accountNum)).first().state.data.linearId
            val termDeposit = subFlow(TDRetreivalFlows.TDRetreivalFlow(dateData, issuingInstitue, interestPercent, depositAmount, TermDeposit.internalState.active, clientID))
            val tdOffer = subFlow(OfferRetrievalFlow(rolloverTerms.offeringInstitue, rolloverTerms.interestPercent, rolloverTerms.duration))
            flowSession.send(listOf(termDeposit.first(), rolloverTerms.withInterest, tdOffer.first()))

            //STEP 5: Sign the transaction and return to the other party
            val signTransactionFlow = object : SignTransactionFlow(flowSession, SignTransactionFlow.tracker()) {
                override fun checkTransaction(stx: SignedTransaction)  {
                    requireThat {
                        //TODO Validate
                    }

                }
            }
            //Sign and then wait for transaction to be commited to the ledger
            val stx = subFlow(signTransactionFlow)
            return waitForLedgerCommit(stx.id)
        }
    }

    @CordaSerializable
    @InitiatedBy(RolloverInitiator::class)
    @Suspendable
    open class RolloverAcceptor(val flowSession: FlowSession): FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            //STEP 2: Receieve the term deposit and rollover instruction
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val params = flowSession.receive<List<*>>().unwrap { it }
            val termDeposit = params[0] as StateAndRef<TermDeposit.State>
            val withInterest = params[1] as Boolean
            val tdOffer = params[2] as StateAndRef<TermDepositOffer.State>



            //STEP 3: Create the txn
            val builder = TransactionBuilder(notary)
            val toSignTx: TransactionBuilder
            val keys: List<PublicKey>
            //Add our required cash
            val amountOfCash: Amount<Currency>
            val ratioToPay: Float
            if (termDeposit.state.data.endDate.isAfter(LocalDateTime.now()) && termDeposit.state.data.earlyTerms.earlyPenalty == true) {
                //The user is exiting early, we allow this but penalize them
                val monthsDiff = Period.between(LocalDateTime.now().toLocalDate(),termDeposit.state.data.endDate.toLocalDate()).months
                val yearsToMonthsDiff = Period.between(LocalDateTime.now().toLocalDate(),termDeposit.state.data.endDate.toLocalDate()).years * 12
                val monthsLeft = monthsDiff + yearsToMonthsDiff
                val monthsDiff2 = Period.between(termDeposit.state.data.startDate.toLocalDate(),termDeposit.state.data.endDate.toLocalDate()).months
                val yearsToMonthsDiff2 = Period.between(termDeposit.state.data.startDate.toLocalDate(),termDeposit.state.data.endDate.toLocalDate()).years * 12
                val totalMonths = monthsDiff2 + yearsToMonthsDiff2
                //Ratio is (monthsLeft - depositDuration/depositDuration) eg 1 month left on a 12 month deposit means user gets 12-1/12 of the interest (11/12 * interest)
                ratioToPay = ((totalMonths.toFloat()-monthsLeft.toFloat())/totalMonths.toFloat())
                println("Ratio to pay $ratioToPay")
                //amountOfCash = Amount((termDeposit.state.data.depositAmount.quantity * (100+(termDeposit.state.data.interestPercent*ratioToPay))/100).toLong(), USD)
                amountOfCash = Amount((termDeposit.state.data.depositAmount.quantity/100 * termDeposit.state.data.interestPercent*ratioToPay).toLong(), USD)
            } else {
//                val (tx, cashKeys) = Cash.generateSpend(serviceHub, builder2, Amount((TermDeposit.state.data.depositAmount.quantity * (100+TermDeposit.state.data.interestPercent)/100).toLong(), USD),
//                        flow.counterparty)
                //amountOfCash = Amount((termDeposit.state.data.depositAmount.quantity * (100+termDeposit.state.data.interestPercent)/100).toLong(), USD)
                amountOfCash = Amount((termDeposit.state.data.depositAmount.quantity/100 * termDeposit.state.data.interestPercent).toLong(), USD)
                ratioToPay = 1.0f
            }
            if (withInterest) {
                //Setup the txn with interest being reinvested
                val tx = TermDeposit().generateRollover(builder, termDeposit, notary, tdOffer, true, ratioToPay)
                toSignTx = tx
                keys = listOf(serviceHub.myInfo.legalIdentities.first().owningKey)
            } else {
                //Setup the txn with interest being returned to sender
                val tx = TermDeposit().generateRollover(builder, termDeposit, notary, tdOffer, false, ratioToPay)
                //Return the interest earned
                //Amount((termDeposit.state.data.depositAmount.quantity/100 * termDeposit.state.data.interestPercent).toLong()
                val (ptx, cashKeys) = Cash.generateSpend(serviceHub, tx, amountOfCash,
                        termDeposit.state.data.owner)
                toSignTx = ptx
                keys = cashKeys
            }

            //Add the TDStates and required command
            builder.addInputState(tdOffer)
            builder.addOutputState(tdOffer.state.copy())
            builder.addCommand(TermDepositOffer.Commands.Rollover(), tdOffer.state.data.institue.owningKey)

            //STEP 4: Sign the initial transaction and invoke collect sigs flow
            val stx = serviceHub.signInitialTransaction(toSignTx, keys)
            val otherPartySig = subFlow(CollectSignaturesFlow(stx, setOf(flowSession), CollectSignaturesFlow.tracker()))

            //STEP 6: Receieve back and commit to ledger
            val twiceSignedTx = stx.plus(otherPartySig.sigs)
            val td = toSignTx.outputStates().filterIsInstance<TransactionState<TermDeposit.State>>().first()
            println("Term Deposit Rollover ${td.data} ${td.data.depositAmount}")
            return subFlow(FinalityFlow(twiceSignedTx, setOf(flowSession.counterparty)))
        }
    }
}