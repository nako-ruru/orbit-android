import os
import re
import sys

# ================= 配置清单 (精确白名单) =================
# 只需要保留的后端
KEEP_BACKENDS = ['webdav']

# 只需要保留的命令
# 注意：要把 copy 和 version 留着，其他没用的 serve/test 都要干掉
KEEP_CMDS = ['copy', 'version']
# =======================================================

# 增加了 rclone_base_path 参数
def prune_file(rclone_base_path, filepath, whitelist, type_name):
    # 使用 os.path.join 将基础路径和相对路径拼接起来
    # 比如 rclone_base_path 是 "app-mirror/src/main/go/rclone"
    # filepath 是 "fs/all/all.go"
    # 拼接后就是完整的绝对或相对路径
    full_path = os.path.join(rclone_base_path, filepath)

    print(f"rclone_base_path: {rclone_base_path}")

    with open(full_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    # 后面是你的裁剪逻辑...
    print(f"正在处理 {type_name}: {full_path}")

    new_lines = []
    count = 0

    # 匹配模式：_ "github.com/rclone/rclone/backend/..."
    pattern = fr'\"github\.com/rclone/rclone/{type_name}/([^\"]+)\"'

    for line in lines:
        clean_line = line.strip()

        # 只有包含项目的 import 行才处理
        match = re.search(pattern, clean_line)
        if match:
            import_path = match.group(1) # 例如 "serve/webdav" 或 "webdav"

            # 检查这个路径的根部是否在白名单里
            # 例如 "serve/webdav" 的根是 "serve"
            root_name = import_path.split('/')[0]

            if import_path not in whitelist and root_name not in whitelist:
                # 如果既不是精确匹配，也不是根部匹配，且还没被注释，就注释掉
                if not clean_line.startswith("//"):
                    new_lines.append(line.replace('_ "', '// _ "'))
                    count += 1
                    continue

        new_lines.append(line)

    with open(full_path, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)
    print(f"已处理 {filepath}: 禁用了 {count} 个模块")

if __name__ == "__main__":

    if len(sys.argv) < 2:
        print("Usage: python3 rclone-trim.py <rclone_source_root>")
        sys.exit(1)
        # 接收命令行传入的第一个参数
    rclone_path = sys.argv[1]

    # 执行裁剪
    prune_file(rclone_path, 'backend/all/all.go', KEEP_BACKENDS, 'backend')
    prune_file(rclone_path, 'cmd/all/all.go', KEEP_CMDS, 'cmd')
