package com.bbdd.tp.model;

import java.util.List;

/**
 * Projection record returned by spatio-temporal timeline queries.
 *
 * <p>Aggregates events from MongoDB timeline buckets that match both
 * a temporal range and a spatial bounding box for a given component.</p>
 *
 * @param componentId the component whose timeline was queried
 * @param startTime   the query's temporal range start (seconds)
 * @param endTime     the query's temporal range end (seconds)
 * @param events      list of matching events with their spatial regions
 */
public record TimelineQueryResult(
        String componentId,
        double startTime,
        double endTime,
        List<EventDetail> events
) {
    /**
     * Detail of a single event within the timeline.
     *
     * @param eventId     unique identifier for the event
     * @param startTime   event start time in seconds
     * @param endTime     event end time in seconds
     * @param description human-readable event description
     * @param regions     spatial regions associated with this event
     */
    public record EventDetail(
            String eventId,
            double startTime,
            double endTime,
            String description,
            List<RegionDetail> regions
    ) {
    }

    /**
     * Axis-aligned bounding box representing a spatial region within a video frame.
     *
     * @param xmin left edge in pixels
     * @param xmax right edge in pixels
     * @param ymin top edge in pixels
     * @param ymax bottom edge in pixels
     */
    public record RegionDetail(
            int xmin,
            int xmax,
            int ymin,
            int ymax
    ) {
    }
}

