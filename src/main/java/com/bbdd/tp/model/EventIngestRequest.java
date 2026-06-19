package com.bbdd.tp.model;

import java.util.List;

/**
 * Request payload for ingesting a new event into a component's timeline bucket.
 *
 * <p>Contains temporal bounds, a description, and a list of spatial regions
 * where the event was detected within the video frame.</p>
 *
 * @param startTime   event start time in seconds relative to the media timeline
 * @param endTime     event end time in seconds relative to the media timeline
 * @param description human-readable description of the event (e.g., "Face detected")
 * @param regions     list of spatial bounding boxes where the event was detected
 */
public record EventIngestRequest(
        double startTime,
        double endTime,
        String description,
        List<RegionInput> regions
) {
    /**
     * Spatial bounding box within a video frame, in pixel coordinates.
     *
     * @param xmin left edge in pixels
     * @param xmax right edge in pixels
     * @param ymin top edge in pixels
     * @param ymax bottom edge in pixels
     */
    public record RegionInput(
            int xmin,
            int xmax,
            int ymin,
            int ymax
    ) {
    }
}

