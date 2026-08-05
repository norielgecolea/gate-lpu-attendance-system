# Directory sync API

The attendance system exposes a read-only pull API for a downstream system to
replicate the student and employee directory.

## Configuration and authentication

Set a long, random value for `APP_SYNC_API_KEY` in both deployments. The
endpoint is disabled when this value is empty. Send it with every request:

```http
X-Sync-Api-Key: <APP_SYNC_API_KEY>
```

The sync API is intentionally separate from user JWT sessions and is available
only to the machine credential:

- `GET /api/sync/students`
- `GET /api/sync/employees`
- `GET /api/sync/deletions`

Never place the key in browser code, source control, or request logs.

## Full initial sync

For each directory endpoint, request the first page without `cursor`:

```text
GET /api/sync/students?limit=500
```

The response has this form:

```json
{
  "records": [],
  "nextCursor": "opaque-checkpoint",
  "hasMore": false
}
```

Apply every record idempotently using `sourceId` as the stable source key.
Repeat the same endpoint with `cursor=nextCursor` while `hasMore` is `true`.
Persist the final non-null `nextCursor` only after that page has been
successfully committed locally. A response with no records has no new
checkpoint.

Student and employee feeds include soft-deactivated records with
`deleted: true`. Do not request or store profile photos from this feed.

## Incremental pulls

Store independent checkpoints for `students`, `employees`, and `deletions`.
Run the same paged procedure on a schedule. Cursors are opaque: treat them as
strings and do not generate, parse, or alter them. Each cursor orders records
by source update/deletion time and source sequence ID, so records that share a
timestamp are not skipped.

`limit` defaults to 500 and must be between 1 and 1000.

## Permanent deletions

An inactive source record can be permanently removed. That source row is no
longer present in the student or employee feed, so the downstream system must
also consume `/api/sync/deletions`. Each tombstone supplies the person type,
source ID, person number, and deletion timestamp. Delete or mark the matching
downstream record as permanently removed only after its tombstone is
successfully stored.

Do not discard a checkpoint after a failed page. Retry that same cursor; the
downstream upsert/delete operations must be idempotent.
