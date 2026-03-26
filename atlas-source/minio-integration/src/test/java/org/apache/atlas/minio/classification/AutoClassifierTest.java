package org.apache.atlas.minio.classification;

import org.apache.atlas.AtlasClientV2;
import org.apache.atlas.AtlasServiceException;
import org.apache.atlas.minio.model.MinioBucket;
import org.apache.atlas.minio.model.MinioObject;
import org.apache.atlas.minio.model.ClassificationResult;
import org.apache.atlas.model.instance.AtlasClassification;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntityWithExtInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AutoClassifier
 */
@RunWith(MockitoJUnitRunner.class)
public class AutoClassifierTest {

    @Mock
    private AtlasClientV2 atlasClient;

    @Mock
    private ClassificationRules rulesEngine;

    @InjectMocks
    private AutoClassifier autoClassifier;

    private MinioObject testObject;
    private MinioBucket testBucket;

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(autoClassifier, "autoClassifyEnabled", true);
        ReflectionTestUtils.setField(autoClassifier, "clusterName", "primary");

        // Setup test object
        testObject = new MinioObject();
        testObject.setBucketName("data-bucket");
        testObject.setPath("production/data.csv");
        testObject.setSize(1024L);
        testObject.setContentType("text/csv");

        // Setup test bucket
        testBucket = new MinioBucket();
        testBucket.setName("production-bucket");
    }

    @Test
    public void testAutoClassifyObject_Success() throws Exception {
        // Arrange
        String qualifiedName = "data-bucket/production/data.csv@primary";
        Map<String, Map<String, Object>> matchedClassifications = new HashMap<>();
        matchedClassifications.put("PRODUCTION_DATA", Collections.singletonMap("environment", "production"));

        setupMockEntityFind(qualifiedName, "minio_object");
        when(rulesEngine.evaluate(testObject)).thenReturn(matchedClassifications);

        // Act
        ClassificationResult result = autoClassifier.autoClassify(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(ClassificationResult.ClassificationSource.AUTOMATIC, result.getSource());
        assertEquals(1, result.getClassifications().size());
        assertTrue(result.getClassifications().contains("PRODUCTION_DATA"));

        verify(atlasClient).addClassification(anyString(), anySet());
    }

    @Test
    public void testAutoClassifyObject_NoMatchingRules() throws Exception {
        // Arrange
        String qualifiedName = "data-bucket/production/data.csv@primary";
        setupMockEntityFind(qualifiedName, "minio_object");
        when(rulesEngine.evaluate(testObject)).thenReturn(new HashMap<>());

        // Act
        ClassificationResult result = autoClassifier.autoClassify(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(ClassificationResult.ClassificationSource.AUTOMATIC, result.getSource());
        assertFalse(result.hasClassifications());

        verify(atlasClient, never()).addClassification(anyString(), anySet());
    }

    @Test
    public void testAutoClassifyObject_WithManualClassifications() throws Exception {
        // Arrange
        String qualifiedName = "data-bucket/production/data.csv@primary";
        AtlasEntityWithExtInfo entity = createMockEntity(qualifiedName);

        // Add manual classification
        AtlasClassification manualClassification = new AtlasClassification("MANUAL_TAG");
        manualClassification.setAttribute("source", "MANUAL");
        entity.getEntity().setClassifications(Arrays.asList(manualClassification));

        when(atlasClient.getEntityByAttribute(eq("minio_object"), anyMap()))
                .thenReturn(entity);

        // Act
        ClassificationResult result = autoClassifier.autoClassify(testObject);

        // Assert
        assertNotNull(result);
        assertFalse(result.hasClassifications());

        // Verify rules were not evaluated
        verify(rulesEngine, never()).evaluate(any());
    }

    @Test
    public void testAutoClassifyObject_AutoClassifyDisabled() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(autoClassifier, "autoClassifyEnabled", false);

        // Act
        ClassificationResult result = autoClassifier.autoClassify(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(ClassificationResult.ClassificationSource.AUTOMATIC, result.getSource());
        assertFalse(result.hasClassifications());

        // Verify nothing was called
        verify(rulesEngine, never()).evaluate(any());
        verify(atlasClient, never()).addClassification(anyString(), anySet());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAutoClassifyObject_NullObject() throws Exception {
        autoClassifier.autoClassify(null);
    }

    @Test
    public void testAutoClassifyObject_MultipleClassifications() throws Exception {
        // Arrange
        String qualifiedName = "data-bucket/production/data.csv@primary";
        Map<String, Map<String, Object>> matchedClassifications = new HashMap<>();
        matchedClassifications.put("PRODUCTION_DATA", Collections.singletonMap("environment", "production"));
        matchedClassifications.put("SENSITIVE_DATA", Collections.singletonMap("level", "high"));

        setupMockEntityFind(qualifiedName, "minio_object");
        when(rulesEngine.evaluate(testObject)).thenReturn(matchedClassifications);

        // Act
        ClassificationResult result = autoClassifier.autoClassify(testObject);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getClassifications().size());
        assertTrue(result.getClassifications().contains("PRODUCTION_DATA"));
        assertTrue(result.getClassifications().contains("SENSITIVE_DATA"));

        verify(atlasClient, times(2)).addClassification(anyString(), anySet());
    }

    @Test
    public void testAutoClassifyObject_WithRetry() throws Exception {
        // Arrange
        String qualifiedName = "data-bucket/production/data.csv@primary";
        Map<String, Map<String, Object>> matchedClassifications = new HashMap<>();
        matchedClassifications.put("PRODUCTION_DATA", Collections.emptyMap());

        setupMockEntityFind(qualifiedName, "minio_object");
        when(rulesEngine.evaluate(testObject)).thenReturn(matchedClassifications);

        // Fail first two attempts, succeed on third
        when(atlasClient.addClassification(anyString(), anySet()))
                .thenThrow(new AtlasServiceException("Connection error"))
                .thenThrow(new AtlasServiceException("Connection error"))
                .thenReturn(null);

        // Act
        ClassificationResult result = autoClassifier.autoClassify(testObject);

        // Assert
        assertNotNull(result);
        assertTrue(result.hasClassifications());
        verify(atlasClient, times(3)).addClassification(anyString(), anySet());
    }

    @Test
    public void testAutoClassifyBucket_Success() throws Exception {
        // Arrange
        String qualifiedName = "production-bucket@primary";
        Map<String, Map<String, Object>> matchedClassifications = new HashMap<>();
        matchedClassifications.put("PRODUCTION_BUCKET", Collections.singletonMap("environment", "production"));

        setupMockEntityFind(qualifiedName, "minio_bucket");
        when(rulesEngine.evaluate(testBucket)).thenReturn(matchedClassifications);

        // Act
        ClassificationResult result = autoClassifier.autoClassify(testBucket);

        // Assert
        assertNotNull(result);
        assertEquals(ClassificationResult.ClassificationSource.AUTOMATIC, result.getSource());
        assertEquals(1, result.getClassifications().size());
        assertTrue(result.getClassifications().contains("PRODUCTION_BUCKET"));

        verify(atlasClient).addClassification(anyString(), anySet());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAutoClassifyBucket_NullBucket() throws Exception {
        autoClassifier.autoClassify(null);
    }

    @Test
    public void testGetClassifications_Success() throws Exception {
        // Arrange
        String qualifiedName = "data-bucket/test.txt@primary";
        AtlasEntityWithExtInfo entity = createMockEntity(qualifiedName);
        entity.getEntity().setClassifications(Arrays.asList(
                new AtlasClassification("AUTO_TAG"),
                new AtlasClassification("MANUAL_TAG")
        ));

        when(atlasClient.getEntityByAttribute("minio_object",
                Collections.singletonMap("qualifiedName", qualifiedName)))
                .thenReturn(entity);

        // Act
        List<String> classifications = autoClassifier.getClassifications(qualifiedName);

        // Assert
        assertNotNull(classifications);
        assertEquals(2, classifications.size());
        assertTrue(classifications.contains("AUTO_TAG"));
        assertTrue(classifications.contains("MANUAL_TAG"));
    }

    @Test
    public void testRemoveClassification_Success() throws Exception {
        // Arrange
        String qualifiedName = "data-bucket/test.txt@primary";
        String classification = "AUTO_TAG";
        setupMockEntityFind(qualifiedName, "minio_object");

        // Act
        autoClassifier.removeClassification(qualifiedName, classification);

        // Assert
        verify(atlasClient).removeClassification(anyString(), eq(classification));
    }

    @Test
    public void testSetAutoClassifyEnabled() {
        // Act
        autoClassifier.setAutoClassifyEnabled(false);

        // Assert
        assertFalse(autoClassifier.isAutoClassifyEnabled());

        autoClassifier.setAutoClassifyEnabled(true);
        assertTrue(autoClassifier.isAutoClassifyEnabled());
    }

    @Test
    public void testAutoClassifyObject_EntityNotFound() throws Exception {
        // Arrange
        String qualifiedName = "data-bucket/test.txt@primary";
        when(atlasClient.getEntityByAttribute(eq("minio_object"), anyMap()))
                .thenThrow(new AtlasServiceException("Entity not found"));

        // Act & Assert
        try {
            autoClassifier.autoClassify(testObject);
            fail("Should have thrown exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Entity not found"));
        }
    }

    @Test
    public void testAutoClassifyObject_ClassificationAlreadyExists() throws Exception {
        // Arrange
        String qualifiedName = "data-bucket/test.txt@primary";
        Map<String, Map<String, Object>> matchedClassifications = new HashMap<>();
        matchedClassifications.put("PRODUCTION_DATA", Collections.emptyMap());

        setupMockEntityFind(qualifiedName, "minio_object");
        when(rulesEngine.evaluate(testObject)).thenReturn(matchedClassifications);

        // Classification already exists
        when(atlasClient.addClassification(anyString(), anySet()))
                .thenThrow(new AtlasServiceException("Classification already exists"));

        // Act
        ClassificationResult result = autoClassifier.autoClassify(testObject);

        // Assert - should still succeed, just log the duplicate
        assertNotNull(result);
        assertTrue(result.hasClassifications());
    }

    // Helper methods

    private void setupMockEntityFind(String qualifiedName, String entityType) throws Exception {
        AtlasEntityWithExtInfo entity = createMockEntity(qualifiedName);
        when(atlasClient.getEntityByAttribute(eq(entityType), anyMap()))
                .thenReturn(entity);
    }

    private AtlasEntityWithExtInfo createMockEntity(String qualifiedName) {
        AtlasEntity entity = new AtlasEntity();
        entity.setGuid("test-guid");
        entity.setAttribute("qualifiedName", qualifiedName);
        entity.setClassifications(new ArrayList<>());

        AtlasEntityWithExtInfo entityWithInfo = new AtlasEntityWithExtInfo();
        entityWithInfo.setEntity(entity);

        return entityWithInfo;
    }
}
