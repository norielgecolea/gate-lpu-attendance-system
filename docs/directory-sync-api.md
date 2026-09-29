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
- `GET /api/sync/employee-attendance`

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

## Main-gate employee attendance

The ERP pulls daily employee attendance recorded at the main gates. Library and
Olive Hotel taps are not included. Each record is one employee on one campus
day: the first time in and the latest time out. `timeOut` is `null` when the
employee has not timed out yet.

`startDate` and `endDate` are required `YYYY-MM-DD` campus dates. The range
cannot exceed 366 days.

```text
GET /api/sync/employee-attendance?startDate=2026-09-01&endDate=2026-09-28
```

```json
{
  "startDate": "2026-09-01",
  "endDate": "2026-09-28",
  "total": 1,
  "offset": 0,
  "limit": 1000,
  "records": [
    {
      "name": "Maria Santos",
      "employeeNo": "EMP-1001",
      "attendanceDate": "2026-09-01",
      "timeIn": "2026-09-01T00:05:00Z",
      "timeOut": "2026-09-01T09:10:00Z"
    }
  ]
}
```

`timeIn` and `timeOut` are UTC instants. `limit` defaults to 1000 and must be
between 1 and 5000. When `total` is larger than the page, request the next
page with `offset`. Upsert by `employeeNo` and `attendanceDate`.

Each `/api/sync/**` request is written to the administration audit trail. The
API key is not stored. Audit rows use type `API`, account `ERP`, and include
the query string, response status, and client IP.
