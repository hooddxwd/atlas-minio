# Apache Atlas 集成 MinIO

## 项目概述

本项目在 Apache Atlas 内部集成 MinIO 元数据管理能力，实现数据治理和元数据管理。

## 功能特性

- 通过 S3 协议连接远程 MinIO 实例
- 提取完整的元数据（bucket、对象、ACL、版本、用户元数据）
- 手动和智能自动分类
- 事件驱动实时同步 + 定时同步兜底
- Web UI 管理界面
- 完整的监控面板

## 快速开始

### 前置要求

- Docker 和 Docker Compose
- JDK 8+
- Maven 3.x

### 初始化

```bash
./scripts/setup.sh
```

### 构建

```bash
./scripts/build.sh
```

### 部署

```bash
./scripts/deploy.sh
```

## 配置

编辑 `docker/config/application.properties` 配置 MinIO 连接信息：

```properties
atlas.minio.endpoint=https://minio.example.com
atlas.minio.access.key=YOUR_ACCESS_KEY
atlas.minio.secret.key=YOUR_SECRET_KEY
```

## 文档

详细设计文档：[docs/superpowers/specs/2026-03-26-minio-integration-design.md](docs/superpowers/specs/2026-03-26-minio-integration-design.md)

## 许可证

Apache License 2.0
