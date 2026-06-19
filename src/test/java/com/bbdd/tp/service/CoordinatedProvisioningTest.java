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
class CoordinatedProvisioningTest {

    @Autowired
    private ComponentProvisioningService provisioningService;

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
    void testCoordinatedTransactionCommitsBothDatabasesOnSuccess() {
        UUID componentId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        ComponentProvisionRequest request = new ComponentProvisionRequest(
                componentId, trackId, assetId, 24000, 1001, 1920, 1080, "video_face_detection"
        );

        provisioningService.provisionCoordinated(request);

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
    void testCoordinatedTransactionRollsBackBothDatabasesOnMongoFailure() {
        UUID componentId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        // Pre-insert a conflicting document to cause the nested MongoDB write to fail
        Document conflictDoc = new Document("component_id", componentId.toString())
                .append("sabotage", "This will trigger a duplicate key error on the coordinated Mongo insert");
        mongoTemplate.getCollection("timeline_buckets").insertOne(conflictDoc);

        ComponentProvisionRequest request = new ComponentProvisionRequest(
                componentId, trackId, assetId, 24000, 1001, 1920, 1080, "video_face_detection"
        );

        // The nested Mongo transaction should fail, propagating up and rolling back the outer JPA transaction
        assertThrows(Exception.class, () -> provisioningService.provisionCoordinated(request));

        // Verify PostgreSQL row was rolled back by the outer JPA transaction
        boolean postgresRecordExists = componentRepository.existsById(componentId);
        assertThat(postgresRecordExists)
                .as("PostgreSQL record should have been rolled back due to Mongo failure in coordinated transaction")
                .isFalse();

        // Verify MongoDB still only has the pre-existing conflict document
        long mongoBucketCount = mongoTemplate.getCollection("timeline_buckets")
                .countDocuments(new Document("component_id", componentId.toString()));
        assertThat(mongoBucketCount)
                .as("Only the pre-existing conflict document should exist in MongoDB")
                .isEqualTo(1);
    }
}

