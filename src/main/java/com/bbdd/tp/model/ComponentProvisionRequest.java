package com.bbdd.tp.model;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record ComponentProvisionRequest(
        UUID componentId,
        UUID trackId,
        UUID assetId,
        int eventRateNumerator,
        int eventRateDenominator,
        @Nullable Integer xSize,
        @Nullable Integer ySize,
        String algorithmName
) {
}