package com.bbdd.tp.controller;

import com.bbdd.tp.model.ComponentProvisionRequest;
import com.bbdd.tp.model.EventIngestRequest;
import com.bbdd.tp.model.TimelineQueryResult;
import com.bbdd.tp.service.ComponentProvisioningService;
import com.bbdd.tp.service.CoordinatedTransactionCoordinator;
import com.bbdd.tp.service.EventIngestionService;
import com.bbdd.tp.service.TimelineQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller exposing three component provisioning endpoints, each demonstrating
 * a different strategy for synchronized dual-engine writes (PostgreSQL + MongoDB)
 * with coordinated rollback on failure.
 *
 * <p>All endpoints accept the same {@link ComponentProvisionRequest} payload and perform
 * identical writes to both databases, differing only in how transactional consistency
 * and rollback are achieved.</p>
 *
 * <p>API versioning is handled via path segments: {@code /api/1.0/...}, {@code /api/2.0/...},
 * {@code /api/3.0/...}.</p>
 */
@RestController
@RequestMapping("/api/{version}")
@NullMarked
@Tag(name = "Media Timeline", description = "Dual-engine (SQL + NoSQL) component provisioning, event ingestion, and spatio-temporal timeline queries")
public class MediaTimelineController {

    private final ComponentProvisioningService provisioningService;
    private final CoordinatedTransactionCoordinator manualCoordinator;
    private final EventIngestionService eventIngestionService;
    private final TimelineQueryService timelineQueryService;

    /**
     * Constructs the controller with required service dependencies.
     *
     * @param provisioningService  handles Saga (v1.0) and Spring-coordinated (v2.0) provisioning
     * @param manualCoordinator    handles manual JDBC/Mongo session provisioning (v3.0)
     * @param eventIngestionService handles event ingestion into MongoDB timeline buckets
     * @param timelineQueryService  handles spatio-temporal queries across both engines
     */
    public MediaTimelineController(
            ComponentProvisioningService provisioningService,
            CoordinatedTransactionCoordinator manualCoordinator,
            EventIngestionService eventIngestionService,
            TimelineQueryService timelineQueryService) {
        this.provisioningService = provisioningService;
        this.manualCoordinator = manualCoordinator;
        this.eventIngestionService = eventIngestionService;
        this.timelineQueryService = timelineQueryService;
    }

    /**
     * Provisions a component using the <strong>Saga compensation</strong> strategy.
     *
     * <p>Commits the PostgreSQL insert first, then attempts the MongoDB bucket write.
     * If MongoDB fails, the PostgreSQL row is deleted as a compensating action.</p>
     *
     * @param request the component provisioning payload
     * @return 201 Created on success
     */
    @Operation(
            summary = "Provision component via Saga compensation (v1.0)",
            description = "Writes the catalog entry to PostgreSQL first, then the timeline bucket to MongoDB. "
                    + "If the MongoDB write fails, a compensating transaction deletes the PostgreSQL row.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Saga dual-write completed successfully",
                            content = @Content(schema = @Schema(implementation = String.class))),
                    @ApiResponse(responseCode = "500", description = "MongoDB write failed; PostgreSQL row rolled back via compensation")
            }
    )
    @PostMapping(value = "/components", version = "1.0")
    public ResponseEntity<String> provisionComponentSaga(@RequestBody ComponentProvisionRequest request) {
        provisioningService.provisionWithSaga(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("SAGA complete: Catalog and bucket definitions synced.");
    }

    /**
     * Provisions a component using <strong>Spring-coordinated nested transactions</strong>.
     *
     * <p>Wraps both the JPA insert and the MongoDB insert inside nested
     * {@link org.springframework.transaction.support.TransactionTemplate} executions.
     * If the inner MongoDB transaction fails, the exception propagates and the outer
     * JPA transaction rolls back automatically.</p>
     *
     * @param request the component provisioning payload
     * @return 201 Created on success
     */
    @Operation(
            summary = "Provision component via Spring-coordinated transactions (v2.0)",
            description = "Nests a MongoDB TransactionTemplate inside a JPA TransactionTemplate. "
                    + "If either write fails, both transactions roll back through Spring's exception propagation.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Coordinated dual-write completed successfully",
                            content = @Content(schema = @Schema(implementation = String.class))),
                    @ApiResponse(responseCode = "500", description = "Transaction failed; both engines rolled back")
            }
    )
    @PostMapping(value = "/components", version = "2.0")
    public ResponseEntity<String> provisionComponentCoordinated(@RequestBody ComponentProvisionRequest request) {
        provisioningService.provisionCoordinated(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("ACID Transaction complete: Atomic dual-engine insert succeeded.");
    }

    /**
     * Provisions a component using <strong>manual JDBC + MongoDB driver transactions</strong>.
     *
     * <p>Opens a raw JDBC {@link java.sql.Connection} with auto-commit disabled and a native
     * MongoDB {@link com.mongodb.client.ClientSession} with snapshot read concern. On failure,
     * both are explicitly rolled back ({@code pgConn.rollback()} and
     * {@code mongoSession.abortTransaction()}).</p>
     *
     * @param request the component provisioning payload
     * @return 201 Created on success
     * @throws Exception if the coordinated transaction fails after rollback attempts
     */
    @Operation(
            summary = "Provision component via manual JDBC/Mongo coordination (v3.0)",
            description = "Manages raw JDBC and MongoDB ClientSession transactions manually. "
                    + "On failure, explicitly calls pgConn.rollback() and mongoSession.abortTransaction().",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Manual dual-write completed successfully",
                            content = @Content(schema = @Schema(implementation = String.class))),
                    @ApiResponse(responseCode = "500", description = "Transaction failed; both engines explicitly rolled back")
            }
    )
    @PostMapping(value = "/components", version = "3.0")
    public ResponseEntity<String> provisionComponentManual(@RequestBody ComponentProvisionRequest request) throws Exception {
        manualCoordinator.provisionComponentManual(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Manual JDBC/Mongo Transaction complete: Atomic dual-engine insert succeeded.");
    }

    // ── Event Ingestion (MongoDB only) ─────────────────────────────────────

    /**
     * Ingests an event into the timeline bucket for a given component.
     *
     * <p>The event is appended to the MongoDB bucket that covers the event's time window.
     * If no bucket exists yet, one is created automatically. Buckets are split when they
     * exceed the maximum event count (Netflix fixed-window bucket pattern).</p>
     *
     * @param componentId the component whose timeline receives the event
     * @param request     the event data with temporal bounds, description, and spatial regions
     * @return 201 Created on success
     */
    @Operation(
            summary = "Ingest event into a component's timeline bucket",
            description = "Appends an event to the MongoDB timeline bucket matching the event's time window. "
                    + "Creates the bucket on demand and splits it if the event count exceeds the threshold.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Event ingested successfully"),
                    @ApiResponse(responseCode = "500", description = "Event ingestion failed")
            }
    )
    @PostMapping("/components/{componentId}/events")
    public ResponseEntity<String> ingestEvent(
            @Parameter(description = "Component UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID componentId,
            @RequestBody EventIngestRequest request) {
        eventIngestionService.appendEvent(componentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Event ingested into timeline bucket for component " + componentId + ".");
    }

    // ── Spatio-Temporal Timeline Query (PostgreSQL + MongoDB) ──────────────

    /**
     * Queries the timeline for events matching both a temporal range and a spatial bounding box.
     *
     * <p>Performs a polyglot query: first looks up components from PostgreSQL by track,
     * then queries MongoDB for timeline bucket events whose time windows and region
     * centroids fall within the specified ranges.</p>
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
    @Operation(
            summary = "Spatio-temporal timeline query",
            description = "Cross-engine query: looks up components in PostgreSQL, then queries MongoDB for "
                    + "timeline events matching the temporal range and spatial bounding box.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Query results returned"),
                    @ApiResponse(responseCode = "500", description = "Query execution failed")
            }
    )
    @GetMapping("/tracks/{trackId}/timeline")
    public ResponseEntity<List<TimelineQueryResult>> queryTimeline(
            @Parameter(description = "Track UUID", example = "0c5f2b8a-3c4a-4d2b-aa5e-8d0768b8e0a2")
            @PathVariable UUID trackId,
            @Parameter(description = "Start time in seconds", example = "0.0")
            @RequestParam double startTime,
            @Parameter(description = "End time in seconds", example = "60.0")
            @RequestParam double endTime,
            @Parameter(description = "Bounding box left edge (pixels)", example = "0")
            @RequestParam int xmin,
            @Parameter(description = "Bounding box right edge (pixels)", example = "1920")
            @RequestParam int xmax,
            @Parameter(description = "Bounding box top edge (pixels)", example = "0")
            @RequestParam int ymin,
            @Parameter(description = "Bounding box bottom edge (pixels)", example = "1080")
            @RequestParam int ymax) {
        List<TimelineQueryResult> results = timelineQueryService.querySpatioTemporal(
                trackId, startTime, endTime, xmin, xmax, ymin, ymax);
        return ResponseEntity.ok(results);
    }
}