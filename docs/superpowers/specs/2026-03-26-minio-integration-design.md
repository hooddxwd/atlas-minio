# Apache Atlas 集成 MinIO 设计文档

**日期：** 2026-03-26
**作者：** Generated with Claude Code
**状态：** 已批准

## 1. 项目概述

### 1.1 目标
构建一个增强版的 Apache Atlas，集成 MinIO 元数据管理能力，用于数据治理和元数据管理。

### 1.2 关键需求
- **主要目标：** 数据治理和元数据管理
- **集成方式：** 在 Apache Atlas 项目内部原生集成（非独立应用）
- **部署方式：** Docker Compose 部署完整的 Atlas 技术栈（Atlas、HBase、ZooKeeper、Solr）
- **元数据范围：** 完整元数据提取（基础 + 扩展 + 自定义标签、用户元数据、ACL、版本信息）
- **认证方式：** 静态凭证（Access Key + Secret Key）
- **用户界面：** 扩展 Atlas Web UI，添加 MinIO 管理界面
- **技术栈：** Spring Boot + Vue.js（与 Atlas 保持一致）
- **分类功能：** 手动分类为主，智能自动分类为辅
- **同步策略：** 事件驱动 + 每天凌晨定时同步作为兜底
- **监控：** 通过 Web UI 提供完整的监控面板（同步状态、成功率、失败原因）

## 2. 系统架构

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Docker Compose 环境                        │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │              增强版 Apache Atlas                       │   │
│  │  ┌──────────────────────────────────────────────┐   │   │
│  │  │            Atlas Web UI (Angular)            │   │   │
│  │  │  ┌────────────┐      ┌──────────────┐        │   │   │
│  │  │  │ 原生 UI    │  +   │ MinIO 面板    │        │   │   │
│  │  │  └────────────┘      └──────────────┘        │   │   │
│  │  └──────────────────────────────────────────────┘   │   │
│  │                       ↕ REST API                     │   │
│  │  ┌──────────────────────────────────────────────┐   │   │
│  │  │          Atlas REST API (Jersey)              │   │   │
│  │  │  ┌──────────────┐    ┌──────────────┐        │   │   │
│  │  │  │  原生 API   │  + │ MinIO API    │        │   │   │
│  │  │  └──────────────┘    └──────────────┘        │   │   │
│  │  └──────────────────────────────────────────────┘   │   │
│  │                       ↕                               │   │
│  │  ┌──────────────────────────────────────────────┐   │   │
│  │  │              Atlas 核心层                      │   │   │
│  │  │  ┌──────────────────┐  ┌─────────────────┐   │   │   │
│  │  │  │   类型系统       │  │  图引擎         │   │   │   │
│  │  │  └──────────────────┘  └─────────────────┘   │   │   │
│  │  │  ┌──────────────────┐  ┌─────────────────┐   │   │   │
│  │  │  │   元数据存储     │  │  搜索引擎       │   │   │   │
│  │  │  └──────────────────┘  └─────────────────┘   │   │   │
│  │  └──────────────────────────────────────────────┘   │   │
│  │                       ↕                               │   │
│  │  ┌──────────────────────────────────────────────┐   │   │
│  │  │          MinIO 集成模块                        │   │   │
│  │  │  ┌──────────────┐  ┌──────────────────┐      │   │   │
│  │  │  │MinIO 桥接器  │  │ 事件处理器      │      │   │   │
│  │  │  └──────────────┘  └──────────────────┘      │   │   │
│  │  │  ┌──────────────┐  ┌──────────────────┐      │   │   │
│  │  │  │Hook 系统     │  │ 分类引擎         │      │   │   │
│  │  │  └──────────────┘  └──────────────────┘      │   │   │
│  │  └──────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────┘   │
│                          ↕ S3 协议                          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              远程 MinIO 实例                         │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 部署架构

```
Docker Compose 技术栈:
├── atlas-app (增强版 Apache Atlas)
│   ├── Web UI (Angular + MinIO 扩展)
│   ├── REST API (Jersey + MinIO 端点)
│   ├── 核心 Atlas (图、元数据、搜索)
│   └── MinIO 集成模块
├── hbase (元数据存储)
├── zookeeper (协调服务)
└── solr (搜索索引)
```

## 3. 核心组件

### 3.1 MinIO 集成模块

**包名：** `org.apache.atlas.minio`

#### 3.1.1 MinIO 桥接器
```java
org.apache.atlas.minio.bridge
├── MinIOBridge.java              // 主桥接器入口
├── MinIOClient.java              // MinIO S3 客户端封装
├── MetadataExtractor.java        // 元数据提取器
└── SyncScheduler.java            // 同步调度器
```

**职责：**
- 初始化和管理 MinIO S3 客户端连接
- 执行全量和增量元数据扫描
- 提取完整的 MinIO 元数据（bucket、对象、ACL、版本等）
- 调度定时同步任务（每天凌晨）

#### 3.1.2 事件处理器
```java
org.apache.atlas.minio.event
├── MinIOEventNotifier.java       // 事件通知监听器
├── MinIOEventHandler.java        // 事件处理逻辑
└── EventQueue.java               // 事件队列（异步处理）
```

**职责：**
- 监听 MinIO 事件通知
- 异步处理对象创建、删除、更新事件
- 实时更新 Atlas 中的元数据

#### 3.1.3 分类引擎
```java
org.apache.atlas.minio.classification
├── ClassificationService.java    // 分类服务
├── ManualClassification.java     // 手动分类
├── AutoClassifier.java           // 智能自动分类
└── ClassificationRules.java      // 分类规则引擎
```

**职责：**
- 提供手动分类功能
- 基于文件路径、类型、命名规则的自动分类
- 管理分类标签和规则

### 3.2 Atlas REST API 扩展

**包名：** `org.apache.atlas.web.resources`

扩展现有的 Atlas REST API，添加 MinIO 专用端点：

```java
MinioResource.java                // 新增 REST 资源
```

**新增 API 端点：**
```
POST   /api/atlas/minio/sync              // 手动触发同步
GET    /api/atlas/minio/buckets           // 获取所有 buckets
GET    /api/atlas/minio/buckets/{id}      // 获取 bucket 详情
GET    /api/atlas/minio/objects           // 获取对象列表
GET    /api/atlas/minio/objects/{id}      // 获取对象详情
POST   /api/atlas/minio/classify          // 手动分类
GET    /api/atlas/minio/sync/status       // 获取同步状态
```

### 3.3 Atlas Web UI 扩展

扩展现有的 Angular UI，添加 MinIO 管理界面：

```
web/app/modules/minio/
├── components/
│   ├── minio-dashboard/           // MinIO 仪表板
│   ├── bucket-list/               // Bucket 列表
│   ├── object-browser/            // 对象浏览器
│   ├── classification-manager/    // 分类管理器
│   └── sync-monitor/              // 同步监控面板
├── services/
│   ├── minio.service.ts           // MinIO API 服务
│   └── classification.service.ts  // 分类服务
└── models/
    └── minio.models.ts            // MinIO 数据模型
```

**UI 组件功能：**
- **仪表板：** MinIO 连接状态、bucket 统计、同步状态
- **Bucket 管理：** 浏览所有 bucket，查看详情和分类
- **对象浏览器：** 树形浏览文件结构，查看完整元数据
- **分类管理：** 手动打标签，管理分类规则
- **同步监控：** 查看同步历史、成功率、失败原因

### 3.4 Atlas 类型系统扩展

定义 MinIO 特有的类型系统：

```java
// Atlas 类型定义
MinioBucket {
    name: string                    // 名称
    creationDate: date              // 创建日期
    location: string                // 位置
    owner: string                   // 所有者
    quota: long                     // 配额
    classification: []              // 分类标签
}

MinioObject {
    bucketName: string              // Bucket 名称
    path: string                    // 对象路径
    size: long                      // 对象大小
    contentType: string             // 内容类型
    etag: string                    // ETag
    lastModified: date              // 最后修改时间
    storageClass: string            // 存储类型
    versionId: string               // 版本 ID
    userMetadata: map<string, string>  // 用户元数据
    acl: []                         // 访问控制列表
    classification: []              // 分类标签
}

MinioSyncEvent {
    eventType: string               // 事件类型
    timestamp: date                 // 时间戳
    bucketName: string              // Bucket 名称
    objectPath: string              // 对象路径
    status: string                  // 状态
    errorMessage: string            // 错误信息
}
```

## 4. 数据流和交互

### 4.1 初始化和首次全量同步

```
用户操作:
1. 通过 Web UI 配置 MinIO 连接信息
   └── Endpoint、Access Key、Secret Key

2. 启动增强版 Atlas (Docker Compose)
   └→ Atlas 启动 → MinIO 模块初始化

3. Web UI 点击"首次全量扫描"
   └→ REST API: POST /api/atlas/minio/sync?mode=full

系统处理:
4. MinIOBridge.connect()
   └→ 验证连接，获取 bucket 列表

5. 遍历每个 bucket:
   ├─ 提取 bucket 元数据
   ├─ 创建 Atlas MinioBucket 实体
   └─ 遍历 bucket 中所有对象:
       ├─ 提取完整对象元数据
       ├─ 应用自动分类规则
       └─ 创建 Atlas MinioObject 实体

6. 更新同步状态:
   └→ 记录到 MinioSyncEvent 实体

7. Web UI 显示进度和结果:
   └→ 实时更新进度条，完成后显示统计信息
```

### 4.2 事件驱动实时同步

```
MinIO 事件触发:
1. 用户操作 MinIO (上传/删除/更新对象)
   └→ MinIO 产生事件通知

2. MinIO 事件通知器（配置在 MinIO 端）:
   └→ 发送事件到 Atlas Webhook 端点
   └→ REST API: POST /api/atlas/minio/events

Atlas 处理:
3. MinioEventHandler 接收事件:
   └→ 解析事件类型和元数据

4. 放入异步事件队列:
   └→ 避免阻塞，处理突发流量

5. 事件处理器异步处理:
   ├─ 创建/更新/删除对应的 Atlas 实体
   ├─ 重新应用分类规则
   └─ 记录同步事件

6. Web UI 实时更新:
   └→ 通过轮询或 WebSocket 显示变化
```

### 4.3 定时同步兜底

```
调度器触发:
1. SyncScheduler (每天凌晨 2:00):
   └→ Quartz 定时任务

2. 执行增量同步:
   ├─ 获取上次同步时间戳
   ├─ 查询 MinIO 中变化的对象
   └─ 只处理有变化的对象

3. 或执行全量校验:
   ├─ 对比 Atlas 和 MinIO 的元数据
   ├─ 修复不一致
   └─ 清理 Atlas 中已删除的对象

4. 生成同步报告:
   ├─ 成功/失败数量
   ├─ 处理时间
   └─ 错误详情

5. Web UI 显示报告:
   └→ 在"同步监控"面板可查看
```

### 4.4 手动分类流程

```
用户操作 (Web UI):
1. 浏览到某个对象或 bucket
   └→ 对象浏览器组件

2. 点击"分类"按钮
   └→ 打开分类对话框

3. 选择或创建分类标签:
   ├─ 从现有标签中选择
   ├─ 创建新标签
   └─ 添加自定义属性

4. 保存分类:
   └→ REST API: POST /api/atlas/minio/classify

后端处理:
5. ClassificationService:
   ├─ 验证标签有效性
   ├─ 更新 Atlas 实体的 classification 属性
   └→ 触发分类规则重新评估

6. 返回成功响应:
   └→ Web UI 更新显示
```

### 4.5 智能自动分类流程

```
触发时机:
1. 对象首次导入时
2. 对象元数据更新时
3. 分类规则更新时

分类引擎:
2. AutoClassifier 执行:
   ├─ 路径匹配规则:
   │   └→ /data/production/* → "production_data"
   │   └→ /tmp/* → "temporary_files"
   │   └→ *.backup → "backup"
   ├─ 文件类型规则:
   │   └→ application/pdf → "document"
   │   └→ image/* → "image"
   ├─ 用户元数据规则:
   │   └→ x-amz-meta-sensitive=true → "sensitive_data"
   └─ 自定义规则引擎 (用户可配置)

3. 应用分类结果:
   └→ 自动添加标签到 Atlas 实体

4. 用户可覆盖:
   └→ 手动分类优先级高于自动分类
```

## 5. 错误处理、监控和配置

### 5.1 错误处理策略

#### 5.1.1 连接错误处理
```
MinIO 连接失败:
├─ 重试机制:
│   ├─ 第 1 次: 立即重试
│   ├─ 第 2 次: 5 秒后重试
│   └─ 第 3 次: 30 秒后重试
├─ 超时设置:
│   ├─ 连接超时: 10 秒
│   └─ 读取超时: 60 秒
└─ 失败处理:
    ├─ 记录错误日志
    ├─ 更新连接状态为"离线"
    └─ Web UI 显示错误提示
```

#### 5.1.2 同步错误处理
```
同步过程中失败:
├─ 单个对象失败:
│   ├─ 跳过该对象，继续处理其他对象
│   ├─ 记录失败对象到错误列表
│   └→ 最后生成错误报告
├─ 批量失败:
│   ├─ 暂停同步任务
│   ├─ 保存进度（已处理的对象）
│   └─ 允许从断点续传
└─ 错误分类:
    ├─ 网络错误 → 可重试
    ├─ 权限错误 → 需要用户干预
    ├─ 数据格式错误 → 跳过并记录
    └→ Atlas 内部错误 → 记录并报警
```

#### 5.1.3 事件处理错误
```
事件处理失败:
├─ 事件队列满:
│   ├─ 有界队列设计，限制大小
│   ├─ 队列满时丢弃最旧的事件
│   └─ 记录丢弃的事件
├─ 处理器异常:
│   ├─ 捕获异常，避免线程崩溃
│   ├─ 记录到死信队列
│   └→ 定时重试死信队列中的事件
└─ 幂等性保证:
    └→ 同一事件多次处理结果一致
```

### 5.2 监控面板设计 (Web UI)

#### 5.2.1 MinIO 仪表板
```
┌─────────────────────────────────────────────────────┐
│  MinIO 连接状态                                      │
│  ├─ 状态: 🟢 在线 / 🔴 离线 / 🟡 不稳定              │
│  ├─ Endpoint: https://minio.example.com             │
│  ├─ 最后检查: 2026-03-26 10:30:00                   │
│  └─ 响应时间: 45ms                                   │
├─────────────────────────────────────────────────────┤
│  Bucket 统计                                         │
│  ├─ 总数: 15                                        │
│  ├─ 总大小: 2.3 TB                                  │
│  ├─ 总对象数: 1,234,567                             │
│  └─ 存储趋势 (近 7 天)                               │
├─────────────────────────────────────────────────────┤
│  分类统计                                           │
│  ├─ 生产数据: 45%                                   │
│  ├─ 测试数据: 30%                                   │
│  ├─ 备份数据: 15%                                   │
│  └─ 未分类: 10%                                     │
└─────────────────────────────────────────────────────┘
```

#### 5.2.2 同步监控面板
```
┌─────────────────────────────────────────────────────┐
│  当前同步状态                                        │
│  ├─ 状态: 🔄 同步中 / ✅ 完成 / ⚠️ 失败              │
│  ├─ 模式: 全量扫描 / 增量同步                       │
│  ├─ 进度: 1,234 / 5,000 对象 (24.7%)               │
│  ├─ 已耗时: 00:15:32                                │
│  ├─ 预计剩余: 00:47:18                              │
│  └─ 进度条: [████░░░░░░░░░░░░░░░]                  │
├─────────────────────────────────────────────────────┤
│  最近同步记录                                        │
│  ├─ 2026-03-26 02:00  ✅ 成功  5,234 对象  15分钟   │
│  ├─ 2026-03-25 02:00  ✅ 成功  5,189 对象  14分钟   │
│  ├─ 2026-03-24 02:00  ⚠️ 部分失败  5,100/5,200     │
│  └─ 查看详情 →                                      │
├─────────────────────────────────────────────────────┤
│  事件处理统计 (实时)                                 │
│  ├─ 今日接收: 1,234 事件                           │
│  ├─ 今日处理: 1,230 事件                           │
│  ├─ 处理成功率: 99.7%                               │
│  ├─ 平均延迟: 230ms                                 │
│  └─ 队列积压: 4 事件                                │
└─────────────────────────────────────────────────────┘
```

#### 5.2.3 错误日志面板
```
┌─────────────────────────────────────────────────────┐
│  错误概览                                            │
│  ├─ 今日错误: 23                                    │
│  ├─ 错误率: 0.02%                                   │
│  └─ 严重错误: 2                                     │
├─────────────────────────────────────────────────────┤
│  错误列表                                            │
│  ├─ [10:30] 🔴 连接超时: bucket-123                 │
│  │   └→ 重试中... (2/3)                            │
│  ├─ [10:15] 🟡 权限拒绝: bucket-secret/object       │
│  │   └→ 已跳过，需检查访问控制                     │
│  └─ [09:45] 🟢 已解决: 事件队列满                   │
│       └→ 自动恢复                                   │
├─────────────────────────────────────────────────────┤
│  导出日志 / 清理历史                                 │
└─────────────────────────────────────────────────────┘
```

### 5.3 配置管理

#### 5.3.1 Atlas 配置文件 (application.properties)
```properties
# MinIO 连接配置
atlas.minio.enabled=true
atlas.minio.endpoint=https://minio.example.com
atlas.minio.access.key=${MINIO_ACCESS_KEY}
atlas.minio.secret.key=${MINIO_SECRET_KEY}
atlas.minio.region=us-east-1
atlas.minio.connection.timeout=10000
atlas.minio.socket.timeout=60000

# 同步配置
atlas.minio.sync.enabled=true
atlas.minio.sync.full.initial=true
atlas.minio.sync.schedule.cron=0 0 2 * * ?
atlas.minio.sync.incremental.enabled=true
atlas.minio.sync.batch.size=100
atlas.minio.sync.thread.pool.size=5

# 事件处理配置
atlas.minio.event.enabled=true
atlas.minio.event.queue.capacity=10000
atlas.minio.event.thread.pool.size=3
atlas.minio.event.retry.max=3
atlas.minio.event.retry.delay=5000

# 分类配置
atlas.minio.classification.auto.enabled=true
atlas.minio.classification.rules.path=/etc/atlas/minio-classification-rules.json

# 监控配置
atlas.minio.monitor.metrics.retention.days=30
atlas.minio.monitor.error.retention.days=90
```

#### 5.3.2 环境变量配置 (Docker Compose)
```yaml
environment:
  - MINIO_ENDPOINT=https://minio.example.com
  - MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY}
  - MINIO_SECRET_KEY=${MINIO_SECRET_KEY}
  - ATLAS_MINIO_SYNC_SCHEDULE=0 0 2 * * ?
```

#### 5.3.3 分类规则配置 (JSON)
```json
{
  "rules": [
    {
      "name": "生产数据识别",
      "type": "path",
      "pattern": "/data/production/.*",
      "classification": "production_data",
      "attributes": {
        "environment": "production",
        "criticality": "high"
      }
    },
    {
      "name": "敏感数据识别",
      "type": "metadata",
      "key": "x-amz-meta-sensitive",
      "value": "true",
      "classification": "sensitive_data",
      "attributes": {
        "data_sensitivity": "high",
        "access_control": "restricted"
      }
    },
    {
      "name": "备份数据识别",
      "type": "path",
      "pattern": ".*\\.backup$",
      "classification": "backup",
      "attributes": {
        "retention_days": 90
      }
    }
  ]
}
```

## 6. 项目结构

```
atlas-minio/
├── docker/                                # Docker 配置
│   ├── docker-compose.yml                # 完整的 Atlas + MinIO 环境
│   ├── Dockerfile.atlas                  # 增强版 Atlas 镜像
│   └── config/                           # 配置文件
│       ├── application.properties        # Atlas 配置
│       ├── classification-rules.json     # 分类规则
│       └── log4j.xml                     # 日志配置
│
├── atlas-source/                         # Apache Atlas 源码（克隆）
│   ├── addons/                           # Atlas 原生插件
│   ├── bridge/                           # Atlas Bridge
│   ├── common/                           # 通用模块
│   ├── core/                             # 核心模块
│   ├── server-api/                       # REST API
│   ├── webapp/                           # Web UI (Angular)
│   └── minio-integration/                # 🆕 新增 MinIO 集成模块
│       ├── src/main/java/
│       │   └── org/apache/atlas/minio/
│       │       ├── bridge/
│       │       │   ├── MinIOBridge.java
│       │       │   ├── MinIOClient.java
│       │       │   ├── MetadataExtractor.java
│       │       │   └── SyncScheduler.java
│       │       ├── event/
│       │       │   ├── MinIOEventNotifier.java
│       │       │   ├── MinIOEventHandler.java
│       │       │   └── EventQueue.java
│       │       ├── classification/
│       │       │   ├── ClassificationService.java
│       │       │   ├── ManualClassification.java
│       │       │   ├── AutoClassifier.java
│       │       │   └── ClassificationRules.java
│       │       ├── model/
│       │       │   ├── MinioBucket.java
│       │       │   ├── MinioObject.java
│       │       │   └── MinioSyncEvent.java
│       │       └── utils/
│       │           ├── MinIOConstants.java
│       │           └── MinIOUtils.java
│       └── src/main/resources/
│           ├── spring-minio.xml          # Spring 配置
│           └── minio-models.json         # Atlas 类型定义
│
├── docs/                                 # 文档
│   └── superpowers/
│       └── specs/
│           └── 2026-03-26-minio-integration-design.md
│
├── scripts/                              # 脚本
│   ├── setup.sh                          # 初始化脚本
│   ├── build.sh                          # 构建脚本
│   └── deploy.sh                         # 部署脚本
│
└── README.md                             # 项目说明
```

## 7. 技术栈总结

| 层级          | 技术选型                | 说明                          |
|---------------|-------------------------|-------------------------------|
| 存储后端      | Apache HBase            | Atlas 元数据存储              |
| 协调服务      | Apache ZooKeeper        | 分布式协调                   |
| 搜索引擎      | Apache Solr             | 全文搜索和索引               |
| 应用框架      | Spring Boot             | Java 后端框架                |
| REST API      | Jersey (JAX-RS)         | RESTful API                  |
| Web UI        | Angular 1.x             | 前端框架（Atlas 原生）       |
| S3 客户端     | AWS SDK for Java        | MinIO S3 协议通信            |
| 任务调度      | Quartz Scheduler        | 定时任务调度                 |
| 消息队列      | 内存队列 (Java)         | 事件队列（可升级为 Kafka）   |
| 构建工具      | Maven                   | Java 项目构建                |
| 容器化        | Docker Compose          | 服务编排                     |
| 日志          | Log4j 2                 | 日志管理                     |

## 8. 关键技术决策

### 8.1 为什么在 Atlas 项目内部开发？
- 直接访问 Atlas 内部 API，性能最优
- 无需额外的服务间通信
- 与类型系统集成更紧密
- 便于后期维护和升级

### 8.2 为什么使用 Angular 1.x 而不是现代框架？
- 与现有 Atlas UI 保持一致
- 降低学习和维护成本
- 避免多框架混用的问题

### 8.3 为什么使用内存队列而不是 Kafka？
- 当前阶段单体架构，不需要分布式消息队列
- 简化部署和运维
- 如需扩展可轻松升级到 Kafka

### 8.4 为什么选择 Quartz 而不是 Spring Scheduler？
- Quartz 功能更强大（支持 cron、持久化、集群）
- 企业级任务调度标准
- 与 Spring 集成良好

## 9. 实施阶段

### 第一阶段：基础设施搭建
- Docker Compose 环境配置
- Atlas 源码克隆和构建
- MinIO 测试环境部署

### 第二阶段：MinIO 集成模块开发
- MinIO 客户端封装
- 元数据提取器
- Atlas 类型定义和注册

### 第三阶段：REST API 开发
- MinIO 专用 API 端点
- 同步控制接口
- 分类管理接口

### 第四阶段：Web UI 开发
- MinIO 仪表板
- Bucket 和对象浏览器
- 分类管理界面
- 同步监控面板

### 第五阶段：事件处理和同步
- MinIO 事件监听
- 异步事件处理器
- 定时同步调度

### 第六阶段：分类和智能引擎
- 手动分类功能
- 自动分类规则引擎
- 分类规则管理

### 第七阶段：错误处理和监控
- 错误处理和重试机制
- 监控面板开发
- 日志和报告

### 第八阶段：测试和优化
- 单元测试和集成测试
- 性能优化
- 文档完善

## 10. 成功标准

- [ ] 成功通过 S3 协议连接远程 MinIO 实例
- [ ] 提取完整元数据（buckets、对象、ACL、版本、用户元数据）
- [ ] 使用自定义类型定义将元数据导入 Apache Atlas
- [ ] Web UI 显示 MinIO 数据，支持浏览和搜索
- [ ] 手动分类功能正常，支持标签管理
- [ ] 自动分类根据路径、类型和元数据应用规则
- [ ] 事件驱动实时同步捕获 MinIO 变化
- [ ] 定时每日同步确保数据一致性
- [ ] 监控面板显示同步状态、成功率和错误详情
- [ ] 错误处理包括重试、日志记录和用户通知
- [ ] 所有组件在 Docker Compose 环境中运行

## 附录 A：API 端点参考

### MinIO 管理 API
```
POST   /api/atlas/minio/sync
       - 手动触发同步（全量或增量）
       - 查询参数: mode=full|incremental
       - 响应: SyncJob 状态

GET    /api/atlas/minio/buckets
       - 获取所有 buckets
       - 响应: List<MinioBucket>

GET    /api/atlas/minio/buckets/{id}
       - 获取 bucket 详情及对象
       - 响应: MinioBucket (包含对象)

GET    /api/atlas/minio/objects
       - 获取对象列表（分页）
       - 查询参数: bucket, path, page, size
       - 响应: PagedResult<MinioObject>

GET    /api/atlas/minio/objects/{id}
       - 获取对象完整元数据详情
       - 响应: MinioObject

POST   /api/atlas/minio/classify
       - 为实体应用分类
       - 请求体: entityId, classifications[]
       - 响应: 成功/失败

GET    /api/atlas/minio/sync/status
       - 获取当前同步状态
       - 响应: SyncStatus (当前、历史、统计)

GET    /api/atlas/minio/sync/history
       - 获取同步历史
       - 查询参数: fromDate, toDate, limit
       - 响应: List<MinioSyncEvent>

POST   /api/atlas/minio/events
       - MinIO 事件的 Webhook
       - 请求体: MinIO 事件通知
       - 响应: 202 Accepted
```

## 附录 B：分类规则示例

### 基于路径的规则
```json
{
  "name": "PII 数据识别",
  "type": "path",
  "pattern": "/data/users/.*",
  "classification": "pii_data",
  "attributes": {
    "data_sensitivity": "high",
    "retention_policy": "7_years"
  }
}
```

### 基于元数据的规则
```json
{
  "name": "机密文档",
  "type": "metadata",
  "key": "x-amz-meta-confidential",
  "value": "true",
  "classification": "confidential",
  "attributes": {
    "access_level": "restricted",
    "audit_required": true
  }
}
```

### 基于内容类型的规则
```json
{
  "name": "财务文档",
  "type": "contentType",
  "pattern": "application/vnd.ms-excel.*",
  "classification": "financial_report",
  "attributes": {
    "department": "finance",
    "requires_approval": true
  }
}
```

---

**文档版本：** 1.0
**最后更新：** 2026-03-26
**状态：** 准备编写实现计划
