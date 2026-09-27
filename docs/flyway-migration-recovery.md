# Flyway migration recovery for the pre-release V50 conflict

The connector metadata migration was briefly created as `V50` while the
release branch already owned `V50__workflow_process_type_scope.sql`. That
state was never a valid release migration set. The released migrations are the
immutable `V58__ai_connector_operation_metadata.sql` and the V59 guard; the
active migration directory must contain exactly one `V50` file.

Normal release databases need no manual step. Flyway applies the existing
workflow V50, the immutable V58 connector metadata migration, and then V59.
V59 is an idempotent release guard for the project-type schema; it does not
guess which meaning an invalid historical V50 row had.

If a disposable development database was started from the conflicting
pre-release commit:

1. Stop the backend and take a database backup.
2. Inspect, without changing anything:

   ```sql
   SELECT version, description, script, checksum, success
   FROM flyway_schema_history
   WHERE version = '50';
   ```

3. If the row points to `V50__ai_connector_operation_metadata.sql`, do not
   start the release artifact against it: the immutable V58 script cannot
   infer whether the historical row means workflow setup or connector
   metadata. For a disposable database, recreate it from the release compose
   stack. For a retained development database, use a reviewed, backed-up
   one-time repair procedure to reconcile both the Flyway history and the
   already-present connector columns, then start the release artifact. Do not
   hand-edit the checksum or apply this procedure to production implicitly.
4. Start the backend with the release artifact. Flyway applies V58 and V59;
   the health probe must report migration version 59.
5. Verify both `pms_project_type.project_creation_enabled` and the three
   connector columns on `pms_ai_operation` before returning the database to
   service.

If the database is disposable, recreating it from the release compose stack is
safer than repairing its history. A production database with this invalid
history is outside the supported upgrade path and requires an explicit backup,
change record, and rollback plan.
