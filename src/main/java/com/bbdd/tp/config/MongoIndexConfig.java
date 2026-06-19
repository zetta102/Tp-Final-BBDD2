package com.bbdd.tp.config;

import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Ensures required MongoDB indexes exist on the {@code timeline_buckets} collection
 * at application startup.
 *
 * <p>Creates the following indexes:</p>
 * <ul>
 *   <li><strong>2dsphere</strong> on {@code events.regions.centroid} — enables GeoJSON
 *       spatial queries for spatio-temporal event lookups.</li>
 *   <li><strong>Compound</strong> on {@code {component_id, bucket_start_time, bucket_end_time}} —
 *       accelerates temporal range scans per component.</li>
 *   <li><strong>Unique compound</strong> on {@code {component_id, bucket_id}} — prevents
 *       duplicate buckets for the same component.</li>
 * </ul>
 */
@Configuration
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;

    /**
     * @param mongoTemplate the session-aware MongoDB template
     */
    public MongoIndexConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Called after the application context is fully initialized.
     * Creates indexes idempotently (MongoDB ignores creation if the index already exists).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        var collection = mongoTemplate.getCollection("timeline_buckets");

        // 2dsphere index on events.regions.centroid for GeoJSON spatial queries
        collection.createIndex(
                new Document("events.regions.centroid", "2dsphere")
        );

        // Compound index for efficient temporal range scans per component
        collection.createIndex(
                new Document("component_id", 1)
                        .append("bucket_start_time", 1)
                        .append("bucket_end_time", 1)
        );

        // Unique compound index to prevent duplicate buckets per component
        collection.createIndex(
                new Document("component_id", 1)
                        .append("bucket_id", 1),
                new IndexOptions().unique(true)
        );
    }
}
