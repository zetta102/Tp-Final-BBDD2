package com.bbdd.tp.service;

import com.bbdd.tp.model.ComponentEntity;
import com.bbdd.tp.model.TimelineQueryResult;
import com.bbdd.tp.repository.ComponentRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TimelineQueryService {

    private final ComponentRepository componentRepository;
    private final MongoTemplate mongoTemplate;

    public TimelineQueryService(ComponentRepository componentRepository, MongoTemplate mongoTemplate) {
        this.componentRepository = componentRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<TimelineQueryResult> querySpatioTemporal(
            UUID trackId, double startTime, double endTime, int xmin, int xmax, int ymin, int ymax) {

        List<ComponentEntity> components = componentRepository.findByTrackId(trackId);
        List<TimelineQueryResult> results = new ArrayList<>();

        org.springframework.data.geo.Box queryBox = new org.springframework.data.geo.Box(
                new org.springframework.data.geo.Point(xmin, ymin),
                new org.springframework.data.geo.Point(xmax, ymax)
        );

        for (ComponentEntity comp : components) {
            String componentIdStr = comp.getComponentId().toString();

            Query query = new Query();
            query.addCriteria(Criteria.where("component_id").is(componentIdStr)
                    .and("bucket_start_time").lt(endTime)
                    .and("bucket_end_time").gt(startTime)
                    .and("events.regions.centroid").within(queryBox)
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

                                    // Precision filtering on bounding box overlaps
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