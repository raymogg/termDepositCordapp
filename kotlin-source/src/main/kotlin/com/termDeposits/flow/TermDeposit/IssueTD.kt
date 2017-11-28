package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.TermDeposit.Companion.TERMDEPOSIT_CONTRACT_ID
import com.termDeposits.contract.TermDeposit
import com.termDeposits.contract.TermDepositOffer
import net.corda.core.contracts.Amount
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.time.LocalDateTime
import java.util.*


/** Flow for issuing a TD. Called by a client (eg AMM Advisor) with the opposing party being the issuer of the TD (who
 * needs to verify this offer is still active and client is valid).
 */

@CordaSerializable
object IssueTD {

    /** Initiator class for creating the TD. This will normally be a client of the TD Issuer who is creating a TD on behalf
     * of one of their clients.
     */
    @CordaSerializable
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val startDate: LocalDateTime, val endDate: LocalDateTime, val interestPercent: Float,
    val issuingInstitue: Party, val depositAmount: Amount<Currency>) : FlowLogic<SignedTransaction>() {//FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {//SignedTransaction {
            //STEP 1: Retrieve TD Offer from vault with the provided terms
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            println("Notary " + notary)
            val TDOffers = subFlow(OfferRetrievalFlow(startDate, endDate, issuingInstitue, interestPercent))
            if (TDOffers.size > 1) {
                //TODO Decide which offer is being taken
            }
            val TDOffer = TDOffers.first()

            //STEP 2: Build Txn with TDOffer as input and TDOffer + TDState as output TODO Work in attachments and send client KYC data here
            val builder = TransactionBuilder(notary = notary)
            val tx = TermDeposit().generateIssue(builder,TDOffer, notary, depositAmount, serviceHub.myInfo.legalIdentities.first())

            //STEP 3: Send to the issuing institue for verification/acceptance
            val flow = initiateFlow(issuingInstitue)
            val stx = flow.sendAndReceive<SignedTransaction>(tx).unwrap { it }
            println("Before TD Issued")
            //STEP 7: Receieve back txn, sign and commit to ledger
            subFlow(ResolveTransactionsFlow(stx, flow))
            val unnotarisedTx = serviceHub.addSignature(stx, serviceHub.myInfo.legalIdentities.first().owningKey)
            println("TD Issued to ${stx.tx.outputStates.filterIsInstance<TermDeposit.State>().first().owner} by ${issuingInstitue.name} at $interestPercent%")
            return subFlow(FinalityFlow(unnotarisedTx))

        }
    }

    @CordaSerializable
    @InitiatedBy(Initiator::class)
    open class Acceptor(val counterPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            //STEP 4: Receieve the transaction with the TD Offer and TD
            val tx = counterPartySession.receive<TransactionBuilder>()

            //STEP 5: Validate and accept txn
            val unwrappedtx = tx.unwrap {
                println(it.inputStates())
                println(it.outputStates())
                requireThat {
                    //This state was actually issued by us
                    //it.inputStates().filterIsInstance<TermDepositOffer.State>().first().institue == serviceHub.myInfo.legalIdentities.first()
                    println("Term Deposit Offer output state" + it.outputStates().map { it.data }.filterIsInstance<TermDepositOffer.State>().map { it.owner })
                    println("Term Deposit Output State "+it.outputStates().map { it.data }.filterIsInstance<TermDeposit.State>())
                }
                it
            }

            //STEP 6: Sign transaction and send back to other party
            val stx = serviceHub.signInitialTransaction(unwrappedtx, serviceHub.myInfo.legalIdentities.first().owningKey)
            counterPartySession.send(stx)
            return stx
        }
    }
}