package com.egm.stellio.entity.model

import com.egm.stellio.entity.util.NgsiLdParsingUtils
import com.egm.stellio.entity.util.NgsiLdParsingUtils.JSONLD_VALUE_KW
import com.egm.stellio.entity.util.NgsiLdParsingUtils.NGSILD_DATE_TIME_TYPE
import com.egm.stellio.entity.util.NgsiLdParsingUtils.NGSILD_ENTITY_TYPE
import com.fasterxml.jackson.annotation.JsonIgnore
import org.neo4j.ogm.annotation.Id
import org.neo4j.ogm.annotation.NodeEntity
import org.neo4j.ogm.annotation.Relationship
import org.neo4j.ogm.annotation.Transient
import java.time.OffsetDateTime
import java.util.*

@NodeEntity
open class Attribute(

    @Transient
    val attributeType: String,

    var observedAt: OffsetDateTime? = null,

    @JsonIgnore
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @JsonIgnore
    var modifiedAt: OffsetDateTime? = null,

    @Relationship(type = "HAS_VALUE")
    val properties: MutableList<Property> = mutableListOf(),

    @Relationship(type = "HAS_OBJECT")
    val relationships: MutableList<com.egm.stellio.entity.model.Relationship> = mutableListOf()

) {
    @Id
    val id: String = "urn:ngsi-ld:$attributeType:${UUID.randomUUID()}"

    open fun serializeCoreProperties(): MutableMap<String, Any> {
        val resultEntity = mutableMapOf<String, Any>()

        resultEntity[NgsiLdParsingUtils.NGSILD_CREATED_AT_PROPERTY] = mapOf(
            NGSILD_ENTITY_TYPE to NGSILD_DATE_TIME_TYPE,
            JSONLD_VALUE_KW to createdAt
        )

        modifiedAt?.run {
            resultEntity[NgsiLdParsingUtils.NGSILD_MODIFIED_AT_PROPERTY] = mapOf(
                NGSILD_ENTITY_TYPE to NGSILD_DATE_TIME_TYPE,
                JSONLD_VALUE_KW to this
            )
        }

        observedAt?.run {
            resultEntity[NgsiLdParsingUtils.NGSILD_OBSERVED_AT_PROPERTY] = mapOf(
                NGSILD_ENTITY_TYPE to NGSILD_DATE_TIME_TYPE,
                JSONLD_VALUE_KW to this
            )
        }

        return resultEntity
    }
}