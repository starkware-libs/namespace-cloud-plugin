# Local test controller

Builds a Jenkins container with the plugin and its dependencies preinstalled.

```bash
mvn -B clean package -DskipTests          # produces target/namespace-cloud.hpi
cp target/namespace-cloud.hpi docker/
docker build -t jenkins-namespace-test docker/
docker run -d --name jenkins-ns -p 8080:8080 -p 50000:50000 \
  -v jenkins-ns-home:/var/jenkins_home jenkins-namespace-test
```

The setup wizard is disabled, so http://localhost:8080 is usable immediately
with no login. That is fine for a local sandbox and wrong for anything else.

To pick up a rebuilt plugin, rebuild the image and recreate the container; the
named volume keeps your configuration.
