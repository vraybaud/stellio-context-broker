package com.egm.datahub.context.registry

import org.neo4j.ogm.config.ClasspathConfigurationSource
import org.neo4j.ogm.session.SessionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class Neo4jEmbeddedConfiguration {

    @Bean
    fun sessionFactory(): SessionFactory {
        return SessionFactory(configuration(), "com.egm.datahub.context.registry.model")
    }

    @Bean
    fun configuration(): org.neo4j.ogm.config.Configuration {
        val properties = ClasspathConfigurationSource("ogm.properties")
        return org.neo4j.ogm.config.Configuration.Builder(properties).build()
    }
}
