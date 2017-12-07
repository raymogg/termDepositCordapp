package com.termDeposits.schema

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for Term Deposit states
 */
object KYCSchema

/**
 * A Term Deposit state schema.
 */
object KYCSchemaV1 : MappedSchema(
        schemaFamily = KYCSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentTDSchema::class.java)) {
    @Entity
    @Table(name = "KYC_States")
    class PersistentTDSchema(
            @Column(name = "first_name")
            var firstName: String,

            @Column(name = "last_name")
            var lastName: String,

            @Column(name = "account_num")
            var accountNum: String

//            @Column(name = "linear_id")
//            var linearID: UniqueIdentifier

    ) : PersistentState()
}