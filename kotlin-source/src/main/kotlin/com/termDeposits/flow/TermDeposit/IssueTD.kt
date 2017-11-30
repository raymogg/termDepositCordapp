package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.TermDeposit.Companion.TERMDEPOSIT_CONTRACT_ID
import com.termDeposits.contract.TermDeposit
import com.termDeposits.contract.TermDepositOffer
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.identity.groupPublicKeysByWellKnownParty
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.utils.sumCashBy
import java.time.LocalDateTime
import java.util.*
import javax.annotation.Signed


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
            val flow = initiateFlow(issuingInstitue)
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val TDOffers = subFlow(OfferRetrievalFlow(startDate, endDate, issuingInstitue, interestPercent))
            if (TDOffers.size > 1) {
                //TODO Decide which offer is being taken - this shouldnt matter as the offers are identical
            }
            val TDOffer = TDOffers.first()

            //STEP 2: Build Txn with TDOffer as input and TDOffer + TDState as output TODO Work in attachments and send client KYC data here
            val builder = TransactionBuilder(notary = notary)
            //Add TD Offer as input
            builder.addInputState(TDOffer)
            builder.addOutputState(TDOffer.state.copy())
            //Add cash as output
            val (tx, cashKeys) = Cash.generateSpend(serviceHub, builder, depositAmount, issuingInstitue)
            builder.addCommand(Command(TermDepositOffer.Commands.CreateTD(), TDOffer.state.data.owner.owningKey))
            val ptx = TermDeposit().generateIssue(tx,TDOffer, notary, depositAmount, serviceHub.myInfo.legalIdentities.first())
            //Sign txn
            val stx = serviceHub.signInitialTransaction(ptx, cashKeys)

            // Sync up confidential identities in the transaction with our counterparty
            subFlow(IdentitySyncFlow.Send(flow, ptx.toWireTransaction(serviceHub)))

            //STEP 3: Send to the issuing institue for verification/acceptance
            //val ftx = flow.sendAndReceive<SignedTransaction>(stx).unwrap { it }
            val otherPartySig = subFlow(CollectSignaturesFlow(stx, setOf(flow), CollectSignaturesFlow.tracker()))
            //STEP 7: Receieve back txn, sign and commit to ledger
            //subFlow(ResolveTransactionsFlow(ftx, flow))
            val twiceSignedTx = stx.plus(otherPartySig.sigs)
            //val unnotarisedTx = serviceHub.addSignature(stx, serviceHub.myInfo.legalIdentities.first().owningKey)
            println("TD Issued to ${stx.tx.outputStates.filterIsInstance<TermDeposit.State>().first().owner} by ${issuingInstitue.name} at $interestPercent%")
            return subFlow(FinalityFlow(twiceSignedTx, setOf(issuingInstitue) + groupPublicKeysByWellKnownParty(serviceHub,cashKeys).keys ))

        }
    }

    @CordaSerializable
    @InitiatedBy(Initiator::class)
    @StartableByRPC
    open class Acceptor(val counterPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            //STEP 4: Receieve the transaction with the TD Offer and TD
//            val tx = counterPartySession.receive<SignedTransaction>()
//
//            //STEP 5: Validate and accept txn
//            val unwrappedtx = tx.unwrap {
//                requireThat {
//                    //This state was actually issued by us
//                    it.tx.outputs.map { it.data }.filterIsInstance<TermDepositOffer.State>().first().institue == serviceHub.myInfo.legalIdentities.first()
//                }
//                it
//            }
//
//            //STEP 6: Sign transaction and send back to other party
//            //val stx = serviceHub.signInitialTransaction(unwrappedtx, serviceHub.myInfo.legalIdentities.first().owningKey)
//            val stx = serviceHub.addSignature(unwrappedtx)
//            counterPartySession.send(stx)

            // Sync identities to ensure we know all of the identities involved in the transaction we're about to
            // be asked to sign
            subFlow(IdentitySyncFlow.Receive(counterPartySession))

            //STEP 4: Receieve the txn and sign it
            val signTransactionFlow = object : SignTransactionFlow(counterPartySession, SignTransactionFlow.tracker()) {
                override fun checkTransaction(stx: SignedTransaction)  {
                    requireThat {
                        //TODO Validate
                    }
                }
            }

            val stx = subFlow(signTransactionFlow)
            return waitForLedgerCommit(stx.id)
        }
    }
}