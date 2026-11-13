/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import org.ethelred.bsdata.roster.Selection;
import org.jspecify.annotations.Nullable;

/**
 * One thing wrong with a roster.
 *
 * @param node the selection the problem is shown on, or null for the roster as a whole
 * @param kind what kind of rule was broken
 * @param constraint the constraint, for {@code MIN}, {@code MAX} and {@code EXACTLY}
 * @param count what was counted, or the roster's cost for {@code COST_LIMIT}
 * @param limit the limit it was compared with
 * @param message the text to show: the constraint's own message if the data has one, otherwise New Recruit's wording
 */
public record Violation(
        @Nullable Selection node,
        Kind kind,
        @Nullable Constraint constraint,
        double count,
        double limit,
        String message) {
    public enum Kind {
        MIN,
        MAX,
        EXACTLY,
        /** A selection of an entry that is hidden. */
        HIDDEN,
        /** A leader with fewer attached units than its association's minimum. */
        ASSOCIATION_MIN,
        /** A leader with more attached units than its association's maximum. */
        ASSOCIATION_MAX,
        /** A roster cost above its limit. */
        COST_LIMIT
    }
}
