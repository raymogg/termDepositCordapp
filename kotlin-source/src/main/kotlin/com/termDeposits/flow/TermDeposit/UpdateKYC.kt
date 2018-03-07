package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.groupPublicKeysByWellKnownParty
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@CordaSerializable
object UpdateKYC {

    /** Flow for updating client KYC data - all fields can be updated
     *
     * These changes are propegated through to all nodes that are party of the states participants list (i.e the owner of
     * the state plus any banks who are already storing this KYC data in their vaults).
     */
    @CordaSerializable
    @InitiatingFlow
    @StartableByRPC
    class Updator(val clientID: UniqueIdentifier, var newAccountNum: String?, var newFirstName : String?,
                  var newLastName : String?) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            //STEP 1: Retrieve the KYC to be updated
            val KYC = subFlow(KYCRetrievalFlowID(clientID))
            val notary = serviceHub.networkMapCache.notaryIdentities.first()


            //STEP 2: Generate the update (for now there isnt really fields that can be changed?)
            val builder = TransactionBuilder(notary)

            //If any supplied string is the empty string, set this to null (helpful for calls using the API)
            if (newAccountNum == "") {newAccountNum = null}
            if (newFirstName == "") {newFirstName = null}
            if (newLastName == "") {newLastName = null}

            val tx = com.termDeposits.contract.KYC().generateUpdate(builder, newAccountNum, newFirstName, newLastName, KYC.first(), notary, serviceHub.myInfo.legalIdentities.first())

            //STEP 3: Sign txn and commit to the ledger
            val stx = serviceHub.signInitialTransaction(tx)
            val extraParties = groupPublicKeysByWellKnownParty(serviceHub, KYC.first().state.data.banksInvolved.toSet().map { it.owningKey }).keys
            //By sending to these extra parties the KYC data should be updated in all the banks vaults
            return subFlow(FinalityFlow(stx, extraParticipants = extraParties ))
        }

    }
}