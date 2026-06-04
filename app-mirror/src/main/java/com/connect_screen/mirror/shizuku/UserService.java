package com.connect_screen.mirror.shizuku;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.IDisplayManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import android.os.RemoteException;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.connect_screen.mirror.job.AndroidVersions;
import com.connect_screen.mirror.job.CreateVirtualDisplay;

import rikka.shizuku.SystemServiceHelper;

public class UserService extends IUserService.Stub  {
    private Context context;
    private boolean listenVolumeKey = false;
    private Process listenVolumeKeyProcess;
    private Thread volumeKeyThread;
    private AudioRecord audioRecord;
    private float[] buffer;

    public UserService() {
        Ln.i("Start UserService without context: " + android.os.Process.myUid());
    }

    @Keep
    public UserService(Context context) {
        this.context = context;
        Ln.i("Start UserService with context: " + android.os.Process.myUid());
    }

    /**
     * Reserved destroy method
     */
    @Override
    public void destroy() {
        Log.i("UserService", "destroy");
        stopListenVolumeKey();
        setScreenPower(SurfaceControl.POWER_MODE_NORMAL);
        if (audioRecord != null) {
            audioRecord.stop();
        }
        System.exit(0);
    }

    @Override
    public void exit() {
        destroy();
    }

    @Override
    public String fetchLogs() throws RemoteException  {
        try {
            Process process = Runtime.getRuntime().exec("logcat -d -f /sdcard/Download/安卓屏连.log");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            reader.close();
            process.waitFor();

            return output.toString();
        } catch (Exception e) {
            Log.e("UserService", "logcat -d failed", e);
            throw new RemoteException("Failed to execute logcat -d: " + e.getMessage());
        }
    }

    @Override
    public String executeCommand(String command) throws RemoteException {
        try {
            Process process = Runtime.getRuntime().exec(command);
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            reader.close();
            process.waitFor();

            return output.toString();
        } catch (Exception e) {
            Log.e("UserService", "execute command failed: " + command, e);
            throw new RemoteException("Failed to execute command: " + command + " " + e.getMessage());
        }
    }

    @Override
    public void setClipboardData(String text, String imagePath) {
// 必须强制切到系统的 Main Looper（主线程），不然高版本 Android 直接吞掉操作
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                System.out.println("OrbitShizuku: 卧槽！断点终于进来了！！！");

                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm == null) return;

                ClipData clip = null;
                if (text != null && !text.isEmpty()) {
                    clip = ClipData.newPlainText("text", text);
                } else if (imagePath != null && !imagePath.isEmpty()) {
                    Uri imageUri = Uri.parse("file://" + imagePath);
                    clip = ClipData.newUri(context.getContentResolver(), "image", imageUri);
                }

                if (clip != null) {
                    try {
                        // 【高兼容反射】强行伪装成系统自带的 shell 进程，解决 Android 12/13/14 拒绝后台写入的策略
                        java.lang.reflect.Method setPrimaryClipMethod = cm.getClass().getMethod(
                                "setPrimaryClip", ClipData.class, String.class, int.class
                        );
                        setPrimaryClipMethod.invoke(cm, clip, "com.android.shell", 0);
                        System.out.println("OrbitShizuku: 写入剪切板绝对成功了！");
                    } catch (Exception ex) {
                        // 老版本 Android 保底老老实实调用
                        cm.setPrimaryClip(clip);
                    }
                }
            } catch (Exception e) {
                System.err.println("OrbitShizuku 底层爆了：");
                e.printStackTrace();
            }
        });
    }
    public boolean setScreenPower(int powerMode) {
        Log.i("UserService", "try to setScreenPower: " + powerMode);
        if (Build.VERSION.SDK_INT >= 35 && powerMode == SurfaceControl.POWER_MODE_NORMAL) {
            setScreenPowerViaNewApi(SurfaceControl.POWER_MODE_OFF);
            setScreenPowerViaNewApi(SurfaceControl.POWER_MODE_NORMAL);
        }
        try {
            IBinder displayToken = getDisplayToken();
            if (displayToken == null) {
                return false;
            }
            Ln.d("setDisplayPowerMode: " + displayToken + " " + powerMode);
            boolean result = SurfaceControl.setDisplayPowerMode(displayToken, powerMode);
            Ln.d("after setDisplayPowerMode: " + result);
        } catch(Throwable e) {
            Ln.e("setScreenPower failed", e);
        }
        return true;
    }

    private boolean setScreenPowerViaNewApi(int powerMode) {
        IDisplayManager displayManager = IDisplayManager.Stub.asInterface(SystemServiceHelper.getSystemService(Context.DISPLAY_SERVICE));
        if (powerMode == SurfaceControl.POWER_MODE_OFF) {
            try {
                displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, false);
                Log.i("UserService", "requestDisplayPower by bool false");
            } catch(Throwable e) {
                Log.e("UserService", "failed to power off screen", e);
                try {
                    displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, SurfaceControl.POWER_MODE_OFF);
                    Log.i("UserService", "requestDisplayPower by int: " + powerMode);
                } catch(Throwable e2) {
                    Log.e("UserService", "failed to power off screen", e2);
                    return false;
                }
            }
        } else {
            try {
                displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, true);
                Log.i("UserService", "requestDisplayPower by bool true");
            } catch (Throwable e) {
                Log.e("UserService", "failed to power up screen", e);
                try {
                    displayManager.requestDisplayPower(Display.DEFAULT_DISPLAY, SurfaceControl.POWER_MODE_NORMAL);
                    Log.i("UserService", "requestDisplayPower by int: " + powerMode);
                } catch(Throwable e2) {
                    Log.e("UserService", "failed to power up screen", e2);
                    return false;
                }
            }
        }
        return true;
    }

    private @Nullable IBinder getDisplayToken() {
        try {
            long[] physicalDisplayIds = DisplayControl.getPhysicalDisplayIds();
            Ln.d("physicalDisplayIds count: " + physicalDisplayIds.length);
            if (physicalDisplayIds.length > 0) {
                return DisplayControl.getPhysicalDisplayToken(physicalDisplayIds[0]);
            }
            return SurfaceControl.getBuiltInDisplay();
        } catch (Throwable e) {
            Ln.e("failed to getDisplayToken", e);
            try {
                return SurfaceControl.getBuiltInDisplay();
            } catch (Throwable e2) {
                Ln.e("failed to getDisplayToken", e2);
            }
        }
        return null;
    }

    public void startListenVolumeKey() throws RemoteException {
        if (listenVolumeKey) {
            return;
        }
        listenVolumeKey = true;
        Thread thread = new Thread(() -> {
            while(listenVolumeKey) {
                try {
                    Ln.i("Run getevent to detect volume key pressed");
                    listenVolumeKeyProcess = Runtime.getRuntime().exec("getevent");
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(listenVolumeKeyProcess.getInputStream()));
                    while (listenVolumeKey) {
                        String line = reader.readLine();
                        if (line == null || !listenVolumeKey) {
                            Ln.i("break out getevent");
                            break;
                        }
                        if (!line.endsWith("0000 0000 00000000") &&
                                (line.endsWith("0001 0072 00000001") || line.endsWith("0001 0073 00000001"))) {
                            Ln.i("detected volume key, try to power on screen");
                            setScreenPower(SurfaceControl.POWER_MODE_NORMAL);
                            if (context != null) {
                                Intent intent = new Intent("com.connect_screen.mirror.EXIT_PURE_BLACK");
                                intent.setPackage("com.connect_screen.mirror");
                                context.sendBroadcast(intent);
                            } else {
                                Ln.i("context is null, can not send EXIT_PURE_BLACK");
                            }
                        }
                    }
                    reader.close();
                    if (listenVolumeKeyProcess != null) {
                        listenVolumeKeyProcess.waitFor();
                        if (android.os.Build.VERSION.SDK_INT >= 26) {
                            listenVolumeKeyProcess.destroyForcibly();
                        } else {
                            listenVolumeKeyProcess.destroy();
                        }
                    }
                } catch (Exception e) {
                    Ln.e("Listen volume key failed", e);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
            Ln.i("getevent thread end");
        });
        volumeKeyThread = thread;
        thread.start();
    }

    public void stopListenVolumeKey() {
        listenVolumeKey = false;
        if (listenVolumeKeyProcess != null) {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                listenVolumeKeyProcess.destroyForcibly();
            } else {
                listenVolumeKeyProcess.destroy();
            }
            listenVolumeKeyProcess = null;
        }
        if (volumeKeyThread != null) {
            volumeKeyThread.interrupt();
            volumeKeyThread = null;
        }
    }

    @Override
    public int createVirtualDisplay(Surface surface) throws RemoteException {
        Ln.i("try to createVirtualDisplay");
        try {
            return DisplayManager.create().createNewVirtualDisplay("test", 1920, 1080, 160, surface, CreateVirtualDisplay.getFlags(true, true)).getDisplay().getDisplayId();
        } catch (Throwable e) {
            Ln.e("failed to create virtual display", e);
        }
        return 0;
    }

    @Override
    public boolean isRooted() throws RemoteException {
        return android.os.Process.myUid() == 0;
    }

    @Override
    public int readAudio(float[] result) throws RemoteException {
        try {
            if (audioRecord == null) {
                return 0;
            }
            return audioRecord.read(result, 0, result.length, AudioRecord.READ_BLOCKING);
        } catch(Throwable e) {
            Ln.e("failed to read audio", e);
            return 0;
        }
    }

    @Override
    public boolean startRecordingAudio() throws RemoteException {
        try {
            if (audioRecord == null) {
                Ln.d("before start recording");
                audioRecord = createAudioRecord();
                audioRecord.startRecording();
                Ln.d("started recording");
                return true;
            } else {
                return true;
            }
        } catch(Throwable e) {
            Ln.e("failed to start recording audio", e);
            return false;
        }
    }

    @Override
    public boolean stopRecordingAudio() throws RemoteException {
        try {
            if (audioRecord == null) {
                return true;
            } else {
                audioRecord.stop();
                audioRecord = null;
                return true;
            }
        } catch(Throwable e) {
            Ln.e("failed to stop recording audio", e);
            return false;
        }
    }

    @SuppressLint({"WrongConstant", "MissingPermission"})
    private AudioRecord createAudioRecord() {
        AudioRecord.Builder builder = new AudioRecord.Builder();
        if (Build.VERSION.SDK_INT >= AndroidVersions.API_31_ANDROID_12) {
            // On older APIs, Workarounds.fillAppInfo() must be called beforehand
            builder.setContext(context);
        }
        builder.setAudioSource(MediaRecorder.AudioSource.REMOTE_SUBMIX);
        int sampleRate = 48000; // 与您的Opus配置匹配
        int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        int audioEncoding = AudioFormat.ENCODING_PCM_FLOAT;
        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(audioEncoding)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build();
        builder.setAudioFormat(audioFormat);
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding);
        if (minBufferSize > 0) {
            // This buffer size does not impact latency
            builder.setBufferSizeInBytes(2 * minBufferSize);
        }
        return builder.build();
    }
}
