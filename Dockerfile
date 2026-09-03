# 影视数据源服务 —— 完整版镜像（ffmpeg + mediamtx(RTSP) + tesseract OCR 中文）
FROM python:3.13-slim-bookworm

# ---- 系统依赖：ffmpeg(转流/抽帧) + tesseract(OCR) + curl(下载 mediamtx) ----
RUN apt-get update && apt-get install -y --no-install-recommends \
        ffmpeg \
        tesseract-ocr \
        tesseract-ocr-chi-sim \
        curl \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# ---- mediamtx（RTSP 服务器单二进制，从官方 release 下载）----
ARG MEDIAMTX_VERSION=1.20.1
RUN curl -fsSL -o /tmp/mediamtx.tar.gz \
        "https://github.com/bluenviron/mediamtx/releases/download/v${MEDIAMTX_VERSION}/mediamtx_v${MEDIAMTX_VERSION}_linux_amd64.tar.gz" \
    && tar -xzf /tmp/mediamtx.tar.gz -C /usr/local/bin/ \
    && chmod +x /usr/local/bin/mediamtx \
    && rm /tmp/mediamtx.tar.gz

# ---- Python 依赖 ----
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# ---- 代码（cache/.venv/.git 已被 .dockerignore 排除）----
COPY . .

# ---- 运行环境 ----
ENV PYTHONUNBUFFERED=1 \
    STREAM_FACTORY_MEDIAMTX_BIN=/usr/local/bin/mediamtx \
    STREAM_FACTORY_MEDIAMTX_CONFIG=/usr/local/bin/mediamtx.yml

# 非 root 用户（UID 1000 与常见 Linux 桌面用户一致，compose 经 ${UID} 自动匹配）
ARG UID=1000
ARG GID=1000
RUN groupadd -g "${GID}" app && useradd -m -u "${UID}" -g "${GID}" app \
    && chown -R app:app /app
USER app

EXPOSE 8000 8554
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
