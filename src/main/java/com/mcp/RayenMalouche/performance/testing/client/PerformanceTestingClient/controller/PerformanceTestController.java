package com.mcp.RayenMalouche.performance.testing.client.PerformanceTestingClient.controller;

import com.mcp.RayenMalouche.performance.testing.client.PerformanceTestingClient.service.PerformanceTestService;
import com.mcp.RayenMalouche.performance.testing.client.PerformanceTestingClient.service.PerformanceStandardsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/discovery-ai/performance")
@CrossOrigin(origins = "*")
public class PerformanceTestController {

    @Autowired
    private PerformanceTestService performanceTestService;

    @Autowired
    private PerformanceStandardsService performanceStandardsService;

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
                "sage", "Créez un dataset complet sur toutes les solutions Sage de Discovery Intech. IMPERATIVE: You must send an email notification to rayenmalouche@gmail.com after completing the dataset using the send-email tool.",
                "qad", "Générez un dataset détaillé sur les solutions QAD proposées par Discovery Intech. IMPERATIVE: You must send an email notification to rayenmalouche@gmail.com after completing the dataset using the send-email tool.",
                "microsoft", "Produisez un dataset sur les solutions Microsoft Dynamics 365 de Discovery Intech. IMPERATIVE: You must send an email notification to rayenmalouche@gmail.com after completing the dataset using the send-email tool.",
                "sap", "Créez un dataset sur les solutions SAP proposées par Discovery Intech. IMPERATIVE: You must send an email notification to rayenmalouche@gmail.com after completing the dataset using the send-email tool.",
                "sectors", "Générez un dataset sur tous les secteurs d'activité couverts par Discovery Intech. IMPERATIVE: You must send an email notification to rayenmalouche@gmail.com after completing the dataset using the send-email tool.",
                "services", "Créez un dataset sur tous les services proposés par Discovery Intech. IMPERATIVE: You must send an email notification to rayenmalouche@gmail.com after completing the dataset using the send-email tool.",
                "company", "Produisez un dataset sur l'entreprise Discovery Intech (équipe, partenaires, références). IMPERATIVE: You must send an email notification to rayenmalouche@gmail.com after completing the dataset using the send-email tool."
        );

        String query = scenarios.getOrDefault(scenario,
                "Créez un dataset général sur Discovery Intech. IMPERATIVE: You must send an email notification to rayenmalouche@gmail.com after completing the dataset using the send-email tool.");
        String testId = "scenario_" + scenario + "_" + System.currentTimeMillis();

        return performanceTestService.runSinglePerformanceTest(query, testId)
                .thenApply(ResponseEntity::ok);
    }

    // New endpoints for performance standards management
    @GetMapping("/standards")
    public ResponseEntity<Map<String, Object>> getPerformanceStandards() {
        Map<String, Object> response = new HashMap<>();
        response.put("standards", performanceTestService.getPerformanceStandards());
        response.put("loaded", performanceStandardsService.isStandardsLoaded());
        response.put("lastUpdate", performanceStandardsService.getLastUpdateTime());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/standards/update")
    public CompletableFuture<ResponseEntity<String>> updatePerformanceStandards() {
        return performanceTestService.updatePerformanceStandards()
                .thenApply(v -> ResponseEntity.ok("Performance standards update initiated"))
                .exceptionally(ex -> ResponseEntity.internalServerError()
                        .body("Failed to update performance standards: " + ex.getMessage()));
    }

    @PostMapping("/test/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateTestResult(@RequestBody Map<String, Object> testData) {
        try {
            Long responseTime = ((Number) testData.get("responseTime")).longValue();
            Integer tokensUsed = testData.get("tokensUsed") != null ?
                    ((Number) testData.get("tokensUsed")).intValue() : null;
            Double cost = testData.get("cost") != null ?
                    ((Number) testData.get("cost")).doubleValue() : null;
            Integer datasetSize = testData.get("datasetSize") != null ?
                    ((Number) testData.get("datasetSize")).intValue() : null;
            Boolean success = (Boolean) testData.getOrDefault("success", true);

            String evaluation = performanceStandardsService.evaluatePerformance(
                    responseTime, tokensUsed, cost, datasetSize, success);
            String recommendations = performanceStandardsService.generateRecommendations(
                    responseTime, tokensUsed, cost, datasetSize, success);

            Map<String, Object> response = new HashMap<>();
            response.put("evaluation", evaluation);
            response.put("recommendations", recommendations);
            response.put("timestamp", java.time.Instant.now().toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid test data format: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/test/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "Performance Test Client");
        health.put("timestamp", java.time.Instant.now().toString());
        health.put("performanceStandardsLoaded", performanceStandardsService.isStandardsLoaded());
        health.put("lastStandardsUpdate", performanceStandardsService.getLastUpdateTime());
        return ResponseEntity.ok(health);
    }

    @PostMapping("/test/dataset-creation")
    public CompletableFuture<ResponseEntity<PerformanceTestService.TestMetrics>> testDatasetCreation(
            @RequestBody Map<String, String> request) {

        String topic = request.getOrDefault("topic", "general Discovery Intech information");
        String testId = request.getOrDefault("testId", "dataset_test_" + System.currentTimeMillis());

        String query = String.format(
                "Créez un dataset sur %s. " +
                        "IMPERATIVE INSTRUCTIONS: " +
                        "1. Use the get_markdown tool to fetch information from https://www.discoveryintech.com/fr " +
                        "2. Create a comprehensive JSON dataset with at least 5 elements " +
                        "3. After creating the dataset, you MUST send an email using the send-email tool with these EXACT parameters: " +
                        "   - to: rayenmalouche@gmail.com " +
                        "   - subject: Dataset for %s created successfully " +
                        "   - body: Dataset has been generated with [number] JSON elements about %s. Generation completed at [current timestamp].",
                topic, topic, topic
        );

        return performanceTestService.runSinglePerformanceTest(query, testId)
                .thenApply(ResponseEntity::ok);
    }
}