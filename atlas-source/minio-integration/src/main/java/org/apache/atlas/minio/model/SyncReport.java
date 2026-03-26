package org.apache.atlas.minio.model;

import java.util.Date;

/**
 * SyncReport - Tracks synchronization operation results
 * Provides statistics and status for sync operations
 */
public class SyncReport {

    public enum SyncType {
        FULL,
        INCREMENTAL
    }

    public enum SyncStatus {
        SUCCESS,
        FAILED,
        PARTIAL,
        IN_PROGRESS
    }

    private Date timestamp;
    private SyncType syncType;
    private SyncStatus status;
    private int bucketsProcessed;
    private int bucketsFailed;
    private int objectsProcessed;
    private int objectsFailed;
    private long durationMs;
    private String errorMessage;
    private String details;

    public SyncReport() {
        this.timestamp = new Date();
        this.status = SyncStatus.IN_PROGRESS;
    }

    public SyncReport(SyncType syncType) {
        this();
        this.syncType = syncType;
    }

    // Getters and Setters

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public SyncType getSyncType() {
        return syncType;
    }

    public void setSyncType(SyncType syncType) {
        this.syncType = syncType;
    }

    public SyncStatus getStatus() {
        return status;
    }

    public void setStatus(SyncStatus status) {
        this.status = status;
    }

    public int getBucketsProcessed() {
        return bucketsProcessed;
    }

    public void setBucketsProcessed(int bucketsProcessed) {
        this.bucketsProcessed = bucketsProcessed;
    }

    public int getBucketsFailed() {
        return bucketsFailed;
    }

    public void setBucketsFailed(int bucketsFailed) {
        this.bucketsFailed = bucketsFailed;
    }

    public int getObjectsProcessed() {
        return objectsProcessed;
    }

    public void setObjectsProcessed(int objectsProcessed) {
        this.objectsProcessed = objectsProcessed;
    }

    public int getObjectsFailed() {
        return objectsFailed;
    }

    public void setObjectsFailed(int objectsFailed) {
        this.objectsFailed = objectsFailed;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    // Utility methods

    public int getTotalBuckets() {
        return bucketsProcessed + bucketsFailed;
    }

    public int getTotalObjects() {
        return objectsProcessed + objectsFailed;
    }

    public boolean hasFailures() {
        return bucketsFailed > 0 || objectsFailed > 0;
    }

    public void markSuccess() {
        this.status = hasFailures() ? SyncStatus.PARTIAL : SyncStatus.SUCCESS;
    }

    public void markFailed(String errorMessage) {
        this.status = SyncStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return String.format(
                "SyncReport{type=%s, status=%s, buckets=%d/%d, objects=%d/%d, duration=%dms, error=%s}",
                syncType, status, bucketsProcessed, getTotalBuckets(),
                objectsProcessed, getTotalObjects(), durationMs,
                errorMessage != null ? errorMessage : "none"
        );
    }

    /**
     * Create a summary string for UI display
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(syncType).append(" sync ");
        summary.append(status).append(": ");
        summary.append(bucketsProcessed).append(" buckets, ");
        summary.append(objectsProcessed).append(" objects processed");

        if (hasFailures()) {
            summary.append(" (").append(bucketsFailed).append(" buckets, ");
            summary.append(objectsFailed).append(" objects failed)");
        }

        if (durationMs > 0) {
            summary.append(" in ").append(durationMs).append("ms");
        }

        return summary.toString();
    }
}
