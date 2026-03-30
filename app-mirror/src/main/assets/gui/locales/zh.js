window.i18nData["zh"] = {
    "app.title": "远程桌面",

    "tab.remote": "远程连接",
    "tab.myDevices": "我的设备",
    "tab.activeConnections": "活动连接",
    "tab.history": "历史记录",
    "tab.proxySettings": "代理设置",
    "tab.androidPermissions": "Android权限",

    // Remote Connection Tab
    "remote.myDevices.title": "我的设备",
    "remote.myDevices.deviceId.label": "设备编号",
    "remote.myDevices.accessPassword.label": "访问密码",
    "remote.myDevices.password.show": "显示",
    "remote.myDevices.password.hide": "隐藏",
    "remote.myDevices.settings.button": "设置",
    "remote.myDevices.newPassword.button": "新密码",
    "remote.connect.title": "连接到其他设备",
    "remote.connect.remoteId.label": "对方设备编号",
    "remote.connect.remoteId.placeholder": "输入对方设备编号",
    "remote.connect.password.label": "连接密码",
    "remote.connect.password.placeholder": "6位密码",
    "remote.connect.control.button": "远程控制",
    "remote.connect.fileTransfer.button": "文件传输",

    // My Devices Tab (Logged Out)
    "myDevices.login.title": "登录以查看设备",
    "myDevices.login.description": "登录后可以查看和管理您的所有设备",
    "myDevices.login.button": "登录账户",

    // My Devices Tab (Logged In)
    "myDevices.header.title": "我的设备",
    "myDevices.header.onlineBadge": "{{count}}台设备在线",
    "myDevices.header.logout.button": "登出",

    // Active Connections Tab
    "activeConnections.header.title": "活动连接",
    "activeConnections.header.badge": "{{count}}个活跃连接",

    // History Tab
    "history.header.title": "历史记录",

    // Proxy Settings Tab
    "proxy.header.title": "代理设置",
    "proxy.option.none.label": "不使用代理",
    "proxy.option.none.description": "直接连接到远程设备，不使用任何代理服务器",
    "proxy.option.system.label": "跟随系统",
    "proxy.option.system.description": "使用系统设置的代理配置",
    "proxy.option.custom.label": "指定代理",
    "proxy.option.custom.description": "手动配置代理服务器地址和端口",
    "proxy.details.type.label": "代理类型",
    "proxy.details.type.http": "HTTP",
    "proxy.details.type.socks5": "SOCKS5",
    "proxy.details.host.label": "代理服务器地址",
    "proxy.details.host.placeholder": "例如: 192.168.1.100 或 proxy.example.com",
    "proxy.details.port.label": "代理端口",
    "proxy.details.port.placeholder": "例如: 8080",
    "proxy.details.username.label": "用户名 (可选)",
    "proxy.details.username.placeholder": "代理服务器用户名",
    "proxy.details.password.label": "密码 (可选)",
    "proxy.details.password.placeholder": "代理服务器密码",
    "proxy.test.title": "测试连接",
    "proxy.test.url.label": "测试连接地址",
    "proxy.test.url.placeholder": "输入要测试的URL地址",
    "proxy.test.button": "测试连接",
    "proxy.save.button": "保存设置",

    // Status Bar
    "statusBar.ready": "状态：就绪",
    "statusBar.lastUpdate": "最后更新：刚刚",

    // Custom Confirm Dialog
    "dialog.confirm.title": "确认操作",
    "button.cancel": "取消",
    "button.confirm": "确定",

    // 添加到现有zh.js中
    "remote.connect.error.idPasswordRequired": "请输入设备编号和密码",
    "remote.connect.connecting": "正在连接到设备...",
    "remote.myDevices.password.generated": "已生成新密码",
    "myDevices.empty.title": "暂无设备",
    "myDevices.empty.description": "添加设备以开始远程控制",
    "myDevices.lastSeen": "最后活动",
    "myDevices.status.self": "本设备",
    "myDevices.status.online": "在线",
    "myDevices.status.offline": "离线",
    "myDevices.action.connect": "连接",
    "myDevices.action.fileTransfer": "文件传输",
    "activeConnections.type.remoteControl": "远程控制会话",
    "activeConnections.type.fileTransfer": "文件传输会话",
    "activeConnections.connectionTime": "连接时间",
    "activeConnections.justNow": "刚刚",
    "activeConnections.dataTransfer": "数据传输",
    "activeConnections.realTime": "实时",
    "activeConnections.status.controlling": "控制中",
    "activeConnections.status.transferring": "传输中",
    "activeConnections.action.switch": "切换至",
    "activeConnections.action.disconnect": "断开",
    "activeConnections.confirm.close": "确定要关闭连接 {{id}} 吗？",
    "activeConnections.status.connectionClosed": "连接已关闭",
    "history.type.remoteControl": "远程控制",
    "history.type.fileTransfer": "文件传输",
    "history.connectionTime": "连接时间",
    "history.duration": "持续时间",
    "history.status.ended": "已结束",
    "history.action.view": "查看",
    "history.action.viewing": "正在查看历史记录 {{id}}...",
    "history.empty.title": "暂无历史记录",
    "history.empty.description": "连接过的设备将显示在这里",
    "proxy.save.error": "请填写代理服务器地址和端口",
    "proxy.save.success": "代理设置已保存",
    "proxy.test.testing": "正在测试代理连接...",
    "proxy.test.hostPortRequired": "请填写代理服务器地址和端口",
    "proxy.test.urlRequired": "请填写要测试的URL地址",
    "proxy.test.testingWithUrl": "正在通过代理测试连接: ",
    "proxy.test.success": "代理连接测试成功！连接正常。",
    "statusBar.loggedIn": "已登录",
    "statusBar.loggedOut": "已登出",
    "statusBar.status": "状态：{{text}}",
    "dialog.confirm.content": "确定要执行此操作吗？",
    "remote.myDevices.settings.message": "打开设置...",


    "android.permissions.header": "⚙️ 无人值守授权配置",
    "android.permissions.description": "请确保以下所有权限均显示为\"已开启\"，否则受控端无法正常工作",
    "android.permissions.action.go": "去授权",
    "android.permissions.action.ok": "已开启",

    core_screen_name: "核心投屏功能",
    core_screen_desc: "必须开启以下权限，否则应用无法正常工作。",

    interface_monitor_name: "界面交互与监控",
    interface_monitor_desc: "可选权限，用于增强交互和监控设备状态。",

    keepalive_name: "进程保活与自动重启",
    keepalive_desc: "确保应用持续运行并可自动重启的权限。",

    perm_mic_name: "录音/麦克风",
    perm_mic_desc: "用于远程捕捉环境声音。",

    perm_projection_name: "投屏",
    perm_projection_desc: "用于将设备屏幕共享到远程会话。",

    perm_vpn_name: "VPN 转发",
    perm_vpn_desc: "用于内网穿透和稳定的数据传输。",

    perm_accessibility_name: "无障碍服务",
    perm_accessibility_desc: "用于模拟点击和手势操作，实现核心控制。",

    perm_overlay_name: "悬浮窗权限",
    perm_overlay_desc: "允许在后台显示悬浮界面并与用户交互。",

    perm_notification_name: "通知监听",
    perm_notification_desc: "监控设备通知，了解状态变化。",

    perm_files_name: "文件访问",
    perm_files_desc: "允许选择和管理设备文件。",

    perm_autostart_name: "自启动权限",
    perm_autostart_desc: "确保应用在重启、关机后自动启动，否则需要手动开启。",

    perm_popup_name: "后台弹出",
    perm_popup_desc: "允许应用在后台弹出窗口或提示。",

    perm_power_name: "省电策略白名单",
    perm_power_desc: "防止系统后台休眠应用，保持持续运行。",

    perm_alarm_name: "精确闹钟/重启",
    perm_alarm_desc: "确保应用在异常退出后按时重启。"
};