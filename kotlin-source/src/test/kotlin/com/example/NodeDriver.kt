package com.example

import com.termDeposits.contract.KYC
import com.termDeposits.contract.TermDeposit
import com.termDeposits.flow.TermDeposit.*
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.File
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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

    fun main(args: Array<String>) {
        println("Simulation Main")
        // No permissions required as we are not invoking flows.
        driver(isDebug = false, extraCordappPackagesToScan = listOf("com.termDeposits.contract", "termDeposits.contract", "termDepositCordapp.com.termDeposits.contract",
                "com.termDeposits.flow", "net.corda.finance"), startNodesInProcess = false) {
            startNode(providedName = CordaX500Name("Controller", "London", "GB"), advertisedServices = setOf(ServiceInfo(ValidatingNotaryService.type)))
            val cBank = startNode(providedName = CordaX500Name("CentralBank", "Brisbane", "AU"), rpcUsers = listOf(cashIssuer)).getOrThrow()
            val (nodeA, nodeB, nodeC, aBank, bBank) = listOf(
                    startNode(providedName = CordaX500Name("AMM", "London", "GB"), rpcUsers = listOf(stdUser)),
                    startNode(providedName = CordaX500Name("ClientB", "New York", "US"), rpcUsers = listOf(stdUser)),
                    startNode(providedName = CordaX500Name("ClientC", "Paris", "FR"), rpcUsers = listOf(stdUser)),
                    startNode(providedName = CordaX500Name("BankA", "Munich", "DE"), rpcUsers = listOf(bank)),
                    startNode(providedName = CordaX500Name("BankB", "Singapore", "SG"), rpcUsers = listOf(bank))).map { it.getOrThrow() }

            aNode = nodeA
            bNode = nodeB
            cNode = nodeC
            bankA = aBank
            bankB = bBank
            centralBank = cBank
            /*startWebserver(aNode)
            startWebserver(bNode)
            startWebserver(cNode)*/


            setup_nodes()
            runSimulation()
            //runTests()
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

        val aBankClient = bankA.rpcClientToNode()
        val abRPC = aBankClient.start(stdUser.username, stdUser.password).proxy

        val bBankClient = bankB.rpcClientToNode()
        val bbRPC = bBankClient.start(stdUser.username, stdUser.password).proxy

        val centralBankClient = centralBank.rpcClientToNode()
        val cbrpc = centralBankClient.start(stdUser.username, stdUser.password).proxy

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
            println("${it.nodeInfo.legalIdentities.first()} started on ${it.configuration.rpcAddress}")
        }
    }

    fun allocateTDPermissions() : Set<String> = setOf(
            //FlowPermissions.startFlowPermission<IssueOffer.Initiator>(),
            FlowPermissions.startFlowPermission<IssueTD.Initiator>(),
            FlowPermissions.startFlowPermission<IssueTD.Acceptor>(),
            //FlowPermissions.startFlowPermission<ActivateTD.Activator>(),
            FlowPermissions.startFlowPermission<ActivateTD.Acceptor>(),
            FlowPermissions.startFlowPermission<IssueOffer.Reciever>(),
            FlowPermissions.startFlowPermission<RedeemTD.RedemptionInitiator>(),
            FlowPermissions.startFlowPermission<RedeemTD.RedemptionAcceptor>(),
            //FlowPermissions.startFlowPermission<RolloverTD.RolloverAcceptor>(),
            FlowPermissions.startFlowPermission<RolloverTD.RolloverInitiator>(),
            FlowPermissions.startFlowPermission<PromptActivate.Prompter>(),
            FlowPermissions.startFlowPermission<PromptActivate.Acceptor>(),
            FlowPermissions.startFlowPermission<CreateKYC.Creator>(),
            FlowPermissions.startFlowPermission<KYCRetrievalFlow>(),
            FlowPermissions.startFlowPermission<KYCRetrievalFlowID>()
    )

    fun allocateBankPermissions() : Set<String> = setOf(
            FlowPermissions.startFlowPermission<IssueOffer.Initiator>(),
            FlowPermissions.startFlowPermission<ActivateTD.Activator>(),
            FlowPermissions.startFlowPermission<RolloverTD.RolloverAcceptor>(),
            FlowPermissions.startFlowPermission<RedeemTD.RedemptionAcceptor>()
    )

    fun allocateCashPermissions() : Set<String> = setOf(
            FlowPermissions.startFlowPermission<CashIssueFlow>(),
            FlowPermissions.startFlowPermission<CashPaymentFlow>(),
            FlowPermissions.startFlowPermission<CashExitFlow>(),
            FlowPermissions.startFlowPermission<CashIssueAndPaymentFlow>()
    )


    /** TESTING FOR FLOW ERRORS */
    @Test
    fun expiredTDOffer() {
        sendTDOffers(banks[0].second, parties[0].second, LocalDateTime.now(), 3.4f, 6)
        CreateKYC(parties[0].second, "Bob", "Smith", "1234")
        Thread.sleep(1000)
        //Should throw an error due to the offer already expirying/no states being found.
        var error = false
        try {
            RequestTD(parties[0].second, banks[0].second, LocalDateTime.now(),
                    3.4f, Amount(30000, USD), "Bob", "Smith", "1234", 6)
        } catch (e: Exception) {
            error = true
            println("Test Passed")
        }
        assertTrue(error)
    }

    @Test
    fun exitNonExpiredTD() {
        var error = false
        sendTDOffers(banks[0].second, parties[0].second, LocalDateTime.MAX, 3.4f, 6)
        CreateKYC(parties[0].second, "Bob", "Smith", "1234")
        val startTime = LocalDateTime.now()
        RequestTD(parties[0].second, banks[0].second, startTime, 3.4f, Amount(30000, USD), "Bob", "Smith", "1234", 6)
        Activate(banks[0].second, parties[0].second, startTime, startTime.plusWeeks(6), 3.4f, Amount(30000, USD), 6,
                "Bob", "Smith", "1234")
        //Should throw an error due to this term deposit not yet being able to exit
        try {
            Redeem(parties[0].second, banks[0].second, startTime, startTime.plusWeeks(6), 3.4f, Amount(30000, USD), 6,
                    "Bob", "Smith", "1234")
        } catch (e: Exception) {
            error = true
            println("Test Passed")
        }

        assertTrue(error)
    }

    @Test
    fun rolloverWithInterestNonExpiredTD() {
        var error = false
        sendTDOffers(banks[0].second, parties[0].second, LocalDateTime.MAX, 3.4f,6)
        CreateKYC(parties[0].second, "Bob", "Smith", "1234")
        val startTime = LocalDateTime.now()
        RequestTD(parties[0].second, banks[0].second, startTime, 3.4f, Amount(30000, USD), "Bob", "Smith", "1234",6)
        Activate(banks[0].second, parties[0].second, startTime, startTime.plusWeeks(6), 3.4f, Amount(30000, USD),6,
                "Bob", "Smith", "1234")
        //Should throw an error due to this term deposit not yet being able to exit
        try {
            Rollover(parties[0].second, banks[0].second, startTime, startTime.plusWeeks(6), LocalDateTime.now(), LocalDateTime.now().plusWeeks(6),
                    3.4f, Amount(30000, USD), true, 6,
                    "Bob", "Smith", "1234")
        } catch (e: Exception) {
            error = true
            println("Test Passed")
        }

        assertTrue(error)
    }

    @Test
    fun rolloverWOInterestNonExpiredTD() {
        var error = false
        sendTDOffers(banks[0].second, parties[0].second, LocalDateTime.MAX, 3.4f, 6)
        CreateKYC(parties[0].second, "Bob", "Smith", "1234")
        val startTime = LocalDateTime.now()
        RequestTD(parties[0].second, banks[0].second, startTime, 3.4f, Amount(30000, USD), "Bob", "Smith", "1234",6)
        Activate(banks[0].second, parties[0].second, startTime, startTime.plusWeeks(6), 3.4f, Amount(30000, USD), 6,
                "Bob", "Smith", "1234")
        //Should throw an error due to this term deposit not yet being able to exit
        try {
            Rollover(parties[0].second, banks[0].second, startTime, startTime.plusWeeks(6), LocalDateTime.now(), LocalDateTime.now().plusWeeks(6),
                    3.4f, Amount(30000, USD), false, 6,
                    "Bob", "Smith", "1234")
        } catch (e: Exception) {
            error = true
            println("Test Passed")
        }

        assertTrue(error)
    }

    @Test
    fun requestNonExistentTD() {
        var error = false
        val startTime = LocalDateTime.now()
        CreateKYC(parties[0].second, "Bob", "Smith", "1234")

        try {
            RequestTD(parties[0].second, banks[0].second, startTime, 3.9f, Amount(65000, USD), "Bob", "Smith", "1234",6)
        } catch (e: Exception) {
            println("Test Passed")
            error = true
        }
        assertTrue(error)
    }

    @Test
    fun activateNonExistentTD() {
        var error = false
        val startTime = LocalDateTime.now()
        try {
            Activate(banks[0].second, parties[0].second, startTime, startTime.plusWeeks(6), 5.5f, Amount(75000, USD), 6,
                    "Bob", "Smith", "1234")
        } catch (e: Exception) {
            println("Test Passed")
            error = true
        }
        assertTrue(error)
    }




    fun runTests() {
        //The following is some basic tests
        //TODO: Write proper tests using a testing framework - rather than testing with exceptions thrown - possibly follow the example on corda tutorials

        //Issue cash to ensure that cash isnt the reason for flow errors
        parties.forEach {
            issueCash(it.second, it.second.notaryIdentities().first())
        }
        //Issue some cash to each of the banks
        banks.forEach{
            issueCash(it.second, it.second.notaryIdentities().first())
        }

        //Run the tests
        expiredTDOffer()
        exitNonExpiredTD()
        rolloverWithInterestNonExpiredTD()
        rolloverWOInterestNonExpiredTD()
        requestNonExistentTD()
        activateNonExistentTD()


        println("All Tests Passed")
    }

    /** Simulations for Cordapp */
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

        //Test retrieving  client data based of unique ID
        val clientKYC = getClientData(parties[0].second, client4)
        println("${clientKYC.firstName} ${clientKYC.lastName} ${client4}")

        println("Simulations")
        //Send out offers from the two banks at different interest percentages
        sendTDOffers(banks[0].second, parties[0].second, LocalDateTime.MAX, 2.55f,6)
        sendTDOffers(banks[0].second, parties[0].second, LocalDateTime.MAX, 2.65f,12)
        sendTDOffers(banks[0].second, parties[0].second, LocalDateTime.MAX, 3.1f,18)
        sendTDOffers(banks[1].second, parties[0].second, LocalDateTime.MAX, 2.7f,6)
        sendTDOffers(banks[1].second, parties[0].second, LocalDateTime.MAX, 3.0f,12)
        sendTDOffers(banks[1].second, parties[0].second, LocalDateTime.MAX, 2.95f,18)

        //Accept some td offers
        RequestTD(parties[0].second, banks[0].second, LocalDateTime.MIN, 2.65f, Amount(30000,USD), "Bob", "Smith",
                "1234",12)
        Activate(banks[0].second, parties[0].second, LocalDateTime.MIN, LocalDateTime.MAX, 2.65f, Amount(30000,USD), 12,
                "Bob", "Smith", "1234")
    }

    fun sendTDOffers(me : CordaRPCOps, receiver: CordaRPCOps, endDate: LocalDateTime,
                     interestPercent: Float, duration: Int) {
        //Get attachment hash for the txn before starting the flow
        //TODO: This hardcoding of a very specific file path probably isnt that great
        val attachmentInputStream = File("C:\\Users\\raymondm\\Documents\\termDepositsCordapp\\kotlin-source\\src\\main\\resources\\Example_TD_Contract.zip").inputStream()
        val attachmentHash = me.uploadAttachment(attachmentInputStream)
        val returnVal = me.startFlow(IssueOffer::Initiator, endDate, interestPercent, me.nodeInfo().legalIdentities.first(), receiver.nodeInfo().legalIdentities.first(),
                attachmentHash, duration).returnValue.getOrThrow()
        //println("TD Offers Issued")
    }

    fun RequestTD(me : CordaRPCOps, issuer: CordaRPCOps, startDate: LocalDateTime,
                  interestPercent: Float, depositAmount: Amount<Currency>, firstName: String, lastName: String, accountNumber: String, duration: Int) {
        //Request a TD at $300 USD
        val kycData = KYC.KYCNameData(firstName, lastName, accountNumber)
        val dateData = TermDeposit.DateData(startDate, startDate.plusMonths(duration.toLong()), duration)
        val returnVal = me.startFlow(IssueTD::Initiator, dateData, interestPercent, issuer.nodeInfo().legalIdentities.first(), depositAmount,
                kycData).returnValue.getOrThrow()
        //println("TD Requested")
    }

    fun Activate(me : CordaRPCOps, client : CordaRPCOps, startDate: LocalDateTime, endDate: LocalDateTime, interestPercent: Float, depositAmount: Amount<Currency>, duration: Int,
                 firstName: String, lastName: String, accountNumber: String) {
        println("Start activate")
        val kycNameData = KYC.KYCNameData(firstName, lastName,accountNumber)
        val dateData = TermDeposit.DateData(startDate, startDate.plusMonths(duration.toLong()), duration)
        val returnVal = me.startFlow(ActivateTD::Activator, dateData, interestPercent, me.nodeInfo().legalIdentities.first(), client.nodeInfo().legalIdentities.first(), depositAmount, kycNameData).returnValue.getOrThrow()
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
               interestPercent: Float, depositAmount: Amount<Currency>, duration: Int,
               firstName: String, lastName: String, accountNumber: String) {
        val kycNameData = KYC.KYCNameData(firstName, lastName, accountNumber)
        val dateData = TermDeposit.DateData(startDate, startDate.plusMonths(duration.toLong()), duration)
        val returnVal = me.startFlow(RedeemTD::RedemptionInitiator, dateData, interestPercent, issuer.nodeInfo().legalIdentities.first(), depositAmount, kycNameData).returnValue.getOrThrow()
    }

    fun Rollover(me: CordaRPCOps, issuer: CordaRPCOps, startDate: LocalDateTime, endDate: LocalDateTime, newStartDate: LocalDateTime,
                 newEndDate: LocalDateTime, interestPercent: Float, depositAmount: Amount<Currency>, withInterest: Boolean, duration: Int,
                 firstName: String, lastName: String, accountNumber: String) {
//        val returnVal = me.startFlow(RolloverTD::RolloverInitiator, startDate, endDate, newStartDate, newEndDate, interestPercent, issuer.nodeInfo().legalIdentities.first(),
//                depositAmount, withInterest).returnValue.getOrThow()
        val kycNameData = KYC.KYCNameData(firstName, lastName, accountNumber)
        val rolloverTerms = TermDeposit.RolloverTerms(newStartDate, newEndDate, withInterest)
        val dateData = TermDeposit.DateData(startDate, startDate.plusMonths(duration.toLong()), duration)
        val returnVal = me.startFlow(RolloverTD::RolloverInitiator, dateData, interestPercent, issuer.nodeInfo().legalIdentities.first(),
                depositAmount, rolloverTerms, kycNameData).returnValue.getOrThrow()
    }

    fun CreateKYC(me: CordaRPCOps, firstName: String, lastName: String, accountNumber: String): UniqueIdentifier {
        val returnVal = me.startFlow(CreateKYC::Creator, firstName, lastName, accountNumber).returnValue.getOrThrow()
        return returnVal
    }

    fun getClientData(me: CordaRPCOps, linearID: UniqueIdentifier): KYC.KYCNameData {
        val returnVal = me.startFlow(::KYCRetrievalFlowID, linearID).returnValue.getOrThrow().first().state.data
        val kycNameData = KYC.KYCNameData(returnVal.firstName, returnVal.lastName, returnVal.accountNum)
        return kycNameData
    }

}
