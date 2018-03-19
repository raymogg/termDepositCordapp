package com.termDepositCordapp.gui.formatters

import jfxtras.scene.layout.HBox
import net.corda.core.identity.CordaX500Name
import org.bouncycastle.asn1.x500.X500Name

object PartyNameFormatter {
    val short = object : Formatter<CordaX500Name> {
        override fun format(value: CordaX500Name): String = value.organisation
    }

    val full = object : Formatter<CordaX500Name> {
        override fun format(value: CordaX500Name): String = value.toString()
    }
}
