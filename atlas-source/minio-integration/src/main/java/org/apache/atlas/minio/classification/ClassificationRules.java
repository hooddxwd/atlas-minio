package org.apache.atlas.minio.classification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.atlas.minio.model.MinioBucket;
import org.apache.atlas.minio.model.MinioObject;
import org.apache.atlas.minio.model.ClassificationRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rule engine for loading and evaluating classification rules
 * Loads rules from JSON configuration and evaluates them against entities
 */
@Component
public class ClassificationRules {
    private static final Logger LOG = LoggerFactory.getLogger(ClassificationRules.class);

    private List<ClassificationRule> rules;
    private final ObjectMapper objectMapper;
    private String rulesFilePath;

    public ClassificationRules() {
        this.rules = new ArrayList<>();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Initialize with default rules file path
     */
    @PostConstruct
    public void init() {
        // Default rules file location
        String defaultPath = System.getProperty("atlas.home", "/opt/atlas") + "/conf/classification-rules.json";
        loadRules(defaultPath);
    }

    /**
     * Load rules from JSON file
     *
     * @param path Path to rules JSON file
     */
    public void loadRules(String path) {
        this.rulesFilePath = path;
        LOG.info("Loading classification rules from: {}", path);

        try {
            File file = new File(path);
            if (!file.exists()) {
                LOG.warn("Classification rules file not found at: {}, using empty rules", path);
                this.rules = new ArrayList<>();
                return;
            }

            String jsonContent = new String(Files.readAllBytes(Paths.get(path)));
            RulesConfig config = objectMapper.readValue(jsonContent, RulesConfig.class);

            if (config != null && config.getRules() != null) {
                this.rules = config.getRules();
                LOG.info("Loaded {} classification rules", this.rules.size());

                // Log each rule
                for (ClassificationRule rule : this.rules) {
                    LOG.debug("Loaded rule: name={}, type={}, pattern={}, classification={}, enabled={}",
                            rule.getName(), rule.getType(), rule.getPattern(),
                            rule.getClassification(), rule.isEnabled());
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to load classification rules from: {}", path, e);
            this.rules = new ArrayList<>();
        }
    }

    /**
     * Evaluate all rules against a MinIO object
     *
     * @param object The MinIO object to evaluate
     * @return Map of classification names to their attributes
     */
    public Map<String, Map<String, Object>> evaluate(MinioObject object) {
        Map<String, Map<String, Object>> results = new HashMap<>();

        if (object == null || rules == null || rules.isEmpty()) {
            return results;
        }

        for (ClassificationRule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }

            try {
                if (matchesRule(object, rule)) {
                    LOG.debug("Rule {} matched for object {}/{}",
                            rule.getName(), object.getBucketName(), object.getPath());

                    Map<String, Object> attributes = rule.getAttributes() != null
                            ? new HashMap<>(rule.getAttributes())
                            : new HashMap<>();

                    results.put(rule.getClassification(), attributes);
                }
            } catch (Exception e) {
                LOG.error("Error evaluating rule {} for object {}/{}",
                        rule.getName(), object.getBucketName(), object.getPath(), e);
            }
        }

        LOG.debug("Evaluation result for object {}/{}: {} classifications matched",
                object.getBucketName(), object.getPath(), results.size());

        return results;
    }

    /**
     * Evaluate all rules against a MinIO bucket
     *
     * @param bucket The MinIO bucket to evaluate
     * @return Map of classification names to their attributes
     */
    public Map<String, Map<String, Object>> evaluate(MinioBucket bucket) {
        Map<String, Map<String, Object>> results = new HashMap<>();

        if (bucket == null || rules == null || rules.isEmpty()) {
            return results;
        }

        for (ClassificationRule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }

            try {
                if (matchesRule(bucket, rule)) {
                    LOG.debug("Rule {} matched for bucket {}", rule.getName(), bucket.getName());

                    Map<String, Object> attributes = rule.getAttributes() != null
                            ? new HashMap<>(rule.getAttributes())
                            : new HashMap<>();

                    results.put(rule.getClassification(), attributes);
                }
            } catch (Exception e) {
                LOG.error("Error evaluating rule {} for bucket {}", rule.getName(), bucket.getName(), e);
            }
        }

        LOG.debug("Evaluation result for bucket {}: {} classifications matched",
                bucket.getName(), results.size());

        return results;
    }

    /**
     * Check if an object matches a rule
     */
    private boolean matchesRule(MinioObject object, ClassificationRule rule) {
        switch (rule.getType()) {
            case PATH:
                return matchesPathRule(object, rule);

            case CONTENT_TYPE:
                return matchesContentTypeRule(object, rule);

            case METADATA:
                return matchesMetadataRule(object, rule);

            case SIZE:
                return matchesSizeRule(object, rule);

            default:
                LOG.warn("Unknown rule type: {}", rule.getType());
                return false;
        }
    }

    /**
     * Check if a bucket matches a rule
     */
    private boolean matchesRule(MinioBucket bucket, ClassificationRule rule) {
        switch (rule.getType()) {
            case PATH:
                // For buckets, path rules match against bucket name
                return matchesPathRule(bucket, rule);

            default:
                LOG.debug("Rule type {} not applicable to buckets", rule.getType());
                return false;
        }
    }

    /**
     * Check path-based rule
     */
    private boolean matchesPathRule(MinioObject object, ClassificationRule rule) {
        if (rule.getPattern() == null) {
            return false;
        }

        String fullPath = "/" + object.getBucketName() + "/" + object.getPath();
        try {
            Pattern pattern = Pattern.compile(rule.getPattern());
            return pattern.matcher(fullPath).matches();
        } catch (Exception e) {
            LOG.error("Invalid regex pattern in rule {}: {}", rule.getName(), rule.getPattern(), e);
            return false;
        }
    }

    /**
     * Check path-based rule for bucket
     */
    private boolean matchesPathRule(MinioBucket bucket, ClassificationRule rule) {
        if (rule.getPattern() == null) {
            return false;
        }

        String bucketPath = "/" + bucket.getName();
        try {
            Pattern pattern = Pattern.compile(rule.getPattern());
            return pattern.matcher(bucketPath).matches();
        } catch (Exception e) {
            LOG.error("Invalid regex pattern in rule {}: {}", rule.getName(), rule.getPattern(), e);
            return false;
        }
    }

    /**
     * Check content-type based rule
     */
    private boolean matchesContentTypeRule(MinioObject object, ClassificationRule rule) {
        if (rule.getPattern() == null || object.getContentType() == null) {
            return false;
        }

        try {
            Pattern pattern = Pattern.compile(rule.getPattern());
            return pattern.matcher(object.getContentType()).matches();
        } catch (Exception e) {
            LOG.error("Invalid regex pattern in rule {}: {}", rule.getName(), rule.getPattern(), e);
            return false;
        }
    }

    /**
     * Check metadata-based rule
     */
    private boolean matchesMetadataRule(MinioObject object, ClassificationRule rule) {
        if (rule.getKey() == null || object.getUserMetadata() == null) {
            return false;
        }

        String metadataValue = object.getUserMetadata().get(rule.getKey());
        if (metadataValue == null) {
            return false;
        }

        if (rule.getValue() != null) {
            return metadataValue.equals(rule.getValue());
        }

        return true;
    }

    /**
     * Check size-based rule
     */
    private boolean matchesSizeRule(MinioObject object, ClassificationRule rule) {
        long size = object.getSize();

        if (rule.getSizeMin() != null && size < rule.getSizeMin()) {
            return false;
        }

        if (rule.getSizeMax() != null && size > rule.getSizeMax()) {
            return false;
        }

        return true;
    }

    /**
     * Reload rules from file
     */
    public void reload() {
        if (rulesFilePath != null) {
            LOG.info("Reloading classification rules from: {}", rulesFilePath);
            loadRules(rulesFilePath);
        } else {
            LOG.warn("Cannot reload rules: no rules file path set");
        }
    }

    /**
     * Get all loaded rules
     */
    public List<ClassificationRule> getRules() {
        return new ArrayList<>(rules);
    }

    /**
     * Get rules by type
     */
    public List<ClassificationRule> getRulesByType(ClassificationRule.RuleType type) {
        return rules.stream()
                .filter(r -> r.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * Add a new rule dynamically
     */
    public void addRule(ClassificationRule rule) {
        if (rule != null) {
            this.rules.add(rule);
            LOG.info("Added new rule: name={}, type={}, classification={}",
                    rule.getName(), rule.getType(), rule.getClassification());
        }
    }

    /**
     * Remove a rule by name
     */
    public void removeRule(String name) {
        rules.removeIf(rule -> rule.getName().equals(name));
        LOG.info("Removed rule: {}", name);
    }

    /**
     * Clear all rules
     */
    public void clearRules() {
        rules.clear();
        LOG.info("Cleared all classification rules");
    }

    /**
     * Configuration class for JSON deserialization
     */
    public static class RulesConfig {
        private List<ClassificationRule> rules;

        public List<ClassificationRule> getRules() {
            return rules;
        }

        public void setRules(List<ClassificationRule> rules) {
            this.rules = rules;
        }
    }
}
