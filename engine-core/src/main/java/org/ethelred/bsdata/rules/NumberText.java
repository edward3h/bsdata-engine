/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

/** Writing numbers as text the way the data does: whole numbers without a decimal point. */
final class NumberText {
    private NumberText() {}

    static String format(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }
}
