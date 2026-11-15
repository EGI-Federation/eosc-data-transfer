# Error reference

Every failure that originates inside an endpoint uses the same JSON envelope:

```json
{
  "id": "destInvalid",
  "description": "No configuration found for destination <unknown>",
  "details": {
    "destination": "unknown"
  }
}
```

The HTTP status carries the outcome. `id` is a stable lowerCamelCase key that clients can branch on;
`description` and `details` are for humans and may be absent. Failures raised by the authentication
layer or by the runtime before an endpoint runs (for example a missing bearer token) can return the
status without this envelope.

## How the status is chosen

- The resource sets the status for validation failures it detects itself (`400`).
- An exception derived from an upstream HTTP error keeps the upstream status, so a `404` or `503`
  from the transfer engine reaches the caller unchanged.
- Everything else defaults to `500`, including configuration errors and timeouts.

## Request and parameter errors (HTTP 400)

| Identifier | Cause |
| --- | --- |
| `destInvalid` | `dest` is missing or not configured on this instance |
| `noFiles` | Estimation requested without any files |
| `noTransfer` | Transfer requested without a body |
| `noJob` | `jobId` is missing or empty |
| `noField` | `fieldName` is missing or empty |
| `seInvalid` | The storage element URI is missing or empty |
| `uriInvalid` | A storage element URI is malformed |
| `uriMismatch` | The URI does not belong to the configured storage system |
| `seNotSupported` | The destination has no storage service for element operations |
| `missingOperationParameters` | Rename requested without an old or new URI |
| `doiNotSupported` | No configured parser recognises the identifier |
| `doiInvalid` | The identifier is malformed |
| `noFilesLink` | A parser could not extract a file list |
| `notFile` / `notFolder` | The operation expects the other kind of element |

## Missing resources (HTTP 404)

| Identifier | Cause |
| --- | --- |
| `transferNotFound` | No transfer exists with the requested `jobId` |
| `fieldNotFound` | `fieldName` is not a field of `TransferInfoExtended` |
| `storageElementNotFound` | The requested file or folder does not exist |
| `notFound` | A back end reported a missing resource |

## Authorization

| Status | Identifier | Cause |
| --- | --- | --- |
| `401` | `notAuthorized` | The access token is missing, expired or invalid |
| `403` | `noAccess` | The token is valid but lacks the required permission |
| `419` | engine-specific | The delegated credentials must be re-delegated |

`419` is deliberate and is not a rate limit. When you receive it, obtain fresh credentials and repeat
the call; retrying unchanged will not help.

## Configuration, transport and engine errors (HTTP 500)

| Identifier | Cause |
| --- | --- |
| `configInvalid` | Destination, service, storage or parser configuration is invalid |
| `exception` | Unexpected error; `description` carries the message |
| `processingError` | The REST client could not process the request |
| `badRequest` | Invalid Base-64 storage credentials in `Authorization-Storage` |
| `noRecordId` / `noSessionId` / `noParseHelper` | A parser could not read the upstream response |
| `serialize` | A field value could not be serialised |
| `copyObject` / `removeObject` / `removeBucket` | A storage operation failed; message in `description` |
| `makeBucket` / `listBuckets` / `listObjects` / `statObject` | A storage query failed; message in `description` |
| `deleteObject` / `deleteError` | A storage deletion failed; message in `description` |

The following timeout identifiers are also reported as `500`; retrying usually helps, so they can be
treated like a temporary failure:

```text
doiParseTimeout              startTransferTimeout        findTransfersTimeout
getTransferInfoTimeout       getTransferInfoFieldTimeout cancelTransferTimeout
listFolderContentTimeout     getStorageElementInfoTimeout createFolderTimeout
deleteFolderTimeout          deleteFileTimeout           renameStorageElementTimeout
```

## Special cases

- `noOp` is returned with HTTP `200` and an `Error` body when a rename has identical source and
  destination URIs. Nothing was changed, and nothing failed.
- Upstream statuses are passed through. If the engine or storage answers `503`, the caller sees
  `503` with the engine's message in `description`.
- `details` keys depend on the operation. Common keys are `destination`, `jobId`, `fieldName`,
  `seUri`, `seUriOld` and `seUriNew`.
