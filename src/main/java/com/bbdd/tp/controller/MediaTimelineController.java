package com.bbdd.tp.controller;

import com.bbdd.tp.model.ComponentProvisionRequest;
import com.bbdd.tp.model.TimelineQueryResult;
import com.bbdd.tp.service.ComponentProvisioningService;
import com.bbdd.tp.service.CoordinatedTransactionCoordinator;
import com.bbdd.tp.service.TimelineQueryService;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/{version}")
@NullMarked
public class MediaTimelineController {

    private final ComponentProvisioningService provisioningService;
    private final TimelineQueryService queryService;
    private final CoordinatedTransactionCoordinator manualCoordinator;

    public MediaTimelineController(
            ComponentProvisioningService provisioningService,
            TimelineQueryService queryService,
            CoordinatedTransactionCoordinator manualCoordinator) {
        this.provisioningService = provisioningService;
        this.queryService = queryService;
        this.manualCoordinator = manualCoordinator;
    }

    @PostMapping(value = "/components", version = "1.0")
    public ResponseEntity<String> provisionComponentSaga(@RequestBody ComponentProvisionRequest request) {
        provisioningService.provisionWithSaga(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("SAGA complete: Catalog and bucket definitions synced.");
    }

    @PostMapping(value = "/components", version = "2.0")
    public ResponseEntity<String> provisionComponentCoordinated(@RequestBody ComponentProvisionRequest request) {
        provisioningService.provisionCoordinated(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("ACID Transaction complete: Atomic dual-engine insert succeeded.");
    }

    @PostMapping(value = "/components", version = "3.0")
    public ResponseEntity<String> provisionComponentManual(@RequestBody ComponentProvisionRequest request) throws Exception {
        manualCoordinator.provisionComponentManual(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Manual JDBC/Mongo Transaction complete: Atomic dual-engine insert succeeded.");
    }

    @GetMapping(value = "/tracks/{trackId}/query", version = "1.0")
    public ResponseEntity<List<TimelineQueryResult>> queryTimeline(
            @PathVariable UUID trackId,
            @RequestParam double startTime,
            @RequestParam double endTime,
            @RequestParam int xmin,
            @RequestParam int xmax,
            @RequestParam int ymin,
            @RequestParam int ymax) {

        List<TimelineQueryResult> results = queryService.querySpatioTemporal(
                trackId, startTime, endTime, xmin, xmax, ymin, ymax
        );
        return ResponseEntity.ok(results);
    }
}