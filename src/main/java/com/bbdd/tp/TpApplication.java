package com.bbdd.tp;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Media Timeline Service.
 *
 * <p>Implements a polyglot persistence architecture using PostgreSQL for relational
 * catalog metadata and MongoDB for temporal timeline bucket storage, inspired by the
 * <a href="https://netflixtechblog.com/netflix-mediadatabase-media-timeline-data-model-4e657e6ffe93">
 * Netflix MediaDatabase Media Timeline</a> data model.</p>
 */
@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "Media Timeline Service API",
        version = "3.0",
        description = "Polyglot persistence API demonstrating three dual-engine (PostgreSQL + MongoDB) "
                + "provisioning strategies with synchronized rollback: Saga compensation, "
                + "Spring-coordinated transactions, and manual JDBC/Mongo session coordination."
))
public class TpApplication {

    static void main(String[] args) {
        SpringApplication.run(TpApplication.class, args);
    }

}
