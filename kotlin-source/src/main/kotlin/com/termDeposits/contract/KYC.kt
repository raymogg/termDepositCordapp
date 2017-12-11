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
 * Term Deposit
 *
 * Class for KnowYourClient Data. This state is used in transactions where a term deposit is issued - it allows
 * banks to capture the KYC data state and is also used to produce the correct TermDeposit. The KYC states linear
 * id is linked to the term deposit state - meaning each TD points to a specific KYC state related to the customer that
 * has opened that TD.
 */

@CordaSerializable
open class KYC : Contract {

    companion object {
        @JvmStatic
        val KYC_CONTRACT_ID = "com.termDeposits.contract.KYC"
    }

    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<KYC.Commands>()
        when (command.value) {
            is Commands.Issue -> requireThat {
                //KYC Data Issue verification

            }

            is Commands.Update -> requireThat {
                //KYC Update verification
            }

            is Commands.IssueTD -> requireThat {
                //KYC Issue Term Deposit command
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
     * Functions used to simplify generating transactions within flows. They add the required term deposit states and
     * commands for each type of transaction
     */
    fun generateIssue(builder: TransactionBuilder, firstName: String, lastName: String, accountNum: String,
                      notary: Party, selfReference: Party): TransactionBuilder {
        val KYCState = TransactionState(data = KYC.State(firstName, lastName, accountNum, selfReference),notary = notary, contract = KYC_CONTRACT_ID)
        builder.addOutputState(KYCState)
        builder.addCommand(KYC.Commands.Issue(), selfReference.owningKey)
        return builder
    }

    fun generateUpdate(builder: TransactionBuilder): TransactionBuilder {
        return builder
    }



    // **********************
    // * KYC Data State *
    // **********************

    @CordaSerializable
    data class State(val firstName: String, val lastName: String, val accountNum: String, val owner: AbstractParty,
                     override val linearId: UniqueIdentifier = UniqueIdentifier(), val banksInvolved: List<AbstractParty> = emptyList()) : QueryableState, ContractState, LinearState {

        //Each time a TD is issued with this KYC data, the bank it is issued to is added to this banks involved list, meaning the data is now stored in that banks vault
        override val participants: List<AbstractParty> get() = banksInvolved.plus(owner)

        //override fun withNewOwner(newOwner: AbstractParty): CommandAndState = CommandAndState(KYC.Commands.Issue(), copy(owner = newOwner))

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