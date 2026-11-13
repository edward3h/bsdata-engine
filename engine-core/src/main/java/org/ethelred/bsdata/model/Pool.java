/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import java.util.HashMap;
import java.util.Map;

/** Shares equal strings, so a type id or a cost name that occurs thousands of times is stored once. */
final class Pool {
    /**
     * Strings longer than this are not shared. In the real 11e data, the fields that are shared (ids, names, type ids,
     * link targets) never exceed 80 characters, and only 11 occurrences of 8 distinct strings are longer than 64, so
     * any limit between about 32 and 128 gives the same result. Rule descriptions are read through
     * {@code Reader.text} and never reach the pool. Characteristic values do go through {@code Reader.str}, which
     * helps, since most are one to three characters and repeat constantly; the limit stops a long unique value from
     * filling the pool.
     */
    static final int MAX_SHARED_LENGTH = 64;

    private final Map<String, String> strings = new HashMap<>();

    String intern(String s) {
        if (s.length() > MAX_SHARED_LENGTH) {
            return s;
        }
        String existing = strings.putIfAbsent(s, s);
        return existing == null ? s : existing;
    }
}
