FROM python:3.8-slim-bullseye

# Debian apt 换清华源
RUN sed -i 's/deb.debian.org/mirrors.tuna.tsinghua.edu.cn/g' /etc/apt/sources.list && \
    sed -i 's/security.debian.org/mirrors.tuna.tsinghua.edu.cn/g' /etc/apt/sources.list

RUN apt-get update && apt-get install -y --no-install-recommends \
        xz-utils \
        tesseract-ocr \
        tesseract-ocr-chi-sim \
        ca-certificates \
        libxml2-dev \
        libxslt-dev \
        gcc \
        python3-dev \
    && rm -rf /var/lib/apt/lists/*

# 复制本地shared压缩包
COPY ffmpeg-master-latest-linux64-gpl-shared.tar.xz /tmp/ffmpeg.tar.xz
RUN mkdir -p /opt/ffmpeg \
    && tar -xf /tmp/ffmpeg.tar.xz -C /opt/ffmpeg --strip-components=1 \
    && rm -rf /opt/ffmpeg/doc /opt/ffmpeg/man /opt/ffmpeg/examples \
    && echo "/opt/ffmpeg/lib" > /etc/ld.so.conf.d/ffmpeg.conf \
    && ldconfig \
    && ln -s /opt/ffmpeg/bin/ffmpeg /usr/local/bin/ffmpeg \
    && ln -s /opt/ffmpeg/bin/ffprobe /usr/local/bin/ffprobe \
    && rm -rf /tmp/ffmpeg.tar.xz

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

COPY . .

ENV PYTHONUNBUFFERED=1

ARG UID=1000
ARG GID=1000
RUN groupadd -g "${GID}" app && useradd -m -u "${UID}" -g "${GID}" app \
    && chown -R app:app /app

EXPOSE 8000
