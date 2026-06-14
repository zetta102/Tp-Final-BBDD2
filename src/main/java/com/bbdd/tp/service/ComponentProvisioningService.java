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

@Service
public class ComponentProvisioningService {

    private final ComponentRepository componentRepository;
    private final MongoTemplate mongoTemplate;
    private final TransactionTemplate mongoTransactionTemplate;
    private final TransactionTemplate jpaTransactionTemplate;

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

    private void writeInitialBucketToMongo(ComponentProvisionRequest request) {
        Document bucketDoc = new Document()
                .append("asset_id", request.assetId().toString())
                .append("track_id", request.trackId().toString())
                .append("component_id", request.componentId().toString())
                .append("bucket_id", 1)
                .append("bucket_start_time", 0.0)
                .append("bucket_end_time", 60.0)
                .append("events", new ArrayList<>());

        mongoTemplate.insert(bucketDoc, "timeline_buckets");
    }
}