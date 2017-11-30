package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.TermDeposit
import com.termDeposits.contract.TermDepositOffer
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.time.LocalDateTime
import java.util.*

/** Flow for a Term Deposit Issuer to activate a TD with a client


        */


@CordaSerializable
object ActivateTD {

    @CordaSerializable
    @StartableByRPC
    @InitiatingFlow
    open class Activator(val startDate: LocalDateTime, val endDate: LocalDateTime, val interestPercent: Float,
                         val issuingInstitue: Party, val client: Party, val depositAmount: Amount<Currency>) : FlowLogic<SignedTransaction>() {//FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        println("Start Activivation")
        //STEP 1: Notify other party of activation
        val flow = initiateFlow(client)
        flow.send(listOf(startDate, endDate, interestPercent, issuingInstitue, client, depositAmount)) //this can be anything, simply starting up the clients flow

        //STEP 6: Recieve back the signed txn and commit it to the ledger
        val ptx = flow.receive<SignedTransaction>()

        //STEP 5: Sign the txn and commit to the ledger
        val stx = serviceHub.addSignature(ptx.unwrap { it })
        subFlow(ResolveTransactionsFlow(stx, flow))
        println("Term Deposit: from $issuingInstitue to $client now activated")
        return subFlow(FinalityFlow(stx, setOf(client)))

        }
    }

    @CordaSerializable
    @InitiatedBy(Activator::class)
    open class Acceptor(val flow: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call() : SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities.single()

            //STEP 2: Receieve the message to start activation
            val args = flow.receive<List<*>>().unwrap { it }

            //STEP 3: Prepare the txn
            val TD = subFlow(TDRetreivalFlow(args[0] as LocalDateTime, args[1] as LocalDateTime, args[3] as Party, args[2] as Float, args[5] as Amount<Currency>, TermDeposit.internalState.pending))
            println("Required Values ${args[5] as Amount<Currency>}")
            println("Term Deposit Values ${TD.first().state.data.depositAmount}")
            //STEP 4: Generate the Activate Txn
            val tx = TransactionBuilder(notary = notary)
            TermDeposit().generateActivate(tx, TD.first(), args[3] as Party, notary)


            //STEP 5: Sign and send back the txn (only updating internal state so no validation required really)
            val ptx = serviceHub.signInitialTransaction(tx)
            flow.send(ptx)
            return waitForLedgerCommit(ptx.id)
        }
    }


}