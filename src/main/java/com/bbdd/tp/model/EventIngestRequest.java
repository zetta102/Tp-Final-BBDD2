package com.bbdd.tp.model;

import java.util.List;

public record EventIngestRequest(
        double startTime,
        double endTime,
        String description,
        List<RegionInput> regions
) {
    public record RegionInput(
            int xmin,
            int xmax,
            int ymin,
            int ymax
    ) {
    }
}

