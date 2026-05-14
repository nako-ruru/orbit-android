#!/bin/bash
# -e: 遇到错误立即停止; -x: 打印执行的命令
set -ex

ORBIT_VERSION=v0.5.1
NPS_VERSION=v0.34.7

JNI_LIBS_DIR="app-mirror/src/main/jniLibs/arm64-v8a"

# 锁定项目根目录的绝对路径
cd "$(dirname "$0")/.." || exit

mkdir -p "app-mirror/libs/"
curl -L -o "app-mirror/libs/orbit.aar" https://gh-releases-mirror.guanghe.asia/nako-ruru/orbit-release/releases/download/${ORBIT_VERSION}/orbit.aar


NPC_TEMP_FILE="/tmp/android_libs_client.tar.gz"
echo "Downloading NPC library from GitHub..."
# 2. 下载文件 (带重试机制，防止网络抖动)
curl -L --retry 3 "https://gh-releases-mirror.guanghe.asia/djylb/nps/releases/download/${NPS_VERSION}/android_libs_client.tar.gz" -o "$NPC_TEMP_FILE"
# 3. 提取指定的 .so 文件
# --strip-components=2 作用：直接去掉压缩包里的多级目录结构
# 假设压缩包结构是 android_libs_client/arm64-v8a/libnpc.so
# 我们只想要最后的 .so
echo "Extracting libnpc.so..."
# 注意：tar 命令在不同系统下路径处理略有差异，
# 这里直接指定要提取的文件路径，然后重定向到目标位置
tar -xzf "$NPC_TEMP_FILE" -C "$JNI_LIBS_DIR" --strip-components=1 "arm64-v8a/libnpc.so"

echo "Success! libnpc.so is now in $JNI_LIBS_DIR"
