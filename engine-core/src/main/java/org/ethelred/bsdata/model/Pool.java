/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import java.util.HashMap;
import java.util.Map;

/** Shares equal strings, so a type id or a cost name that occurs thousands of times is stored once. */
final class Pool {
    /**
     * Strings longer than this are not shared. In the real 11e data, the fields that are shared (ids, names, type ids,
     * link targets) never exceed 80 characters, and only 11 occurrences of 8 distinct strings are longer than 64, so
     * any limit between about 32 and 128 gives the same result. The limit is a backstop: long free text (rule
     * descriptions, characteristic values) is read through {@code Reader.text} and never reaches the pool, and this
     * stops a long unique string that slips through {@code Reader.str} from filling it.
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
