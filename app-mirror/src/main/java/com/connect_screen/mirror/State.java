package com.connect_screen.mirror;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.os.IBinder;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.connect_screen.mirror.job.Job;
import com.connect_screen.mirror.job.YieldException;
import com.connect_screen.mirror.shizuku.IUserService;
import com.connect_screen.mirror.shizuku.UserService;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import rikka.shizuku.Shizuku;

public class State {
    // 弱引用保存当前的 MainActivity 实例
    private static WeakReference<MirrorMainActivity> currentActivity = new WeakReference<>(null);
    public static final MutableLiveData<MirrorUiState> uiState = new MutableLiveData<>(new MirrorUiState());
    public static FloatingButtonService floatingButtonService;
    public static String serverUuid;
    private static Job currentJob;
    public static List<String> logs = new ArrayList<>();
    public static DisplaylinkState displaylinkState = new DisplaylinkState();
    private static MediaProjection mediaProjection;
    public static MediaProjection mediaProjectionInUse;
    public static int lastSingleAppDisplay;
    public static String displaylinkDeviceName;
    public static VirtualDisplay mirrorVirtualDisplay;
    public static Activity isInPureBlackActivity = null;
    public static volatile IUserService userService;
    public static Set<String> discoveredConnectScreenClients = new HashSet<>();

    public static MirrorMainActivity getCurrentActivity() {
        if (currentActivity == null) {
            return null;
        }
        return currentActivity.get();
    }

    public static void setCurrentActivity(MirrorMainActivity activity) {
        currentActivity = new WeakReference<>(activity);
    }

    public static final ServiceConnection userServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            // 🛡️ 安全第一步：既然进来了，说明物理连接绝对成功，立刻拆除外面的超时炸弹！
            State.log("user service connected");
            State.log("授予媒体投影权限和悬浮窗权限");
            synchronized (BIND_LOCK) {
                State.userService = IUserService.Stub.asInterface(binder);
                if (timeoutRunnable != null) {
                    mainHandler.removeCallbacks(timeoutRunnable);
                    timeoutRunnable = null; // 释放
                }
                try {
                    String packageName = componentName.getPackageName();
                    String result = State.userService.executeCommand("appops get com.orbit SYSTEM_ALERT_WINDOW");
                    if (!result.contains("allow")) {
                        State.userService.executeCommand("appops set com.orbit SYSTEM_ALERT_WINDOW allow");
                    }
                    result = State.userService.executeCommand("appops get com.orbit PROJECT_MEDIA");
                    if (!result.contains("allow")) {
                        State.userService.executeCommand("appops set com.orbit PROJECT_MEDIA allow");
                    }
                    result = State.userService.executeCommand("appops get com.orbit ACTIVATE_VPN");
                    if (!result.contains("allow")) {
                        State.userService.executeCommand("appops set com.orbit ACTIVATE_VPN allow");
                    }
                    result = State.userService.executeCommand("appops get com.orbit 10021");
                    if (!result.contains("allow")) {
                        State.userService.executeCommand("appops set com.orbit 10021 allow");
                    }
                    result = State.userService.executeCommand("dumpsys deviceidle whitelist");
                    if (!result.contains(",com.orbit,")) {
                        State.userService.executeCommand("dumpsys deviceidle whitelist +com.orbit");
                    }
                    result = State.userService.executeCommand("appops get " + packageName + " POST_NOTIFICATION");
                    // 3. 只要返回结果里包含不干净的状态（ignore / deny），或者干脆不只有单纯的 allow
                    if (result == null || result.contains("ignore") || result.contains("deny") || !result.trim().equals("POST_NOTIFICATION: allow")) {
                        // 4. 【核心大招】直接动用官方包管理器命令，强行注入运行时权限，彻底粉碎 Uid mode 的 ignore 状态
                        State.userService.executeCommand("pm grant " + packageName + " android.permission.POST_NOTIFICATIONS");
                        // 5. 【辅助保险】顺手把 AppOps 层的状态也刷新为 allow
                        State.userService.executeCommand("appops set " + packageName + " POST_NOTIFICATION allow");
                    }
                    // 1. 强行往系统的权限清单里塞入“允许录音”
                    State.userService.executeCommand("pm grant com.orbit android.permission.RECORD_AUDIO");
                    // 2. 强行扭开系统的 AppOps 运行时开关（双重保险，防止部分国内魔改系统拦截）
                    State.userService.executeCommand("appops set com.orbit RECORD_AUDIO allow");
                    // 用 Shizuku 强行激活 ORBIT 自己的无障碍服务
                    State.userService.executeCommand(String.format("settings put secure enabled_accessibility_services %s/%s", packageName, TouchpadAccessibilityService.class.getName()));
                    State.userService.executeCommand("settings put secure accessibility_enabled 1");

                    // 拆除超时炸弹的责任可以交给外界，或者通过解耦处理。为了简单，我们直接让 Future 落地
                    if (activeFuture != null && !activeFuture.isDone()) {
                        // 🔔 这一枪下去，第一个调用者以及后续所有挂载在上面的竞争者，将【同时】被唤醒执行！
                        activeFuture.complete(null);
                        activeFuture = null; // 功成身退，清空池子
                    }
                } catch (Throwable e) {
                    // ignorepp
                    e.printStackTrace();
                    // 拆除超时炸弹的责任可以交给外界，或者通过解耦处理。为了简单，我们直接让 Future 落地
                    if (activeFuture != null && !activeFuture.isDone()) {
                        // 🔔 这一枪下去，第一个调用者以及后续所有挂载在上面的竞争者，将【同时】被唤醒执行！
                        activeFuture.completeExceptionally(e);
                        activeFuture = null; // 功成身退，清空池子
                    }
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            State.log("user service disconnected");
            synchronized (BIND_LOCK) {
                if (activeFuture != null && !activeFuture.isDone()) {
                    activeFuture.completeExceptionally(new RuntimeException("Disconnected"));
                    activeFuture = null;
                }
            }
        }
    };

    public static Shizuku.UserServiceArgs userServiceArgs = new Shizuku.UserServiceArgs(new ComponentName(BuildConfig.APPLICATION_ID, UserService.class.getName()))
            .daemon(true)
            .tag("temp7")
            .processNameSuffix("connect-screen")
            .debuggable(false)
            .version(BuildConfig.VERSION_CODE);

    private static final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    public static boolean isJobRunning() {
        return currentJob != null;
    }   

    public static void startNewJob(Job job) {
        if (currentJob != null) {
            if (currentActivity != null && currentActivity.get() != null) {
                State.log("当前任务 " + currentJob.getClass().getSimpleName() + " 正在进行中");
            }
            return;
        }
        currentJob = job;
        try {
            State.log("开始任务 " + job.getClass().getSimpleName());
            currentJob.start();
            State.log("任务 " + job.getClass().getSimpleName() + " 完成");
            currentJob = null;
        } catch (YieldException e) {
            State.log("任务 " + job.getClass().getSimpleName() + " 暂停, " + e.getMessage());
        } catch (RuntimeException e) {
            State.log("任务 " + job.getClass().getSimpleName() + " 启动失败");
            String stackTrace = android.util.Log.getStackTraceString(e);
            State.log("堆栈跟踪: " + stackTrace);
            currentJob = null;
        }
    }

    public static void resumeJob() {
        if (currentJob == null) {
            return;
        }
        try {
            State.log("恢复任务 " + currentJob.getClass().getSimpleName());
            currentJob.start();
            State.log("任务 " + currentJob.getClass().getSimpleName() + " 完成");
            currentJob = null;
        } catch (YieldException e) {
            State.log("任务 " + currentJob.getClass().getSimpleName() + " 暂停, " + e.getMessage());
        } catch (RuntimeException e) {
            State.log("任务 " + currentJob.getClass().getSimpleName() + " 恢复失败");
            String stackTrace = android.util.Log.getStackTraceString(e);
            State.log("堆栈跟踪: " + stackTrace);
            currentJob = null;
        }
    }

    public static void resumeJobLater(long delayMillis) {
        if (currentActivity.get() != null) {
            mainHandler.postDelayed(() -> {
                resumeJob();
            }, delayMillis);
        }
    }

    public static void log(String message) {
        logs.add(message);
        Log.i("ConnectScreen", message);
        if (currentActivity != null && currentActivity.get() != null) {
            IMainActivity  mainActivity = (IMainActivity) currentActivity.get();
            mainActivity.updateLogs();
        }
    }

    public static MediaProjection getMediaProjection() {
        return mediaProjection;
    }

    public static void setMediaProjection(MediaProjection newMediaProjection) {
        if (newMediaProjection == null) {
            Log.d("State", "MediaProjection used");
            mediaProjection = null;
        } else {
            Log.d("State", "MediaProjection acquired");
            mediaProjection = newMediaProjection;
            mediaProjectionInUse = newMediaProjection;
        }
    }

    public static int getDisplaylinkVirtualDisplayId() {
        if (displaylinkState.getVirtualDisplay() == null) {
            return -1;
        }
        return displaylinkState.getVirtualDisplay().getDisplay().getDisplayId();
    }

    public static int getMirrorVirtualDisplayId() {
        if (mirrorVirtualDisplay == null) {
            return -1;
        }
        return mirrorVirtualDisplay.getDisplay().getDisplayId();
    }

    public static void unbindUserService() {
        try {
            Shizuku.unbindUserService(State.userServiceArgs, userServiceConnection, false); // 解绑用户服务
            State.userService = null;
        } catch (Exception e) {
            // ignore
        }
    }

    public static void refreshMainActivity() {
        MirrorMainActivity mirrorMainActivity = currentActivity.get();
        if (mirrorMainActivity != null) {
            mirrorMainActivity.runOnUiThread(mirrorMainActivity::refresh);
        }
    }

    public static void showErrorStatus(String msg) {
        State.log(msg);
        MirrorUiState newUiState = new MirrorUiState();
        newUiState.errorStatusText = msg;
        State.uiState.setValue(newUiState);
    }

    public static Context getContext() {
        if (currentActivity != null && currentActivity.get() != null) {
            return currentActivity.get();
        }
        if (SunshineService.instance != null) {
            return SunshineService.instance;
        }
        return null;
    }

    private static final Object BIND_LOCK = new Object();
    private static final int BIND_TIMEOUT_MS = 2000;
    private static Runnable timeoutRunnable = null;

    // 🔔 核心蓄水池：用来承载当前正在进行的绑定任务
    private static CompletableFuture<Void> activeFuture = null;

    public static CompletableFuture<Void> bindUserService() {
        synchronized (BIND_LOCK) {
            // 2️⃣ 核心竞争拦截：如果 activeFuture 不为空，说明有一个绑定正在进行中！
            if (activeFuture != null) {
                State.log("检测到并发绑定竞争，当前调用将直接挂载到既有任务上...");
                // ⚠️ 绝不发起第二次绑定，直接返回这个正在走生命周期的同一个 Future！
                return activeFuture;
            }

            // 3️⃣ 走到这里，说明是第一个抢到坑位的“开路先锋”，正式创建承诺
            State.log("开始发起第一个物理绑定任务...");
            activeFuture = new CompletableFuture<>();
        }

        // 4️⃣ 埋下 2 秒超时炸弹（防止 Shizuku 底层静默悬空导致所有挂载的调用者全部卡死）
        timeoutRunnable = () -> {
            synchronized (BIND_LOCK) {
                if (activeFuture != null && !activeFuture.isDone()) {
                    State.log("Shizuku 物理绑定超时，斩断死等！");

                    // 让当前以及所有挂载在上面的竞争者全部收到失败信号（或者完结信号，视你的状态机而定）
                    // 推荐用 completeExceptionally 或者直接 complete(null) 并在外部判断 userService == null
                    activeFuture.completeExceptionally(new java.util.concurrent.TimeoutException("Shizuku bind timeout"));

                    activeFuture = null; // 释放坑位

                    try {
                        Shizuku.unbindUserService(State.userServiceArgs, State.userServiceConnection, true);
                    } catch (Exception ignored) {}
                }
            }
        };
        mainHandler.postDelayed(timeoutRunnable, BIND_TIMEOUT_MS);

        // 5️⃣ 执行原 fork 的物理绑定
        try {
            Shizuku.peekUserService(State.userServiceArgs, State.userServiceConnection);
            Shizuku.bindUserService(State.userServiceArgs, State.userServiceConnection);
        } catch (Throwable t) {
            synchronized (BIND_LOCK) {
                if (activeFuture != null) {
                    activeFuture.completeExceptionally(t);
                    activeFuture = null;
                }
            }
            mainHandler.removeCallbacks(timeoutRunnable);
        }

        // 返回给第一个调用者的 Future
        return activeFuture;
    }
}
