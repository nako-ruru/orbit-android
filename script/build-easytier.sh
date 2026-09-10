#!/bin/bash
set -x

# 1. 定义初始相对路径
SOURCE_PATH="app-mirror/src/main/rust/easytier"
BUILD_PATH="$HOME/build/easytier"

cd "$(dirname "$0")/.." || exit

# 2. 【关键】在 cd 之前，把相对路径转为绝对路径
BASE_PATH=$(pwd)

# 3. 准备 Home 环境并同步
mkdir -p "$BUILD_PATH"
# 清理目标路径（保留 target 缓存）
find "$BUILD_PATH" -maxdepth 1 ! -name 'target' ! -name "$(basename "$BUILD_PATH")" ! -name '.' -exec rm -rf {} +
# 【核心：原地同步】
tar -cC "$SOURCE_PATH" --exclude='target' --exclude='.git' . | tar -xC "$BUILD_PATH"

# 4. 进场构建
cd "$BUILD_PATH" || exit

cargo ndk --platform 26 -t aarch64-linux-android build --release -p easytier-android-jni -p easytier-ffi || { echo "Cargo build failed"; exit 1; }

# 5. 使用绝对路径拷回产物
JNI_LIBS_PATH=$BASE_PATH/app-mirror/src/main/jniLibs
mkdir -p "$JNI_LIBS_PATH/arm64-v8a"
cp "target/aarch64-linux-android/release/libeasytier_android_jni.so" "$JNI_LIBS_PATH/arm64-v8a/"
 cp "target/aarch64-linux-android/release/libeasytier_ffi.so" "$JNI_LIBS_PATH/arm64-v8a/"

# 6. 复制 Kotlin 源码
KOTLIN_SRC_PATH="$BUILD_PATH/easytier-contrib/easytier-android-jni/kotlin/com/easytier/jni"
ANDROID_JAVA_PATH="$BASE_PATH/app-mirror/src/main/java/com/easytier/jni"
mkdir -p "$ANDROID_JAVA_PATH"
cp "$KOTLIN_SRC_PATH/EasyTierJNI.kt" "$ANDROID_JAVA_PATH/"
