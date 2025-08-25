package com.mcp.RayenMalouche.performance.testing.client.PerformanceTestingClient.controller;

import com.mcp.RayenMalouche.performance.testing.client.PerformanceTestingClient.service.PerformanceTestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/discovery-ai/performance")
public class PerformanceTestController {

    @Autowired
    private PerformanceTestService performanceTestService;

    @PostMapping("/test/single")
    public CompletableFuture<ResponseEntity<PerformanceTestService.TestMetrics>> runSingleTest(
            @RequestBody Map<String, String> request) {

        String query = request.get("query");
        String testId = request.getOrDefault("testId", "test_" + System.currentTimeMillis());

        return performanceTestService.runSinglePerformanceTest(query, testId)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/test/load")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> runLoadTest(
            @RequestBody Map<String, Integer> request) {

        int concurrentUsers = request.getOrDefault("concurrentUsers", 5);
        int requestsPerUser = request.getOrDefault("requestsPerUser", 2);

        return performanceTestService.runLoadTest(concurrentUsers, requestsPerUser)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/results/summary")
    public ResponseEntity<Map<String, Object>> getTestSummary() {
        return ResponseEntity.ok(performanceTestService.getTestSummary());
    }

    @GetMapping("/results/all")
    public ResponseEntity<Object> getAllResults() {
        Map<String, Object> response = new HashMap<>();
        response.put("summary", performanceTestService.getTestSummary());
        response.put("details", performanceTestService.getAllTestResults());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/results/clear")
    public ResponseEntity<String> clearResults() {
        performanceTestService.clearTestResults();
        return ResponseEntity.ok("Test results cleared successfully");
    }

    @PostMapping("/test/scenario/{scenario}")
    public CompletableFuture<ResponseEntity<PerformanceTestService.TestMetrics>> runScenarioTest(
            @PathVariable String scenario) {

        Map<String, String> scenarios = Map.of(
                "sage", "Créez un dataset complet sur toutes les solutions Sage de Discovery Intech",
                "qad", "Générez un dataset détaillé sur les solutions QAD proposées par Discovery Intech",
                "microsoft", "Produisez un dataset sur les solutions Microsoft Dynamics 365 de Discovery Intech",
                "sap", "Créez un dataset sur les solutions SAP proposées par Discovery Intech",
                "sectors", "Générez un dataset sur tous les secteurs d'activité couverts par Discovery Intech",
                "services", "Créez un dataset sur tous les services proposés par Discovery Intech",
                "company", "Produisez un dataset sur l'entreprise Discovery Intech (équipe, partenaires, références)"
        );

        String query = scenarios.getOrDefault(scenario,
                "Créez un dataset général sur Discovery Intech");
        String testId = "scenario_" + scenario + "_" + System.currentTimeMillis();

        return performanceTestService.runSinglePerformanceTest(query, testId)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/test/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "Performance Test Client");
        health.put("timestamp", java.time.Instant.now().toString());
        return ResponseEntity.ok(health);
    }
}