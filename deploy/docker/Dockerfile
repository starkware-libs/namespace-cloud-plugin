# Controller image with the Namespace Cloud plugin baked in.
#
# Unlike docker/Dockerfile (the local sandbox), this one sets no JAVA_OPTS:
# security is configured by JCasC in the Deployment, so nothing here can leave
# the controller unauthenticated.
#
#   mvn -B clean package -DskipTests
#   cp target/namespace-cloud.hpi deploy/docker/
#   docker build -t REGISTRY/jenkins-namespace:1.0.0 deploy/docker/
#   docker push REGISTRY/jenkins-namespace:1.0.0
#
# JDK21 to match the target controller's Java version.
FROM jenkins/jenkins:lts-jdk21

# Dependencies of the plugin, plus Pipeline and JCasC. jenkins-plugin-cli
# resolves transitive dependencies itself.
RUN jenkins-plugin-cli --plugins \
      caffeine-api \
      credentials \
      plain-credentials \
      ssh-credentials \
      ssh-slaves \
      workflow-aggregator \
      configuration-as-code

COPY namespace-cloud.hpi /usr/share/jenkins/ref/plugins/namespace-cloud.jpi
