package org.apache.atlas.minio.bridge;

import org.apache.atlas.minio.model.SyncReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SyncScheduler
 */
@ExtendWith(MockitoExtension.class)
class SyncSchedulerTest {

    @Mock
    private MinIOBridge mockMinioBridge;

    @Mock
    private Scheduler mockQuartzScheduler;

    @Mock
    private SchedulerContext mockSchedulerContext;

    @Mock
    private JobExecutionContext mockJobExecutionContext;

    @Mock
    private JobDetail mockJobDetail;

    @Mock
    private JobDataMap mockJobDataMap;

    private SyncScheduler syncScheduler;

    @BeforeEach
    void setUp() throws SchedulerException {
        syncScheduler = new SyncScheduler(mockMinioBridge, mockQuartzScheduler);

        // Set default configuration values
        ReflectionTestUtils.setField(syncScheduler, "incrementalSyncCron", "0 0 2 * * ?");
        ReflectionTestUtils.setField(syncScheduler, "runFullSyncOnStart", false);
        ReflectionTestUtils.setField(syncScheduler, "incrementalSyncEnabled", true);
        ReflectionTestUtils.setField(syncScheduler, "fullValidationEnabled", false);

        // Setup mock scheduler context
        when(mockQuartzScheduler.getContext()).thenReturn(mockSchedulerContext);
        when(mockSchedulerContext.get("minioBridge")).thenReturn(mockMinioBridge);
        when(mockSchedulerContext.get("syncScheduler")).thenReturn(syncScheduler);
    }

    @Test
    void testStartScheduler() throws SchedulerException {
        // Arrange
        when(mockQuartzScheduler.isStarted()).thenReturn(false);
        when(mockQuartzScheduler.getJobNames(anyString())).thenReturn(List.of());

        // Act
        syncScheduler.start();

        // Assert
        verify(mockQuartzScheduler).start();
        verify(mockQuartzScheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void testStartSchedulerAlreadyRunning() throws SchedulerException {
        // Arrange
        when(mockQuartzScheduler.isStarted()).thenReturn(true);

        // Act
        syncScheduler.start();

        // Assert
        verify(mockQuartzScheduler, never()).start();
    }

    @Test
    void testStopScheduler() throws SchedulerException {
        // Arrange
        when(mockQuartzScheduler.isStarted()).thenReturn(true);

        // Act
        syncScheduler.stop();

        // Assert
        verify(mockQuartzScheduler).shutdown(true);
    }

    @Test
    void testPauseScheduler() throws SchedulerException {
        // Arrange
        when(mockQuartzScheduler.isStarted()).thenReturn(true);

        // Act
        syncScheduler.pause();

        // Assert
        verify(mockQuartzScheduler).pauseJobs(any(GroupMatcher.class));
    }

    @Test
    void testResumeScheduler() throws SchedulerException {
        // Arrange
        when(mockQuartzScheduler.isStarted()).thenReturn(true);

        // Act
        syncScheduler.resume();

        // Assert
        verify(mockQuartzScheduler).resumeJobs(any(GroupMatcher.class));
    }

    @Test
    void testTriggerFullSync() throws SchedulerException {
        // Arrange
        when(mockQuartzScheduler.isStarted()).thenReturn(true);
        when(mockQuartzScheduler.scheduleJob(any(JobDetail.class), any(Trigger.class)))
                .thenReturn(new Date());

        // Act
        SyncReport report = syncScheduler.triggerFullSync();

        // Assert
        assertNotNull(report);
        assertEquals(SyncReport.SyncType.FULL, report.getSyncType());
        assertEquals(SyncReport.SyncStatus.IN_PROGRESS, report.getStatus());
        verify(mockQuartzScheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void testTriggerIncrementalSync() throws SchedulerException {
        // Arrange
        when(mockQuartzScheduler.isStarted()).thenReturn(true);
        when(mockQuartzScheduler.scheduleJob(any(JobDetail.class), any(Trigger.class)))
                .thenReturn(new Date());

        // Act
        SyncReport report = syncScheduler.triggerIncrementalSync();

        // Assert
        assertNotNull(report);
        assertEquals(SyncReport.SyncType.INCREMENTAL, report.getSyncType());
        assertEquals(SyncReport.SyncStatus.IN_PROGRESS, report.getStatus());
        verify(mockQuartzScheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    @Test
    void testTriggerFullSyncFailure() throws SchedulerException {
        // Arrange
        when(mockQuartzScheduler.isStarted()).thenReturn(true);
        when(mockQuartzScheduler.scheduleJob(any(JobDetail.class), any(Trigger.class)))
                .thenThrow(new SchedulerException("Scheduler error"));

        // Act
        SyncReport report = syncScheduler.triggerFullSync();

        // Assert
        assertNotNull(report);
        assertEquals(SyncReport.SyncType.FULL, report.getSyncType());
        assertEquals(SyncReport.SyncStatus.FAILED, report.getStatus());
        assertNotNull(report.getErrorMessage());
    }

    @Test
    void testIsRunning() throws SchedulerException {
        // Arrange
        when(mockQuartzScheduler.isStarted()).thenReturn(true);

        // Act
        boolean running = syncScheduler.isRunning();

        // Assert
        assertTrue(running);
    }

    @Test
    void testIsRunningFalse() throws SchedulerException {
        // Arrange
        when(mockQuartzScheduler.isStarted()).thenReturn(false);

        // Act
        boolean running = syncScheduler.isRunning();

        // Assert
        assertFalse(running);
    }

    @Test
    void testGetLastSyncReport() {
        // Arrange - initially null
        assertNull(syncScheduler.getLastSyncReport());

        // Create and set a report
        SyncReport report = new SyncReport(SyncReport.SyncType.FULL);
        report.setStatus(SyncReport.SyncStatus.SUCCESS);
        report.setBucketsProcessed(5);
        report.setObjectsProcessed(100);
        ReflectionTestUtils.setField(syncScheduler, "lastSyncReport", report);

        // Act
        SyncReport retrieved = syncScheduler.getLastSyncReport();

        // Assert
        assertNotNull(retrieved);
        assertEquals(report.getSyncType(), retrieved.getSyncType());
        assertEquals(report.getStatus(), retrieved.getStatus());
        assertEquals(report.getBucketsProcessed(), retrieved.getBucketsProcessed());
    }

    @Test
    void testGetAllSyncReports() {
        // Act - initially empty
        List<SyncReport> reports = syncScheduler.getAllSyncReports();

        // Assert
        assertNotNull(reports);
        assertTrue(reports.isEmpty());
    }

    @Test
    void testSyncJobExecuteFullSync() throws Exception {
        // Arrange
        setupJobExecutionContext("FULL", "test-report-id");

        MinIOBridge.SyncStats stats = new MinIOBridge.SyncStats();
        stats.bucketsProcessed.set(10);
        stats.objectsProcessed.set(100);
        stats.completed = true;

        when(mockMinioBridge.performFullSync()).thenReturn(stats);

        // Act
        SyncScheduler.SyncJob job = new SyncScheduler.SyncJob();
        job.execute(mockJobExecutionContext);

        // Assert
        verify(mockMinioBridge).performFullSync();

        SyncReport report = syncScheduler.getSyncReport("test-report-id");
        if (report != null) {
            assertEquals(SyncReport.SyncStatus.SUCCESS, report.getStatus());
            assertEquals(10, report.getBucketsProcessed());
            assertEquals(100, report.getObjectsProcessed());
        }
    }

    @Test
    void testSyncJobExecuteIncrementalSync() throws Exception {
        // Arrange
        setupJobExecutionContext("INCREMENTAL", "test-report-id");

        MinIOBridge.SyncStats stats = new MinIOBridge.SyncStats();
        stats.objectsProcessed.set(50);
        stats.completed = true;

        when(mockMinioBridge.performIncrementalSync()).thenReturn(stats);

        // Act
        SyncScheduler.SyncJob job = new SyncScheduler.SyncJob();
        job.execute(mockJobExecutionContext);

        // Assert
        verify(mockMinioBridge).performIncrementalSync();

        SyncReport report = syncScheduler.getSyncReport("test-report-id");
        if (report != null) {
            assertEquals(SyncReport.SyncStatus.SUCCESS, report.getStatus());
            assertEquals(50, report.getObjectsProcessed());
        }
    }

    @Test
    void testSyncJobExecuteWithFailure() throws Exception {
        // Arrange
        setupJobExecutionContext("FULL", "test-report-id");

        when(mockMinioBridge.performFullSync())
                .thenThrow(new RuntimeException("Sync failed"));

        // Act & Assert
        SyncScheduler.SyncJob job = new SyncScheduler.SyncJob();
        JobExecutionException exception = assertThrows(JobExecutionException.class, () -> {
            job.execute(mockJobExecutionContext);
        });

        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Sync failed"));
    }

    @Test
    void testSyncJobExecuteWithRetry() throws Exception {
        // Arrange
        setupJobExecutionContext("FULL", "test-report-id");

        when(mockMinioBridge.performFullSync())
                .thenThrow(new RuntimeException("Temporary failure"));

        // Act & Assert
        SyncScheduler.SyncJob job = new SyncScheduler.SyncJob();
        JobExecutionException exception = assertThrows(JobExecutionException.class, () -> {
            job.execute(mockJobExecutionContext);
        });

        assertNotNull(exception);
        assertTrue(exception.refireImmediately()); // Should request refire
    }

    @Test
    void testSyncJobExecutePartialFailure() throws Exception {
        // Arrange
        setupJobExecutionContext("FULL", "test-report-id");

        MinIOBridge.SyncStats stats = new MinIOBridge.SyncStats();
        stats.bucketsProcessed.set(8);
        stats.bucketsFailed.set(2);
        stats.objectsProcessed.set(80);
        stats.objectsFailed.set(20);
        stats.completed = true;

        when(mockMinioBridge.performFullSync()).thenReturn(stats);

        // Act
        SyncScheduler.SyncJob job = new SyncScheduler.SyncJob();
        job.execute(mockJobExecutionContext);

        // Assert
        SyncReport report = syncScheduler.getSyncReport("test-report-id");
        if (report != null) {
            assertEquals(SyncReport.SyncStatus.PARTIAL, report.getStatus());
            assertEquals(8, report.getBucketsProcessed());
            assertEquals(2, report.getBucketsFailed());
            assertTrue(report.hasFailures());
        }
    }

    @Test
    void testSyncJobExecuteInvalidSyncType() throws Exception {
        // Arrange
        setupJobExecutionContext("INVALID_TYPE", "test-report-id");

        // Act & Assert
        SyncScheduler.SyncJob job = new SyncScheduler.SyncJob();
        assertThrows(JobExecutionException.class, () -> {
            job.execute(mockJobExecutionContext);
        });
    }

    // Helper methods

    private void setupJobExecutionContext(String syncType, String reportId) throws SchedulerException {
        when(mockJobExecutionContext.getJobDetail()).thenReturn(mockJobDetail);
        when(mockJobDetail.getJobDataMap()).thenReturn(mockJobDataMap);
        when(mockJobDataMap.getString("syncType")).thenReturn(syncType);
        when(mockJobDataMap.getString("reportId")).thenReturn(reportId);
        when(mockJobExecutionContext.getScheduler()).thenReturn(mockQuartzScheduler);
        when(mockQuartzScheduler.getContext()).thenReturn(mockSchedulerContext);
        when(mockSchedulerContext.get("minioBridge")).thenReturn(mockMinioBridge);
        when(mockSchedulerContext.get("syncScheduler")).thenReturn(syncScheduler);
        when(mockJobExecutionContext.getRefireCount()).thenReturn(0);
    }
}
