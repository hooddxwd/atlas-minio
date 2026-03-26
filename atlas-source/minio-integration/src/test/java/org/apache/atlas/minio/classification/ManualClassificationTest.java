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
import org.apache.atlas.model.typedef.AtlasStructDef.AtlasAttributeDef;
import org.apache.atlas.model.typedef.AtlasTypesDef;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ManualClassification
 */
@RunWith(MockitoJUnitRunner.class)
public class ManualClassificationTest {

    @Mock
    private AtlasClientV2 atlasClient;

    @Mock
    private ClassificationRules rulesEngine;

    @InjectMocks
    private ManualClassification manualClassification;

    private MinioObject testObject;
    private MinioBucket testBucket;

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(manualClassification, "autoClassifyEnabled", true);

        // Setup test object
        testObject = new MinioObject();
        testObject.setBucketName("test-bucket");
        testObject.setPath("test-object.txt");
        testObject.setSize(1024L);
        testObject.setContentType("text/plain");

        // Setup test bucket
        testBucket = new MinioBucket();
        testBucket.setName("test-bucket");
    }

    @Test
    public void testClassifyObject_Success() throws Exception {
        // Arrange
        List<String> tags = Arrays.asList("PII", "CONFIDENTIAL");
        String qualifiedName = "test-bucket/test-object.txt";

        setupMockClassificationDefs(tags);
        setupMockEntityFind(qualifiedName, "minio_object");

        // Act
        ClassificationResult result = manualClassification.classifyObject(testObject, tags);

        // Assert
        assertNotNull(result);
        assertEquals(ClassificationResult.ClassificationSource.MANUAL, result.getSource());
        assertEquals(tags, result.getClassifications());
        assertEquals(qualifiedName, result.getQualifiedName());

        // Verify classifications were applied
        verify(atlasClient, times(2)).addClassification(anyString(), anySet());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClassifyObject_NullObject() throws Exception {
        manualClassification.classifyObject(null, Arrays.asList("PII"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClassifyObject_NullTags() throws Exception {
        manualClassification.classifyObject(testObject, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClassifyObject_EmptyTags() throws Exception {
        manualClassification.classifyObject(testObject, new ArrayList<>());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClassifyObject_InvalidTags() throws Exception {
        // Arrange
        List<String> tags = Arrays.asList("INVALID_TAG");
        setupMockClassificationDefs(new ArrayList<>());

        // Act
        manualClassification.classifyObject(testObject, tags);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClassifyObject_EntityNotFound() throws Exception {
        // Arrange
        List<String> tags = Arrays.asList("PII");
        setupMockClassificationDefs(tags);
        when(atlasClient.getEntityByAttribute(anyString(), anyMap()))
                .thenThrow(new AtlasServiceException("Entity not found"));

        // Act
        manualClassification.classifyObject(testObject, tags);
    }

    @Test
    public void testClassifyBucket_Success() throws Exception {
        // Arrange
        List<String> tags = Arrays.asList("FINANCE_DATA");
        String qualifiedName = "test-bucket@cluster";

        setupMockClassificationDefs(tags);
        setupMockEntityFind(qualifiedName, "minio_bucket");

        // Act
        ClassificationResult result = manualClassification.classifyBucket(testBucket, tags);

        // Assert
        assertNotNull(result);
        assertEquals(ClassificationResult.ClassificationSource.MANUAL, result.getSource());
        assertEquals(tags, result.getClassifications());
        assertEquals(qualifiedName, result.getQualifiedName());

        // Verify classifications were applied
        verify(atlasClient, times(1)).addClassification(anyString(), anySet());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClassifyBucket_NullBucket() throws Exception {
        manualClassification.classifyBucket(null, Arrays.asList("PII"));
    }

    @Test
    public void testGetClassifications_Success() throws Exception {
        // Arrange
        String qualifiedName = "test-bucket/test-object.txt";
        AtlasEntityWithExtInfo entity = createMockEntity(qualifiedName);
        entity.getEntity().setClassifications(Arrays.asList(
                new AtlasClassification("PII"),
                new AtlasClassification("CONFIDENTIAL")
        ));

        when(atlasClient.getEntityByAttribute("minio_object",
                Collections.singletonMap("qualifiedName", qualifiedName)))
                .thenReturn(entity);

        // Act
        List<String> classifications = manualClassification.getClassifications(qualifiedName);

        // Assert
        assertNotNull(classifications);
        assertEquals(2, classifications.size());
        assertTrue(classifications.contains("PII"));
        assertTrue(classifications.contains("CONFIDENTIAL"));
    }

    @Test
    public void testRemoveClassification_Success() throws Exception {
        // Arrange
        String qualifiedName = "test-bucket/test-object.txt";
        String classification = "PII";
        setupMockEntityFind(qualifiedName, "minio_object");

        // Act
        manualClassification.removeClassification(qualifiedName, classification);

        // Assert
        verify(atlasClient).removeClassification(anyString(), eq(classification));
    }

    @Test
    public void testClassifyObject_WithRetry() throws Exception {
        // Arrange
        List<String> tags = Arrays.asList("PII");
        String qualifiedName = "test-bucket/test-object.txt";

        setupMockClassificationDefs(tags);

        AtlasEntityWithExtInfo entity = createMockEntity(qualifiedName);
        when(atlasClient.getEntityByAttribute(eq("minio_object"), anyMap()))
                .thenReturn(entity);

        // Fail first two attempts, succeed on third
        when(atlasClient.addClassification(anyString(), anySet()))
                .thenThrow(new AtlasServiceException("Connection error"))
                .thenThrow(new AtlasServiceException("Connection error"))
                .thenReturn(null);

        // Act
        ClassificationResult result = manualClassification.classifyObject(testObject, tags);

        // Assert
        assertNotNull(result);
        assertEquals(tags, result.getClassifications());
        verify(atlasClient, times(3)).addClassification(anyString(), anySet());
    }

    @Test
    public void testClassifyObject_RemovesAutomaticClassifications() throws Exception {
        // Arrange
        List<String> tags = Arrays.asList("MANUAL_TAG");
        String qualifiedName = "test-bucket/test-object.txt";
        String entityGuid = "test-guid";

        setupMockClassificationDefs(tags);

        AtlasEntityWithExtInfo entity = createMockEntity(qualifiedName);
        entity.getEntity().setGuid(entityGuid);

        // Add automatic classification
        AtlasClassification autoClassification = new AtlasClassification("AUTO_TAG");
        autoClassification.setAttribute("source", "AUTOMATIC");
        entity.getEntity().setClassifications(Arrays.asList(autoClassification));

        when(atlasClient.getEntityByAttribute(eq("minio_object"), anyMap()))
                .thenReturn(entity);
        when(atlasClient.removeClassification(entityGuid, "AUTO_TAG"))
                .thenReturn(null);
        when(atlasClient.addClassification(anyString(), anySet()))
                .thenReturn(null);

        // Act
        ClassificationResult result = manualClassification.classifyObject(testObject, tags);

        // Assert
        assertNotNull(result);
        assertEquals(tags, result.getClassifications());

        // Verify automatic classification was removed
        verify(atlasClient).removeClassification(entityGuid, "AUTO_TAG");
    }

    // Helper methods

    private void setupMockClassificationDefs(List<String> tags) throws Exception {
        AtlasTypesDef typesDef = new AtlasTypesDef();
        Map<String, AtlasClassificationDef> classificationDefs = new HashMap<>();

        for (String tag : tags) {
            AtlasClassificationDef def = new AtlasClassificationDef(tag);
            classificationDefs.put(tag, def);
        }

        typesDef.setClassificationDefs(new ArrayList<>(classificationDefs.values()));
        when(atlasClient.getAllTypeDefs()).thenReturn(typesDef);
    }

    private void setupMockEntityFind(String qualifiedName, String entityType) throws Exception {
        AtlasEntityWithExtInfo entity = createMockEntity(qualifiedName);
        when(atlasClient.getEntityByAttribute(eq(entityType), anyMap()))
                .thenReturn(entity);
        when(atlasClient.addClassification(anyString(), anySet()))
                .thenReturn(null);
    }

    private AtlasEntityWithExtInfo createMockEntity(String qualifiedName) {
        AtlasEntity entity = new AtlasEntity();
        entity.setGuid("test-guid");
        entity.setAttribute("qualifiedName", qualifiedName);

        AtlasEntityWithExtInfo entityWithInfo = new AtlasEntityWithExtInfo();
        entityWithInfo.setEntity(entity);

        return entityWithInfo;
    }
}
