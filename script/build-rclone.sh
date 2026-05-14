#!/bin/bash
# -e: 遇到错误立即停止; -x: 打印执行的命令
set -ex

# 1. 定义路径
SOURCE_PATH="app-mirror/src/main/go/rclone"
# 锁定项目根目录的绝对路径
cd "$(dirname "$0")/.." || exit
PROJECT_ROOT=$(pwd)

# 2. 执行 Python 裁剪 (传入相对路径即可，Python 内部已做 join)
python3 script/prune_rclone.py "$SOURCE_PATH"

# 3. 进入源码目录
cd "$SOURCE_PATH" || exit

# 4. 配置 Android 交叉编译环境变量
# 注意：你需要确保 $ANDROID_NDK_HOME 已设置，或者直接写死 NDK 路径
export GOOS=android
export GOARCH=arm64
export CGO_ENABLED=0

# 建议：明确指定 CC 编译器，否则 CGO 可能无法通过编译
# 这里的路径根据你的 NDK 版本可能略有不同（通常在 toolchains 目录下）
# export CC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang"

# 5. 执行编译
# -buildmode=c-shared 是必须的，否则产出的是可执行文件而不是 .so
# -o 后面使用根目录的绝对路径，确保产物位置精准
go build -v -o "$PROJECT_ROOT/app-mirror/src/main/jniLibs/arm64-v8a/librclone.so" .

git checkout "backend/all/all.go" "cmd/all/all.go"

echo "Build Success: librclone.so has been generated."