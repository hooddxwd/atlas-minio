#!/bin/bash
set -e

echo "=== 部署 MinIO 集成环境 ==="

# 检查配置文件
if [ ! -f "docker/config/application.properties" ]; then
    echo "错误: 未找到配置文件 docker/config/application.properties"
    echo "请先配置 MinIO 连接信息"
    exit 1
fi

# 启动 Docker Compose
echo "正在启动 Docker Compose 环境..."
cd docker
docker-compose up -d

echo "✓ 部署完成"
echo "Atlas Web UI: http://localhost:21000"
echo "用户名: admin"
echo "密码: admin"
