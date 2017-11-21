package com.termDeposit.flow.TermDeposit

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
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
                    val issuingInstitue: Party, val otherParty: List<Party>) : FlowLogic<Unit>() {//FlowLogic<SignedTransaction>() {
        override fun call(): Unit {//SignedTransaction {
            //STEP 1: Create TDOffer

            //STEP 2: Send TDOffer to all parties provided to the flow
            return
        }
    }
}