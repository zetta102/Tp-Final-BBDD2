package com.bbdd.tp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "components")
public class ComponentEntity {

    @Id
    @Column(name = "component_id")
    private UUID componentId;

    @Column(name = "track_id", nullable = false)
    private UUID trackId;

    @Column(name = "event_rate_numerator", nullable = false)
    private int eventRateNumerator;

    @Column(name = "event_rate_denominator", nullable = false)
    private int eventRateDenominator;

    @Column(name = "x_size")
    private Integer xSize;

    @Column(name = "y_size")
    private Integer ySize;

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