# Apache Atlas 源码下载指南

由于网络限制无法直接从 GitHub 克隆，请使用以下替代方案：

## 方案 1: 使用浏览器下载（推荐）

### 步骤：

1. **访问 Apache Atlas 发布页面**
   - 直接访问：https://atlas.apache.org/downloads/
   - 或 GitHub Release：https://github.com/apache/atlas/releases

2. **下载源码包**
   - 下载最新版本（如 2.3.0）：apache-atlas-2.3.0-sources.zip
   - 或从 Apache Archive：https://archive.apache.org/atlas/

3. **解压到项目目录**
   ```bash
   # Windows (使用 7zip 或 WinRAR)
   # 解压到 E:\claude code projects\atlas-minio\
   
   # 或使用命令行
   unzip apache-atlas-2.3.0-sources.zip -d atlas-source
   ```

---

## 方案 2: 使用镜像站点

### GitHub 镜像
```bash
# 尝试使用 GitHub 镜像（如果可访问）
git clone https://gitee.com/mirrors/atlas.git atlas-source
```

### Apache 镜像
```bash
# 从国内 Apache 镜像下载
wget https://mirrors.tuna.tsinghua.edu.cn/apache/atlas/apache-atlas-2.3.0-sources.zip
unzip apache-atlas-2.3.0-sources.zip -d atlas-source
```

---

## 方案 3: 配置代理

### 如果您有 HTTP/HTTPS 代理

```bash
# 配置 Git 使用代理
git config --global http.proxy http://proxy-server:port
git config --global https.proxy https://proxy-server:port

# 然后克隆
git clone https://github.com/apache/atlas.git atlas-source

# 克隆完成后移除代理配置
git config --global --unset http.proxy
git config --global --unset https.proxy
```

---

## 方案 4: 使用 SSH 协议

```bash
# 如果您配置了 SSH 密钥
git clone git@github.com:apache/atlas.git atlas-source
```

---

## 方案 5: 手动导入

如果您有其他方式获取 Atlas 源码（如公司内部镜像、U盘拷贝等）：

1. 将源码放到 `atlas-source` 目录
2. 确保目录结构为：
   ```
   atlas-source/
   ├── addons/
   ├── bridge/
   ├── ...
   └── pom.xml (根 POM 文件)
   ```

---

## 下载完成后

### 下一步操作：

1. **验证下载**
   ```bash
   cd atlas-source
   ls -la
   # 应该看到 pom.xml 和其他目录
   ```

2. **将我们的 MinIO 集成模块集成**
   ```bash
   # 将我们的模块复制到 Atlas addons/ 目录
   cp -r atlas-source-backup/minio-integration atlas-source/addons/
   
   # 或直接在当前位置继续开发
   ```

3. **构建和测试**
   ```bash
   cd atlas-source
   mvn clean install -DskipTests
   ```

---

## 推荐方案

**最快方案：** 方案 1（浏览器下载）
**最稳定方案：** 方案 2（镜像站点）
**开发推荐：** 方案 3（配置代理，如果您的环境支持）

请选择适合您网络环境的方案。
