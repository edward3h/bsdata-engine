/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

/**
 * One condition: count something (a query), then compare the count with {@code value}.
 *
 * @param type {@code atLeast}, {@code atMost}, {@code lessThan}, {@code greaterThan}, {@code equalTo},
 *     {@code notEqualTo}, {@code instanceOf}, {@code notInstanceOf}, {@code before}, {@code always} or {@code never}
 * @param field {@code selections}, {@code forces}, {@code associations}, a cost type id, or {@code limit::<cost id>}
 * @param scope where to count from (see {@code docs/bsdata-extensions-spec.md}, section 5.9)
 * @param childId what to count: {@code any}, a type word such as {@code unit}, or an id
 * @param percentValue whether the comparison is of a ratio to the count of everything
 */
public record Condition(
        String type,
        String field,
        String scope,
        String childId,
        double value,
        boolean includeChildSelections,
        boolean includeChildForces,
        boolean percentValue,
        boolean shared) {}
