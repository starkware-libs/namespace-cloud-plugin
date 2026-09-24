# Throwaway Jenkins on Kubernetes, for testing the plugin

A standalone controller reachable at a public hostname, so agents provisioned
on Namespace can actually dial back to it. Intended to be deleted once testing
is done — the plugin is then installed by hand on the real controller.

No private registry: the stock `jenkins/jenkins:lts-jdk21` image is used, an
init container installs the plugin's dependencies from the update centre, and
`namespace-cloud.hpi` is uploaded through the UI.

## 1. Fill in the placeholders

| File | Placeholder |
| --- | --- |
| `k8s/30-testing-credentials.yaml` | `admin-password`, `namespace-token` |
| `k8s/10-pvc.yaml` | `storageClassName` (commented out; omit to take the cluster default) |
| `k8s/40-jcasc-configmap.yaml` | `adminAddress` |

Mint the token with — note `resource_id`, which is not optional:

```bash
nsc token create \
  --name jenkins \
  --expires_in 720h \
  --grant '{"resource_type":"instance","resource_id":"*","actions":["create","destroy","get","list","wait"]}' \
  --grant '{"resource_type":"instance/o11y/logs","resource_id":"*","actions":["get"]}'
```

## 2. Apply

```bash
kubectl apply -f k8s/ --dry-run=client   # sanity check first
kubectl apply -f k8s/
kubectl -n jenkins-ns rollout status deploy/jenkins
```

Wait for external-dns and cert-manager, then confirm the hostname resolves and
serves a certificate before going further:

```bash
kubectl -n jenkins-ns get ingress jenkins
curl -sSI https://jenkins.example.com/login | head -1
```

## 3. Install the plugin

Build it, then upload at
**Manage Jenkins → Plugins → Advanced settings → Deploy Plugin**:

```bash
mvn -B clean package -DskipTests   # produces target/namespace-cloud.hpi
```

Restart when prompted. This is the same manual install you will do on the real
controller.

## 4. Configure the cloud

**Manage Jenkins → Clouds → Add a new cloud → Namespace**. The
`namespace-token` credential already exists — JCasC created it from the
ConfigMap. Press **Test connection**; you want four ticks before going on.

Then add a profile, e.g. label `ns-linux-4x8`, 4 vCPU, 8 GB.

To manage the cloud declaratively instead, paste
`jcasc-cloud-snippet.yaml` into `40-jcasc-configmap.yaml` once the plugin
is installed, and restart. It is left out of the initial config on purpose:
JCasC resolves the `namespace:` symbol at startup, so declaring the cloud
before the plugin exists makes the first boot fail.

## 5. Run a build

```groovy
pipeline {
    agent { label 'ns-linux-4x8' }
    options { timeout(time: 10, unit: 'MINUTES') }
    stages {
        stage('Who am I?') {
            steps {
                sh 'echo "agent  : $(hostname)"'
                sh 'echo "vCPU   : $(nproc)"'
                sh 'echo "arch   : $(uname -m)"'
            }
        }
    }
}
```

## 6. Tear down

```bash
kubectl delete namespace jenkins-ns     # also drops the PVC
```

Check nothing is left running on the Namespace side:

```bash
nsc list
```

## Notes

- **`proxy-read-timeout: 3600`** on the Ingress is load-bearing. The inbound
  agent holds a long-lived WebSocket; nginx's 60s default kills it and the
  agent reconnects in a loop.
- **Port 50000 is not exposed.** Agents connect over WebSocket on 443.
- **Credentials are in a ConfigMap**, which is fine only because this is a
  throwaway. Replace with `ExternalSecret` for anything shared; the Deployment
  reads them via env vars, so it is a `configMapKeyRef` -> `secretKeyRef` swap.
- `docker/Dockerfile` here is the *other* path — baking the plugin into an
  image and pushing to a registry. Unused by this flow; kept for the real
  deployment, where you would not install plugins by hand.
