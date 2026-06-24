package com.azure.redisperf.controller;

import com.azure.redisperf.model.TestConfig;
import com.azure.redisperf.model.TestResult;
import com.azure.redisperf.service.PerfTestService;
import com.azure.redisperf.service.RedisConnections;
import com.azure.redisperf.service.ReportService;
import com.azure.redisperf.service.ServerInfoReader;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API backing the HTML UI: start a run, poll status, test the connection,
 * and list/download generated reports.
 */
@RestController
@RequestMapping("/api")
public class TestController {

    private final PerfTestService perfTestService;
    private final ReportService reportService;
    private final ServerInfoReader infoReader = new ServerInfoReader();

    public TestController(PerfTestService perfTestService, ReportService reportService) {
        this.perfTestService = perfTestService;
        this.reportService = reportService;
    }

    /** Quick connectivity + server INFO check before launching a full run. */
    @PostMapping("/test-connection")
    public ResponseEntity<?> testConnection(@RequestBody TestConfig cfg) {
        Map<String, Object> resp = new HashMap<>();
        if (cfg.getPrimaryHost() == null || cfg.getPrimaryHost().isBlank()) {
            resp.put("ok", false);
            resp.put("error", "primaryHost is required");
            return ResponseEntity.badRequest().body(resp);
        }
        try (RedisConnections.Endpoint ep = RedisConnections.primary(cfg);
             StatefulRedisConnection<String, String> conn = ep.newConnection()) {
            var info = infoReader.read(conn.sync());
            resp.put("ok", true);
            resp.put("endpoint", ep.description());
            resp.put("redisVersion", info.getRedisVersion());
            resp.put("role", info.getRole());
            resp.put("mode", info.getMode());
            resp.put("uptimeSeconds", info.getUptimeSeconds());
            resp.put("connectedClients", info.getConnectedClients());
            resp.put("usedMemory", info.getUsedMemoryHuman());
        } catch (Exception e) {
            resp.put("ok", false);
            resp.put("error", e.getMessage());
            return ResponseEntity.ok(resp);
        }

        // Optionally validate the replica endpoint too.
        if (cfg.hasReplicaEndpoint()) {
            try (RedisConnections.Endpoint ep = RedisConnections.replica(cfg);
                 StatefulRedisConnection<String, String> conn = ep.newConnection()) {
                conn.sync().ping();
                resp.put("replicaOk", true);
                resp.put("replicaEndpoint", ep.description());
            } catch (Exception e) {
                resp.put("replicaOk", false);
                resp.put("replicaError", e.getMessage());
            }
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody TestConfig cfg) {
        try {
            String runId = perfTestService.start(cfg);
            return ResponseEntity.ok(Map.of("runId", runId, "status", "RUNNING"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<TestResult> status() {
        return ResponseEntity.ok(perfTestService.getCurrent());
    }

    @GetMapping("/status/{runId}")
    public ResponseEntity<TestResult> status(@PathVariable String runId) {
        TestResult r = perfTestService.getById(runId);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(r);
    }

    @GetMapping("/reports")
    public List<ReportService.ReportFile> reports() throws Exception {
        return reportService.listReports();
    }

    @GetMapping("/reports/{fileName}")
    public ResponseEntity<Resource> downloadReport(@PathVariable String fileName) throws Exception {
        Path path = reportService.resolveReport(fileName);
        if (!Files.exists(path)) return ResponseEntity.notFound().build();
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + path.getFileName() + "\"")
                .body(resource);
    }
}
