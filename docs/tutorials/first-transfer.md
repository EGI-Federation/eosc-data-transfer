# Your first transfer

This tutorial walks through a complete transfer with the public EOSC Data Transfer API: discover the
available destinations, turn a DOI into a file list, estimate the cost in credits, start the
transfer, follow its progress and cancel it.

You need a shell with `curl` and [`jq`](https://jqlang.github.io/jq/), and an EGI Check-in access
token with permission to use the transfer service. The examples use the public deployment; replace
`API` with `http://localhost:8089` when running the service locally.

```shell
export API=https://data-transfer.service.eosc-beyond.eu
export TOKEN='paste-your-access-token-here'
```

## Step 1 - discover the destinations

The `dest` query parameter selects the destination storage type, which in turn selects the transfer
engine and the storage service. This endpoint is public.

```shell
curl -sS "$API/storage/destinations" | jq
```

The response is an array. Each entry names a `destination` value that you can pass as `dest`:

```json
[
  {
    "kind": "DestinationInfo",
    "destination": "dcache",
    "description": "dCache",
    "authType": "token",
    "protocol": "https",
    "transferWith": "EGI Data Transfer",
    "browseWith": "WebDAV storage"
  }
]
```

If you omit `dest`, the API uses `dcache`.

## Step 2 - turn a DOI into a file list

The parser endpoint selects a parser automatically and returns the files behind the identifier. The
access token is optional here; when supplied, it is forwarded to the data repository.

```shell
curl -sS "$API/parser?doi=doi:10.5281/zenodo.6511035" \
  -H "Authorization: Bearer $TOKEN" | jq '.elements[] | {name, size, accessUrl}'
```

Every element has an `accessUrl` that can be used as a transfer source and a `size` in bytes. Keep
the URL and the size of the file you want to transfer.

## Step 3 - estimate the cost in credits

Send the files with their sizes to the estimation endpoint. No transfer is started.

```shell
curl -sS -X POST "$API/transfers/estimate?dest=dcache" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"files":[{"sources":["https://zenodo.org/record/6511035/files/data.zip"],'\
'"size":1073741824}]}' | jq
```

The response is `202 Accepted` with the total size and the cost:

```json
{
  "bytes": 1073741824,
  "credits": 1,
  "creditsExact": 0.5
}
```

`credits` is rounded up; `creditsExact` is the unrounded value. How many bytes one credit buys is
configured per transfer service (`bytes-per-credit`, 2 GiB for EGI Data Transfer by default).

## Step 4 - start the transfer

A transfer request contains one entry per file. Each entry lists all known sources and all desired
destinations; the engine tries them in order.

```shell
curl -sS -X POST "$API/transfers?dest=dcache" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"files":[{"sources":["https://zenodo.org/record/6511035/files/data.zip"],'\
'"destinations":["https://dcache.example.org/data/data.zip"]}],'\
'"params":{"overwrite":false,"retry":1}}' | jq
```

The API answers with `202 Accepted` and the job identifier:

```json
{
  "kind": "TransferInfo",
  "jobId": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"
}
```

Store the identifier; every later query needs it.

```shell
export JOB=a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d
```

## Step 5 - follow the transfer

Ask for the job status. `fileInfo=failed` adds the status of files that failed; use `all` to get
every file.

```shell
curl -sS "$API/transfer/$JOB?dest=dcache&fileInfo=failed" \
  -H "Authorization: Bearer $TOKEN" | jq '{jobState, submittedAt, finishedAt, reason}'
```

`jobState` moves from `submitted` to `active` and then to `succeeded`, `partial`, `failed` or
`canceled`. To list all your transfers instead, use the search endpoint:

```shell
curl -sS "$API/transfers?dest=dcache&state=active&limit=10" \
  -H "Authorization: Bearer $TOKEN" | jq '.count, .transfers[].jobId'
```

## Step 6 - cancel the transfer

Cancellation returns the job with its current status:

```shell
curl -sS -X DELETE "$API/transfer/$JOB?dest=dcache" \
  -H "Authorization: Bearer $TOKEN" | jq '{jobState}'
```

## What next

- [Endpoint reference](../reference/endpoints.md) for every route and parameter.
- [Error reference](../reference/error-ids.md) when a call fails with an unexpected `id`.
- [README](../../README.md) for authentication details, including the `Authorization-Storage`
  header required by S3 destinations.
