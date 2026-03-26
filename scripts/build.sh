#!/bin/bash
set -e

echo "=== 构建 MinIO 集成模块 ==="

# 进入 Atlas 源码目录
cd atlas-source

# 构建 Atlas（跳过测试以加快速度）
echo "正在构建 Apache Atlas..."
mvn clean install -DskipTests

echo "✓ Atlas 构建完成"
echo "下一步: 运行 ./scripts/deploy.sh 部署项目"
