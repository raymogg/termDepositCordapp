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
val TERMDEPOSIT_OFFER_CONTRACT_ID = "com.termDeposit.TermDepositOffer"

open class TemplateContract : Contract {
    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        // Verification logic goes here.
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class Action : Commands
    }
}

// *********
// * State *
// *********
/** Term Deposit Offer Contract State
 * This is an offer of a TD, issued by a TD issuer (such as a bank or institution). The idea is that a TD offer can
 * be converted to an active TD as long as it is within its valid period (start and end date). Comes with attached
 * Terms and conditions as a hash, and these terms are then attached to the issued TD.
 */
data class TemplateState(val data: String) : ContractState {
    override val participants: List<AbstractParty> get() = listOf()
}