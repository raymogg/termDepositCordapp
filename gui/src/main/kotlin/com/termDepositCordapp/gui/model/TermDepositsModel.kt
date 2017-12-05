package com.termDepositCordapp.gui.model

import com.termDeposits.contract.TermDeposit
import com.termDeposits.contract.TermDepositOffer
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import net.corda.client.jfx.model.Diff
import net.corda.client.jfx.model.NodeMonitorModel
import net.corda.client.jfx.model.observable
import net.corda.client.jfx.utils.fold
import net.corda.client.jfx.utils.map
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.services.Vault
import net.corda.finance.contracts.asset.Cash
import rx.Observable

/**This model is based on ContractStateModel.kt, under corda source code /client/jfx/.../model/
 *
 * Allows live reads to be performed on a node's internal vault w.r.t securityClaims, securityLoans, and cashStates
 */

class TermDepositsModel {
    private val vaultUpdates: Observable<Vault.Update<ContractState>> by observable(NodeMonitorModel::vaultUpdates)

    private val contractStatesDiff: Observable<Diff<ContractState>> = vaultUpdates.map {
        Diff(it.produced, it.consumed)
    }

    //Cash States active in the vault
    private val cashStatesDiff: Observable<Diff<Cash.State>> = contractStatesDiff.map {
        Diff(it.added.filterCashStateAndRefs(), it.removed.filterCashStateAndRefs())
    }
    val cashStates: ObservableList<StateAndRef<Cash.State>> = cashStatesDiff.fold(FXCollections.observableArrayList()) { list: MutableList<StateAndRef<Cash.State>>, statesDiff ->
        list.removeIf { it in statesDiff.removed }
        list.addAll(statesDiff.added)
    }
    val cash = cashStates.map { it.state.data.amount }

    //Security claim states active in the vault
    private val offerStatesDiff: Observable<Diff<TermDepositOffer.State>> = contractStatesDiff.map {
        Diff(it.added.filterOfferStateAndRef(), it.removed.filterOfferStateAndRef())
    }
    val offerStates: ObservableList<StateAndRef<TermDepositOffer.State>> = offerStatesDiff.fold(FXCollections.observableArrayList()) { list: MutableList<StateAndRef<TermDepositOffer.State>>, statesDiff ->
        list.removeIf { it in statesDiff.removed }
        list.addAll(statesDiff.added)
    }

    //Active termdeposit states in the vault
    private val depositStatesDiff: Observable<Diff<TermDeposit.State>> = contractStatesDiff.map {
        Diff(it.added.filterDepositStateAndRef(), it.removed.filterDepositStateAndRef())
    }
    val depositStates: ObservableList<StateAndRef<TermDeposit.State>> = depositStatesDiff.fold(FXCollections.observableArrayList()) { list: MutableList<StateAndRef<TermDeposit.State>>, statesDiff ->
        list.removeIf { it in statesDiff.removed}
        list.addAll(statesDiff.added)
    }

    //Pending termdeposit states in the vault
    private val pendingStatesDiff: Observable<Diff<TermDeposit.State>> = contractStatesDiff.map {
        Diff(it.added.filterPendingStateAndRef(), it.removed.filterPendingStateAndRef())
    }
    val pendingStates: ObservableList<StateAndRef<TermDeposit.State>> = pendingStatesDiff.fold(FXCollections.observableArrayList()) { list: MutableList<StateAndRef<TermDeposit.State>>, statesDiff ->
        list.removeIf { it in statesDiff.removed}
        list.addAll(statesDiff.added)
    }


    private fun Collection<StateAndRef<ContractState>>.filterCashStateAndRefs(): List<StateAndRef<Cash.State>> {
        return this.map { stateAndRef ->
            @Suppress("UNCHECKED_CAST")
            if (stateAndRef.state.data is Cash.State) {
                // Kotlin doesn't unify here for some reason
                stateAndRef as StateAndRef<Cash.State>
            } else {
                null
            }
        }.filterNotNull()
    }
    private fun Collection<StateAndRef<ContractState>>.filterOfferStateAndRef(): List<StateAndRef<TermDepositOffer.State>> {
        return this.map { stateAndRef ->
            @Suppress("UNCHECKED_CAST")
            if (stateAndRef.state.data is TermDepositOffer.State) {
                // Kotlin doesn't unify here for some reason
                stateAndRef as StateAndRef<TermDepositOffer.State>
            } else {
                null
            }
        }.filterNotNull()
    }
    private fun Collection<StateAndRef<ContractState>>.filterDepositStateAndRef(): List<StateAndRef<TermDeposit.State>> {
        return this.map { stateAndRef ->
            @Suppress("UNCHECKED_CAST")
            if (stateAndRef.state.data is TermDeposit.State) {
                // Kotlin doesn't unify here for some reason
                if ((stateAndRef as StateAndRef<TermDeposit.State>).state.data.internalState == TermDeposit.internalState.active) {
                    stateAndRef as StateAndRef<TermDeposit.State>
                } else {
                    null
                }
            } else {
                null
            }
        }.filterNotNull()
    }

    private fun Collection<StateAndRef<ContractState>>.filterPendingStateAndRef(): List<StateAndRef<TermDeposit.State>> {
        return this.map { stateAndRef ->
            @Suppress("UNCHECKED_CAST")
            if (stateAndRef.state.data is TermDeposit.State) {
                // Kotlin doesn't unify here for some reason
                if ((stateAndRef as StateAndRef<TermDeposit.State>).state.data.internalState == TermDeposit.internalState.pending) {
                    stateAndRef
                } else {
                    null
                }

            } else {
                null
            }
        }.filterNotNull()
    }
}

//data class Diff<out T : ContractState>(
//        val added: Collection<StateAndRef<T>>,
//        val removed: Collection<StateAndRef<T>>
//)