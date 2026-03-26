package org.apache.atlas.minio.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Result of a classification operation
 * Contains classifications applied to an entity along with metadata
 */
public class ClassificationResult {
    private String qualifiedName;
    private List<String> classifications;
    private ClassificationSource source;
    private Date timestamp;
    private double confidence;

    public enum ClassificationSource {
        MANUAL,
        AUTOMATIC
    }

    public ClassificationResult() {
        this.classifications = new ArrayList<>();
        this.timestamp = new Date();
        this.confidence = 1.0;
    }

    public ClassificationResult(String qualifiedName, ClassificationSource source) {
        this();
        this.qualifiedName = qualifiedName;
        this.source = source;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public void setQualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    public List<String> getClassifications() {
        return classifications;
    }

    public void setClassifications(List<String> classifications) {
        this.classifications = classifications;
    }

    public void addClassification(String classification) {
        if (this.classifications == null) {
            this.classifications = new ArrayList<>();
        }
        if (!this.classifications.contains(classification)) {
            this.classifications.add(classification);
        }
    }

    public ClassificationSource getSource() {
        return source;
    }

    public void setSource(ClassificationSource source) {
        this.source = source;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public boolean hasClassifications() {
        return classifications != null && !classifications.isEmpty();
    }

    @Override
    public String toString() {
        return "ClassificationResult{" +
                "qualifiedName='" + qualifiedName + '\'' +
                ", classifications=" + classifications +
                ", source=" + source +
                ", timestamp=" + timestamp +
                ", confidence=" + confidence +
                '}';
    }
}
