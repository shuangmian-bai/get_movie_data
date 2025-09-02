#!/bin/bash

echo "开始构建自定义数据源实现JAR包..."

# 设置项目路径
PROJECT_PATH=$(cd "$(dirname "$0")" && pwd)

# 创建临时目录用于构建
BUILD_DIR="$PROJECT_PATH/datasource-build"
mkdir -p "$BUILD_DIR"

# 复制必要的源文件到构建目录
echo "复制源文件..."
cp -r "$PROJECT_PATH/src/main/java" "$BUILD_DIR/src/main/java"
cp -r "$PROJECT_PATH/src/main/resources" "$BUILD_DIR/src/main/resources"

# 复制构建配置文件
cp "$PROJECT_PATH/datasource-pom.xml" "$BUILD_DIR/pom.xml"

# 进入构建目录
cd "$BUILD_DIR"

# 使用Maven构建JAR包
echo "正在使用Maven构建JAR包..."
mvn clean package

# 检查构建是否成功
if [ $? -eq 0 ]; then
    echo "构建成功!"
    
    # 将生成的JAR文件复制到libs目录
    echo "复制JAR文件到libs目录..."
    cp target/*.jar ../libs/
    
    echo "自定义数据源实现已成功构建并放入libs目录!"
else
    echo "构建失败，请检查错误信息!"
    exit 1
fi

# 清理临时构建目录
cd "$PROJECT_PATH"
rm -rf "$BUILD_DIR"

echo "构建脚本执行完成!"