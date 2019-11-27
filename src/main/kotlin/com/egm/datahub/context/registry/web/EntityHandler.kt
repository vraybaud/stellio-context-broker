package com.egm.datahub.context.registry.web

import com.egm.datahub.context.registry.model.EntityEvent
import com.egm.datahub.context.registry.model.EventType
import com.egm.datahub.context.registry.repository.Neo4jRepository
import com.egm.datahub.context.registry.service.Neo4jService
import com.egm.datahub.context.registry.service.NgsiLdParserService
import org.neo4j.ogm.config.ObjectMapperFactory.objectMapper
import org.neo4j.ogm.response.model.NodeModel
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.*
import org.springframework.web.reactive.function.server.bodyToMono
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import java.net.URI

@Component
class EntityHandler(
    private val ngsiLdParserService: NgsiLdParserService,
    private val neo4JRepository: Neo4jRepository,
    private val neo4jService: Neo4jService,
    private val applicationEventPublisher: ApplicationEventPublisher
) {

    private val logger = LoggerFactory.getLogger(EntityHandler::class.java)

    fun generatesProblemDetails(list: List<String>): String {
        return objectMapper().writeValueAsString(mapOf("ProblemDetails" to list))
    }

    fun create(req: ServerRequest): Mono<ServerResponse> {
        val contextLink = extractLinkContextFromLinkHeader(req)

        return req.bodyToMono<String>()
            .map {
                val urn = ngsiLdParserService.run { extractEntityUrn(it) }
                if (neo4JRepository.checkExistingUrn(urn)) {
                    throw AlreadyExistsException("Already Exists")
                }
                val type = ngsiLdParserService.extractEntityType(it)
                if (!ngsiLdParserService.checkResourceNSmatch(contextLink, type)) {
                    throw BadRequestDataException("the NS provided in the Link Header is not correct or you're trying to access an undefined entity type")
                }
                ngsiLdParserService.parseEntity(it)
            }
            .map {
                neo4JRepository.createEntity(it.entityUrn, it.entityStatements, it.relationshipStatements)
                it
            }.map {
                val entityEvent = EntityEvent(it.entityType, it.entityUrn, EventType.POST, it.ngsiLdPayload)
                applicationEventPublisher.publishEvent(entityEvent)
                it.entityUrn
            }
            .flatMap {
                created(URI("/ngsi-ld/v1/entities/$it")).build()
            }.onErrorResume {
                when (it) {
                    is AlreadyExistsException -> status(HttpStatus.CONFLICT).build()
                    is InternalErrorException -> status(HttpStatus.INTERNAL_SERVER_ERROR).build()
                    is BadRequestDataException -> status(HttpStatus.BAD_REQUEST).body(BodyInserters.fromValue(it.localizedMessage))
                    else -> badRequest().body(BodyInserters.fromValue(generatesProblemDetails(listOf(it.message.toString()))))
                }
            }
    }

    fun getEntities(req: ServerRequest): Mono<ServerResponse> {
        val type = req.queryParam("type").orElse("")
        var q = req.queryParams()["q"].orEmpty()

        val contextLink = extractLinkContextFromLinkHeader(req)

        if (q.isNullOrEmpty() && type.isNullOrEmpty()) {
            return badRequest().body(BodyInserters.fromValue("query or type have to be specified: generic query on entities NOT yet supported"))
        }
        if (!q.isNullOrEmpty()) {
            q = ngsiLdParserService.prependNsToQuery(q, contextLink)
        }

        if (!ngsiLdParserService.checkResourceNSmatch(contextLink, type)) {
            return badRequest().body(BodyInserters.fromValue("th NS provided in the Link Header is not correct or you're trying to access an undefined entity type"))
        }

        return "".toMono()
            .map {
                val prefixedType = ngsiLdParserService.addPrefixToType(contextLink, type)
                neo4JRepository.getEntities(q, prefixedType)
            }
            .map {
                it.map {
                    neo4jService.queryResultToNgsiLd(it.get("n") as NodeModel)
                }
            }
            .flatMap {
                ok().body(BodyInserters.fromValue(it))
            }
            .onErrorResume {
                badRequest().body(BodyInserters.fromValue(generatesProblemDetails(listOf(it.message.toString()))))
            }
    }

    fun getByURI(req: ServerRequest): Mono<ServerResponse> {
        val uri = req.pathVariable("entityId")
        return uri.toMono()
            .map {
                if (!neo4JRepository.checkExistingUrn(uri)) throw ResourceNotFoundException("Entity Not Found")
                neo4JRepository.getNodeByURI(it)
            }
            .map {
                neo4jService.queryResultToNgsiLd(it.get("n") as NodeModel)
            }
            .flatMap {
                ok().body(BodyInserters.fromValue(it))
            }
            .onErrorResume {
                when (it) {
                    is ResourceNotFoundException -> status(HttpStatus.NOT_FOUND).build()
                    else -> badRequest().body(BodyInserters.fromValue(generatesProblemDetails(listOf(it.message.toString()))))
                }
            }
    }

    fun updateEntity(req: ServerRequest): Mono<ServerResponse> {
        val uri = req.pathVariable("entityId")
        val type = getTypeFromURI(uri)
        val contextLink = extractLinkContextFromLinkHeader(req)

        return req.bodyToMono<String>()
            .map {
                if (!ngsiLdParserService.checkResourceNSmatch(contextLink, type)) {
                    throw BadRequestDataException("the NS provided in the Link Header is not correct or you're trying to access an undefined entity type")
                }
                ngsiLdParserService.ngsiLdToUpdateEntityQuery(it, uri)
            }
            .map {
                neo4JRepository.updateEntity(it)
            }
            .flatMap {
                status(HttpStatus.NO_CONTENT).build()
            }
            .onErrorResume {
                when (it) {
                    is ResourceNotFoundException -> status(HttpStatus.NOT_FOUND).body(BodyInserters.fromObject(it.localizedMessage))
                    else -> badRequest().body(BodyInserters.fromObject(it.localizedMessage))
                }
            }
    }

    fun updateAttribute(req: ServerRequest): Mono<ServerResponse> {
        val attr = req.pathVariable("attrId")
        val uri = req.pathVariable("entityId")
        val type = getTypeFromURI(uri)

        val contextLink = extractLinkContextFromLinkHeader(req)

        return req.bodyToMono<String>()
            .map {

                if (!ngsiLdParserService.checkResourceNSmatch(contextLink, type)) {
                    throw BadRequestDataException("the NS provided in the Link Header is not correct or you're trying to access an undefined entity type")
                }
                ngsiLdParserService.ngsiLdToUpdateEntityAttributeQuery(it, uri, attr)
            }
            .map {
                neo4JRepository.updateEntity(it)
            }
            .flatMap {
                status(HttpStatus.NO_CONTENT).build()
            }
            .onErrorResume {
                when (it) {
                    is ResourceNotFoundException -> status(HttpStatus.NOT_FOUND).body(BodyInserters.fromValue(it.localizedMessage))
                    else -> badRequest().body(BodyInserters.fromValue(it.localizedMessage))
                }
            }
    }

    fun delete(req: ServerRequest): Mono<ServerResponse> {
        val entityId = req.pathVariable("entityId")

        return entityId.toMono()
            .map {
                neo4JRepository.deleteEntity(entityId)
            }
            .flatMap {
                if (it.first >= 1)
                    noContent().build()
                else
                    notFound().build()
            }
            .onErrorResume {
                status(HttpStatus.INTERNAL_SERVER_ERROR).body(BodyInserters.fromObject(generatesProblemDetails(listOf(it.localizedMessage))))
            }
    }

    fun extractLinkContextFromLinkHeader(req: ServerRequest): String {
        return if (req.headers().header("Link").isNotEmpty() && req.headers().header("Link").get(0) != null)
            req.headers().header("Link")[0].split(";")[0].removePrefix("<").removeSuffix(">")
        else
            "https://uri.etsi.org/ngsi-ld/v1/ngsild.jsonld"
    }

    fun getTypeFromURI(uri: String): String {
        return uri.split(":")[2]
    }
}
