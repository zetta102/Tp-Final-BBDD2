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

@Configuration
public class TransactionConfig {

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory dbFactory, MongoConverter converter) {
        MongoTemplate template = new MongoTemplate(dbFactory, converter);
        template.setSessionSynchronization(SessionSynchronization.ALWAYS);

        return template;
    }

    @Primary
    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean(name = "mongoTransactionManager")
    public MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }

    @Bean(name = "mongoTransactionTemplate")
    public TransactionTemplate mongoTransactionTemplate(MongoTransactionManager mongoTransactionManager) {
        return new TransactionTemplate(mongoTransactionManager);
    }

    @Bean(name = "jpaTransactionTemplate")
    public TransactionTemplate jpaTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}