# EOSC Data Transfer API documentation

The EOSC Data Transfer API moves files between data repositories, transfer engines and storage
systems. It parses DOIs and other persistent identifiers into file lists, estimates the cost of a
transfer in credits, starts and tracks transfers, and lists or manipulates storage elements. The
API itself is authorization-agnostic: the OIDC access token you send is forwarded unchanged to the
services that need it.

## Where to start

| I want to... | Read |
| --- | --- |
| Learn the API with a complete transfer | [Your first transfer](tutorials/first-transfer.md) |
| Understand how the service is built | [Architecture](explanation/architecture.md) |
| Look up an endpoint or a parameter | [Endpoint reference](reference/endpoints.md) |
| Understand an error identifier | [Error reference](reference/error-ids.md) |
| Try the API interactively | [Swagger UI](https://data-transfer.service.eosc-beyond.eu/swagger-ui/) |

## The four kinds of documentation

This documentation follows the [Diataxis](https://diataxis.fr) framework, so each page has one job:

- **Tutorials** teach by doing. Start with
  [Your first transfer](tutorials/first-transfer.md); it takes a DOI and follows it through
  estimation, submission, polling and cancellation.
- **Reference** is looked up, not read end to end. The [endpoint reference](reference/endpoints.md)
  lists every route, parameter and header, and the [error reference](reference/error-ids.md) maps
  error identifiers to HTTP statuses and causes.
- **Explanation** answers "why". [Architecture](explanation/architecture.md) describes the
  request path, the configuration-driven plug-in model, accounting and observability.
- **How-to guides** live in the [README](../README.md): authentication, DOI parsing, transfers,
  storage management, configuration, development and deployment.

## Machine-readable API description

The OpenAPI document is generated from the source annotations at build time, so it always matches
the running code:

- [Swagger UI](https://data-transfer.service.eosc-beyond.eu/swagger-ui/) renders the live document.
- `https://data-transfer.service.eosc-beyond.eu/openapi` returns it as YAML.
- A local development instance exposes it at `http://localhost:8089/openapi` (port from
  `src/main/resources/application.yml`).

The [endpoint reference](reference/endpoints.md) is a compact map of the same information; the
OpenAPI document is authoritative.

## Related documents

- [README](../README.md) - overview, how-to guides and deployment
- [CHANGELOG](../CHANGELOG.md) - released changes
- [CONTRIBUTING](../CONTRIBUTING.md) - branch and pull-request workflow
