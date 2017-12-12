package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.KYC
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable

/**
 * Created by raymondm on 7/12/2017.
 *
 * Flow for retrieving customer KYC data based both on the fields (i.e first name, last name, account number) or the linear ID
 * of their KYC state
 */

    @StartableByRPC
    @CordaSerializable
    class KYCRetrievalFlow(val firstName: String, val lastName: String, val accountNum: String) : FlowLogic<List<StateAndRef<KYC.State>>>() {
        @Suspendable
        override fun call(): List<StateAndRef<KYC.State>> {
            //Query the vault for unconsumed states and then for Security loan states
            val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)
            val filteredStates = serviceHub.vaultService.queryBy<KYC.State>(criteria).states.filter {
                it.state.data.firstName == firstName &&
                        it.state.data.lastName == lastName &&
                        it.state.data.accountNum == accountNum
            }

            if (filteredStates.isEmpty()) {
                throw FlowException("No Client KYC Data found")
            } else if (filteredStates.size > 1) {
                throw FlowException("Too many KYC states found for this client")
            }

            return filteredStates
        }
    }



    /** Vault query for finding KYC data by its unique ID */
    @StartableByRPC
    @CordaSerializable
    class KYCRetrievalFlowID(val linearID: UniqueIdentifier) : FlowLogic<List<StateAndRef<KYC.State>>>() {
        @Suspendable
        override fun call(): List<StateAndRef<KYC.State>> {
            //Query the vault for unconsumed states and then for Security loan states
            val criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.UNCONSUMED)
            val filteredStates = serviceHub.vaultService.queryBy<KYC.State>(criteria).states.filter {
                it.state.data.linearId == linearID
            }

            if (filteredStates.isEmpty()) {
                throw FlowException("No Client KYC Data found")
            } else if (filteredStates.size > 1) {
                throw FlowException("Too many KYC states found for this client")
            }

            return filteredStates
        }
    }


