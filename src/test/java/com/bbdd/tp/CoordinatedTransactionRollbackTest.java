package com.bbdd.tp;


import com.bbdd.tp.model.ComponentEntity;
import com.bbdd.tp.model.ComponentProvisionRequest;
import com.bbdd.tp.repository.ComponentRepository;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CoordinatedTransactionRollbackTest {

    @Autowired
    private ComponentRepository componentRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    @Qualifier("jpaTransactionTemplate")
    private TransactionTemplate jpaTransactionTemplate;

    @Autowired
    @Qualifier("mongoTransactionTemplate")
    private TransactionTemplate mongoTransactionTemplate;

    @BeforeEach
    void setUp() {
        cleanupDatabase();
    }

    @AfterEach
    void tearDown() {
        cleanupDatabase();
    }

    private void cleanupDatabase() {
        componentRepository.deleteAll();
        if (mongoTemplate.collectionExists("timeline_buckets")) {
            mongoTemplate.dropCollection("timeline_buckets");
        }
        mongoTemplate.createCollection("timeline_buckets");
    }

    @Test
    void testCoordinatedTransactionRollbacksBothDatabasesOnFailure() {
        UUID componentId = UUID.fromString("2d9e8f1a-5b6c-4c2d-9e0f-3a4b5c6d7e8f");
        UUID trackId = UUID.fromString("0c5f2b8a-3c4a-4d2b-aa5e-8d0768b8e0a2");
        UUID assetId = UUID.fromString("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d");

        ComponentProvisionRequest request = new ComponentProvisionRequest(
                componentId,
                trackId,
                assetId,
                24000,
                1001,
                1920,
                1080,
                "video_face_detection"
        );

        assertThrows(RuntimeException.class, () -> {
            jpaTransactionTemplate.execute(status -> {

                ComponentEntity entity = new ComponentEntity();
                entity.setComponentId(request.componentId());
                entity.setTrackId(request.trackId());
                entity.setEventRateNumerator(request.eventRateNumerator());
                entity.setEventRateDenominator(request.eventRateDenominator());
                entity.setXSize(request.xSize());
                entity.setYSize(request.ySize());
                entity.setMetadata("{\"algorithm\": \"" + request.algorithmName() + "\"}");
                componentRepository.save(entity);

                mongoTransactionTemplate.execute(mongoStatus -> {
                    Document bucketDoc = new Document()
                            .append("asset_id", request.assetId().toString())
                            .append("track_id", request.trackId().toString())
                            .append("component_id", request.componentId().toString())
                            .append("bucket_id", 1)
                            .append("bucket_start_time", 0.0)
                            .append("bucket_end_time", 60.0)
                            .append("events", new ArrayList<>());

                    mongoTemplate.insert(bucketDoc, "timeline_buckets");
                    return null;
                });

                throw new RuntimeException("Forced system failure to trigger coordinated database rollback.");
            });
        });

        // Assert: Ensure PostgreSQL rollback
        boolean postgresRecordExists = componentRepository.existsById(componentId);
        assertThat(postgresRecordExists)
                .as("PostgreSQL transaction should have been rolled back")
                .isFalse();

        // Assert: Ensure MongoDB rollback
        long mongoBucketCount = mongoTemplate.getCollection("timeline_buckets")
                .countDocuments(new Document("component_id", componentId.toString()));
        assertThat(mongoBucketCount)
                .as("MongoDB transaction should have been aborted and rolled back")
                .isZero();
    }
}