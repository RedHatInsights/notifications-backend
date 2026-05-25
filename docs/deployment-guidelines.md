# Deployment Guidelines

## Dockerfile Conventions

### Naming and Location
- Place all Dockerfiles in `docker/` with the naming pattern `Dockerfile.<service-name>.<mode>` where mode is `jvm` or `native`.
- Every service except `notifications-mcp` uses JVM mode. The MCP service uses native mode (`Dockerfile.notifications-mcp.native`).
- Exception: `Dockerfile.notifications-connector-drawer.jvm` is named `.jvm` but actually builds a Mandrel native image. Do not rename it -- the Tekton pipeline references this exact filename.

### JVM Dockerfile Structure
- Use multi-stage builds: `ubi9/openjdk-21:latest` for building, `ubi9/openjdk-21-runtime:latest` for runtime.
- Build stage runs Maven with `./mvnw -s .mvn/settings.xml clean package -DskipTests -pl :<module-name> -am --no-transfer-progress`. Some connectors use `-Dmaven.test.skip -Dcheckstyle.skip` instead of `-DskipTests`.
- Runtime stage must run `microdnf upgrade --refresh --nodocs --setopt=install_weak_deps=0 -y` to patch base image CVEs.
- Copy the Quarkus fast-jar output as four distinct layers (lib, jars, app, quarkus) for Docker layer caching.
- Set `JAVA_OPTIONS` with `-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -XX:+ExitOnOutOfMemoryError`.
- Run as user `185` (numeric, not `jboss`) in the final stage.
- Copy `LICENSE.txt` to `/licenses/LICENSE` -- Konflux preflight check requires this.

### Native Dockerfile Structure (MCP, Drawer)
- Use `ubi9/ubi-minimal:latest` for both build and runtime stages.
- Install Mandrel manually (not a pre-built GraalVM image). Pin the `MANDREL_VERSION` and verify the download with a SHA256 checksum.
- Build with `-Dquarkus.package.type=native -Dquarkus.native.container-build=false -Dquarkus.kafka.snappy.enabled=true`.
- Copy the native runner binary to `/application` in the runtime stage.

### mTLS CA Certificates
- Only `notifications-connector-email` and `notifications-recipients-resolver` install extra CA certificates. They copy `recipients-resolver/src/main/resources/mtls-ca-validators.crt` into `/etc/pki/ca-trust/source/anchors/` and run `update-ca-trust`.
- Do not add CA trust steps to other services unless they need mTLS.

## Tekton / Konflux CI Pipelines

### File Organization
- Each service has four pipeline files in `.tekton/`:
  - `<service>-pull-request.yaml` -- triggers on PRs to `master`
  - `<service>-push.yaml` -- triggers on pushes to `master`
  - `<service>-sc-pull-request.yaml` -- triggers on PRs to `security-compliance`
  - `<service>-sc-push.yaml` -- triggers on pushes to `security-compliance`
- Exception: `notifications-mcp` has no `sc-` variants.

### Pipeline Configuration
- All pipelines use the `docker-build` pipeline from `RedHatInsights/konflux-pipelines`.
- Regular pipelines (non-SC) pin to a specific version tag (e.g., `v1.67.1`). SC pipelines reference `main`.
- PR pipelines set `cancel-in-progress: "true"`. Push pipelines set `cancel-in-progress: "false"`.
- PR images include `on-pr-` prefix in the tag and set `image-expires-after: 5d`.
- Push images are tagged with `{{revision}}` only (no expiration).

### Image Registries
- Konflux builds push to: `quay.io/redhat-user-workloads/hcc-integrations-tenant/notifications/<service>`.
- SC builds push to: `quay.io/redhat-user-workloads/hcc-integrations-tenant/notifications-sc/<service>-sc`.
- Production ClowdApps pull from: `quay.io/redhat-services-prod/hcc-integrations-tenant/notifications/<service>`.

### Service Account Naming
- Use `build-pipeline-<service>` for regular builds, `build-pipeline-<service>-sc` for SC builds.

### Renovate / MintMaker
- Tekton pipeline version bumps are auto-merged via Renovate (`renovate.json` has `tekton.automerge: true`).
- The `.baseimage` file tracks the digest of `ubi9/openjdk-21-runtime:latest`. A daily GitHub Action (`base-image-auto-update.yml`) creates PRs when the digest changes.

## Clowder / ClowdApp Deployment

### ClowdApp Templates
- Deployment manifests are OpenShift Templates in `.rhcicd/clowdapp-<service>.yaml`.
- Use `apiVersion: cloud.redhat.com/v1alpha1`, kind `ClowdApp`.
- The `ENV_NAME` parameter controls the target environment (ephemeral, stage, prod).
- Declare inter-service `dependencies` and `optionalDependencies` in ClowdApp specs.

### Database
- `notifications-backend` owns the database (`database.name: notifications-backend`, PostgreSQL 15).
- Other services that need database access use `sharedDbAppName: notifications-backend` (e.g., aggregator, engine, recipients-resolver).

### Health Probes
- Services with `QUARKUS_HTTP_PORT` override (connectors, MCP) use `/q/health/ready` and `/q/health/live` on that port.
- Core services (backend, engine) use `/health/ready` and `/health/live` on port `8000`.
- All probes use `initialDelaySeconds: 40`, `periodSeconds: 10`.

### Post-Deployment Tests
- Each service has a stage post-deployment test in `.rhcicd/stage-<service>-post-deployment-tests.yaml`.
- Tests use `ClowdJobInvocation` with the `iqe` plugin `notifications` and service-specific markers (e.g., `notif_backend and api`, `notif_email and api`).

## GitHub Actions

### Build & Test (`build.yml`)
- Runs on push, PR, and manual dispatch. Uses JDK 21 (Adoptium).
- Executes `./mvnw clean verify` (with tests). Uploads OpenAPI spec artifacts.
- Admin-console builds only trigger when `admin-console/**` files change (`-Padmin-console` profile).

### Security Scans
- Per-service `platsec-*.yml` workflows run Anchore Grype and Syft scans on push/PR to `master` and `security-compliance`.
- CodeQL analysis covers `java` and `javascript` languages.

## Legacy Build (Jenkins)
- `.rhcicd/build_deploy.sh` is the Jenkins build script. It builds all 13 JVM services sequentially, pushing to `quay.io/cloudservices/<service>`.
- On `security-compliance` branch, images are tagged `sc-YYYYMMDD-<short-sha>`. On other branches, images get `<short-sha>`, `qa`, and `latest` tags.

## Verification

```bash
# Verify LICENSE.txt exists (required by all Dockerfiles)
test -f LICENSE.txt && echo "OK" || echo "MISSING: LICENSE.txt"

# Verify every Tekton push pipeline references a Dockerfile that exists
grep -h "value: ./docker" .tekton/*-push.yaml | awk '{print $2}' | sort -u | while read df; do
  [ ! -f "$df" ] && echo "MISSING: $df"
done

# Verify ClowdApp templates parse
for f in .rhcicd/clowdapp-*.yaml; do
  python3 -c "import yaml; yaml.safe_load_all(open('$f'))" 2>&1 | grep -v "^$" && echo "INVALID: $f"
done
```
