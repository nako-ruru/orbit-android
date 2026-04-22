package com.orbit;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import aar.TunProvider;

public class AndroidTunProvider implements TunProvider {

    private final Context context;
    private final String[] fixedIps;

    public static CompletableFuture<Object> future;
    private final Object lock = new Object();

    @Override
    public long getTunId() throws Exception {
        synchronized (lock) {
            // 1. 检查权限：如果返回不为 null，说明需要用户点击系统确认
            if (VpnService.prepare(context) != null) {
                // 激活透明 Activity 呼出系统弹窗
                Intent intent = new Intent(context, TransparentProxyActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                future = new CompletableFuture<>();
                context.startActivity(intent);
                future.get(1, TimeUnit.MINUTES);
            }
        }

        synchronized (lock) {
            future = new CompletableFuture<>();
            Intent intent = new Intent(context, NebulaService.class);
            intent.putExtra("FIXED_IPS", fixedIps);
            context.startForegroundService(intent);
            future.get(20, TimeUnit.SECONDS);
            ParcelFileDescriptor pfd = NebulaService.vpnInterface;
            if (pfd != null) {
                return pfd.getFd(); // 成功：返回 FD 供 Go 读写数据包
            }
        }

        throw new Exception("getTunId failed");
    }

    @Override
    public String advertiseIPs() throws Exception {
        return String.join(", ", getRawLocalIpList());
    }

    public AndroidTunProvider(Context context, String[] fixedIps) {
        this.context = context.getApplicationContext();
        this.fixedIps = fixedIps;
    }

    /**
     * 获取经过优化的 P2P 候选地址列表
     * 逻辑：
     * 1. 自动消除 IPv4-mapped IPv6 (::ffff:x.x.x.x)
     * 2. 过滤 10.133.0.0/16 私有网段
     * 3. 局域网 fe80 地址保留 ScopeID 以提升连通率
     * 4. 优先上报随机/临时 IPv6 地址，仅在无临时地址时上报 EUI-64 永久地址
     */
    public List<String> getRawLocalIpList() throws SocketException, UnknownHostException {
        List<String> addresses = new ArrayList<>();
        List<String> ipv6Temporary = new ArrayList<>();
        List<String> ipv6Permanent = new ArrayList<>();


        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) {
            return addresses;
        }

        while (interfaces.hasMoreElements()) {
            NetworkInterface nif = interfaces.nextElement();

            // 过滤未启动、环回接口
            if (!nif.isUp() || nif.isLoopback()) {
                continue;
            }

            Enumeration<InetAddress> inetAddresses = nif.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress addr = inetAddresses.nextElement();

                if (addr.isLoopbackAddress()) {
                    continue;
                }

                // 1. 核心 API 归一化：通过原始字节重建对象，彻底消除 ::ffff: 等映射 IP 的干扰
                InetAddress normalizedAddr = InetAddress.getByAddress(addr.getAddress());
                String hostAddress = normalizedAddr.getHostAddress();
                if (hostAddress == null) {
                    continue;
                }

                // 2. 处理 IPv4 逻辑
                if (normalizedAddr instanceof Inet4Address) {
                    // 过滤指定的 10.133. 网段
                    if (!hostAddress.startsWith("10.133.")) {
                        addresses.add(hostAddress);
                    }
                }
                // 3. 处理 IPv6 逻辑
                else if (normalizedAddr instanceof Inet6Address) {
                    Inet6Address v6Addr = (Inet6Address) normalizedAddr;

                    // 链路本地地址 (fe80:) 对局域网 P2P 极其重要，必须保留 Scope ID (%)
                    if (v6Addr.isLinkLocalAddress()) {
                        addresses.add(hostAddress);
                    } else {
                        // 公网单播地址：去掉百分号后缀，方便跨网连接
                        if (hostAddress.contains("%")) {
                            hostAddress = hostAddress.substring(0, hostAddress.indexOf("%"));
                        }

                        // 4. 区分隐私地址与永久地址
                        if (isEui64PermanentAddress(v6Addr)) {
                            ipv6Permanent.add(hostAddress);
                        } else {
                            ipv6Temporary.add(hostAddress);
                        }
                    }
                }
            }
        }

        // 5. 最终组合逻辑：成功率第一，隐私优先
        if (!ipv6Temporary.isEmpty()) {
            // 只要有临时/随机地址，就完全隐藏永久地址
            addresses.addAll(ipv6Temporary);
        } else {
            // 只有在完全没有临时地址的情况下，才上报永久地址作为连接兜底
            addresses.addAll(ipv6Permanent);
        }

        return addresses;
    }

    /**
     * 判定是否为基于 EUI-64 规范生成的永久 IPv6 地址。
     * 特征：在 IPv6 地址数组的第 11、12 字节固定包含 0xFF, 0xFE
     */
    private boolean isEui64PermanentAddress(Inet6Address addr) {
        byte[] bytes = addr.getAddress();
        // 标准 IPv6 字节数组长度为 16
        return bytes.length == 16 &&
                (bytes[11] & 0xFF) == 0xFF &&
                (bytes[12] & 0xFF) == 0xFE;
    }
}
