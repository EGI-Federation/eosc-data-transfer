# Endpoint reference

The table below maps every route of the API. The [OpenAPI document](https://data-transfer.service.eosc-beyond.eu/openapi)
is authoritative; this page is the quick map for finding your way around.

- Public base URL: `https://data-transfer.service.eosc-beyond.eu`
- Local development: `http://localhost:8089` (see `src/main/resources/application.yml`)
- Request and response bodies are JSON unless stated otherwise.
- Failures always return the `Error` envelope; see the [error reference](error-ids.md).

## DOI Parsing

| Method | Path | Auth | Success |
| --- | --- | --- | --- |
| `GET` | `/parser?doi=...` | optional token | `200 StorageContent` |

`depth` is an internal recursion guard; clients do not need to send it.

## Data Transfer

| Method | Path | Auth | Success |
| --- | --- | --- | --- |
| `POST` | `/transfers/estimate` | token | `202 TransferEstimationInfo` |
| `POST` | `/transfers` | token + storage | `202 TransferInfo` |
| `GET` | `/transfers` | token | `200 TransferList` |
| `GET` | `/transfer/{jobId}` | token | `200 TransferInfoExtended` |
| `GET` | `/transfer/{jobId}/{fieldName}` | token | `200` value (JSON or text) |
| `DELETE` | `/transfer/{jobId}` | token | `200 TransferInfoExtended` |

All of these accept `dest`. `GET /transfers` also accepts the search filters listed below, and
`GET /transfer/{jobId}` accepts `fileInfo`.

## Data Transfer User

| Method | Path | Auth | Success |
| --- | --- | --- | --- |
| `GET` | `/user/info` | token | `200 UserInfo` |

## Data Storage

| Method | Path | Auth | Success |
| --- | --- | --- | --- |
| `GET` | `/storage/destinations` | public | `200` array of `DestinationInfo` |
| `GET` | `/storage/destination` | public | `200 DestinationInfo` |
| `GET` | `/storage/folder/list` | token + storage | `200 StorageContent` |
| `GET` | `/storage/file` | token + storage | `200 StorageElement` |
| `GET` | `/storage/folder` | token + storage | `200 StorageElement` |
| `POST` | `/storage/folder` | token + storage | `201 Success` |
| `DELETE` | `/storage/folder` | token + storage | `200 Success` |
| `DELETE` | `/storage/file` | token + storage | `200 Success` |
| `PUT` | `/storage/file` | token + storage | `200 Success` |
| `PUT` | `/storage/folder` | token + storage | `200 Success` |

The two `PUT` operations take a `StorageRenameOperation` body with `seUriOld` and `seUriNew`. When
both URIs are equal the call succeeds with `200` and error id `noOp`.

## Service Info

| Method | Path | Auth | Success |
| --- | --- | --- | --- |
| `GET` | `/version` | public | `200` version string (`text/plain`) |

## Query parameters

| Parameter | Used by | Meaning |
| --- | --- | --- |
| `dest` | transfer and storage operations | Destination storage type; defaults to `dcache` |
| `doi` | `/parser` | Identifier to resolve; DOI notation or HTTP URL |
| `fields` | `GET /transfers` | Comma separated `TransferInfoExtended` fields to return |
| `limit` | `GET /transfers` | Maximum number of results; default `100` |
| `timeWindow` | `GET /transfers` | For terminal states, `hours[:minutes]` into the past |
| `state` | `GET /transfers` | Job state to match; defaults to `active` |
| `sourceSE` | `GET /transfers` | Filter by source storage element |
| `destSE` | `GET /transfers` | Filter by destination storage element |
| `voName` | `GET /transfers` | Filter by the virtual organisation of the caller |
| `userId` | `GET /transfers` | Filter by the user who started the transfer |
| `fileInfo` | `GET /transfer/{jobId}` | `none`, `failed` or `all` per-file details |
| `seUri` | `/storage/file`, `/storage/folder` | URI of the file or folder to act on |
| `folderUri` | `/storage/folder/list` | URI of the folder whose content to list |

The values accepted for `dest` are the destination keys configured on the instance. Call
`GET /storage/destinations` to list them; do not assume that every deployment supports every value.

## Headers

| Header | Meaning |
| --- | --- |
| `Authorization` | `Bearer <token>` OIDC access token, forwarded to the back end |
| `Authorization-Storage` | Base-64 `user:password` or `access-key:secret-key` for the storage |

`Authorization-Storage` is required by destinations whose `authType` is `keys`, and by WebDAV or FTP
storage that does not accept the OIDC token. `GET /storage/destination` reports the `authType` of a
destination.
