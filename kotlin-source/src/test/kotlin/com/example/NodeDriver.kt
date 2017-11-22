package com.example

import com.example.flow.ExampleFlow
import com.termDeposits.flow.TermDeposit.IssueOffer
import com.termDeposits.flow.TermDeposit.IssueTD
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.finance.AMOUNT
import net.corda.finance.USD
import net.corda.node.services.FlowPermissions
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.nodeapi.User
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
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
    val parties = ArrayList<Pair<Party, CordaRPCOps>>()
    lateinit var aNode : NodeHandle
    lateinit var bNode : NodeHandle
    lateinit var cNode : NodeHandle
    val stdUser = User("user1", "test",
            permissions = TDPermissions)
    init {
        main(arrayOf())
    }

    fun main(args: Array<String>) {
        println("Simulation Main")
        // No permissions required as we are not invoking flows. //TODO Adding startNodesInProcess = true is causing crashes
        driver(isDebug = false, extraCordappPackagesToScan = listOf("com.termDeposits.contract", "termDeposits.contract", "termDepositCordapp.com.termDeposits.contract"), startNodesInProcess = true) {
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
    }

    fun allocateTDPermissions() : Set<String> = setOf(
            FlowPermissions.startFlowPermission<IssueOffer.Initiator>(),
            FlowPermissions.startFlowPermission<IssueTD.Initiator>(),
            FlowPermissions.startFlowPermission<IssueTD.Acceptor>()
    )

    //Flow test suite for TD flows
    fun runSimulation() {
        println("Simulations")
        //Send an offer to parties for a TD
        sendTDOffers(parties[0].second, parties[1].second, LocalDateTime.MIN, LocalDateTime.MAX, 3.4f)
        sendTDOffers(parties[0].second, parties[1].second, LocalDateTime.MIN, LocalDateTime.MAX, 3.6f)
        sendTDOffers(parties[0].second, parties[1].second, LocalDateTime.MIN, LocalDateTime.MAX, 3.8f)
        sendTDOffers(parties[0].second, parties[1].second, LocalDateTime.MIN, LocalDateTime.MAX, 2.4f)
        sendTDOffers(parties[1].second, parties[0].second, LocalDateTime.MIN, LocalDateTime.MAX, 5.7f)
        sendTDOffers(parties[1].second, parties[0].second, LocalDateTime.MIN, LocalDateTime.MAX, 6.9f)

        //Accept this offer
        RequestTD(parties[1].second, parties[0].second, LocalDateTime.MIN, LocalDateTime.MAX, 3.4f)
        //RequestTD(parties[0].second, parties[1].second, LocalDateTime.MIN, LocalDateTime.MAX, 3.4f)
    }

    fun sendTDOffers(me : CordaRPCOps, receiver: CordaRPCOps, startDate: LocalDateTime, endDate: LocalDateTime,
                     interestPercent: Float) {
        //me.startFlow { IssueOffer.Initiator(startDate, endDate, interestPercent, me.nodeInfo().legalIdentities.first(), listOf(receiver.nodeInfo().legalIdentities.first())) }
        me.startFlow(IssueOffer::Initiator, startDate, endDate, interestPercent, me.nodeInfo().legalIdentities.first(), receiver.nodeInfo().legalIdentities.first())
        println("TD Offers Issued")
    }

    fun RequestTD(me : CordaRPCOps, issuer: CordaRPCOps, startDate: LocalDateTime, endDate: LocalDateTime,
                  interestPercent: Float) {
        me.startFlow(IssueTD::Initiator, startDate, endDate, interestPercent, issuer.nodeInfo().legalIdentities.first(), Amount<Currency>(300, USD))
        println("TD Requested")
    }

}