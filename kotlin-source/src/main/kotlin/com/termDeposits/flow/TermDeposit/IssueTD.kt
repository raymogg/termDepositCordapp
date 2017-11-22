package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.TermDeposit.Companion.TERMDEPOSIT_CONTRACT_ID
import com.termDeposits.contract.TermDeposit
import com.termDeposits.contract.TermDepositOffer
import net.corda.core.contracts.Amount
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.time.LocalDateTime
import java.util.*


/** Flow for issuing a TD. Called by a client (eg AMM Advisor) with the opposing party being the issuer of the TD (who
 * needs to verify this offer is still active and client is valid).
 */

@CordaSerializable
object IssueTD {

    /** Initiator class for creating the TD. This will normally be a client of the TD Issuer who is creating a TD on behalf
     * of one of their clients.
     */
    @CordaSerializable
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val startDate: LocalDateTime, val endDate: LocalDateTime, val interestPercent: Float,
    val issuingInstitue: Party, val depositAmount: Amount<Currency>) : FlowLogic<Unit>() {//FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): Unit {//SignedTransaction {
            //STEP 1: Retrieve TD Offer from vault with the provided terms
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            println("Notary " + notary)
            val TDOffers = subFlow(OfferRetrievalFlow(startDate, endDate, issuingInstitue, interestPercent))
            if (TDOffers.size > 1) {
                //TODO Decide which offer is being taken
            }
            val TDOffer = TDOffers.first()

            //STEP 2: Build Txn with TDOffer as input and TDOffer + TDState as output TODO Work in attachments and send client KYC data here
            val builder = TransactionBuilder(notary = notary)
            val tx = TermDeposit().generateIssue(builder,TDOffer,serviceHub.myInfo.legalIdentities.first(), notary, depositAmount)

            //STEP 3: Send to the issuing institue for verification/acceptance
            val flow = initiateFlow(issuingInstitue)
            flow.sendAndReceive<TransactionBuilder>(tx)

            //STEP 7: Receieve back txn, sign and commit to ledger
            val stx = flow.receive<SignedTransaction>().unwrap { it }
            subFlow(ResolveTransactionsFlow(stx, flow))
            val unnotarisedTx = serviceHub.addSignature(stx, serviceHub.myInfo.legalIdentities.first().owningKey)
            val finishedTx = subFlow(FinalityFlow(unnotarisedTx, setOf(issuingInstitue)))
            println("TD Issued to ${serviceHub.myInfo.legalIdentities.first().name} by ${issuingInstitue.name} at $interestPercent%")
            return
        }
    }

    @CordaSerializable
    @InitiatedBy(Initiator::class)
    open class Acceptor(val counterPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call(): Unit {
            //STEP 4: Receieve the transaction with the TD Offer and TD
            val tx = counterPartySession.receive<TransactionBuilder>()

            //STEP 5: Validate and accept txn
            tx.unwrap {
                requireThat {
                    //This state was actually issued by us
                    it.inputStates().filterIsInstance<TermDepositOffer.State>().first().institue == serviceHub.myInfo.legalIdentities.first()
                }
                it
            }

            //STEP 6: Sign transaction and send back to other party
            val stx = serviceHub.signInitialTransaction(tx.unwrap{it}, serviceHub.myInfo.legalIdentities.first().owningKey)
            counterPartySession.send(stx)
            waitForLedgerCommit(stx.id)
        }
    }
}