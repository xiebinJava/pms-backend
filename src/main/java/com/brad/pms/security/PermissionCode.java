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
    public static final String FEEDBACK_READ = "feedback:read";
    public static final String FEEDBACK_WRITE = "feedback:write";
    public static final String FEEDBACK_MANAGE = "feedback:manage";

    /**
     * Feedback permissions are intentionally hierarchical: a manager can
     * read and submit feedback, while a writer can read their own feedback.
     * Other permission families remain exact-match to avoid broadening access
     * accidentally.
     */
    public static boolean isSatisfiedBy(String requested, String granted) {
        if (requested == null || granted == null) return false;
        if (requested.equals(granted)) return true;
        if (FEEDBACK_READ.equals(requested)) {
            return FEEDBACK_WRITE.equals(granted) || FEEDBACK_MANAGE.equals(granted);
        }
        if (FEEDBACK_WRITE.equals(requested)) return FEEDBACK_MANAGE.equals(granted);
        return false;
    }

    private PermissionCode() { }
}
