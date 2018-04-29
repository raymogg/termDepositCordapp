package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.KYC
import com.termDeposits.contract.TermDeposit
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.finance.USD
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.utils.sumCashBy
import java.time.LocalDateTime
import java.time.Period
import java.util.*
import java.util.logging.Logger

/** Flow for redeeming a TD. This flow is invoked by a client/node that "owns" the term deposit (i.e have given money to the
 * issuing institue). After this flow the term deposits will be consumed.
 * The correct amount of money will be returned to the client.
 */


object RedeemTD {

    @InitiatingFlow
    @StartableByRPC
    @CordaSerializable
    class RedemptionInitiator(val dateData: TermDeposit.DateData, val interestPercent: Float,
    val issuingInstitue: Party, val depositAmount: Amount<Currency>, val kycNameData: KYC.KYCNameData) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {

            //STEP 1: Retrieve the TD to Redeem and begin flow with other party
            val clientID = subFlow(KYCRetrievalFlow(kycNameData.firstName, kycNameData.lastName, kycNameData.accountNum)).first().state.data.linearId
            val TermDeposits = subFlow(TDRetreivalFlows.TDRetreivalFlow(dateData, issuingInstitue, interestPercent, depositAmount, TermDeposit.internalState.active, clientID))
            val flowSession = initiateFlow(issuingInstitue)

            //STEP 2: Send the term deposit to the other party
            flowSession.send(TermDeposits.first())

            //STEP 6: Sync confidential identities and sign the transaction from the other party
            // Sync identities to ensure we know all of the identities involved in the transaction we're about to
            // be asked to sign
            subFlow(IdentitySyncFlow.Receive(flowSession))

            val signTransactionFlow = object : SignTransactionFlow(flowSession, SignTransactionFlow.tracker()) {
                override fun checkTransaction(stx: SignedTransaction)  {
                    val cashProvided = stx.tx.outputStates.sumCashBy(serviceHub.myInfo.legalIdentities.first()).quantity
                    val cashNeeded = (TermDeposits.first().state.data.depositAmount.quantity * (100+TermDeposits.first().state.data.interestPercent)/100).toLong()
                    requireThat {
                        //TODO: Factor in if we exited early
                        if (TermDeposits.first().state.data.endDate.isAfter(LocalDateTime.now()) && TermDeposits.first().state.data.earlyTerms.earlyPenalty == true) {
                            //Exited early
                            //"Must have been paid the correct amount of cash for this term deposit" using (cashProvided == cashNeeded)
                        } else {
                            "Must have been paid the correct amount of cash for this term deposit" using (cashProvided == cashNeeded)
                        }
                    }
                }
            }
            //Sign and wait for this transaction to be commited to the ledger
            val stx = subFlow(signTransactionFlow)
            return waitForLedgerCommit(stx.id)
        }
    }

    @CordaSerializable
    @InitiatedBy(RedemptionInitiator::class)
    class RedemptionAcceptor(val flow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            //STEP 3: Receive the TD from the client that is being redeemed
            val TermDeposit = flow.receive<StateAndRef<TermDeposit.State>>().unwrap {
                requireThat {
                    //TODO: any required validation
                    //it.state.data.endDate.isAfter(LocalDateTime.now()) add back in after testing phase
                }
                it
            }

            //STEP 4: Build the Transaction
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = TransactionBuilder(notary)
            val builder2 = TermDeposit().genereateRedeem(builder, TermDeposit)
            //Add our required cash
            val amountOfCash: Amount<Currency>
            if (TermDeposit.state.data.endDate.isAfter(LocalDateTime.now()) && TermDeposit.state.data.earlyTerms.earlyPenalty == true) {
                //The user is exiting early, we allow this but penalize them
                val monthsDiff = Period.between(LocalDateTime.now().toLocalDate(),TermDeposit.state.data.endDate.toLocalDate()).months
                val yearsToMonthsDiff = Period.between(LocalDateTime.now().toLocalDate(),TermDeposit.state.data.endDate.toLocalDate()).years * 12
                val monthsLeft = monthsDiff + yearsToMonthsDiff
                val monthsDiff2 = Period.between(TermDeposit.state.data.startDate.toLocalDate(),TermDeposit.state.data.endDate.toLocalDate()).months
                val yearsToMonthsDiff2 = Period.between(TermDeposit.state.data.startDate.toLocalDate(),TermDeposit.state.data.endDate.toLocalDate()).years * 12
                val totalMonths = monthsDiff2 + yearsToMonthsDiff2
                //Ratio is (monthsLeft - depositDuration/depositDuration) eg 1 month left on a 12 month deposit means user gets 12-1/12 of the interest (11/12 * interest)
                val ratioToPay = ((totalMonths.toFloat()-monthsLeft.toFloat())/totalMonths.toFloat())
                println("Ratio to pay $ratioToPay")
                amountOfCash = Amount((TermDeposit.state.data.depositAmount.quantity * (100+(TermDeposit.state.data.interestPercent*ratioToPay))/100).toLong(), USD)
            } else {
//                val (tx, cashKeys) = Cash.generateSpend(serviceHub, builder2, Amount((TermDeposit.state.data.depositAmount.quantity * (100+TermDeposit.state.data.interestPercent)/100).toLong(), USD),
//                        flow.counterparty)
                amountOfCash = Amount((TermDeposit.state.data.depositAmount.quantity * (100+TermDeposit.state.data.interestPercent)/100).toLong(), USD)
            }
            val (tx, cashKeys) = Cash.generateSpend(serviceHub, builder2, amountOfCash,
                    flow.counterparty)

            //STEP 5: Sign transaction and get the client to sign the transaction
            val partSignedTxn = serviceHub.signInitialTransaction(tx, cashKeys.plus(serviceHub.myInfo.legalIdentities.first().owningKey))
            // Sync up confidential identities in the transaction with our counterparty
            subFlow(IdentitySyncFlow.Send(flow, tx.toWireTransaction(serviceHub)))
            val otherPartySig = subFlow(CollectSignaturesFlow(partSignedTxn, listOf(flow), CollectSignaturesFlow.tracker()))

            //STEP 7: Merge all signatures and commit this to the ledger
            val twiceSignedTx = partSignedTxn.plus(otherPartySig.sigs) //This is different to tutorial so hopefully works
            println("Term Deposit Redeemed: ${TermDeposit.state.data.toString()}")
            return subFlow(FinalityFlow(twiceSignedTx, setOf(flow.counterparty)))
        }
    }
}