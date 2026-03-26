#!/bin/bash
set -e

echo "=== Atlas MinIO 集成项目初始化 ==="

# 创建必要的目录
mkdir -p docker/config
mkdir -p atlas-source

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "错误: 未找到 Docker，请先安装 Docker"
    exit 1
fi

# 检查 Docker Compose
if ! command -v docker-compose &> /dev/null; then
    echo "错误: 未找到 docker-compose，请先安装 docker-compose"
    exit 1
fi

echo "✓ Docker 环境检查通过"

# 克隆 Apache Atlas 源码（如果不存在）
if [ ! -d "atlas-source/.git" ]; then
    echo "正在克隆 Apache Atlas 源码..."
    git clone https://github.com/apache/atlas.git atlas-source
    echo "✓ Atlas 源码克隆完成"
else
    echo "✓ Atlas 源码已存在"
fi

echo "=== 初始化完成 ==="
echo "下一步: 运行 ./scripts/build.sh 构建项目"
