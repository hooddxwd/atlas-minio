package org.apache.atlas.minio.classification;

import org.apache.atlas.minio.model.MinioBucket;
import org.apache.atlas.minio.model.MinioObject;
import org.apache.atlas.minio.model.ClassificationResult;

import java.util.List;

/**
 * Service interface for classification operations
 * Provides manual and automatic classification capabilities for MinIO entities
 */
public interface ClassificationService {

    /**
     * Manually classify a MinIO object with specified tags
     *
     * @param object The MinIO object to classify
     * @param tags List of classification tags to apply
     * @return ClassificationResult with applied classifications
     * @throws Exception if classification fails
     */
    ClassificationResult classifyObject(MinioObject object, List<String> tags) throws Exception;

    /**
     * Manually classify a MinIO bucket with specified tags
     *
     * @param bucket The MinIO bucket to classify
     * @param tags List of classification tags to apply
     * @return ClassificationResult with applied classifications
     * @throws Exception if classification fails
     */
    ClassificationResult classifyBucket(MinioBucket bucket, List<String> tags) throws Exception;

    /**
     * Apply automatic classification rules to a MinIO object
     *
     * @param object The MinIO object to classify
     * @return ClassificationResult with applied classifications
     * @throws Exception if classification fails
     */
    ClassificationResult autoClassify(MinioObject object) throws Exception;

    /**
     * Apply automatic classification rules to a MinIO bucket
     *
     * @param bucket The MinIO bucket to classify
     * @return ClassificationResult with applied classifications
     * @throws Exception if classification fails
     */
    ClassificationResult autoClassify(MinioBucket bucket) throws Exception;

    /**
     * Get current classifications for an entity
     *
     * @param qualifiedName The qualified name of the entity
     * @return List of current classification names
     * @throws Exception if retrieval fails
     */
    List<String> getClassifications(String qualifiedName) throws Exception;

    /**
     * Remove a specific classification from an entity
     *
     * @param qualifiedName The qualified name of the entity
     * @param classification The classification to remove
     * @throws Exception if removal fails
     */
    void removeClassification(String qualifiedName, String classification) throws Exception;
}
