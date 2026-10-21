package app.hisn.core;

/** Distinguishes granted permission, a live binding and configured enforcement. */
public enum GuardHealth {
    DISABLED(false), PERMISSION_REQUIRED(true), RECONNECT_REQUIRED(true),
    READY_NO_RULES(false), ACTIVE(false), LIMITED(true),
    SETTINGS_UNAVAILABLE(true), PERMISSION_UNKNOWN(true);

    public final boolean needsAttention;
    GuardHealth(boolean attention) { needsAttention = attention; }

    public static GuardHealth evaluate(Boolean enabled, boolean connected,
            boolean configured, boolean readable, boolean partial) {
        if (!readable) return SETTINGS_UNAVAILABLE;
        if (enabled == null) return PERMISSION_UNKNOWN;
        if (!enabled) return configured ? PERMISSION_REQUIRED : DISABLED;
        if (!connected) return RECONNECT_REQUIRED;
        if (!configured) return READY_NO_RULES;
        return partial ? LIMITED : ACTIVE;
    }
}
