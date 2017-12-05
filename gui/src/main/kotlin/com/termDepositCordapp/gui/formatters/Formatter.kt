package com.termDepositCordapp.gui.formatters


interface Formatter<in T> {
    fun format(value: T): String
}
