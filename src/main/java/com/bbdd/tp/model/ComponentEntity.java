package com.bbdd.tp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * JPA entity representing a component in the PostgreSQL catalog.
 *
 * <p>Maps to the {@code components} table, which stores structural metadata
 * about media processing components (e.g., video analysis outputs). Each component
 * belongs to a track and has an associated event rate and optional spatial dimensions.</p>
 *
 * <p>This entity participates in the "catalog" side of the polyglot persistence model,
 * while the corresponding temporal event data is stored in MongoDB's
 * {@code timeline_buckets} collection.</p>
 */
@Entity
@Table(name = "components")
public class ComponentEntity {

    /** Unique identifier for this component (primary key). */
    @Id
    @Column(name = "component_id")
    private UUID componentId;

    /** Track this component belongs to (foreign key to {@code tracks} table). */
    @Column(name = "track_id", nullable = false)
    private UUID trackId;

    /** Numerator of the event rate rational number (e.g., 24000 for 23.976fps). */
    @Column(name = "event_rate_numerator", nullable = false)
    private int eventRateNumerator;

    /** Denominator of the event rate rational number (e.g., 1001 for 23.976fps). */
    @Column(name = "event_rate_denominator", nullable = false)
    private int eventRateDenominator;

    /** Horizontal resolution in pixels, or {@code null} for non-spatial components. */
    @Column(name = "x_size")
    private Integer xSize;

    /** Vertical resolution in pixels, or {@code null} for non-spatial components. */
    @Column(name = "y_size")
    private Integer ySize;

    /** JSONB metadata blob (e.g., algorithm configuration). */
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    public UUID getComponentId() {
        return componentId;
    }

    public void setComponentId(UUID componentId) {
        this.componentId = componentId;
    }

    public UUID getTrackId() {
        return trackId;
    }

    public void setTrackId(UUID trackId) {
        this.trackId = trackId;
    }

    public int getEventRateNumerator() {
        return eventRateNumerator;
    }

    public void setEventRateNumerator(int eventRateNumerator) {
        this.eventRateNumerator = eventRateNumerator;
    }

    public int getEventRateDenominator() {
        return eventRateDenominator;
    }

    public void setEventRateDenominator(int eventRateDenominator) {
        this.eventRateDenominator = eventRateDenominator;
    }

    public Integer getXSize() {
        return xSize;
    }

    public void setXSize(Integer xSize) {
        this.xSize = xSize;
    }

    public Integer getYSize() {
        return ySize;
    }

    public void setYSize(Integer ySize) {
        this.ySize = ySize;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}