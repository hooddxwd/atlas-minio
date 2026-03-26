package org.apache.atlas.minio.bridge;

import org.apache.atlas.minio.model.SyncReport;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SyncScheduler - Manages scheduled synchronization using Quartz
 * Automates MinIO to Atlas sync operations with configurable schedules
 */
@Component
public class SyncScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(SyncScheduler.class);

    // Job keys
    private static final String INCREMENTAL_SYNC_JOB = "MINIO_INCREMENTAL_SYNC";
    private static final String FULL_SYNC_JOB = "MINIO_FULL_SYNC";
    private static final String JOB_GROUP = "MINIO_SYNC_GROUP";

    // Job data keys
    private static final String SYNC_TYPE_KEY = "syncType";
    private static final String REPORT_ID_KEY = "reportId";

    private final MinIOBridge minioBridge;
    private final Scheduler quartzScheduler;

    // Configuration
    @Value("${atlas.minio.sync.schedule.cron:0 0 2 * * ?}")
    private String incrementalSyncCron;

    @Value("${atlas.minio.sync.full.initial:true}")
    private boolean runFullSyncOnStart;

    @Value("${atlas.minio.sync.incremental.enabled:true}")
    private boolean incrementalSyncEnabled;

    @Value("${atlas.minio.sync.full.validation.cron:0 0 3 ? * SUN}")
    private String fullValidationCron;

    @Value("${atlas.minio.sync.full.validation.enabled:false}")
    private boolean fullValidationEnabled;

    // Store recent sync reports
    private final ConcurrentMap<String, SyncReport> syncReports = new ConcurrentHashMap<>();
    private volatile SyncReport lastSyncReport;

    @Autowired
    public SyncScheduler(MinIOBridge minioBridge, Scheduler quartzScheduler) {
        this.minioBridge = minioBridge;
        this.quartzScheduler = quartzScheduler;
    }

    @PostConstruct
    public void init() {
        LOG.info("SyncScheduler initializing...");
        start();
    }

    @PreDestroy
    public void destroy() {
        LOG.info("SyncScheduler shutting down...");
        stop();
    }

    /**
     * Start the scheduler and schedule jobs
     */
    public void start() {
        try {
            if (quartzScheduler.isStarted()) {
                LOG.info("Scheduler is already started");
                return;
            }

            LOG.info("Starting MinIO sync scheduler...");
            quartzScheduler.start();

            // Schedule incremental sync job
            if (incrementalSyncEnabled) {
                scheduleIncrementalSync();
            }

            // Schedule full validation job (optional)
            if (fullValidationEnabled) {
                scheduleFullValidation();
            }

            // Run initial full sync if configured
            if (runFullSyncOnStart) {
                LOG.info("Running initial full sync on startup...");
                triggerFullSync();
            }

            LOG.info("Sync scheduler started successfully");
            logScheduledJobs();

        } catch (SchedulerException e) {
            LOG.error("Failed to start sync scheduler", e);
            throw new RuntimeException("Failed to start sync scheduler", e);
        }
    }

    /**
     * Stop the scheduler
     */
    public void stop() {
        try {
            if (!quartzScheduler.isStarted()) {
                LOG.info("Scheduler is not running");
                return;
            }

            LOG.info("Stopping MinIO sync scheduler...");
            quartzScheduler.shutdown(true);
            LOG.info("Sync scheduler stopped");

        } catch (SchedulerException e) {
            LOG.error("Failed to stop sync scheduler", e);
        }
    }

    /**
     * Pause all scheduled jobs
     */
    public void pause() {
        try {
            LOG.info("Pausing all sync jobs...");
            quartzScheduler.pauseJobs(GroupMatcher.jobGroupEquals(JOB_GROUP));
            LOG.info("All sync jobs paused");

        } catch (SchedulerException e) {
            LOG.error("Failed to pause jobs", e);
            throw new RuntimeException("Failed to pause jobs", e);
        }
    }

    /**
     * Resume all paused jobs
     */
    public void resume() {
        try {
            LOG.info("Resuming all sync jobs...");
            quartzScheduler.resumeJobs(GroupMatcher.jobGroupEquals(JOB_GROUP));
            LOG.info("All sync jobs resumed");

        } catch (SchedulerException e) {
            LOG.error("Failed to resume jobs", e);
            throw new RuntimeException("Failed to resume jobs", e);
        }
    }

    /**
     * Manually trigger a full sync
     */
    public SyncReport triggerFullSync() {
        LOG.info("Manually triggering full sync...");
        String reportId = UUID.randomUUID().toString();
        SyncReport report = new SyncReport(SyncReport.SyncType.FULL);
        syncReports.put(reportId, report);

        try {
            JobDetail job = JobBuilder.newJob(SyncJob.class)
                    .withIdentity(JOB_NAME(FULL_SYNC_JOB, reportId), JOB_GROUP)
                    .usingJobData(SYNC_TYPE_KEY, SyncType.FULL.name())
                    .usingJobData(REPORT_ID_KEY, reportId)
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(TRIGGER_NAME(FULL_SYNC_JOB, reportId), JOB_GROUP)
                    .startNow()
                    .build();

            quartzScheduler.scheduleJob(job, trigger);
            return report;

        } catch (SchedulerException e) {
            LOG.error("Failed to trigger full sync", e);
            report.markFailed(e.getMessage());
            return report;
        }
    }

    /**
     * Manually trigger an incremental sync
     */
    public SyncReport triggerIncrementalSync() {
        LOG.info("Manually triggering incremental sync...");
        String reportId = UUID.randomUUID().toString();
        SyncReport report = new SyncReport(SyncReport.SyncType.INCREMENTAL);
        syncReports.put(reportId, report);

        try {
            JobDetail job = JobBuilder.newJob(SyncJob.class)
                    .withIdentity(JOB_NAME(INCREMENTAL_SYNC_JOB, reportId), JOB_GROUP)
                    .usingJobData(SYNC_TYPE_KEY, SyncType.INCREMENTAL.name())
                    .usingJobData(REPORT_ID_KEY, reportId)
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(TRIGGER_NAME(INCREMENTAL_SYNC_JOB, reportId), JOB_GROUP)
                    .startNow()
                    .build();

            quartzScheduler.scheduleJob(job, trigger);
            return report;

        } catch (SchedulerException e) {
            LOG.error("Failed to trigger incremental sync", e);
            report.markFailed(e.getMessage());
            return report;
        }
    }

    /**
     * Get the most recent sync report
     */
    public SyncReport getLastSyncReport() {
        return lastSyncReport;
    }

    /**
     * Get the latest sync report (alias for getLastSyncReport)
     */
    public SyncReport getLatestReport() {
        return lastSyncReport;
    }

    /**
     * Get a specific sync report by ID
     */
    public SyncReport getSyncReport(String reportId) {
        return syncReports.get(reportId);
    }

    /**
     * Get all recent sync reports
     */
    public List<SyncReport> getAllSyncReports() {
        return List.copyOf(syncReports.values());
    }

    /**
     * Get sync report history with limit
     */
    public List<SyncReport> getReportHistory(int limit) {
        List<SyncReport> allReports = List.copyOf(syncReports.values());
        // Sort by timestamp descending (most recent first)
        allReports.sort((r1, r2) -> r2.getTimestamp().compareTo(r1.getTimestamp()));

        // Return limited results
        if (allReports.size() <= limit) {
            return allReports;
        }

        return allReports.subList(0, limit);
    }

    /**
     * Check if scheduler is running
     */
    public boolean isRunning() {
        try {
            return quartzScheduler.isStarted();
        } catch (SchedulerException e) {
            LOG.error("Failed to check scheduler status", e);
            return false;
        }
    }

    // Private methods

    private void scheduleIncrementalSync() throws SchedulerException {
        JobDetail job = JobBuilder.newJob(SyncJob.class)
                .withIdentity(INCREMENTAL_SYNC_JOB, JOB_GROUP)
                .usingJobData(SYNC_TYPE_KEY, SyncType.INCREMENTAL.name())
                .withDescription("MinIO incremental sync job")
                .storeDurably()
                .build();

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(INCREMENTAL_SYNC_JOB + "_TRIGGER", JOB_GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule(incrementalSyncCron))
                .withDescription("Trigger for incremental sync at " + incrementalSyncCron)
                .build();

        quartzScheduler.scheduleJob(job, trigger);
        LOG.info("Scheduled incremental sync with cron: {}", incrementalSyncCron);
    }

    private void scheduleFullValidation() throws SchedulerException {
        JobDetail job = JobBuilder.newJob(SyncJob.class)
                .withIdentity(FULL_SYNC_JOB, JOB_GROUP)
                .usingJobData(SYNC_TYPE_KEY, SyncType.FULL.name())
                .withDescription("MinIO full validation sync job")
                .storeDurably()
                .build();

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(FULL_SYNC_JOB + "_TRIGGER", JOB_GROUP)
                .withSchedule(CronScheduleBuilder.cronSchedule(fullValidationCron))
                .withDescription("Trigger for full validation at " + fullValidationCron)
                .build();

        quartzScheduler.scheduleJob(job, trigger);
        LOG.info("Scheduled full validation sync with cron: {}", fullValidationCron);
    }

    private void logScheduledJobs() throws SchedulerException {
        List<String> jobNames = quartzScheduler.getJobNames(JOB_GROUP);
        LOG.info("Currently scheduled jobs: {}", jobNames);

        for (String jobName : jobNames) {
            JobKey jobKey = JobKey.jobKey(jobName, JOB_GROUP);
            List<Trigger> triggers = quartzScheduler.getTriggersOfJob(jobKey);
            for (Trigger trigger : triggers) {
                LOG.info("  - Job: {}, Next fire time: {}", jobName, trigger.getNextFireTime());
            }
        }
    }

    private static String JOB_NAME(String baseName, String uniqueId) {
        return baseName + "_" + uniqueId;
    }

    private static String TRIGGER_NAME(String baseName, String uniqueId) {
        return baseName + "_TRIGGER_" + uniqueId;
    }

    /**
     * SyncJob - Quartz Job that executes sync operations
     */
    public static class SyncJob implements Job {

        private static final Logger LOG = LoggerFactory.getLogger(SyncJob.class);

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            JobDataMap dataMap = context.getJobDetail().getJobDataMap();
            String syncTypeStr = dataMap.getString(SYNC_TYPE_KEY);
            String reportId = dataMap.getString(REPORT_ID_KEY);

            LOG.info("Executing sync job: type={}, reportId={}", syncTypeStr, reportId);

            // Get MinIOBridge from Spring context via scheduler context
            SchedulerContext schedulerContext = null;
            MinIOBridge minioBridge = null;
            SyncScheduler scheduler = null;

            try {
                schedulerContext = context.getScheduler().getContext();
                minioBridge = (MinIOBridge) schedulerContext.get("minioBridge");
                scheduler = (SyncScheduler) schedulerContext.get("syncScheduler");

            } catch (SchedulerException e) {
                LOG.error("Failed to get scheduler context", e);
                throw new JobExecutionException(e);
            }

            if (minioBridge == null || scheduler == null) {
                String error = "MinIOBridge or SyncScheduler not found in scheduler context";
                LOG.error(error);
                throw new JobExecutionException(error);
            }

            SyncReport report = scheduler.syncReports.get(reportId);
            if (report == null) {
                report = new SyncReport(SyncReport.SyncType.valueOf(syncTypeStr));
                scheduler.syncReports.put(reportId, report);
            }

            long startTime = System.currentTimeMillis();

            try {
                MinIOBridge.SyncStats stats;

                if (SyncType.INCREMENTAL.name().equals(syncTypeStr)) {
                    stats = minioBridge.performIncrementalSync();
                } else if (SyncType.FULL.name().equals(syncTypeStr)) {
                    stats = minioBridge.performFullSync();
                } else {
                    throw new IllegalArgumentException("Unknown sync type: " + syncTypeStr);
                }

                // Update report with stats
                report.setBucketsProcessed(stats.bucketsProcessed.get());
                report.setBucketsFailed(stats.bucketsFailed.get());
                report.setObjectsProcessed(stats.objectsProcessed.get());
                report.setObjectsFailed(stats.objectsFailed.get());
                report.setDurationMs(System.currentTimeMillis() - startTime);

                if (stats.completed) {
                    report.markSuccess();
                    LOG.info("Sync completed successfully: {}", report);
                } else {
                    report.markFailed("Sync did not complete successfully");
                    LOG.warn("Sync completed with failures: {}", report);
                }

            } catch (Exception e) {
                LOG.error("Sync job failed", e);
                report.setDurationMs(System.currentTimeMillis() - startTime);
                report.markFailed(e.getMessage());

                // Retry logic - max 3 retries
                int refireCount = context.getRefireCount();
                if (refireCount < 3) {
                    LOG.info("Refiring job attempt {}/3", refireCount + 1);
                    throw new JobExecutionException(e, true); // refire immediately
                } else {
                    LOG.error("Job failed after {} retry attempts", refireCount);
                    throw new JobExecutionException(e, false); // don't refire
                }
            } finally {
                scheduler.lastSyncReport = report;
            }
        }
    }

    /**
     * SyncType enum for job configuration
     */
    private enum SyncType {
        FULL,
        INCREMENTAL
    }
}
