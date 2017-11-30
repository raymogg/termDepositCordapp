package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.TermDeposit
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.finance.USD
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.utils.sumCashBy
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/** Flow for redeeming a TD.
 */


object RedeemTD {

    @InitiatingFlow
    @StartableByRPC
    @CordaSerializable
    class RedemptionInitiator(val startDate: LocalDateTime, val endDate: LocalDateTime, val interestPercent: Float,
    val issuingInstitue: Party, val depositAmount: Amount<Currency>) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            //Note - This is a improved version of flow compared to the Issue and Activate TD Flows

            //STEP 1: Retrieve the TD to Redeem and begin flow with other party
            val TermDeposits = subFlow(TDRetreivalFlow(startDate,endDate, issuingInstitue, interestPercent, depositAmount, TermDeposit.internalState.exited))
            val flowSession = initiateFlow(issuingInstitue)

            //STEP 2: Send the term deposit to the other party
            flowSession.send(TermDeposits.first())

            //Sign transaction when sent back from other party
            val signTransactionFlow = object : SignTransactionFlow(flowSession, SignTransactionFlow.tracker()) {
                override fun checkTransaction(stx: SignedTransaction)  {
                    val cashProvided = stx.tx.outputStates.sumCashBy(serviceHub.myInfo.legalIdentities.first()).quantity
                    val cashNeeded = (TermDeposits.first().state.data.depositAmount.quantity * (100+TermDeposits.first().state.data.interestPercent)/100).toLong()
                    requireThat {
                        println(cashProvided)
                        println(cashNeeded)
                        "Cash amount not correct" using (cashProvided == cashNeeded)
                        //TODO Validate
                    }
                }
            }

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
                //TODO: Required validation
                it
            }

            //STEP 4: Build the Ttransaction
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val builder = TransactionBuilder(notary)
            val builder2 = TermDeposit().genereateRedeem(builder, TermDeposit)
            //Add our required cash
            val (tx, cashKeys) = Cash.generateSpend(serviceHub, builder2, Amount((TermDeposit.state.data.depositAmount.quantity * (100+TermDeposit.state.data.interestPercent)/100).toLong(), USD),
                    TermDeposit.state.data.owner)

            //STEP 5: Get the client to sign the transaction
            val partSignedTxn = serviceHub.signInitialTransaction(tx, cashKeys)
            val otherPartySig = subFlow(CollectSignaturesFlow(partSignedTxn, listOf(flow), CollectSignaturesFlow.tracker()))

            //STEP 6: Merge all signatures and commit this to the ledger
            val twiceSignedTx = partSignedTxn.plus(otherPartySig.sigs) //This is different to tutorial so hopefully works
            return subFlow(FinalityFlow(twiceSignedTx))
        }
    }
}