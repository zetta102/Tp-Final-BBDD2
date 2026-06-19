package com.bbdd.tp.config;

import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;

    public MongoIndexConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

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

