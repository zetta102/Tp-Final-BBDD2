package com.bbdd.tp.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.SessionSynchronization;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spring configuration that sets up dual transaction infrastructure for the polyglot
 * persistence layer.
 *
 * <p>Defines separate {@link PlatformTransactionManager} beans for JPA (PostgreSQL) and
 * MongoDB, each wrapped in a named {@link TransactionTemplate} for programmatic transaction
 * demarcation used by {@link com.bbdd.tp.service.ComponentProvisioningService}.</p>
 *
 * <p>Also configures the {@link MongoTemplate} with {@link SessionSynchronization#ALWAYS}
 * to ensure it participates in Spring-managed MongoDB transactions.</p>
 */
@Configuration
public class TransactionConfig {

    /**
     * Creates a session-aware {@link MongoTemplate} that automatically binds to
     * the current MongoDB transaction session when one is active.
     *
     * @param dbFactory the MongoDB database factory
     * @param converter the MongoDB object-document converter
     * @return a configured {@link MongoTemplate} instance
     */
    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory dbFactory, MongoConverter converter) {
        MongoTemplate template = new MongoTemplate(dbFactory, converter);
        template.setSessionSynchronization(SessionSynchronization.ALWAYS);

        return template;
    }

    /**
     * Primary JPA transaction manager backed by PostgreSQL via Hibernate.
     *
     * @param entityManagerFactory the JPA entity manager factory
     * @return the primary {@link PlatformTransactionManager}
     */
    @Primary
    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    /**
     * MongoDB transaction manager, requires a replica set to support multi-document transactions.
     *
     * @param dbFactory the MongoDB database factory
     * @return a {@link MongoTransactionManager} instance
     */
    @Bean(name = "mongoTransactionManager")
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }

    /**
     * Transaction template for programmatic MongoDB transaction demarcation.
     *
     * @param mongoTransactionManager the MongoDB transaction manager
     * @return a named {@link TransactionTemplate} for MongoDB operations
     */
    @Bean(name = "mongoTransactionTemplate")
    public TransactionTemplate mongoTransactionTemplate(MongoTransactionManager mongoTransactionManager) {
        return new TransactionTemplate(mongoTransactionManager);
    }

    /**
     * Transaction template for programmatic JPA/PostgreSQL transaction demarcation.
     *
     * @param transactionManager the primary JPA transaction manager
     * @return a named {@link TransactionTemplate} for JPA operations
     */
    @Bean(name = "jpaTransactionTemplate")
    public TransactionTemplate jpaTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}