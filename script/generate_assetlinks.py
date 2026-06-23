import os
import sys
import subprocess
import json

# 接收从 GitHub Actions 或命令行传进来的参数
APK_PATH = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/release/app-release.apk"
OUTPUT_PATH = sys.argv[2] if len(sys.argv) > 2 else "./assetlinks.json"

ANDROID_SDK = os.environ.get("ANDROID_HOME", "d:/Android/Sdk")
APKANALYZER = "apkanalyzer" if os.name != 'nt' else os.path.join(ANDROID_SDK, "cmdline-tools/latest/bin/apkanalyzer.bat")

def get_apk_info():
    # 1. 动态提取最终 APK 的真实 applicationId
    pkg_cmd = [APKANALYZER, "manifest", "application-id", APK_PATH]
    package_name = subprocess.check_output(pkg_cmd).decode('utf-8').strip()

    # 2. 动态提取最终 APK 的 Release 签名指纹
    cert_cmd = [APKANALYZER, "apk", "summary", APK_PATH]
    summary = subprocess.check_output(cert_cmd).decode('utf-8')

    sha256 = ""
    for line in summary.splitlines():
        if "certs:" in line or "SHA-256" in line:
            sha256 = line.split()[-1].strip().upper()
            break

    return package_name, sha256

def generate_json(package_name, sha256):
    # 严格拆分为两个独立的 Target 配置块，形成标准的互不干扰数组
    assetlinks = [
        {
            "relation": ["delegate_permission/common.handle_all_urls"],
            "target": {
                "namespace": "android_app",
                "package_name": package_name,
                "sha256_cert_fingerprints": [sha256]
            }
        },
        {
            "relation": ["delegate_permission/common.handle_all_urls"],
            "target": {
                "namespace": "android_app",
                "package_name": package_name, # 动态对齐包名，防止你以后改包名
                "sha256_cert_fingerprints": [
                    "24:E8:2F:19:59:66:A4:32:FC:79:CB:69:78:78:69:B6:05:C3:7E:88:40:91:90:37:A9:4D:E9:63:B3:CE:D3:4C"
                ]
            }
        }
    ]

    os.makedirs(os.path.dirname(os.path.abspath(OUTPUT_PATH)), exist_ok=True)
    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(assetlinks, f, indent=2, ensure_ascii=False)
    print(f"==================================================")
    print(f"✅ AssetLinks 动态双通道注入成功！")
    print(f"📦 目标包名: {package_name}")
    print(f"🚀 线上 Release 指纹: {sha256}")
    print(f"💻 本地 Debug   指纹: 24:E8:2F...D3:4C")
    print(f"==================================================")

if __name__ == "__main__":
    package_name, sha256 = get_apk_info()
    generate_json(package_name, sha256)