package com.bbdd.tp.service;


import com.bbdd.tp.model.ComponentEntity;
import com.bbdd.tp.model.ComponentProvisionRequest;
import com.bbdd.tp.repository.ComponentRepository;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;

/**
 * Service responsible for provisioning components across PostgreSQL and MongoDB
 * using Spring-managed transaction abstractions.
 *
 * <p>Exposes two dual-engine write strategies:</p>
 * <ul>
 *   <li><strong>Saga compensation</strong> ({@link #provisionWithSaga}) — commits SQL first,
 *       then attempts NoSQL; deletes the SQL row as compensation if NoSQL fails.</li>
 *   <li><strong>Spring-coordinated</strong> ({@link #provisionCoordinated}) — nests a MongoDB
 *       {@link TransactionTemplate} inside a JPA {@link TransactionTemplate} so that a failure
 *       in either engine rolls back both.</li>
 * </ul>
 */
@Service
public class ComponentProvisioningService {

    private final ComponentRepository componentRepository;
    private final MongoTemplate mongoTemplate;
    private final TransactionTemplate mongoTransactionTemplate;
    private final TransactionTemplate jpaTransactionTemplate;

    /**
     * @param componentRepository      JPA repository for the {@code components} table
     * @param mongoTemplate            Spring MongoDB template with session synchronization enabled
     * @param mongoTransactionTemplate transaction template backed by {@code MongoTransactionManager}
     * @param jpaTransactionTemplate   transaction template backed by {@code JpaTransactionManager}
     */
    public ComponentProvisioningService(
            ComponentRepository componentRepository,
            MongoTemplate mongoTemplate,
            @Qualifier("mongoTransactionTemplate") TransactionTemplate mongoTransactionTemplate,
            @Qualifier("jpaTransactionTemplate") TransactionTemplate jpaTransactionTemplate) {
        this.componentRepository = componentRepository;
        this.mongoTemplate = mongoTemplate;
        this.mongoTransactionTemplate = mongoTransactionTemplate;
        this.jpaTransactionTemplate = jpaTransactionTemplate;
    }

    /**
     * Provisions a component using the <em>Saga compensation</em> pattern.
     *
     * <ol>
     *   <li>Inserts the catalog row into PostgreSQL (committed immediately).</li>
     *   <li>Inserts the initial timeline bucket into MongoDB.</li>
     *   <li>If step 2 fails, deletes the PostgreSQL row as a compensating action.</li>
     * </ol>
     *
     * @param request the provisioning payload
     * @throws RuntimeException wrapping the original MongoDB exception after compensation
     */
    public void provisionWithSaga(ComponentProvisionRequest request) {
        jpaTransactionTemplate.execute(status -> {
            createComponentEntity(request);
            return null;
        });

        try {
            writeInitialBucketToMongo(request);
        } catch (Exception ex) {
            jpaTransactionTemplate.execute(status -> {
                componentRepository.deleteById(request.componentId());
                return null;
            });
            throw new RuntimeException("NoSQL document write failed. SQL catalog item removed by Saga compensation.", ex);
        }
    }

    /**
     * Creates and persists a {@link ComponentEntity} in PostgreSQL from the given request.
     *
     * @param request the source data for the new entity
     */
    private void createComponentEntity(ComponentProvisionRequest request) {
        ComponentEntity entity = new ComponentEntity();
        entity.setComponentId(request.componentId());
        entity.setTrackId(request.trackId());
        entity.setEventRateNumerator(request.eventRateNumerator());
        entity.setEventRateDenominator(request.eventRateDenominator());
        entity.setXSize(request.xSize());
        entity.setYSize(request.ySize());
        entity.setMetadata("{\"algorithm\": \"" + request.algorithmName() + "\"}");
        componentRepository.save(entity);
    }

    /**
     * Provisions a component using <em>Spring-coordinated nested transactions</em>.
     *
     * <p>The outer JPA transaction wraps both the PostgreSQL insert and an inner
     * MongoDB transaction. If the MongoDB insert throws, the exception propagates
     * and Spring rolls back the outer JPA transaction as well.</p>
     *
     * @param request the provisioning payload
     */
    public void provisionCoordinated(ComponentProvisionRequest request) {
        jpaTransactionTemplate.execute(status -> {
            createComponentEntity(request);

            mongoTransactionTemplate.execute(mongoStatus -> {
                writeInitialBucketToMongo(request);
                return null;
            });
            return null;
        });
    }

    /**
     * Inserts an initial (empty) timeline bucket document into the MongoDB
     * {@code timeline_buckets} collection.
     *
     * <p>The bucket covers the first 60-second window [0.0, 60.0) with zero events,
     * following the Netflix Media Timeline fixed-window bucket pattern.</p>
     *
     * @param request the source data containing asset, track, and component identifiers
     */
    private void writeInitialBucketToMongo(ComponentProvisionRequest request) {
        Document bucketDoc = new Document()
                .append("asset_id", request.assetId().toString())
                .append("track_id", request.trackId().toString())
                .append("component_id", request.componentId().toString())
                .append("bucket_id", 1)
                .append("bucket_start_time", 0.0)
                .append("bucket_end_time", 60.0)
                .append("event_count", 0)
                .append("events", new ArrayList<>());

        mongoTemplate.insert(bucketDoc, "timeline_buckets");
    }
}