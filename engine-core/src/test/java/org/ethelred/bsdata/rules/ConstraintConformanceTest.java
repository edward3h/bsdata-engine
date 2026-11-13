/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.ethelred.bsdata.DataNode;
import org.ethelred.bsdata.MinimalJsonSource;
import org.ethelred.bsdata.TestData;
import org.ethelred.bsdata.roster.Selection;
import org.junit.jupiter.api.Test;

/**
 * Compares the validator with what New Recruit showed for the {@code M5 Constraints} catalogue: for each recorded state
 * (the entries selected under one unit), the messages on that unit.
 */
class ConstraintConformanceTest {
    private static DataNode.Obj observed() throws IOException {
        return (DataNode.Obj)
                new MinimalJsonSource().parse(TestData.resource("/conformance/constraints-observed.json"));
    }

    private static List<DataNode> items(DataNode node) {
        return ((DataNode.Arr) node).items();
    }

    private static Map<String, DataNode> fields(DataNode node) {
        return ((DataNode.Obj) node).fields();
    }

    private static String text(DataNode node) {
        return ((DataNode.Str) node).value();
    }

    private static List<String> messages(DataNode node) {
        return items(node).stream()
                .map(ConstraintConformanceTest::text)
                .sorted()
                .toList();
    }

    private static List<String> messagesOn(List<Violation> all, Selection unit) {
        return all.stream()
                .filter(v -> v.node() == unit)
                .map(Violation::message)
                .sorted()
                .toList();
    }

    @Test
    void statesMatchNewRecruit() throws IOException {
        List<String> failures = new ArrayList<>();
        int n = 0;
        for (DataNode c : items(TestData.required(observed().fields().get("cases")))) {
            Map<String, DataNode> f = fields(c);
            String unitName = text(TestData.required(f.get("unit")));
            Fixture fx = new Fixture("M5 Constraints");
            Selection unit = fx.unit(unitName);
            for (var e : fields(TestData.required(f.get("state"))).entrySet()) {
                double amount = ((DataNode.Num) e.getValue()).value();
                if (amount > 0) {
                    fx.add(unit, e.getKey(), amount);
                }
            }
            Validator v = new Validator(fx.model, new View(fx.model));
            List<String> actual = messagesOn(v.validate(), unit);
            List<String> expected = messages(TestData.required(f.get("messages")));
            n++;
            if (!actual.equals(expected)) {
                failures.add(unitName + " " + f.get("state") + "\n  NR:     " + expected + "\n  engine: " + actual);
            }
        }
        assertTrue(n > 100, "cases read: " + n);
        assertEquals(List.of(), failures, failures.size() + " of " + n + " differ");
    }

    @Test
    void scopesCountUnitsLikeNewRecruit() throws IOException {
        for (DataNode c : items(TestData.required(observed().fields().get("k5")))) {
            Map<String, DataNode> f = fields(c);
            int units = (int) ((DataNode.Num) TestData.required(f.get("units"))).value();
            Fixture fx = new Fixture("M5 Constraints");
            List<Selection> added = new ArrayList<>();
            for (int i = 0; i < units; i++) {
                added.add(fx.unit("K5 Unit"));
            }
            List<Violation> all = new Validator(fx.model, new View(fx.model)).validate();
            List<DataNode> rows = items(TestData.required(f.get("rows")));
            for (int i = 0; i < units; i++) {
                assertEquals(messages(rows.get(i)), messagesOn(all, added.get(i)), units + " units, unit " + i);
            }
        }
    }

    @Test
    void rosterCostLimit() throws IOException {
        Fixture fx = new Fixture("M5 Constraints");
        fx.unit("K1 Unit"); // 10 pts
        String pts = fx.costTypeId("pts");
        fx.model.setCostLimit(pts, 10);
        assertEquals(
                List.of(),
                new Validator(fx.model, new View(fx.model))
                        .validate().stream()
                                .filter(v -> v.kind() == Violation.Kind.COST_LIMIT)
                                .toList());
        fx.model.setCostLimit(pts, 9);
        var v = new Validator(fx.model, new View(fx.model))
                .validate().stream()
                        .filter(x -> x.kind() == Violation.Kind.COST_LIMIT)
                        .toList();
        assertEquals(1, v.size());
        assertEquals("Roster has 1 pts too many (max 9)", v.get(0).message());
        fx.model.setCostLimit(pts, -1);
        assertEquals(
                0,
                new Validator(fx.model, new View(fx.model))
                        .validate().stream()
                                .filter(x -> x.kind() == Violation.Kind.COST_LIMIT)
                                .count());
    }
}
