package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.KYC
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
 * Flow for  TD Issuer to activate a TD with a client. This flow would be invoked after the issuer has recieved the transfer
 * of cash off ledger. This flow will change the term deposits internal state from pending to active indicating it is now
 * "live"
 */

@CordaSerializable
object ActivateTD {

    @CordaSerializable
    @StartableByRPC
    @InitiatingFlow
    open class Activator(val dateData: TermDeposit.DateData, val interestPercent: Float,
                         val issuingInstitue: Party, val client: Party, val depositAmount: Amount<Currency>, val kycData: KYC.KYCNameData) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        //STEP 1: Notify other party of activation
        val flow = initiateFlow(client)
        flow.send(listOf(dateData, interestPercent, issuingInstitue, client, depositAmount, kycData))

        //STEP 6: Recieve back the signed txn and commit it to the ledger
        val ptx = flow.receive<SignedTransaction>()

        //STEP 5: Sign the txn and commit to the ledger
        val stx = serviceHub.addSignature(ptx.unwrap { it })
        subFlow(ResolveTransactionsFlow(stx, flow))
        println("Term Deposit: from $issuingInstitue to $client now activated")
        println("Participants ${stx.tx.outputStates.filterIsInstance<TermDeposit.State>().first().participants}")
        return subFlow(FinalityFlow(stx, setOf(client)))
        }
    }

    @CordaSerializable
    @InitiatedBy(Activator::class)
    open class Acceptor(val flow: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call() : SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities.single()

            //STEP 2: Receive the message to start activation
            val args = flow.receive<List<*>>().unwrap { it }

            //STEP 3: Prepare the txn
            val kycNameData = args[5] as KYC.KYCNameData
            val kycData = subFlow(KYCRetrievalFlow(kycNameData.firstName, kycNameData.lastName, kycNameData.accountNum))
            val TD = subFlow(TDRetreivalFlows.TDRetreivalFlow(args[0] as TermDeposit.DateData,
                    args[2] as Party, args[1] as Float, args[4] as Amount<Currency>, TermDeposit.internalState.pending, kycData.first().state.data.linearId))

            //Ensure that the caller of the flow was not the owner (i.e client) of the state
            if (TD.first().state.data.owner == flow.counterparty) {
                throw FlowException("Owner of a TD cannot activate it")
            }

            //STEP 4: Generate the Activate Txn
            val tx = TransactionBuilder(notary = notary)
            TermDeposit().generateActivate(tx, TD.first(), args[3] as Party, notary)


            //STEP 5: Sign and send back the txn (only updating internal state so no validation required really)
            val ptx = serviceHub.signInitialTransaction(tx, serviceHub.myInfo.legalIdentities.first().owningKey)
            flow.send(ptx)
            return waitForLedgerCommit(ptx.id)
        }
    }


}