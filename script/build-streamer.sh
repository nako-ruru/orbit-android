#!/bin/bash
set -x

# 1. 定义初始相对路径
SOURCE_PATH="app-mirror/src/main/rust/moonlight-web-stream"
BUILD_PATH="$HOME/build/moonlight-web-stream"

cd "$(dirname "$0")/.." || exit

# 2. 【关键】在 cd 之前，把相对路径转为绝对路径
# readlink -f 能拿到 D 盘挂载点在 WSL 里的完整绝对路径
BASE_PATH=$(pwd)

# 3. 准备 Home 环境并同步
mkdir -p "$BUILD_PATH"
# 清理目标路径（保留 target 缓存），注意这里要在 BUILD_PATH 目录下操作
find "$BUILD_PATH" -maxdepth 1 ! -name 'target' ! -name "$(basename "$BUILD_PATH")" ! -name '.' -exec rm -rf {} +
# 【核心：原地同步】
# 1. -C 指定进入源码目录
# 2. --exclude 过滤垃圾
# 3. . 代表目录下的所有内容
# 4. 后面接的 tar -x -C 指定解压到目标绝对路径
tar -cC "$SOURCE_PATH" --exclude='node_modules' --exclude='target' --exclude='dist' . | tar -xC "$BUILD_PATH"
# 4. 进场构建
cd "$BUILD_PATH" || exit

# 4. 进场构建
cargo ndk --platform 33 -t aarch64-linux-android build --release || { echo "Cargo build failed"; exit 1; }
npm install && npm run build || { echo "NPM build failed"; exit 1; }

# 5. 使用绝对路径拷回产物，完全不用担心上下文丢失
JNI_LIBS_PATH=$BASE_PATH/app-mirror/src/main/jniLibs
mkdir -p "$JNI_LIBS_PATH"
cp "target/aarch64-linux-android/release/web-server" "$JNI_LIBS_PATH/arm64-v8a/libweb-server.so"
cp "target/aarch64-linux-android/release/streamer" "$JNI_LIBS_PATH/arm64-v8a/libstreamer.so"
ASSETS_PATH=$BASE_PATH/app-mirror/src/main/assets
mkdir -p "$ASSETS_PATH/streamer/static"
tar -cf - -C dist . | tar -xf - -C "$ASSETS_PATH/streamer/static"