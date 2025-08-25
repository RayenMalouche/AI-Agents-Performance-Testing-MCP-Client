package com.mcp.RayenMalouche.performance.testing.client.PerformanceTestingClient.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PerformanceTestService {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceTestService.class);

    @Value("${test.target.discovery.url:http://localhost:8072}")
    private String discoveryClientUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model}")
    private String modelName;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Metrics storage
    private final Map<String, TestMetrics> testResults = new ConcurrentHashMap<>();
    private final AtomicLong totalTokensUsed = new AtomicLong(0);
    private final AtomicLong totalCost = new AtomicLong(0); // in cents

    // Test scenarios for Discovery Intech
    private final List<String> testQueries = Arrays.asList(
            "Créez un dataset sur les solutions Sage X3 de Discovery Intech",
            "Générez un dataset complet sur les services QAD proposés par Discovery Intech",
            "Créez un dataset détaillé sur les solutions Microsoft Dynamics 365 de Discovery Intech",
            "Produisez un dataset sur les secteurs d'activité couverts par Discovery Intech",
            "Générez un dataset sur les services d'intégration ERP de Discovery Intech",
            "Créez un dataset sur les solutions SAP S/4 HANA proposées par Discovery Intech",
            "Produisez un dataset sur l'équipe dirigeante de Discovery Intech",
            "Générez un dataset sur les partenaires de Discovery Intech",
            "Créez un dataset sur les références clients de Discovery Intech",
            "Produisez un dataset sur les actualités récentes de Discovery Intech"
    );

    public static class TestMetrics {
        public String testId;
        public String query;
        public Instant startTime;
        public Instant endTime;
        public Duration duration;
        public int tokensUsed;
        public double costInCents;
        public boolean successful;
        public String errorMessage;
        public int datasetSize; // Number of JSON elements generated
        public boolean emailSent;
        public int mcpCallsCount;
        public List<String> urlsFetched;

        // Performance metrics
        public double avgResponseTime;
        public int totalRequests;
        public int failedRequests;
    }

    public CompletableFuture<TestMetrics> runSinglePerformanceTest(String query, String testId) {
        return CompletableFuture.supplyAsync(() -> {
            TestMetrics metrics = new TestMetrics();
            metrics.testId = testId;
            metrics.query = query;
            metrics.startTime = Instant.now();
            metrics.urlsFetched = new ArrayList<>();

            try {
                logger.info("Starting performance test: {} with query: {}", testId, query);

                // Call the Discovery Intech MCP client
                String response = callDiscoveryClient(query);

                metrics.endTime = Instant.now();
                metrics.duration = Duration.between(metrics.startTime, metrics.endTime);

                // Analyze response for metrics
                analyzeResponse(response, metrics);

                metrics.successful = true;
                logger.info("Performance test {} completed successfully in {} ms",
                        testId, metrics.duration.toMillis());

            } catch (Exception e) {
                metrics.endTime = Instant.now();
                metrics.duration = Duration.between(metrics.startTime, metrics.endTime);
                metrics.successful = false;
                metrics.errorMessage = e.getMessage();
                logger.error("Performance test {} failed: {}", testId, e.getMessage());
            }

            testResults.put(testId, metrics);
            return metrics;
        });
    }

    private String callDiscoveryClient(String query) throws Exception {
        // Simulate calling the Discovery Intech MCP client
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("message", query);
        requestBody.put("timestamp", Instant.now().toString());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        // In real implementation, this would call your Discovery client endpoint
        ResponseEntity<String> response = restTemplate.postForEntity(
                discoveryClientUrl + "/discovery-ai/chat", request, String.class);

        return response.getBody();
    }

    private void analyzeResponse(String response, TestMetrics metrics) {
        try {
            // Count dataset elements (JSON objects)
            int jsonElementCount = countJsonElements(response);
            metrics.datasetSize = jsonElementCount;

            // Check if email was sent
            metrics.emailSent = response.contains("email") &&
                    response.contains("rayenmalouche@gmail.com");

            // Count MCP calls by looking for URL patterns
            metrics.mcpCallsCount = countMcpCalls(response);

            // Extract URLs used
            extractUrlsUsed(response, metrics);

            // Estimate token usage based on response length
            metrics.tokensUsed = estimateTokens(response);
            totalTokensUsed.addAndGet(metrics.tokensUsed);

            // Calculate cost based on model pricing
            metrics.costInCents = calculateCost(metrics.tokensUsed);
            totalCost.addAndGet((long) metrics.costInCents);

        } catch (Exception e) {
            logger.warn("Error analyzing response: {}", e.getMessage());
        }
    }

    private int countJsonElements(String response) {
        // Simple count of JSON objects in response
        return response.split("\"Input\"").length - 1;
    }

    private int countMcpCalls(String response) {
        // Count patterns indicating MCP tool usage
        int count = 0;
        String[] mcpPatterns = {"get_markdown", "get_raw_text", "get_rendered_html", "send-email"};
        for (String pattern : mcpPatterns) {
            count += response.split(pattern).length - 1;
        }
        return count;
    }

    private void extractUrlsUsed(String response, TestMetrics metrics) {
        // Extract URLs from response (simplified regex)
        String urlPattern = "https://www\\.discoveryintech\\.com[^\\s]+";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(urlPattern);
        java.util.regex.Matcher matcher = pattern.matcher(response);

        while (matcher.find()) {
            String url = matcher.group();
            if (!metrics.urlsFetched.contains(url)) {
                metrics.urlsFetched.add(url);
            }
        }
    }

    private int estimateTokens(String text) {
        // Rough estimation: ~4 characters per token
        return text.length() / 4;
    }

    private double calculateCost(int tokens) {
        // Groq pricing for llama-3.1-8b-instant (example rates)
        double inputCostPer1KTokens = 0.05; // cents
        double outputCostPer1KTokens = 0.08; // cents

        // Assume 70% input, 30% output
        double inputTokens = tokens * 0.7;
        double outputTokens = tokens * 0.3;

        return (inputTokens / 1000 * inputCostPer1KTokens) +
                (outputTokens / 1000 * outputCostPer1KTokens);
    }

    public CompletableFuture<Map<String, Object>> runLoadTest(int concurrentUsers, int requestsPerUser) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting load test: {} concurrent users, {} requests per user",
                    concurrentUsers, requestsPerUser);

            List<CompletableFuture<TestMetrics>> futures = new ArrayList<>();
            AtomicInteger testCounter = new AtomicInteger(0);

            Instant loadTestStart = Instant.now();

            // Create concurrent test tasks
            for (int user = 0; user < concurrentUsers; user++) {
                for (int req = 0; req < requestsPerUser; req++) {
                    String query = testQueries.get(req % testQueries.size());
                    String testId = "load_test_" + testCounter.incrementAndGet();

                    CompletableFuture<TestMetrics> future = runSinglePerformanceTest(query, testId);
                    futures.add(future);
                }
            }

            // Wait for all tests to complete
            CompletableFuture<Void> allTests = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));

            try {
                allTests.get(); // Wait for completion
                Instant loadTestEnd = Instant.now();

                // Compile results
                Map<String, Object> results = new HashMap<>();
                results.put("totalTests", futures.size());
                results.put("duration", Duration.between(loadTestStart, loadTestEnd));
                results.put("successful", futures.stream().mapToInt(f -> {
                    try { return f.get().successful ? 1 : 0; }
                    catch (Exception e) { return 0; }
                }).sum());
                results.put("failed", futures.size() - (Integer) results.get("successful"));
                results.put("totalTokens", totalTokensUsed.get());
                results.put("totalCostCents", totalCost.get());
                results.put("avgResponseTimeMs", calculateAverageResponseTime());

                logger.info("Load test completed: {}", results);
                return results;

            } catch (Exception e) {
                logger.error("Load test failed: {}", e.getMessage());
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("error", e.getMessage());
                return errorResult;
            }
        });
    }

    private double calculateAverageResponseTime() {
        return testResults.values().stream()
                .mapToLong(m -> m.duration.toMillis())
                .average()
                .orElse(0.0);
    }

    public Map<String, Object> getTestSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalTests", testResults.size());
        summary.put("successfulTests", testResults.values().stream().mapToInt(m -> m.successful ? 1 : 0).sum());
        summary.put("totalTokensUsed", totalTokensUsed.get());
        summary.put("totalCostCents", totalCost.get());
        summary.put("averageResponseTimeMs", calculateAverageResponseTime());
        summary.put("averageDatasetSize", testResults.values().stream()
                .mapToInt(m -> m.datasetSize).average().orElse(0.0));
        summary.put("emailSuccessRate", testResults.values().stream()
                .mapToDouble(m -> m.emailSent ? 1.0 : 0.0).average().orElse(0.0));

        return summary;
    }

    public List<TestMetrics> getAllTestResults() {
        return new ArrayList<>(testResults.values());
    }

    public void clearTestResults() {
        testResults.clear();
        totalTokensUsed.set(0);
        totalCost.set(0);
    }
}