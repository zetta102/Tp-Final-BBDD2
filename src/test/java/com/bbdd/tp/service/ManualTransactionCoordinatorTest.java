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
class ManualTransactionCoordinatorTest {

    @Autowired
    private CoordinatedTransactionCoordinator manualCoordinator;

    @Autowired
    private ComponentRepository componentRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        cleanupDatabase();
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
    void testManualTransactionCommitsBothDatabasesOnSuccess() throws Exception {
        UUID componentId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        ComponentProvisionRequest request = new ComponentProvisionRequest(
                componentId, trackId, assetId, 24000, 1001, 1920, 1080, "video_face_detection"
        );

        manualCoordinator.provisionComponentManual(request);

        boolean postgresRecordExists = componentRepository.existsById(componentId);
        assertThat(postgresRecordExists)
                .as("PostgreSQL transaction should be committed")
                .isTrue();

        long mongoBucketCount = mongoTemplate.getCollection("timeline_buckets")
                .countDocuments(new Document("component_id", componentId.toString()));
        assertThat(mongoBucketCount)
                .as("MongoDB transaction should be committed")
                .isEqualTo(1);
    }

    @Test
    void testManualTransactionRollbacksBothDatabasesOnFailure() {
        UUID componentId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        Document conflictDoc = new Document("component_id", componentId.toString())
                .append("sabotage", "This will cause the transaction's insert to fail");
        mongoTemplate.getCollection("timeline_buckets").insertOne(conflictDoc);

        ComponentProvisionRequest request = new ComponentProvisionRequest(
                componentId, trackId, assetId, 24000, 1001, 1920, 1080, "video_face_detection"
        );

        Exception thrown = assertThrows(Exception.class, () -> {
            manualCoordinator.provisionComponentManual(request);
        });

        assertThat(thrown.getMessage()).contains("E11000 duplicate key error");

        boolean postgresRecordExists = componentRepository.existsById(componentId);
        assertThat(postgresRecordExists)
                .as("PostgreSQL transaction should have been rolled back due to Mongo failure")
                .isFalse();

        long mongoBucketCount = mongoTemplate.getCollection("timeline_buckets")
                .countDocuments(new Document("component_id", componentId.toString()));
        assertThat(mongoBucketCount)
                .as("No new documents should have been written to MongoDB by the aborted transaction")
                .isEqualTo(1);
    }
}