package com.mcp.RayenMalouche.performance.testing.client.PerformanceTestingClient.service;

/*
Designed to update standards dynamically from the internet
It uses an AI prompt (FETCH_PERFORMANCE_STANDARDS_PROMPT) to fetch and summarize
data from specific URLs (e.g., Google PageSpeed, Hugging Face, HTTP Archive) via tools like get_markdown.
It runs this fetch on startup (via ApplicationRunner) and periodically (via @Scheduled every
performance.standards.update-interval ms, default 1 hour).
*/


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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@EnableAsync(proxyTargetClass = true)
public class PerformanceStandardsService implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceStandardsService.class);

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
            
            Extract and summarize the key performance metrics in a structured format like:
            - Response Time Excellent: 200ms (source: Google PageSpeed)
            - Token Efficiency Good: 30 tokens/sec (source: Hugging Face)
            """;  // Tweak: Made output format more parse-friendly

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
                //System.err.println("Updating performance standards from web sources...");
                logger.info("Updating performance standards from web sources...");

                String response = chatClient.prompt()
                        .user(FETCH_PERFORMANCE_STANDARDS_PROMPT)
                        .call()
                        .content();

                // Parse and store the fetched standards
                lastUpdate = LocalDateTime.now();
                parseAndStoreStandards(response);


                //System.err.println("Performance standards updated successfully at: " + lastUpdate);
                logger.info("Performance standards updated successfully at: {}", lastUpdate);

            } catch (Exception e) {
                //System.err.println("Error updating performance standards: " + e.getMessage());
                logger.error("Error updating performance standards: {}", e.getMessage(), e);
                // Set default standards if web fetch fails
                setDefaultStandards();
            }
        });
    }

    /*
        private void parseAndStoreStandards(String response) {
            // Parse the fetched data and extract key metrics
            performanceStandards.clear(); //still haven't introduced the parsing logic, so we're using static data

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
    */
    private void parseAndStoreStandards(String response) {
        performanceStandards.clear();
        logger.debug("Parsing response: {}", response);

        // Regex to match lines like "- Category Metric Level: Value unit (desc)"
        // Flexible to handle variations (e.g., "Excellent: 200ms", "Good: 30 tokens/sec")
        Pattern pattern = Pattern.compile("(?i)-\\s*(Response Time|TTFB|LCP|FCP|Token Efficiency|TPS|Tokens per second|Cost|Dataset|Success Rate|Uptime|Error Rate)\\s*(Excellent|Good|Acceptable|Poor|Min|Recommended|Max|High|Low)?\\s*:\\s*([0-9.]+)\\s*(ms|sec|tokens/sec|tokens/s|t/s|cents|%|elements)?\\s*\\(?(.*?)\\)?", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);

        // Temporary maps to build from parsed data
        Map<String, Long> responseTimeStandards = new HashMap<>();
        Map<String, Double> tokenEfficiencyStandards = new HashMap<>();
        Map<String, Double> costStandards = new HashMap<>();
        Map<String, Integer> datasetQualityStandards = new HashMap<>();
        Map<String, Double> successRateStandards = new HashMap<>();

        int parsedCount = 0;
        while (matcher.find()) {
            String category = matcher.group(1).toLowerCase();
            String level = matcher.group(2) != null ? matcher.group(2).toLowerCase() : "unknown";
            double value = Double.parseDouble(matcher.group(3));
            String unit = matcher.group(4) != null ? matcher.group(4).toLowerCase() : "";
            String desc = matcher.group(5);

            logger.debug("Parsed: Category={}, Level={}, Value={}, Unit={}, Desc={}", category, level, value, unit, desc);

            // Convert units (e.g., sec to ms)
            if (unit.contains("sec")) {
                value *= 1000;  // Assume ms for time
            }

            // Assign to maps based on category
            if (category.contains("response") || category.contains("ttfb") || category.contains("lcp") || category.contains("fcp")) {
                mapToResponseTime(level, (long) value, responseTimeStandards);
            } else if (category.contains("token") || category.contains("tps") || category.contains("t/s")) {
                mapToTokenEfficiency(level, value, tokenEfficiencyStandards);
            } else if (category.contains("cost")) {
                mapToCost(level, value, costStandards);
            } else if (category.contains("dataset")) {
                mapToDatasetQuality(level, (int) value, datasetQualityStandards);
            } else if (category.contains("success") || category.contains("uptime") || category.contains("error")) {
                // For error rate, invert if needed (e.g., error poor: >20% -> success poor: <80%)
                if (category.contains("error")) {
                    value = 100 - value;  // Rough inversion for success rate
                }
                mapToSuccessRate(level, value / 100, successRateStandards);  // As decimal
            }
            parsedCount++;
        }

        // If nothing parsed for a category, use defaults
        if (responseTimeStandards.isEmpty()) {
            setDefaultResponseTimeStandards(responseTimeStandards);
        }
        if (tokenEfficiencyStandards.isEmpty()) {
            setDefaultTokenEfficiencyStandards(tokenEfficiencyStandards);
        }
        if (costStandards.isEmpty()) {
            setDefaultCostStandards(costStandards);
        }
        if (datasetQualityStandards.isEmpty()) {
            setDefaultDatasetQualityStandards(datasetQualityStandards);
        }
        if (successRateStandards.isEmpty()) {
            setDefaultsuccessRateStandards(successRateStandards);
        }

        // Store the maps
        performanceStandards.put("responseTime", responseTimeStandards);
        performanceStandards.put("tokenEfficiency", tokenEfficiencyStandards);
        performanceStandards.put("costEfficiency", costStandards);
        performanceStandards.put("datasetQuality", datasetQualityStandards);
        performanceStandards.put("successRate", successRateStandards);

        // Store metadata
        performanceStandards.put("fetchedData", response);
        performanceStandards.put("lastUpdated", lastUpdate.toString());
        performanceStandards.put("parsedItems", parsedCount);

        if (parsedCount == 0) {
            logger.warn("No metrics parsed from response. Using defaults.");
        }
    }

    // Helper methods to map parsed levels to your standard keys
    private void mapToResponseTime(String level, long value, Map<String, Long> map) {
        if (level.contains("excellent")) {
            map.put("excellent", Math.min(map.getOrDefault("excellent", Long.MAX_VALUE), value));
        } else if (level.contains("good")) {
            map.put("good", value);
        } else if (level.contains("acceptable")) {
            map.put("acceptable", value);
        } else if (level.contains("poor")) {
            map.put("poor", value);
        }
    }

    // Similar for other categories (adapt as needed)
    private void mapToTokenEfficiency(String level, double value, Map<String, Double> map) {
        if (level.contains("excellent")) {
            map.put("excellent", value);
        } else if (level.contains("good")) {
            map.put("good", value);
        } else if (level.contains("acceptable")) {
            map.put("acceptable", value);
        } else if (level.contains("poor")) {
            map.put("poor", value);
        }
    }

    private void mapToCost(String level, double value, Map<String, Double> map) {
        if (level.contains("excellent")) {
            map.put("excellent", value);
        } else if (level.contains("good")) {
            map.put("good", value);
        } else if (level.contains("acceptable")) {
            map.put("acceptable", value);
        } else if (level.contains("expensive")) {
            map.put("expensive", value);
        }
    }

    private void mapToDatasetQuality(String level, int value, Map<String, Integer> map) {
        if (level.contains("min")) {
            map.put("minElements", value);
        } else if (level.contains("recommended")) {
            map.put("recommendedElements", value);
        } else if (level.contains("excellent")) {
            map.put("excellentElements", value);
        } else if (level.contains("maxMcp")) {
            map.put("maxMcpCalls", value);
        }
    }

    private void mapToSuccessRate(String level, double value, Map<String, Double> map) {
        if (level.contains("excellent")) {
            map.put("excellent", value);
        } else if (level.contains("good")) {
            map.put("good", value);
        } else if (level.contains("acceptable")) {
            map.put("acceptable", value);
        } else if (level.contains("poor")) {
            map.put("poor", value);
        }
    }

    // Default setters (Slightly updated with 2025 benchmarks)
    private void setDefaultResponseTimeStandards(Map<String, Long> map) {
        map.put("excellent", 200L);  // From API standards ~100-200ms
        map.put("good", 500L);
        map.put("acceptable", 1000L);
        map.put("poor", 3000L);
    }

    private void setDefaultTokenEfficiencyStandards(Map<String, Double> map) {
        map.put("excellent", 50.0);  // From LLM benchmarks ~50+ t/s
        map.put("good", 30.0);
        map.put("acceptable", 15.0);
        map.put("poor", 5.0);
    }

   private void setDefaultCostStandards(Map<String, Double> map) {
        map.put("excellent", 0.03);
        map.put("good", 0.06);
        map.put("acceptable", 0.10);
        map.put("expensive", 0.20);
   }
    private void setDefaultDatasetQualityStandards(Map<String, Integer> map) {
        map.put("minElements", 5);
        map.put("recommendedElements", 10);
        map.put("excellentElements", 20);
        map.put("maxMcpCalls", 10);
    }
    private void setDefaultsuccessRateStandards(Map<String, Double> map) {
        map.put("excellent", 0.98);
        map.put("good", 0.95);
        map.put("acceptable", 0.90);
        map.put("poor", 0.80);
    }

/*
    private void setDefaultStandards() {
        //If the fetch fails, setDefaultStandards() hardcodes values.
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
*/
    private void setDefaultStandards() {
        setDefaultResponseTimeStandards(new HashMap<>());
        setDefaultTokenEfficiencyStandards(new HashMap<>());
        setDefaultCostStandards(new HashMap<>());
        setDefaultDatasetQualityStandards(new HashMap<>());
        setDefaultsuccessRateStandards(new HashMap<>());
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
        recommendations.append("=".repeat(40)).append("\n");

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