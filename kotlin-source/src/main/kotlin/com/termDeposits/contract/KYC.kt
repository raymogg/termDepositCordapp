package com.termDeposits.contract


import com.termDeposits.schema.KYCSchemaV1
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder


/**
 * Term Deposits Cordapp
 *
 * Class for KnowYourClient Data. This state is used in transactions where a term deposit is issued - it allows
 * banks to capture the KYC data state and is also used to produce the correct TermDeposit. The KYC states linear
 * id is linked to the term deposit state - meaning each TD points to a specific KYC state related to the customer that
 * has opened that TD.
 */

@CordaSerializable
open class KYC : Contract {

    //Contract ID used to identify this contract
    companion object {
        @JvmStatic
        val KYC_CONTRACT_ID = "com.termDeposits.contract.KYC"
    }

    /** A transaction is considered valid if the verify() function of the contract of each of the transaction's input
        and output states does not throw an exception. Each transaction type has a different command which is used below
    to choose which verify type is used */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<KYC.Commands>()
        when (command.value) {
            is Commands.Issue -> {
                val inputKYC = tx.outputStates.filterIsInstance<KYC.State>().first()
                requireThat {
                    //KYC Data Issue verification
                    "No inputs are present" using tx.inputStates.isEmpty()
                    "One output is present" using (tx.outputStates.size == 1)
                    "The owner has signed the command" using (inputKYC.owner.owningKey in command.signers)
                }
            }

            is Commands.Update -> {
                val inputKYC = tx.outputStates.filterIsInstance<KYC.State>().first()
                requireThat {
                    println("Command Update Validation")
                    //KYC Update verification
                    "One input is present" using (tx.inputStates.size == 1)
                    "One output is present" using (tx.outputStates.size == 1)
                    "The owner has signed the command" using (inputKYC.owner.owningKey in command.signers)
                    //Ensure the id remains consistent through state updates
                    "The UniqueID of the input and output states match" using (tx.inputStates.filterIsInstance<KYC.State>().first().linearId
                            == tx.outputStates.filterIsInstance<KYC.State>().first().linearId )
                }
            }

            is Commands.IssueTD -> {
                val inputKYC = tx.outputStates.filterIsInstance<KYC.State>().first()
                requireThat {
                    //KYC Issue Term Deposit command -> requires that there is KYC data when we issue
                    "One KYC input is present" using (tx.inputStates.filterIsInstance<KYC.State>().size == 1)
                    "One KYC output is present" using (tx.outputStates.filterIsInstance<KYC.State>().size == 1)
                    "The owner has signed the command" using (inputKYC.owner.owningKey in command.signers)
                }
            }
        }
    }

    // Used to indicate the transaction's intent
    interface Commands : CommandData {
        class Issue : Commands
        class Update: Commands
        class IssueTD: Commands

    }


    /**
     * Functions used to simplify generating transactions within flows. They add the required kyc states and
     * commands for each type of transaction
     */
    fun generateIssue(builder: TransactionBuilder, firstName: String, lastName: String, accountNum: String,
                      notary: Party, selfReference: Party): TransactionBuilder {
        //Create the KYC state
        val KYCState = TransactionState(data = KYC.State(firstName, lastName, accountNum, selfReference),notary = notary, contract = KYC_CONTRACT_ID)
        //Add as an output with the Issue KYC command.
        builder.addOutputState(KYCState)
        builder.addCommand(KYC.Commands.Issue(), selfReference.owningKey)
        return builder
    }

    fun generateUpdate(builder: TransactionBuilder, newAccountNum: String?, newFirstName: String?, newLastName: String?, originalKYC: StateAndRef<KYC.State>,
                       notary: Party, selfReference: Party): TransactionBuilder {
        //Retrieve the original KYC state and create a new updated KYC state
        val originalKYCState = originalKYC.state.data
        val outputState = TransactionState(data = originalKYCState.copy(accountNum = newAccountNum ?: originalKYCState.accountNum, firstName = newFirstName ?: originalKYCState.firstName,
                lastName = newLastName ?: originalKYCState.lastName), notary = notary, contract = KYC_CONTRACT_ID)
        //Add the original state as input, new state as output and attach the Update command.
        builder.addInputState(originalKYC)
        builder.addOutputState(outputState)
        builder.addCommand(KYC.Commands.Update(), selfReference.owningKey)
        return builder
    }


    /** KYC Data State
     * This state is used to represent each individual KYC on the ledger. Each KYC state has a first name, last name and
     * account number (external account eg AMM/Bank account number). The state also has a linearID which can be used to track
     * this states evolution over time (through state updates). The banks involved field is a list of banks that know this client and
     * therefor are storing the KYC data in their vault.
     */
    @CordaSerializable
    data class State(val firstName: String, val lastName: String, val accountNum: String, val owner: AbstractParty,
                     override val linearId: UniqueIdentifier = UniqueIdentifier(), val banksInvolved: List<AbstractParty> = emptyList()) : QueryableState, ContractState, LinearState {

        //Each time a TD is issued with this KYC data, the bank it is issued to is added to this banks involved list, meaning the data is now stored in that banks vault
        override val participants: List<AbstractParty> get() = banksInvolved.plus(owner)


        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is KYCSchemaV1 -> KYCSchemaV1.PersistentTDSchema(
                        firstName = this.firstName,
                        lastName = this.lastName,
                        accountNum = this.accountNum
                        //linearID = this.linearId

                )
                else -> throw IllegalArgumentException("Unrecognised Schema $schema")
            }
        }

        override fun supportedSchemas(): Iterable<MappedSchema> = setOf(KYCSchemaV1)


        override fun toString(): String {
            return "KYC Data: $firstName $lastName with account number $accountNum (LinearID: $linearId)"
        }
    }

    @CordaSerializable
    data class KYCNameData(
            val firstName: String,
            val lastName: String,
            val accountNum: String
    )

}