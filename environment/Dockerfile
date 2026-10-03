# Use a digest-pinned official Java image
FROM eclipse-temurin:21-jdk-jammy@sha256:4a6dc600f6820556e9cffbb7ea3b7a5996cc0a7cc420a3203c7fcbe735e05a39

WORKDIR /app

# Install required tools: tmux, asciinema (required by Mina), and python3
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       asciinema \
       tmux \
       python3 \
       python3-pip \
       python3-venv \
       curl \
    && rm -rf /var/lib/apt/lists/*

# Install uv for fast offline venv management for the verifier
COPY --from=ghcr.io/astral-sh/uv:0.6.14 /uv /usr/local/bin/uv

# Copy the environment files (ensure tests/ and solution/ are excluded!)
COPY . /app/

# Pre-download gradle wrapper dependencies (offline cache)
RUN cd /app/mercury-cli && ./gradlew --no-daemon dependencies

# Pre-build Python verifier environment (offline isolation)
RUN uv venv /opt/verifier-venv --python=python3 \
    && /opt/verifier-venv/bin/pip install --no-cache-dir pytest==8.3.2 pytest-json-ctrf==0.1.1 pyyaml==6.0.1