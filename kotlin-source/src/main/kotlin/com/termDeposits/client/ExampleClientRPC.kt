package com.example.client

import com.example.state.IOUState
import com.termDeposits.contract.TermDeposit
import com.termDeposits.contract.TermDepositOffer
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger

/**
 *  Demonstration of using the CordaRPCClient to connect to a Corda Node and
 *  steam some State data from the node.
 **/

fun main(args: Array<String>) {
    ExampleClientRPC().main(args)
}

private class ExampleClientRPC {
    companion object {
        val logger: Logger = loggerFor<ExampleClientRPC>()
        private fun logState(state: StateAndRef<*>) = logger.info("{}", state.state.data)
    }

    fun main(args: Array<String>) {
        require(args.size == 1) { "Usage: ExampleClientRPC <node address>" }
        val nodeAddress = NetworkHostAndPort.parse(args[0])
        val client = CordaRPCClient(nodeAddress)

        // Can be amended in the com.example.MainKt file.
        val proxy = client.start("user1", "test").proxy

        // Grab all signed transactions and all future signed transactions.
        //val (snapshot, updates) = proxy.vaultTrack(IOUState::class.java)
        //val (snapshot, updates) = proxy.vaultTrack(TermDepositOffer.State::class.java)

        val (snapshot, updates) = proxy.vaultTrack(TermDeposit.State::class.java)

        // Log the 'placed' TDO states and listen for new ones.
        snapshot.states.forEach { logState(it) }
        updates.toBlocking().subscribe { update ->
            update.produced.forEach { logState(it) }
        }

        // Log the 'placed' TD states and listen for new ones.
//        snapshot2.states.forEach { logState(it) }
//        updates2.toBlocking().subscribe { update ->
//            update.produced.forEach { logState(it) }
//        }
    }
}
