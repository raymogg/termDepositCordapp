package com.example.flow

import net.corda.core.identity.Party
import net.corda.node.internal.StartedNode
import net.corda.testing.*
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Before

class IssueOfferTest {
    lateinit var mockNet: MockNetwork
    lateinit var a: StartedNode<MockNetwork.MockNode>
    lateinit var b: StartedNode<MockNetwork.MockNode>
    lateinit var notary: Party
    lateinit var megaCorpServices: MockServices
    lateinit var notaryServices: MockServices

    @Before
    fun setup() {
        setCordappPackages("com.termDeposits")
        megaCorpServices = MockServices(MEGA_CORP_KEY)
        notaryServices = MockServices(DUMMY_NOTARY_KEY)
        mockNet = MockNetwork()
        val nodes = mockNet.createSomeNodes()
        a = nodes.partyNodes[0]
        b = nodes.partyNodes[1]
        //a.internals.registerInitiatedFlow(IssueOffer::class.java)
        //b.internals.registerInitiatedFlow(TestResponseFlow::class.java)
        mockNet.runNetwork()
        notary = a.services.getDefaultNotary()
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
        unsetCordappPackages()
    }
}