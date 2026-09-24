# Local test controller for the Namespace Cloud plugin.
# JDK 21 to match the target Jenkins server.
FROM jenkins/jenkins:lts-jdk21

USER root
# curl is handy for poking the API from inside the controller while debugging.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
USER jenkins

# The plugin's declared dependencies, plus the Pipeline suite so a job can
# actually be run against a provisioned agent. jenkins-plugin-cli resolves
# transitive dependencies itself.
RUN jenkins-plugin-cli --plugins \
      caffeine-api \
      credentials \
      plain-credentials \
      ssh-credentials \
      ssh-slaves \
      workflow-aggregator \
      configuration-as-code

# Our plugin. Dropping it in ref/plugins makes Jenkins install it on first boot.
COPY namespace-cloud.hpi /usr/share/jenkins/ref/plugins/namespace-cloud.jpi

# Local sandbox: skip the setup wizard so the UI is immediately usable.
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
