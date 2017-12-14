package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.TermDeposit
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import java.time.LocalDateTime
import java.util.*
import com.termDeposits.contract.TermDeposit.internalState
import net.corda.core.contracts.*
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
 *
 * Another query option is also provided to query based on a term deposits linear ID if required. The same optional internal
 * state field can be provided for this query.
 */

object TDRetreivalFlows {

    @StartableByRPC
    @CordaSerializable
    class TDRetreivalFlow(val dateData: TermDeposit.DateData, val offeringInstitute: Party,
                          val interest: Float, val depositAmount: Amount<Currency>, val state: String = TermDeposit.internalState.active,
                          val clientIdentifier: UniqueIdentifier) : FlowLogic<List<StateAndRef<TermDeposit.State>>>() {
        @Suspendable
        override fun call(): List<StateAndRef<TermDeposit.State>> {
            //Query the vault for unconsumed states and then for Security loan states
            println("Retrieval Start ${dateData.startDate} End ${dateData.endDate} Amount ${depositAmount} Client Ref ${clientIdentifier} \n" +
                    "Duration ${dateData.duration} Interest ${interest} Institue ${offeringInstitute}")
            val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)
            val offerStates = serviceHub.vaultService.queryBy<TermDeposit.State>(criteria)
            val filteredStates: List<StateAndRef<TermDeposit.State>>
            println("All States ${offerStates.states.map { it.state.data.toString() }}")
            //Filter offer states to get the states we want
            //Active Filter
            if (state == TermDeposit.internalState.active) {
                filteredStates = offerStates.states.filter {
                    println("Active Filter")
                    //it.state.data.endDate.isAfter(LocalDateTime.now()) &&
                            it.state.data.startDate == dateData.startDate &&
                            it.state.data.endDate == dateData.endDate && //for now dont do this -> because of duration being added into the tdo state
                            it.state.data.institue == offeringInstitute &&
                            it.state.data.interestPercent == interest &&
                            it.state.data.internalState == TermDeposit.internalState.active &&
                            it.state.data.depositAmount == depositAmount &&
                            it.state.data.clientIdentifier == clientIdentifier
                }
            }

            //Pending filter
            else if (state == TermDeposit.internalState.pending) {
                filteredStates = offerStates.states.filter {
                    //it.state.data.endDate.isAfter(LocalDateTime.now()) &&
                            it.state.data.startDate == dateData.startDate &&
                            it.state.data.endDate == dateData.endDate &&
                            it.state.data.institue == offeringInstitute &&
                            it.state.data.interestPercent == interest &&
                            it.state.data.internalState == TermDeposit.internalState.pending &&
                            it.state.data.depositAmount == depositAmount &&
                            it.state.data.clientIdentifier == clientIdentifier
                }
            }

            //Deposits that are "expired" (i.e end date done, but not actually consumed)
            else if (state == TermDeposit.internalState.exited) {
                filteredStates = offerStates.states.filter {
                    it.state.data.endDate.isBefore(LocalDateTime.now()) &&
                    it.state.data.startDate == dateData.startDate &&
                            it.state.data.endDate == dateData.endDate &&
                            it.state.data.institue == offeringInstitute &&
                            it.state.data.interestPercent == interest &&
                            it.state.data.depositAmount == depositAmount &&
                            it.state.data.clientIdentifier == clientIdentifier
                }
            } else {
                throw FlowException("Invalid Term Deposit State provided")
            }

            if (filteredStates.isEmpty()) {
                throw FlowException("No Term Deposit states found")
            }

            return filteredStates
        }
    }

    //Query based on linearID
    @StartableByRPC
    @CordaSerializable
    class TDRetreivalFlowID(val id: UniqueIdentifier, val state: String = internalState.active) : FlowLogic<List<StateAndRef<TermDeposit.State>>>() {
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
                    it.state.data.linearId == id
                }
            }

            //Pending filter
            else if (state == TermDeposit.internalState.pending) {
                //println("Pending Filter")
                filteredStates = offerStates.states.filter {
                    it.state.data.linearId == id
                }
            }

            //Deposits that are "expired" (i.e end date done, but not actually consumed)
            else if (state == TermDeposit.internalState.exited) {
                //println("Expired Filter")
                filteredStates = offerStates.states.filter {
                    //it.state.data.endDate.isBefore(LocalDateTime.now()) &&
                    it.state.data.linearId == id
                }
            } else {
                throw FlowException("Invalid Term Deposit State provided")
            }

            if (filteredStates.isEmpty()) {
                throw FlowException("No Term Deposit states found")
            }

            return filteredStates
        }
    }

}