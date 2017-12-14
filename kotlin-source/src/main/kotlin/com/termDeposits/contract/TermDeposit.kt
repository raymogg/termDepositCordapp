package com.termDeposits.contract

import com.termDeposit.schema.TDSchemaV1
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
import net.corda.finance.USD
import net.corda.finance.utils.sumCashBy
import java.time.LocalDateTime
import java.util.*

/**
 * Term Deposit
 *
 * Class for Term Deposits. Contains all the verification logic needed for different transactions, as well as a class
 * that defines the term deposit state.
 *
 * See IssueTD, ActivateTD, RedeemdTD and RolloverTD to see the flows which use this state and transaction types.
 */

@CordaSerializable
open class TermDeposit : Contract {

    companion object {
        @JvmStatic
        val TERMDEPOSIT_CONTRACT_ID = "com.termDeposits.contract.TermDeposit"
    }

    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<TermDeposit.Commands>()
        when (command.value) {
            is Commands.Issue -> {
                val outputTD = tx.outputStates.filterIsInstance<TermDeposit.State>().first()
                requireThat {
                    //TD Issue verification
                    "more than two outputs are required" using (tx.outputStates.size > 2) //output should be a replica TDOffer state and the newly created TD State, plus various cash states
                    "More than one input allowed" using (tx.inputStates.size > 1) //input should be a single TDOffer state plus cash
                    "input state must be a term deposit offer" using ((tx.inputStates.filterIsInstance<TermDepositOffer.State>().size == 1))
                    "Owner must have signed the command" using (outputTD.owner.owningKey in command.signers)
                }
            }

            is Commands.Activate -> {
                requireThat {
                    //Pending to active verification
                    "Only one input allowed" using (tx.inputStates.size == 1)
                    "Only one output allowed" using (tx.outputStates.size == 1)
                    "Input must be a term deposit" using (tx.inputStates.first() is TermDeposit.State)
                    "Output must be a term deposit" using (tx.outputStates.first() is TermDeposit.State)
                    val input = tx.inputStates.filterIsInstance<TermDeposit.State>().first()
                    val output = tx.outputStates.filterIsInstance<TermDeposit.State>().first()
                    "Deposit amounts must match" using (input.depositAmount == output.depositAmount)
                    "End dates must match" using (input.endDate == output.endDate)
                    "Issuing institue must match" using (input.institue == output.institue)
                    "interest percent must match" using (input.interestPercent == output.interestPercent)
                    "Owner must have signed the command" using (output.owner.owningKey in command.signers)
                }
            }

            is Commands.Redeem -> requireThat {
                //TD Redeem verification
                "Atleast two inputs are present" using (tx.inputStates.size >= 2) //TD State and Cash State
                "Atleast two one output is present" using (tx.outputStates.isNotEmpty())
                val td = tx.inputStates.filterIsInstance<TermDeposit.State>().first()
                val outputCash = tx.outputStates.sumCashBy(td.owner).quantity
                "Term Deposit amount must match output cash amount" using (outputCash == (td.depositAmount.quantity * (100+td.interestPercent)/100).toLong() )
                //"The term deposit has not yet expired" using (td.endDate.isBefore(LocalDateTime.now()))
                "Owner must have signed the command" using (td.owner.owningKey in command.signers)

            }

            is Commands.Rollover -> requireThat {
                //TD Rollover verification
                "Only one term deposit input must be present" using (tx.inputStates.filterIsInstance<TermDeposit.State>().size == 1)
                "Only one term deposit output must be present" using (tx.outputStates.filterIsInstance<TermDeposit.State>().size == 1)
                val input = tx.inputStates.filterIsInstance<TermDeposit.State>().first()
                val output = tx.outputStates.filterIsInstance<TermDeposit.State>().first()
                "Input and Output issuer must be the same" using (input.institue == output.institue)
                //"The term deposit has not yet expired" using (input.endDate.isBefore(LocalDateTime.now()))
                "Owner must have signed the command" using (output.owner.owningKey in command.signers)
            }
        }
    }

    // Used to indicate the transaction's intent
    interface Commands : CommandData {
        class Issue : Commands
        class Activate: Commands
        class Rollover : Commands
        class Redeem : Commands
    }


    /**
     * Functions used to simplify generating transactions within flows. They add the required term deposit states and
     * commands for each type of transaction
     */
    fun generateIssue(builder: TransactionBuilder, TDOffer: StateAndRef<TermDepositOffer.State>,
                      notary: Party, depositAmount: Amount<Currency>, to: Party, startDate: LocalDateTime,
                      endDate: LocalDateTime, kyc: StateAndRef<KYC.State>): TransactionBuilder {
        val offerState = TDOffer.state.data
        val TDState = TransactionState(data = TermDeposit.State(startDate, endDate, offerState.interestPercent, offerState.institue,
                depositAmount, internalState.pending, to, clientIdentifier = kyc.state.data.linearId), notary = notary, contract = TERMDEPOSIT_CONTRACT_ID)
        //Add tje TermDeposit as the output
        builder.addOutputState(TDState)
        //Add the issue command
        builder.addCommand(TermDeposit.Commands.Issue(), offerState.institue.owningKey, to.owningKey)
        return builder
    }

    fun genereateRedeem(builder: TransactionBuilder, TDOffer: StateAndRef<TermDeposit.State>): TransactionBuilder {
        builder.addInputState(TDOffer)
        builder.addCommand(TermDeposit.Commands.Redeem(), TDOffer.state.data.institue.owningKey, TDOffer.state.data.owner.owningKey)
        return builder
    }

    fun generateRolloever(builder: TransactionBuilder, oldState: StateAndRef<TermDeposit.State>, notary: Party,
                          tdOffer: StateAndRef<TermDepositOffer.State>, withInterest: Boolean): TransactionBuilder {
        builder.addInputState(oldState)
        val newStartDate = LocalDateTime.MIN //TODO Change this to time.now()
        val newEndDate = newStartDate.plusMonths(tdOffer.state.data.duration.toLong())
        if (withInterest) {
            //Change the deposit amount to be the new amount plus interest
            builder.addOutputState(TransactionState(data = oldState.state.data.copy(startDate = newStartDate, endDate = newEndDate,
                    depositAmount = Amount((oldState.state.data.depositAmount.quantity * (100+oldState.state.data.interestPercent)/100).toLong(), USD),
                    interestPercent = tdOffer.state.data.interestPercent),
                    notary = notary, contract = TERMDEPOSIT_CONTRACT_ID))
        } else {
            //Deposit amount stays the same
            builder.addOutputState(TransactionState(data = oldState.state.data.copy(startDate = newStartDate, endDate = newEndDate, interestPercent = tdOffer.state.data.interestPercent)
                    , notary = notary, contract = TERMDEPOSIT_CONTRACT_ID))
        }
        builder.addCommand(TermDeposit.Commands.Rollover(), oldState.state.data.institue.owningKey, oldState.state.data.owner.owningKey)
        return builder
    }

    fun generateActivate(builder: TransactionBuilder, TDState: StateAndRef<TermDeposit.State>, TDConsumer: Party,
                         notary: Party): TransactionBuilder {
        builder.addInputState(TDState)
        builder.addOutputState(TransactionState(data = TDState.state.data.copy(internalState = internalState.active), notary = TDState.state.notary, contract = TERMDEPOSIT_CONTRACT_ID))
        builder.addCommand(TermDeposit.Commands.Activate(), TDState.state.data.institue.owningKey, TDConsumer.owningKey)
        return builder
    }


    /** Object used to define the states that a term deposit can be in
     * These are used in flows where a term deposit is being finalized. Transitioning the internalState is done through flows
     * and certain flows will require the internal state to be a certain value before they proceed. Once the internal state
     * is transitioned to exited, the TD state will become consumed in the vault - as it is no longer an active loan.
     */


    object internalState {
        val pending = "Pending" //Term deposit has been created, but waiting for confirmation of payment reception from issuing party
        val active = "Active" //Term Deposit is active (i.e between start date and end date)
        val exited = "Exited" //A TD state with this internal state should always be "consumed" and hence unusuable in a txn
    }

    // **********************
    // * Term Deposit State *
    // **********************

    @CordaSerializable
    data class State(val startDate: LocalDateTime, val endDate: LocalDateTime, val interestPercent: Float,
                     val institue: Party, val depositAmount: Amount<Currency>, val internalState: String, val owner: AbstractParty,
                     override val linearId: UniqueIdentifier = UniqueIdentifier(), val clientIdentifier: UniqueIdentifier) : QueryableState, ContractState, LinearState {

        //Participants store this state in their vault - therefor this should be both the owner (whoever has taken out the loan) and the issueing institute
        override val participants: List<AbstractParty> get() = listOf(owner, institue)

        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is TDSchemaV1 -> TDSchemaV1.PersistentTDSchema(
                        startDate = this.startDate,
                        endDate = this.endDate,
                        interest = this.interestPercent,
                        institute = this.institue.owningKey.toBase58String()

                )
                else -> throw IllegalArgumentException("Unrecognised Schema $schema")
            }
        }

        override fun supportedSchemas(): Iterable<MappedSchema> = setOf(TDSchemaV1)


        override fun toString(): String {
            return "Term Deposit: From $institue at $interestPercent% starting on $startDate and ending on $endDate to $owner with amount $depositAmount (InternalState: $internalState)"
        }
    }

    /**
     * Data class to hold the required terms for a Term deposit rollover. Needed as the number of variables that can be past
     * to a flow is limited to 6 - so these three need to be grouped into a single object.
     */
    @CordaSerializable
    data class RolloverTerms(val interestPercent: Float, val offeringInstitue: Party, val duration: Int, val withInterest: Boolean)


    /** Data Class to hold required date data. Needed for same reason as rolloverTerms data class above */
    @CordaSerializable
    data class DateData(val startDate: LocalDateTime, val endDate: LocalDateTime, val duration: Int)
}