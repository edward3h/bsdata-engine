/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import java.util.HashMap;
import java.util.Map;

/** Shares equal strings, so a type id or a cost name that occurs thousands of times is stored once. */
final class Pool {
    private final Map<String, String> strings = new HashMap<>();

    String intern(String s) {
        if (s.length() > 64) {
            return s;
        }
        String existing = strings.putIfAbsent(s, s);
        return existing == null ? s : existing;
    }
}
