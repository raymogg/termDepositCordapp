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


    /** Returns a JSON containing an array of term deposit states (mappings) for every term deposit in the nodes vault.
     *  Each term deposit mapping contains the following fields {from: Party, to: Party, percent: Float, startDate:
     *  LocalDateTime, endDate: LocalDateTime, amount: Amount<Currency>, internal state: }
     */
    @GET
    @Path("deposits")
    @Produces(MediaType.APPLICATION_JSON)
    fun getDeposits(): Map<String, List<Map<String, Any>>> {
        val states =  rpcOps.vaultQueryBy<TermDeposit.State>().states
        //return mapOf("states" to states.map { it.state.data.toString() })
        return mapOf("states" to states.map { mapOf("from" to it.state.data.institue, "to" to it.state.data.owner,
                "percent" to it.state.data.interestPercent, "startDate" to it.state.data.startDate,
                "endDate" to it.state.data.endDate, "client" to it.state.data.clientIdentifier, "amount" to it.state.data.depositAmount.toString(),
                "internalState" to it.state.data.internalState) })
    }

    /** Returns a JSON containing an array of term deposit states (mappings) for every offer issued to this node.
     * Each offer mapping contains the following {validTill: LocalDateTime, interest: Float, duration: Int,
     * issuingInstitute: Party}
     */
    @GET
    @Path("offers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getOffers() : Map<String, List<Map<String, Any>>> {
        val offers = rpcOps.vaultQueryBy<TermDepositOffer.State>().states
        return mapOf("offers" to offers.map { mapOf("validTill" to it.state.data.validTill, "interest" to it.state.data.interestPercent,
                "duration" to it.state.data.duration, "issuingInstitute" to it.state.data.institue) })
    }

    /** Post Call to issue a term deposit from the node
     *
     */
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

    /** Post call to activate a TD. Requires the correct paramaters for a currently pending term deposit to be supplied
     *
     */
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

    /** Post call to redeem a TD. Requires the correct paramaters for a currently expired TD.
     *
     */
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

    /** Post call to Rollover a TD - requires date data, interest, issuing institue, deposit amount, rollover terms and kyc name data
     * Rollover terms requires a new interest percent, institute, duration, and with interest boolean
     */
    @POST
    @Path("rollover_td")
    fun rolloverTD(@QueryParam("td_value") tdValue: Int, @QueryParam("offering_institute") offeringInstitue:String,
                 @QueryParam("interest_percent") interestPercent: Float, @QueryParam("duration") duration: Int,
                 @QueryParam("customer_fname") firstName: String, @QueryParam("customer_lname") lastName: String,
                 @QueryParam("customer_anum") accountNum: String, @QueryParam("start_date") startDate: String,
                   @QueryParam("new_interest") newInterest: Float, @QueryParam("new_institute") newInstitute: String,
                   @QueryParam("new_duration") newDuration: Int, @QueryParam("with_interest") withInterest: Boolean) : Response {

        val issuingInstitute = rpcOps.networkMapSnapshot().filter { it.legalIdentities.first().name.organisation == offeringInstitue }.first().legalIdentities.first()
        val newInstituteParty = rpcOps.networkMapSnapshot().filter { it.legalIdentities.first().name.organisation == newInstitute }.first().legalIdentities.first()
        val rolloverTerms = TermDeposit.RolloverTerms(newInterest, newInstituteParty, newDuration, withInterest)
        val kyc = KYC.KYCNameData(firstName, lastName, accountNum)
        val startDateActual = LocalDateTime.parse(startDate)
        val dateData = TermDeposit.DateData(LocalDateTime.MIN, duration)
        val depositAmount = AMOUNT(tdValue, USD)

        return try {
            val flowHandle = rpcOps.startFlow(RolloverTD::RolloverInitiator, dateData, interestPercent, issuingInstitute, depositAmount, rolloverTerms, kyc)
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

    /** Get all KYC data known by this node
     * returns a JSON containing an array of KYC states (mappings). Each KYC data mapping is in the following format
     * {firstName: String, lastName: String, accountNum: String, uniqueIdentifier: UniqueIdentifier}
     */
    @GET
    @Path("kyc")
    @Produces(MediaType.APPLICATION_JSON)
    fun getDeposits(): Map<String, List<Map<String, Any>>> {
        val kycStates = rpcOps.vaultQueryBy<KYC.State>().states
        return mapOf("kyc" to kycStates.map { mapOf("firstName" to it.state.data.firstName, "lastName" to it.state.data.lastName,
                "accountNum" to it.state.data.accountNum, "uniqueIdentifier" to it.state.data.linearId) })
    }

    /**POST call to create new KYC data - first name, last name and account number must all be supplied.
     *
     */
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

    /**POST call to update KYC data - the client ID (states unique identifier) must be supplied
     *
     */
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

