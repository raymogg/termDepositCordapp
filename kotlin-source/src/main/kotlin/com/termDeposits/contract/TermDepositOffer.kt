package com.termDeposits.contract


import com.termDeposit.schema.TDOSchemaV1
import com.termDeposit.schema.TermDepositOfferSchema
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.toBase58String
import java.time.LocalDateTime

// *****************
// * Contract Code *
// *****************
// This is used to identify our contract when building a transaction

@CordaSerializable
open class TermDepositOffer : Contract {
    companion object {
        @JvmStatic
        val TERMDEPOSIT_OFFER_CONTRACT_ID = "com.termDeposits.contract.TermDepositOffer"
    }
    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        // Verification logic for each of the TDOffer commands
        val command = tx.commands.requireSingleCommand<TermDepositOffer.Commands>()
        when (command.value) {
            is Commands.CreateTD -> requireThat {
                //Requirements for creating a TD from a TDOffer
                tx.inputStates.size == 1
                tx.outputStates.size == 2 //One TDOffer state, one TD state
                ((tx.outputStates[0] is State) || (tx.outputStates[1] is State) &&
                        (tx.outputStates[0] is TermDeposit.State) || (tx.outputStates[1] is TermDeposit.State))
                val TDTerms = listOf<String>()
                val TDOTerms = listOf<String>()
                //Validate the individual terms match
                tx.outputStates.forEach {
                    if (it is TermDeposit.State) {
                        TDTerms.plus(it.startDate.toString())
                        TDTerms.plus(it.endDate.toString())
                        TDTerms.plus(it.interestPercent.toString())
                        TDTerms.plus(it.institue.name.commonName.toString())
                    } else if (it is State) {
                        TDOTerms.plus(it.startDate.toString())
                        TDOTerms.plus(it.endDate.toString())
                        TDOTerms.plus(it.interestPercent.toString())
                        TDOTerms.plus(it.institue.name.commonName.toString())
                    }
                }
                TDTerms.equals(TDOTerms) //all terms should match
            }

            is Commands.Issue -> requireThat {
                //Requirements for issuing a new TDOffer
                tx.inputStates.isEmpty()
                tx.outputStates.size == 1
                val offerState = tx.outputStates.first() as State
                //Individual requirements for offer states - e.g non negative values
                offerState.startDate != offerState.endDate
                offerState.interestPercent > 0
            }
        }
    }

    // Used to indicate the transaction's intent - current commands for an offer are simply issue and createTD
    interface Commands : CommandData {
        class Issue : Commands
        class CreateTD : Commands
    }

    public fun generateIssue(builder: TransactionBuilder, startDate: LocalDateTime, endDate: LocalDateTime, interestPercent: Float,
                             institue: Party, notary: Party, receiver: Party): TransactionBuilder {
        val state = TransactionState(data = TermDepositOffer.State(startDate, endDate, interestPercent, institue, receiver), notary = notary, contract = TERMDEPOSIT_OFFER_CONTRACT_ID)
        builder.addOutputState(state)
        builder.addCommand(TermDepositOffer.Commands.Issue(), institue.owningKey)
        return builder
    }

    //TODO Should this actually be in the TD contract and not here?? - probably should under TD.Commands.Issue() -> ensure a TDOffer is provided as input and create the output of the TD
    fun genereateCreateTD(builder: TransactionBuilder, TDOffer: StateAndRef<State>, selfReference: Party) {

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
     *
     * Potential other terms : minimum deposit amount, max deposit amount, fees - (todo would these be kept within contract or just in the attachment?)
     */
    @CordaSerializable
    data class State(val startDate: java.time.LocalDateTime, val endDate: java.time.LocalDateTime,
                                     val interestPercent: Float, val institue: Party, override val owner: AbstractParty) : QueryableState, OwnableState {

        override val participants: List<AbstractParty> get() = listOf(owner)

        override fun withNewOwner(newOwner: AbstractParty): CommandAndState = CommandAndState(Commands.Issue(), copy(owner = newOwner))

        override fun toString(): String {
            return "Term Deposit Offer: From ${institue} at ${interestPercent}%"
        }

        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(TDOSchemaV1)

        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is TDOSchemaV1 -> TDOSchemaV1.PersistentTDOSchema(
                        startDate = this.startDate,
                        endDate = this.endDate,
                        interest = this.interestPercent,
                        institute = this.institue.owningKey.toBase58String()

                )
                else -> throw IllegalArgumentException("Unrecognised Schema $schema")
            }
        }
    }

}