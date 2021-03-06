package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.KYC
import com.termDeposits.contract.TermDeposit
import com.termDeposits.contract.TermDepositOffer
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.identity.groupPublicKeysByWellKnownParty
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.asset.Cash
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
    class Initiator(val dateData: TermDeposit.DateData, val interestPercent: Float,
    val issuinginstitute: Party, val depositAmount: Amount<Currency>, val KYCData: KYC.KYCNameData) : FlowLogic<SignedTransaction>() {//FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {//SignedTransaction {
            //STEP 1: Retrieve TD Offer and KYC data from vault
            val flow = initiateFlow(issuinginstitute)
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            //Get td offer
            val TDOffers = subFlow(OfferRetrievalFlow(issuinginstitute, interestPercent, dateData.duration))
            val TDOffer = TDOffers.first() //Doesnt matter if there is more than one offer as they will be identical offer states
            //Get KYC data
            val KYCData = subFlow(KYCRetrievalFlow(KYCData.firstName, KYCData.lastName, KYCData.accountNum))

            //STEP 2: Build Txn with TDOffer as input and TDOffer + TDState as output TODO Work in attachments
            val builder = TransactionBuilder(notary = notary)
            //Add TD Offer as input
            builder.addInputState(TDOffer)
            builder.addOutputState(TDOffer.state.copy())
            //Add KYC data as input -> for the output change the states participants to both the client and the bank node
            builder.addInputState(KYCData.first())
            builder.addOutputState(KYCData.first().state.copy(data = KYCData.first().state.data.copy(
                    banksInvolved = KYCData.first().state.data.banksInvolved.plus(issuinginstitute)))) //Same state but with a new bank involved - i.e able to view this KYC data

            //Add required commands
            builder.addCommand(Command(TermDepositOffer.Commands.CreateTD(), TDOffer.state.data.institute.owningKey))
            builder.addCommand(Command(KYC.Commands.IssueTD(), KYCData.first().state.data.owner.owningKey))
            val ptx = TermDeposit().generateIssue(builder,TDOffer, notary, depositAmount, serviceHub.myInfo.legalIdentities.first(), dateData.startDate,
                    dateData.startDate.plusMonths(dateData.duration.toLong()), KYCData.first()) //not doing this for testing purposes atm
            //Sign txn
            val stx = serviceHub.signInitialTransaction(ptx, serviceHub.myInfo.legalIdentities.first().owningKey)//+cashKeys)

            // Sync up confidential identities in the transaction with our counterparty
            subFlow(IdentitySyncFlow.Send(flow, ptx.toWireTransaction(serviceHub)))

            //STEP 3: Send to the issuing institute for verification/acceptance
            val otherPartySig = subFlow(CollectSignaturesFlow(stx, setOf(flow), CollectSignaturesFlow.tracker()))

            //STEP 5: Receieve back txn, sign and commit to ledger
            val twiceSignedTx = stx.plus(otherPartySig.sigs)
            println("TD Issued to ${stx.tx.outputStates.filterIsInstance<TermDeposit.State>().first().owner} by ${issuinginstitute.name} at $interestPercent%")
            return subFlow(FinalityFlow(twiceSignedTx, setOf(issuinginstitute) ))//+ groupPublicKeysByWellKnownParty(serviceHub,cashKeys).keys ))

        }
    }

    @CordaSerializable
    @InitiatedBy(Initiator::class)
    @StartableByRPC
    open class Acceptor(val counterPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {

            //STEP 4: Sync Identities, Receive the txn and sign it
            // Sync identities to ensure we know all of the identities involved in the transaction we're about to
            // be asked to sign
            subFlow(IdentitySyncFlow.Receive(counterPartySession))

            //Sign the txn
            val signTransactionFlow = object : SignTransactionFlow(counterPartySession, SignTransactionFlow.tracker()) {
                override fun checkTransaction(stx: SignedTransaction)  {
                    requireThat {
                        //TODO Validate
                    }
                }
            }

            //Return to the other party and wait for this state to hit the ledger
            val stx = subFlow(signTransactionFlow)
            return waitForLedgerCommit(stx.id)
        }
    }
}