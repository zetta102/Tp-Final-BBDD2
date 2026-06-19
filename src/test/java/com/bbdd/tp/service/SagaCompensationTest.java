package com.bbdd.tp.service;

import com.bbdd.tp.model.ComponentProvisionRequest;
import com.bbdd.tp.repository.ComponentRepository;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class SagaCompensationTest {

    @Autowired
    private ComponentProvisioningService provisioningService;

    @Autowired
    private ComponentRepository componentRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        cleanupDatabase();
        // Create a unique index on component_id to force duplicate key errors
        mongoTemplate.getCollection("timeline_buckets")
                .createIndex(new Document("component_id", 1), new IndexOptions().unique(true));
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
    void testSagaCompensationRollsBackPostgresOnMongoFailure() {
        UUID componentId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        // Pre-insert a conflicting document to cause the MongoDB write to fail
        Document conflictDoc = new Document("component_id", componentId.toString())
                .append("sabotage", "This will trigger a duplicate key error on the Saga's Mongo insert");
        mongoTemplate.getCollection("timeline_buckets").insertOne(conflictDoc);

        ComponentProvisionRequest request = new ComponentProvisionRequest(
                componentId, trackId, assetId, 24000, 1001, 1920, 1080, "video_face_detection"
        );

        // The saga should fail on MongoDB write and compensate by deleting the PG row
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            provisioningService.provisionWithSaga(request);
        });

        assertThat(thrown.getMessage())
                .contains("NoSQL document write failed. SQL catalog item removed by Saga compensation.");

        // Verify PostgreSQL row was rolled back via saga compensation
        boolean postgresRecordExists = componentRepository.existsById(componentId);
        assertThat(postgresRecordExists)
                .as("PostgreSQL record should have been removed by Saga compensation")
                .isFalse();

        // Verify MongoDB still only has the pre-existing conflict document
        long mongoBucketCount = mongoTemplate.getCollection("timeline_buckets")
                .countDocuments(new Document("component_id", componentId.toString()));
        assertThat(mongoBucketCount)
                .as("Only the pre-existing conflict document should exist in MongoDB")
                .isEqualTo(1);
    }

    @Test
    void testSagaCommitsBothDatabasesOnSuccess() {
        UUID componentId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        ComponentProvisionRequest request = new ComponentProvisionRequest(
                componentId, trackId, assetId, 24000, 1001, 1920, 1080, "video_face_detection"
        );

        provisioningService.provisionWithSaga(request);

        // Verify PostgreSQL record exists
        boolean postgresRecordExists = componentRepository.existsById(componentId);
        assertThat(postgresRecordExists)
                .as("PostgreSQL record should be committed")
                .isTrue();

        // Verify MongoDB bucket was created
        long mongoBucketCount = mongoTemplate.getCollection("timeline_buckets")
                .countDocuments(new Document("component_id", componentId.toString()));
        assertThat(mongoBucketCount)
                .as("MongoDB bucket should be committed")
                .isEqualTo(1);
    }
}

