package com.bbdd.tp.service;

import com.bbdd.tp.model.EventIngestRequest;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for appending events into MongoDB timeline buckets.
 *
 * <p>Implements the Netflix Media Timeline <em>fixed-time-window bucket pattern</em>:
 * events are routed to 60-second time windows, and buckets are split when they
 * exceed {@value #MAX_EVENTS_PER_BUCKET} events.</p>
 *
 * <p>Each region within an ingested event is enriched with a GeoJSON {@code centroid}
 * point to support {@code 2dsphere} spatial queries.</p>
 */
@Service
public class EventIngestionService {

    /** Duration of each fixed time window bucket in seconds. */
    private static final double BUCKET_WINDOW_SECONDS = 60.0;

    /** Maximum number of events per bucket before triggering a split. */
    private static final int MAX_EVENTS_PER_BUCKET = 200;

    private final MongoTemplate mongoTemplate;
    private final TransactionTemplate mongoTransactionTemplate;

    /**
     * @param mongoTemplate            the session-aware MongoDB template
     * @param mongoTransactionTemplate transaction template backed by {@code MongoTransactionManager}
     */
    public EventIngestionService(MongoTemplate mongoTemplate,
                                 @Qualifier("mongoTransactionTemplate") TransactionTemplate mongoTransactionTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.mongoTransactionTemplate = mongoTransactionTemplate;
    }

    /**
     * Appends an event to the appropriate timeline bucket for the given component.
     *
     * <p>The entire operation (bucket lookup/creation, event push, and potential bucket split)
     * is executed within a MongoDB transaction to ensure atomicity.</p>
     *
     * <p>If no bucket exists for the event's time window, one is created automatically.
     * After insertion, if the bucket exceeds the maximum event count, it is split into
     * two buckets.</p>
     *
     * @param componentId the component to append the event to
     * @param request     the event data including time range, description, and regions
     */
    public void appendEvent(UUID componentId, EventIngestRequest request) {
        mongoTransactionTemplate.execute(status -> {
            appendEventInternal(componentId, request);
            return null;
        });
    }

    /**
     * Internal implementation of event appending, executed within a MongoDB transaction.
     */
    private void appendEventInternal(UUID componentId, EventIngestRequest request) {
        String componentIdStr = componentId.toString();

        Document bucket = findBucketForTime(componentIdStr, request.startTime());

        if (bucket == null) {
            bucket = createBucketForTime(componentIdStr, request.startTime());
        }

        Document eventDoc = buildEventDocument(request);

        Query query = new Query(Criteria.where("_id").is(bucket.getObjectId("_id")));
        Update update = new Update()
                .push("events", eventDoc)
                .inc("event_count", 1);

        mongoTemplate.updateFirst(query, update, "timeline_buckets");

        Document updated = mongoTemplate.findOne(query, Document.class, "timeline_buckets");
        if (updated != null && updated.getInteger("event_count", 0) > MAX_EVENTS_PER_BUCKET) {
            splitBucket(updated);
        }
    }

    /**
     * Finds the bucket matching the fixed time window that contains the given event time.
     *
     * @param componentId    the component identifier
     * @param eventStartTime the event start time in seconds
     * @return the matching bucket document, or {@code null} if none exists
     */
    private Document findBucketForTime(String componentId, double eventStartTime) {
        double bucketStart = computeBucketStart(eventStartTime);
        double bucketEnd = bucketStart + BUCKET_WINDOW_SECONDS;

        Query query = new Query(Criteria.where("component_id").is(componentId)
                .and("bucket_start_time").is(bucketStart)
                .and("bucket_end_time").is(bucketEnd));

        return mongoTemplate.findOne(query, Document.class, "timeline_buckets");
    }

    /**
     * Creates a new empty bucket for the time window containing the given event time.
     *
     * @param componentId    the component identifier
     * @param eventStartTime the event start time used to determine the window
     * @return the newly inserted bucket document
     */
    private Document createBucketForTime(String componentId, double eventStartTime) {
        double bucketStart = computeBucketStart(eventStartTime);
        double bucketEnd = bucketStart + BUCKET_WINDOW_SECONDS;

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

    /**
     * Builds a BSON event document from the request, enriching each region with a
     * GeoJSON {@code Point} centroid computed from the bounding box center.
     *
     * @param request the event ingest request
     * @return a BSON document ready for insertion into the bucket's {@code events} array
     */
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
     * Splits a full bucket by keeping the first {@value #MAX_EVENTS_PER_BUCKET} events
     * and moving overflow events into a new adjacent bucket.
     *
     * @param fullBucket the bucket document that has exceeded the event limit
     */
    private void splitBucket(Document fullBucket) {
        List<Document> events = fullBucket.getList("events", Document.class);
        if (events == null || events.size() <= MAX_EVENTS_PER_BUCKET) {
            return;
        }

        List<Document> keep = new ArrayList<>(events.subList(0, MAX_EVENTS_PER_BUCKET));
        List<Document> overflow = new ArrayList<>(events.subList(MAX_EVENTS_PER_BUCKET, events.size()));

        Query query = new Query(Criteria.where("_id").is(fullBucket.getObjectId("_id")));
        Update update = new Update()
                .set("events", keep)
                .set("event_count", MAX_EVENTS_PER_BUCKET);
        mongoTemplate.updateFirst(query, update, "timeline_buckets");

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
     * Windows are {@code [0, 60)}, {@code [60, 120)}, {@code [120, 180)}, etc.
     *
     * @param time the time in seconds
     * @return the bucket start time
     */
    private double computeBucketStart(double time) {
        return Math.floor(time / BUCKET_WINDOW_SECONDS) * BUCKET_WINDOW_SECONDS;
    }
}
