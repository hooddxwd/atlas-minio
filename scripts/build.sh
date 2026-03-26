#!/bin/bash
set -e

echo "=== 构建 MinIO 集成模块 ==="

# 检查源码目录
if [ ! -d "atlas-source" ]; then
    echo "错误: 未找到 atlas-source 目录"
    echo "请先运行 ./scripts/setup.sh"
    exit 1
fi

# 进入 Atlas 源码目录
cd atlas-source

# 构建 Atlas（跳过测试以加快速度）
echo "正在构建 Apache Atlas..."
echo "预计需要 5-10 分钟，请耐心等待..."
mvn clean install -DskipTests

echo "✓ Atlas 构建完成"
echo "下一步: 运行 ./scripts/deploy.sh 部署项目"
