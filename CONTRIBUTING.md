# Contributing

Thanks for your interest. This is a community plugin, not affiliated with
Namespace Labs.

## Building

```bash
mvn clean verify        # tests, SpotBugs, Spotless -> target/namespace-cloud.hpi
mvn hpi:run             # a Jenkins at http://localhost:8080/jenkins with the plugin
```

Requires JDK 17 or 21 and Maven 3.9+.

## Running it against a real Jenkins

`deploy/` has a throwaway Kubernetes deployment, and `docker/` a local
container. Both are for testing the plugin, not for production use.

Note that a build only completes if the agent can reach the controller's
`JENKINS_URL` **from inside Namespace's cloud**. A controller on `localhost`
provisions instances successfully and then times out waiting for them to
connect.

## The protobuf stubs

gRPC stubs are generated at build time from the `.proto` files vendored under
`src/main/proto`. Buf publishes no prebuilt Java SDK for
[buf.build/namespace/cloud](https://buf.build/namespace/cloud) (its Maven
registry returns 422), so the definitions are committed here.

To refresh them, see the "Protobuf stubs" section of the README. Record the new
upstream commit in `src/main/proto/VENDORED.txt` and keep `NOTICE` accurate.

## Before opening a pull request

- `mvn clean verify` passes, including SpotBugs and Spotless
- New behaviour has a test. `ConfigurationRoundTripTest` in particular exercises
  the Jelly forms; a typo in a field name shows up there rather than as an empty
  form in production
- Jelly note: `${%...}` is a localisation expression, and a `(` inside one is
  parsed as the start of an argument list. Put parenthetical text outside the
  braces or the page will not parse

## Reporting problems

Open an issue here rather than contacting Namespace support — this plugin is
not their software. Please include:

- the plugin version and Jenkins version
- the controller log around the failure (`Manage Jenkins -> System Log`)
- for provisioning failures, whether **Test connection** reports all four
  instance actions as granted
