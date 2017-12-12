package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.groupPublicKeysByWellKnownParty
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder


object UpdateKYC {

    @CordaSerializable
    @InitiatingFlow
    @StartableByRPC
    class Updator(val clientID: UniqueIdentifier, val newAccountNum: String) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            //STEP 1: Retrieve the KYC to be updated
            val KYC = subFlow(KYCRetrievalFlowID(clientID))
            val notary = serviceHub.networkMapCache.notaryIdentities.first()


            //STEP 2: Generate the update (for now there isnt really fields that can be changed?)
            val builder = TransactionBuilder(notary)
            val tx = com.termDeposits.contract.KYC().generateUpdate(builder, newAccountNum, KYC.first(), notary, serviceHub.myInfo.legalIdentities.first())

            //STEP 3: Sign txn and commit to the ledger
            val stx = serviceHub.signInitialTransaction(tx)
            val extraParties = groupPublicKeysByWellKnownParty(serviceHub, KYC.first().state.data.banksInvolved.toSet().map { it.owningKey }).keys
            //By sending to these extra parties the KYC data should be updated in all the banks vaults
            return subFlow(FinalityFlow(stx, extraParticipants = extraParties ))
        }

    }
}