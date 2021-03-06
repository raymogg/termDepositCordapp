package com.termDeposit.schema

import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for Term Deposit states
 */
object TermDepositSchema

/**
 * A Term Deposit state schema.
 */
object TDSchemaV1 : MappedSchema(
        schemaFamily = TermDepositSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentTDSchema::class.java)) {
    @Entity
    @Table(name = "TD_states")
    class PersistentTDSchema(
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