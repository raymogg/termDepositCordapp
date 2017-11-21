package com.termDeposit.flow.TermDeposit

import com.termDeposit.contract.TermDepositOfferContract
import com.termDeposit.contract.TermDepositOfferState
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.time.LocalDateTime


/** Flow for issuing a TD Offer. This flow would be initiated by a institue offering term deposits (eg banks) then
 * This offer is sent to all parties (supplied to the flow). No validation/response is required from parties as this state
 * will simply be stored in their vault, and can be used to create an actual TD at any point by creating a copy of the offer
 * and calling the IssueTD flow with it as a input. */


object IssueOffer {

    /** Initiator class for creating the TDOffers. For the purpose of this issue flow, there is no other party that
     * is required to do anything and hence this initiator is the only party involved in the flow
     */
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val startDateTime: LocalDateTime, val endDate: LocalDateTime, val interestPercent: Float,
                    val issuingInstitue: Party, val otherParties: List<Party>) : FlowLogic<Unit>() {//FlowLogic<SignedTransaction>() {
        override fun call(): Unit { //SignedTransaction {
            //STEP 1: Create TDOffer
            val notary = serviceHub.networkMapCache.notaryIdentities.single()
            val tx = TransactionBuilder()
            TermDepositOfferContract().generateIssue(tx, startDateTime, endDate, interestPercent, issuingInstitue, notary)

            //STEP 2: Start a flowSession w/ each party and commit this offer to the ledger (TODO does this mean the other party receieves this??)
            otherParties.forEach {
                val flowSession = initiateFlow(it)
                val unnotarisedTX = serviceHub.signInitialTransaction(tx, serviceHub.myInfo.legalIdentities.first().owningKey)
                subFlow(ResolveTransactionsFlow(unnotarisedTX, flowSession)) //This is required for notary validation to pass
                //val unnotarisedTX = serviceHub.addSignature(tx, serviceHub.myInfo.legalIdentities.first().owningKey)
                val finishedTX = subFlow(FinalityFlow(unnotarisedTX, setOf(it))) //This parties vault will receieve the txn data and state in their vault.
            }

            return
        }
    }
}