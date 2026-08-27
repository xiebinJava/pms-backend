package com.brad.pms.security;

public final class PermissionCode {
    public static final String USER_READ = "admin:user:read";
    public static final String USER_WRITE = "admin:user:write";
    public static final String ORG_READ = "admin:org:read";
    public static final String ORG_WRITE = "admin:org:write";
    public static final String ROLE_READ = "admin:role:read";
    public static final String ROLE_WRITE = "admin:role:write";
    public static final String IMPORT_WRITE = "admin:import:write";
    public static final String AUDIT_READ = "admin:audit:read";
    public static final String PROJECT_READ = "project:read";
    public static final String PROJECT_WRITE = "project:write";

    private PermissionCode() { }
}
