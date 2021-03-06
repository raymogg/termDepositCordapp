package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.TermDepositOffer
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.LocalDateTime


/** Flow for issuing a TD Offer. This flow would be initiated by a institue offering term deposits (eg banks) then
 * This offer is sent to all parties (supplied to the flow). These parties then simply sign the offer in order to store
 * it in their vault. Signing the offer does not lock the clients in to anything. This offer is able to be redeemed
 * for an actual term deposit at the offers rates with the issuing institue (provided the current time is between the
 * start and end date
 */

@CordaSerializable
object IssueOffer {

    @CordaSerializable
    @StartableByRPC
    @InitiatingFlow
    open class Initiator(val dateData: TermDepositOffer.offerDateData, val interestPercent: Float,
                    val issuingInstitue: Party, val otherParty: Party, val attachmentID: SecureHash, val earlyTerms: TermDepositOffer.earlyTerms) : FlowLogic<SignedTransaction>() {//FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            //STEP 1: Create TDOffer and build txn - including adding the term deposit terms as an attachment
            val notary = serviceHub.networkMapCache.notaryIdentities.single()
            val tx = TransactionBuilder()
            val partTx = TermDepositOffer().generateIssue(tx, dateData, interestPercent, issuingInstitue, notary, otherParty, earlyTerms)
            val flowSession = initiateFlow(otherParty)
            //Attach the term deposit terms
            partTx.addAttachment(attachmentID)

            //STEP 2: Send the Transaction to the other party
            flowSession.send(Pair(partTx, attachmentID))

            //STEP 5: Receieve back txn
            val stx = flowSession.receive<SignedTransaction>().unwrap { it }

            //STEP 6: Notarise and finalize this txn
            val unnotarisedTX = serviceHub.addSignature(stx)
            subFlow(ResolveTransactionsFlow(unnotarisedTX, flowSession)) //This is required for notary validation to pass
            println("Offer Issued to $otherParty for a TD at $interestPercent% by ${issuingInstitue.name}")
            return subFlow(FinalityFlow(unnotarisedTX, setOf(otherParty))) //This parties vault will receieve the txn data and state in their vault.
        }
    }

    @CordaSerializable
    @StartableByRPC
    @InitiatedBy(IssueOffer.Initiator::class)
    open class Reciever(val flow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            //STEP 3: Receive txn from issuing institute
            val tx = flow.receive<Pair<TransactionBuilder, SecureHash>>().unwrap {
                requireThat {
                    //"Contract hash matches" using (it.first.attachments().first() == getFileHash(it.second)) //TODO Instead of receiving this, check with our contracts - if we dont have this hash need to download
                }
                it
            }

            //STEP 4: Sign and send back this offer
            val stx = serviceHub.signInitialTransaction(tx.first)
            flow.send(stx)
            return waitForLedgerCommit(stx.id)

        }
    }

    /** Helper function to get the filehash of a particular file (i.e external legal documents */
    fun getFileHash(filename: String) : SecureHash {
        val loader = Thread.currentThread().contextClassLoader.getResourceAsStream(filename)
        val md = MessageDigest.getInstance("MD5")
        val digestStream = DigestInputStream(loader, md)
        val bytes = md.digest()
        return SecureHash.sha256(File.pathSeparator)
    }
}