#!/bin/sh
set -e

PYSCRIPT_DIR="/opt/pyscript"
VENV_DIR="${PYSCRIPT_DIR}/venv"
REQUIREMENTS="${PYSCRIPT_DIR}/sc/requirements.txt"

# 检测 venv 是否可用（符号链接是否指向有效的 python3）
venv_ok=false
if [ -x "${VENV_DIR}/bin/python3" ]; then
    if "${VENV_DIR}/bin/python3" -c "import sys" 2>/dev/null; then
        venv_ok=true
    fi
fi

if [ "$venv_ok" = "false" ]; then
    echo "[entrypoint] Python虚拟环境不可用，正在重建..."
    rm -rf "${VENV_DIR}"
    python3 -m venv "${VENV_DIR}"
    if [ -f "${REQUIREMENTS}" ]; then
        echo "[entrypoint] 安装依赖: ${REQUIREMENTS}"
        "${VENV_DIR}/bin/pip" install -r "${REQUIREMENTS}"
    fi
    echo "[entrypoint] 虚拟环境重建完成"
else
    echo "[entrypoint] Python虚拟环境正常"
fi

# 启动 Java 应用
exec java -jar app.jar