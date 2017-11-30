package com.example

import com.termDeposits.contract.TermDeposit
import com.termDeposits.flow.TermDeposit.*
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.USD
import net.corda.finance.flows.CashExitFlow
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.services.FlowPermissions
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.nodeapi.User
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

/**
 * This file is exclusively for being able to run your nodes through an IDE (as opposed to running deployNodes)
 * Do not use in a production environment.
 *
 * To debug your CorDapp:
 *
 * 1. Firstly, run the "Run Example CorDapp" run configuration.
 * 2. Wait for all the nodes to start.
 * 3. Note the debug ports which should be output to the console for each node. They typically start at 5006, 5007,
 *    5008. The "Debug CorDapp" configuration runs with port 5007, which should be "NodeB". In any case, double check
 *    the console output to be sure.
 * 4. Set your breakpoints in your CorDapp code.
 * 5. Run the "Debug CorDapp" remote debug run configuration.
 */

fun main(args: Array<String>) {
    println("Start")
    Simulation("Place Options here")
}

class Simulation(options: String) {
    val TDPermissions = allocateTDPermissions()
    val cashPermissions = allocateCashPermissions()
    val parties = ArrayList<Pair<Party, CordaRPCOps>>()
    val cashIssuers = ArrayList<Pair<Party, CordaRPCOps>>()
    lateinit var aNode : NodeHandle
    lateinit var bNode : NodeHandle
    lateinit var cNode : NodeHandle
    val stdUser = User("user1", "test",
            permissions = TDPermissions+cashPermissions)
    val cashUser = User("user1", "test",
            permissions = TDPermissions+cashPermissions)
    init {
        main(arrayOf())
    }

    fun main(args: Array<String>) {
        println("Simulation Main")
        // No permissions required as we are not invoking flows.
        driver(isDebug = false, extraCordappPackagesToScan = listOf("com.termDeposits.contract", "termDeposits.contract", "termDepositCordapp.com.termDeposits.contract",
                "com.termDeposits.flow", "net.corda.finance"), startNodesInProcess = false) {
            startNode(providedName = CordaX500Name("Controller", "London", "GB"), advertisedServices = setOf(ServiceInfo(ValidatingNotaryService.type)))
            val (nodeA, nodeB, nodeC) = listOf(
                    startNode(providedName = CordaX500Name("PartyA", "London", "GB"), rpcUsers = listOf(stdUser)),
                    startNode(providedName = CordaX500Name("PartyB", "New York", "US"), rpcUsers = listOf(stdUser)),
                    startNode(providedName = CordaX500Name("PartyC", "Paris", "FR"), rpcUsers = listOf(stdUser))).map { it.getOrThrow() }

            aNode = nodeA
            bNode = nodeB
            cNode = nodeC

            /*startWebserver(aNode)
            startWebserver(bNode)
            startWebserver(cNode)*/


            setup_nodes()
            runSimulation()
            waitForAllNodesToFinish()
        }
    }


    fun setup_nodes() {
        val aClient = aNode.rpcClientToNode()
        val aRPC = aClient.start(stdUser.username, stdUser.password).proxy

        val bClient = bNode.rpcClientToNode()
        val bRPC = bClient.start(stdUser.username, stdUser.password).proxy

        val cClient = cNode.rpcClientToNode()
        val cRPC = cClient.start(stdUser.username, stdUser.password).proxy

        parties.addAll(listOf(
                aRPC.nodeInfo().legalIdentities.first() to aRPC,
                bRPC.nodeInfo().legalIdentities.first() to bRPC,
                cRPC.nodeInfo().legalIdentities.first() to cRPC
        ))

        cashIssuers.add(cRPC.nodeInfo().legalIdentities.first() to cRPC)
        arrayOf(aNode, bNode, cNode).forEach {
            println("${it.nodeInfo.legalIdentities.first()} started on ${it.configuration.rpcAddress}")
        }
    }

    fun allocateTDPermissions() : Set<String> = setOf(
            FlowPermissions.startFlowPermission<IssueOffer.Initiator>(),
            FlowPermissions.startFlowPermission<IssueTD.Initiator>(),
            FlowPermissions.startFlowPermission<IssueTD.Acceptor>(),
            FlowPermissions.startFlowPermission<ActivateTD.Activator>(),
            FlowPermissions.startFlowPermission<ActivateTD.Acceptor>(),
            FlowPermissions.startFlowPermission<IssueOffer.Reciever>(),
            FlowPermissions.startFlowPermission<RedeemTD.RedemptionInitiator>(),
            FlowPermissions.startFlowPermission<RedeemTD.RedemptionAcceptor>(),
            FlowPermissions.startFlowPermission<RolloverTD.RolloverAcceptor>(),
            FlowPermissions.startFlowPermission<RolloverTD.RolloverInitiator>()
            )

    fun allocateCashPermissions() : Set<String> = setOf(
            FlowPermissions.startFlowPermission<CashIssueFlow>(),
            FlowPermissions.startFlowPermission<CashPaymentFlow>(),
            FlowPermissions.startFlowPermission<CashExitFlow>(),
            FlowPermissions.startFlowPermission<CashIssueAndPaymentFlow>()
    )

    //Flow test suite for TD flows
    fun runSimulation() {
        //Issue some cash to each party
        parties.forEach {
            issueCash(it.second, it.second.notaryIdentities().first())
        }

        println("Simulations")
        //Send an offer to parties for a TD - 0 is the issuing institue, 1 is receiever
        sendTDOffers(parties[0].second, parties[1].second, LocalDateTime.MIN, LocalDateTime.MAX, 3.4f)
        //sendTDOffers(parties[0].second, parties[1].second, LocalDateTime.MIN, LocalDateTime.MAX, 3.8f)
        sendTDOffers(parties[1].second, parties[0].second, LocalDateTime.MIN, LocalDateTime.MAX, 3.2f)
        //Accept this offer
        RequestTD(parties[1].second, parties[0].second, LocalDateTime.MIN, LocalDateTime.MAX, 3.4f, Amount<Currency>(300000, USD))
        RequestTD(parties[0].second, parties[1].second, LocalDateTime.MIN, LocalDateTime.MAX, 3.2f, Amount<Currency>(500000, USD))
        //Activate this TD - Done once the issuing party receieves its cash through regular bank transfer
        Activate(parties[0].second, parties[1].second, LocalDateTime.MIN, LocalDateTime.MAX, 3.4f, Amount<Currency>(300000, USD))
        Activate(parties[1].second, parties[0].second, LocalDateTime.MIN, LocalDateTime.MAX, 3.2f, Amount<Currency>(500000, USD))
        //Redeem this TD - removed time constraints on this for now so it works
        //Redeem(parties[1].second, parties[0].second, LocalDateTime.MIN, LocalDateTime.MAX, 3.4f, Amount<Currency>(300000, USD))
        Redeem(parties[0].second, parties[1].second, LocalDateTime.MIN, LocalDateTime.MAX, 3.2f, Amount<Currency>(500000, USD))
        Rollover(parties[1].second, parties[0].second, LocalDateTime.MIN, LocalDateTime.MAX, LocalDateTime.MIN,
                LocalDateTime.MAX, 3.4f, Amount<Currency>(300000, USD), true)
        Redeem(parties[1].second, parties[0].second, LocalDateTime.MIN, LocalDateTime.MAX, 3.4f, Amount<Currency>(310200, USD)) //3102 USD due to the rollover interest in previous txn
    }

    fun sendTDOffers(me : CordaRPCOps, receiver: CordaRPCOps, startDate: LocalDateTime, endDate: LocalDateTime,
                     interestPercent: Float) {
        val returnVal = me.startFlow(IssueOffer::Initiator, startDate, endDate, interestPercent, me.nodeInfo().legalIdentities.first(), receiver.nodeInfo().legalIdentities.first()).returnValue.getOrThrow()
        //println("TD Offers Issued")
    }

    fun RequestTD(me : CordaRPCOps, issuer: CordaRPCOps, startDate: LocalDateTime, endDate: LocalDateTime,
                  interestPercent: Float, depositAmount: Amount<Currency>) {
        //Request a TD at $300 USD
        val returnVal = me.startFlow(IssueTD::Initiator, startDate, endDate, interestPercent, issuer.nodeInfo().legalIdentities.first(), depositAmount).returnValue.getOrThrow()
        //println("TD Requested")
    }

    fun Activate(me : CordaRPCOps, client : CordaRPCOps, startDate: LocalDateTime, endDate: LocalDateTime, interestPercent: Float, depositAmount: Amount<Currency>) {
        val returnVal = me.startFlow(ActivateTD::Activator, startDate, endDate, interestPercent, me.nodeInfo().legalIdentities.first(), client.nodeInfo().legalIdentities.first(), depositAmount).returnValue.getOrThrow()
        println("TD Activated")
    }

    private fun issueCash(recipient : CordaRPCOps, notaryNode : Party) {
        val rand = Random()
        val dollaryDoos = BigDecimal((rand.nextInt(100 + 1 - 1) + 1) * 1000000)     // $1,000,000 to $100,000,000
        val amount = Amount.fromDecimal(dollaryDoos, USD)
        //Self issue cash now - i.e not sent from central bank
        //recipient.startTrackedFlow(::CashIssueFlow, amount, OpaqueBytes.of(1), notaryNode).returnValue.getOrThrow()
        cashIssuers.first().second.startFlow(::CashIssueAndPaymentFlow, amount,OpaqueBytes.of(1), recipient.nodeInfo().legalIdentities.first()
                ,false, notaryNode )
        println("Cash Issue: ${amount} units of $USD issued to ${recipient.nodeInfo().legalIdentities.first()}")
    }

    fun Redeem(me : CordaRPCOps, issuer: CordaRPCOps, startDate: LocalDateTime, endDate: LocalDateTime,
               interestPercent: Float, depositAmount: Amount<Currency>) {
        val returnVal = me.startFlow(RedeemTD::RedemptionInitiator, startDate, endDate, interestPercent, issuer.nodeInfo().legalIdentities.first(), depositAmount).returnValue.getOrThrow()
    }

    fun Rollover(me: CordaRPCOps, issuer: CordaRPCOps, startDate: LocalDateTime, endDate: LocalDateTime, newStartDate: LocalDateTime,
                 newEndDate: LocalDateTime, interestPercent: Float, depositAmount: Amount<Currency>, withInterest: Boolean) {
//        val returnVal = me.startFlow(RolloverTD::RolloverInitiator, startDate, endDate, newStartDate, newEndDate, interestPercent, issuer.nodeInfo().legalIdentities.first(),
//                depositAmount, withInterest).returnValue.getOrThow()
        val rolloverTerms = TermDeposit.RolloverTerms(newStartDate, newEndDate, withInterest)
        val returnVal = me.startFlow(RolloverTD::RolloverInitiator, startDate, endDate, interestPercent, issuer.nodeInfo().legalIdentities.first(),
                depositAmount, rolloverTerms).returnValue.getOrThrow()
    }


    /** ERROR NOTES/LOG
     *
     * 1. Redeem flow has an issue with adding cash states if offers are requested in two directions (i.e A starts a TD with B, B starts a TD with A). Random cash output stsates are added to the txn
     *    Question has been posted on SO.
     *
     *
     *
     */


}