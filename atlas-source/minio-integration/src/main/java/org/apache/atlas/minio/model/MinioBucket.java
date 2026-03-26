package org.apache.atlas.minio.model;

import java.util.Date;

/**
 * MinIO 存储桶模型（占位符实现）
 * Task 2.3 将完善此模型
 */
public class MinioBucket {
    private String name;
    private Date creationDate;
    private String location;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
