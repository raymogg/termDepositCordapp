package com.termDeposits.api

import com.termDeposits.contract.KYC
import com.termDeposits.contract.TermDeposit
import com.termDeposits.contract.TermDepositOffer
import com.termDeposits.flow.TermDeposit.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.finance.AMOUNT
import net.corda.finance.USD
import net.corda.finance.contracts.JavaCommercialPaper
import org.slf4j.Logger
import java.time.LocalDateTime
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status.BAD_REQUEST
import javax.ws.rs.core.Response.Status.CREATED

val SERVICE_NAMES = listOf("Controller", "Network Map Service")

//This API is accessible from /api/example. All paths specified below are relative to it.
@Path("example")
class ExampleApi(private val rpcOps: CordaRPCOps) {
    private val myLegalName: CordaX500Name = rpcOps.nodeInfo().legalIdentities.first().name

    companion object {
        private val logger: Logger = loggerFor<ExampleApi>()
    }

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = rpcOps.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                //filter out myself, notary and eventual network map started by driver
                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }
}

/** API for interacting with all aspects of the Term Deposits cordapp */

//API for deposits
@Path("term_deposits")
class DepositsAPI(private val rpcOps: CordaRPCOps) {

    //Gets all active term deposit states for the current node
    @GET
    @Path("deposits")
    @Produces(MediaType.APPLICATION_JSON)
    fun getDeposits() = rpcOps.vaultQueryBy<TermDeposit.State>().states

    //Get all term deposit offers that have been issued to the current node (or by the current node if they are a bank node)
    @GET
    @Path("offers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getOffers() = rpcOps.vaultQueryBy<TermDepositOffer.State>().states

    //Issue a TD - Requires selecting an active TD offer
    //Required fields - dateData, interestPercent, issuingInstitue, KYCData.KYCNameData
    @POST
    @Path("issue_td")
    fun issueTD(@QueryParam("td_value") tdValue: Int) : Response {
        //todo change this hardcoding, for now just seeing if this works to issue a new TD
        //All these values are known from the NodeDriver test and are issued as current states on the ledger.
        val startDate = LocalDateTime.now()
        val issuingInstitue = rpcOps.networkMapSnapshot().filter { it.legalIdentities.first().name == CordaX500Name.parse("C=SG,L=Singapore,O=BankB") }
        val interestPercent = 2.7f
        val duration = 6
        val kyc = KYC.KYCNameData("Jane", "Doe", "9384")
        val dateData = TermDeposit.DateData(startDate, duration)
        val depositAmount = AMOUNT(tdValue, USD)

        return try {
            val flowHandle = rpcOps.startTrackedFlow(IssueTD::Initiator, dateData, interestPercent, issuingInstitue.first().legalIdentities.first(), depositAmount, kyc)
            // The line below blocks and waits for the future to resolve.
            val result = flowHandle.returnValue.getOrThrow()
            Response.status(CREATED).entity("Transaction id ${result.id} committed to ledger.\n").build()

        } catch (ex: Throwable) {
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }

    }
}

//API for interacting with KYC data.
@Path("term_deposits")
class KYCAPI(private val rpcOps: CordaRPCOps) {

    //Get all KYC data known by this node
    @GET
    @Path("kyc")
    @Produces(MediaType.APPLICATION_JSON)
    fun getDeposits() = rpcOps.vaultQueryBy<KYC.State>().states

    @POST
    @Path("create_kyc")
    fun createKYC(@QueryParam("first_name") firstName: String, @QueryParam("last_name") lastName:String,
                  @QueryParam("account_num") accountNum:String) : Response{
        return try {
            val flowHandle = rpcOps.startFlow(CreateKYC::Creator, firstName, lastName, accountNum)
            val result = flowHandle.returnValue.getOrThrow()
            Response.status(CREATED).entity("Transaction id ${result.id} committed to ledger.\n").build()
        } catch (ex: Throwable) {
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
    }

}

