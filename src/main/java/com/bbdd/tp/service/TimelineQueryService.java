package com.bbdd.tp.service;

import com.bbdd.tp.model.ComponentEntity;
import com.bbdd.tp.model.TimelineQueryResult;
import com.bbdd.tp.repository.ComponentRepository;
import org.bson.Document;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for executing spatio-temporal queries across the polyglot data store.
 *
 * <p>Combines a PostgreSQL lookup (to find components belonging to a track) with a
 * MongoDB query (to find timeline bucket events matching a temporal range and a
 * GeoJSON spatial bounding box on region centroids).</p>
 */
@Service
public class TimelineQueryService {

    private final ComponentRepository componentRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * @param componentRepository JPA repository for component catalog lookups
     * @param mongoTemplate       session-aware MongoDB template for bucket queries
     */
    public TimelineQueryService(ComponentRepository componentRepository, MongoTemplate mongoTemplate) {
        this.componentRepository = componentRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Queries the timeline for events matching both a temporal range and a spatial bounding box.
     *
     * <p>For each component in the given track, finds MongoDB buckets whose time windows
     * overlap {@code [startTime, endTime)}, then filters individual events whose regions
     * intersect the spatial bounding box defined by {@code (xmin, ymin)} to {@code (xmax, ymax)}.</p>
     *
     * @param trackId   the track to query components for
     * @param startTime temporal range start in seconds
     * @param endTime   temporal range end in seconds
     * @param xmin      bounding box left edge in pixels
     * @param xmax      bounding box right edge in pixels
     * @param ymin      bounding box top edge in pixels
     * @param ymax      bounding box bottom edge in pixels
     * @return list of results per component, each containing matching events and regions
     */
    public List<TimelineQueryResult> querySpatioTemporal(
            UUID trackId, double startTime, double endTime, int xmin, int xmax, int ymin, int ymax) {

        List<ComponentEntity> components = componentRepository.findByTrackId(trackId);
        List<TimelineQueryResult> results = new ArrayList<>();

        // Build a GeoJSON polygon representing the bounding box for $geoWithin query
        GeoJsonPolygon boundingBox = new GeoJsonPolygon(
                new Point(xmin, ymin),
                new Point(xmax, ymin),
                new Point(xmax, ymax),
                new Point(xmin, ymax),
                new Point(xmin, ymin) // close the ring
        );

        for (ComponentEntity comp : components) {
            String componentIdStr = comp.getComponentId().toString();

            Query query = new Query();
            query.addCriteria(Criteria.where("component_id").is(componentIdStr)
                    .and("bucket_start_time").lt(endTime)
                    .and("bucket_end_time").gt(startTime)
                    .and("events.regions.centroid").within(boundingBox)
            );

            List<Document> buckets = mongoTemplate.find(query, Document.class, "timeline_buckets");
            List<TimelineQueryResult.EventDetail> eventsList = new ArrayList<>();

            for (Document doc : buckets) {
                List<Document> events = doc.getList("events", Document.class);
                if (events != null) {
                    for (Document event : events) {
                        double evtStart = event.getDouble("startTime");
                        double evtEnd = event.getDouble("endTime");

                        if (evtStart < endTime && evtEnd > startTime) {
                            List<Document> regions = event.getList("regions", Document.class);
                            List<TimelineQueryResult.RegionDetail> matchingRegions = new ArrayList<>();

                            if (regions != null) {
                                for (Document region : regions) {
                                    int rxMin = region.getInteger("xmin");
                                    int rxMax = region.getInteger("xmax");
                                    int ryMin = region.getInteger("ymin");
                                    int ryMax = region.getInteger("ymax");

                                    if (rxMax >= xmin && rxMin <= xmax && ryMax >= ymin && ryMin <= ymax) {
                                        matchingRegions.add(new TimelineQueryResult.RegionDetail(rxMin, rxMax, ryMin, ryMax));
                                    }
                                }
                            }

                            if (!matchingRegions.isEmpty()) {
                                Document meta = (Document) event.get("metadata");
                                String desc = meta != null ? meta.getString("description") : "Detected Object";
                                eventsList.add(new TimelineQueryResult.EventDetail(
                                        event.getString("event_id"),
                                        evtStart,
                                        evtEnd,
                                        desc,
                                        matchingRegions
                                ));
                            }
                        }
                    }
                }
            }
            results.add(new TimelineQueryResult(componentIdStr, startTime, endTime, eventsList));
        }
        return results;
    }
}
