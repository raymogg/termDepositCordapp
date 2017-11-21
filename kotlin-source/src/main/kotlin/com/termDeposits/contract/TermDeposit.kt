package com.termDeposit.contract

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.time.LocalDateTime

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
        val command = tx.commands.requireSingleCommand<TermDepositContract.Commands>()
        when (command.value) {
            is Commands.Issue -> requireThat {
                //TD Issue verification
            }

            is Commands.Redeem -> requireThat {
                //TD Redeem verification
            }

            is Commands.Rollover -> requireThat {
                //TD Rollover verification
            }
        }
    }

    // Used to indicate the transaction's intent - current three types of TD txns - issue, rollover and redeem.
    interface Commands : CommandData {
        class Issue : Commands
        class Rollover : Commands
        class Redeem : Commands
    }
}

/** Object used to define the states that a term deposit can be in
 * These are used in flows where a term deposit is being finalized. Transitioning the internalState is done through flows
 * and certain flows will require the internal state to be a certain value before they proceed. Once the internal state
 * is transitioned to exited, the TD state will become consumed in the vault - as it is no longer an active loan.
 */
//TODO: Is internal state needed, or should it just be if the flow isnt finalized within timeout period the txn is reveresed (As corda normally does)
object internalState {
    val ordered = "Ordered"
    val tentative = "Tentative"
    val active = "Active"
    val exited = "Exited" //A TD state with this internal state should always be "consumed" and hence unusuable in a txn
}

// *********
// * State *
// *********
/** Term Deposit Contract State
 * Fields it has are a start and end date, current state (internal state) and offer details (or terms)
 */
data class TermDepositState(val startDate: LocalDateTime, val endDate: LocalDateTime, val interestPercent: Float,
                            val institue: Party) : ContractState {
    override val participants: List<AbstractParty> get() = listOf()

    override fun toString(): String {
        return "Term Deposit: From ${institue} at ${interestPercent}% starting on ${startDate} and ending on ${endDate}"
    }
}