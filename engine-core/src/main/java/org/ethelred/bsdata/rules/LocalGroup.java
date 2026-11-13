/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.util.List;

/**
 * A condition on a <em>count of selections that satisfy inner conditions</em>: every selection in the scope for which
 * all the inner conditions hold (each acting as "self") is counted, and the count is compared with {@code value}.
 */
public record LocalGroup(
        String type,
        double value,
        String scope,
        String field,
        boolean includeChildSelections,
        boolean includeChildForces,
        double repeats,
        List<Condition> conditions,
        List<ConditionGroup> groups) {}
