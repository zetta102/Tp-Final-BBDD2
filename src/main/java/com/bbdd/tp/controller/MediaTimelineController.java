package com.bbdd.tp.controller;

import com.bbdd.tp.model.ComponentProvisionRequest;
import com.bbdd.tp.service.ComponentProvisioningService;
import com.bbdd.tp.service.CoordinatedTransactionCoordinator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
@Tag(name = "Component Provisioning", description = "Dual-engine (SQL + NoSQL) component provisioning with synchronized rollback strategies")
public class MediaTimelineController {

    private final ComponentProvisioningService provisioningService;
    private final CoordinatedTransactionCoordinator manualCoordinator;

    /**
     * Constructs the controller with required service dependencies.
     *
     * @param provisioningService handles Saga (v1.0) and Spring-coordinated (v2.0) provisioning
     * @param manualCoordinator   handles manual JDBC/Mongo session provisioning (v3.0)
     */
    public MediaTimelineController(
            ComponentProvisioningService provisioningService,
            CoordinatedTransactionCoordinator manualCoordinator) {
        this.provisioningService = provisioningService;
        this.manualCoordinator = manualCoordinator;
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
}