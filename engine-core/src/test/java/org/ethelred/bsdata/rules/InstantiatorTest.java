/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.ethelred.bsdata.roster.Selection;
import org.junit.jupiter.api.Test;

/** Compares the instantiator with what New Recruit added by itself for the M5 and M6 synthetic catalogues. */
class InstantiatorTest {
    /** The selections below a unit as {@code path=amount}, depth first, paths joined with a slash. */
    private static List<String> below(Selection s, String prefix) {
        List<String> out = new ArrayList<>();
        for (Selection c : s.children()) {
            String path = prefix + c.name();
            out.add(path + "=" + (int) c.amount());
            out.addAll(below(c, path + "/"));
        }
        return out;
    }

    private static List<String> children(String catalogue, String unit) {
        Fixture fx = new Fixture(catalogue);
        Selection s = fx.unit(unit);
        new Instantiator(fx.model).populate(s);
        return below(s, "");
    }

    private static Map<String, Integer> roots(String catalogue) {
        Fixture fx = new Fixture(catalogue);
        Map<String, Integer> out = new TreeMap<>();
        for (Selection s : new Instantiator(fx.model).populateForce(fx.force)) {
            out.merge(s.name(), 1, Integer::sum);
        }
        return out;
    }

    @Test
    void newForceGetsRootUnitsUpToTheirMinimum() {
        assertEquals(
                Map.of(
                        "R1 force min 2", 2,
                        "R2 roster min 1", 1,
                        "R3 exactly 2 parent", 2,
                        "R6 min 3 max 2", 3,
                        "R7 min 1 scope self", 1),
                roots("M6 Instantiate"));
        // observed in the M5 catalogue: K0 min 3, K1 min 1 and exactly 2, K9 min 1 and exactly 1; percentages,
        // maxima and a negative -1 add nothing
        assertEquals(Map.of("K0 Unit", 3, "K1 Unit", 2, "K9 Unit", 1), roots("M5 Constraints"));
    }

    @Test
    void childrenAreAddedRecursively() {
        assertEquals(List.of("C1 M=1", "C1 M/C1 U=2", "C1 M/C1 U/C1 V=1"), children("M6 Instantiate", "C1 Nested"));
    }

    @Test
    void groupsGetTheirDefaultOrFirstMember() {
        assertEquals(List.of("G2 2=1"), children("M6 Instantiate", "C2 Group default"));
        assertEquals(List.of("G3 1=2"), children("M6 Instantiate", "C3 Group min 2"));
        assertEquals(List.of("G6 1=1"), children("M6 Instantiate", "C6 Member min"));
    }

    @Test
    void childConstraintsDecideByScopeAndMinOnly() {
        // a min 2, c scope self, e scope root-entry, g counts any; not b (exactly), d (scope force), f (min 0)
        assertEquals(List.of("C4 a=2", "C4 c=1", "C4 e=1", "C4 g=1"), children("M6 Instantiate", "C4 Child values"));
        assertEquals(List.of("Sh1=1", "Sh2=2"), children("M6 Instantiate", "C5 Links"));
    }

    @Test
    void m5Catalogue() {
        assertEquals(List.of("K2 x=2", "K2 z=1", "GA 1=1"), children("M5 Constraints", "K2 Unit"));
        assertEquals(List.of("K4 s=1", "K4 h=1"), children("M5 Constraints", "K4 Unit"));
        assertEquals(List.of("K6 a=1", "K6 b=2", "K6 c=1"), children("M5 Constraints", "K6 Unit"));
        assertEquals(List.of("K9 c=1"), children("M5 Constraints", "K9 Unit"));
        assertEquals(List.of(), children("M5 Constraints", "K1 Unit"));
    }
}
