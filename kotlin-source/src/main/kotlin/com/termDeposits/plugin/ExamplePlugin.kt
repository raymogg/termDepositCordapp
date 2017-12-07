package com.termDeposits.plugin

import com.termDeposits.contract.*
import com.termDeposits.flow.TermDeposit.*
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.transactions.TransactionBuilder
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

class ExamplePlugin : WebServerPluginRegistry {
    /**
     * A list of classes that expose web APIs.
     */
    //override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::ExampleApi))
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf()

    /**
     * A list of directories in the resources directory that will be served by Jetty under /web.
     */
    override val staticServeDirs: Map<String, String> = mapOf(
            // This will serve the exampleWeb directory in resources to /web/example
            "example" to javaClass.classLoader.getResource("exampleWeb").toExternalForm()
    )
}


class SerilizationPlugin: SerializationWhitelist {
    /**
     * Whitelisting the required types for serialisation by the Corda node.
     */
    override val whitelist: List<Class<*>> = listOf(
            TermDeposit::class.java,
            TermDepositOffer::class.java,
            TermDepositOffer.State::class.java,
            TermDeposit.State::class.java,
            IssueOffer.Initiator::class.java,
            IssueTD.Initiator::class.java,
            IssueTD.Acceptor::class.java,
            KYC::class.java,
            KYC.State::class.java,
            CreateKYC.Creator::class.java,
            RolloverTD.RolloverInitiator::class.java,
            RolloverTD.RolloverAcceptor::class.java,
            RedeemTD.RedemptionAcceptor::class.java,
            RedeemTD.RedemptionInitiator::class.java,
            TDRetreivalFlows.TDRetreivalFlow::class.java,
            TDRetreivalFlows.TDRetreivalFlowID::class.java,
            OfferRetrievalFlow::class.java,
            TransactionBuilder::class.java,
            Boolean::class.java,
            UniqueIdentifier::class.java
    )
}