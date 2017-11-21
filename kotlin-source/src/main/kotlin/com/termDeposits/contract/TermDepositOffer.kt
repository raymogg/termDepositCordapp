package com.termDeposit.contract

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

// *****************
// * Contract Code *
// *****************
// This is used to identify our contract when building a transaction
val TERMDEPOSIT_OFFER_CONTRACT_ID = "com.termDeposit.TermDepositOffer"

open class TermDepositOfferContract : Contract {
    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        // Verification logic for each of the TDOffer commands
        val command = tx.commands.requireSingleCommand<TermDepositOfferContract.Commands>()
        when (command.value) {
            is Commands.CreateTD -> requireThat {
                //Requirements for creating a TD from a TDOffer
            }

            is Commands.Issue -> requireThat {
                //Requirements for issuing a new TDOffer
            }
        }
    }

    // Used to indicate the transaction's intent - current commands for an offer are simply issue and createTD
    interface Commands : CommandData {
        class Issue : Commands
        class CreateTD : Commands
    }
}

// *********
// * State *
// *********
/** Term Deposit Offer Contract State
 * This is an offer of a TD, issued by a TD issuer (such as a bank or institution). The idea is that a TD offer can
 * be converted to an active TD as long as it is within its valid period (start and end date). Comes with attached
 * Terms and conditions as a hash, and these terms are then attached to the issued TD.
 *
 * See flows for how a TD Offer is convereted to a TD - not that the state is reproduced so one TD offer can produce many
 * identical TD's.
 */
data class TermDepositOfferState(val startDate: java.time.LocalDateTime, val endDate : java.time.LocalDateTime,
                                 val interestPercent: Float, val institue: Party) : ContractState {

    override val participants: List<AbstractParty> get() = listOf()

    override fun toString(): String {
        return "Term Deposit Offer: From ${institue} at ${interestPercent}%"
    }
}