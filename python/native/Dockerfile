FROM rust:1.88-bookworm

ENV PATH="/usr/local/cargo/bin:/usr/local/bin:${PATH}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        python3 \
        python3-dev \
        python3-pip \
        python3-venv \
    && rm -rf /var/lib/apt/lists/*

RUN python3 -m pip install --break-system-packages --no-cache-dir maturin

WORKDIR /workspace-root/languages-sdk/python/native

CMD ["bash"]
