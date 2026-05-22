#!/bin/bash
set -e

# 🎯 ORBIT 纯权限精准撤销脚本 (版本 5.0 终极强杀组件版)
# 绝对保全数据！通过临时钝化组件逼出 VPN 红灯
ADB_PATH="/mnt/d/Android/Sdk/platform-tools/adb.exe"
PKG="com.orbit"

# ⚠️ 请在此处填入你 AndroidManifest.xml 中真正的 VpnService 类名（带点或全路径）
# 比如 ".service.OrbitVpnService" 或 ".VpnServiceCore"
VPN_COMPONENT=".VpnServiceCore"

echo "⚡ 正在通过组件重置强行扒掉 $PKG 的 VPN 凭证（数据完好无损）..."

# ==========================================
# 【核心大招】重置 VPN 组件状态 (vpn)
# ==========================================
$ADB_PATH shell pm disable $PKG/$PKG$VPN_COMPONENT 2>/dev/null || true
$ADB_PATH shell pm enable $PKG/$PKG$VPN_COMPONENT 2>/dev/null || true
$ADB_PATH shell am force-stop $PKG

# ==========================================
# 2. 撤销电池白名单 (power)
# ==========================================
set +e
$ADB_PATH shell dumpsys deviceidle whitelist -$PKG 2>/dev/null
$ADB_PATH shell appops set $PKG RUN_IN_BACKGROUND ignore 2>/dev/null
$ADB_PATH shell appops set $PKG REQUEST_IGNORE_BATTERY_OPTIMIZATIONS ignore 2>/dev/null
set -e

# ==========================================
# 3. 其他核心权限继续精准点杀
# ==========================================
$ADB_PATH shell pm revoke $PKG android.permission.RECORD_AUDIO 2>/dev/null || true
$ADB_PATH shell pm revoke $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
$ADB_PATH shell appops set $PKG SYSTEM_ALERT_WINDOW ignore 2>/dev/null || true
$ADB_PATH shell appops set $PKG MANAGE_EXTERNAL_STORAGE deny 2>/dev/null || true
$ADB_PATH shell appops set $PKG SET_ALARM_CLOCK deny 2>/dev/null || true
$ADB_PATH shell settings put secure enabled_accessibility_services "\"\"" 2>/dev/null || true
$ADB_PATH shell settings put secure accessibility_enabled 0 2>/dev/null || true

echo "✨ [终极撤销完毕] 再次去刷新你的向导网页，VPN 应该已经被彻底打回原形了！"