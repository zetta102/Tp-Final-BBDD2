package com.bbdd.tp.controller;

import com.bbdd.tp.model.ComponentProvisionRequest;
import com.bbdd.tp.service.ComponentProvisioningService;
import com.bbdd.tp.service.CoordinatedTransactionCoordinator;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/{version}")
@NullMarked
public class MediaTimelineController {

    private final ComponentProvisioningService provisioningService;
    private final CoordinatedTransactionCoordinator manualCoordinator;

    public MediaTimelineController(
            ComponentProvisioningService provisioningService,
            CoordinatedTransactionCoordinator manualCoordinator) {
        this.provisioningService = provisioningService;
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
}