import os
import subprocess
import argparse
import sys
from google import genai

def main():
    parser = argparse.ArgumentParser(description="Pure AI Release Notes Generator using Gemini API")
    parser.add_argument("--old", required=True, help="Old commit/tag")
    parser.add_argument("--new", required=True, help="New commit/tag")
    parser.add_argument("--repo", default=".", help="Repo path")
    parser.add_argument("--output", default="ai_content.md", help="Output file")
    args = parser.parse_args()

    # 1. 提取 Git Diff
    diff_cmd = ["git", "-C", args.repo, "diff", args.old, args.new, "--unified=0", "--no-prefix", "--ignore-all-space"]
    result = subprocess.run(diff_cmd, capture_output=True, text=True, encoding="utf-8", errors="ignore")
    diff_text = result.stdout.strip()

    if not diff_text:
        print("⚠️ No changes detected.")
        sys.exit(0)

    # 2. 获取 API Key (支持 GEMINI_API_KEY 或 GOOGLE_API_KEY)
    api_key = os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY")
    if not api_key:
        print("❌ Error: GEMINI_API_KEY (or GOOGLE_API_KEY) is missing.")
        sys.exit(1)

    # 3. 调用官方 google-genai SDK 客户端
    # 使用用户指定的 gemini-2.5-flash 或 gemini-3.5-flash / gemini-2.5-flash-lite / gemini-3.5-flash-lite
    model_name = os.getenv("GEMINI_MODEL", "gemini-3.5-flash-lite")
    
    client = genai.Client(api_key=api_key)
    
    prompt = f"""You are a professional technical release notes assistant. Please generate structured, clear, and professional release notes in both English and Chinese based on the following git diff differences. Focus on new features, bug fixes, performance improvements, and critical changes.

Git Diff:
{diff_text[:100000]}
"""

    try:
        response = client.models.generate_content(
            model=model_name,
            contents=prompt,
        )
        content = response.text

        # 4. 写入输出文件
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"✅ AI content successfully generated and saved to {args.output} using Gemini model: {model_name}")

    except Exception as e:
        print(f"❌ Gemini AI Error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
