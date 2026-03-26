package org.apache.atlas.minio.rest;

import org.apache.atlas.minio.bridge.MinIOBridge;
import org.apache.atlas.minio.bridge.SyncScheduler;
import org.apache.atlas.minio.classification.ClassificationService;
import org.apache.atlas.minio.event.EventQueue;
import org.apache.atlas.minio.model.ClassificationResult;
import org.apache.atlas.minio.model.MinioBucket;
import org.apache.atlas.minio.model.MinioEvent;
import org.apache.atlas.minio.model.MinioObject;
import org.apache.atlas.minio.model.SyncReport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MinioResource REST endpoints
 */
@RunWith(MockitoJUnitRunner.class)
public class MinioResourceTest {

    @Mock
    private MinIOBridge minioBridge;

    @Mock
    private SyncScheduler syncScheduler;

    @Mock
    private EventQueue eventQueue;

    @Mock
    private ClassificationService classificationService;

    @InjectMocks
    private MinioResource minioResource;

    @Before
    public void setUp() {
        // Reset all mocks before each test
        reset(minioBridge, syncScheduler, eventQueue, classificationService);
    }

    /**
     * Test 1: Test Connection - Success
     */
    @Test
    public void testTestConnection_Success() {
        // Setup
        when(minioBridge.testConnection()).thenReturn(true);
        when(minioBridge.getEndpoint()).thenReturn("http://localhost:9000");

        // Execute
        Response response = minioResource.testConnection();

        // Verify
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        TestConnectionResponse result = (TestConnectionResponse) response.getEntity();
        assertEquals("connected", result.getStatus());
        assertEquals("http://localhost:9000", result.getEndpoint());

        verify(minioBridge, times(1)).testConnection();
        verify(minioBridge, times(1)).getEndpoint();
    }

    /**
     * Test 2: Test Connection - Failure
     */
    @Test
    public void testTestConnection_Failure() {
        // Setup
        when(minioBridge.testConnection()).thenReturn(false);
        when(minioBridge.getEndpoint()).thenReturn("http://localhost:9000");

        // Execute
        Response response = minioResource.testConnection();

        // Verify
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        TestConnectionResponse result = (TestConnectionResponse) response.getEntity();
        assertEquals("disconnected", result.getStatus());
        assertEquals("http://localhost:9000", result.getEndpoint());
    }

    /**
     * Test 3: Trigger Sync - Full Sync
     */
    @Test
    public void testTriggerSync_FullSync() {
        // Setup
        SyncReport report = new SyncReport(SyncReport.SyncType.FULL);
        report.setStatus(SyncReport.SyncStatus.SUCCESS);
        report.setBucketsProcessed(5);
        report.setObjectsProcessed(100);

        when(syncScheduler.triggerFullSync()).thenReturn(report);

        // Execute
        Response response = minioResource.triggerSync("full");

        // Verify
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        SyncReport result = (SyncReport) response.getEntity();
        assertEquals(SyncReport.SyncType.FULL, result.getSyncType());
        assertEquals(5, result.getBucketsProcessed());

        verify(syncScheduler, times(1)).triggerFullSync();
        verify(syncScheduler, never()).triggerIncrementalSync();
    }

    /**
     * Test 4: Trigger Sync - Incremental Sync
     */
    @Test
    public void testTriggerSync_IncrementalSync() {
        // Setup
        SyncReport report = new SyncReport(SyncReport.SyncType.INCREMENTAL);
        report.setStatus(SyncReport.SyncStatus.SUCCESS);
        report.setObjectsProcessed(10);

        when(syncScheduler.triggerIncrementalSync()).thenReturn(report);

        // Execute
        Response response = minioResource.triggerSync("incremental");

        // Verify
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        SyncReport result = (SyncReport) response.getEntity();
        assertEquals(SyncReport.SyncType.INCREMENTAL, result.getSyncType());

        verify(syncScheduler, times(1)).triggerIncrementalSync();
        verify(syncScheduler, never()).triggerFullSync();
    }

    /**
     * Test 5: Trigger Sync - Invalid Mode
     */
    @Test
    public void testTriggerSync_InvalidMode() {
        // Execute
        Response response = minioResource.triggerSync("invalid");

        // Verify
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

        verify(syncScheduler, never()).triggerFullSync();
        verify(syncScheduler, never()).triggerIncrementalSync();
    }

    /**
     * Test 6: List Buckets
     */
    @Test
    public void testListBuckets() {
        // Setup
        List<MinioBucket> buckets = Arrays.asList(
                createMinioBucket("bucket1"),
                createMinioBucket("bucket2")
        );
        when(minioBridge.listMinioBuckets()).thenReturn(buckets);

        // Execute
        Response response = minioResource.listBuckets();

        // Verify
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<MinioBucket> result = (List<MinioBucket>) response.getEntity();
        assertEquals(2, result.size());
        assertEquals("bucket1", result.get(0).getName());

        verify(minioBridge, times(1)).listMinioBuckets();
    }

    /**
     * Test 7: List Objects
     */
    @Test
    public void testListObjects() {
        // Setup
        List<MinioObject> objects = Arrays.asList(
                createMinioObject("bucket1", "file1.txt"),
                createMinioObject("bucket1", "file2.txt")
        );
        when(minioBridge.listObjects(eq("bucket1"), eq(""), eq(100))).thenReturn(objects);

        // Execute
        Response response = minioResource.listObjects("bucket1", "", 100);

        // Verify
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<MinioObject> result = (List<MinioObject>) response.getEntity();
        assertEquals(2, result.size());
        assertEquals("file1.txt", result.get(0).getPath());

        verify(minioBridge, times(1)).listObjects(eq("bucket1"), eq(""), eq(100));
    }

    /**
     * Test 8: List Objects - Missing Bucket Name
     */
    @Test
    public void testListObjects_MissingBucketName() {
        // Execute
        Response response = minioResource.listObjects("", "", 100);

        // Verify
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

        verify(minioBridge, never()).listObjects(anyString(), anyString(), anyInt());
    }

    /**
     * Test 9: Classify Entity - Success (Bucket)
     */
    @Test
    public void testClassifyEntity_Bucket() throws Exception {
        // Setup
        ClassificationRequest request = new ClassificationRequest("bucket1@cluster", Arrays.asList("PII", "FINANCE"));
        MinioBucket bucket = createMinioBucket("bucket1");
        ClassificationResult result = new ClassificationResult();
        result.setSuccess(true);
        result.setAppliedTags(Arrays.asList("PII", "FINANCE"));

        when(minioBridge.getBucket("bucket1@cluster")).thenReturn(bucket);
        when(classificationService.classifyBucket(eq(bucket), anyList())).thenReturn(result);

        // Execute
        Response response = minioResource.classifyEntity(request);

        // Verify
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        ClassificationResult responseResult = (ClassificationResult) response.getEntity();
        assertTrue(responseResult.isSuccess());
        assertEquals(2, responseResult.getAppliedTags().size());

        verify(minioBridge, times(1)).getBucket("bucket1@cluster");
        verify(classificationService, times(1)).classifyBucket(eq(bucket), anyList());
    }

    /**
     * Test 10: Classify Entity - Success (Object)
     */
    @Test
    public void testClassifyEntity_Object() throws Exception {
        // Setup
        ClassificationRequest request = new ClassificationRequest("bucket1/file1.txt@cluster", Arrays.asList("CONFIDENTIAL"));
        MinioBucket bucket = null; // Not found as bucket
        MinioObject object = createMinioObject("bucket1", "file1.txt");
        ClassificationResult result = new ClassificationResult();
        result.setSuccess(true);
        result.setAppliedTags(Arrays.asList("CONFIDENTIAL"));

        when(minioBridge.getBucket("bucket1/file1.txt@cluster")).thenReturn(bucket);
        when(minioBridge.getObject("bucket1/file1.txt@cluster")).thenReturn(object);
        when(classificationService.classifyObject(eq(object), anyList())).thenReturn(result);

        // Execute
        Response response = minioResource.classifyEntity(request);

        // Verify
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        ClassificationResult responseResult = (ClassificationResult) response.getEntity();
        assertTrue(responseResult.isSuccess());

        verify(minioBridge, times(1)).getBucket("bucket1/file1.txt@cluster");
        verify(minioBridge, times(1)).getObject("bucket1/file1.txt@cluster");
        verify(classificationService, times(1)).classifyObject(eq(object), anyList());
    }

    /**
     * Test 11: Classify Entity - Not Found
     */
    @Test
    public void testClassifyEntity_NotFound() {
        // Setup
        ClassificationRequest request = new ClassificationRequest("notfound@cluster", Arrays.asList("PII"));
        when(minioBridge.getBucket("notfound@cluster")).thenReturn(null);
        when(minioBridge.getObject("notfound@cluster")).thenReturn(null);

        // Execute
        Response response = minioResource.classifyEntity(request);

        // Verify
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        verify(minioBridge, times(1)).getBucket("notfound@cluster");
        verify(minioBridge, times(1)).getObject("notfound@cluster");
    }

    /**
     * Test 12: Receive Event - Success
     */
    @Test
    public void testReceiveEvent_Success() {
        // Setup
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("EventName", "s3:ObjectCreated:Put");
        eventData.put("BucketName", "bucket1");
        eventData.put("Key", "file1.txt");

        when(eventQueue.enqueue(any(MinioEvent.class))).thenReturn(true);

        // Execute
        Response response = minioResource.receiveEvent(eventData);

        // Verify
        assertEquals(Response.Status.ACCEPTED.getStatusCode(), response.getStatus());

        verify(eventQueue, times(1)).enqueue(any(MinioEvent.class));
    }

    /**
     * Test 13: Receive Event - Queue Full
     */
    @Test
    public void testReceiveEvent_QueueFull() {
        // Setup
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("EventName", "s3:ObjectCreated:Put");
        eventData.put("BucketName", "bucket1");
        eventData.put("Key", "file1.txt");

        when(eventQueue.enqueue(any(MinioEvent.class))).thenReturn(false);

        // Execute
        Response response = minioResource.receiveEvent(eventData);

        // Verify
        assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), response.getStatus());

        verify(eventQueue, times(1)).enqueue(any(MinioEvent.class));
    }

    /**
     * Test 14: Get Sync Report - Found
     */
    @Test
    public void testGetSyncReport_Found() {
        // Setup
        SyncReport report = new SyncReport(SyncReport.SyncType.FULL);
        report.setStatus(SyncReport.SyncStatus.SUCCESS);
        report.setBucketsProcessed(10);

        when(syncScheduler.getLatestReport()).thenReturn(report);

        // Execute
        Response response = minioResource.getSyncReport();

        // Verify
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        SyncReport result = (SyncReport) response.getEntity();
        assertEquals(SyncReport.SyncType.FULL, result.getSyncType());
        assertEquals(10, result.getBucketsProcessed());

        verify(syncScheduler, times(1)).getLatestReport();
    }

    /**
     * Test 15: Get Sync Report - Not Found
     */
    @Test
    public void testGetSyncReport_NotFound() {
        // Setup
        when(syncScheduler.getLatestReport()).thenReturn(null);

        // Execute
        Response response = minioResource.getSyncReport();

        // Verify
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        verify(syncScheduler, times(1)).getLatestReport();
    }

    /**
     * Test 16: Get Sync History
     */
    @Test
    public void testGetSyncHistory() {
        // Setup
        List<SyncReport> reports = Arrays.asList(
                new SyncReport(SyncReport.SyncType.FULL),
                new SyncReport(SyncReport.SyncType.INCREMENTAL)
        );
        when(syncScheduler.getReportHistory(10)).thenReturn(reports);

        // Execute
        Response response = minioResource.getSyncHistory(10);

        // Verify
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        List<SyncReport> result = (List<SyncReport>) response.getEntity();
        assertEquals(2, result.size());

        verify(syncScheduler, times(1)).getReportHistory(10);
    }

    /**
     * Test 17: Get Sync History - Invalid Limit
     */
    @Test
    public void testGetSyncHistory_InvalidLimit() {
        // Execute
        Response response = minioResource.getSyncHistory(0);

        // Verify
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

        verify(syncScheduler, never()).getReportHistory(anyInt());
    }

    // Helper methods

    private MinioBucket createMinioBucket(String name) {
        MinioBucket bucket = new MinioBucket();
        bucket.setName(name);
        bucket.setOwner("test-user");
        bucket.setLocation("default");
        return bucket;
    }

    private MinioObject createMinioObject(String bucketName, String path) {
        MinioObject object = new MinioObject();
        object.setBucketName(bucketName);
        object.setPath(path);
        object.setSize(1024L);
        object.setETag("abc123");
        return object;
    }
}
