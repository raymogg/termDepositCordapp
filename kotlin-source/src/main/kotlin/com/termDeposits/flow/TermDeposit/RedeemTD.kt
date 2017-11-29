package com.termDeposits.flow.TermDeposit

import co.paralleluniverse.fibers.Suspendable
import com.termDeposits.contract.TermDeposit
import net.corda.core.contracts.Amount
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.finance.USD
import net.corda.finance.contracts.asset.Cash
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/** Flow for redeeming a TD.
 */


object RedeemTD {

    @InitiatingFlow
    @StartableByRPC
    @CordaSerializable
    class RedemptionInitiator(val startDate: LocalDateTime, val endDate: LocalDateTime, val interestPercent: Float,
    val issuingInstitue: Party, val depositAmount: Amount<Currency>) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            //STEP 1: Gather TD State from the vault - this will only retrieve states that are past the end date and can be redeemed
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val TermDeposits = subFlow(TDRetreivalFlow(startDate,endDate, issuingInstitue, interestPercent, depositAmount, TermDeposit.internalState.exited))
            println(TermDeposits.first())

            //STEP 2: Build Txn
            val builder = TransactionBuilder(notary)
            val ptx = TermDeposit().genereateRedeem(builder, TermDeposits.first())

            //STEP 3: Send to other party to sign
            val flowSession = initiateFlow(issuingInstitue)
            val stx = flowSession.sendAndReceive<SignedTransaction>(Pair(ptx, TermDeposits.first().state.data)).unwrap { it }

            //STEP 6: Receieve back the transaction, commit to ledger
            subFlow(ResolveTransactionsFlow(stx, flowSession))
            val unnotarisedTx = serviceHub.addSignature(stx, serviceHub.myInfo.legalIdentities.first().owningKey)
            println("TD Redeemed: ${TermDeposits.first().state.data.toString()}")
            return subFlow(FinalityFlow(unnotarisedTx, setOf(issuingInstitue)))
        }
    }

    @CordaSerializable
    @InitiatedBy(RedemptionInitiator::class)
    class RedemptionAcceptor(val flow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            //STEP 4: Recieve the txn for the redemption, ensure end date has past
            val ptx = flow.receive<Pair<TransactionBuilder, TermDeposit.State>>().unwrap {
                requireThat {
                    it.second.institue == serviceHub.myInfo.legalIdentities.first()
//                    it.second.endDate.isBefore(LocalDateTime.now())
//                    println(it.first.inputStates().first().index)
//                    println(it.second.hashCode())
//                    it.first.inputStates().first().index == it.second.hashCode()
                }
                it
            }

            //STEP 5: Add cash as required
            val (ptx2, CashSigningKeys) = Cash.generateSpend(serviceHub, ptx.first, Amount((ptx.second.depositAmount.quantity * (100+ptx.second.interestPercent)/100).toLong(), USD), ptx.second.owner )
            println("Cash added ${Amount((ptx.second.depositAmount.quantity * (100+ptx.second.interestPercent)/100).toLong(), USD)}")
            //STEP 5: Sign and Send back the txn
            val stx = serviceHub.signInitialTransaction(ptx2, CashSigningKeys)
            flow.send(stx)
            return waitForLedgerCommit(stx.id)
        }
    }
}