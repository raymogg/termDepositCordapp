package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.TermDeposit
import com.termDeposits.contract.TermDepositOffer
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import java.time.LocalDateTime
import java.util.*
import com.termDeposits.contract.TermDeposit.internalState
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowException
import net.corda.core.flows.StartableByRPC
import net.corda.core.serialization.CordaSerializable

/**
 * Created by raymondm on 21/11/2017.
 *
 * Flow for retrieving TD's from the nodes vault. Queries based on start date, end date, interest percentage and offering
 * institute. Optionally, a field can be supplied to filter by the TD's current internal state (i.e active, tentative, eg).
 *
 * There is no restriction on this flow to retrieve only one TD (i.e a TD with the exact same terms from the same institue
 * is possible)
 */


@StartableByRPC
@CordaSerializable
class TDRetreivalFlow(val startDate: LocalDateTime, val endDate: LocalDateTime, val offeringInstitute: Party,
                         val interest: Float, val depositAmount: Amount<Currency>, val state: String = internalState.active): FlowLogic<List<StateAndRef<TermDeposit.State>>>() {
    @Suspendable
    override fun call(): List<StateAndRef<TermDeposit.State>> {
        //Query the vault for unconsumed states and then for Security loan states
        val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)
        val offerStates = serviceHub.vaultService.queryBy<TermDeposit.State>(criteria)
        val filteredStates: List<StateAndRef<TermDeposit.State>>
        //Filter offer states to get the states we want
        //Active Filter
        if (state == TermDeposit.internalState.active) {
            //println("Active Filter")
            filteredStates = offerStates.states.filter {
                it.state.data.endDate.isAfter(LocalDateTime.now()) && it.state.data.startDate == startDate &&
                        it.state.data.endDate == endDate && it.state.data.institue == offeringInstitute &&
                        it.state.data.interestPercent == interest && it.state.data.internalState == TermDeposit.internalState.active
            }
        }

        //Pending filter
        else if (state == TermDeposit.internalState.pending) {
            //println("Pending Filter")
            filteredStates = offerStates.states.filter {
                it.state.data.endDate.isAfter(LocalDateTime.now()) && it.state.data.startDate == startDate &&
                        it.state.data.endDate == endDate && it.state.data.institue == offeringInstitute &&
                        it.state.data.interestPercent == interest && it.state.data.internalState == TermDeposit.internalState.pending
            }
        }

        //Deposits that are "expired" (i.e end date done, but not actually consumed)
        else if (state == TermDeposit.internalState.exited) {
            //println("Expired Filter")
            filteredStates = offerStates.states.filter {
                //it.state.data.endDate.isBefore(LocalDateTime.now()) &&
                        it.state.data.startDate == startDate &&
                        it.state.data.endDate == endDate && it.state.data.institue == offeringInstitute &&
                        it.state.data.interestPercent == interest
            }
        }

        else {
            throw FlowException("Invalid Term Deposit State provided")
        }

        if (filteredStates.isEmpty()) {
            throw FlowException("No Term Deposit states found")
        }

        return filteredStates
    }
}