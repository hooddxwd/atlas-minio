package org.apache.atlas.minio.classification;

import org.apache.atlas.AtlasClientV2;
import org.apache.atlas.AtlasServiceException;
import org.apache.atlas.minio.model.MinioBucket;
import org.apache.atlas.minio.model.MinioObject;
import org.apache.atlas.minio.model.ClassificationResult;
import org.apache.atlas.model.instance.AtlasClassification;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntityWithExtInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Apply intelligent automatic classification based on rules
 * Evaluates path patterns, content types, metadata, and custom rules
 * Automatic classifications can be overridden by manual classifications
 */
@Component
public class AutoClassifier {
    private static final Logger LOG = LoggerFactory.getLogger(AutoClassifier.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final AtlasClientV2 atlasClient;
    private final ClassificationRules rulesEngine;

    @Value("${atlas.minio.classification.auto.enabled:true}")
    private boolean autoClassifyEnabled;

    @Value("${atlas.minio.cluster.name:primary}")
    private String clusterName;

    @Autowired
    public AutoClassifier(AtlasClientV2 atlasClient, ClassificationRules rulesEngine) {
        this.atlasClient = atlasClient;
        this.rulesEngine = rulesEngine;
    }

    /**
     * Apply automatic classification rules to a MinIO object
     *
     * @param object The MinIO object to classify
     * @return ClassificationResult with applied classifications
     * @throws Exception if classification fails
     */
    public ClassificationResult autoClassify(MinioObject object) throws Exception {
        if (object == null) {
            throw new IllegalArgumentException("MinioObject cannot be null");
        }

        if (!autoClassifyEnabled) {
            LOG.debug("Auto-classification is disabled, skipping object {}/{}",
                    object.getBucketName(), object.getPath());
            return new ClassificationResult(getObjectQualifiedName(object),
                    ClassificationResult.ClassificationSource.AUTOMATIC);
        }

        String qualifiedName = getObjectQualifiedName(object);
        LOG.debug("Auto-classifying object: {}", qualifiedName);

        // Check if entity has manual classifications (manual takes priority)
        if (hasManualClassifications(qualifiedName)) {
            LOG.debug("Object {} has manual classifications, skipping auto-classification", qualifiedName);
            return new ClassificationResult(qualifiedName,
                    ClassificationResult.ClassificationSource.AUTOMATIC);
        }

        // Evaluate rules
        Map<String, Map<String, Object>> matchedClassifications = rulesEngine.evaluate(object);

        if (matchedClassifications.isEmpty()) {
            LOG.debug("No classification rules matched for object {}", qualifiedName);
            return new ClassificationResult(qualifiedName,
                    ClassificationResult.ClassificationSource.AUTOMATIC);
        }

        // Apply classifications
        applyClassifications(qualifiedName, matchedClassifications);

        // Build result
        ClassificationResult result = new ClassificationResult(qualifiedName,
                ClassificationResult.ClassificationSource.AUTOMATIC);
        result.setClassifications(new ArrayList<>(matchedClassifications.keySet()));

        LOG.info("Auto-classified object {} with {} classifications: {}",
                qualifiedName, matchedClassifications.size(), matchedClassifications.keySet());

        return result;
    }

    /**
     * Apply automatic classification rules to a MinIO bucket
     *
     * @param bucket The MinIO bucket to classify
     * @return ClassificationResult with applied classifications
     * @throws Exception if classification fails
     */
    public ClassificationResult autoClassify(MinioBucket bucket) throws Exception {
        if (bucket == null) {
            throw new IllegalArgumentException("MinioBucket cannot be null");
        }

        if (!autoClassifyEnabled) {
            LOG.debug("Auto-classification is disabled, skipping bucket {}", bucket.getName());
            return new ClassificationResult(getBucketQualifiedName(bucket),
                    ClassificationResult.ClassificationSource.AUTOMATIC);
        }

        String qualifiedName = getBucketQualifiedName(bucket);
        LOG.debug("Auto-classifying bucket: {}", qualifiedName);

        // Check if entity has manual classifications (manual takes priority)
        if (hasManualClassifications(qualifiedName)) {
            LOG.debug("Bucket {} has manual classifications, skipping auto-classification", qualifiedName);
            return new ClassificationResult(qualifiedName,
                    ClassificationResult.ClassificationSource.AUTOMATIC);
        }

        // Evaluate rules
        Map<String, Map<String, Object>> matchedClassifications = rulesEngine.evaluate(bucket);

        if (matchedClassifications.isEmpty()) {
            LOG.debug("No classification rules matched for bucket {}", qualifiedName);
            return new ClassificationResult(qualifiedName,
                    ClassificationResult.ClassificationSource.AUTOMATIC);
        }

        // Apply classifications
        applyClassifications(qualifiedName, matchedClassifications);

        // Build result
        ClassificationResult result = new ClassificationResult(qualifiedName,
                ClassificationResult.ClassificationSource.AUTOMATIC);
        result.setClassifications(new ArrayList<>(matchedClassifications.keySet()));

        LOG.info("Auto-classified bucket {} with {} classifications: {}",
                qualifiedName, matchedClassifications.size(), matchedClassifications.keySet());

        return result;
    }

    /**
     * Check if entity has manual classifications
     *
     * @param qualifiedName The entity's qualified name
     * @return true if entity has manual classifications
     */
    private boolean hasManualClassifications(String qualifiedName) throws Exception {
        try {
            AtlasEntityWithExtInfo entity = findEntity(qualifiedName);
            if (entity == null || entity.getEntity().getClassifications() == null) {
                return false;
            }

            for (AtlasClassification classification : entity.getEntity().getClassifications()) {
                Map<String, Object> attributes = classification.getAttributes();
                if (attributes != null && "MANUAL".equals(attributes.get("source"))) {
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            LOG.debug("Error checking for manual classifications on entity {}", qualifiedName, e);
            return false;
        }
    }

    /**
     * Apply classifications to an entity in Atlas
     *
     * @param qualifiedName The entity's qualified name
     * @param classifications Map of classification names to their attributes
     * @throws Exception if application fails
     */
    private void applyClassifications(String qualifiedName,
                                      Map<String, Map<String, Object>> classifications) throws Exception {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            try {
                // Find entity by qualified name
                AtlasEntityWithExtInfo entity = findEntity(qualifiedName);
                if (entity == null) {
                    throw new IllegalArgumentException("Entity not found: " + qualifiedName);
                }

                // Apply each classification with attributes
                for (Map.Entry<String, Map<String, Object>> entry : classifications.entrySet()) {
                    String classificationName = entry.getKey();
                    Map<String, Object> attributes = entry.getValue();

                    // Add source attribute to indicate automatic classification
                    Map<String, Object> classificationAttrs = new HashMap<>(attributes);
                    classificationAttrs.put("source", "AUTOMATIC");

                    AtlasClassification classification = new AtlasClassification(classificationName, classificationAttrs);

                    try {
                        atlasClient.addClassifications(entity.getEntity().getGuid(),
                                Collections.singletonList(classification));

                        LOG.debug("Applied automatic classification {} to entity {} with attributes: {}",
                                classificationName, qualifiedName, classificationAttrs);
                    } catch (AtlasServiceException e) {
                        if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                            LOG.debug("Classification {} already exists on entity {}", classificationName, qualifiedName);
                        } else {
                            throw e;
                        }
                    }
                }

                return; // Success

            } catch (AtlasServiceException e) {
                lastException = e;
                attempt++;

                if (attempt < MAX_RETRIES) {
                    LOG.warn("Attempt {}/{} failed to apply classifications to entity {}, retrying...",
                            attempt, MAX_RETRIES, qualifiedName, e);
                    Thread.sleep(RETRY_DELAY_MS);
                } else {
                    LOG.error("Failed to apply classifications after {} attempts", MAX_RETRIES, e);
                }
            }
        }

        throw new Exception("Failed to apply classifications after " + MAX_RETRIES + " attempts", lastException);
    }

    /**
     * Get current classifications for an entity
     *
     * @param qualifiedName The entity's qualified name
     * @return List of classification names
     * @throws Exception if retrieval fails
     */
    public List<String> getClassifications(String qualifiedName) throws Exception {
        AtlasEntityWithExtInfo entity = findEntity(qualifiedName);
        if (entity == null) {
            throw new IllegalArgumentException("Entity not found: " + qualifiedName);
        }

        List<String> classifications = new ArrayList<>();
        if (entity.getEntity().getClassifications() != null) {
            for (AtlasClassification classification : entity.getEntity().getClassifications()) {
                classifications.add(classification.getTypeName());
            }
        }

        LOG.debug("Retrieved classifications for entity {}: {}", qualifiedName, classifications);
        return classifications;
    }

    /**
     * Remove a specific classification from an entity
     *
     * @param qualifiedName The entity's qualified name
     * @param classification The classification to remove
     * @throws Exception if removal fails
     */
    public void removeClassification(String qualifiedName, String classification) throws Exception {
        AtlasEntityWithExtInfo entity = findEntity(qualifiedName);
        if (entity == null) {
            throw new IllegalArgumentException("Entity not found: " + qualifiedName);
        }

        atlasClient.removeClassification(entity.getEntity().getGuid(), classification, null);
        LOG.info("Removed classification {} from entity {}", classification, qualifiedName);
    }

    /**
     * Find entity in Atlas by qualified name
     */
    private AtlasEntityWithExtInfo findEntity(String qualifiedName) throws Exception {
        // Try minio_object first
        try {
            return atlasClient.getEntityByAttribute("minio_object",
                    Collections.singletonMap("qualifiedName", qualifiedName));
        } catch (Exception e) {
            LOG.debug("Entity not found as minio_object: {}", qualifiedName);
        }

        // Try minio_bucket
        try {
            return atlasClient.getEntityByAttribute("minio_bucket",
                    Collections.singletonMap("qualifiedName", qualifiedName));
        } catch (Exception e) {
            LOG.debug("Entity not found as minio_bucket: {}", qualifiedName);
        }

        return null;
    }

    /**
     * Get qualified name for object
     */
    private String getObjectQualifiedName(MinioObject object) {
        return object.getBucketName().toLowerCase() + "/" + object.getPath().toLowerCase() + "@" + clusterName;
    }

    /**
     * Get qualified name for bucket
     */
    private String getBucketQualifiedName(MinioBucket bucket) {
        return bucket.getName().toLowerCase() + "@" + clusterName;
    }

    /**
     * Check if auto-classification is enabled
     */
    public boolean isAutoClassifyEnabled() {
        return autoClassifyEnabled;
    }

    /**
     * Enable or disable auto-classification
     */
    public void setAutoClassifyEnabled(boolean enabled) {
        this.autoClassifyEnabled = enabled;
        LOG.info("Auto-classification {}", enabled ? "enabled" : "disabled");
    }
}
