package com.shambanexa.common;

import java.util.Set;

public final class AccessScopes {

    public static final String OWN_FARM = "OWN_FARM";
    public static final String SELECTED_FARMS = "SELECTED_FARMS";
    public static final String ALL_FARMS = "ALL_FARMS";

    private static final Set<String> KNOWN = Set.of(OWN_FARM, SELECTED_FARMS, ALL_FARMS);

    private AccessScopes() {
    }

    public static boolean known(String scope) {
        return scope != null && KNOWN.contains(scope);
    }

    public static String label(String scope) {
        return switch (scope) {
            case OWN_FARM -> "Own Farm Only";
            case SELECTED_FARMS -> "Selected Farms";
            case ALL_FARMS -> "All Farms";
            default -> scope;
        };
    }
}
