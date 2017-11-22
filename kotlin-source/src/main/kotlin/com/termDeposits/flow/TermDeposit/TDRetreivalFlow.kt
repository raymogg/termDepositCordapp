package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposit.contract.TermDepositOfferState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import java.time.LocalDateTime
import java.util.*
import com.termDeposit.contract.internalState

/**
 * Created by raymondm on 21/11/2017.
 *
 * Flow for retrieving TD's from the nodes vault. Queries based on start date, end date, interest percentage and offering
 * institute. Optionally, a field can be supplied to filter by the TD's current internal state (i.e active, tentative, eg).
 *
 * There is no restriction on this flow to retrieve only one TD (i.e a TD with the exact same terms from the same institue
 * is possible)
 */


class TDRetreivalFlow(val startDate: LocalDateTime, val endDate: LocalDateTime, val offeringInstitute: Party,
                         val interest: Float, val depositAmount: Amount<Currency>, val state: String = internalState.active): FlowLogic<List<StateAndRef<TermDepositOfferState>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<TermDepositOfferState>> {
        //Query the vault for unconsumed states and then for Security loan states
        val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)
        val offerStates = serviceHub.vaultService.queryBy<TermDepositOfferState>(criteria)
        //Filter offer states to get the states we want
        val filteredStates = offerStates.states.filter {
            it.state.data.endDate.isAfter(LocalDateTime.now()) && it.state.data.startDate == startDate &&
                    it.state.data.endDate == endDate &&  it.state.data.institue == offeringInstitute &&
                    it.state.data.interestPercent == interest //it.state.data.internalState == state (Not implemented in the stsate yet)
        }
        return filteredStates
    }
}