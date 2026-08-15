#!/bin/bash
# ============================================================
# TGZJDVchat 构建脚本
# 使用方法: bash build.sh <gradle任务> (如: bash build.sh build)
# ============================================================

# 显式设置 Linux 原生工具链（避免 Windows PATH 干扰）
export JAVA_HOME="$HOME/jdk-25.0.2"
export GRADLE_HOME="$HOME/tools/gradle-9.7.0"
export PATH="$JAVA_HOME/bin:$GRADLE_HOME/bin:$PATH"

# 切换到项目根目录
cd "$(dirname "$0")"

echo "=== TGZJDVchat 构建 ==="
echo "Java:   $(java -version 2>&1 | head -1)"
echo "Gradle: $(gradle --version 2>/dev/null | grep -m1 'Gradle ')"

# 执行 Gradle
exec gradle "$@"
