package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.TermDeposit
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

/**
 * Flow for prompting a TD issuer to activate a TD - NOTE: This is only for demo purposes. In a real scenario the td issuer
 * only calls the activate flow once the off ledger assets for payment (cash) have been receieved.
 */

@CordaSerializable
object PromptActivate {

    @CordaSerializable
    @StartableByRPC
    @InitiatingFlow
    open class Prompter(val startDate: LocalDateTime, val endDate: LocalDateTime, val interestPercent: Float,
                         val issuingInstitue: Party, val client: Party, val depositAmount: Amount<Currency>) : FlowLogic<Unit>() {//FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): Unit {
        //STEP 1: Notify other party of activation
        val flow = initiateFlow(issuingInstitue)
        flow.send(listOf(startDate, endDate, interestPercent, issuingInstitue, client, depositAmount))

        //STEP 6: Recieve back the signed txn and commit it to the ledger
        return

    }
    }

    @CordaSerializable
    @InitiatedBy(Prompter::class)
    open class Acceptor(val flow: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call() : SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities.single()

            //STEP 2: Receieve the message to start activation
            val args = flow.receive<List<*>>().unwrap { it }

            //STEP 3: Prepare the txn
            val TD = subFlow(ActivateTD.Activator(args[0] as LocalDateTime, args[1] as LocalDateTime, args[2] as Float, args[3] as Party,
                    args[4] as Party, args[5] as Amount<Currency>))

            //STEP 4: Generate the Activate Txn
            return TD
        }
    }


}