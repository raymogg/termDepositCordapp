package com.termDeposits.flow.TermDeposit

import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import java.time.LocalDateTime

/** Flow for redeeming a TD.
 */


object RedeemTD {

    @InitiatingFlow
    @StartableByRPC
    class RedemptionInitiator(val startDateTime: LocalDateTime:, val endDate: LocalDateTime, val interestPercent: Float,
    val issuingInstitue: Party) : FlowLogic<SignedTransaction>() {

    }

    class RedemptionAcceptor(val otherParty: Party) {

    }
}