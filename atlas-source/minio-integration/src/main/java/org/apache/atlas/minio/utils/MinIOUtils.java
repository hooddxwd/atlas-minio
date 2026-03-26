package org.apache.atlas.minio.utils;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.atlas.minio.model.MinioBucket;
import org.apache.atlas.minio.model.MinioObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * MinIO 工具类
 */
public class MinIOUtils {

    private static final Logger LOG = LoggerFactory.getLogger(MinIOUtils.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            .withZone(ZoneId.systemDefault());

    /**
     * 从 S3 摘要创建 MinioObject
     */
    public static MinioObject fromS3Summary(String bucketName, S3ObjectSummary summary) {
        MinioObject object = new MinioObject();
        object.setBucketName(bucketName);
        object.setPath(summary.getKey());
        object.setSize(summary.getSize());
        object.setETag(summary.getETag());
        object.setLastModified(new Date(summary.getLastModified().getTime()));
        object.setStorageClass(summary.getStorageClass());
        return object;
    }

    /**
     * 从 S3 元数据提取用户自定义元数据
     */
    public static Map<String, String> extractUserMetadata(ObjectMetadata metadata) {
        Map<String, String> userMetadata = new HashMap<>();

        for (Map.Entry<String, String> entry : metadata.getUserMetadata().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(MinIOConstants.USER_METADATA_PREFIX)) {
                String cleanKey = key.substring(MinIOConstants.USER_METADATA_PREFIX.length());
                userMetadata.put(cleanKey, entry.getValue());
            } else {
                userMetadata.put(key, entry.getValue());
            }
        }

        return userMetadata;
    }

    /**
     * 格式化日期
     */
    public static String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        return DATE_FORMATTER.format(Instant.ofEpochMilli(date.getTime()));
    }

    /**
     * 格式化文件大小
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 检查字符串是否为空
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 安全获取配置值
     */
    public static String getConfigValue(Map<String, String> config, String key, String defaultValue) {
        String value = config.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 安全获取整型配置值
     */
    public static int getConfigInt(Map<String, String> config, String key, int defaultValue) {
        try {
            String value = config.get(key);
            if (value != null) {
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException e) {
            LOG.warn("Invalid integer value for config key: {}, using default: {}", key, defaultValue);
        }
        return defaultValue;
    }

    /**
     * 安全获取长整型配置值
     */
    public static long getConfigLong(Map<String, String> config, String key, long defaultValue) {
        try {
            String value = config.get(key);
            if (value != null) {
                return Long.parseLong(value);
            }
        } catch (NumberFormatException e) {
            LOG.warn("Invalid long value for config key: {}, using default: {}", key, defaultValue);
        }
        return defaultValue;
    }

    // 私有构造函数
    private MinIOUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
}
