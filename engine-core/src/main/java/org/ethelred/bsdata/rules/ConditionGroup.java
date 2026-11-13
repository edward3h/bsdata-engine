/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A group of conditions. {@code and} needs all, {@code or} needs one, {@code not} needs none, and {@code count} needs
 * the number of passing members to lie in {@code [min, max]} (both inclusive; an unset {@code min} acts like 1).
 * Groups nest, and may hold local groups. The numeric and comparison types the editor lists are parsed but not
 * evaluated.
 */
public record ConditionGroup(
        String type,
        @Nullable Double min,
        @Nullable Double max,
        List<Condition> conditions,
        List<ConditionGroup> groups,
        List<LocalGroup> localGroups) {}
