package org.apache.atlas.minio.utils;

/**
 * MinIO 集成相关常量
 */
public class MinIOConstants {

    // 配置键
    public static final String CONFIG_ENABLED = "atlas.minio.enabled";
    public static final String CONFIG_ENDPOINT = "atlas.minio.endpoint";
    public static final String CONFIG_ACCESS_KEY = "atlas.minio.access.key";
    public static final String CONFIG_SECRET_KEY = "atlas.minio.secret.key";
    public static final String CONFIG_REGION = "atlas.minio.region";
    public static final String CONFIG_CONNECTION_TIMEOUT = "atlas.minio.connection.timeout";
    public static final String CONFIG_SOCKET_TIMEOUT = "atlas.minio.socket.timeout";

    // 默认值
    public static final String DEFAULT_REGION = "us-east-1";
    public static final int DEFAULT_CONNECTION_TIMEOUT = 10000;
    public static final int DEFAULT_SOCKET_TIMEOUT = 60000;

    // 同步配置
    public static final String CONFIG_SYNC_ENABLED = "atlas.minio.sync.enabled";
    public static final String CONFIG_SYNC_FULL_INITIAL = "atlas.minio.sync.full.initial";
    public static final String CONFIG_SYNC_SCHEDULE_CRON = "atlas.minio.sync.schedule.cron";
    public static final String CONFIG_SYNC_INCREMENTAL_ENABLED = "atlas.minio.sync.incremental.enabled";
    public static final String CONFIG_SYNC_BATCH_SIZE = "atlas.minio.sync.batch.size";
    public static final String CONFIG_SYNC_THREAD_POOL_SIZE = "atlas.minio.sync.thread.pool.size";

    public static final int DEFAULT_SYNC_BATCH_SIZE = 100;
    public static final int DEFAULT_SYNC_THREAD_POOL_SIZE = 5;
    public static final String DEFAULT_SYNC_SCHEDULE_CRON = "0 0 2 * * ?";

    // 事件配置
    public static final String CONFIG_EVENT_ENABLED = "atlas.minio.event.enabled";
    public static final String CONFIG_EVENT_QUEUE_CAPACITY = "atlas.minio.event.queue.capacity";
    public static final String CONFIG_EVENT_THREAD_POOL_SIZE = "atlas.minio.event.thread.pool.size";
    public static final String CONFIG_EVENT_RETRY_MAX = "atlas.minio.event.retry.max";
    public static final String CONFIG_EVENT_RETRY_DELAY = "atlas.minio.event.retry.delay";

    public static final int DEFAULT_EVENT_QUEUE_CAPACITY = 10000;
    public static final int DEFAULT_EVENT_THREAD_POOL_SIZE = 3;
    public static final int DEFAULT_EVENT_RETRY_MAX = 3;
    public static final long DEFAULT_EVENT_RETRY_DELAY = 5000L;

    // 分类配置
    public static final String CONFIG_CLASSIFICATION_AUTO_ENABLED = "atlas.minio.classification.auto.enabled";
    public static final String CONFIG_CLASSIFICATION_RULES_PATH = "atlas.minio.classification.rules.path";

    // Atlas 类型名称
    public static final String TYPE_MINIO_BUCKET = "minio_bucket";
    public static final String TYPE_MINIO_OBJECT = "minio_object";
    public static final String TYPE_MINIO_SYNC_EVENT = "minio_sync_event";

    // 用户元数据前缀
    public static final String USER_METADATA_PREFIX = "x-amz-meta-";

    // 私有构造函数
    private MinIOConstants() {
        throw new UnsupportedOperationException("Utility class");
    }
}
