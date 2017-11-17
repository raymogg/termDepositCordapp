package com.termDeposit.contract

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

// *****************
// * Contract Code *
// *****************
// This is used to identify our contract when building a transaction
val TERMDEPOSIT_CONTRACT_ID = "com.termDeposit.TermDepositContract"

open class TermDepositContract : Contract {
    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        // Verification logic goes here.
    }

    // Used to indicate the transaction's intent - current three types of TD txns - issue, rollover and redeem.
    interface Commands : CommandData {
        class Issue : Commands
        class Rollover : Commands
        class Redeem : Commands
    }
}

// *********
// * State *
// *********
/** Term Deposit Contract State
 * Fields it has are a start and end date, current state and offer details (or terms)
 */
data class TermDepositState(val data: String) : ContractState {
    override val participants: List<AbstractParty> get() = listOf()
}