@echo off
:: 编码设置为 UTF-8，防止中文提示变成乱码
chcp 65001 >nul

echo ===================================================
echo 🚀 正在配置远程 ADB 端口转发 (A 端 -> B 端)
echo ===================================================

:: 【在这里修改机器 B 的实际 IP】
set TARGET_IP=192.168.3.19

echo ⏳ 1. 正在清理旧的监听规则...
netsh interface portproxy delete v4tov4 listenport=5038 listenaddress=0.0.0.0 >nul 2>&1
netsh interface portproxy delete v6tov4 listenport=5038 listenaddress=:: >nul 2>&1

echo ⏳ 2. 正在重新建立转发隧道 (指向 %TARGET_IP%)...
netsh interface portproxy add v4tov4 listenport=5038 listenaddress=0.0.0.0 connectport=5037 connectaddress=%TARGET_IP%
netsh interface portproxy add v6tov4 listenport=5038 listenaddress=:: connectport=5037 connectaddress=%TARGET_IP%

echo ---------------------------------------------------
echo ✨ [配置成功] 本地 5038 端口已接管远程设备！
echo ---------------------------------------------------
echo 📊 当前处于激活状态的转发规则：
netsh interface portproxy show all

echo.
pause