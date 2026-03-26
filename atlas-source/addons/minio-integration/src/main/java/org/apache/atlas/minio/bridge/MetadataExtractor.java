package org.apache.atlas.minio.bridge;

import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.atlas.minio.model.MinioBucket;
import org.apache.atlas.minio.model.MinioObject;
import org.apache.atlas.minio.utils.MinIOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * MinIO 元数据提取器
 */
public class MetadataExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(MetadataExtractor.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private final MinIOClient client;

    public MetadataExtractor(MinIOClient client) {
        this.client = client;
    }

    public MinioBucket extractBucketMetadata(Bucket s3Bucket) {
        LOG.debug("提取 bucket 元数据: {}", s3Bucket.getName());
        MinioBucket bucket = new MinioBucket();
        bucket.setName(s3Bucket.getName());
        bucket.setCreationDate(DATE_FORMAT.format(new Date(s3Bucket.getCreationDate().getTime())));
        bucket.setOwner(s3Bucket.getOwner() != null ? s3Bucket.getOwner().getDisplayName() : "unknown");
        bucket.setLocation("default");
        bucket.addAttribute("s3_name", s3Bucket.getName());
        return bucket;
    }

    public MinioObject extractObjectMetadata(String bucketName, S3ObjectSummary summary) {
        LOG.debug("提取对象元数据: bucket={}, key={}", bucketName, summary.getKey());
        MinioObject object = MinIOUtils.fromS3Summary(bucketName, summary);
        try {
            ObjectMetadata metadata = client.getObjectMetadata(bucketName, summary.getKey());
            enrichObjectMetadata(object, metadata);
        } catch (Exception e) {
            LOG.warn("获取对象完整元数据失败: bucket={}, key={}", bucketName, summary.getKey(), e);
        }
        return object;
    }

    private void enrichObjectMetadata(MinioObject object, ObjectMetadata metadata) {
        object.setContentType(metadata.getContentType());
        if (metadata.getVersionId() != null) {
            object.setVersionId(metadata.getVersionId());
        }
        Map<String, String> userMetadata = MinIOUtils.extractUserMetadata(metadata);
        object.setUserMetadata(userMetadata);
        for (Map.Entry<String, String> entry : userMetadata.entrySet()) {
            object.addAttribute("meta_" + entry.getKey(), entry.getValue());
        }
        if (metadata.getContentEncoding() != null) {
            object.addAttribute("content_encoding", metadata.getContentEncoding());
        }
        if (metadata.getCacheControl() != null) {
            object.addAttribute("cache_control", metadata.getCacheControl());
        }
    }

    public void extractObjectMetadataBatch(String bucketName, List<S3ObjectSummary> summaries, List<MinioObject> results) {
        for (S3ObjectSummary summary : summaries) {
            try {
                MinioObject object = extractObjectMetadata(bucketName, summary);
                results.add(object);
            } catch (Exception e) {
                LOG.error("提取对象元数据失败: {}", summary.getKey(), e);
            }
        }
    }
}
