package org.apache.atlas.minio.classification;

import org.apache.atlas.AtlasClientV2;
import org.apache.atlas.AtlasServiceException;
import org.apache.atlas.minio.model.MinioBucket;
import org.apache.atlas.minio.model.MinioObject;
import org.apache.atlas.minio.model.ClassificationResult;
import org.apache.atlas.model.instance.AtlasClassification;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntityWithExtInfo;
import org.apache.atlas.model.typedef.AtlasClassificationDef;
import org.apache.atlas.model.typedef.AtlasTypesDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Handle manual classification requests from users
 * Validates tags exist in Atlas and applies them to entities
 * Manual classifications take priority over automatic ones
 */
@Component
public class ManualClassification {
    private static final Logger LOG = LoggerFactory.getLogger(ManualClassification.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final AtlasClientV2 atlasClient;
    private final ClassificationRules rulesEngine;

    @Value("${atlas.minio.classification.auto.enabled:true}")
    private boolean autoClassifyEnabled;

    @Autowired
    public ManualClassification(AtlasClientV2 atlasClient, ClassificationRules rulesEngine) {
        this.atlasClient = atlasClient;
        this.rulesEngine = rulesEngine;
    }

    /**
     * Manually classify a MinIO object with specified tags
     *
     * @param object The MinIO object to classify
     * @param tags List of classification tags to apply
     * @return ClassificationResult with applied classifications
     * @throws Exception if classification fails
     */
    public ClassificationResult classifyObject(MinioObject object, List<String> tags) throws Exception {
        if (object == null) {
            throw new IllegalArgumentException("MinioObject cannot be null");
        }

        if (tags == null || tags.isEmpty()) {
            throw new IllegalArgumentException("Classification tags cannot be null or empty");
        }

        String qualifiedName = getObjectQualifiedName(object);
        LOG.info("Manual classification for object {}: tags={}", qualifiedName, tags);

        // Validate classification tags exist in Atlas
        validateClassifications(tags);

        // Remove any existing automatic classifications
        if (autoClassifyEnabled) {
            removeAutomaticClassifications(qualifiedName);
        }

        // Apply manual classifications
        applyClassifications(qualifiedName, tags);

        // Build result
        ClassificationResult result = new ClassificationResult(qualifiedName,
                ClassificationResult.ClassificationSource.MANUAL);
        result.setClassifications(new ArrayList<>(tags));

        LOG.info("Successfully applied manual classifications to object {}: {}", qualifiedName, tags);
        return result;
    }

    /**
     * Manually classify a MinIO bucket with specified tags
     *
     * @param bucket The MinIO bucket to classify
     * @param tags List of classification tags to apply
     * @return ClassificationResult with applied classifications
     * @throws Exception if classification fails
     */
    public ClassificationResult classifyBucket(MinioBucket bucket, List<String> tags) throws Exception {
        if (bucket == null) {
            throw new IllegalArgumentException("MinioBucket cannot be null");
        }

        if (tags == null || tags.isEmpty()) {
            throw new IllegalArgumentException("Classification tags cannot be null or empty");
        }

        String qualifiedName = getBucketQualifiedName(bucket);
        LOG.info("Manual classification for bucket {}: tags={}", qualifiedName, tags);

        // Validate classification tags exist in Atlas
        validateClassifications(tags);

        // Remove any existing automatic classifications
        if (autoClassifyEnabled) {
            removeAutomaticClassifications(qualifiedName);
        }

        // Apply manual classifications
        applyClassifications(qualifiedName, tags);

        // Build result
        ClassificationResult result = new ClassificationResult(qualifiedName,
                ClassificationResult.ClassificationSource.MANUAL);
        result.setClassifications(new ArrayList<>(tags));

        LOG.info("Successfully applied manual classifications to bucket {}: {}", qualifiedName, tags);
        return result;
    }

    /**
     * Validate that classification tags exist in Atlas
     *
     * @param tags List of classification names to validate
     * @throws Exception if any classification doesn't exist
     */
    private void validateClassifications(List<String> tags) throws Exception {
        AtlasTypesDef typesDef = atlasClient.getAllTypeDefs();
        Map<String, AtlasClassificationDef> classificationDefs = typesDef.getClassificationDefsAsMap();

        List<String> invalidTags = new ArrayList<>();
        for (String tag : tags) {
            if (!classificationDefs.containsKey(tag)) {
                invalidTags.add(tag);
            }
        }

        if (!invalidTags.isEmpty()) {
            String errorMsg = String.format("Invalid classification tags: %s. " +
                    "These classifications do not exist in Atlas.", invalidTags);
            LOG.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        LOG.debug("All classification tags are valid: {}", tags);
    }

    /**
     * Apply classifications to an entity in Atlas
     *
     * @param qualifiedName The entity's qualified name
     * @param tags List of classification names to apply
     * @throws Exception if application fails
     */
    private void applyClassifications(String qualifiedName, List<String> tags) throws Exception {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            try {
                // Find entity by qualified name
                AtlasEntityWithExtInfo entity = findEntity(qualifiedName);
                if (entity == null) {
                    throw new IllegalArgumentException("Entity not found: " + qualifiedName);
                }

                // Apply each classification
                for (String tag : tags) {
                    AtlasClassification classification = new AtlasClassification(tag);
                    atlasClient.addClassification(entity.getEntity().getGuid(),
                            Collections.singleton(classification));

                    LOG.debug("Applied classification {} to entity {}", tag, qualifiedName);
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
     * Remove automatic classifications from an entity
     *
     * @param qualifiedName The entity's qualified name
     */
    private void removeAutomaticClassifications(String qualifiedName) throws Exception {
        try {
            AtlasEntityWithExtInfo entity = findEntity(qualifiedName);
            if (entity == null) {
                return;
            }

            List<String> classificationsToRemove = new ArrayList<>();
            if (entity.getEntity().getClassifications() != null) {
                for (AtlasClassification classification : entity.getEntity().getClassifications()) {
                    // Check if this was an automatic classification
                    Map<String, Object> attributes = classification.getAttributes();
                    if (attributes != null && "AUTOMATIC".equals(attributes.get("source"))) {
                        classificationsToRemove.add(classification.getTypeName());
                    }
                }
            }

            // Remove automatic classifications
            for (String classification : classificationsToRemove) {
                try {
                    atlasClient.removeClassification(entity.getEntity().getGuid(), classification);
                    LOG.debug("Removed automatic classification {} from entity {}", classification, qualifiedName);
                } catch (Exception e) {
                    LOG.warn("Failed to remove automatic classification {} from entity {}",
                            classification, qualifiedName, e);
                }
            }

        } catch (Exception e) {
            LOG.warn("Failed to remove automatic classifications from entity {}", qualifiedName, e);
        }
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

        atlasClient.removeClassification(entity.getEntity().getGuid(), classification);
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
        return object.getBucketName().toLowerCase() + "/" + object.getPath().toLowerCase();
    }

    /**
     * Get qualified name for bucket
     */
    private String getBucketQualifiedName(MinioBucket bucket) {
        return bucket.getName().toLowerCase() + "@cluster";
    }
}
