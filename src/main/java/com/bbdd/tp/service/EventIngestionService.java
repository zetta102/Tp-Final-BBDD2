package com.bbdd.tp.service;

import com.bbdd.tp.model.EventIngestRequest;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class EventIngestionService {

    private static final double BUCKET_WINDOW_SECONDS = 60.0;
    private static final int MAX_EVENTS_PER_BUCKET = 200;

    private final MongoTemplate mongoTemplate;

    public EventIngestionService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void appendEvent(UUID componentId, EventIngestRequest request) {
        String componentIdStr = componentId.toString();

        // Find the bucket whose fixed time window contains this event's startTime
        Document bucket = findBucketForTime(componentIdStr, request.startTime());

        if (bucket == null) {
            // No bucket exists for this time window — create one
            bucket = createBucketForTime(componentIdStr, request.startTime());
        }

        // Build the event document with GeoJSON centroid in each region
        Document eventDoc = buildEventDocument(request);

        // Atomically push event and increment event_count
        Query query = new Query(Criteria.where("_id").is(bucket.getObjectId("_id")));
        Update update = new Update()
                .push("events", eventDoc)
                .inc("event_count", 1);

        mongoTemplate.updateFirst(query, update, "timeline_buckets");

        // Check if bucket needs splitting (overflow → new adjacent bucket)
        Document updated = mongoTemplate.findOne(query, Document.class, "timeline_buckets");
        if (updated != null && updated.getInteger("event_count", 0) > MAX_EVENTS_PER_BUCKET) {
            splitBucket(updated);
        }
    }

    private Document findBucketForTime(String componentId, double eventStartTime) {
        double bucketStart = computeBucketStart(eventStartTime);
        double bucketEnd = bucketStart + BUCKET_WINDOW_SECONDS;

        Query query = new Query(Criteria.where("component_id").is(componentId)
                .and("bucket_start_time").is(bucketStart)
                .and("bucket_end_time").is(bucketEnd));

        return mongoTemplate.findOne(query, Document.class, "timeline_buckets");
    }

    private Document createBucketForTime(String componentId, double eventStartTime) {
        double bucketStart = computeBucketStart(eventStartTime);
        double bucketEnd = bucketStart + BUCKET_WINDOW_SECONDS;

        // Determine the next bucket_id for this component
        Query countQuery = new Query(Criteria.where("component_id").is(componentId));
        long existingBuckets = mongoTemplate.count(countQuery, "timeline_buckets");

        Document bucketDoc = new Document()
                .append("component_id", componentId)
                .append("bucket_id", (int) existingBuckets + 1)
                .append("bucket_start_time", bucketStart)
                .append("bucket_end_time", bucketEnd)
                .append("event_count", 0)
                .append("events", new ArrayList<>());

        mongoTemplate.insert(bucketDoc, "timeline_buckets");
        return bucketDoc;
    }

    private Document buildEventDocument(EventIngestRequest request) {
        List<Document> regionDocs = new ArrayList<>();
        for (EventIngestRequest.RegionInput region : request.regions()) {
            double centroidX = (region.xmin() + region.xmax()) / 2.0;
            double centroidY = (region.ymin() + region.ymax()) / 2.0;

            Document centroid = new Document("type", "Point")
                    .append("coordinates", List.of(centroidX, centroidY));

            Document regionDoc = new Document()
                    .append("xmin", region.xmin())
                    .append("xmax", region.xmax())
                    .append("ymin", region.ymin())
                    .append("ymax", region.ymax())
                    .append("centroid", centroid);

            regionDocs.add(regionDoc);
        }

        return new Document()
                .append("event_id", UUID.randomUUID().toString())
                .append("startTime", request.startTime())
                .append("endTime", request.endTime())
                .append("metadata", new Document("description", request.description()))
                .append("regions", regionDocs);
    }

    /**
     * Splits a full bucket by moving overflow events into a new bucket.
     * Uses the blog's fixed-time-window approach: the current bucket keeps
     * MAX_EVENTS_PER_BUCKET events; overflow events are moved to adjacent windows.
     */
    private void splitBucket(Document fullBucket) {
        List<Document> events = fullBucket.getList("events", Document.class);
        if (events == null || events.size() <= MAX_EVENTS_PER_BUCKET) {
            return;
        }

        // Keep first MAX_EVENTS_PER_BUCKET events in current bucket, move the rest
        List<Document> keep = new ArrayList<>(events.subList(0, MAX_EVENTS_PER_BUCKET));
        List<Document> overflow = new ArrayList<>(events.subList(MAX_EVENTS_PER_BUCKET, events.size()));

        // Update existing bucket to only keep the first N events
        Query query = new Query(Criteria.where("_id").is(fullBucket.getObjectId("_id")));
        Update update = new Update()
                .set("events", keep)
                .set("event_count", MAX_EVENTS_PER_BUCKET);
        mongoTemplate.updateFirst(query, update, "timeline_buckets");

        // Create a new overflow bucket for the next time window
        String componentId = fullBucket.getString("component_id");
        double currentEnd = fullBucket.getDouble("bucket_end_time");

        Query countQuery = new Query(Criteria.where("component_id").is(componentId));
        long existingBuckets = mongoTemplate.count(countQuery, "timeline_buckets");

        Document overflowBucket = new Document()
                .append("component_id", componentId)
                .append("bucket_id", (int) existingBuckets + 1)
                .append("bucket_start_time", currentEnd)
                .append("bucket_end_time", currentEnd + BUCKET_WINDOW_SECONDS)
                .append("event_count", overflow.size())
                .append("events", overflow);

        mongoTemplate.insert(overflowBucket, "timeline_buckets");
    }

    /**
     * Computes the start of the fixed time window containing the given time.
     * Windows are [0, 60), [60, 120), [120, 180), ...
     */
    private double computeBucketStart(double time) {
        return Math.floor(time / BUCKET_WINDOW_SECONDS) * BUCKET_WINDOW_SECONDS;
    }
}

