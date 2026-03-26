package org.apache.atlas.minio.bridge;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.atlas.minio.utils.MinIOConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * MinIO S3 客户端封装
 */
public class MinIOClient {
    private static final Logger LOG = LoggerFactory.getLogger(MinIOClient.class);
    private final AmazonS3 s3Client;
    private final String endpoint;

    public MinIOClient(String endpoint, String accessKey, String secretKey, String region) {
        this.endpoint = endpoint;
        this.s3Client = buildClient(endpoint, accessKey, secretKey, region);
    }

    private AmazonS3 buildClient(String endpoint, String accessKey, String secretKey, String region) {
        LOG.info("正在初始化 MinIO S3 客户端: {}", endpoint);
        BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
        ClientConfiguration clientConfig = new ClientConfiguration();
        clientConfig.setConnectionTimeout(MinIOConstants.DEFAULT_CONNECTION_TIMEOUT);
        clientConfig.setSocketTimeout(MinIOConstants.DEFAULT_SOCKET_TIMEOUT);
        clientConfig.setUseExpectContinue(false);

        return AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfig)
                .build();
    }

    public boolean testConnection() {
        try {
            s3Client.listBuckets();
            LOG.info("MinIO 连接测试成功");
            return true;
        } catch (Exception e) {
            LOG.error("MinIO 连接测试失败", e);
            return false;
        }
    }

    public List<Bucket> listBuckets() {
        try {
            return s3Client.listBuckets();
        } catch (Exception e) {
            LOG.error("获取 bucket 列表失败", e);
            throw new RuntimeException("Failed to list buckets", e);
        }
    }

    public boolean bucketExists(String bucketName) {
        try {
            return s3Client.doesBucketExistV2(bucketName);
        } catch (Exception e) {
            LOG.error("检查 bucket 是否存在失败: {}", bucketName, e);
            return false;
        }
    }

    public List<S3ObjectSummary> listObjects(String bucketName, String prefix, int maxKeys) {
        try {
            ListObjectsV2Request request = new ListObjectsV2Request()
                    .withBucketName(bucketName)
                    .withPrefix(prefix)
                    .withMaxKeys(maxKeys);
            ListObjectsV2Result result = s3Client.listObjectsV2(request);
            return result.getObjectSummaries();
        } catch (Exception e) {
            LOG.error("获取对象列表失败: bucket={}, prefix={}", bucketName, prefix, e);
            throw new RuntimeException("Failed to list objects", e);
        }
    }

    public ObjectMetadata getObjectMetadata(String bucketName, String objectKey) {
        try {
            return s3Client.getObjectMetadata(bucketName, objectKey);
        } catch (Exception e) {
            LOG.error("获取对象元数据失败: bucket={}, key={}", bucketName, objectKey, e);
            throw new RuntimeException("Failed to get object metadata", e);
        }
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void shutdown() {
        if (s3Client != null) {
            s3Client.shutdown();
            LOG.info("MinIO S3 客户端已关闭");
        }
    }
}
