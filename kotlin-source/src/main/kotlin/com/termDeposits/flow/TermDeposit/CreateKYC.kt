package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.KYC
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.TransactionBuilder


@CordaSerializable
object CreateKYC {

    @CordaSerializable
    @StartableByRPC
    @InitiatingFlow
    class Creator(val firstName: String, val lastName: String, val accountNum: String) : FlowLogic<UniqueIdentifier>() {

        @Suspendable
        override fun call(): UniqueIdentifier {
            //STEP 1: Create KYC Data and build txn
            val notary = serviceHub.networkMapCache.notaryIdentities.single()
            val builder = TransactionBuilder(notary)
            val tx = KYC().generateIssue(builder, firstName, lastName, accountNum, notary, serviceHub.myInfo.legalIdentities.first())

            //STEP 2: Sign and commit txn
            val stx = serviceHub.signInitialTransaction(tx)
            val finalTx = subFlow(FinalityFlow(stx))
            println("KYC Created: $firstName $lastName")
            return tx.outputStates().filterIsInstance<TransactionState<KYC.State>>().first().data.linearId
        }
    }
}