# 本地快速启动 Atlas - 嵌入式部署指南

> **推荐方案：** 使用 Atlas 自带的 HBase 和 Solr，无需单独安装依赖服务

---

## 📋 前置要求

- ✅ **Java 8+** (已安装 Java 21)
- ✅ **Maven 3.5.0+**
- ✅ **足够的内存** (至少 8GB RAM)
- ✅ **磁盘空间** (至少 5GB)

---

## 🚀 快速启动步骤

### 步骤 1: 配置 Maven 环境变量

由于当前 Maven 配置指向不存在的 JDK 8，需要设置环境变量：

```bash
# 设置 JAVA_HOME 指向 Java 21
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))

# 设置 Maven 内存
export MAVEN_OPTS="-Xms2g -Xmx2g"

# 验证配置
echo "JAVA_HOME=$JAVA_HOME"
java -version
```

**Windows (Git Bash)** 版本：
```bash
export JAVA_HOME=/d/ProgramFiles/Java/jdk-21
export MAVEN_OPTS="-Xms2g -Xmx2g"
```

### 步骤 2: 编译 Atlas（使用嵌入式模式）

```bash
cd atlas-source

# 使用 embedded-hbase-solr profile 编译
mvn clean -DskipTests package -Pdist,embedded-hbase-solr
```

**注意：**
- 编译可能需要 20-30 分钟（首次编译会下载依赖）
- 如果编译中断，重新运行命令即可
- 成功标志：在 `distro/target/` 目录下生成 `apache-atlas-2.4.0-server.tar.gz`

### 步骤 3: 解压编译好的包

```bash
cd distro/target

# 解压服务器包
tar -xzvf apache-atlas-*.tar.gz

# 进入目录
cd atlas-*
```

### 步骤 4: 配置 MinIO 连接

创建或编辑配置文件：

```bash
# 编辑配置文件
vim conf/atlas-application.properties
```

添加 MinIO 配置（到文件末尾）：

```properties
#########  MinIO Integration Configs  #########

# MinIO 连接配置
atlas.minio.enabled=true
atlas.minio.endpoint=http://129.226.204.202:10050
atlas.minio.access.key=admin
atlas.minio.secret.key=password123
atlas.minio.region=us-east-1

# 同步配置
atlas.minio.sync.enabled=true
atlas.minio.sync.full.initial=true
atlas.minio.sync.schedule.cron=0 0 2 * * ?
atlas.minio.sync.incremental.enabled=true

# 事件处理配置
atlas.minio.event.enabled=true
atlas.minio.event.queue.capacity=10000
atlas.minio.event.retry.max=3
```

### 步骤 5: 启动 Atlas（嵌入式模式）

```bash
# 设置使用内嵌的 HBase 和 Solr
export MANAGE_LOCAL_HBASE=true
export MANAGE_LOCAL_SOLR=true

# 启动 Atlas
bin/atlas_start.py
```

**启动标志：**
```
Starting Atlas
...
starting atlas on port 21000
...
Atlas server started successfully!
```

### 步骤 6: 验证 Atlas 启动

```bash
# 等待 1-2 分钟，然后测试
curl -u admin:admin http://localhost:21000/api/atlas/admin/version
```

**预期输出：**
```json
{
  "Description": "Metadata Management and Data Governance Platform over Hadoop",
  "Version": "2.4.0",
  "Name": "apache-atlas"
}
```

### 步骤 7: 访问 Atlas Web UI

在浏览器中打开：

```
http://localhost:21000
```

**登录信息：**
- 用户名: `admin`
- 密码: `admin`

---

## 🎯 测试 MinIO UI

### 1. 访问 MinIO Dashboard

登录后：
1. 点击右上角用户名菜单
2. 选择 **"MinIO"** → **"Dashboard"**
3. 查看连接状态和统计信息

### 2. 测试 MinIO API 连接

在 Atlas UI 中：
1. 进入 MinIO Dashboard
2. 点击 "Test Connection" 按钮
3. 验证是否能连接到 MinIO 服务器 (129.226.204.202:10050)

### 3. 触发首次同步

1. 在 MinIO Dashboard 中点击 "Sync Now"
2. 或访问 MinIO → Sync Monitor
3. 点击 "Sync Now" 或 "Full Sync"

### 4. 查看导入的数据

1. 在 Atlas 全局搜索框中输入 "minio"
2. 查看导入的 buckets 和 objects
3. 点击实体查看详细信息

---

## 🛑 停止 Atlas

```bash
bin/atlas_stop.py
```

这将自动停止：
- Atlas 服务器
- 嵌入式 HBase
- 嵌入式 Solr
- 嵌入式 ZooKeeper

---

## 📊 验证 MinIO 集成

### 检查 API 端点

```bash
# 1. 测试连接
curl -X GET http://localhost:21000/api/atlas/minio/test

# 2. 获取 buckets
curl -X GET http://localhost:21000/api/atlas/minio/buckets

# 3. 获取同步状态
curl -X GET http://localhost:21000/api/atlas/minio/sync/status

# 4. 触发同步
curl -X POST "http://localhost:21000/api/atlas/minio/sync?mode=full"
```

### 查看日志

```bash
# Atlas 主日志
tail -f logs/application.log

# 查看错误
tail -f logs/application.log | grep -i error
```

---

## 🔧 常见问题

### 编译失败

**问题：** Maven 编译报错
```
* MAVEN VERSION ERROR Maven 3.5.0 or above is required.
```

**解决：** 升级 Maven 到 3.5.0+ 版本

---

**问题：** 内存不足
```
Java heap space
```

**解决：** 增加 Maven 内存
```bash
export MAVEN_OPTS="-Xms4g -Xmx4g"
```

---

### 启动失败

**问题：** 端口 21000 被占用

**解决：**
```bash
# Windows
netstat -ano | findstr :21000
taskkill /F /PID <PID>

# Linux/Mac
lsof -i :21000
kill -9 <PID>
```

---

**问题：** HBase/Solr 启动失败

**解决：** 检查日志
```bash
cat logs/application.log | grep -i "hbase\|solr"
```

确保设置了环境变量：
```bash
echo $MANAGE_LOCAL_HBASE  # 应该是 true
echo $MANAGE_LOCAL_SOLR   # 应该是 true
```

---

### MinIO 连接失败

**问题：** 无法连接到 MinIO

**解决：**
1. 检查 MinIO 服务器是否运行：
```bash
curl http://129.226.204.202:10050
```

2. 检查配置文件中的连接信息

3. 查看日志：
```bash
tail -f logs/application.log | grep -i minio
```

---

## 📝 下一步

启动成功后，你可以：

1. **配置分类规则** - 编辑 `conf/classification-rules.json`
2. **测试事件处理** - 配置 MinIO webhook
3. **性能调优** - 调整 JVM 参数和缓存设置
4. **生产部署** - 使用独立部署方式（外部 HBase/Solr）

---

## 📚 参考资源

- **官方文档：** http://atlas.apache.org
- **本文项目：** [atlas-minio](https://github.com/hooddxwd/atlas-minio)
- **CSDN 教程：** [Apache Atlas 2.0 安装部署详解](https://blog.csdn.net/weixin_39128926/article/details/107016683)

---

## ⚡ 快速命令参考

```bash
# 1. 编译
cd atlas-source
export MAVEN_OPTS="-Xms2g -Xmx2g"
mvn clean -DskipTests package -Pdist,embedded-hbase-solr

# 2. 解压
cd distro/target
tar -xzvf apache-atlas-*.tar.gz
cd atlas-*

# 3. 配置 MinIO（编辑 conf/atlas-application.properties）

# 4. 启动
export MANAGE_LOCAL_HBASE=true
export MANAGE_LOCAL_SOLR=true
bin/atlas_start.py

# 5. 访问
http://localhost:21000
用户名/密码: admin/admin

# 6. 停止
bin/atlas_stop.py
```

---

**版本：** 1.0.0
**最后更新：** 2026-03-27
**适用于：** Apache Atlas 2.4.0 + MinIO 集成
