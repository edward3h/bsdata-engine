/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

/**
 * Makes a modifier apply several times: the query's count divided by {@code value}, rounded down (up if
 * {@code roundUp}), times {@code repeats}.
 */
public record Repeat(Query query, double value, double repeats, boolean roundUp) {}
