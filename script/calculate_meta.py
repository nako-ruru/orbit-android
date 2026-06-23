import os
import re
import json
import subprocess

def get_last_release_data():
    """安全地获取上一次 Release 的数据，失败则返回空默认值"""
    try:
        # 直接调用 gh CLI 获取最新一次 release 的 json 数据
        result = subprocess.check_output(
            "gh release view --json tagName,body",
            shell=True,
            stderr=subprocess.DEVNULL
        ).decode('utf-8').strip()

        data = json.loads(result)
        return data.get("tagName", "v0"), data.get("body", "")
    except Exception:
        # 如果是项目第一次发布，或者找不到任何 Release，返回初始默认值
        return "v0", ""

def main():
    last_tag, body = get_last_release_data()

    # 1. 赋默认初始值（防止第一次运行或匹配失败时崩掉）
    old_vc = 0
    old_notify = 0
    old_force = 0

    # 2. 用正则精准跨行提取隐藏的元数据
    if body:
        vc_match = re.search(r"versionCode=(\d+)", body)
        notify_match = re.search(r"lastNotifyVersionCode=(\d+)", body)
        force_match = re.search(r"lastForceVersionCode=(\d+)", body)

        if vc_match: old_vc = int(vc_match.group(1))
        if notify_match: old_notify = int(notify_match.group(1))
        if force_match: old_force = int(force_match.group(1))

    # 3. 核心业务逻辑计算
    # 规则 1: 本次 versionCode = 上次 + 1 (如果是第一次则为 1)
    current_vc = old_vc + 1 if old_vc > 0 else 900  # 注：你可以指定一个初始基准值，比如 900
    if old_vc == 0:
        current_vc = 1 # 纯初始白纸状态

    # 从 GitHub Actions 传进来的环境变量中读取布尔值
    is_notify = os.environ.get("INPUT_NOTIFY", "false").lower() == "true"
    is_force = os.environ.get("INPUT_FORCE", "false").lower() == "true"

    # 规则 2: Notify 逻辑
    current_notify = current_vc if is_notify else (old_notify if old_notify > 0 else current_vc)

    # 规则 3: Force 逻辑
    current_force = current_vc if is_force else old_force

    # 4. 导出给 GitHub Actions 的环境变量
    github_env = os.environ.get("GITHUB_ENV")
    if github_env:
        with open(github_env, "a", encoding="utf-8") as f:
            f.write(f"VC={current_vc}\n")
            f.write(f"LAST_NOTIFY_VC={current_notify}\n")
            f.write(f"LAST_FORCE_VC={current_force}\n")
            f.write(f"LAST_TAG={last_tag}\n")

    print(f"--- 计算完毕 ---")
    print(f"Calculated VC: {current_vc}")
    print(f"Calculated Notify VC: {current_notify}")
    print(f"Calculated Force VC: {current_force}")

if __name__ == "__main__":
    main()