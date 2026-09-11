package com.orbit;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.util.Log;

import com.easytier.jni.EasyTierJNI;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import aar.Aar;
import aar.PrivNetwork;
import aar.PrivNetworkProvider;

import java.util.HashMap;
import java.util.Map;

public class AndroidPrivNetworkProvider implements PrivNetworkProvider {

    private final Context context;

    public AndroidPrivNetworkProvider(Context context) {
        Log.i("AndroidPrivNetworkProvider", "AndroidPrivNetworkProvider init");
        this.context = context.getApplicationContext();
    }

    @Override
    public PrivNetwork getPrivNetwork() throws Exception {
        return ExceptionProxyUtils.wrapWithStackMessageException(PrivNetwork.class, new AndroidPrivNetwork(this.context));
    }
}

class AndroidPrivNetwork implements PrivNetwork {

    private final Context context;

    // 核心：所有 JNI 操作共享同一个进程级别的静态锁！
    private static final Object JNI_LOCK = new Object();
    
    // 会话与 Peers 的映射管理
    private final Map<String, List<String>> sessionPeers = new HashMap<>();

    AndroidPrivNetwork(Context context) {
        this.context = context;
    }

    @Override
    public void start() throws Exception {
        Log.i("AndroidPrivNetwork", "0.0 enter start()");
        
        Log.i("AndroidPrivNetwork", "1. about to call Aar.selectCandidateIPs");
        String ipsJson = Aar.selectCandidateIPs("", "", getLocalIpNetsJson());
        Log.i("AndroidPrivNetwork", "1.1 Aar.selectCandidateIPs success, result: " + ipsJson);

        JSONObject ipsObj = new JSONObject(ipsJson);
        String vpnIp = ipsObj.optString("ipv4", "");
        String vpnIpv6 = ipsObj.optString("ipv6", "");

        Log.i("AndroidPrivNetwork", "2. about to call Aar.randDualPortsJSON");
        String portsJson = Aar.randDualPortsJSON("40000-60000", 3);
        Log.i("AndroidPrivNetwork", "2.1 Aar.randDualPortsJSON success, result: " + portsJson);

        Log.i("AndroidPrivNetwork", "2. randDualPortsJSON parsed");
        JSONArray portsArr = new JSONArray(portsJson);
        int udpPort = portsArr.getInt(0);
        int quicPort = portsArr.getInt(1);
        int wssPort = portsArr.getInt(1); // QUIC 和 WSS 共享端口

        String ipv4Config = "", ipv6Config = "", proxyNetworkConfig = "";
        if(!vpnIp.isEmpty()) {
           ipv4Config = String.format("ipv4 = \"%s/26\"", vpnIp) ;
           proxyNetworkConfig =  String.format("[[proxy_network]]\ncidr = \"%s\"", vpnIp);
        }
        if(!vpnIpv6.isEmpty()) {
            ipv6Config = String.format("ipv6 = \"%s\"", vpnIpv6);
        }

        String easytierConf = String.format("""
                instance_name = "easytier"
                instance_id = "19411df7-3589-4a54-902f-cf7649d008fa"

                %s
                %s
                listeners = [
                	   "udp://0.0.0.0:%d",
                	   "quic://0.0.0.0:%d",
                	   "wss://0.0.0.0:%d",
                ]

                [network_identity]
                network_name = "abc"
                network_secret = ""

                %s

                [acl.acl_v1]
                [[acl.acl_v1.chains]]
                name = "Inbound"
                chain_type = "Inbound"
                enabled = true
                default_action = "Drop"
                """, ipv4Config, ipv6Config, udpPort, quicPort, wssPort, proxyNetworkConfig
        );

        // 1. JNI 启动实例（使用全局静态锁保护 C 底层全局状态）
        Log.i("AndroidPrivNetwork", "3. running EasyTier network instance: " + easytierConf);
        synchronized (JNI_LOCK) {
            int res = EasyTierJNI.runNetworkInstance(easytierConf);
            if (res != 0) {
                String error = EasyTierJNI.getLastError();
                Log.e("AndroidPrivNetwork", "Network instance start failed: " + error);
                throw new RuntimeException("网络实例启动失败: " + error);
            }
        }
        Log.i("AndroidPrivNetwork", "4. EasyTier network instance started successfully");

        // 2. 检查并请求 VPN 权限
        Log.i("AndroidPrivNetwork", "5. requesting VPN permission if needed");
        CompletableFuture<Boolean> vpnFuture = PrivNetworkPermissionManager.requestPermissionIfNeeded(context);
        if (vpnFuture != null) {
            Boolean granted = vpnFuture.get(1, TimeUnit.MINUTES);
            if (!Boolean.TRUE.equals(granted)) {
                Log.e("AndroidPrivNetwork", "VPN permission denied by user");
                throw new IllegalStateException("用户拒绝了 VPN 权限授权");
            }
        }
        Log.i("AndroidPrivNetwork", "6. VPN permission granted");

        // 3. 启动前台服务
        Log.i("AndroidPrivNetwork", "7. starting foreground service PrivNetworkService");
        Intent intent = new Intent(context, PrivNetworkService.class);
        List<String> ips = new ArrayList<>();
        if (!vpnIp.isEmpty()) ips.add(vpnIp);
        if (!vpnIpv6.isEmpty()) ips.add(vpnIpv6);
        String[] fixedIps = ips.toArray(new String[0]);
        intent.putExtra("FIXED_IPS", fixedIps);
        context.startForegroundService(intent);
        Log.i("AndroidPrivNetwork", "8. start() completed successfully");
    }

    @Override
    public void terminate() throws Exception {
        synchronized (JNI_LOCK) {
            int res = EasyTierJNI.deleteNetworkInstance("easytier");
            if (res != 0) {
                String error = EasyTierJNI.getLastError();
                throw new RuntimeException("网络实例停止失败: " + error);
            }
        }
    }

    @Override
    public String getIPs() throws Exception {
        synchronized (JNI_LOCK) {
            JSONObject selectorObj = new JSONObject();
            selectorObj.put("name", "easytier");
            JSONObject instanceSelectorObj = new JSONObject();
            instanceSelectorObj.put("instance_selector", selectorObj);
            JSONObject payload = new JSONObject();
            payload.put("instance", instanceSelectorObj);

            String nodeConfigStr = EasyTierJNI.callJsonRpc("api.config.ConfigRpcService", "GetConfig", "easytier", payload.toString());

            // 修复：RPC 响应可能包含多个 JSON 对象（如果后端返回了日志或错误），只取第一个完整 JSON 对象
            if (nodeConfigStr != null) {
                nodeConfigStr = nodeConfigStr.trim();
                // 修复：RPC 响应如果包含非 JSON 内容，寻找第一个 '{' 和最后一个 '}' 之间的内容
                int firstBrace = nodeConfigStr.indexOf('{');
                int lastBrace = nodeConfigStr.lastIndexOf('}');
                if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                    nodeConfigStr = nodeConfigStr.substring(firstBrace, lastBrace + 1);
                }
            }

            JSONObject resp = new JSONObject(nodeConfigStr);

            List<String> ips = new ArrayList<>();

            // 如果还是没有，尝试从 toml_config 字符串中解析

            String tomlConfig = resp.optString("toml_config", "");
            String[] lines = tomlConfig.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("ipv4")) {
                    String[] parts = line.split("=");
                    if (parts.length > 1) {
                        String val = parts[1].trim().replace("\"", "");
                        if (val.contains("/")) {
                            val = val.split("/")[0];
                        }
                        if (!val.isEmpty() && !ips.contains(val)) {
                            ips.add(val);
                        }
                    }
                } else if (line.startsWith("ipv6")) {
                    String[] parts = line.split("=");
                    if (parts.length > 1) {
                        String val = parts[1].trim().replace("\"", "");
                        if (val.contains("/")) {
                            val = val.split("/")[0];
                        }
                        if (!val.isEmpty() && !ips.contains(val)) {
                            ips.add(val);
                        }
                    }
                }
            }
            return new JSONArray(ips).toString();
        }
    }

    @Override
    public void createSession(String sessionConfigJson) throws Exception {
        synchronized (JNI_LOCK) {
            // 参数 sessionConfigJson 即为 PC 版传递的 SessionConfig 序列化后的 JSON
            JSONObject config = new JSONObject(sessionConfigJson);
            String sessionId = config.getString("SessionID");
            
            // 1. 获取 Instance 实例选择器
            JSONObject instSel = new JSONObject();
            instSel.put("name", "easytier");
            JSONObject inst = new JSONObject();
            inst.put("instance_selector", instSel);

            // 2. 获取当前配置并合并 Connector 与 ACL Patch 到同一个请求中
            JSONObject nodePayload = new JSONObject();
            nodePayload.put("instance", inst);
            String nodeConfigStr = EasyTierJNI.callJsonRpc("api.config.ConfigRpcService", "GetConfig", "easytier", nodePayload.toString());
            JSONObject nodeConfig = new JSONObject(nodeConfigStr);
            JSONObject configObj = nodeConfig.optJSONObject("config");
            JSONObject currentAcl = configObj != null ? configObj.optJSONObject("acl") : null;

            List<String> peerList = new ArrayList<>();
            JSONArray peers = config.optJSONArray("Peers");
            JSONArray connectorsArr = new JSONArray();
            if (peers != null) {
                for (int i = 0; i < peers.length(); i++) {
                    String peer = peers.getString(i);
                    peerList.add(peer);

                    JSONObject commonUrlObj = new JSONObject();
                    commonUrlObj.put("url", peer);

                    JSONObject urlPatchObj = new JSONObject();
                    urlPatchObj.put("action", 0); // 0 = Add
                    urlPatchObj.put("url", commonUrlObj);
                    connectorsArr.put(urlPatchObj);
                }
            }
            sessionPeers.put(sessionId, peerList);

            JSONObject sessionAcl = config.optJSONObject("ACL");
            JSONObject innerAcl = null;
            if (sessionAcl != null) {
                if (currentAcl == null) {
                    currentAcl = new JSONObject();
                }
                JSONObject aclV1 = currentAcl.optJSONObject("acl_v1");
                if (aclV1 == null) aclV1 = new JSONObject();
                
                // Group 处理
                JSONObject group = aclV1.optJSONObject("group");
                if (group == null) group = new JSONObject();
                
                JSONArray declares = group.optJSONArray("declares");
                if (declares == null) declares = new JSONArray();
                
                JSONObject newDeclare = new JSONObject();
                newDeclare.put("group_name", config.getString("SessionID"));
                newDeclare.put("group_secret", sessionAcl.getString("Secret"));
                declares.put(newDeclare);
                group.put("declares", declares);
                
                JSONArray members = group.optJSONArray("members");
                if (members == null) members = new JSONArray();
                members.put(config.getString("SessionID"));
                group.put("members", members);
                
                aclV1.put("group", group);
                
                // Inbound 规则处理
                if (sessionAcl.optInt("Direction") == 0) {
                    JSONArray chains = aclV1.optJSONArray("chains");
                    if (chains == null) chains = new JSONArray();
                    
                    JSONObject inboundChain = null;
                    for (int i = 0; i < chains.length(); i++) {
                        if ("Inbound".equals(chains.getJSONObject(i).optString("name"))) {
                            inboundChain = chains.getJSONObject(i);
                            break;
                        }
                    }
                    
                    if (inboundChain == null) {
                        inboundChain = new JSONObject();
                        inboundChain.put("name", "Inbound");
                        inboundChain.put("chain_type", "Inbound");
                        inboundChain.put("enabled", true);
                        inboundChain.put("default_action", "Drop");
                        inboundChain.put("rules", new JSONArray());
                        chains.put(inboundChain);
                    }
                    
                    JSONArray rules = inboundChain.optJSONArray("rules");
                    if (rules == null) rules = new JSONArray();
                    
                    JSONObject newRule = new JSONObject();
                    newRule.put("priority", 1);
                    newRule.put("enabled", true);
                    newRule.put("protocol", 5);
                    newRule.put("action", "Allow");
                    newRule.put("ports", sessionAcl.optJSONArray("AllowedPorts"));
                    JSONArray sourceGroups = new JSONArray();
                    sourceGroups.put(config.getString("SessionID"));
                    newRule.put("source_groups", sourceGroups);
                    rules.put(newRule);
                    
                    inboundChain.put("rules", rules);
                    aclV1.put("chains", chains);
                }
                
                innerAcl = new JSONObject();
                innerAcl.put("acl_v1", aclV1);
            }

            // 构建合并后的 Patch 请求
            JSONObject patchObj = new JSONObject();
            if (connectorsArr.length() > 0) {
                patchObj.put("connectors", connectorsArr);
            }
            if (innerAcl != null) {
                JSONObject patchAcl = new JSONObject();
                patchAcl.put("acl", innerAcl);
                patchObj.put("acl", patchAcl);
            }

            if (patchObj.length() > 0) {
                JSONObject requestObj = new JSONObject();
                requestObj.put("instance", inst);
                requestObj.put("patch", patchObj);

                String res = EasyTierJNI.callJsonRpc("api.config.ConfigRpcService", "PatchConfig", "easytier", requestObj.toString());
                Log.i("AndroidPrivNetwork", "PatchConfig Combined (Connectors & ACL) result: " + res);
            }

            // 复核 Connector 是否添加成功并打印当前 Config 与 peers 映射状态
            try {
                JSONObject listReq = new JSONObject();
                listReq.put("instance", inst);
                String listRes = EasyTierJNI.callJsonRpc("api.instance.ConnectorManageRpcService", "ListConnector", "easytier", listReq.toString());
                Log.i("AndroidPrivNetwork", "Verify ListConnector result after createSession: " + listRes);

                String verifyConfigStr = EasyTierJNI.callJsonRpc("api.config.ConfigRpcService", "GetConfig", "easytier", listReq.toString());
                Log.i("AndroidPrivNetwork", "Verify GetConfig after createSession: " + verifyConfigStr);
                Log.i("AndroidPrivNetwork", "Current sessionPeers mapping: " + sessionPeers.toString());
            } catch (Exception e) {
                Log.e("AndroidPrivNetwork", "Failed to verify connectors or config", e);
            }
        }
    }

    @Override
    public void closeSession(String sessionId) throws Exception {
        synchronized (JNI_LOCK) {
            Log.i("AndroidPrivNetwork", "closeSession: " + sessionId);
            
            JSONObject instSel = new JSONObject();
            instSel.put("name", "easytier");
            JSONObject inst = new JSONObject();
            inst.put("instance_selector", instSel);
            
            // 1. 获取当前配置
            String nodeConfigStr = EasyTierJNI.callJsonRpc("api.config.ConfigRpcService", "GetConfig", "easytier", new JSONObject().put("instance", inst).toString());
            JSONObject nodeConfig = new JSONObject(nodeConfigStr);
            JSONObject configObj = nodeConfig.optJSONObject("config");
            JSONObject currentAcl = configObj != null ? configObj.optJSONObject("acl") : null;
            JSONObject aclV1 = currentAcl != null ? currentAcl.optJSONObject("acl_v1") : null;
            
            // 2. 准备移除 Connector 数组（仅当 peer 不再被其他任何存活 session 占用时才移除）
            List<String> peers = sessionPeers.remove(sessionId);
            JSONArray connectorsArr = new JSONArray();
            if (peers != null && !peers.isEmpty()) {
                for (String peer : peers) {
                    boolean usedByOther = false;
                    for (List<String> otherPeers : sessionPeers.values()) {
                        if (otherPeers != null && otherPeers.contains(peer)) {
                            usedByOther = true;
                            break;
                        }
                    }

                    if (!usedByOther) {
                        JSONObject commonUrlObj = new JSONObject();
                        commonUrlObj.put("url", peer);

                        JSONObject urlPatchObj = new JSONObject();
                        urlPatchObj.put("action", 1); // 1 = Remove
                        urlPatchObj.put("url", commonUrlObj);
                        connectorsArr.put(urlPatchObj);
                        Log.i("AndroidPrivNetwork", "Peer " + peer + " is not used by other sessions, scheduling removal.");
                    } else {
                        Log.i("AndroidPrivNetwork", "Peer " + peer + " is still used by other sessions, skipping removal.");
                    }
                }
            }
            
            // 3. 准备清理 ACL
            JSONObject innerAcl = null;
            if (aclV1 != null) {
                JSONObject group = aclV1.optJSONObject("group");
                if (group != null) {
                    JSONArray declares = group.optJSONArray("declares");
                    JSONArray newDeclares = new JSONArray();
                    if (declares != null) {
                        for(int i=0; i<declares.length(); i++) {
                            if (!sessionId.equals(declares.getJSONObject(i).getString("group_name"))) {
                                newDeclares.put(declares.getJSONObject(i));
                            }
                        }
                    }
                    group.put("declares", newDeclares);
                    
                    JSONArray members = group.optJSONArray("members");
                    JSONArray newMembers = new JSONArray();
                    if (members != null) {
                        for(int i=0; i<members.length(); i++) {
                            if (!sessionId.equals(members.getString(i))) {
                                newMembers.put(members.getString(i));
                            }
                        }
                    }
                    group.put("members", newMembers);
                    
                    aclV1.put("group", group);
                }
                
                JSONArray chains = aclV1.optJSONArray("chains");
                if (chains != null) {
                    JSONArray newChains = new JSONArray();
                    for(int i=0; i<chains.length(); i++) {
                        JSONObject chain = chains.getJSONObject(i);
                        JSONArray rules = chain.optJSONArray("rules");
                        if (rules != null) {
                            JSONArray newRules = new JSONArray();
                            for(int j=0; j<rules.length(); j++) {
                                JSONObject rule = rules.getJSONObject(j);
                                JSONArray sgs = rule.optJSONArray("source_groups");
                                boolean keep = true;
                                if (sgs != null) {
                                    for(int k=0; k<sgs.length(); k++) {
                                        if (sessionId.equals(sgs.getString(k))) {
                                            keep = false;
                                            break;
                                        }
                                    }
                                }
                                if (keep) newRules.put(rule);
                            }
                            chain.put("rules", newRules);
                        }
                        newChains.put(chain);
                    }
                    aclV1.put("chains", newChains);
                }
                
                innerAcl = new JSONObject();
                innerAcl.put("acl_v1", aclV1);
            }
            
            // 4. 构建合并后的 Patch 请求
            JSONObject patchObj = new JSONObject();
            if (connectorsArr.length() > 0) {
                patchObj.put("connectors", connectorsArr);
            }
            if (innerAcl != null) {
                JSONObject patchAcl = new JSONObject();
                patchAcl.put("acl", innerAcl);
                patchObj.put("acl", patchAcl);
            }
            
            if (patchObj.length() > 0) {
                JSONObject requestObj = new JSONObject();
                requestObj.put("instance", inst);
                requestObj.put("patch", patchObj);
                
                String res = EasyTierJNI.callJsonRpc("api.config.ConfigRpcService", "PatchConfig", "easytier", requestObj.toString());
                Log.i("AndroidPrivNetwork", "PatchConfig Combined CloseSession (Connectors & ACL) result: " + res);
            }

            // 5. 复核并打印当前最新配置与 peers 映射状态
            try {
                JSONObject verifyReq = new JSONObject();
                verifyReq.put("instance", inst);
                String verifyRes = EasyTierJNI.callJsonRpc("api.config.ConfigRpcService", "GetConfig", "easytier", verifyReq.toString());
                Log.i("AndroidPrivNetwork", "Verify GetConfig result after closeSession: " + verifyRes);

                String listRes = EasyTierJNI.callJsonRpc("api.instance.ConnectorManageRpcService", "ListConnector", "easytier", verifyReq.toString());
                Log.i("AndroidPrivNetwork", "Verify ListConnector result after closeSession: " + listRes);
                Log.i("AndroidPrivNetwork", "Current sessionPeers mapping after closeSession: " + sessionPeers.toString());
            } catch (Exception e) {
                Log.e("AndroidPrivNetwork", "Failed to verify config after closeSession", e);
            }
        }
    }

    private String getLocalIpNetsJson() throws SocketException {
        JSONArray jsonArray = new JSONArray();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

        // 1. 防空检查：部分硬件或极特殊状态下可能返回 null
        if (interfaces == null) {
            return jsonArray.toString();
        }

        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();

            // 注意：此处不过滤 !ni.isUp() 和 ni.isLoopback()
            // 确保已被占用或未来可能被激活的网卡网段全量收集

            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress addr = ia.getAddress();
                if (addr == null) {
                    continue;
                }

                String hostAddress = addr.getHostAddress();
                if (hostAddress == null) {
                    continue;
                }

                // 2. 剥离 IPv6 链路本地地址带有的 %ScopeID（如 %rmnet_data0、%eth0）
                // 保证输出标准的 CIDR 格式，便于后端解析或网段碰撞计算
                int percentPos = hostAddress.indexOf('%');
                if (percentPos != -1) {
                    hostAddress = hostAddress.substring(0, percentPos);
                }

                short prefixLength = ia.getNetworkPrefixLength();

                // 3. 过滤掉前缀长度异常的情况（极少数虚拟网卡会返回负数）
                if (prefixLength >= 0) {
                    jsonArray.put(hostAddress + "/" + prefixLength);
                }
            }
        }

        return jsonArray.toString();
    }
}

class PrivNetworkPermissionManager {

    private static final Object LOCK = new Object();
    private static volatile CompletableFuture<Boolean> activeFuture = null;

    public static CompletableFuture<Boolean> requestPermissionIfNeeded(Context context) {
        if (VpnService.prepare(context) == null) {
            return null;
        }

        synchronized (LOCK) {
            if (activeFuture == null || activeFuture.isDone()) {
                activeFuture = new CompletableFuture<>();

                Intent intent = new Intent(context, TransparentProxyActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
            return activeFuture;
        }
    }

    public static void complete(boolean success) {
        synchronized (LOCK) {
            if (activeFuture != null && !activeFuture.isDone()) {
                activeFuture.complete(success);
            }
        }
    }
}
