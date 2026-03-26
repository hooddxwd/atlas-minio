package org.apache.atlas.minio.model;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * MinIO 对象模型
 */
public class MinioObject {
    private String bucketName;
    private String path;
    private long size;
    private String eTag;
    private Date lastModified;
    private String storageClass;
    private String contentType;
    private String versionId;
    private Map<String, String> userMetadata = new HashMap<>();
    private Map<String, Object> acl = new HashMap<>();
    private Map<String, Object> attributes = new HashMap<>();

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    // Fixed: use getEtag to match setEtag
    public String getEtag() {
        return eTag;
    }

    // Also keep getETag for backward compatibility
    public String getETag() {
        return eTag;
    }

    public void setETag(String eTag) {
        this.eTag = eTag;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    public String getStorageClass() {
        return storageClass;
    }

    public void setStorageClass(String storageClass) {
        this.storageClass = storageClass;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getVersionId() {
        return versionId;
    }

    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }

    public Map<String, String> getUserMetadata() {
        return userMetadata;
    }

    public void setUserMetadata(Map<String, String> userMetadata) {
        this.userMetadata = userMetadata;
    }

    public void addUserMetadata(String key, String value) {
        if (this.userMetadata == null) {
            this.userMetadata = new HashMap<>();
        }
        this.userMetadata.put(key, value);
    }

    public Map<String, Object> getAcl() {
        return acl;
    }

    public void setAcl(Map<String, Object> acl) {
        this.acl = acl;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public void addAttribute(String key, Object value) {
        if (this.attributes == null) {
            this.attributes = new HashMap<>();
        }
        this.attributes.put(key, value);
    }
}
