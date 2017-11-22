package com.termDeposits.contract

import com.termDeposits.contract.TermDepositOffer
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.asset.Cash
import java.time.LocalDateTime
import java.util.*

// *****************
// * Contract Code *
// *****************

@CordaSerializable
open class TermDeposit : Contract {
    companion object {
        @JvmStatic
        val TERMDEPOSIT_CONTRACT_ID = "com.termDeposits.contract.TermDeposit"
    }
    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        // Verification logic goes here.
        val command = tx.commands.requireSingleCommand<TermDeposit.Commands>()
        when (command.value) {
            is Commands.Issue -> requireThat {
                //TD Issue verification
                tx.outputStates.size == 2 //output should be a replica TDOffer state and the newly created TD State
                tx.inputStates.size == 1 //input should be a single TDOffer state
                tx.inputStates.first() is TermDepositOffer.State

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

    fun generateIssue(builder: TransactionBuilder, TDOffer: StateAndRef<TermDepositOffer.State>, selfReference: Party,
                      notary: Party, depositAmount: Amount<Currency>): TransactionBuilder {
        val offerState = TDOffer.state.data
        val TDState = TransactionState(data = State(offerState.startDate, offerState.endDate, offerState.interestPercent, offerState.institue,
                depositAmount), notary = notary, contract = TERMDEPOSIT_CONTRACT_ID)
        builder.addOutputState(TDState)
        builder.addInputState(TDOffer)
        builder.addOutputState(TDOffer.state) //TODO Not sure this will work, may need to make a duplicate of this state (eg deep copy)
        builder.addCommand(TermDeposit.Commands.Issue(), offerState.institue.owningKey, selfReference.owningKey)
        return builder
    }

    fun genereateRedeem(builder: TransactionBuilder, TDOffer: StateAndRef<TermDepositOffer.State>, selfReference: Party,
                        notary: Party): TransactionBuilder {
        //TODO
        return builder
    }

    fun generateRolloever(builder: TransactionBuilder, TDOffer: StateAndRef<TermDepositOffer.State>, selfReference: Party,
                          notary: Party): TransactionBuilder {
        //TODO
        return builder
    }


    /** Object used to define the states that a term deposit can be in
     * These are used in flows where a term deposit is being finalized. Transitioning the internalState is done through flows
     * and certain flows will require the internal state to be a certain value before they proceed. Once the internal state
     * is transitioned to exited, the TD state will become consumed in the vault - as it is no longer an active loan.
     */
    //TODO: Is internal state needed, or should it just be if the flow isnt finalized within timeout period the txn is reveresed (As corda normally does)
//      if it is, will need to add to the TermDepositState below

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
     *
     */
    @CordaSerializable
    data class State(val startDate: LocalDateTime, val endDate: LocalDateTime, val interestPercent: Float,
                                val institue: Party, val depositAmount: Amount<Currency>) : ContractState {
        override val participants: List<AbstractParty> get() = listOf()

        override fun toString(): String {
            return "Term Deposit: From $institue at $interestPercent% starting on $startDate and ending on $endDate"
        }
    }
}