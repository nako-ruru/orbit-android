#!/bin/bash
# 路径：主项目根目录/build-rust.sh

# 1. 配置参数（主项目说了算）
JNI_LIBS_DIR="app-mirror/src/main/jniLibs/arm64-v8a"
ASSETS_DIR="app-mirror/src/assets"
RUST_SRC_DIR="app-mirror/src/main/rust/moonlight-web-stream" # 子模块目录

# 2. 调用子模块编译
echo "Building Rust submodule..."
cd $RUST_SRC_DIR
cargo ndk --platform 33 -t aarch64-linux-android build --release
npm install
npm run build

cd ..

# 3. 执行分发（主项目来搬运）
echo "Distributing .so files..."
mkdir -p $JNI_LIBS_DIR
cp $RUST_SRC_DIR/target/aarch64-linux-android/release/web-server $JNI_LIBS_DIR/libweb-server.so
cp $RUST_SRC_DIR/target/aarch64-linux-android/release/streamer $JNI_LIBS_DIR/libstreamer.so

npm install
npm run build
mkdir -p $ASSETS_DIR/streamer/static
cp -r $RUST_SRC_DIR/dist/* $ASSETS_DIR/streamer/static

echo "Done!"