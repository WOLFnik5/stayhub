# Container registry and delivery

The GitHub workflow runs Maven verification on pushes and pull requests. After a
successful build on the repository's default branch, it publishes a Linux amd64
image to `ghcr.io/wolfnik5/accomodationg:sha-<commit>`. Pull requests never publish
or deploy. The registry name is derived from the repository, so forks use their
own namespace. The workflow summary records the immutable `@sha256:...` reference.

Publication uses GitHub's job-scoped `GITHUB_TOKEN` with `packages: write`.
No registry password belongs in this repository. Publication starts when the
workflow changes are pushed to GitHub; it has not been executed locally.

## Enable CD on a Linux host

CD is disabled unless the repository Actions variable `ENABLE_CD` is `true`.
This change prepares delivery; no server or cloud account has been provisioned.

1. Prepare a dedicated Linux x64 host with Docker Engine, Compose 2.24.4 or newer,
   Bash and `flock` (util-linux). The runner account must be able to use Docker.
   Reserve application port 8080 and sufficient resources for PostgreSQL/Kafka.
2. Create a deployment directory, for example `/srv/stayhub`, owned by that
   account. Create `/srv/stayhub/.env` from the project's `.env.sample`, with
   actual server credentials and `SPRING_PROFILES_ACTIVE=observability`.
   Restrict this file's access, for example `chmod 600 /srv/stayhub/.env`.
   Use Docker's internal Kafka address `kafka:29092`.
3. If the GHCR package is private, authenticate Docker on the host using a
   read-only package token and `docker login ghcr.io --password-stdin`.
   Keep the credentials on the host. Public packages can be pulled anonymously.
4. Register a dedicated repository self-hosted runner on that host with labels
   `self-hosted`, `linux`, `x64`, `stayhub-deploy`. Do not use this runner for PR
   builds or other untrusted jobs; Docker access grants substantial host access.
5. Create the GitHub environment `staging`, and set its variable
   `DEPLOY_DIRECTORY=/srv/stayhub`. Set repository variable `ENABLE_CD=true`
   only after the host and credentials are ready.
6. Push to the default branch or run `Java CI` through `workflow_dispatch` on
   that branch. The flow is verify → publish → deploy. Other branches still run
   verification but do not publish or deploy.

The Compose project is named `stayhub-staging`. It keeps persistent PostgreSQL
and Kafka volumes. Database and broker ports are not published on the host, and
the application binds to `127.0.0.1:8080`. A public cloud deployment still needs
an explicitly configured HTTPS reverse proxy, network access and backups.
The monitoring overlay is a separate local setup and is not enabled by this CD.

## Health checks and rollback

The runtime image includes curl and runs as UID/GID 10001. Deployment pulls the
new image before replacing the running application. Compose waits up to 240
seconds for PostgreSQL, Kafka and the application's `/actuator/health` check.
This uses aggregate health, including the datasource; `/health` alone only
demonstrates that the HTTP handler is alive.

On success, the script atomically records the digest in
`DEPLOY_DIRECTORY/current-image`. On failure it returns a nonzero exit status
and restores the existing container's image, if one existed. A failed first
deployment stops the application. A pull failure leaves the running services
untouched. GitHub concurrency and a host `flock` prevent overlapping releases.

Rollback restores the application image only. Liquibase migrations already
applied to PostgreSQL remain applied; changes must support the previous version,
or recovery needs a planned database procedure. This single-instance flow has
brief downtime during replacement and is not a zero-downtime deployment.

To redeploy a known compatible digest manually on the host from a checkout:

```bash
bash ops/deployment/deploy.sh \
  ghcr.io/wolfnik5/accomodationg@sha256:<64-character-digest> /srv/stayhub
```

Mutable tags such as `latest` are deliberately rejected. The deployment script
never removes database volumes and does not run `docker compose down`.

## Verification

```bash
bash -n ops/deployment/deploy.sh
bash ops/deployment/test-deploy.sh
docker build -t stayhub-cd-check:local .
```

The deployment tests use a stub Docker command and verify successful state
recording, unhealthy-image rollback, first-deployment failure, pull failure,
mutable-tag rejection and concurrent deployment rejection. They also run in CI.
They do not demonstrate registry credentials or connectivity to a real server.

See [local verification results](VERIFICATION.md) for checks completed on this revision.

See [GitHub's image publication guide](https://docs.github.com/en/actions/tutorials/publish-packages/publish-docker-images)
and [Compose health waiting](https://docs.docker.com/reference/cli/docker/compose/up/).
