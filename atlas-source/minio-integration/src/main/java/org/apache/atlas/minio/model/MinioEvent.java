package org.apache.atlas.minio.model;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * MinIO Event Model
 * Represents an event notification from MinIO via Webhook
 */
public class MinioEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    // Event type: s3:ObjectCreated:*, s3:ObjectRemoved:*, s3:ObjectUpdated:*
    private String eventType;

    // When the event occurred
    private Date eventTime;

    // Bucket name
    private String bucketName;

    // Object path/key
    private String objectPath;

    // MinIO event name
    private String eventName;

    // Who triggered the event
    private Map<String, String> userIdentity;

    // Source IP address
    private String sourceIPAddress;

    // Original event data
    private Map<String, Object> rawData;

    /**
     * Default constructor
     */
    public MinioEvent() {
    }

    /**
     * Constructor with essential fields
     */
    public MinioEvent(String eventType, Date eventTime, String bucketName, String objectPath) {
        this.eventType = eventType;
        this.eventTime = eventTime;
        this.bucketName = bucketName;
        this.objectPath = objectPath;
    }

    // Getters and Setters

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Date getEventTime() {
        return eventTime;
    }

    public void setEventTime(Date eventTime) {
        this.eventTime = eventTime;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getObjectPath() {
        return objectPath;
    }

    public void setObjectPath(String objectPath) {
        this.objectPath = objectPath;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public Map<String, String> getUserIdentity() {
        return userIdentity;
    }

    public void setUserIdentity(Map<String, String> userIdentity) {
        this.userIdentity = userIdentity;
    }

    public String getSourceIPAddress() {
        return sourceIPAddress;
    }

    public void setSourceIPAddress(String sourceIPAddress) {
        this.sourceIPAddress = sourceIPAddress;
    }

    public Map<String, Object> getRawData() {
        return rawData;
    }

    public void setRawData(Map<String, Object> rawData) {
        this.rawData = rawData;
    }

    /**
     * Check if this is an ObjectCreated event
     */
    public boolean isObjectCreated() {
        return eventType != null && eventType.startsWith("s3:ObjectCreated:");
    }

    /**
     * Check if this is an ObjectRemoved event
     */
    public boolean isObjectRemoved() {
        return eventType != null && eventType.startsWith("s3:ObjectRemoved:");
    }

    /**
     * Check if this is an ObjectUpdated event
     */
    public boolean isObjectUpdated() {
        return eventType != null && eventType.startsWith("s3:ObjectUpdated:");
    }

    @Override
    public String toString() {
        return "MinioEvent{" +
                "eventType='" + eventType + '\'' +
                ", eventTime=" + eventTime +
                ", bucketName='" + bucketName + '\'' +
                ", objectPath='" + objectPath + '\'' +
                ", eventName='" + eventName + '\'' +
                ", sourceIPAddress='" + sourceIPAddress + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MinioEvent that = (MinioEvent) o;

        if (eventType != null ? !eventType.equals(that.eventType) : that.eventType != null) return false;
        if (eventTime != null ? !eventTime.equals(that.eventTime) : that.eventTime != null) return false;
        if (bucketName != null ? !bucketName.equals(that.bucketName) : that.bucketName != null) return false;
        return objectPath != null ? objectPath.equals(that.objectPath) : that.objectPath == null;
    }

    @Override
    public int hashCode() {
        int result = eventType != null ? eventType.hashCode() : 0;
        result = 31 * result + (eventTime != null ? eventTime.hashCode() : 0);
        result = 31 * result + (bucketName != null ? bucketName.hashCode() : 0);
        result = 31 * result + (objectPath != null ? objectPath.hashCode() : 0);
        return result;
    }
}
