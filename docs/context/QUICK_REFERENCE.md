# Quick Reference - 继续开发指南

## 🚀 在新 Claude Code 会话中开始

### 第一步：告诉 Claude Code

```
继续开发 atlas-minio 项目（Apache Atlas 集成 MinIO）。

项目位置：e:\claude code projects\atlas-minio
GitHub：https://github.com/hooddxwd/atlas-minio

请阅读 docs/context/ 目录了解项目状态。

下一步：推送代码到 GitHub，然后实现 Web UI（Angular）
```

---

## 📋 项目状态速览

**完成度：** 78.6% (11/14 任务)
**后端：** ✅ 100% 完成
**前端：** ⏳ 待开发（Angular）

**已完成：**
- MinIOBridge, SyncScheduler, 事件处理
- 完整分类系统（手动 + 自动）
- 8 个 REST API 端点
- Atlas 类型定义
- 3,500+ 行测试代码

**剩余任务：**
1. Web UI (Angular)
2. 集成测试
3. 部署优化

---

## 🔑 关键配置

### MinIO 服务器
```
URL: http://129.226.204.202:10050
Access Key: admin
Secret Key: password123
Region: us-east-1
```

### 本地开发
```
Atlas Web UI: http://localhost:21000
用户名/密码: admin/admin
```

---

## 📁 重要文件位置

### REST API
```
atlas-source/minio-integration/src/main/java/org/apache/atlas/minio/rest/MinioResource.java
```

### 主桥接器
```
atlas-source/minio-integration/src/main/java/org/apache/atlas/minio/bridge/MinIOBridge.java
```

### 配置文件
```
docker/config/application.properties
```

### 设计文档
```
docs/superpowers/specs/2026-03-26-minio-integration-design.md
```

---

## 🛠️ 常用命令

### 构建
```bash
cd atlas-source/minio-integration
mvn clean install
```

### 测试
```bash
mvn test
```

### 启动 Atlas
```bash
cd docker
docker-compose up -d
```

### 测试 API
```bash
# 测试连接
curl -X GET http://localhost:21000/api/atlas/minio/test

# 触发同步
curl -X POST "http://localhost:21000/api/atlas/minio/sync?mode=full"

# 列出 buckets
curl -X GET http://localhost:21000/api/atlas/minio/buckets
```

---

## 🎯 下一个任务：Web UI

### 位置
```
atlas-source/dashboardv3/src/main/java/org/apache/atlas/dashboard/
```

### 需要创建的组件
1. MinIO Dashboard（统计概览）
2. Bucket Browser（bucket 列表）
3. Object Browser（对象列表）
4. Sync Monitor（同步状态）
5. Classification UI（分类管理）

### 技术栈
- Angular 1.x（Atlas 原生）
- AngularJS Material
- Atlas REST API

---

## ⚠️ 已知问题和解决方案

### 1. SimpleDateFormat 线程安全
**问题：** 并发同步可能数据损坏
**修复：** 使用 DateTimeFormatter 替换

### 2. 增量同步内存
**问题：** 大 bucket 导致 OOM
**修复：** 实现分页处理

---

## 📊 代码统计

- 总代码：~10,000+ 行
- Java 类：27 个
- 测试类：10 个
- 提交：17+ 次

---

## 💡 开发建议

1. **先测试再开发** - 确保 REST API 正常工作
2. **增量实现** - UI 组件一个一个来
3. **频繁提交** - 每完成一个功能就提交
4. **查看设计** - 参考设计文档

---

**最后更新：** 2026-03-26 23:00

**快速链接：**
- [完整状态](README.md) - 此文件
- [使用指南](../../DEVELOPMENT_COMPLETE.md) - 详细文档
- [设计文档](../superpowers/specs/2026-03-26-minio-integration-design.md) - 原始设计
