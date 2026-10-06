package eu.strictworkout.admin;

/**
 * The effective admin allowlist for this process.
 * {@code STRICT_ADMIN_GOOGLE_SUBJECTS} is bound at startup. Changing it requires
 * a restart. Every admin request reads this value again; it is not copied elsewhere.
 */
public interface AdminAllowlist {

    boolean allows(String googleSubject);
}
