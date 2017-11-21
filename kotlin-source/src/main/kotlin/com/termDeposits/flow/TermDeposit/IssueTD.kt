package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposit.contract.TermDepositContract
import com.termDeposit.contract.TermDepositOfferState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.time.LocalDateTime
import java.util.*


/** Flow for issuing a TD. Called by a client (eg AMM Advisor) with the opposing party being the issuer of the TD (who
 * needs to verify this offer is still active and client is valid).
 */


object IssueTD {

    /** Initiator class for creating the TD. This will normally be a client of the TD Issuer who is creating a TD on behalf
     * of one of their clients.
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val startDate: LocalDateTime, val endDate: LocalDateTime, val interestPercent: Float,
    val issuingInstitue: Party, val notary: Party, val depositAmount: Amount<Currency>) : FlowLogic<Unit>() {//FlowLogic<SignedTransaction>() {
        override fun call(): Unit {//SignedTransaction {
            //STEP 1: Retrieve TD Offer from vault with the provided terms
            val TDOffers = subFlow(OfferRetrievalFlow(startDate, endDate, issuingInstitue, interestPercent))
            if (TDOffers.size > 1) {
                //TODO Decide which offer is being taken
            }
            val TDOffer = TDOffers.first()

            //STEP 2: Build Txn with TDOffer as input and TDOffer + TDState as output TODO Work in attachments and send client KYC data here
            val builder = TransactionBuilder()
            val tx = TermDepositContract().generateIssue(builder,TDOffer,serviceHub.myInfo.legalIdentities.first(), notary, depositAmount)

            //STEP 3: Send to the issuing institue for verification/acceptance
            val flow = initiateFlow(issuingInstitue)
            flow.sendAndReceive<TransactionBuilder>(tx)
            return
        }
    }

    @InitiatedBy(Initiator::class)
    @StartableByRPC
    open class Acceptor(val counterPartySession: FlowSession) : FlowLogic<Unit>() {
        //STEP 4: Receieve the transaction with the TD Offer and TD
        @Suspendable
        override fun call(): Unit {
            val tx = counterPartySession.receive<TransactionBuilder>()
            //STEP 5: Validate and accept txn
            tx.unwrap {
                requireThat {
                    //This state was actually issued by us
                    it.inputStates().filterIsInstance<TermDepositOfferState>().first().institue == serviceHub.myInfo.legalIdentities.first()
                }
            }

            //STEP 6: Sign transaction and commit to ledger

        }
    }
}