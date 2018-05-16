package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.KYC
import com.termDeposits.contract.TermDeposit
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.finance.contracts.asset.Cash
import java.util.*

/**
 * Flow for  TD Issuer to activate a TD with a client. This flow would be invoked after the issuer has recieved the transfer
 * of cash off ledger. This flow will change the term deposits internal state from pending to active indicating it is now
 * "live"
 */

@CordaSerializable
object ActivateTD {

    @CordaSerializable
    @StartableByRPC
    @InitiatingFlow
    open class Activator(val dateData: TermDeposit.DateData, val interestPercent: Float,
                         val issuingInstitue: Party, val client: Party, val depositAmount: Amount<Currency>, val kycData: KYC.KYCNameData) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        //STEP 1: Notify other party of activation
        val flow = initiateFlow(client)
        flow.send(listOf(dateData, interestPercent, issuingInstitue, client, depositAmount, kycData))

        //STEP 6: Sync Identities
        subFlow(IdentitySyncFlow.Receive(flow))

        //STEP 8: Sign the txn and send back to the other party
        //Sign the txn
        val signTransactionFlow = object : SignTransactionFlow(flow, SignTransactionFlow.tracker()) {
            override fun checkTransaction(stx: SignedTransaction)  {
                requireThat {
                    //TODO Validate if we want to sign this activation
                }
            }
        }
        //Return to the other party and wait for this state to hit the ledger
        val stx = subFlow(signTransactionFlow)
        return waitForLedgerCommit(stx.id)
        }
    }

    @CordaSerializable
    @InitiatedBy(Activator::class)
    open class Acceptor(val flow: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call() : SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities.single()

            //STEP 2: Receive the message to start activation
            val args = flow.receive<List<*>>().unwrap { it }

            //STEP 3: Prepare the txn
            val kycNameData = args[5] as KYC.KYCNameData
            val kycData = subFlow(KYCRetrievalFlow(kycNameData.firstName, kycNameData.lastName, kycNameData.accountNum))
            val TD = subFlow(TDRetreivalFlows.TDRetreivalFlow(args[0] as TermDeposit.DateData,
                    args[2] as Party, args[1] as Float, args[4] as Amount<Currency>, TermDeposit.internalState.pending, kycData.first().state.data.linearId))

            //Ensure that the caller of the flow was not the owner (i.e client) of the state
            if (TD.first().state.data.owner == flow.counterparty) {
                throw FlowException("Owner of a TD cannot activate it")
            }

            //STEP 4: Generate the Activate Txn
            val tx = TransactionBuilder(notary = notary)
            val generatedTx = TermDeposit().generateActivate(tx, TD.first(), notary)
            //Add cash as output
            val (tx2, cashKeys) = Cash.generateSpend(serviceHub, generatedTx, TD.first().state.data.depositAmount, TD.first().state.data.institue)

            //STEP 5: Sync Identities
            // Sync up confidential identities in the transaction with our counterparty
            subFlow(IdentitySyncFlow.Send(flow, tx2.toWireTransaction(serviceHub)))

            //STEP 7: Sign and retrieve the other parties sig
            val ptx = serviceHub.signInitialTransaction(tx2, cashKeys+serviceHub.myInfo.legalIdentities.first().owningKey)
            val otherPartySig = subFlow(CollectSignaturesFlow(ptx, setOf(flow), CollectSignaturesFlow.tracker()))
            val twiceSignedTx = ptx.plus(otherPartySig.sigs)

            //STEP 8: Commit to the ledger
            return subFlow(FinalityFlow(twiceSignedTx, setOf(flow.counterparty)))
        }
    }


}