# Prebaked CI agent image for the Namespace Cloud plugin.
#
# Set the resulting reference as "Agent image" on the Jenkins profile. Every
# build then starts from this image instead of installing tools per run.
#
# BUILD IT REMOTELY. Pushing from a laptop uploads ~700MB of layers and tends
# to time out; nsc builds inside Namespace and pushes to nscr.io over their
# network, so only this Dockerfile and its context leave your machine:
#
#   nsc build . -n jenkins-agent:1.0 --push
#
# CRITICAL: stay FROM jenkins/inbound-agent and do NOT override ENTRYPOINT/CMD.
# The plugin injects JENKINS_URL / JENKINS_SECRET / JENKINS_AGENT_NAME and
# relies on the base image's entrypoint to launch agent.jar with them. A custom
# entrypoint means the agent never connects and the launch times out.
#
# Match the JDK tag to the controller's Java major version.
FROM jenkins/inbound-agent:latest-jdk21

USER root

# --- OS packages -----------------------------------------------------------
RUN apt-get update && apt-get install -y --no-install-recommends \
      build-essential \
      ca-certificates \
      curl \
      git \
      gnupg \
      jq \
      make \
      python3 \
      python3-pip \
      python3-venv \
      unzip \
      zip \
    && rm -rf /var/lib/apt/lists/*

# --- Docker CLI ------------------------------------------------------------
# Client only. Tick "Expose the instance Docker socket to the agent" on the
# profile and this talks to the dockerd already running in the instance, so no
# docker-in-docker is needed.
RUN install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg \
         -o /etc/apt/keyrings/docker.asc \
    && chmod a+r /etc/apt/keyrings/docker.asc \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
         https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
       > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends docker-ce-cli docker-buildx-plugin \
    && rm -rf /var/lib/apt/lists/*

# --- kubectl / helm --------------------------------------------------------
ARG KUBECTL_VERSION=v1.31.0
RUN curl -fsSLo /usr/local/bin/kubectl \
      "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/$(dpkg --print-architecture)/kubectl" \
    && chmod +x /usr/local/bin/kubectl \
    && curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# --- the nsc CLI, so builds can use Namespace caches/builders --------------
RUN curl -fsSL https://get.namespace.so/cloud/install.sh | bash \
    && mv /root/.ns/bin/nsc /usr/local/bin/nsc 2>/dev/null || true

# --- your tooling goes here -------------------------------------------------
# e.g. Rust, Node, Bazel, or any language toolchain your builds need:
# RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
# COPY requirements.txt /tmp/
# RUN pip3 install --break-system-packages -r /tmp/requirements.txt

# Back to the agent user: the entrypoint expects to run unprivileged.
USER jenkins

# Sanity check at build time so a broken image fails here, not mid-build.
RUN git --version && jq --version && docker --version && kubectl version --client=true 2>/dev/null | head -1
