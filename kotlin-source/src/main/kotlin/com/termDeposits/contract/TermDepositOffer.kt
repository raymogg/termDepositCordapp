package com.termDeposits.contract


import com.termDeposit.schema.TDOSchemaV1
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

/**
 * Term Deposit Offer
 *
 * A term deposit offer is issued by an institue which provides term deposits (i.e banks, etc). Provided the current
 * date is between the offers start and end date, and other offer terms are met (i.e minimum or maximum deposit amounts),
 * a TermDeposit offer can be redeemed for an actual term deposit with the issuing institue.
 */

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
            is Commands.CreateTD -> {
                val offer = tx.inputStates.filterIsInstance<TermDepositOffer.State>().first()
                requireThat {
                    //Requirements for creating a TD from a TDOffer
                    tx.inputStates.size == 1
                    tx.outputStates.size == 2 //One TDOffer state, one TD state
                    ((tx.outputStates[0] is TermDepositOffer.State) || (tx.outputStates[1] is TermDepositOffer.State) &&
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
                            TDOTerms.plus(it.validTill.toString())
                            TDOTerms.plus(it.interestPercent.toString())
                            TDOTerms.plus(it.institue.name.commonName.toString())
                        }
                    }
                    TDTerms.equals(TDOTerms) //all terms should match
                    "Issuing party has signed the command" using (offer.institue in command.signingParties)
                }
            }

            is Commands.Issue -> requireThat {
                //Requirements for issuing a new TDOffer
                tx.inputStates.isEmpty()
                tx.outputStates.size == 1
                val offerState = tx.outputStates.filterIsInstance<TermDepositOffer.State>().first()
                //Individual requirements for offer states - e.g non negative values
                "Interest percent must be greater than zero" using (offerState.interestPercent > 0)
                "Issuing institue must have signed the command" using (offerState.institue in command.signingParties)
            }
        }
    }

    // Used to indicate the transaction's intent - current commands for an offer are simply issue and createTD
    interface Commands : CommandData {
        class Issue : Commands
        class CreateTD : Commands
    }

    fun generateIssue(builder: TransactionBuilder, endDate: LocalDateTime, interestPercent: Float,
                             institue: Party, notary: Party, receiver: Party, duration: Int): TransactionBuilder {
        val state = TransactionState(data = TermDepositOffer.State(endDate, duration, interestPercent, institue, receiver), notary = notary, contract = TERMDEPOSIT_OFFER_CONTRACT_ID)
        builder.addOutputState(state)
        builder.addCommand(TermDepositOffer.Commands.Issue(), institue.owningKey)
        return builder
    }


    // *********
    // * State *
    // *********
    /** Term Deposit Offer Contract State
     * See flows for how a TD Offer is convereted to a TD - not that the state is reproduced so one TD offer can produce many
     * identical TD's.
     *
     * Potential other terms : minimum deposit amount, max deposit amount, fees - (todo would these be kept within contract or just in the attachment?)
     */
    @CordaSerializable
    data class State(val validTill: LocalDateTime, val duration: Int,
                                     val interestPercent: Float, val institue: Party, val owner: AbstractParty) : QueryableState, ContractState {

        override val participants: List<AbstractParty> get() = listOf(owner)

        //override fun withNewOwner(newOwner: AbstractParty): CommandAndState = CommandAndState(Commands.Issue(), copy(owner = newOwner))

        override fun toString(): String {
            return "Term Deposit Offer: From ${institue} at ${interestPercent}%"
        }

        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(TDOSchemaV1)

        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is TDOSchemaV1 -> TDOSchemaV1.PersistentTDOSchema(
                        endDate = this.validTill,
                        interest = this.interestPercent,
                        institute = this.institue.owningKey.toBase58String()

                )
                else -> throw IllegalArgumentException("Unrecognised Schema $schema")
            }
        }
    }

}