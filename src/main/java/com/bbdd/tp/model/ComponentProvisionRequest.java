package com.bbdd.tp.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Request payload for provisioning a new component across both database engines.
 *
 * <p>Contains the identifiers and metadata needed to create a catalog entry in PostgreSQL
 * and an initial timeline bucket document in MongoDB.</p>
 *
 * @param componentId          unique identifier for the new component
 * @param trackId              the track this component belongs to
 * @param assetId              the parent media asset identifier
 * @param eventRateNumerator   numerator of the event rate (e.g., 24000 for 23.976fps)
 * @param eventRateDenominator denominator of the event rate (e.g., 1001 for 23.976fps)
 * @param xSize                optional horizontal resolution in pixels
 * @param ySize                optional vertical resolution in pixels
 * @param algorithmName        name of the processing algorithm (e.g., "video_face_detection")
 */
@Schema(description = "Request payload for dual-engine component provisioning (PostgreSQL catalog + MongoDB timeline bucket)")
public record ComponentProvisionRequest(
        @Schema(description = "Unique component identifier", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID componentId,
        @Schema(description = "Track this component belongs to", example = "0c5f2b8a-3c4a-4d2b-aa5e-8d0768b8e0a2")
        UUID trackId,
        @Schema(description = "Parent media asset identifier", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID assetId,
        @Schema(description = "Event rate numerator (e.g., 24000 for 23.976fps)", example = "24000")
        int eventRateNumerator,
        @Schema(description = "Event rate denominator (e.g., 1001 for 23.976fps)", example = "1001")
        int eventRateDenominator,
        @Schema(description = "Horizontal resolution in pixels (optional)", example = "1920", nullable = true)
        @Nullable Integer xSize,
        @Schema(description = "Vertical resolution in pixels (optional)", example = "1080", nullable = true)
        @Nullable Integer ySize,
        @Schema(description = "Processing algorithm name", example = "video_face_detection")
        String algorithmName
) {
}