package com.example

import com.termDeposits.contract.KYC
import com.termDeposits.contract.TermDeposit
import com.termDeposits.contract.TermDepositOffer
import com.termDeposits.flow.TermDeposit.*
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.USD
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashExitFlow
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.services.Permissions
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** This file is for running both tests and for starting up the nodes to run a simulation of the network (which you can
 * then interact with using the GUI or web front end)
 *
 * To switch between running tests and running the simulation, change the line commented out on lines 104 and 105.
*/

fun main(args: Array<String>) {
    println("Start")
    Simulation("Place Options here")
}

class Simulation(options: String) {
    val TDPermissions = allocateTDPermissions()
    val bankPermissions = allocateBankPermissions()
    val cashPermissions = allocateCashPermissions()
    val parties = ArrayList<Pair<Party, CordaRPCOps>>()
    val banks = ArrayList<Pair<Party, CordaRPCOps>>()
    val cashIssuers = ArrayList<Pair<Party, CordaRPCOps>>()
    lateinit var aNode : NodeHandle
    lateinit var bNode : NodeHandle
    lateinit var cNode : NodeHandle
    lateinit var bankA : NodeHandle
    lateinit var bankB : NodeHandle
    lateinit var centralBank : NodeHandle
    val stdUser = User("user1", "test",
            permissions = TDPermissions+cashPermissions)
    val cashIssuer = User("user1", "test",
            permissions = TDPermissions+cashPermissions+bankPermissions)
    val bank = User("user1", "test",
            permissions = TDPermissions+bankPermissions)
    init {
        main(arrayOf())
    }

    /** Sets up the node driver and the nodes needed. Starts either running the simulation or starts running tests
     * depending on which line is commented out at lines 104 and 105.
     */
    fun main(args: Array<String>) {
        println("Simulation Main")
        //Startup the node driver
        val parameters = DriverParameters(isDebug = false, extraCordappPackagesToScan = listOf("com.termDeposits.contract", "termDeposits.contract", "termDepositCordapp.com.termDeposits.contract",
                "com.termDeposits.flow", "net.corda.finance"), startNodesInProcess = false, waitForAllNodesToFinish = true,
                notarySpecs = listOf(NotarySpec(CordaX500Name("Controller", "London", "GB"))))
        driver(parameters) {
            //Startup the nodes
            val cBank = startNode(providedName = CordaX500Name("CentralBank", "Brisbane", "AU"), rpcUsers = listOf(cashIssuer)).getOrThrow()
            val (nodeA, nodeB, nodeC, aBank, bBank) = listOf(
                    startNode(providedName = CordaX500Name("AMM", "London", "GB"), rpcUsers = listOf(stdUser)),
                    startNode(providedName = CordaX500Name("ClientA", "New York", "US"), rpcUsers = listOf(stdUser)),
                    startNode(providedName = CordaX500Name("ClientB", "Paris", "FR"), rpcUsers = listOf(stdUser)),
                    startNode(providedName = CordaX500Name("BankA", "Munich", "DE"), rpcUsers = listOf(bank)),
                    startNode(providedName = CordaX500Name("BankB", "Singapore", "SG"), rpcUsers = listOf(bank))).map { it.getOrThrow() }

            aNode = nodeA
            bNode = nodeB
            cNode = nodeC
            bankA = aBank
            bankB = bBank
            centralBank = cBank

            //Startup a webserver for each node -> this will allow us to serve the frontend found in resources/testWeb on every nodes server
            val serverA = startWebserver(aNode)
            val serverB = startWebserver(bNode)
            val serverC = startWebserver(cNode)
            val serverBA = startWebserver(bankA)
            val serverBB = startWebserver(bankB)
            println("Webservers Started: ${serverA.get().listenAddress} ${serverB.get().listenAddress} ${serverC.get().listenAddress} " +
                    "${serverBA.get().listenAddress} ${serverBB.get().listenAddress}")

            setup_nodes()
            //Comment out one of runSimulation() and runTests() -> only one of these should be run at once
            runSimulation()
            //runTests()
            //waitForAllNodesToFinish()
        }
    }

    /**Setups the nodes to allow this class access to the nodes rpc methods. lets us invoke flows as each node */
    fun setup_nodes() {
        val aClient = aNode.rpc
        val aRPC = aClient //.start(stdUser.username, stdUser.password).proxy

        val bClient = bNode.rpc
        val bRPC = bClient //.start(stdUser.username, stdUser.password).proxy

        val cClient = cNode.rpc
        val cRPC = cClient //.start(stdUser.username, stdUser.password).proxy

        val aBankClient = bankA.rpc
        val abRPC = aBankClient //.start(stdUser.username, stdUser.password).proxy

        val bBankClient = bankB.rpc
        val bbRPC = bBankClient //.start(stdUser.username, stdUser.password).proxy

        val centralBankClient = centralBank.rpc
        val cbrpc = centralBankClient //.start(stdUser.username, stdUser.password).proxy

        //Keep track of these so they can be accessed from anywhere
        parties.addAll(listOf(
                aRPC.nodeInfo().legalIdentities.first() to aRPC,
                bRPC.nodeInfo().legalIdentities.first() to bRPC,
                cRPC.nodeInfo().legalIdentities.first() to cRPC
        ))

        banks.addAll(listOf(
                abRPC.nodeInfo().legalIdentities.first() to abRPC,
                bbRPC.nodeInfo().legalIdentities.first() to bbRPC
        ))

        cashIssuers.add(cbrpc.nodeInfo().legalIdentities.first() to cbrpc
        )
        arrayOf(aNode, bNode, cNode, bankA, bankB).forEach {
            println("${it.nodeInfo.legalIdentities.first()} started on ${it.rpcAddress}")
        }
    }

    /** Function to allocate permissions needed for all term deposit and KYC functionality */
    fun allocateTDPermissions() : Set<String> = setOf(
            //FlowPermissions.startFlowPermission<IssueOffer.Initiator>(),
            Permissions.startFlow<IssueTD.Initiator>(),
            Permissions.startFlow<IssueTD.Acceptor>(),
            //FlowPermissions.startFlowPermission<ActivateTD.Activator>(),
            Permissions.startFlow<ActivateTD.Acceptor>(),
            Permissions.startFlow<IssueOffer.Reciever>(),
            Permissions.startFlow<RedeemTD.RedemptionInitiator>(),
            Permissions.startFlow<RedeemTD.RedemptionAcceptor>(),
            //FlowPermissions.startFlowPermission<RolloverTD.RolloverAcceptor>(),
            Permissions.startFlow<RolloverTD.RolloverInitiator>(),
            Permissions.startFlow<PromptActivate.Prompter>(),
            Permissions.startFlow<PromptActivate.Acceptor>(),
            Permissions.startFlow<CreateKYC.Creator>(),
            Permissions.startFlow<KYCRetrievalFlow>(),
            Permissions.startFlow<KYCRetrievalFlowID>(),
            Permissions.startFlow<UpdateKYC.Updator>(),
            Permissions.invokeRpc("uploadAttachment"),
            Permissions.invokeRpc("attachmentExists")
    )

    /** Function to allocate permissions required for banks in regard to term deposits */
    fun allocateBankPermissions() : Set<String> = setOf(
            Permissions.startFlow<IssueOffer.Initiator>(),
            Permissions.startFlow<ActivateTD.Activator>(),
            Permissions.startFlow<RolloverTD.RolloverAcceptor>(),
            Permissions.startFlow<RedeemTD.RedemptionAcceptor>()
    )

    /** Function to allocate cash permissions */
    fun allocateCashPermissions() : Set<String> = setOf(
            //Permissions.startFlow<CashIssueFlow>(),
            Permissions.startFlow<CashExitFlow>(),
            Permissions.startFlow<CashPaymentFlow>(),
            Permissions.startFlow<CashIssueAndPaymentFlow>()
    )


    /** TESTING Functions for Term Deposit Flows */

    /** Test attempting to create a TD using an expired offer state - should throw an error */
    @Test
    fun expiredTDOffer() {
        sendTDOffers(banks[0].second, parties[0].second, TermDepositOffer.offerDateData(LocalDateTime.now(), 6), 3.4f, TermDepositOffer.earlyTerms(true))
        CreateKYC(parties[0].second, "Bob", "Smith", "1234")
        Thread.sleep(1000)
        //Should throw an error due to the offer already expiring
        var error = false
        try {
            RequestTD(parties[0].second, banks[0].second, LocalDateTime.now(),
                    3.4f, Amount(30000, USD), "Bob", "Smith", "1234", 6)
        } catch (e: Exception) {
            error = true
            println("Test Passed - Attempting to use an expired offer failed")
        }
        assertTrue(error)
    }

    /** Testing attempting to exit a td early -> should exit early with a interest amount proportionally less than the full amount */
    @Test
    fun exitNonExpiredTD() {
        var error: Boolean
        sendTDOffers(banks[0].second, parties[0].second, TermDepositOffer.offerDateData(LocalDateTime.MAX, 6), 3.4f, TermDepositOffer.earlyTerms(true))
        val startTime = LocalDateTime.now()
        RequestTD(parties[0].second, banks[0].second, startTime, 3.4f, Amount(30000, USD), "Bob", "Smith", "1234", 6)
        Activate(banks[0].second, parties[0].second, startTime, 3.4f, Amount(30000, USD), 6,
                "Bob", "Smith", "1234")
        val output = Redeem(parties[0].second, banks[0].second, startTime, 3.4f, Amount(30000, USD), 6,
                "Bob", "Smith", "1234")
        val returnedCash: List<Cash.State> = output.tx.outputs.map { it.data }.filterIsInstance<Cash.State>() //{it.data is Cash.State && it.contract == Cash.PROGRAM_ID && (it.data as Cash.State).owner == parties[0].first }
        returnedCash.filter { it.owner == parties[0].first }
        try {
            //Because only a few seconds has past of this loan, the proportional value paid back is 0% interest (1sec / 6 months ~ 0)
            require(returnedCash.first().amount.quantity == Amount(30000, USD).quantity)
            println("Test Passed - Correct Cash returned for an early exit")
            error = false
        } catch (e: Exception) {
            error = true
            println("Test Failed - incorrect amount of cash returned for early exit: ${returnedCash.first().amount.quantity} != ${Amount(30000, USD).quantity}")
        }

        assertFalse(error)
    }

    /** Testing an early rollover with interest -> should rollover early with a interest amount proportionally less than the full amount (in test cases 0 interest) */
    @Test
    fun rolloverWithInterestNonExpiredTD() {
        var error: Boolean
        sendTDOffers(banks[0].second, parties[0].second, TermDepositOffer.offerDateData(LocalDateTime.MAX, 8), 3.4f, TermDepositOffer.earlyTerms(true))
        val startTime = LocalDateTime.now().minusMonths(4) //Loan started 4 months ago
        RequestTD(parties[0].second, banks[0].second, startTime, 3.4f, Amount(30000, USD), "Bob", "Smith", "1234",8)
        Activate(banks[0].second, parties[0].second, startTime, 3.4f, Amount(30000, USD),8,
                "Bob", "Smith", "1234")
        val output = Rollover(parties[0].second, banks[0].second, startTime,
                3.4f, Amount(30000, USD), true, 8,
                "Bob", "Smith", "1234", 3.4f, banks[0].first, 6)
        val outputLoan: List<TermDeposit.State> = output.tx.outputs.map { it.data }.filterIsInstance<TermDeposit.State>() //{it.data is Cash.State && it.contract == Cash.PROGRAM_ID && (it.data as Cash.State).owner == parties[0].first }
        outputLoan.filter { it.owner == parties[0].first }
        try {
            //Because half the loan time has passed, the proportional value rolled oer is 50% interest
            require(outputLoan.first().depositAmount.quantity == Amount((30000 * (100+(3.4f*0.5f))/100).toLong(), USD).quantity)
            println("Test Passed - Correct loan value for an early rollover with interest")
            error = false
        } catch (e: Exception) {
            error = true
            println("Test Failed - incorrect amount of cash returned for early rollover with interest: ${outputLoan.first().depositAmount.quantity} != ${Amount(30000, USD).quantity}")
        }

        assertFalse(error)
    }

    /** Testing an early rollover without interest -> should rollover early with a interest amount proportionally less than the full amount (in test cases 0 interest) */
    @Test
    fun rolloverWOInterestNonExpiredTD() {
        var error: Boolean
        sendTDOffers(banks[0].second, parties[0].second, TermDepositOffer.offerDateData(LocalDateTime.MAX, 12), 3.4f, TermDepositOffer.earlyTerms(true))
        val startTime = LocalDateTime.now().minusMonths(6) //Loan started 6 months ago
        RequestTD(parties[0].second, banks[0].second, startTime, 3.4f, Amount(30000, USD), "Bob", "Smith", "1234",12)
        Activate(banks[0].second, parties[0].second, startTime, 3.4f, Amount(30000, USD), 12,
                "Bob", "Smith", "1234")
        val output = Rollover(parties[0].second, banks[0].second, startTime,
                3.4f, Amount(30000, USD), false, 6,
                "Bob", "Smith", "1234", 3.4f, banks[0].first, 6)
        val outputLoan: List<TermDeposit.State> = output.tx.outputs.map { it.data }.filterIsInstance<TermDeposit.State>() //{it.data is Cash.State && it.contract == Cash.PROGRAM_ID && (it.data as Cash.State).owner == parties[0].first }
        outputLoan.filter { it.owner == parties[0].first }
        val outputCash: List<Cash.State> = output.tx.outputs.map { it.data }.filterIsInstance<Cash.State>()
        outputCash.filter { it.owner == parties[0].first }
        try {
            //Because only a few seconds has past of this loan, the proportional value rolled oer is 0% interest (1sec / 6 months ~ 0)
            require(outputLoan.first().depositAmount.quantity == Amount(30000, USD).quantity)
            require(outputCash.first().amount.quantity == Amount((30000 * ((3.4f*0.5f))/100).toLong(), USD).quantity)
            println("Test Passed - Correct loan value for an early rollover without interest")
            error = false
        } catch (e: Exception) {
            error = true
            println("Test Failed - incorrect amount of cash returned for early rollover without interest: ${outputLoan.first().depositAmount.quantity} != ${Amount(30000, USD).quantity} OR" +
                    "${outputCash.first().amount.quantity} != ${Amount((30000 * ((3.4f*0.5f))/100).toLong(), USD).quantity}")
        }
        assertFalse(error)
    }

    /** Testing exiting a TD that has expired - ensures the returned cash is as expected */
    @Test
    fun exitExpiredTD() {
        var error: Boolean
        val startTime = LocalDateTime.now().minusMonths(8) //set the deposit to have started 8 months ago (hence it is passed expiry)
        RequestTD(parties[0].second, banks[0].second, startTime, 3.4f, Amount(30000, USD), "Bob", "Smith", "1234", 6)
        Activate(banks[0].second, parties[0].second, startTime, 3.4f, Amount(30000, USD), 6,
                "Bob", "Smith", "1234")
        val output = Redeem(parties[0].second, banks[0].second, startTime, 3.4f, Amount(30000, USD), 6,
                "Bob", "Smith", "1234")
        val returnedCash: List<Cash.State> = output.tx.outputs.map { it.data }.filterIsInstance<Cash.State>() //{it.data is Cash.State && it.contract == Cash.PROGRAM_ID && (it.data as Cash.State).owner == parties[0].first }
        returnedCash.filter { it.owner == parties[0].first }
        try {
            //Because only a few seconds has past of this loan, the proportional value paid back is 0% interest (1sec / 6 months ~ 0)
            require(returnedCash.first().amount.quantity == Amount((30000 * ((100+3.4f))/100).toLong(), USD).quantity)
            println("Test Passed - Correct Cash returned for a exit")
            error = false
        } catch (e: Exception) {
            error = true
            println("Test Failed - incorrect amount of cash returned for early exit: ${returnedCash.first().amount.quantity} != ${Amount((30000 * ((3.4f))/100).toLong(), USD).quantity}")
        }

        assertFalse(error)
    }

    /** Testing rolling over (with interest) a TD that has expired - ensures the output loan is as expected */
    @Test
    fun rolloverWithInterestExpiredTD() {
        var error: Boolean
        sendTDOffers(banks[0].second, parties[0].second, TermDepositOffer.offerDateData(LocalDateTime.MAX, 8), 3.4f, TermDepositOffer.earlyTerms(true))
        val startTime = LocalDateTime.now().minusMonths(10) //Loan started 4 months ago
        RequestTD(parties[0].second, banks[0].second, startTime, 3.4f, Amount(30000, USD), "Bob", "Smith", "1234", 8)
        Activate(banks[0].second, parties[0].second, startTime, 3.4f, Amount(30000, USD), 8,
                "Bob", "Smith", "1234")
        val output = Rollover(parties[0].second, banks[0].second, startTime,
                3.4f, Amount(30000, USD), true, 8,
                "Bob", "Smith", "1234", 3.4f, banks[0].first, 6)
        val outputLoan: List<TermDeposit.State> = output.tx.outputs.map { it.data }.filterIsInstance<TermDeposit.State>()
        outputLoan.filter { it.owner == parties[0].first }
        try {
            //Because half the loan time has passed, the proportional value rolled oer is 50% interest
            require(outputLoan.first().depositAmount.quantity == Amount((30000 * (100 + (3.4f)) / 100).toLong(), USD).quantity)
            println("Test Passed - Correct loan value for a rollover with interest")
            error = false
        } catch (e: Exception) {
            error = true
            println("Test Failed - incorrect amount of cash returned for early rollover with interest: ${outputLoan.first().depositAmount.quantity} != ${Amount((30000 * (100 + (3.4f)) / 100).toLong(), USD).quantity}")
        }

        assertFalse(error)
    }

    /** Testing rolling over (without interest) a TD that has expired - ensures the output loan is as expected */
    @Test
    fun rolloverWOInterestExpiredTD() {
        var error: Boolean
        sendTDOffers(banks[0].second, parties[0].second, TermDepositOffer.offerDateData(LocalDateTime.MAX, 12), 3.4f, TermDepositOffer.earlyTerms(true))
        val startTime = LocalDateTime.now().minusMonths(12) //Loan started 6 months ago
        RequestTD(parties[0].second, banks[0].second, startTime, 3.4f, Amount(30000, USD), "Bob", "Smith", "1234",12)
        Activate(banks[0].second, parties[0].second, startTime, 3.4f, Amount(30000, USD), 12,
                "Bob", "Smith", "1234")
        val output = Rollover(parties[0].second, banks[0].second, startTime,
                3.4f, Amount(30000, USD), false, 6,
                "Bob", "Smith", "1234", 3.4f, banks[0].first, 6)
        val outputLoan: List<TermDeposit.State> = output.tx.outputs.map { it.data }.filterIsInstance<TermDeposit.State>() //{it.data is Cash.State && it.contract == Cash.PROGRAM_ID && (it.data as Cash.State).owner == parties[0].first }
        outputLoan.filter { it.owner == parties[0].first }
        val outputCash: List<Cash.State> = output.tx.outputs.map { it.data }.filterIsInstance<Cash.State>()
        outputCash.filter { it.owner == parties[0].first }
        try {
            //Because only a few seconds has past of this loan, the proportional value rolled oer is 0% interest (1sec / 6 months ~ 0)
            require(outputLoan.first().depositAmount.quantity == Amount(30000, USD).quantity)
            require(outputCash.first().amount.quantity == Amount((30000 * ((3.4f))/100).toLong(), USD).quantity)
            println("Test Passed - Correct loan value for a rollover without interest")
            error = false
        } catch (e: Exception) {
            error = true
            println("Test Failed - incorrect amount of cash returned for early rollover without interest: ${outputLoan.first().depositAmount.quantity} != ${Amount(30000, USD).quantity} OR" +
                    "${outputCash.first().amount.quantity} != ${Amount((30000 * ((3.4f))/100).toLong(), USD).quantity}")
        }
        assertFalse(error)
    }

    /** Testing reqeusting a term deposit when the offer being used doesng exist */
    @Test
    fun requestNonExistentTD() {
        var error = false
        val startTime = LocalDateTime.now()

        try {
            RequestTD(parties[0].second, banks[0].second, startTime, 3.9f, Amount(65000, USD), "Bob", "Smith", "1234",6)
            println("Test failed - Requesting a term deposit from a non-existent offer worked")
        } catch (e: Exception) {
            println("Test Passed - Requesting a term deposit from a non-existent offer failed")
            error = true
        }
        assertTrue(error)
    }

    /** Testing activating a term deposit when there is no pending term deposit that matches */
    @Test
    fun activateNonExistentTD() {
        var error = false
        val startTime = LocalDateTime.now()
        try {
            Activate(banks[0].second, parties[0].second, startTime, 5.5f, Amount(75000, USD), 6,
                    "Bob", "Smith", "1234")
            println("Test failed - Activating a non-existent Term deposit passed")
        } catch (e: Exception) {
            println("Test Passed - Activating a non-existent Term deposit failed")
            error = true
        }
        assertTrue(error)
    }

    /** Testing attempting to activate an already activated term deposit */
    @Test
    fun doubleActivate() {
        var error: Boolean
        sendTDOffers(banks[0].second, parties[0].second, TermDepositOffer.offerDateData(LocalDateTime.MAX, 18), 3.1f, TermDepositOffer.earlyTerms(true))
        val startTime = LocalDateTime.now().minusMonths(4) //Loan started 4 months ago
        RequestTD(parties[0].second, banks[0].second, startTime, 3.1f, Amount(30000, USD), "Bob", "Smith", "1234",18)
        Activate(banks[0].second, parties[0].second, startTime, 3.1f, Amount(30000, USD),18,
                "Bob", "Smith", "1234")
        try {
            Activate(banks[0].second, parties[0].second, startTime, 3.1f, Amount(30000, USD),18,
                    "Bob", "Smith", "1234")
            println("Test failed - attempting to double activate worked")
            error = false
        } catch (e: Exception) {
            println("Test passed - attempting to double activate failed")
            error = true
        }
        assertTrue(error)
    }
    /** Testing exiting an already exited term deposit */
    @Test
    fun doubleExit() {
        var error: Boolean
        sendTDOffers(banks[0].second, parties[0].second, TermDepositOffer.offerDateData(LocalDateTime.MAX, 14), 2.8f, TermDepositOffer.earlyTerms(true))
        val startTime = LocalDateTime.now().minusMonths(4) //Loan started 4 months ago
        RequestTD(parties[0].second, banks[0].second, startTime, 2.8f, Amount(30000, USD), "Bob", "Smith", "1234",14)
        Activate(banks[0].second, parties[0].second, startTime, 2.8f, Amount(30000, USD),14,
                "Bob", "Smith", "1234")
        Redeem(parties[0].second, banks[0].second, startTime, 2.8f, Amount(30000, USD), 14,
                "Bob", "Smith", "1234")
        try {
            Redeem(parties[0].second, banks[0].second, startTime, 2.8f, Amount(30000, USD), 14,
                    "Bob", "Smith", "1234")
            println("Test failed - attempting to double exit worked")
            error = false
        } catch (e: Exception) {
            println("Test passed - attempting to double exit failed")
            error = true
        }
        assertTrue(error)
    }

    /** Testing exiting a term deposit when it has not been activated */
    @Test
    fun exitNonActivated() {
        var error: Boolean
        sendTDOffers(banks[0].second, parties[0].second, TermDepositOffer.offerDateData(LocalDateTime.MAX, 24), 4.1f, TermDepositOffer.earlyTerms(true))
        val startTime = LocalDateTime.now().minusMonths(4) //Loan started 4 months ago
        RequestTD(parties[0].second, banks[0].second, startTime, 4.1f, Amount(30000, USD), "Bob", "Smith", "1234",24)
        try {
            Redeem(parties[0].second, banks[0].second, startTime, 4.1f, Amount(30000, USD), 24,
                    "Bob", "Smith", "1234")
            println("Test failed - attempting to exit a non activated td worked")
            error = false
        } catch (e: Exception) {
            println("Test passed - attempting to exit a non activated td failed")
            error = true
        }
        assertTrue(error)
    }

    /** Testing rolling over a term deposit (with interest) when it has not been activated */
    @Test
    fun rolloverWithInterestNonActivated() {
        var error: Boolean
        sendTDOffers(banks[0].second, parties[0].second, TermDepositOffer.offerDateData(LocalDateTime.MAX, 20), 4.1f, TermDepositOffer.earlyTerms(true))
        val startTime = LocalDateTime.now().minusMonths(4) //Loan started 4 months ago
        RequestTD(parties[0].second, banks[0].second, startTime, 4.1f, Amount(30000, USD), "Bob", "Smith", "1234",20)
        try {
            Rollover(parties[0].second, banks[0].second, startTime,
                    3.4f, Amount(30000, USD), true, 20,
                    "Bob", "Smith", "1234", 3.4f, banks[0].first, 6)
            println("Test failed - attempting to rollover (with interest) a non activated td worked")
            error = false
        } catch (e: Exception) {
            println("Test passed - attempting to rollover (with interest) a non activated td failed")
            error = true
        }
        assertTrue(error)
    }

    /** Testing rolling over a term deposit (without interest) when it has not been activated */
    @Test
    fun rolloverWithoutInterestNonActivated() {
        var error: Boolean
        sendTDOffers(banks[0].second, parties[0].second, TermDepositOffer.offerDateData(LocalDateTime.MAX, 20), 4.1f, TermDepositOffer.earlyTerms(true))
        val startTime = LocalDateTime.now().minusMonths(4) //Loan started 4 months ago
        RequestTD(parties[0].second, banks[0].second, startTime, 4.1f, Amount(30000, USD), "Bob", "Smith", "1234",20)
        try {
            Rollover(parties[0].second, banks[0].second, startTime,
                    3.4f, Amount(30000, USD), false, 20,
                    "Bob", "Smith", "1234", 3.4f, banks[0].first, 6)
            println("Test failed - attempting to rollover a non activated td worked")
            error = false
        } catch (e: Exception) {
            println("Test passed - attempting to rollover a non activated td failed")
            error = true
        }
        assertTrue(error)
    }

    /** Testing a TD issue with non existent client */
    @Test
    fun tdIssueNoClient() {
        var error: Boolean
        sendTDOffers(banks[0].second, parties[0].second, TermDepositOffer.offerDateData(LocalDateTime.MAX, 20), 4.1f, TermDepositOffer.earlyTerms(true))
        val startTime = LocalDateTime.now().minusMonths(4) //Loan started 4 months ago
        try {
            RequestTD(parties[0].second, banks[0].second, startTime, 4.1f, Amount(30000, USD), "Doesnt", "Exist", "0000",20)
            println("Test failed - attempting to issue a td with a non-existent client worked")
            error = false
        } catch (e: Exception) {
            println("Test passed - attempting to issue a td with a non-existent client failed")
            error = true
        }
        assertTrue(error)
    }

    /** Testing the creation of a duplicate client */
    @Test
    fun duplicateClient() {
        var error: Boolean
        CreateKYC(parties[0].second, "Test","Guy", "1234")
        try {
            CreateKYC(parties[0].second, "Test","Guy", "1234")
            println("Test failed - attempting to create a duplicate KYC worked")
            error = false
        } catch (e: Exception) {
            println("Test passed - attempting to create a duplicate KYC failed")
            error = true
        }
        assertTrue(error)
    }

    /** Testing updating a non-existent client */
    @Test
    fun updateNonExistentClient() {
        var error: Boolean
        try {
            updateKYC(parties[0].second, "1234", null, null,UniqueIdentifier.fromString("No Client for this ID"))
            error = false
            println("Test failed - updating a non existent client worked")
        } catch (e: Exception) {
            error = true
            println("Test passed - updating a non existent client failed")
        }
        assertTrue(error)
    }

    /** Testing a TD issue with client data that has been updated */
    @Test
    fun issueWithUpdatedClient() {
        var error: Boolean
        val linearID = CreateKYC(parties[0].second, "Update","Guy", "1234")
        updateKYC(parties[0].second, "new1234", null,null, linearID)
        val startTime = LocalDateTime.now()
        try {
            RequestTD(parties[0].second, banks[0].second, startTime, 4.1f, Amount(30000, USD), "Update", "Guy", "1234",20)
            println("Test failed - issuing a TD with old client data worked")
            error = false
        } catch (e: Exception) {
            println("Test passed - issuing a TD with old client data failed")
            error = true
        }
        assertTrue(error)
    }



    /** Run all tests detailed above */
    fun runTests() {
        //Issue cash to ensure that cash isnt the reason for flow errors
        parties.forEach {
            issueCash(it.second, it.second.notaryIdentities().first())
        }
        //Issue some cash to each of the banks
        banks.forEach{
            issueCash(it.second, it.second.notaryIdentities().first())
        }

        //Run the tests
        //Test cases for errors
        expiredTDOffer()
        requestNonExistentTD()
        activateNonExistentTD()
        doubleActivate()
        doubleExit()
        exitNonActivated()
        rolloverWithInterestNonActivated()
        rolloverWithoutInterestNonActivated()
        tdIssueNoClient()
        duplicateClient()
        updateNonExistentClient()
        issueWithUpdatedClient()

        //Test cases that ensure values match (i.e correct values paid, correct loans generated)
        exitNonExpiredTD()
        rolloverWithInterestNonExpiredTD()
        rolloverWOInterestNonExpiredTD()
        exitExpiredTD()
        rolloverWithInterestExpiredTD()
        rolloverWOInterestExpiredTD()


        println("All Tests Passed")
    }

    /** Simulations for Cordapp
     * This simulation issues some cash around the network, creates some clients, and issues various term deposit offers/term deposits*/

    fun runSimulation() {
        //Issue some cash to each party
        parties.forEach {
            issueCash(it.second, it.second.notaryIdentities().first())
        }
        //Issue some cash to each of the banks
        banks.forEach{
            issueCash(it.second, it.second.notaryIdentities().first())
        }

        //Create some kyc data
        val client1 = CreateKYC(parties[0].second, "Bob", "Smith", "1234")
        val client2 = CreateKYC(parties[0].second, "Jane", "Doe", "9384")
        val client3 = CreateKYC(parties[0].second, "Alice", "Anon", "6820")
        val client4 = CreateKYC(parties[0].second, "Elon", "Musk", "5236")
        val client5 = CreateKYC(parties[0].second, "Bill", "Gates", "0384")
        val client6 = CreateKYC(parties[0].second, "Matt", "Rose", "2893")

        //Send out some example offers from the two banks at different interest percentages
        sendTDOffers(banks[0].second, parties[0].second, TermDepositOffer.offerDateData(LocalDateTime.MAX, 6), 2.55f,TermDepositOffer.earlyTerms(true))
        sendTDOffers(banks[0].second, parties[0].second,TermDepositOffer.offerDateData(LocalDateTime.MAX, 12), 2.65f,TermDepositOffer.earlyTerms(true))
        sendTDOffers(banks[0].second, parties[0].second, TermDepositOffer.offerDateData(LocalDateTime.MAX, 18), 3.1f,TermDepositOffer.earlyTerms(true))
        sendTDOffers(banks[1].second, parties[0].second, TermDepositOffer.offerDateData(LocalDateTime.MAX, 6), 2.7f, TermDepositOffer.earlyTerms(true))
        sendTDOffers(banks[1].second, parties[0].second, TermDepositOffer.offerDateData(LocalDateTime.MAX, 12), 3.0f, TermDepositOffer.earlyTerms(true))
        sendTDOffers(banks[1].second, parties[0].second, TermDepositOffer.offerDateData(LocalDateTime.MAX, 18), 2.95f, TermDepositOffer.earlyTerms(true))

        //Accept an offer
        RequestTD(parties[0].second, banks[0].second, LocalDateTime.MIN, 2.65f, Amount(300000,USD), "Bob", "Smith", "1234",12)
        Activate(banks[0].second, parties[0].second, LocalDateTime.MIN,  2.65f, Amount(300000,USD), 12,
                "Bob", "Smith", "1234")

//        //NOTE: These are not included in the simulations as they can easily be demoed in the corda demo bench or through the web api.
//        //Should you wish to include them simply uncomment the lines below.

//        Redeem(parties[0].second, banks[0].second, LocalDateTime.MIN, LocalDateTime.MIN.plusMonths(12), 2.65f, Amount(30000,USD), 12,
//                "Bob", "Smith", "1234" )

        //Update some KYC data
//        updateKYC(parties[0].second, "NEWACCOUNT", client1)
//
//        Rollover(parties[0].second, banks[0].second, LocalDateTime.MIN, 2.65f, Amount(300000,USD), true, 12,
//                "Bob", "Smith", "1234", 3.1f, banks[0].first, 18)

//        Redeem(parties[0].second, banks[0].second, LocalDateTime.MIN, 3.1f, Amount((300000 * (100+2.65f)/100).toLong(),USD), 18,
//                "Bob", "Smith", "1234" )

    }

    /** Helper method for sending a term deposit offer. Sent from a bank to all parties in the network */
    fun sendTDOffers(me : CordaRPCOps, receiver: CordaRPCOps, dateData: TermDepositOffer.offerDateData,
                     interestPercent: Float, earlyTerms: TermDepositOffer.earlyTerms): SignedTransaction {
        //Get attachment hash for the txn before starting the flow
        //TODO: This hardcoding of a very specific file path probably isnt that great
        val attachmentInputStream = File("C:\\Users\\raymondm\\Documents\\termDepositCordapp\\kotlin-source\\src\\main\\resources\\Example_TD_Contract.zip").inputStream()
        val inputStreamCopy = File("C:\\Users\\raymondm\\Documents\\termDepositCordapp\\kotlin-source\\src\\main\\resources\\Example_TD_Contract.zip").inputStream()
        val bytes = attachmentInputStream.readBytes()
        val hash = SecureHash.sha256(bytes)
        val attachmentHash: SecureHash
        if (me.attachmentExists(hash)) {
            attachmentHash = hash
        } else {
            //Upload the attachment to our node
            attachmentHash = me.uploadAttachment(inputStreamCopy)
        }
        //Start the flow for issuing a td offer
        val returnVal = me.startFlow(IssueOffer::Initiator, dateData, interestPercent, me.nodeInfo().legalIdentities.first(), receiver.nodeInfo().legalIdentities.first(),
                attachmentHash, earlyTerms).returnValue.getOrThrow()
        println("TD Offer Issued: "+interestPercent+"% for " + dateData.duration + " months from "+me.nodeInfo().legalIdentities.first());
        return returnVal
    }

    /** Helper method for requesting (issuing) a term deposit for a specific client */
    fun RequestTD(me : CordaRPCOps, issuer: CordaRPCOps, startDate: LocalDateTime,
                  interestPercent: Float, depositAmount: Amount<Currency>, firstName: String, lastName: String, accountNumber: String, duration: Int): SignedTransaction {
        //Request a TD at $300 USD
        val kycData = KYC.KYCNameData(firstName, lastName, accountNumber)
        val dateData = TermDeposit.DateData(startDate, duration)
        //Start the flow for issuing a term deposit
        val returnVal = me.startFlow(IssueTD::Initiator, dateData, interestPercent, issuer.nodeInfo().legalIdentities.first(), depositAmount,
                kycData).returnValue.getOrThrow()
        println("TD Requested: For client "+kycData.firstName+" "+kycData.lastName + " with offering institute "+ issuer.nodeInfo())
        return returnVal
    }

    /** Helper method for activating a term deposit. Called by a bank node that has a term deposit in "pending" state */
    fun Activate(me : CordaRPCOps, client : CordaRPCOps, startDate: LocalDateTime, interestPercent: Float, depositAmount: Amount<Currency>, duration: Int,
                 firstName: String, lastName: String, accountNumber: String): SignedTransaction {
        val kycNameData = KYC.KYCNameData(firstName, lastName,accountNumber)
        val dateData = TermDeposit.DateData(startDate, duration)
        val returnVal = me.startFlow(ActivateTD::Activator, dateData, interestPercent, me.nodeInfo().legalIdentities.first(), client.nodeInfo().legalIdentities.first(), depositAmount, kycNameData).returnValue.getOrThrow()
        println("TD Activated: ID "+returnVal.coreTransaction.id)
        return returnVal
    }

    /** Helper method for issuing some USD */
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

    /** Helper method for redeeming a term deposit */
    fun Redeem(me : CordaRPCOps, issuer: CordaRPCOps, startDate: LocalDateTime,
               interestPercent: Float, depositAmount: Amount<Currency>, duration: Int,
               firstName: String, lastName: String, accountNumber: String) : SignedTransaction {
        val kycNameData = KYC.KYCNameData(firstName, lastName, accountNumber)
        val dateData = TermDeposit.DateData(startDate, duration)
        //Call the flow to redeem the term deposit
        val returnVal = me.startFlow(RedeemTD::RedemptionInitiator, dateData, interestPercent, issuer.nodeInfo().legalIdentities.first(), depositAmount, kycNameData).returnValue.getOrThrow()
        println("TD Redeemed "+returnVal.coreTransaction.id + " Cash ${returnVal.tx.outputs.map { it.data }}" )
        return returnVal;
    }

    /** Helper method for rolling over a term deposit */
    fun Rollover(me: CordaRPCOps, issuer: CordaRPCOps, startDate: LocalDateTime, interestPercent: Float, depositAmount: Amount<Currency>, withInterest: Boolean, duration: Int,
                 firstName: String, lastName: String, accountNumber: String, newInterest: Float, newOfferingInstitue:Party, newDuration: Int): SignedTransaction {
        val kycNameData = KYC.KYCNameData(firstName, lastName, accountNumber)
        val rolloverTerms = TermDeposit.RolloverTerms(newInterest, newOfferingInstitue, newDuration, withInterest)
        val dateData = TermDeposit.DateData(startDate, duration)
        //Call the flow to roll over a term deposit
        val returnVal = me.startFlow(RolloverTD::RolloverInitiator, dateData, interestPercent, issuer.nodeInfo().legalIdentities.first(),
                depositAmount, rolloverTerms, kycNameData).returnValue.getOrThrow()
        println("TD Rollover " +returnVal.coreTransaction.id )
        return returnVal
    }

    /** Helper method for creating some KYC data */
    fun CreateKYC(me: CordaRPCOps, firstName: String, lastName: String, accountNumber: String): UniqueIdentifier {
        //Call the flow to create KYC data
        val returnVal = me.startFlow(CreateKYC::Creator, firstName, lastName, accountNumber).returnValue.getOrThrow()
        return returnVal
    }

    /** Helper method for retrieving some client data (name and account number) given a UniqueIdentifier (linearID for a KYC state) */
    fun getClientData(me: CordaRPCOps, linearID: UniqueIdentifier): KYC.KYCNameData {
        //Call the flow to retrieve kyc data
        val returnVal = me.startFlow(::KYCRetrievalFlowID, linearID).returnValue.getOrThrow().first().state.data
        //assemble a kycNameData wrapper object
        val kycNameData = KYC.KYCNameData(returnVal.firstName, returnVal.lastName, returnVal.accountNum)
        return kycNameData
    }

    /** Helper method for updating KYC data */
    fun updateKYC(me: CordaRPCOps, firstName: String ?= null, lastName: String ?= null, newAccountNum: String ?= null, clientID: UniqueIdentifier) {
        val returnVal = me.startFlow(UpdateKYC::Updator, clientID, firstName, lastName,newAccountNum).returnValue.getOrThrow()
        println("KYC Updated: ClientID "+clientID.id.toString())
    }

}
