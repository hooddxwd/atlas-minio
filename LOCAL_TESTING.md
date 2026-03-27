# 本地启动 Atlas - 测试 MinIO UI

## 方案选择

由于 Atlas 需要 HBase、Solr、ZooKeeper 等依赖服务，本地完整启动比较复杂。我提供以下几种方案：

### 🎯 方案一：快速 UI 预览（推荐用于快速测试）

直接在浏览器中查看 UI 效果，不需要启动 Atlas。

**优点**：
- 无需任何依赖
- 立即可用
- 可以查看所有 UI 页面

**限制**：
- 没有 Atlas 后端，数据是模拟的
- 无法测试真实的 API 调用

### 🏗️ 方案二：完整本地启动（生产环境）

启动完整的 Atlas 技术栈。

**要求**：
- Java 8+ (✅ 已安装 Java 21)
- Maven 3.x
- 足够的内存 (至少 8GB RAM)
- HBase、Solr、ZooKeeper

## 方案一：快速 UI 预览

### 1. 创建本地测试服务器

使用 Python 简单的 HTTP 服务器：

```bash
# 进入 dashboard 目录
cd atlas-source/dashboardv3/public

# 启动 HTTP 服务器
python -m http.server 8080
```

### 2. 访问 UI

在浏览器中打开：

```
http://localhost:8080/index.html
```

### 3. 查看 MinIO 页面

直接访问以下 URL（在浏览器地址栏输入）：

```
http://localhost:8080/
```

然后点击用户名菜单 → MinIO → Dashboard

## 方案二：完整本地启动

### 步骤 1: 修复 Maven 配置

当前 Maven 指向不存在的 JDK 8，需要更新：

```bash
# 编辑 ~/.m2rc 或 MAVEN_HOME/conf/settings.xml
# 设置 JAVA_HOME 环境变量
export JAVA_HOME=/path/to/java21
export PATH=$JAVA_HOME/bin:$PATH
```

### 步骤 2: 安装依赖服务

#### 选项 A: 使用 Docker（推荐）

```bash
# 启动 HBase, Solr, ZooKeeper
cd docker
docker-compose up -d zookeeper hbase solr

# 等待服务启动（约 2-3 分钟）
docker-compose logs -f
```

#### 选项 B: 手动安装

需要单独安装并启动：
- ZooKeeper (端口 2181)
- HBase (端口 16010, 16000)
- Solr (端口 8983)

### 步骤 3: 构建 Atlas

```bash
cd atlas-source

# 清理并构建（跳过测试以加快速度）
mvn clean install -DskipTests

# 或者只构建 webapp 模块
cd webapp
mvn clean package -DskipTests
```

### 步骤 4: 配置 Atlas

创建本地配置文件 `atlas-source/target/classes/application.properties`:

```properties
# Atlas 基础配置
atlas.rest.address=http://localhost:21000

# HBase 配置
atlas.graph.storage.backend=hbase
atlas.graph.storage.hostname=localhost
atlas.graph.storage.hbase.regionsperserver=1

# Solr 配置
atlas.graph.index.search.solr.wait-searcher=true
atlas.graph.index.search.backend=solr
atlas.graph.index.search.solr.mode=cloud
atlas.graph.index.search.solr.zookeeper-url=localhost:2181

# 服务器配置
atlas.server.bind.address=0.0.0.0
atlas.server.port=21000

# MinIO 集成配置
atlas.minio.enabled=true
atlas.minio.endpoint=http://129.226.204.202:10050
atlas.minio.access.key=admin
atlas.minio.secret.key=password123
atlas.minio.region=us-east-1
```

### 步骤 5: 启动 Atlas

```bash
# 方式 1: 使用 Maven Jetty 插件
cd atlas-source/webapp
mvn jetty:run -Djetty.port=21000

# 方式 2: 使用构建好的 war 包
cd atlas-source/webapp/target
java -jar atlas-webapp-*.jar
```

### 步骤 6: 访问 Atlas

打开浏览器访问：

```
http://localhost:21000
```

默认登录：
- 用户名: admin
- 密码: admin

## 方案三：使用已编译的二进制文件

如果有预编译的 Atlas war 包：

```bash
# 下载 Apache Atlas 2.4.0
wget https://downloads.apache.org/atlas/2.4.0/apache-atlas-2.4.0-bin.tar.gz

# 解压
tar -xzf apache-atlas-2.4.0-bin.tar.gz
cd apache-atlas-2.4.0

# 启动（需要 HBase 和 Solr）
bin/atlas_start.py
```

## 当前推荐方案

基于你当前的环境，我推荐：

**立即查看 UI 效果**（方案一）：

```bash
cd atlas-source/dashboardv3/public
python -m http.server 8080
```

然后访问：http://localhost:8080/

---

**需要完整功能？**

如果需要测试完整的 MinIO 集成功能（API 调用、数据展示等），建议使用 Docker 方式：

```bash
cd docker
docker-compose up -d
```

这样可以一键启动完整的 Atlas 技术栈。

## 故障排查

### Maven 构建失败

```bash
# 清理 Maven 缓存
rm -rf ~/.m2/repository

# 重新构建
mvn clean install -U -DskipTests
```

### 端口冲突

如果 21000 端口被占用：

```bash
# Windows
netstat -ano | findstr :21000
taskkill /F /PID <PID>

# Linux/Mac
lsof -i :21000
kill -9 <PID>
```

### HBase 连接失败

确保 HBase 已启动：

```bash
docker-compose logs hbase
curl http://localhost:16010
```

## 下一步

选择一个方案后，我可以帮你：
1. 配置具体的启动命令
2. 调试启动问题
3. 测试 MinIO UI 功能

你想使用哪个方案？
