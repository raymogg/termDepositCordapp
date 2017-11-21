package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposit.contract.TermDepositOfferState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import java.time.LocalDateTime

/**
 * Created by raymondm on 21/11/2017.
 *
 * Flow for retrieving a TD Offer from the vault (to then be processed into an active TD.
 * Queries are done using the appropriate start date, end date, interest percentage and offering institution. This flow
 * will only present states that are still able to be redeemed (i.e the current date is before the end date for the offer)
 */

class OfferRetrievalFlow(val startDate: LocalDateTime, val endDate: LocalDateTime, val offeringInstitute: Party,
                         val interest: Float): FlowLogic<List<StateAndRef<TermDepositOfferState>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<TermDepositOfferState>> {
        //Query the vault for unconsumed states and then for Security loan states
        val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)
        val offerStates = serviceHub.vaultService.queryBy<TermDepositOfferState>(criteria)
        //Filter offer states to get the states we want
        val filteredStates = offerStates.states.filter {
            it.state.data.endDate.isAfter(LocalDateTime.now()) && it.state.data.startDate == startDate &&
                    it.state.data.endDate == endDate &&  it.state.data.institue == offeringInstitute &&
                    it.state.data.interestPercent == interest
        }
        return filteredStates
    }
}