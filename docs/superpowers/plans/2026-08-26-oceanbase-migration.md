# PMS OceanBase Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe OceanBase profile and migrate the current in-memory H2 dataset into the `brad_pms` database without overwriting an existing non-empty target.

**Architecture:** Keep H2 as the default development profile. Add an explicit OceanBase profile using MySQL compatibility and runtime environment variables. Export the live H2 database through H2 Console, normalize the generated snapshot, then import it into an empty OceanBase database with preflight checks and row-count validation.

**Tech Stack:** Spring Boot 2.7.5, MyBatis-Plus, MySQL Connector/J, H2 Console, Java 17, Maven, OceanBase MySQL mode.

**Spec:** `docs/superpowers/specs/2026-08-26-oceanbase-migration-design.md`

## Global Constraints

- The target database name is exactly `brad_pms`.
- OceanBase is accessed through MySQL compatibility mode and the existing local SQL endpoint.
- Credentials come only from runtime environment variables or local machine configuration and are never committed.
- A non-empty target database must stop the migration; no drop, truncate, or overwrite is allowed.
- The current H2 backend must stay running until the snapshot has been exported.
- The default H2 profile remains available for local development and rollback.

### Task 1: Add the OceanBase runtime profile

**Files:**
- Create: `src/main/resources/application-oceanbase.yml`
- Modify: `README.md`
- Test: `src/test/java/com/brad/pms/config/OceanbaseConfigurationTest.java`

**Interfaces:**
- Produces the `oceanbase` Spring profile and the `OCEANBASE_*` runtime configuration contract.
- Does not change the default H2 profile.

- [ ] **Step 1: Write the failing configuration test**

Create a Spring configuration test that loads the OceanBase profile with test properties and asserts the JDBC driver, database name, and H2 Console setting.

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `mvn -q -Dtest=OceanbaseConfigurationTest test`

Expected: FAIL because `application-oceanbase.yml` does not exist yet.

- [ ] **Step 3: Add the OceanBase profile**

Use `com.mysql.cj.jdbc.Driver` and a URL rooted at `brad_pms`; read host, port, database, username, and password from `OCEANBASE_HOST`, `OCEANBASE_PORT`, `OCEANBASE_DATABASE`, `OCEANBASE_USER`, and `OCEANBASE_PASSWORD`. Disable H2 Console in this profile and keep schema initialization enabled for an empty database.

- [ ] **Step 4: Update the backend runbook**

Document the profile command, required environment variables, the fact that the database uses MySQL compatibility mode, and the rule that secrets must stay outside the repository.

- [ ] **Step 5: Run the focused test and the full backend tests**

Run: `mvn -q -Dtest=OceanbaseConfigurationTest test` and then `mvn -q test`.

Expected: PASS.

### Task 2: Add a safe SQL snapshot importer and preflight command

**Files:**
- Create: `src/main/java/com/brad/pms/migration/OceanbaseSqlImporter.java`
- Create: `scripts/import-h2-snapshot.sh`
- Create: `src/test/java/com/brad/pms/migration/OceanbaseSqlImporterTest.java`

**Interfaces:**
- `OceanbaseSqlImporter.splitStatements(String)` returns a `List<String>` while respecting single-quoted SQL values and escaped quotes.
- `OceanbaseSqlImporter.normalizeH2Statement(String)` converts H2 table syntax to MySQL-compatible syntax and rejects H2-only user/session statements.
- The shell entrypoint accepts a snapshot path and reads `OCEANBASE_*` values without printing the password.

- [ ] **Step 1: Write tests for SQL splitting and normalization**

Cover semicolons inside string literals, escaped single quotes, `CREATE MEMORY TABLE` conversion, double-quoted identifiers, and skipping H2 `SET`, `CREATE USER`, `ALTER USER`, and `GRANT` statements.

- [ ] **Step 2: Run the focused migration tests and verify they fail**

Run: `mvn -q -Dtest=OceanbaseSqlImporterTest test`

Expected: FAIL because the importer class does not exist.

- [ ] **Step 3: Implement the importer**

Implement a JDBC importer that connects to the target database, executes normalized schema/data statements in order, uses a transaction for data statements, and reports statement numbers without logging credentials. The importer must fail before connecting if the snapshot path is missing.

- [ ] **Step 4: Add the shell entrypoint**

Validate that `OCEANBASE_USER` and `OCEANBASE_PASSWORD` are set, build the backend classpath with Maven, and invoke the importer. Do not put credentials in command output or generated files.

- [ ] **Step 5: Run focused and full tests**

Run: `mvn -q -Dtest=OceanbaseSqlImporterTest test` and then `mvn -q test`.

Expected: PASS.

### Task 3: Export the live H2 database and migrate it

**Files:**
- Modify: `scripts/import-h2-snapshot.sh`
- Create: `scripts/oceanbase-preflight.sh`
- Create: `scripts/validate-oceanbase-migration.sh`

**Interfaces:**
- The H2 Console export path is an explicit input; the scripts never stop the running PMS process.
- `oceanbase-preflight.sh` checks connectivity, creates `brad_pms` only when absent, and exits non-zero when any business table in an existing target is non-empty.
- `validate-oceanbase-migration.sh` compares expected row counts from the snapshot against target counts and checks the core foreign-key relationships.

- [ ] **Step 1: Export the live H2 snapshot**

In the already-running H2 Console execute `SCRIPT TO '/private/tmp/pms-h2-snapshot.sql'`. Confirm the file exists before any backend restart.

- [ ] **Step 2: Normalize the snapshot and inspect it**

Run the importer’s dry-run normalization against the snapshot. Confirm it contains only the nine PMS business tables and data statements; abort if it contains an unexpected database, user, or destructive statement.

- [ ] **Step 3: Run OceanBase preflight**

Run: `OCEANBASE_DATABASE=brad_pms scripts/oceanbase-preflight.sh`

Expected: connection succeeds; missing `brad_pms` is created, an empty existing database is accepted, and a non-empty database stops with no data change.

- [ ] **Step 4: Initialize schema and import the snapshot**

Apply `src/main/resources/schema.sql` to `brad_pms`, then run `scripts/import-h2-snapshot.sh /private/tmp/pms-h2-snapshot.sql`. Preserve IDs and import parent records before dependent records.

- [ ] **Step 5: Validate migrated data**

Run: `scripts/validate-oceanbase-migration.sh /private/tmp/pms-h2-snapshot.sql`

Expected: all nine table counts match and no project/node/task relationship points to a missing record.

### Task 4: Switch the backend and smoke-test

**Files:**
- Modify: `README.md`
- Test: existing backend test suite and local API smoke checks

**Interfaces:**
- The backend starts with `SPRING_PROFILES_ACTIVE=oceanbase` and the same `/api` context path.
- The frontend continues using the existing backend URL and does not need database-specific logic.

- [ ] **Step 1: Stop only the old H2 backend after validation**

Keep the H2 snapshot and validation output. Stop the old process only after the import validation passes.

- [ ] **Step 2: Start with the OceanBase profile**

Run: `SPRING_PROFILES_ACTIVE=oceanbase mvn spring-boot:run` with the five `OCEANBASE_*` variables supplied by the local runtime configuration.

- [ ] **Step 3: Smoke-test APIs**

Verify login, current-user lookup, project detail, node list, task list, task move, and permission responses. Confirm the migrated project and user avatars/names are present.

- [ ] **Step 4: Verify the front-end page**

Open the existing project detail page and confirm the header, selected node, task board, and public collaboration tabs load without a refresh loop or database errors.

- [ ] **Step 5: Run the full test suite and record the final state**

Run: `mvn -q test`. Record the active profile, target database name, validation result, and backup path without recording credentials.
