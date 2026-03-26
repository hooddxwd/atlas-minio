package org.apache.atlas.minio.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a classification rule for automatic classification
 * Rules are evaluated to determine if an entity should be classified
 */
public class ClassificationRule {
    private String name;
    private RuleType type;
    private String pattern;
    private String classification;
    private Map<String, Object> attributes;
    private boolean enabled;

    // For metadata rules
    private String key;
    private String value;

    // For size rules
    private Long sizeMin;
    private Long sizeMax;

    public enum RuleType {
        PATH,
        CONTENT_TYPE,
        METADATA,
        SIZE
    }

    public ClassificationRule() {
        this.attributes = new HashMap<>();
        this.enabled = true;
    }

    public ClassificationRule(String name, RuleType type, String pattern, String classification) {
        this();
        this.name = name;
        this.type = type;
        this.pattern = pattern;
        this.classification = classification;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RuleType getType() {
        return type;
    }

    public void setType(RuleType type) {
        this.type = type;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(String classification) {
        this.classification = classification;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public void addAttribute(String key, Object value) {
        if (this.attributes == null) {
            this.attributes = new HashMap<>();
        }
        this.attributes.put(key, value);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Long getSizeMin() {
        return sizeMin;
    }

    public void setSizeMin(Long sizeMin) {
        this.sizeMin = sizeMin;
    }

    public Long getSizeMax() {
        return sizeMax;
    }

    public void setSizeMax(Long sizeMax) {
        this.sizeMax = sizeMax;
    }

    @Override
    public String toString() {
        return "ClassificationRule{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", pattern='" + pattern + '\'' +
                ", classification='" + classification + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
