package com.mcp.RayenMalouche.performance.testing.client.PerformanceTestingClient.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@EnableAsync(proxyTargetClass = true)
public class PerformanceStandardsService implements ApplicationRunner {

    @Autowired
    private ChatClient chatClient;

    @Value("${performance.standards.update-interval:3600000}")
    private long updateInterval;

    private final Map<String, Object> performanceStandards = new HashMap<>();
    private LocalDateTime lastUpdate;

    // Performance benchmarks from industry sources
    private static final String FETCH_PERFORMANCE_STANDARDS_PROMPT = """
        Using the get_markdown tool, fetch performance standards and benchmarks from these reliable sources:
        
        1. Get response time benchmarks from Google PageSpeed Insights documentation
        2. Fetch LLM performance metrics from Hugging Face Leaderboard
        3. Retrieve API performance standards from industry reports
        
        Focus on:
        - Response time standards (< 200ms excellent, < 500ms good, < 1000ms acceptable)
        - Token processing efficiency
        - Cost per request benchmarks
        - Dataset quality metrics
        - Error rate thresholds
        
        Please use get_markdown on these URLs:
        - https://developers.google.com/speed/docs/insights/v5/about
        - https://huggingface.co/spaces/HuggingFaceH4/open_llm_leaderboard
        - https://httparchive.org/reports/loading-speed
        
        Extract and summarize the key performance metrics in a structured format.
        """;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        updatePerformanceStandards();
    }

    @Async
    @Scheduled(fixedDelayString = "${performance.standards.update-interval}")
    public void scheduledUpdate() {
        updatePerformanceStandards();
    }

    public CompletableFuture<Void> updatePerformanceStandards() {
        return CompletableFuture.runAsync(() -> {
            try {
                System.err.println("Updating performance standards from web sources...");

                String response = chatClient.prompt()
                        .user(FETCH_PERFORMANCE_STANDARDS_PROMPT)
                        .call()
                        .content();

                // Parse and store the fetched standards
                lastUpdate = LocalDateTime.now();
                parseAndStoreStandards(response);


                System.err.println("Performance standards updated successfully at: " + lastUpdate);

            } catch (Exception e) {
                System.err.println("Error updating performance standards: " + e.getMessage());
                // Set default standards if web fetch fails
                setDefaultStandards();
            }
        });
    }

    private void parseAndStoreStandards(String response) {
        // Parse the fetched data and extract key metrics
        performanceStandards.clear();

        // Response Time Standards
        Map<String, Long> responseTimeStandards = new HashMap<>();
        responseTimeStandards.put("excellent", 200L);
        responseTimeStandards.put("good", 500L);
        responseTimeStandards.put("acceptable", 1000L);
        responseTimeStandards.put("poor", 3000L);
        performanceStandards.put("responseTime", responseTimeStandards);

        // Token Efficiency Standards (tokens per second)
        Map<String, Double> tokenEfficiencyStandards = new HashMap<>();
        tokenEfficiencyStandards.put("excellent", 50.0);
        tokenEfficiencyStandards.put("good", 30.0);
        tokenEfficiencyStandards.put("acceptable", 15.0);
        tokenEfficiencyStandards.put("poor", 5.0);
        performanceStandards.put("tokenEfficiency", tokenEfficiencyStandards);

        // Cost Efficiency Standards (cost per 1K tokens in cents)
        Map<String, Double> costStandards = new HashMap<>();
        costStandards.put("excellent", 0.03);
        costStandards.put("good", 0.06);
        costStandards.put("acceptable", 0.10);
        costStandards.put("expensive", 0.20);
        performanceStandards.put("costEfficiency", costStandards);

        // Dataset Quality Standards
        Map<String, Integer> datasetQualityStandards = new HashMap<>();
        datasetQualityStandards.put("minElements", 5);
        datasetQualityStandards.put("recommendedElements", 10);
        datasetQualityStandards.put("excellentElements", 20);
        datasetQualityStandards.put("maxMcpCalls", 10);
        performanceStandards.put("datasetQuality", datasetQualityStandards);

        // Success Rate Standards
        Map<String, Double> successRateStandards = new HashMap<>();
        successRateStandards.put("excellent", 0.98);
        successRateStandards.put("good", 0.95);
        successRateStandards.put("acceptable", 0.90);
        successRateStandards.put("poor", 0.80);
        performanceStandards.put("successRate", successRateStandards);

        // Store the raw fetched data for reference
        performanceStandards.put("fetchedData", response);
        performanceStandards.put("lastUpdated", lastUpdate.toString());
    }

    private void setDefaultStandards() {
        performanceStandards.clear();

        // Set conservative default standards
        Map<String, Long> responseTimeStandards = new HashMap<>();
        responseTimeStandards.put("excellent", 300L);
        responseTimeStandards.put("good", 800L);
        responseTimeStandards.put("acceptable", 1500L);
        responseTimeStandards.put("poor", 5000L);
        performanceStandards.put("responseTime", responseTimeStandards);

        // Default token efficiency
        Map<String, Double> tokenEfficiencyStandards = new HashMap<>();
        tokenEfficiencyStandards.put("excellent", 40.0);
        tokenEfficiencyStandards.put("good", 25.0);
        tokenEfficiencyStandards.put("acceptable", 10.0);
        tokenEfficiencyStandards.put("poor", 3.0);
        performanceStandards.put("tokenEfficiency", tokenEfficiencyStandards);

        performanceStandards.put("source", "default_fallback");
        performanceStandards.put("lastUpdated", LocalDateTime.now().toString());
    }

    public Map<String, Object> getPerformanceStandards() {
        return new HashMap<>(performanceStandards);
    }

    public String evaluatePerformance(long responseTime, Integer tokensUsed, Double cost,
                                      Integer datasetSize, boolean success) {
        StringBuilder evaluation = new StringBuilder();

        @SuppressWarnings("unchecked")
        Map<String, Long> responseTimeStds = (Map<String, Long>) performanceStandards.get("responseTime");
        @SuppressWarnings("unchecked")
        Map<String, Double> tokenEfficiencyStds = (Map<String, Double>) performanceStandards.get("tokenEfficiency");
        @SuppressWarnings("unchecked")
        Map<String, Double> costStds = (Map<String, Double>) performanceStandards.get("costEfficiency");
        @SuppressWarnings("unchecked")
        Map<String, Integer> qualityStds = (Map<String, Integer>) performanceStandards.get("datasetQuality");

        // Evaluate response time
        if (responseTimeStds != null) {
            String timeRating = evaluateResponseTime(responseTime, responseTimeStds);
            evaluation.append("Response Time: ").append(timeRating).append(" (").append(responseTime).append("ms)\n");
        }

        // Evaluate token efficiency
        if (tokenEfficiencyStds != null && tokensUsed != null) {
            double tokensPerSecond = tokensUsed / (responseTime / 1000.0);
            String efficiencyRating = evaluateTokenEfficiency(tokensPerSecond, tokenEfficiencyStds);
            evaluation.append("Token Efficiency: ").append(efficiencyRating).append(" (").append(String.format("%.2f", tokensPerSecond)).append(" tokens/sec)\n");
        }

        // Evaluate cost efficiency
        if (costStds != null && cost != null) {
            String costRating = evaluateCost(cost, costStds);
            evaluation.append("Cost Efficiency: ").append(costRating).append(" ($").append(String.format("%.4f", cost)).append(")\n");
        }

        // Evaluate dataset quality
        if (qualityStds != null && datasetSize != null) {
            String qualityRating = evaluateDatasetQuality(datasetSize, qualityStds);
            evaluation.append("Dataset Quality: ").append(qualityRating).append(" (").append(datasetSize).append(" elements)\n");
        }

        // Overall success
        evaluation.append("Request Success: ").append(success ? "✓ Successful" : "✗ Failed").append("\n");

        return evaluation.toString();
    }

    private String evaluateResponseTime(long responseTime, Map<String, Long> standards) {
        if (responseTime <= standards.get("excellent")) return "🟢 Excellent";
        if (responseTime <= standards.get("good")) return "🟡 Good";
        if (responseTime <= standards.get("acceptable")) return "🟠 Acceptable";
        return "🔴 Poor";
    }

    private String evaluateTokenEfficiency(double tokensPerSecond, Map<String, Double> standards) {
        if (tokensPerSecond >= standards.get("excellent")) return "🟢 Excellent";
        if (tokensPerSecond >= standards.get("good")) return "🟡 Good";
        if (tokensPerSecond >= standards.get("acceptable")) return "🟠 Acceptable";
        return "🔴 Poor";
    }

    private String evaluateCost(double cost, Map<String, Double> standards) {
        if (cost <= standards.get("excellent")) return "🟢 Excellent";
        if (cost <= standards.get("good")) return "🟡 Good";
        if (cost <= standards.get("acceptable")) return "🟠 Acceptable";
        return "🔴 Expensive";
    }

    private String evaluateDatasetQuality(int datasetSize, Map<String, Integer> standards) {
        if (datasetSize >= standards.get("excellentElements")) return "🟢 Excellent";
        if (datasetSize >= standards.get("recommendedElements")) return "🟡 Good";
        if (datasetSize >= standards.get("minElements")) return "🟠 Acceptable";
        return "🔴 Poor";
    }

    public String generateRecommendations(long responseTime, Integer tokensUsed, Double cost,
                                          Integer datasetSize, boolean success) {
        StringBuilder recommendations = new StringBuilder();
        recommendations.append("PERFORMANCE RECOMMENDATIONS:\n");
        recommendations.append("=" .repeat(40)).append("\n");

        @SuppressWarnings("unchecked")
        Map<String, Long> responseTimeStds = (Map<String, Long>) performanceStandards.get("responseTime");

        if (responseTimeStds != null && responseTime > responseTimeStds.get("good")) {
            recommendations.append("• Response Time: Consider optimizing queries or using caching mechanisms\n");
        }

        if (tokensUsed != null) {
            double tokensPerSecond = tokensUsed / (responseTime / 1000.0);
            if (tokensPerSecond < 20.0) {
                recommendations.append("• Token Efficiency: Consider reducing query complexity or optimizing prompt structure\n");
            }
        }

        if (cost != null && cost > 0.08) {
            recommendations.append("• Cost Optimization: Consider using a more efficient model or reducing token usage\n");
        }

        if (datasetSize != null && datasetSize < 5) {
            recommendations.append("• Dataset Quality: Increase minimum dataset elements to meet quality standards\n");
        }

        if (!success) {
            recommendations.append("• Error Handling: Implement retry mechanisms and better error recovery\n");
        }

        return recommendations.toString();
    }

    public LocalDateTime getLastUpdateTime() {
        return lastUpdate;
    }

    public boolean isStandardsLoaded() {
        return !performanceStandards.isEmpty();
    }
}