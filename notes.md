# Known issues and follow-ups

Working register of known defects, inconsistencies and cleanup tasks that are out of scope for the
current change. Each entry contains enough context to fix the issue directly or to open a ticket.

Line references are valid as of version `1.1.107-rc` (branch `review_openapi_docs`).

## How to use this file

- Each entry lists **severity**, **what happens**, **where**, **suggested fix** and a **ticket idea**.
- Severity: **High** = broken feature, data loss or security risk; **Medium** = incorrect or
  confusing behaviour with a workaround; **Low** = cleanup, hardening or documentation accuracy;
  **Suggestion** = optional follow-up owned by maintainers, not required for this change.
- When an entry is ticketed, add the issue link next to it and keep it here until it is closed.
- This register describes current behaviour. The OpenAPI document and `README.md` remain the
  authoritative description of the API.

## Summary

| ID | Area | Severity | Summary |
| --- | --- | --- | --- |
| KI-1 | DOI parsing | High | Signposting recursion depth is not propagated |
| KI-2 | API | Medium | `GET /storage/destination` omits `browseWith` |
| KI-3 | API | Medium | HTTP `419` is documented but never generated locally |
| KI-4 | API | Medium | Authentication failures bypass the `Error` envelope |
| KI-5 | API | Medium | Client-side errors reported as HTTP `500` |
| KI-6 | API | Low | `GET /transfers` documents an unreachable `404` |
| KI-7 | API | Low | `noArgs` is a dead entry in the error mapping |
| KI-8 | API | Low | `transferStateToString(null)` would throw |
| KI-9 | API | Low | `Destination` enum drifts from the configuration |
| KI-10 | Code | Low | Dead CDI fields in `EgiDataTransfer` |
| KI-11 | Code | Low | Unused `StorageSimpleOperation` model |
| KI-12 | Code | Low | Repository-wide Checkstyle debt |
| KI-13 | OpenAPI | Low | `dest` renders as an `allOf` instead of a plain enum |
| KI-14 | OpenAPI | Low | `Authorization` header parameters are not emitted |
| KI-15 | OpenAPI | Low | Timestamp schema relies on a per-field workaround |
| KI-16 | Docs | Trivial | README says the API has three functionality groups |
| KI-17 | Docs | Low | Two port conventions (`8081` vs `8089`) are easy to confuse |
| KI-18 | Docs | Low | Kubernetes deployment guidance is minimal |
| KI-19 | Testing | Medium | No automated tests and no CI test job |
| KI-20 | Release | Suggestion | `CITATION.cff` metadata is stale (maintainers) |
| KI-21 | Operations | Suggestion | Public deployment still serves `1.1.105` (maintainers) |
| KI-22 | Security | Low | Raw upstream messages are exposed in error responses |
| KI-23 | Operations | Low | Accounting silently needs Redis |
| KI-24 | Release | Suggestion | Version `1.1.107-rc` is not tagged or released |

## API behaviour

### KI-1 - Signposting recursion depth is not propagated

- **Severity:** high
- **Observed:** `DataTransferSelf.parseDOIAsync()` sends the recursion level as a query parameter named
  `level`, while `GET /parser` reads `depth`. `SignpostParser` calls that client with `level + 1`, but the
  server ignores it and keeps using the default depth `1`.
- **Impact:** the `MAX_RECURSION = 3` guard in `SignpostParser` never advances. A cycle of Signposting
  links (A links to B, B links to A) can recurse until an HTTP or client timeout stops it.
- **Where:** `src/main/java/eosc/eu/DataTransferSelf.java:24`,
  `src/main/java/parser/generic/SignpostParser.java:230`,
  `src/main/java/eosc/eu/DigitalObjectIdentifier.java:195`.
- **Suggested fix:** send `depth` (or accept both names) in `DataTransferSelf`; add a parser test with a
  two-page cycle and assert that recursion stops at `MAX_RECURSION`.
- **Ticket idea:** "Signposting parser bypasses its recursion limit because depth is sent as level".

### KI-2 - `GET /storage/destination` omits `browseWith`

- **Severity:** medium
- **Observed:** `getDestinationInfo()` calls `getStorageSystem(destination, null, null, null)`.
  `DataTransferBase.getStorageSystem()` only calls `initService()` when a storage-element URL is given, so
  the storage service is instantiated but never initialised and `getServiceName()` returns `null`.
  Confirmed against a running instance: `GET /storage/destination?dest=dcache` omits `browseWith`, while
  `GET /storage/destinations` reports `"WebDAV storage"` for the same destination.
- **Impact:** clients cannot use this endpoint to decide whether storage management is available.
- **Where:** `src/main/java/eosc/eu/DataStorage.java:229`,
  `src/main/java/eosc/eu/DataTransferBase.java:210`.
- **Suggested fix:** use the already-resolved `storageConfig.name()` for `browseWith` instead of
  instantiating a throwaway client, or pass the same placeholder URL that `listDestinations()` uses.
- **Ticket idea:** "Destination info endpoint never reports the storage service name".

### KI-3 - HTTP `419` is documented but never generated locally

- **Severity:** medium
- **Observed:** `419` appears in operation annotations only. No code path in this repository raises it; it
  can only reach a client when an upstream engine or storage returns `419` and the status is passed
  through.
- **Impact:** clients are told to handle `419`, but the trigger is undocumented and depends entirely on
  the back end. The OpenAPI contract suggests the API itself produces it.
- **Where:** `src/main/java/eosc/eu/DataTransfer.java`, `src/main/java/eosc/eu/DataStorage.java` and
  `src/main/java/eosc/eu/DataTransferUser.java` (response annotations).
- **Suggested fix:** verify how EGI Data Transfer and the storage services signal re-delegation, then map
  it explicitly (for example to a stable error id) or document `419` as an upstream-dependent status.
- **Ticket idea:** "Define and test the re-delegation (`419`) flow end to end".

### KI-4 - Authentication failures bypass the `Error` envelope

- **Severity:** medium
- **Observed:** authenticated resources rely on Quarkus OIDC, which rejects a request before the resource
  method runs. There are no JAX-RS exception mappers for authentication or authorization failures, so
  these responses do not necessarily contain the `Error` JSON body that every `401`/`403` response
  annotation references.
- **Impact:** clients that parse `id` for every failure must special-case `401` and `403`.
- **Where:** `src/main/java/eosc/eu/ActionError.java`, `src/main/java/eosc/eu/DataTransfer.java` and
  `src/main/java/eosc/eu/DataStorage.java` (response annotations); no mapper exists under `eosc/eu` or
  `egi/eu`.
- **Suggested fix:** add `ExceptionMapper` implementations for `AuthenticationFailedException`,
  `UnauthorizedException` and `ForbiddenException` that return `ActionError`, and verify them with
  `@TestSecurity`.
- **Ticket idea:** "Return the standard error envelope for `401` and `403` responses".

### KI-5 - Client-side errors reported as HTTP `500`

- **Severity:** medium
- **Observed:** several identifiers that describe caller or upstream-data problems map to `500` because
  they are missing from the `400` list in `ActionError`:
  - `badRequest` is thrown when `Authorization-Storage` is not valid Base-64 (`EgiDataTransfer`).
  - `noRecordId`, `noSessionId` and `noParseHelper` are thrown by parsers when the upstream response
    cannot be interpreted.
- **Impact:** clients retry or escalate errors that will not succeed until the request or credentials are
  corrected.
- **Where:** `src/main/java/eosc/eu/ActionError.java:215-230`,
  `src/main/java/egi/eu/EgiDataTransfer.java:228`,
  `src/main/java/parser/esrf/EsrfParser.java:184`,
  `src/main/java/parser/b2share/B2ShareParser.java:172`,
  `src/main/java/parser/generic/SignpostParser.java:171`.
- **Suggested fix:** add these identifiers to the `400` mapping, or pass an explicit `400` code when
  throwing; document any that must stay `500`.
- **Ticket idea:** "Map client-supplied credential and parser-data errors to HTTP `400`".

### KI-6 - `GET /transfers` documents an unreachable `404`

- **Severity:** low
- **Observed:** the operation documents `404 "No matching transfer"`, but `EgiDataTransfer.findTransfers()`
  returns a `TransferList` (and an empty list is a valid `200`). No `NOT_FOUND` path exists for this
  endpoint.
- **Impact:** generated clients and users assume a `404` behaviour that does not occur.
- **Where:** `src/main/java/eosc/eu/DataTransfer.java` (`findTransfers` response list),
  `src/main/java/egi/eu/EgiDataTransfer.java` (`findTransfers`).
- **Suggested fix:** confirm with a real engine that no `404` is produced, then remove the response (as
  was done for `207`), or implement the documented behaviour.
- **Ticket idea:** "Verify and correct the documented `404` for `GET /transfers`".

### KI-7 - `noArgs` is a dead entry in the error mapping

- **Severity:** low
- **Observed:** `noArgs` appears only in the `400` mapping inside `ActionError`; nothing throws it.
- **Where:** `src/main/java/eosc/eu/ActionError.java:228`.
- **Suggested fix:** remove the entry, or use it consistently for missing arguments.
- **Ticket idea:** "Drop unused error identifiers from the mapping".


### KI-8 - `transferStateToString(null)` would throw

- **Severity:** low
- **Observed:** `transferStateToString()` calls `state.toString()` without a null check. The resource now
  defaults `state` to `active`, which hides the problem at the API boundary, but any other caller or
  `TransferService` implementation can still pass `null`.
- **Where:** `src/main/java/egi/eu/EgiDataTransfer.java:139-140`.
- **Suggested fix:** return `"ACTIVE"` when the argument is `null` and document the default in the method.
- **Ticket idea:** "Harden transfer state translation against null".

### KI-9 - `Destination` enum drifts from the configuration

- **Severity:** low
- **Observed:** `Transfer.Destination` is a documentation aid and must be kept in sync manually with the
  keys under `eosc.transfer.destination`. `storm` was missing until this change (`1.1.107-rc`), even
  though the runtime accepted it. `ftp` has no storage service in the default configuration.
- **Impact:** OpenAPI can advertise values that are not configured, or omit configured ones.
- **Where:** `src/main/java/eosc/eu/model/Transfer.java` (enum `Destination`),
  `src/main/resources/application.yml` (`eosc.transfer.destination`).
- **Suggested fix:** add a startup consistency check (log or fail when the configured keys and the enum
  differ), or generate the allowed values from configuration in an `OASFilter`.
- **Ticket idea:** "Keep documented destination values in sync with configuration".

## Code hygiene

### KI-10 - Dead CDI fields in `EgiDataTransfer`

- **Severity:** low
- **Observed:** `EgiDataTransfer` declares `@Inject OidcClient client` and a `TokensHelper tokenHelper`.
  The class is instantiated reflectively with `newInstance()`, so CDI never injects `client`; neither field
  is read anywhere. `tokenHelper` is constructed in the constructor and never used.
- **Impact:** misleading code that suggests the engine obtains its own token, which it does not (the
  caller's token is forwarded).
- **Where:** `src/main/java/egi/eu/EgiDataTransfer.java:56-58,67`.
- **Suggested fix:** remove both fields and the imports; if the engine ever needs its own token, obtain it
  explicitly from an `OidcClient` passed through `initService`.
- **Ticket idea:** "Remove unused OIDC client and token helper from `EgiDataTransfer`".

### KI-11 - Unused `StorageSimpleOperation` model

- **Severity:** low
- **Observed:** `StorageSimpleOperation` is referenced only by itself; no endpoint uses it, so it does not
  appear in the OpenAPI schema. Its constructor javadoc also violates the CI Checkstyle rule
  (`MissingJavadocMethod`/`JavadocMethod`: missing `@param seUrl`).
- **Where:** `src/main/java/eosc/eu/model/StorageSimpleOperation.java:25`.
- **Suggested fix:** delete the class, or annotate it fully and use it if a single-URI operation is
  planned.
- **Ticket idea:** "Remove unused `StorageSimpleOperation` model".

### KI-12 - Repository-wide Checkstyle debt

- **Severity:** low
- **Observed:** the CI Checkstyle ruleset (`sun_checks.xml`) reports violations in files that this change
  did not touch, for example unused imports and `DesignForExtension` in `RolesCustomization`, missing
  `@param` tags in `TransferServiceException`, `ModifierOrder` in `egi/checkin/model/CheckinUser`, and
  javadoc gaps in `egi/s3/model/ObjectInfo`. CI only lints changed files, so the debt surfaces whenever
  one of these files is edited.
- **Impact:** unrelated edits to those files fail the Lint workflow for pre-existing reasons.
- **Where:** repository-wide; run the command in the "Verification" section below without the file filter.
- **Suggested fix:** schedule a cleanup PR that fixes the violations and then enforce Checkstyle on the
  whole tree.
- **Ticket idea:** "Clean up pre-existing Checkstyle violations and lint the whole source tree".


## OpenAPI and documentation

### KI-13 - `dest` renders as an `allOf` instead of a plain enum

- **Severity:** low
- **Observed:** every `dest` parameter is generated as
  `allOf: [$ref Destination, {default: dcache}]` plus an example. Swagger UI does not reliably render that
  as a dropdown of allowed values.
- **Impact:** clients must read the `Destination` schema separately to see the accepted values.
- **Where:** parameter annotations in `src/main/java/eosc/eu/DataTransfer.java`,
  `src/main/java/eosc/eu/DataStorage.java` and `src/main/java/eosc/eu/DataTransferBase.java`
  (`DESTINATION_STORAGE`).
- **Suggested fix:** an `OASFilter` that replaces the parameter schema with an inline enumeration based on
  the configuration (ties in with KI-9), or explicit `enumeration` values kept in sync.
- **Ticket idea:** "Render the `dest` parameter as an enumeration in Swagger UI".

### KI-14 - `Authorization` header parameters are not emitted

- **Severity:** low
- **Observed:** SmallRye OpenAPI filters out parameters named `Authorization` (as well as `Accept` and
  `Content-Type`) from the generated document, even when annotated with `@Parameter`. The token is
  therefore documented only in `info.description`, the operation text and the `OIDC` security scheme.
- **Impact:** the generated clients and the parameter list do not show the header explicitly.
- **Where:** `src/main/java/eosc/eu/DigitalObjectIdentifier.java` (optional token) and the OIDC security
  scheme in `src/main/resources/application.yml`.
- **Suggested fix:** accept the limitation, or inject a description with an `OASFilter` that adds an
  `Authorization` header parameter to authenticated operations. Do not duplicate the value at runtime.
- **Ticket idea:** "Expose the `Authorization` header in the generated OpenAPI document".

### KI-15 - Timestamp schema relies on a per-field workaround

- **Severity:** low
- **Observed:** `java.util.Date` fields were previously generated as a shared `Date` component with
  `format: date`, even though the JSON values contain a time. Each field now uses
  `@Schema(implementation = String.class, format = "date-time", ...)` to force the correct schema. A new
  `Date` field added without that annotation will reintroduce the problem.
- **Impact:** schema regressions are easy to introduce.
- **Where:** `src/main/java/eosc/eu/model/TransferInfoExtended.java`,
  `src/main/java/eosc/eu/model/TransferPayloadInfo.java`,
  `src/main/java/eosc/eu/model/StorageElement.java`.
- **Suggested fix:** an `OASFilter` that rewrites every `java.util.Date`-derived schema to
  `type: string, format: date-time` (and ideally removes the shared `Date` component), then drop the
  per-field workaround.
- **Ticket idea:** "Handle timestamp schemas centrally instead of per field".

### KI-16 - README says the API has three functionality groups

- **Severity:** trivial
- **Observed:** the introduction says "The API covers three sets of functionalities" and lists DOI parsing,
  transfers and storage. The API also exposes caller information (`/user/info`) and service information
  (`/version`), and the OpenAPI document declares five tag groups.
- **Where:** `README.md:27`.
- **Suggested fix:** align the wording with the five groups, or state explicitly that two groups are
  auxiliary.
- **Ticket idea:** "Align README API overview with the documented API groups".

### KI-17 - Two port conventions (`8081` vs `8089`) are easy to confuse

- **Severity:** low
- **Observed:** the application listens on `8089` (`quarkus.http.port`, `application.yml:164`). Docker
  Compose maps host port `8081` to container port `8089` (`src/main/docker/docker-compose.yaml:72`), and
  `src/main/docker/.env.template:19` suggests `http://localhost:8081` for the Swagger UI local server.
  Dev mode therefore uses `8089`, while the Compose deployment is reachable on `8081`.
- **Impact:** documentation and diagrams mix the two ports; readers cannot tell which one applies.
- **Where:** `README.md`, `src/main/docker/.env.template`, `docs/architecture.drawio`,
  `docs/architecture.png`.
- **Suggested fix:** document both conventions in one place (dev mode `8089`, Compose host `8081`) and use
  that wording consistently in diagrams and examples.
- **Ticket idea:** "Document development and Compose port mappings consistently".

### KI-18 - Kubernetes deployment guidance is minimal

- **Severity:** low
- **Observed:** the README states that the repository ships no Kubernetes manifests and points at the
  container image and environment variables. There is no example for Argo CD, ingress, TLS or the
  telemetry sidecars.
- **Impact:** operators must derive the deployment themselves.
- **Where:** `README.md` (Kubernetes section), `src/main/docker/docker-compose.yaml`.
- **Suggested fix:** publish example manifests (or a Helm chart) that mirror the Compose topology, or
  document the required configuration keys in a dedicated how-to page.
- **Ticket idea:** "Provide Kubernetes deployment examples".


## Testing, release and operations

### KI-19 - No automated tests and no CI test job

- **Severity:** medium
- **Observed:** there is no `src/test` tree, so `./mvnw.sh test` runs zero tests. The Lint workflow runs
  Super-Linter and the image workflow builds the container, but no workflow exercises the API.
- **Impact:** regressions in routing, the error envelope, the parser flow and the OpenAPI document are only
  caught by manual checks.
- **Where:** repository root (`pom.xml` has `quarkus-junit` and `rest-assured` but no tests),
  `.github/workflows/`.
- **Suggested fix:** add `@QuarkusTest` smoke tests for `GET /version`, the presence of the five OpenAPI
  tags, public storage endpoints, the error envelope and unauthenticated `401`s. Use `@TestSecurity` and
  disable or stub Redis and OIDC so no external service is needed. Run them in a workflow.
- **Ticket idea:** "Add smoke tests and a CI test job".

### KI-20 - `CITATION.cff` metadata is stale

- **Severity:** suggestion
- **Observed:** `CITATION.cff` declares version `1.1.69` and `date-released: 2024-11-29`, while the project
  is at `1.1.107-rc`.
- **Where:** `CITATION.cff:8,12`.
- **Suggested action (maintainers):** update it on each release, or generate it in the release process.
  The Zenodo DOI remains the same; only the version and release date need to move.
- **Owner:** maintainers; out of scope for this change.
- **Ticket idea:** "Keep `CITATION.cff` in sync with releases".

### KI-21 - Public deployment still serves `1.1.105`

- **Severity:** suggestion (operational)
- **Observed:** on 2026-11-16, `https://data-transfer.service.eosc-beyond.eu/openapi` reported version
  `1.1.105` and did not include `POST /transfers/estimate`. The documentation improvements in
  `1.1.107-rc` are therefore not visible on the public Swagger UI yet.
- **Impact:** users still see the old specification.
- **Where:** deployment pipeline (`registry.egi.eu/eosc-beyond.eu/eosc-data-transfer`) and the host behind
  the public domain.
- **Suggested action (maintainers):** deploy the `1.1.107-rc` image if and when this RC is promoted, then
  re-run the checks in the verification section against the public domain. No deployment is part of this
  change.
- **Owner:** maintainers; out of scope for this change.
- **Ticket idea:** "Deploy `1.1.107-rc` to the public environment (maintainer decision)".

### KI-22 - Raw upstream messages are exposed in error responses

- **Severity:** low (security hardening)
- **Observed:** `ActionError(Throwable)` copies `getMessage()` into `description`, and the REST client
  exception mappers keep the upstream response body. Upstream errors can therefore surface internal
  hostnames, paths or engine details to the caller.
- **Where:** `src/main/java/eosc/eu/ActionError.java`, `src/main/java/cern/FileTransferServiceException.java`,
  `src/main/java/egi/s3/S3StorageException.java`.
- **Suggested fix:** keep detailed messages in the server log and return a generic description for `5xx`,
  or allowlist what may be exposed. Leave actionable `4xx` descriptions in place.
- **Ticket idea:** "Sanitise upstream error messages returned to clients".

### KI-23 - Accounting silently needs Redis

- **Severity:** low (operational)
- **Observed:** `AccountingCollector` is a `@Startup` bean that consumes the `transfer:jobs` Redis stream.
  The API itself starts and serves requests without Redis (verified locally with nothing listening on
  `6379`), but transfer jobs are not recorded and no usage records are sent.
- **Impact:** accounting can be silently lost in a deployment where Redis is unavailable, while the API
  appears healthy.
- **Where:** `src/main/java/eosc/eu/AccountingCollector.java`, `src/main/resources/application.yml`
  (`quarkus.redis.hosts`), `README.md` (deployment section).
- **Suggested fix:** document Redis as required for accounting, add a readiness or health check for the
  stream, and consider a feature flag that makes the dependency explicit when accounting is enabled.
- **Ticket idea:** "Surface missing Redis as a degraded accounting state".

### KI-24 - Version `1.1.107-rc` is not tagged or released

- **Severity:** suggestion
- **Observed:** `pom.xml`, `src/main/resources/application.yml`, `OpenApiDefinition.java` and
  `CHANGELOG.md` use `1.1.107-rc`. No Git tag or GitHub release exists for it.
- **Impact:** none for the code or documentation; the public deployment still reports `1.1.105`.
- **Where:** version triplet and `.github/workflows/image.yml`.
- **Suggested action (maintainers):** if this RC is promoted to a release, publish a GitHub Release for
  the chosen tag. The Build workflow pushes the versioned image only on `release: published`
  (`latest` plus `<tag>`); pushing a bare tag does not build it.
- **Owner:** maintainers; out of scope for this change.
- **Ticket idea:** "Release `1.1.107` (or promote `1.1.107-rc`)".

## Verification commands

Use these to reproduce the checks performed for `1.1.107-rc`:

```shell
# Build and export the generated OpenAPI document
./mvnw.sh -DskipTests package \
  -Dquarkus.smallrye-openapi.store-schema-directory=target/openapi

# Assert the documented state
grep -c 'operationId:'  target/openapi/openapi.yaml   # 19
grep -c '^- name:'      target/openapi/openapi.yaml   # 5 described tags
grep -m1 '^  version:'  target/openapi/openapi.yaml   # 1.1.107-rc
grep -c '"207"'         target/openapi/openapi.yaml   # 0
grep -c 'format: date$' target/openapi/openapi.yaml   # 0

# Markdown gate (CI configuration)
npx --yes markdownlint-cli --config .github/linters/.markdown-lint.yml \
  README.md CHANGELOG.md notes.md docs/index.md docs/tutorials/*.md \
  docs/reference/*.md docs/explanation/*.md

# Checkstyle gate for the changed files
./mvnw.sh -q checkstyle:check \
  -Dcheckstyle.config.location=.github/linters/sun_checks.xml \
  -Dcheckstyle.includes='**/eosc/eu/**/*.java'
```

Running Checkstyle without a file filter reports the pre-existing debt described in KI-12, so restrict
the `includes` pattern to the files you changed or compare against `git diff --name-only main`.

Runtime smoke tests (with a development instance on `http://localhost:8089`):

```shell
curl -sS http://localhost:8089/version
curl -sS http://localhost:8089/storage/destinations | jq 'length'
curl -sS 'http://localhost:8089/storage/destination?dest=unknown'
curl -sS 'http://localhost:8089/parser?doi=doi:10.5281/zenodo.6511035' | jq '.count'
```

## Related documents

- [Documentation map](docs/index.md)
- [Endpoint reference](docs/reference/endpoints.md)
- [Error reference](docs/reference/error-ids.md)
- [Architecture](docs/explanation/architecture.md)
- [README](README.md)
- [CHANGELOG](CHANGELOG.md)

Review this file at each release: close fixed entries, add newly discovered ones, and link tickets as
soon as they exist.

