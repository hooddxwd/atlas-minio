package org.apache.atlas.minio.bridge;

import org.apache.atlas.AtlasClientV2;
import org.apache.atlas.AtlasServiceException;
import org.apache.atlas.minio.model.MinioBucket;
import org.apache.atlas.minio.model.MinioObject;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntity.AtlasEntityWithExtInfo;
import org.apache.atlas.model.instance.AtlasObjectId;
import org.apache.atlas.model.instance.EntityMutationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MinIOBridge
 */
@ExtendWith(MockitoExtension.class)
class MinIOBridgeTest {

    @Mock
    private AtlasClientV2 mockAtlasClient;

    @Mock
    private MinIOClient mockMinioClient;

    private MinIOBridge minioBridge;
    private MetadataExtractor metadataExtractor;

    @BeforeEach
    void setUp() throws Exception {
        // Create bridge with mocked dependencies using reflection
        minioBridge = new MinIOBridge(
                mockAtlasClient,
                "http://localhost:9000",
                "minioadmin",
                "minioadmin",
                "us-east-1"
        );

        // Replace the MinIOClient and MetadataExtractor with mocks
        ReflectionTestUtils.setField(minioBridge, "minioClient", mockMinioClient);
        metadataExtractor = new MetadataExtractor(mockMinioClient);
        ReflectionTestUtils.setField(minioBridge, "metadataExtractor", metadataExtractor);

        // Set default configuration values
        ReflectionTestUtils.setField(minioBridge, "clusterName", "primary");
        ReflectionTestUtils.setField(minioBridge, "batchSize", 100);
    }

    @Test
    void testCreateBucketEntity() {
        // Arrange
        MinioBucket bucket = createTestBucket("test-bucket");

        // Act
        AtlasEntity entity = minioBridge.createBucketEntity(bucket);

        // Assert
        assertNotNull(entity);
        assertEquals("minio_bucket", entity.getTypeName());
        assertEquals("test-bucket", entity.getAttribute("name"));
        assertEquals("primary", entity.getAttribute("clusterName"));
        assertEquals("test-owner", entity.getAttribute("owner"));
        assertEquals("test-location", entity.getAttribute("location"));
        assertNotNull(entity.getAttribute("qualifiedName"));
    }

    @Test
    void testCreateObjectEntity() {
        // Arrange
        MinioObject object = createTestObject("test-bucket", "test/path/object.txt");

        // Act
        AtlasEntity entity = minioBridge.createObjectEntity(object);

        // Assert
        assertNotNull(entity);
        assertEquals("minio_object", entity.getTypeName());
        assertEquals("test/path/object.txt", entity.getAttribute("path"));
        assertEquals("test-bucket", entity.getAttribute("bucketName"));
        assertEquals("primary", entity.getAttribute("clusterName"));
        assertEquals(1024L, entity.getAttribute("size"));
        assertEquals("test-etag", entity.getAttribute("etag"));
        assertEquals("text/plain", entity.getAttribute("contentType"));
        assertNotNull(entity.getAttribute("qualifiedName"));
    }

    @Test
    void testSyncBucket_New() throws Exception {
        // Arrange
        MinioBucket bucket = createTestBucket("new-bucket");
        when(mockAtlasClient.getEntityByAttribute(eq("minio_bucket"), any()))
                .thenThrow(new AtlasServiceException("not found", 404));

        EntityMutationResponse mockResponse = mock(EntityMutationResponse.class);
        when(mockAtlasClient.createEntity(any(AtlasEntityWithExtInfo.class)))
                .thenReturn(mockResponse);

        // Act
        minioBridge.syncBucket(bucket);

        // Assert
        ArgumentCaptor<AtlasEntityWithExtInfo> captor = ArgumentCaptor.forClass(AtlasEntityWithExtInfo.class);
        verify(mockAtlasClient).createEntity(captor.capture());

        AtlasEntity capturedEntity = captor.getValue().getEntity();
        assertEquals("new-bucket", capturedEntity.getAttribute("name"));
        assertEquals("minio_bucket", capturedEntity.getTypeName());
    }

    @Test
    void testSyncBucket_Existing() throws Exception {
        // Arrange
        MinioBucket bucket = createTestBucket("existing-bucket");

        AtlasEntityWithExtInfo existingEntity = new AtlasEntityWithExtInfo(
                new AtlasEntity("minio_bucket", "qualifiedName", "existing-bucket@primary")
        );

        when(mockAtlasClient.getEntityByAttribute(eq("minio_bucket"), any()))
                .thenReturn(existingEntity);

        EntityMutationResponse mockResponse = mock(EntityMutationResponse.class);
        when(mockAtlasClient.updateEntity(any(AtlasEntityWithExtInfo.class)))
                .thenReturn(mockResponse);

        // Act
        minioBridge.syncBucket(bucket);

        // Assert
        ArgumentCaptor<AtlasEntityWithExtInfo> captor = ArgumentCaptor.forClass(AtlasEntityWithExtInfo.class);
        verify(mockAtlasClient).updateEntity(captor.capture());

        AtlasEntity capturedEntity = captor.getValue().getEntity();
        assertEquals("existing-bucket", capturedEntity.getAttribute("name"));
    }

    @Test
    void testSyncObject_New() throws Exception {
        // Arrange
        MinioObject object = createTestObject("test-bucket", "new-object.txt");

        when(mockAtlasClient.getEntityByAttribute(eq("minio_object"), any()))
                .thenThrow(new AtlasServiceException("not found", 404));

        EntityMutationResponse mockResponse = mock(EntityMutationResponse.class);
        when(mockAtlasClient.createEntity(any(AtlasEntityWithExtInfo.class)))
                .thenReturn(mockResponse);

        // Act
        minioBridge.syncObject(object);

        // Assert
        ArgumentCaptor<AtlasEntityWithExtInfo> captor = ArgumentCaptor.forClass(AtlasEntityWithExtInfo.class);
        verify(mockAtlasClient).createEntity(captor.capture());

        AtlasEntity capturedEntity = captor.getValue().getEntity();
        assertEquals("new-object.txt", capturedEntity.getAttribute("path"));
        assertEquals("minio_object", capturedEntity.getTypeName());
    }

    @Test
    void testPerformFullSync() throws Exception {
        // Arrange
        List<MinioBucket> buckets = new ArrayList<>();
        buckets.add(createTestBucket("bucket1"));
        buckets.add(createTestBucket("bucket2"));

        List<MinioObject> objects1 = new ArrayList<>();
        objects1.add(createTestObject("bucket1", "obj1.txt"));
        objects1.add(createTestObject("bucket1", "obj2.txt"));

        List<MinioObject> objects2 = new ArrayList<>();
        objects2.add(createTestObject("bucket2", "obj3.txt"));

        when(mockMinioClient.listBuckets()).thenReturn(buckets);
        when(mockMinioClient.listObjects(eq("bucket1"))).thenReturn(objects1);
        when(mockMinioClient.listObjects(eq("bucket2"))).thenReturn(objects2);

        when(mockAtlasClient.getEntityByAttribute(any(), any()))
                .thenThrow(new AtlasServiceException("not found", 404));

        EntityMutationResponse mockResponse = mock(EntityMutationResponse.class);
        when(mockAtlasClient.createEntity(any(AtlasEntityWithExtInfo.class)))
                .thenReturn(mockResponse);

        // Act
        MinIOBridge.SyncStats stats = minioBridge.performFullSync();

        // Assert
        assertTrue(stats.completed);
        assertEquals(2, stats.bucketsProcessed.get());
        assertEquals(3, stats.objectsProcessed.get());
        assertEquals(0, stats.bucketsFailed.get());
        assertEquals(0, stats.objectsFailed.get());
        assertNotNull(minioBridge.getLastSyncTimestamp());
    }

    @Test
    void testPerformFullSync_WithFailures() throws Exception {
        // Arrange
        List<MinioBucket> buckets = new ArrayList<>();
        buckets.add(createTestBucket("bucket1"));
        buckets.add(createTestBucket("bucket2"));

        List<MinioObject> objects1 = new ArrayList<>();
        objects1.add(createTestObject("bucket1", "obj1.txt"));

        when(mockMinioClient.listBuckets()).thenReturn(buckets);
        when(mockMinioClient.listObjects(eq("bucket1"))).thenReturn(objects1);
        when(mockMinioClient.listObjects(eq("bucket2")))
                .thenThrow(new RuntimeException("Connection error"));

        when(mockAtlasClient.getEntityByAttribute(any(), any()))
                .thenThrow(new AtlasServiceException("not found", 404));

        EntityMutationResponse mockResponse = mock(EntityMutationResponse.class);
        when(mockAtlasClient.createEntity(any(AtlasEntityWithExtInfo.class)))
                .thenReturn(mockResponse);

        // Act
        MinIOBridge.SyncStats stats = minioBridge.performFullSync();

        // Assert
        assertFalse(stats.completed);
        assertEquals(1, stats.bucketsProcessed.get());
        assertEquals(1, stats.bucketsFailed.get());
    }

    @Test
    void testPerformIncrementalSync_NoPreviousSync() throws Exception {
        // Arrange - no previous sync timestamp
        List<MinioBucket> buckets = new ArrayList<>();
        buckets.add(createTestBucket("bucket1"));

        when(mockMinioClient.listBuckets()).thenReturn(buckets);
        when(mockMinioClient.listObjects(eq("bucket1"))).thenReturn(Collections.emptyList());

        when(mockAtlasClient.getEntityByAttribute(any(), any()))
                .thenThrow(new AtlasServiceException("not found", 404));

        EntityMutationResponse mockResponse = mock(EntityMutationResponse.class);
        when(mockAtlasClient.createEntity(any(AtlasEntityWithExtInfo.class)))
                .thenReturn(mockResponse);

        // Act
        MinIOBridge.SyncStats stats = minioBridge.performIncrementalSync();

        // Assert - should fall back to full sync
        assertTrue(stats.completed);
        assertEquals(1, stats.bucketsProcessed.get());
    }

    @Test
    void testGetBucketQualifiedName() {
        assertEquals("test-bucket@primary", minioBridge.getBucketQualifiedName("Test-Bucket"));
    }

    @Test
    void testGetObjectQualifiedName() {
        assertEquals("test-bucket/path/to/object.txt@primary",
                minioBridge.getObjectQualifiedName("Test-Bucket", "path/to/Object.txt"));
    }

    @Test
    void testImportBucketToAtlas() throws Exception {
        // Arrange
        MinioBucket bucket = createTestBucket("import-bucket");
        when(mockAtlasClient.getEntityByAttribute(eq("minio_bucket"), any()))
                .thenThrow(new AtlasServiceException("not found", 404));

        EntityMutationResponse mockResponse = mock(EntityMutationResponse.class);
        when(mockAtlasClient.createEntity(any(AtlasEntityWithExtInfo.class)))
                .thenReturn(mockResponse);

        // Act
        minioBridge.importBucketToAtlas(bucket);

        // Assert
        ArgumentCaptor<AtlasEntityWithExtInfo> captor = ArgumentCaptor.forClass(AtlasEntityWithExtInfo.class);
        verify(mockAtlasClient).createEntity(captor.capture());

        AtlasEntity capturedEntity = captor.getValue().getEntity();
        assertEquals("import-bucket", capturedEntity.getAttribute("name"));
        assertEquals("minio_bucket", capturedEntity.getTypeName());
    }

    @Test
    void testImportObjectToAtlas() throws Exception {
        // Arrange
        MinioObject object = createTestObject("test-bucket", "import-object.txt");

        when(mockAtlasClient.getEntityByAttribute(eq("minio_object"), any()))
                .thenThrow(new AtlasServiceException("not found", 404));

        EntityMutationResponse mockResponse = mock(EntityMutationResponse.class);
        when(mockAtlasClient.createEntity(any(AtlasEntityWithExtInfo.class)))
                .thenReturn(mockResponse);

        // Act
        minioBridge.importObjectToAtlas(object);

        // Assert
        ArgumentCaptor<AtlasEntityWithExtInfo> captor = ArgumentCaptor.forClass(AtlasEntityWithExtInfo.class);
        verify(mockAtlasClient).createEntity(captor.capture());

        AtlasEntity capturedEntity = captor.getValue().getEntity();
        assertEquals("import-object.txt", capturedEntity.getAttribute("path"));
        assertEquals("minio_object", capturedEntity.getTypeName());
    }

    @Test
    void testInitialize() throws Exception {
        // Arrange
        when(mockMinioClient.testConnection()).thenReturn(true);

        // Act
        minioBridge.initialize();

        // Assert
        verify(mockMinioClient).testConnection();
    }

    @Test
    void testShutdown() throws Exception {
        // Act
        minioBridge.shutdown();

        // Assert
        verify(mockMinioClient).shutdown();
    }

    // Helper methods

    private MinioBucket createTestBucket(String name) {
        MinioBucket bucket = new MinioBucket();
        bucket.setName(name);
        bucket.setCreationDate(new Date());
        bucket.setLocation("test-location");
        bucket.setOwner("test-owner");
        bucket.setQuota(1073741824L);
        bucket.addAttribute("s3_name", name);
        return bucket;
    }

    private MinioObject createTestObject(String bucketName, String path) {
        MinioObject object = new MinioObject();
        object.setBucketName(bucketName);
        object.setPath(path);
        object.setSize(1024L);
        object.setETag("test-etag");
        object.setLastModified(new Date());
        object.setContentType("text/plain");
        object.setStorageClass("STANDARD");
        object.setVersionId("v1");
        object.addUserMetadata("key1", "value1");
        object.addAttribute("attr1", "attrValue1");
        return object;
    }
}
