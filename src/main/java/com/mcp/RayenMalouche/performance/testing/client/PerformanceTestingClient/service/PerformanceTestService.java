package com.mcp.RayenMalouche.performance.testing.client.PerformanceTestingClient.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class PerformanceTestService {

    @Autowired
    private PerformanceStandardsService performanceStandardsService;

    @Value("${test.target.discovery.url:http://localhost:8072}")
    private String targetDiscoveryUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final Map<String, TestMetrics> testResults = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    // Test result classes
    public static class TestMetrics {
        public String testId;
        public String query;
        public String timestamp;
        public long responseTime;
        public boolean success;
        public String errorMessage;
        public Integer tokensUsed;
        public Double cost;
        public Integer datasetSize;
        public Integer mcpCallsCount;
        public List<String> urlsFetched = new ArrayList<>();
        public String performanceEvaluation;
        public String recommendations;

        public TestMetrics(String testId, String query) {
            this.testId = testId;
            this.query = query;
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    public static class LoadTestResult {
        public String testId;
        public String startTime;
        public String endTime;
        public int totalTests;
        public int successfulTests;
        public int failedTests;
        public double averageResponseTime;
        public int totalTokens;
        public double totalCost;
        public int concurrentUsers;
        public int requestsPerUser;
        public String performanceEvaluation;
        public List<String> recommendations = new ArrayList<>();
    }

    public CompletableFuture<TestMetrics> runSinglePerformanceTest(String query, String testId) {
        return CompletableFuture.supplyAsync(() -> {
            TestMetrics testMetrics = new TestMetrics(testId, query);
            long startTime = System.currentTimeMillis();

            try {
                // Create request with explicit email instruction
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("message", query +
                        "\n\nIMPERATIVE: After creating the dataset, you MUST send an email notification using the send-email tool with these exact parameters:" +
                        "\n- to: rayenmalouche@gmail.com" +
                        "\n- subject: Dataset for [topic] created successfully" +
                        "\n- body: Dataset has been generated with [number] elements on [topic]. Generation completed at [timestamp].");
                requestBody.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                String jsonBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(requestBody);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(targetDiscoveryUrl + "/discovery-ai/chat"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofMinutes(5))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                long endTime = System.currentTimeMillis();
                testMetrics.responseTime = endTime - startTime;
                testMetrics.success = response.statusCode() >= 200 && response.statusCode() < 300;

                if (testMetrics.success) {
                    analyzeResponseData(response.body(), testMetrics);

                    // Evaluate performance using standards
                    if (performanceStandardsService.isStandardsLoaded()) {
                        testMetrics.performanceEvaluation = performanceStandardsService.evaluatePerformance(
                                testMetrics.responseTime, testMetrics.tokensUsed, testMetrics.cost,
                                testMetrics.datasetSize, testMetrics.success);

                        testMetrics.recommendations = performanceStandardsService.generateRecommendations(
                                testMetrics.responseTime, testMetrics.tokensUsed, testMetrics.cost,
                                testMetrics.datasetSize, testMetrics.success);
                    } else {
                        testMetrics.performanceEvaluation = "Performance standards not loaded";
                        testMetrics.recommendations = "Update performance standards to get recommendations";
                    }
                } else {
                    testMetrics.errorMessage = "HTTP " + response.statusCode() + ": " + response.body();
                    testMetrics.performanceEvaluation = "Test failed - no evaluation available";
                    testMetrics.recommendations = "Fix the underlying issue causing test failure";
                }

            } catch (Exception e) {
                long endTime = System.currentTimeMillis();
                testMetrics.responseTime = endTime - startTime;
                testMetrics.success = false;
                testMetrics.errorMessage = e.getMessage();
                testMetrics.performanceEvaluation = "Test error - no evaluation available";
                testMetrics.recommendations = "Debug and fix the exception: " + e.getMessage();
            }

            testResults.put(testId, testMetrics);
            return testMetrics;
        }, executorService);
    }

    public CompletableFuture<Map<String, Object>> runLoadTest(int concurrentUsers, int requestsPerUser) {
        return CompletableFuture.supplyAsync(() -> {
            String loadTestId = "load_test_" + System.currentTimeMillis();
            Instant startTime = Instant.now();

            // Predefined test queries
            List<String> testQueries = Arrays.asList(
                    "Créez un dataset complet sur toutes les solutions Sage de Discovery Intech. IMPERATIVE: Send email to rayenmalouche@gmail.com after completion.",
                    "Générez un dataset détaillé sur les solutions QAD proposées par Discovery Intech. IMPERATIVE: Send email to rayenmalouche@gmail.com after completion.",
                    "Produisez un dataset sur les solutions Microsoft Dynamics 365 de Discovery Intech. IMPERATIVE: Send email to rayenmalouche@gmail.com after completion.",
                    "Créez un dataset sur les solutions SAP proposées par Discovery Intech. IMPERATIVE: Send email to rayenmalouche@gmail.com after completion."
            );

            List<CompletableFuture<TestMetrics>> futures = new ArrayList<>();

            // Create test tasks
            for (int user = 0; user < concurrentUsers; user++) {
                for (int req = 0; req < requestsPerUser; req++) {
                    String query = testQueries.get(req % testQueries.size());
                    String testId = String.format("%s_user%d_req%d", loadTestId, user, req);

                    CompletableFuture<TestMetrics> future = runSinglePerformanceTest(query, testId);
                    futures.add(future);
                }
            }

            // Wait for all tests to complete
            List<TestMetrics> results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            Instant endTime = Instant.now();

            // Process results
            List<TestMetrics> successfulTests = results.stream()
                    .filter(r -> r.success)
                    .collect(Collectors.toList());

            LoadTestResult loadTestResult = new LoadTestResult();
            loadTestResult.testId = loadTestId;
            loadTestResult.startTime = startTime.toString();
            loadTestResult.endTime = endTime.toString();
            loadTestResult.totalTests = results.size();
            loadTestResult.successfulTests = successfulTests.size();
            loadTestResult.failedTests = results.size() - successfulTests.size();
            loadTestResult.concurrentUsers = concurrentUsers;
            loadTestResult.requestsPerUser = requestsPerUser;

            if (!successfulTests.isEmpty()) {
                loadTestResult.averageResponseTime = successfulTests.stream()
                        .mapToLong(r -> r.responseTime)
                        .average()
                        .orElse(0.0);

                loadTestResult.totalTokens = successfulTests.stream()
                        .mapToInt(r -> r.tokensUsed != null ? r.tokensUsed : 0)
                        .sum();

                loadTestResult.totalCost = successfulTests.stream()
                        .mapToDouble(r -> r.cost != null ? r.cost : 0.0)
                        .sum();

                // Generate load test evaluation
                if (performanceStandardsService.isStandardsLoaded()) {
                    double successRate = (double) successfulTests.size() / results.size();
                    loadTestResult.performanceEvaluation = generateLoadTestEvaluation(loadTestResult, successRate);
                    loadTestResult.recommendations = generateLoadTestRecommendations(loadTestResult, successRate);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("loadTestResult", loadTestResult);
            response.put("detailedResults", results);

            return response;
        }, executorService);
    }

    private void analyzeResponseData(String responseData, TestMetrics testMetrics) {
        // Check for email sending confirmation
        boolean emailSent = responseData.toLowerCase().contains("email") &&
                (responseData.toLowerCase().contains("sent") || responseData.toLowerCase().contains("envoyé"));

        if (!emailSent) {
            if (testMetrics.recommendations == null) {
                testMetrics.recommendations = "";
            }
            testMetrics.recommendations += "\nWARNING: Email notification may not have been sent. Ensure send-email tool is working properly.\n";
        }

        // Count dataset elements
        int datasetCount = responseData.split("\"Input\":", -1).length - 1;
        testMetrics.datasetSize = datasetCount;

        // Count MCP calls
        String[] mcpPatterns = {"get_markdown", "get_raw_text", "get_rendered_html", "send-email"};
        int mcpCallsCount = 0;
        for (String pattern : mcpPatterns) {
            mcpCallsCount += responseData.split(pattern, -1).length - 1;
        }
        testMetrics.mcpCallsCount = mcpCallsCount;

        // Extract URLs
        Set<String> uniqueUrls = new HashSet<>();
        String[] urlPatterns = {"https://www\\.discoveryintech\\.com[^\\s]*", "http://[^\\s]*", "https://[^\\s]*"};
        for (String pattern : urlPatterns) {
            java.util.regex.Pattern compiledPattern = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher matcher = compiledPattern.matcher(responseData);
            while (matcher.find()) {
                uniqueUrls.add(matcher.group());
            }
        }
        testMetrics.urlsFetched = new ArrayList<>(uniqueUrls);

        // Estimate tokens (rough estimation: ~4 chars per token)
        testMetrics.tokensUsed = (int) Math.ceil(responseData.length() / 4.0);

        // Calculate cost (Groq pricing for llama-3.1-8b-instant)
        double inputCostPer1K = 0.05; // cents
        double outputCostPer1K = 0.08; // cents
        double inputTokens = testMetrics.tokensUsed * 0.7; // 70% input
        double outputTokens = testMetrics.tokensUsed * 0.3; // 30% output

        testMetrics.cost = (inputTokens / 1000 * inputCostPer1K) + (outputTokens / 1000 * outputCostPer1K);
    }

    private String generateLoadTestEvaluation(LoadTestResult result, double successRate) {
        StringBuilder evaluation = new StringBuilder();
        evaluation.append("LOAD TEST EVALUATION:\n");
        evaluation.append("=".repeat(30)).append("\n");

        // Success Rate
        if (successRate >= 0.98) {
            evaluation.append("Success Rate: Excellent (").append(String.format("%.1f%%", successRate * 100)).append(")\n");
        } else if (successRate >= 0.95) {
            evaluation.append("Success Rate: Good (").append(String.format("%.1f%%", successRate * 100)).append(")\n");
        } else if (successRate >= 0.90) {
            evaluation.append("Success Rate: Acceptable (").append(String.format("%.1f%%", successRate * 100)).append(")\n");
        } else {
            evaluation.append("Success Rate: Poor (").append(String.format("%.1f%%", successRate * 100)).append(")\n");
        }

        // Average Response Time
        Map<String, Object> standards = performanceStandardsService.getPerformanceStandards();
        @SuppressWarnings("unchecked")
        Map<String, Long> responseTimeStds = (Map<String, Long>) standards.get("responseTime");

        if (responseTimeStds != null) {
            if (result.averageResponseTime <= responseTimeStds.get("excellent")) {
                evaluation.append("Average Response Time: Excellent (").append(String.format("%.0f", result.averageResponseTime)).append("ms)\n");
            } else if (result.averageResponseTime <= responseTimeStds.get("good")) {
                evaluation.append("Average Response Time: Good (").append(String.format("%.0f", result.averageResponseTime)).append("ms)\n");
            } else if (result.averageResponseTime <= responseTimeStds.get("acceptable")) {
                evaluation.append("Average Response Time: Acceptable (").append(String.format("%.0f", result.averageResponseTime)).append("ms)\n");
            } else {
                evaluation.append("Average Response Time: Poor (").append(String.format("%.0f", result.averageResponseTime)).append("ms)\n");
            }
        }

        // Cost Analysis
        double averageCostPerTest = result.totalTests > 0 ? result.totalCost / result.totalTests : 0.0;
        evaluation.append("Average Cost per Test: $").append(String.format("%.4f", averageCostPerTest)).append("\n");
        evaluation.append("Total Cost: $").append(String.format("%.4f", result.totalCost)).append("\n");

        return evaluation.toString();
    }

    private List<String> generateLoadTestRecommendations(LoadTestResult result, double successRate) {
        List<String> recommendations = new ArrayList<>();

        if (successRate < 0.95) {
            recommendations.add("Improve system stability - success rate below 95%");
        }

        if (result.averageResponseTime > 1000) {
            recommendations.add("Optimize response times - consider caching or query optimization");
        }

        if (result.totalCost > 1.0) {
            recommendations.add("Monitor costs - total test cost exceeding $1.00");
        }

        if (result.concurrentUsers > 5 && result.failedTests > 0) {
            recommendations.add("Scale testing gradually - some failures under load");
        }

        return recommendations;
    }

    public Map<String, Object> getTestSummary() {
        Map<String, Object> summary = new HashMap<>();

        List<TestMetrics> allTests = new ArrayList<>(testResults.values());
        List<TestMetrics> successfulTests = allTests.stream()
                .filter(t -> t.success)
                .collect(Collectors.toList());

        summary.put("totalTests", allTests.size());
        summary.put("successfulTests", successfulTests.size());
        summary.put("failedTests", allTests.size() - successfulTests.size());

        if (!successfulTests.isEmpty()) {
            summary.put("averageResponseTime", successfulTests.stream()
                    .mapToLong(t -> t.responseTime)
                    .average().orElse(0.0));

            summary.put("totalTokens", successfulTests.stream()
                    .mapToInt(t -> t.tokensUsed != null ? t.tokensUsed : 0)
                    .sum());

            summary.put("totalCost", successfulTests.stream()
                    .mapToDouble(t -> t.cost != null ? t.cost : 0.0)
                    .sum());
        }

        // Add performance standards info
        summary.put("performanceStandardsLoaded", performanceStandardsService.isStandardsLoaded());
        summary.put("lastStandardsUpdate", performanceStandardsService.getLastUpdateTime());

        return summary;
    }

    public List<TestMetrics> getAllTestResults() {
        return new ArrayList<>(testResults.values());
    }

    public void clearTestResults() {
        testResults.clear();
    }

    public Map<String, Object> getPerformanceStandards() {
        return performanceStandardsService.getPerformanceStandards();
    }

    public CompletableFuture<Void> updatePerformanceStandards() {
        return performanceStandardsService.updatePerformanceStandards();
    }
}