# Archived migrations

Files in this directory are retained for release audit only. Flyway does not
scan this directory.

`V50__ai_connector_operation_metadata.sql` was introduced after the project
already owned version `V50__workflow_process_type_scope.sql`. It therefore
could not be a valid active Flyway migration in a clean release. The connector
metadata migration is released as `V58__ai_connector_operation_metadata.sql`.
The archived copy is kept so the original release attempt remains traceable.

This archive is not a compatibility alias. A database that recorded the
archived script as version 50 is a development-only, unsupported migration
history: Flyway cannot infer whether version 50 means workflow setup or
connector metadata when both scripts share the same version. Before upgrading
such a database, stop the application, back up the database, verify the row in
`flyway_schema_history`, and use the release-specific, reviewed repair
procedure under operator control. V58 remains immutable, and V59 is only the
formal release guard for the project-type schema; neither migration guesses
the meaning of an invalid V50 row. Never edit Flyway history on a production
database without a backup and a tested rollback.
