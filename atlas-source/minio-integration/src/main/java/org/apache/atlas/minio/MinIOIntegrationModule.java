package org.apache.atlas.minio;

import org.apache.atlas.minio.bridge.MinIOBridge;
import org.apache.atlas.minio.event.MinioEventNotifier;
import org.apache.atlas.minio.classification.ClassificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

/**
 * MinIO 集成模块主类
 * 负责初始化和管理 MinIO 集成功能
 */
@ComponentScan(basePackages = "org.apache.atlas.minio")
public class MinIOIntegrationModule {

    private static final Logger LOG = LoggerFactory.getLogger(MinIOIntegrationModule.class);

    private final MinIOBridge bridge;
    private final MinioEventNotifier eventNotifier;
    private final ClassificationService classificationService;

    public MinIOIntegrationModule(MinIOBridge bridge,
                                   MinioEventNotifier eventNotifier,
                                   ClassificationService classificationService) {
        this.bridge = bridge;
        this.eventNotifier = eventNotifier;
        this.classificationService = classificationService;
    }

    /**
     * 初始化 MinIO 集成模块
     */
    public void initialize() {
        LOG.info("正在初始化 MinIO 集成模块...");

        try {
            // 初始化桥接器
            bridge.initialize();

            // 启动事件监听器
            eventNotifier.start();

            LOG.info("MinIO 集成模块初始化完成");
        } catch (Exception e) {
            LOG.error("MinIO 集成模块初始化失败", e);
            throw new RuntimeException("Failed to initialize MinIO integration", e);
        }
    }

    /**
     * 关闭 MinIO 集成模块
     */
    public void shutdown() {
        LOG.info("正在关闭 MinIO 集成模块...");

        try {
            eventNotifier.stop();
            bridge.shutdown();

            LOG.info("MinIO 集成模块已关闭");
        } catch (Exception e) {
            LOG.error("关闭 MinIO 集成模块时出错", e);
        }
    }
}
