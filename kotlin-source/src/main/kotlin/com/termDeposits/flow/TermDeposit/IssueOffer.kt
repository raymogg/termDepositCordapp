package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.TermDepositOffer
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.time.LocalDateTime


/** Flow for issuing a TD Offer. This flow would be initiated by a institue offering term deposits (eg banks) then
 * This offer is sent to all parties (supplied to the flow). These parties then simply sign the offer in order to store
 * it in their vault. Signing the offer does not lock the clients in to anything. This offer is able to be redeemed
 * for an actual term deposit at the offers rates with the issuing institue (provided the current time is between the
 * start and end date
 */

@CordaSerializable
object IssueOffer {

    @CordaSerializable
    @StartableByRPC
    @InitiatingFlow
    open class Initiator(val startDateTime: LocalDateTime, val endDate: LocalDateTime, val interestPercent: Float,
                    val issuingInstitue: Party, val otherParty: Party) : FlowLogic<SignedTransaction>() {//FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            //STEP 1: Create TDOffer and build txn
            val notary = serviceHub.networkMapCache.notaryIdentities.single()
            val tx = TransactionBuilder()
            val partTx = TermDepositOffer().generateIssue(tx, startDateTime, endDate, interestPercent, issuingInstitue, notary, otherParty)
            val flowSession = initiateFlow(otherParty)
            //Send the Transaction to the other party
            flowSession.send(partTx)

            //STEP 4: Receieve back txn
            val stx = flowSession.receive<SignedTransaction>().unwrap { it }

            //STEP 5: Notarise and finalize this txn
            val unnotarisedTX = serviceHub.addSignature(stx)
            subFlow(ResolveTransactionsFlow(unnotarisedTX, flowSession)) //This is required for notary validation to pass
            println("Offer Issued to $otherParty for a TD at $interestPercent% by ${issuingInstitue.name}")
            val finishedTX = subFlow(FinalityFlow(unnotarisedTX, setOf(otherParty))) //This parties vault will receieve the txn data and state in their vault.
            return finishedTX
        }
    }

    @CordaSerializable
    @StartableByRPC
    @InitiatedBy(IssueOffer.Initiator::class)
    open class Reciever(val flow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            //STEP 2: Recieve txn from issuing institue
            val tx = flow.receive<TransactionBuilder>().unwrap {
                //println("Recieved Txn")
                //TODO: Any checks that need to be done on the offer (shouldnt need to be any) or potential logging of offers recieved
                it
            }

            //STEP 3: Sign and send back this offer
            val stx = serviceHub.signInitialTransaction(tx)
            flow.send(stx)
            return waitForLedgerCommit(stx.id)

        }
    }
}