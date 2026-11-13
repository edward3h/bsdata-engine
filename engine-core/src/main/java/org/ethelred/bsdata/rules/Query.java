/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

/**
 * What a condition, a repeat or a local group counts: a field of the nodes found from a scope, filtered by
 * {@code childId}. The comparison and the {@code value} belong to the condition, not the query.
 */
public record Query(
        String field,
        String scope,
        String childId,
        boolean includeChildSelections,
        boolean includeChildForces,
        boolean percentValue) {}
