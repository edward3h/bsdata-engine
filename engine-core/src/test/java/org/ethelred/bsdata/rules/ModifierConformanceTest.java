/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.ethelred.bsdata.DataNode;
import org.ethelred.bsdata.MinimalJsonSource;
import org.ethelred.bsdata.TestData;
import org.junit.jupiter.api.Test;

/**
 * Runs the observations in {@code nr-extension-tests/tests/expected.json} that need no roster: a base value, some
 * modifiers, and the value New Recruit showed. Entries with repeats need a roster to count, and entries with dice or
 * other compound values need number tokens (M4); the engine reports those as unsupported and they are listed, not
 * compared.
 */
class ModifierConformanceTest {
    private record Case(
            String id,
            String base,
            List<Modifier> modifiers,
            String expect,
            boolean hasRepeats,
            boolean hasConditions) {}

    private static List<Case> cases() throws IOException {
        DataNode all = new MinimalJsonSource().parse(TestData.resource("/conformance/expected.json"));
        List<Case> out = new ArrayList<>();
        for (DataNode n : ((DataNode.Arr) all).items()) {
            Map<String, DataNode> f = ((DataNode.Obj) n).fields();
            DataNode expect = f.get("expect");
            if (expect == null || expect instanceof DataNode.Null) {
                continue; // no observation recorded in the file
            }
            String base = "";
            for (DataNode v :
                    ((DataNode.Obj) TestData.required(f.get("base"))).fields().values()) {
                base = text(v);
            }
            List<DataNode> mods = ((DataNode.Arr) TestData.required(f.get("modifiers"))).items();
            boolean repeats =
                    mods.stream().anyMatch(m -> ((DataNode.Obj) m).fields().containsKey("repeats"));
            // some entries describe a roster to build (select 0..4 toggles); their conditions are not in the file
            boolean needsRoster =
                    text(f.getOrDefault("note", new DataNode.Str(""))).contains("select ");
            boolean conditions = needsRoster
                    || mods.stream().anyMatch(m -> {
                        var keys = ((DataNode.Obj) m).fields().keySet();
                        return keys.contains("conditions")
                                || keys.contains("conditionGroups")
                                || keys.contains("localConditionGroups");
                    });
            out.add(new Case(
                    text(TestData.required(f.get("id"))), base, Modifier.of(mods), text(expect), repeats, conditions));
        }
        return out;
    }

    private static String text(DataNode n) {
        return switch (n) {
            case DataNode.Str s -> s.value();
            case DataNode.Num d -> NumberText.format(d.value());
            default -> "";
        };
    }

    @Test
    void everyObservationTheEngineHandlesMatchesNewRecruit() throws IOException {
        List<String> wrong = new ArrayList<>();
        TreeSet<String> needsRepeats = new TreeSet<>();
        TreeSet<String> needsTokens = new TreeSet<>();
        int compared = 0;
        for (Case c : cases()) {
            if (c.hasRepeats() || c.hasConditions()) {
                needsRepeats.add(c.id());
                continue;
            }
            List<String> diagnostics = new ArrayList<>();
            List<ModifierEngine.Applied> applied = c.modifiers().stream()
                    .map(m -> new ModifierEngine.Applied(m, 1))
                    .toList();
            String result = ModifierEngine.applyText(c.base(), applied, diagnostics);
            if (!diagnostics.isEmpty()) {
                needsTokens.add(c.id());
                continue;
            }
            compared++;
            if (!result.equals(c.expect())) {
                wrong.add(c.id() + ": base \"" + c.base() + "\" gave \"" + result + "\", NR showed \"" + c.expect()
                        + "\"");
            }
        }
        System.out.println(
                "compared " + compared + "; needs a roster " + needsRepeats + "; needs tokens " + needsTokens);
        assertEquals("", String.join("\n", wrong));
    }
}
