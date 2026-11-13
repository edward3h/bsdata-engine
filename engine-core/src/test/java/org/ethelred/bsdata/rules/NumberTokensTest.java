/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.ethelred.bsdata.DataNode;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class NumberTokensTest {
    private static String plus(String text, @Nullable Integer position) {
        return NumberTokens.map(text, position, true, n -> n + 10);
    }

    @Test
    void everyNumberChangesWithoutAPositionOrWithZero() {
        assertEquals("D16+11", plus("D6+1", null));
        assertEquals("D16+11", plus("D6+1", 0)); // 0 means every number, not the first (observed)
        assertEquals("13+", plus("3+", null));
        assertEquals("15", plus("5", null));
    }

    @Test
    void positiveCountsFromOneAndNegativeFromTheEnd() {
        assertEquals("D16+1+2", plus("D6+1+2", 1));
        assertEquals("D6+11+2", plus("D6+1+2", 2));
        assertEquals("D6+1+12", plus("D6+1+2", -1));
        assertEquals("D16+1+2", plus("D6+1+2", -3));
    }

    @Test
    void aPositionOutsideTheNumbersChangesNothing() {
        assertEquals("D6+1+2", plus("D6+1+2", 4));
        assertEquals("D6+1+2", plus("D6+1+2", -4));
    }

    @Test
    void anEmptyValueIsZeroAndTextWithoutNumbersIsLeftAlone() {
        assertEquals("10", plus("", null));
        assertEquals("abc", plus("abc", null));
        assertEquals("-", plus("-", null));
    }

    @Test
    void decimalsAndALeadingMinusAreOneNumber() {
        assertEquals("12.5", plus("2.5", null));
        assertEquals("7", plus("-3", null));
        // a minus directly before digits belongs to the number wherever it is (observed): D6-3 has 6 and -3
        assertEquals("D16-3", plus("D6-3", 1));
        assertEquals("D67", plus("D6-3", -1));
        assertEquals("D167", plus("D6-3", null));
    }

    @Test
    void aNumberTheChangeLeavesAloneKeepsItsText() {
        assertEquals("007", NumberTokens.map("007", null, true, n -> Math.max(n, 5))); // floor 5 on 7: unchanged
        assertEquals("5", NumberTokens.map("007", null, true, n -> Math.max(n, 9) - 4));
        assertEquals("", NumberTokens.map("", null, false, n -> Math.max(n, 5))); // a clamp leaves an empty value empty
    }

    @Test
    void resultsAreWrittenWithoutANeedlessDecimalPoint() {
        assertEquals("2", NumberTokens.map("4", null, true, n -> n / 2));
        assertEquals("2.5", NumberTokens.map("5", null, true, n -> n / 2));
    }

    // ---- the dice trick from the spec's worked example (section 8)

    private static Modifier mod(
            String type,
            String field,
            @Nullable String value,
            @Nullable String arg,
            @Nullable String join,
            @Nullable Integer position) {
        java.util.Map<String, DataNode> f = new java.util.LinkedHashMap<>();
        f.put("type", new DataNode.Str(type));
        f.put("field", new DataNode.Str(field));
        if (value != null) {
            f.put("value", new DataNode.Str(value));
        }
        if (arg != null) {
            f.put("arg", new DataNode.Str(arg));
        }
        if (join != null) {
            f.put("join", new DataNode.Str(join));
        }
        if (position != null) {
            f.put("position", new DataNode.Num(position));
        }
        return Modifier.of(List.of(new DataNode.Obj(f))).get(0);
    }

    @Test
    void appendIncrementAtTheEndThenStripLeavesADiceExpressionWithAnAddedNumber() {
        // "+0" is appended so the expression has a trailing number, that number is raised by 2, and any leftover "+0"
        // is
        // removed. Whatever order the three are listed in, the stages run append, then arithmetic, then replace.
        Modifier append = mod("append", "A", "+0", null, "", null);
        Modifier increment = mod("increment", "A", "2", null, null, -1);
        Modifier strip = mod("replace", "A", null, "+0", null, null);
        List<List<Modifier>> orders = List.of(
                List.of(append, increment, strip),
                List.of(strip, increment, append),
                List.of(increment, strip, append));
        for (List<Modifier> order : orders) {
            List<ModifierEngine.Applied> applied = new ArrayList<>();
            order.forEach(m -> applied.add(new ModifierEngine.Applied(m, 1)));
            assertEquals("D6+2", ModifierEngine.applyText("D6", applied, new ArrayList<>()));
            // the data applies this only to bare dice (a category marks them); on D6+3 the added +0 becomes the last
            // number
            assertEquals("D6+3+2", ModifierEngine.applyText("D6+3", applied, new ArrayList<>()));
        }
    }
}
