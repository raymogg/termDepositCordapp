package com.termDeposits.flow.TermDeposit

import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import java.time.LocalDateTime


/** Flow for issuing a TD. Called by a client (eg AMM Advisor) with the opposing party being the issuer of the TD (who
 * needs to verify this offer is still active and client is valid).
 */


object IssueTD {

    @InitiatingFlow
    @StartableByRPC
    /** Initiator class for creating the TD. This will normally be a client of the TD Issuer who is creating a TD on behalf
     * of one of their clients.
     */
    class Initiator(val startDateTime: LocalDateTime, val endDate: LocalDateTime, val interestPercent: Float,
    val issuingInstitue: Party) : FlowLogic<SignedTransaction>() {

        //STEP 1: Retrieve TD Offer from vault with the provided terms

        //STEP 2: Build Txn with TDOffer as input and TDOffer + TDState as output

        //STEP 3: Send to the issuing institue for verification/acceptance
    }

    class Acceptor(val otherParty: Party) {
        //STEP 4: Receieve the transaction with the TD Offer and TD

        //STEP 5: Validate and accept txn

        //STEP 6: Sign transaction and commit to ledger
    }
}