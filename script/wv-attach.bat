@echo off
:: 强制当前 CMD 窗口使用 UTF-8 编码，防止中文乱码和解析错误
chcp 65001 >nul
setlocal enabledelayedexpansion

set ADB=d:\Android\Sdk\platform-tools\adb

echo === 正在自动获取 WebView PID ... ===

for /f "tokens=8" %%a in ('%ADB% shell "cat /proc/net/unix | grep webview_devtools_remote_ | grep -v browser"') do (
    set RAW_SOCKET=%%a
)

if not defined RAW_SOCKET (
    echo [错误] 未找到活跃的 WebView 调试套接字！请确保手机屏幕点亮且应用在前台。
    pause
    exit /b
)

set PID=!RAW_SOCKET:@webview_devtools_remote_=!

echo 成功提取目标 PID: !PID!

echo === 开始重置 adb 转发通道 ===
%ADB% forward --remove-all
%ADB% forward tcp:9222 localabstract:webview_devtools_remote_!PID!

echo === [一键完成] 已经稳稳锁定 localhost:9222 ===