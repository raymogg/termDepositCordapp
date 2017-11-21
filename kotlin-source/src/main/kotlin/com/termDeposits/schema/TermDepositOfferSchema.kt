package com.termDeposit.schema

import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.LocalDateTime
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for Term Deposit Offers
 */
object TermDepositOfferSchema

/**
 * An Term Deposit offer state schema.
 */
object TDOSchemaV1 : MappedSchema(
        schemaFamily = TermDepositOfferSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentIOU::class.java)) {
    @Entity
    @Table(name = "TDOffer_states")
    class PersistentIOU(
            @Column(name = "start_date")
            var startDate: LocalDateTime,

            @Column(name = "end_date")
            var endDate: LocalDateTime,

            @Column(name = "interest")
            var interest: Float,

            @Column(name = "offering_institue_key")
            var institute: String
    ) : PersistentState()
}