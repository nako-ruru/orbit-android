import os
import subprocess
import argparse
import sys
from huggingface_hub import InferenceClient


def main():
    parser = argparse.ArgumentParser(description="Pure AI Release Notes Generator")
    parser.add_argument("--old", required=True, help="Old commit/tag")
    parser.add_argument("--new", required=True, help="New commit/tag")
    parser.add_argument("--repo", default=".", help="Repo path")
    parser.add_argument("--output", default="ai_content.md", help="Output file")
    args = parser.parse_args()

    # 2. 提取 Git Diff
    diff_cmd = ["git", "-C", args.repo, "diff", args.old, args.new, "--unified=0", "--no-prefix", "--ignore-all-space"]
    result = subprocess.run(diff_cmd, capture_output=True, text=True, encoding="utf-8", errors="ignore")
    diff_text = result.stdout.strip()

    if not diff_text:
        print("⚠️ No changes detected.");
        sys.exit(0)

    # 3. 调用 AI
    token = os.getenv("HF_API_TOKEN")
    if not token:
        print("❌ Error: HF_API_TOKEN is missing.");
        sys.exit(1)

    client = InferenceClient(api_key=token)
    prompt = f"Please generate professional release notes in English and Chinese based on this git diff:\n\n{diff_text[:8000]}"

    try:
        response = client.chat_completion(
            model="meta-llama/Llama-3.1-8B-Instruct",
            messages=[{"role": "user", "content": prompt}],
            max_tokens=1000
        )
        content = response.choices[0].message.content

        # 4. 写入独立文件
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"✅ AI content saved to {args.output}")

    except Exception as e:
        print(f"❌ AI Error: {e}");
        sys.exit(1)


if __name__ == "__main__":
    main()
