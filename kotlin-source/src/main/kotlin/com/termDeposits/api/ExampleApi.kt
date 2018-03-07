package com.termDeposits.api

import com.termDeposits.contract.KYC
import com.termDeposits.contract.TermDeposit
import com.termDeposits.contract.TermDepositOffer
import com.termDeposits.flow.TermDeposit.*
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.finance.AMOUNT
import net.corda.finance.USD
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

/** API for interacting with all aspects of the Term Deposits cordapp
 * Note that most API calls require many fields. Most of this data can be receieved from states currently on the
 * ledger (eg customer name and account number, start date of a term deposit, etc). It is expected a front end
 * would be provided that allows the user to simply select a client from a drop down list, and all their paramaters
 * will be passed to the API call - rather than requiring all fields be entered manually.
 * */

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

    //Issue a TD - Requires selecting an active TD offer from the fields provided in the POST call
    @POST
    @Path("issue_td")
    fun issueTD(@QueryParam("td_value") tdValue: Int, @QueryParam("offering_institute") offeringInstitue:String,
                @QueryParam("interest_percent") interestPercent: Float, @QueryParam("duration") duration: Int,
                @QueryParam("customer_fname") firstName: String, @QueryParam("customer_lname") lastName: String,
                @QueryParam("customer_anum") accountNum: String) : Response {
        val startDate = LocalDateTime.now()
        val issuingInstitute = rpcOps.networkMapSnapshot().filter { it.legalIdentities.first().name.organisation == offeringInstitue }.first().legalIdentities.first()
        val kyc = KYC.KYCNameData(firstName, lastName, accountNum)
        //TODO use actual dates, for testing we use LocalDateTime.MIN for now
        val dateData = TermDeposit.DateData(LocalDateTime.MIN, duration)
        val depositAmount = AMOUNT(tdValue, USD)

        return try {
            val flowHandle = rpcOps.startTrackedFlow(IssueTD::Initiator, dateData, interestPercent, issuingInstitute, depositAmount, kyc)
            // The line below blocks and waits for the future to resolve.
            val result = flowHandle.returnValue.getOrThrow()
            Response.status(CREATED).entity("Transaction id ${result.id} committed to ledger.\n").build()

        } catch (ex: Throwable) {
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
    }

    //Activate a TD - Requires selecting an pending TD based off the fields provided in the POST request
    @POST
    @Path("activate_td")
    fun activateTD(@QueryParam("td_value") tdValue: Int, @QueryParam("offering_institute") offeringInstitue:String,
                @QueryParam("interest_percent") interestPercent: Float, @QueryParam("duration") duration: Int,
                @QueryParam("customer_fname") firstName: String, @QueryParam("customer_lname") lastName: String,
                @QueryParam("customer_anum") accountNum: String, @QueryParam("start_date") startDate: String,
                   @QueryParam("client") client:String) : Response {

        val issuingInstitute = rpcOps.networkMapSnapshot().filter { it.legalIdentities.first().name.organisation == offeringInstitue }.first().legalIdentities.first()
        val clientParty = rpcOps.networkMapSnapshot().filter { it.legalIdentities.first().name.organisation == client }.first().legalIdentities.first()
        val kyc = KYC.KYCNameData(firstName, lastName, accountNum)
        val startDateActual = LocalDateTime.parse(startDate)
        //TODO Actually parse a correct string, for now we use LocalDateTime.min for all
        val dateData = TermDeposit.DateData(LocalDateTime.MIN, duration)
        val depositAmount = AMOUNT(tdValue, USD)

        return try {
            val flowHandle = rpcOps.startFlow(ActivateTD::Activator, dateData, interestPercent, issuingInstitute,
                    clientParty, depositAmount, kyc)
            // The line below blocks and waits for the future to resolve.
            val result = flowHandle.returnValue.getOrThrow()
            Response.status(CREATED).entity("Transaction id ${result.id} committed to ledger.\n").build()

        } catch (ex: Throwable) {
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
    }

    //Redeem a TD
    @POST
    @Path("redeem_td")
    fun redeemTD(@QueryParam("td_value") tdValue: Int, @QueryParam("offering_institute") offeringInstitue:String,
                   @QueryParam("interest_percent") interestPercent: Float, @QueryParam("duration") duration: Int,
                   @QueryParam("customer_fname") firstName: String, @QueryParam("customer_lname") lastName: String,
                   @QueryParam("customer_anum") accountNum: String, @QueryParam("start_date") startDate: String) : Response {

        val issuingInstitute = rpcOps.networkMapSnapshot().filter { it.legalIdentities.first().name.organisation == offeringInstitue }.first().legalIdentities.first()
        val kyc = KYC.KYCNameData(firstName, lastName, accountNum)
        val startDateActual = LocalDateTime.parse(startDate)
        //TODO Actually parse a correct string, for now we use LocalDateTime.min for all
        val dateData = TermDeposit.DateData(LocalDateTime.MIN, duration)
        val depositAmount = AMOUNT(tdValue, USD)

        return try {
            val flowHandle = rpcOps.startFlow(RedeemTD::RedemptionInitiator, dateData, interestPercent, issuingInstitute, depositAmount, kyc)
            // The line below blocks and waits for the future to resolve.
            val result = flowHandle.returnValue.getOrThrow()
            Response.status(CREATED).entity("Transaction id ${result.id} committed to ledger.\n").build()

        } catch (ex: Throwable) {
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
    }

    //TODO API - RolloverTD's
}

//API for interacting with KYC data.
@Path("term_deposits")
class KYCAPI(private val rpcOps: CordaRPCOps) {

    //Get all KYC data known by this node
    @GET
    @Path("kyc")
    @Produces(MediaType.APPLICATION_JSON)
    fun getDeposits() = rpcOps.vaultQueryBy<KYC.State>().states

    //Create new KYC data - first name, last name and account number must all be supplied.
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

    //Update KYC data - the client ID (states unique identifier) must be supplied
    @POST
    @Path("update_kyc")
    fun updateKYC(@QueryParam("client_id") clientID: String, @QueryParam("new_fname") newFirstName:String?,
                  @QueryParam("new_lname") newLastName:String?, @QueryParam("new_anum") newAccountNum:String?) : Response{
        return try {
            val uniqueClientID = UniqueIdentifier.fromString(clientID)
            val flowHandle = rpcOps.startFlow(UpdateKYC::Updator, uniqueClientID, newAccountNum, newFirstName, newLastName)
            val result = flowHandle.returnValue.getOrThrow()
            Response.status(CREATED).entity("Transaction id ${result.id} committed to ledger.\n").build()
        } catch (ex: Throwable) {
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
    }

}

