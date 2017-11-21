package com.termDeposits.flow.TermDeposit

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import java.time.LocalDateTime

/** Flow for redeeming a TD.
 */


object RedeemTD {

    @InitiatingFlow
    @StartableByRPC
    class RedemptionInitiator(val startDateTime: LocalDateTime, val endDate: LocalDateTime, val interestPercent: Float,
    val issuingInstitue: Party) : FlowLogic<Unit>() {//FlowLogic<SignedTransaction>() {
        override fun call(): Unit {//SignedTransaction {

        }
    }

    @InitiatedBy(RedemptionInitiator::class)
    class RedemptionAcceptor(val otherParty: Party) : FlowLogic<Unit>() {
        override fun call(): Unit {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }
}