package com.brad.pms.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports a snapshot produced by H2 SCRIPT into an empty OceanBase MySQL-mode database.
 * Credentials are read from runtime environment variables and are never accepted as CLI arguments.
 */
public final class OceanbaseSqlImporter {

    public static final List<String> BUSINESS_TABLES = List.of(
            "sys_user",
            "project",
            "project_member",
            "project_follower",
            "project_task",
            "project_milestone",
            "project_comment",
            "project_node",
            "project_lifecycle_log"
    );

    private static final Pattern DOUBLE_QUOTED_IDENTIFIER = Pattern.compile("\\\"([^\\\"]+)\\\"");
    private static final Pattern TYPED_DATE_LITERAL = Pattern.compile(
            "(?i)\\b(?:DATE|TIME|TIMESTAMP)\\s+'([^']*)'");

    private OceanbaseSqlImporter() {
    }

    public static List<String> splitStatements(String script) {
        Objects.requireNonNull(script, "script");
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean escaped = false;

        for (int i = 0; i < script.length(); i++) {
            char ch = script.charAt(i);
            char next = i + 1 < script.length() ? script.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (ch == '\n' || ch == '\r') {
                    inLineComment = false;
                    current.append(ch);
                }
                continue;
            }
            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote && ch == '-' && next == '-') {
                inLineComment = true;
                i++;
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote && ch == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }

            if (ch == '\\' && inSingleQuote && !escaped) {
                escaped = true;
                current.append(ch);
                continue;
            }
            if (ch == '\'' && !escaped && !inDoubleQuote) {
                if (inSingleQuote && next == '\'') {
                    current.append(ch).append(next);
                    i++;
                    continue;
                }
                inSingleQuote = !inSingleQuote;
                current.append(ch);
                continue;
            }
            if (ch == '"' && !escaped && !inSingleQuote) {
                if (inDoubleQuote && next == '"') {
                    current.append(ch).append(next);
                    i++;
                    continue;
                }
                inDoubleQuote = !inDoubleQuote;
                current.append(ch);
                continue;
            }
            if (ch == ';' && !inSingleQuote && !inDoubleQuote) {
                addStatement(statements, current);
                continue;
            }

            current.append(ch);
            escaped = false;
        }
        addStatement(statements, current);
        return statements;
    }

    /**
     * Returns a safe data statement, or null for H2 metadata/DDL that is not needed after schema.sql.
     */
    public static String normalizeH2Statement(String statement) {
        if (statement == null) return null;
        String normalized = statement.trim();
        if (normalized.isEmpty()) return null;

        String upper = normalized.toUpperCase(Locale.ROOT);
        if (upper.startsWith("DROP ") || upper.startsWith("TRUNCATE ")
                || upper.startsWith("DELETE ")) {
            throw new IllegalArgumentException("destructive statement is not allowed in a migration snapshot");
        }
        if (upper.startsWith("SET ") || upper.startsWith("CREATE USER")
                || upper.startsWith("ALTER USER") || upper.startsWith("GRANT ")
                || upper.startsWith("CREATE SCHEMA") || upper.startsWith("CREATE TABLE")
                || upper.startsWith("CREATE MEMORY TABLE") || upper.startsWith("CREATE INDEX")
                || upper.startsWith("CREATE UNIQUE INDEX") || upper.startsWith("CREATE SEQUENCE")
                || upper.startsWith("ALTER TABLE") || upper.startsWith("ALTER SEQUENCE")) {
            return null;
        }
        if (upper.startsWith("UPDATE ")) {
            throw new IllegalArgumentException("destructive statement is not allowed in a migration snapshot");
        }
        if (!upper.startsWith("INSERT INTO ")) {
            throw new IllegalArgumentException("unsupported statement in migration snapshot: "
                    + firstWords(normalized));
        }

        String dataStatement = normalized.replaceAll("(?i)\\\"PUBLIC\\\"\\s*\\.", "");
        Matcher matcher = DOUBLE_QUOTED_IDENTIFIER.matcher(dataStatement);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1).toLowerCase(Locale.ROOT)));
        }
        matcher.appendTail(result);
        return normalizeH2Literals(result.toString());
    }

    private static String normalizeH2Literals(String statement) {
        String decoded = decodeUnicodeStringLiterals(statement);
        Matcher matcher = TYPED_DATE_LITERAL.matcher(decoded);
        return matcher.replaceAll("'$1'");
    }

    private static String decodeUnicodeStringLiterals(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        for (int i = 0; i < sql.length(); i++) {
            if (i + 2 < sql.length()
                    && (sql.charAt(i) == 'U' || sql.charAt(i) == 'u')
                    && sql.charAt(i + 1) == '&' && sql.charAt(i + 2) == '\'') {
                int end = i + 3;
                StringBuilder value = new StringBuilder();
                while (end < sql.length()) {
                    char ch = sql.charAt(end);
                    if (ch == '\'' ) {
                        if (end + 1 < sql.length() && sql.charAt(end + 1) == '\'') {
                            value.append('\'');
                            end += 2;
                            continue;
                        }
                        break;
                    }
                    if (ch == '\\' && end + 1 < sql.length()) {
                        int codePointLength = sql.charAt(end + 1) == '+' ? 6 : 4;
                        int codeStart = end + (sql.charAt(end + 1) == '+' ? 2 : 1);
                        int codeEnd = codeStart + codePointLength;
                        if (codeEnd <= sql.length()) {
                            String hex = sql.substring(codeStart, codeEnd);
                            try {
                                value.appendCodePoint(Integer.parseInt(hex, 16));
                                end = codeEnd;
                                continue;
                            } catch (NumberFormatException ignored) {
                                // Keep a non-Unicode escape as a literal sequence.
                            }
                        }
                    }
                    value.append(ch);
                    end++;
                }
                if (end < sql.length() && sql.charAt(end) == '\'') {
                    result.append('\'');
                    for (int j = 0; j < value.length(); j++) {
                        char valueChar = value.charAt(j);
                        if (valueChar == '\'') result.append("''");
                        else result.append(valueChar);
                    }
                    result.append('\'');
                    i = end;
                    continue;
                }
            }
            result.append(sql.charAt(i));
        }
        return result.toString();
    }

    public static Map<String, Long> expectedRowCounts(Path snapshot) throws IOException {
        Map<String, Long> counts = new LinkedHashMap<>();
        BUSINESS_TABLES.forEach(table -> counts.put(table, 0L));
        for (String statement : splitStatements(Files.readString(snapshot, StandardCharsets.UTF_8))) {
            String normalized = normalizeH2Statement(statement);
            if (normalized == null) continue;
            String table = normalized.substring("INSERT INTO ".length()).trim();
            int end = table.indexOf(' ');
            if (end < 0) end = table.indexOf('(');
            if (end < 0) {
                throw new IllegalArgumentException("cannot determine insert table from snapshot statement");
            }
            table = table.substring(0, end).replace("`", "").toLowerCase(Locale.ROOT);
            if (!counts.containsKey(table)) {
                throw new IllegalArgumentException("unexpected table in migration snapshot: " + table);
            }
            counts.computeIfPresent(table, (key, value) -> value + countInsertRows(normalized));
        }
        return counts;
    }

    private static long countInsertRows(String statement) {
        int valuesIndex = statement.toUpperCase(Locale.ROOT).indexOf(" VALUES");
        if (valuesIndex < 0) {
            throw new IllegalArgumentException("INSERT statement is missing VALUES");
        }
        long rows = 0;
        int depth = 0;
        boolean inString = false;
        for (int i = valuesIndex + 7; i < statement.length(); i++) {
            char ch = statement.charAt(i);
            if (ch == '\'' ) {
                if (inString && i + 1 < statement.length() && statement.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                inString = !inString;
                continue;
            }
            if (inString) continue;
            if (ch == '(') {
                if (depth == 0) rows++;
                depth++;
            } else if (ch == ')' && depth > 0) {
                depth--;
            }
        }
        if (rows == 0) throw new IllegalArgumentException("INSERT statement has no value rows");
        return rows;
    }

    public static String jdbcUrl(String host, String port, String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&useSSL=false&allowPublicKeyRetrieval=true";
    }

    public static String serverJdbcUrl(String host, String port) {
        return "jdbc:mysql://" + host + ":" + port
                + "/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&useSSL=false&allowPublicKeyRetrieval=true";
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        if (!statement.isEmpty()) statements.add(statement);
        current.setLength(0);
    }

    private static String firstWords(String statement) {
        return Arrays.stream(statement.split("\\s+"))
                .limit(3)
                .reduce((left, right) -> left + " " + right)
                .orElse(statement);
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        switch (options.mode()) {
            case "normalize" -> normalize(options.snapshot());
            case "preflight" -> preflight(options);
            case "import" -> importSnapshot(options);
            case "validate" -> validate(options);
            default -> throw new IllegalArgumentException("mode must be normalize, preflight, import, or validate");
        }
    }

    private static void normalize(Path snapshot) throws IOException {
        PrintStream output = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        for (String statement : splitStatements(Files.readString(snapshot, StandardCharsets.UTF_8))) {
            String normalized = normalizeH2Statement(statement);
            if (normalized != null) output.println(normalized + ";");
        }
        output.flush();
    }

    private static void preflight(Options options) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                serverJdbcUrl(options.host(), options.port()), options.user(), options.password());
             Statement statement = connection.createStatement()) {
            boolean exists;
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '" + options.database() + "'")) {
                exists = resultSet.next();
            }
            if (!exists) {
                statement.execute("CREATE DATABASE `" + options.database() + "` DEFAULT CHARACTER SET utf8mb4");
                System.out.println("target_database=created");
            } else {
                System.out.println("target_database=exists");
            }
        }
    }

    private static void importSnapshot(Options options) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl(options.host(), options.port(), options.database()), options.user(), options.password())) {
            ensureEmpty(connection);
            executeSchema(connection, options.schema());
            List<String> statements = normalizedDataStatements(options.snapshot());
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) statement.executeUpdate(sql);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
            System.out.println("imported_statements=" + statements.size());
        }
    }

    private static void validate(Options options) throws Exception {
        Map<String, Long> expected = expectedRowCounts(options.snapshot());
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl(options.host(), options.port(), options.database()), options.user(), options.password())) {
            boolean mismatch = false;
            for (Map.Entry<String, Long> entry : expected.entrySet()) {
                long actual = countRows(connection, entry.getKey());
                System.out.println(entry.getKey() + " expected=" + entry.getValue() + " actual=" + actual);
                mismatch |= actual != entry.getValue();
            }
            long brokenReferences = brokenReferenceCount(connection);
            System.out.println("broken_references=" + brokenReferences);
            if (mismatch || brokenReferences != 0) {
                throw new IllegalStateException("migration validation failed");
            }
        }
    }

    private static void ensureEmpty(Connection connection) throws SQLException {
        for (String table : BUSINESS_TABLES) {
            if (tableExists(connection, table) && countRows(connection, table) > 0) {
                throw new IllegalStateException("target database is not empty: " + table);
            }
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?")) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1) > 0;
            }
        }
    }

    private static void executeSchema(Connection connection, Path schema) throws Exception {
        try (Statement statement = connection.createStatement()) {
            for (String sql : splitStatements(Files.readString(schema, StandardCharsets.UTF_8))) {
                if (!sql.isBlank()) statement.execute(sql);
            }
        }
    }

    private static List<String> normalizedDataStatements(Path snapshot) throws IOException {
        List<String> normalized = new ArrayList<>();
        for (String statement : splitStatements(Files.readString(snapshot, StandardCharsets.UTF_8))) {
            String dataStatement = normalizeH2Statement(statement);
            if (dataStatement != null) normalized.add(dataStatement);
        }
        return normalized;
    }

    private static long countRows(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM `" + table + "`")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static long brokenReferenceCount(Connection connection) throws SQLException {
        List<String> checks = List.of(
                "SELECT COUNT(*) FROM project_member x LEFT JOIN project p ON p.id = x.project_id WHERE p.id IS NULL",
                "SELECT COUNT(*) FROM project_follower x LEFT JOIN project p ON p.id = x.project_id WHERE p.id IS NULL",
                "SELECT COUNT(*) FROM project_task x LEFT JOIN project p ON p.id = x.project_id WHERE p.id IS NULL",
                "SELECT COUNT(*) FROM project_node x LEFT JOIN project p ON p.id = x.project_id WHERE p.id IS NULL",
                "SELECT COUNT(*) FROM project_comment x LEFT JOIN project p ON p.id = x.project_id WHERE p.id IS NULL"
        );
        long broken = 0;
        try (Statement statement = connection.createStatement()) {
            for (String check : checks) {
                try (ResultSet resultSet = statement.executeQuery(check)) {
                    resultSet.next();
                    broken += resultSet.getLong(1);
                }
            }
        }
        return broken;
    }

    private record Options(String mode, Path snapshot, Path schema, String host, String port,
                           String database, String user, String password) {
        private static Options parse(String[] args) {
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (!arg.startsWith("--") || i + 1 >= args.length) {
                    throw new IllegalArgumentException("arguments must use --name value");
                }
                values.put(arg.substring(2), args[++i]);
            }
            String mode = values.getOrDefault("mode", "import");
            Path snapshot = values.containsKey("snapshot") ? requirePath(values, "snapshot") : null;
            if (("normalize".equals(mode) || "import".equals(mode) || "validate".equals(mode))
                    && snapshot == null) {
                throw new IllegalArgumentException("missing --snapshot");
            }
            Path schema = Path.of(values.getOrDefault("schema", "src/main/resources/schema.sql"));
            String host = validateIdentifier(env("OCEANBASE_HOST", "127.0.0.1"), "OCEANBASE_HOST");
            String port = validateIdentifier(env("OCEANBASE_PORT", "2881"), "OCEANBASE_PORT");
            String database = validateIdentifier(env("OCEANBASE_DATABASE", "brad_pms"), "OCEANBASE_DATABASE");
            String user = "";
            String password = "";
            if ("preflight".equals(mode)) {
                user = firstEnv("OCEANBASE_ADMIN_USER", "OCEANBASE_USER");
                password = firstEnv("OCEANBASE_ADMIN_PASSWORD", "OCEANBASE_PASSWORD");
            } else if (!"normalize".equals(mode)) {
                user = requireEnv("OCEANBASE_USER");
                password = requireEnv("OCEANBASE_PASSWORD");
            }
            return new Options(mode, snapshot, schema, host, port, database, user, password);
        }

        private static Path requirePath(Map<String, String> values, String name) {
            String value = values.get(name);
            if (value == null || value.isBlank()) throw new IllegalArgumentException("missing --" + name);
            Path path = Path.of(value);
            if (!Files.isRegularFile(path)) throw new IllegalArgumentException("file does not exist: " + path);
            return path;
        }

        private static String env(String name, String fallback) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? fallback : value;
        }

        private static String requireEnv(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
            return value;
        }

        private static String firstEnv(String preferred, String fallback) {
            String value = System.getenv(preferred);
            return value == null || value.isBlank() ? requireEnv(fallback) : value;
        }

        private static String validateIdentifier(String value, String name) {
            if (!value.matches("[A-Za-z0-9_.-]+")) {
                throw new IllegalArgumentException(name + " contains unsupported characters");
            }
            return value;
        }
    }
}
