package com.bbdd.tp.model;

import java.util.List;

public record TimelineQueryResult(
        String componentId,
        double startTime,
        double endTime,
        List<EventDetail> events
) {
    public record EventDetail(
            String eventId,
            double startTime,
            double endTime,
            String description,
            List<RegionDetail> regions
    ) {
    }

    public record RegionDetail(
            int xmin,
            int xmax,
            int ymin,
            int ymax
    ) {
    }
}