window.i18nData["zh-Hant"] =  {
    "app.title": "遠端桌面",

    // Tab 標籤
    "tab.remote": "遠端連線",
    "tab.myDevices": "我的裝置",
    "tab.activeConnections": "活動連線",
    "tab.history": "歷史記錄",
    "tab.proxySettings": "代理設定",
    "tab.androidPermissions": "Android權限",

    // Remote Connection Tab
    "remote.myDevices.title": "我的裝置",
    "remote.myDevices.deviceId.label": "裝置編號",
    "remote.myDevices.accessPassword.label": "存取密碼",
    "remote.myDevices.password.show": "顯示",
    "remote.myDevices.password.hide": "隱藏",
    "remote.myDevices.settings.button": "設定",
    "remote.myDevices.settings.message": "開啟設定...",
    "remote.myDevices.newPassword.button": "新密碼",
    "remote.myDevices.password.generated": "已產生新密碼",

    "remote.connect.title": "連線到其他裝置",
    "remote.connect.remoteId.label": "對方裝置編號",
    "remote.connect.remoteId.placeholder": "輸入對方裝置編號",
    "remote.connect.password.label": "連線密碼",
    "remote.connect.password.placeholder": "6位密碼",
    "remote.connect.control.button": "遠端控制",
    "remote.connect.fileTransfer.button": "檔案傳輸",
    "remote.connect.error.idPasswordRequired": "請輸入裝置編號和密碼",
    "remote.connect.connecting": "正在連線到裝置...",

    // My Devices Tab (Logged Out)
    "myDevices.login.title": "登入以查看裝置",
    "myDevices.login.description": "登入後可以查看和管理您的所有裝置",
    "myDevices.login.button": "登入帳戶",

    // My Devices Tab (Logged In)
    "myDevices.header.title": "我的裝置",
    "myDevices.header.onlineBadge": "{{count}}台裝置線上",
    "myDevices.header.logout.button": "登出",
    "myDevices.empty.title": "暫無裝置",
    "myDevices.empty.description": "新增裝置以開始遠端控制",
    "myDevices.lastSeen": "最後活動",
    "myDevices.status.self": "本裝置",
    "myDevices.status.online": "線上",
    "myDevices.status.offline": "離線",
    "myDevices.action.connect": "連線",
    "myDevices.action.fileTransfer": "檔案傳輸",

    // Active Connections Tab
    "activeConnections.header.title": "活動連線",
    "activeConnections.header.badge": "{{count}}個活躍連線",
    "activeConnections.type.remoteControl": "遠端控制會話",
    "activeConnections.type.fileTransfer": "檔案傳輸會話",
    "activeConnections.connectionTime": "連線時間",
    "activeConnections.justNow": "剛剛",
    "activeConnections.dataTransfer": "資料傳輸",
    "activeConnections.realTime": "即時",
    "activeConnections.status.controlling": "控制中",
    "activeConnections.status.transferring": "傳輸中",
    "activeConnections.action.switch": "切換至",
    "activeConnections.action.disconnect": "中斷",
    "activeConnections.confirm.close": "確定要關閉連線 {{id}} 嗎？",
    "activeConnections.status.connectionClosed": "連線已關閉",

    // History Tab
    "history.header.title": "歷史記錄",
    "history.type.remoteControl": "遠端控制",
    "history.type.fileTransfer": "檔案傳輸",
    "history.connectionTime": "連線時間",
    "history.duration": "持續時間",
    "history.status.ended": "已結束",
    "history.action.view": "查看",
    "history.action.viewing": "正在查看歷史記錄 {{id}}...",
    "history.empty.title": "暫無歷史記錄",
    "history.empty.description": "連線過的裝置將顯示在這裡",

    // Proxy Settings Tab
    "proxy.header.title": "代理設定",
    "proxy.option.none.label": "不使用代理",
    "proxy.option.none.description": "直接連線到遠端裝置，不使用任何代理伺服器",
    "proxy.option.system.label": "跟隨系統",
    "proxy.option.system.description": "使用系統設定的代理配置",
    "proxy.option.custom.label": "指定代理",
    "proxy.option.custom.description": "手動配置代理伺服器地址和連接埠",
    "proxy.details.type.label": "代理類型",
    "proxy.details.type.http": "HTTP",
    "proxy.details.type.socks5": "SOCKS5",
    "proxy.details.host.label": "代理伺服器地址",
    "proxy.details.host.placeholder": "例如: 192.168.1.100 或 proxy.example.com",
    "proxy.details.port.label": "代理連接埠",
    "proxy.details.port.placeholder": "例如: 8080",
    "proxy.details.username.label": "使用者名稱 (可選)",
    "proxy.details.username.placeholder": "代理伺服器使用者名稱",
    "proxy.details.password.label": "密碼 (可選)",
    "proxy.details.password.placeholder": "代理伺服器密碼",
    "proxy.test.title": "測試連線",
    "proxy.test.url.label": "測試連線地址",
    "proxy.test.url.placeholder": "輸入要測試的URL地址",
    "proxy.test.button": "測試連線",
    "proxy.save.button": "儲存設定",
    "proxy.save.error": "請填寫代理伺服器地址和連接埠",
    "proxy.save.success": "代理設定已儲存",
    "proxy.test.testing": "正在測試代理連線...",
    "proxy.test.hostPortRequired": "請填寫代理伺服器地址和連接埠",
    "proxy.test.urlRequired": "請填寫要測試的URL地址",
    "proxy.test.testingWithUrl": "正在透過代理測試連線: ",
    "proxy.test.success": "代理連線測試成功！連線正常。",

    // Status Bar
    "statusBar.ready": "狀態：就緒",
    "statusBar.lastUpdate": "最後更新：剛剛",
    "statusBar.loggedIn": "已登入",
    "statusBar.loggedOut": "已登出",
    "statusBar.status": "狀態：{{text}}",

    // Custom Confirm Dialog
    "dialog.confirm.title": "確認操作",
    "dialog.confirm.content": "確定要執行此操作嗎？",

    // Buttons
    "button.cancel": "取消",
    "button.confirm": "確定",


    "android.permissions.header": "⚙️ 無人值守授權配置",
    "android.permissions.description": "請確保以下所有權限均顯示為\"已開啟\"，否則受控端無法正常運作",
    "android.permissions.action.go": "前往授權",
    "android.permissions.action.ok": "已開啟",

    core_screen_name: "核心投屏功能",
    core_screen_desc: "必須開啟以下權限，否則應用無法正常運作。",

    interface_monitor_name: "介面互動與監控",
    interface_monitor_desc: "可選權限，用於增強互動和監控設備狀態。",

    keepalive_name: "進程保活與自動重啟",
    keepalive_desc: "確保應用持續運行並可自動重啟的權限。",

    perm_mic_name: "錄音/麥克風",
    perm_mic_desc: "用於遠程捕捉環境聲音。",

    perm_projection_name: "投屏",
    perm_projection_desc: "用於將設備螢幕共享到遠端會話。",

    perm_vpn_name: "VPN 轉發",
    perm_vpn_desc: "用於內網穿透和穩定的資料傳輸。",

    perm_accessibility_name: "無障礙服務",
    perm_accessibility_desc: "用於模擬點擊和手勢操作，實現核心控制。",

    perm_overlay_name: "懸浮窗權限",
    perm_overlay_desc: "允許在背景顯示懸浮界面並與使用者互動。",

    perm_notification_name: "通知監聽",
    perm_notification_desc: "監控設備通知，了解狀態變化。",

    perm_files_name: "文件訪問",
    perm_files_desc: "允許選擇和管理設備文件。",

    perm_autostart_name: "自啟動權限",
    perm_autostart_desc: "確保應用在重啟、關機後自動啟動，否則需手動開啟。",

    perm_popup_name: "背景彈出",
    perm_popup_desc: "允許應用在背景彈出視窗或提示。",

    perm_power_name: "省電策略白名單",
    perm_power_desc: "防止系統在背景休眠應用，保持持續運行。",

    perm_alarm_name: "精確鬧鐘/重啟",
    perm_alarm_desc: "確保應用在異常退出後按時重啟。"
};