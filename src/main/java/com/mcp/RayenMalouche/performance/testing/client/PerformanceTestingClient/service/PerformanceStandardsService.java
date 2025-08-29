package com.mcp.RayenMalouche.performance.testing.client.PerformanceTestingClient.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
@EnableAsync(proxyTargetClass = true)
public class PerformanceStandardsService implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceStandardsService.class);

    @Autowired
    private ChatClient chatClient;

    @Value("${performance.standards.update-interval:3600000}")
    private long updateInterval;

    @Value("classpath:/prompts/standards-update-prompt.md")
    private Resource systemPromptResource;

    @Value("${performance.standards.sources.nlp-benchmarks}")
    private String nlpBenchmarksUrl;

    @Value("${performance.standards.sources.api-performance}")
    private String apiPerformanceUrl;

    @Value("${performance.standards.sources.llm-metrics}")
    private String llmMetricsUrl;

    private final Map<String, Object> performanceStandards = new HashMap<>();
    private LocalDateTime lastUpdate;

    private static final String CONSTANT_USER_MESSAGE = "Update the performance standards from the web sources.";

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
                logger.info("Updating performance standards from web sources...");

                SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemPromptResource);
                Message systemMessage = systemPromptTemplate.createMessage(Map.of(
                        "nlpBenchmarksUrl", nlpBenchmarksUrl,
                        "apiPerformanceUrl", apiPerformanceUrl,
                        "llmMetricsUrl", llmMetricsUrl
                ));

                UserMessage userMessage = new UserMessage(CONSTANT_USER_MESSAGE);

                Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

                String response = chatClient.prompt(prompt)
                        .call()
                        .content();
                logger.debug("Raw AI response (first 500 chars): {}", response.length() > 500 ? response.substring(0, 500) : response);
                logger.debug("Raw AI response starts with backtick: {}", response.startsWith("`"));
                logger.debug("Raw AI response ends with backtick: {}", response.endsWith("`"));
                logger.debug("Raw AI response contains code fence: {}", response.contains("```"));

                lastUpdate = LocalDateTime.now();
                parseAndStoreStandards(response);

                logger.info("Performance standards updated successfully at: {}", lastUpdate);

            } catch (Exception e) {
                logger.error("Error updating performance standards: {}", e.getMessage(), e);
                if (e.getCause() instanceof org.stringtemplate.v4.compiler.STException) {
                    logger.error("StringTemplate parsing error: {}", e.getCause().getMessage());
                }
                setDefaultStandards();
            }
        });
    }

    private void parseAndStoreStandards(String response) {
        performanceStandards.clear();
        try {
            // Clean response to remove backticks or code fences
            String cleanedResponse = response.trim();
            if (cleanedResponse.startsWith("```json") && cleanedResponse.endsWith("```")) {
                cleanedResponse = cleanedResponse.substring(7, cleanedResponse.length() - 3).trim();
                logger.debug("Cleaned AI response (removed ```json fence, first 500 chars): {}", cleanedResponse.length() > 500 ? cleanedResponse.substring(0, 500) : cleanedResponse);
            } else if (cleanedResponse.startsWith("```") && cleanedResponse.endsWith("```")) {
                cleanedResponse = cleanedResponse.substring(3, cleanedResponse.length() - 3).trim();
                logger.debug("Cleaned AI response (removed generic code fence, first 500 chars): {}", cleanedResponse.length() > 500 ? cleanedResponse.substring(0, 500) : cleanedResponse);
            } else if (cleanedResponse.startsWith("`")) {
                cleanedResponse = cleanedResponse.substring(1).trim();
                logger.debug("Cleaned AI response (removed leading backtick, first 500 chars): {}", cleanedResponse.length() > 500 ? cleanedResponse.substring(0, 500) : cleanedResponse);
            }

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> parsed = mapper.readValue(cleanedResponse, new TypeReference<Map<String, Object>>(){});
            performanceStandards.putAll(parsed);
            performanceStandards.put("fetchedData", response);
            performanceStandards.put("lastUpdated", lastUpdate.toString());
            performanceStandards.put("parsedItems", parsed.size());
            logger.info("Parsed {} metrics from JSON response", parsed.size());
        } catch (Exception e) {
            logger.warn("Failed to parse JSON response: {}. Falling back to regex parsing.", e.getMessage());
            parseWithRegex(response);
        }
    }

    private void parseWithRegex(String response) {
        String metricsSection = response;
        int startIdx = response.indexOf("- Response Time Standards:");
        if (startIdx != -1) {
            metricsSection = response.substring(startIdx);
        } else {
            logger.warn("Could not isolate metrics section; using full response");
        }

        Pattern pattern = Pattern.compile("(?i)-\\s*(Response Time|Token Efficiency|Cost per Request|Dataset Quality|Error Rate Threshold)\\s*(Excellent|Good|Acceptable|Poor|Min|Recommended|ExcellentElements)?\\s*:\\s*([0-9.]+|\\\\$X|X)\\s*(ms|tokens\\/sec|cents|elements|%)?\\s*\\(source:.*?\\)");
        Matcher matcher = pattern.matcher(metricsSection);

        Map<String, Long> responseTimeStandards = new HashMap<>();
        Map<String, Double> tokenEfficiencyStandards = new HashMap<>();
        Map<String, Double> costStandards = new HashMap<>();
        Map<String, Integer> datasetQualityStandards = new HashMap<>();
        Map<String, Double> successRateStandards = new HashMap<>();

        int parsedCount = 0;
        while (matcher.find()) {
            String category = matcher.group(1).toLowerCase();
            String level = matcher.group(2) != null ? matcher.group(2).toLowerCase() : "unknown";
            String valueStr = matcher.group(3);
            String unit = matcher.group(4) != null ? matcher.group(4).toLowerCase() : "";

            if (valueStr.equals("\\$X") || valueStr.equals("X")) {
                continue;
            }

            double value = Double.parseDouble(valueStr);
            logger.debug("Parsed: Category={}, Level={}, Value={}, Unit={}", category, level, value, unit);

            if (unit.equals("sec") && category.contains("time")) {
                value *= 1000;
            } else if (unit.equals("%") && category.contains("error")) {
                value = 1 - (value / 100);
            } else if (unit.equals("cents") && category.contains("cost")) {
                value /= 1000;
            }

            if (category.contains("response")) {
                mapToResponseTime(level, (long) value, responseTimeStandards);
            } else if (category.contains("token")) {
                mapToTokenEfficiency(level, value, tokenEfficiencyStandards);
            } else if (category.contains("cost")) {
                mapToCost(level, value, costStandards);
            } else if (category.contains("dataset")) {
                mapToDatasetQuality(level, (int) value, datasetQualityStandards);
            } else if (category.contains("error")) {
                mapToSuccessRate(level, value, successRateStandards);
            }
            parsedCount++;
        }

        if (responseTimeStandards.isEmpty()) setDefaultResponseTimeStandards(responseTimeStandards);
        if (tokenEfficiencyStandards.isEmpty()) setDefaultTokenEfficiencyStandards(tokenEfficiencyStandards);
        if (costStandards.isEmpty()) setDefaultCostStandards(costStandards);
        if (datasetQualityStandards.isEmpty()) setDefaultDatasetQualityStandards(datasetQualityStandards);
        if (successRateStandards.isEmpty()) setDefaultSuccessRateStandards(successRateStandards);

        performanceStandards.put("responseTime", responseTimeStandards);
        performanceStandards.put("tokenEfficiency", tokenEfficiencyStandards);
        performanceStandards.put("costEfficiency", costStandards);
        performanceStandards.put("datasetQuality", datasetQualityStandards);
        performanceStandards.put("successRate", successRateStandards);
        performanceStandards.put("fetchedData", response);
        performanceStandards.put("lastUpdated", lastUpdate.toString());
        performanceStandards.put("parsedItems", parsedCount);

        if (parsedCount == 0) {
            logger.warn("No metrics parsed from response. Using defaults.");
        } else {
            logger.info("Successfully parsed {} metrics with regex.", parsedCount);
        }
    }

    private void mapToResponseTime(String level, long value, Map<String, Long> map) {
        switch (level) {
            case "excellent": map.put("excellent", value); break;
            case "good": map.put("good", value); break;
            case "acceptable": map.put("acceptable", value); break;
            case "poor": map.put("poor", value); break;
        }
    }

    private void mapToTokenEfficiency(String level, double value, Map<String, Double> map) {
        switch (level) {
            case "excellent": map.put("excellent", value); break;
            case "good": map.put("good", value); break;
            case "acceptable": map.put("acceptable", value); break;
            case "poor": map.put("poor", value); break;
        }
    }

    private void mapToCost(String level, double value, Map<String, Double> map) {
        switch (level) {
            case "excellent": map.put("excellent", value); break;
            case "good": map.put("good", value); break;
            case "acceptable": map.put("acceptable", value); break;
            case "expensive": map.put("expensive", value); break;
        }
    }

    private void mapToDatasetQuality(String level, int value, Map<String, Integer> map) {
        switch (level) {
            case "min": case "minelements": map.put("minElements", value); break;
            case "recommended": case "recommendedelements": map.put("recommendedElements", value); break;
            case "excellent": case "excellentelements": map.put("excellentElements", value); break;
        }
    }

    private void mapToSuccessRate(String level, double value, Map<String, Double> map) {
        switch (level) {
            case "excellent": map.put("excellent", value); break;
            case "good": map.put("good", value); break;
            case "acceptable": map.put("acceptable", value); break;
            case "poor": map.put("poor", value); break;
        }
    }

    private void setDefaultStandards() {
        setDefaultResponseTimeStandards(new HashMap<>());
        setDefaultTokenEfficiencyStandards(new HashMap<>());
        setDefaultCostStandards(new HashMap<>());
        setDefaultDatasetQualityStandards(new HashMap<>());
        setDefaultSuccessRateStandards(new HashMap<>());
        performanceStandards.put("source", "default_fallback");
        performanceStandards.put("lastUpdated", lastUpdate.toString());
    }

    private void setDefaultResponseTimeStandards(Map<String, Long> map) {
        map.put("excellent", 200L);
        map.put("good", 500L);
        map.put("acceptable", 1000L);
        map.put("poor", 3000L);
    }

    private void setDefaultTokenEfficiencyStandards(Map<String, Double> map) {
        map.put("excellent", 50.0);
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

    private void setDefaultSuccessRateStandards(Map<String, Double> map) {
        map.put("excellent", 0.98);
        map.put("good", 0.95);
        map.put("acceptable", 0.90);
        map.put("poor", 0.80);
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

        if (responseTimeStds != null) {
            String timeRating = evaluateResponseTime(responseTime, responseTimeStds);
            evaluation.append("Response Time: ").append(timeRating).append(" (").append(responseTime).append("ms)\n");
        }

        if (tokenEfficiencyStds != null && tokensUsed != null) {
            double tokensPerSecond = tokensUsed / (responseTime / 1000.0);
            String efficiencyRating = evaluateTokenEfficiency(tokensPerSecond, tokenEfficiencyStds);
            evaluation.append("Token Efficiency: ").append(efficiencyRating).append(" (").append(String.format("%.2f", tokensPerSecond)).append(" tokens/sec)\n");
        }

        if (costStds != null && cost != null) {
            String costRating = evaluateCost(cost, costStds);
            evaluation.append("Cost Efficiency: ").append(costRating).append(" ($").append(String.format("%.4f", cost)).append(")\n");
        }

        if (qualityStds != null && datasetSize != null) {
            String qualityRating = evaluateDatasetQuality(datasetSize, qualityStds);
            evaluation.append("Dataset Quality: ").append(qualityRating).append(" (").append(datasetSize).append(" elements)\n");
        }

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