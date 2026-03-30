window.i18nData["en"] = {
    "app.title": "Remote Desktop",

    "tab.remote": "Remote Connection",
    "tab.myDevices": "My Devices",
    "tab.activeConnections": "Active Connections",
    "tab.history": "History",
    "tab.proxySettings": "Proxy Settings",
    "tab.androidPermissions": "Android Permissions",

    // Remote Connection Tab
    "remote.myDevices.title": "My Devices",
    "remote.myDevices.deviceId.label": "Device ID",
    "remote.myDevices.accessPassword.label": "Access Password",
    "remote.myDevices.password.show": "Show",
    "remote.myDevices.password.hide": "Hide",
    "remote.myDevices.settings.button": "Settings",
    "remote.myDevices.newPassword.button": "New Password",
    "remote.connect.title": "Connect to Another Device",
    "remote.connect.remoteId.label": "Remote Device ID",
    "remote.connect.remoteId.placeholder": "Enter remote device ID",
    "remote.connect.password.label": "Connection Password",
    "remote.connect.password.placeholder": "6-digit password",
    "remote.connect.control.button": "Remote Control",
    "remote.connect.fileTransfer.button": "File Transfer",

    // My Devices Tab (Logged Out)
    "myDevices.login.title": "Login to View Devices",
    "myDevices.login.description": "You can view and manage all your devices after logging in",
    "myDevices.login.button": "Login Account",

    // My Devices Tab (Logged In)
    "myDevices.header.title": "My Devices",
    "myDevices.header.onlineBadge": "{{count}} devices online",
    "myDevices.header.logout.button": "Logout",
    // Device cards will be populated dynamically, but static text can be here if needed

    // Active Connections Tab
    "activeConnections.header.title": "Active Connections",
    "activeConnections.header.badge": "{{count}} active connections",

    // History Tab
    "history.header.title": "History",

    // Proxy Settings Tab
    "proxy.header.title": "Proxy Settings",
    "proxy.option.none.label": "No Proxy",
    "proxy.option.none.description": "Connect directly to the remote device without using any proxy server",
    "proxy.option.system.label": "Follow System",
    "proxy.option.system.description": "Use the proxy configuration set by the system",
    "proxy.option.custom.label": "Custom Proxy",
    "proxy.option.custom.description": "Manually configure the proxy server address and port",
    "proxy.details.type.label": "Proxy Type",
    "proxy.details.type.http": "HTTP",
    "proxy.details.type.socks5": "SOCKS5",
    "proxy.details.host.label": "Proxy Server Address",
    "proxy.details.host.placeholder": "e.g., 192.168.1.100 or proxy.example.com",
    "proxy.details.port.label": "Proxy Port",
    "proxy.details.port.placeholder": "e.g., 8080",
    "proxy.details.username.label": "Username (Optional)",
    "proxy.details.username.placeholder": "Proxy server username",
    "proxy.details.password.label": "Password (Optional)",
    "proxy.details.password.placeholder": "Proxy server password",
    "proxy.test.title": "Test Connection",
    "proxy.test.url.label": "Test Connection URL",
    "proxy.test.url.placeholder": "Enter the URL address to test",
    "proxy.test.button": "Test Connection",
    "proxy.save.button": "Save Settings",

    // Status Bar
    "statusBar.ready": "Status: Ready",
    "statusBar.lastUpdate": "Last updated: just now",

    // Generic Buttons (if any not covered)
    "button.cancel": "Cancel",
    "button.confirm": "Confirm",

    // 添加到现有en.js中
    "remote.connect.error.idPasswordRequired": "Please enter device ID and password",
    "remote.connect.connecting": "Connecting to device...",
    "remote.myDevices.password.generated": "New password generated",
    "myDevices.empty.title": "No Devices",
    "myDevices.empty.description": "Add a device to start remote control",
    "myDevices.lastSeen": "Last Seen",
    "myDevices.status.self": "This Device",
    "myDevices.status.online": "Online",
    "myDevices.status.offline": "Offline",
    "myDevices.action.connect": "Connect",
    "myDevices.action.fileTransfer": "File Transfer",
    "activeConnections.type.remoteControl": "Remote Control Session",
    "activeConnections.type.fileTransfer": "File Transfer Session",
    "activeConnections.connectionTime": "Connection Time",
    "activeConnections.justNow": "Just now",
    "activeConnections.dataTransfer": "Data Transfer",
    "activeConnections.realTime": "Real-time",
    "activeConnections.status.controlling": "Controlling",
    "activeConnections.status.transferring": "Transferring",
    "activeConnections.action.switch": "Switch To",
    "activeConnections.action.disconnect": "Disconnect",
    "activeConnections.confirm.close": "Are you sure you want to close connection {{id}}?",
    "activeConnections.status.connectionClosed": "Connection closed",
    "history.type.remoteControl": "Remote Control",
    "history.type.fileTransfer": "File Transfer",
    "history.connectionTime": "Connection Time",
    "history.duration": "Duration",
    "history.status.ended": "Ended",
    "history.action.view": "View",
    "history.action.viewing": "Viewing history record {{id}}...",
    "history.empty.title": "No History",
    "history.empty.description": "Connected devices will appear here",
    "proxy.save.error": "Please fill in proxy server address and port",
    "proxy.save.success": "Proxy settings saved",
    "proxy.test.testing": "Testing proxy connection...",
    "proxy.test.hostPortRequired": "Please fill in proxy server address and port",
    "proxy.test.urlRequired": "Please fill in the URL address to test",
    "proxy.test.testingWithUrl": "Testing connection via proxy: ",
    "proxy.test.success": "Proxy connection test successful!",
    "statusBar.loggedIn": "Logged in",
    "statusBar.loggedOut": "Logged out",
    "statusBar.status": "Status: {{text}}",
    "dialog.confirm.content": "Are you sure you want to perform this operation?",
    "remote.myDevices.settings.message": "Opening settings...",


    "android.permissions.header": "⚙️ Unattended Authorization Configuration",
    "android.permissions.description": "Please ensure all permissions below show as \"Granted\", otherwise the controlled device will not function properly",
    "android.permissions.action.go": "Grant",
    "android.permissions.action.ok": "Granted",

    // 分组标题与描述
    core_screen_name: "Core Screen Features",
    core_screen_desc: "Essential permissions for screen casting. The app cannot work without these enabled.",

    interface_monitor_name: "Interface & Monitoring",
    interface_monitor_desc: "Optional permissions to enhance interaction and monitor device status.",

    keepalive_name: "Keepalive & Auto-Restart",
    keepalive_desc: "Permissions to ensure the app stays running and can restart automatically.",

    // 核心投屏组
    perm_mic_name: "Microphone",
    perm_mic_desc: "Used for capturing sound during remote sessions.",

    perm_projection_name: "Screen Casting",
    perm_projection_desc: "Required to share the device screen to a remote session.",

    perm_vpn_name: "VPN Forwarding",
    perm_vpn_desc: "Enables internal network access and stable data transmission.",

    perm_accessibility_name: "Accessibility Service",
    perm_accessibility_desc: "Allows automated control for gestures and simulated clicks.",

    // 界面监控组
    perm_overlay_name: "Overlay Permission",
    perm_overlay_desc: "Allows floating UI and interaction while app runs in background.",

    perm_notification_name: "Notification Access",
    perm_notification_desc: "Monitors device notifications to track status changes.",

    perm_files_name: "File Access",
    perm_files_desc: "Allows selecting and managing files on the device.",

    // 保活组
    perm_autostart_name: "Auto-Start",
    perm_autostart_desc: "Ensures the app starts automatically after reboot or update.",

    perm_popup_name: "Background Popups",
    perm_popup_desc: "Allows the app to show dialogs or windows in the background.",

    perm_power_name: "Battery Whitelist",
    perm_power_desc: "Prevents system from suspending the app in the background.",

    perm_alarm_name: "Precise Alarm / Restart",
    perm_alarm_desc: "Ensures scheduled tasks or restarts run on time after shutdown or crash."

};