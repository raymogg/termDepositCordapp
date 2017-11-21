package com.termDeposits.flow.TermDeposit

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import java.time.LocalDateTime

/** Flow for rolling over a TD.
 */


object RolloverTD {

    @InitiatingFlow
    @StartableByRPC
    class RedemptionInitiator(val startDateTime: LocalDateTime, val endDate: LocalDateTime, val interestPercent: Float,
    val issuingInstitue: Party) : FlowLogic<SignedTransaction>() {
        override fun call(): SignedTransaction {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }

    class RedemptionAcceptor(val otherParty: Party) {

    }
}