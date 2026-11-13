/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import org.ethelred.bsdata.TestData;
import org.ethelred.bsdata.model.Catalogue;
import org.ethelred.bsdata.model.Entry;
import org.ethelred.bsdata.model.GameData;
import org.ethelred.bsdata.model.Parts.Profile;
import org.junit.jupiter.api.Test;

/** The condition shapes in the Extension Tests catalogue, which are the shapes the real data uses. */
class ConditionsParseTest {
    private final Catalogue catalogue = load();

    private static Catalogue load() {
        try {
            GameData data = TestData.gameData();
            return data.catalogues().stream()
                    .filter(c -> c.name().equals("Extension Tests Catalogue"))
                    .findFirst()
                    .orElseThrow();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Entry entry(String namePrefix) {
        return catalogue.sharedEntries().stream()
                .filter(e -> e.name().startsWith(namePrefix))
                .findFirst()
                .orElseThrow();
    }

    private Profile profile(Entry e, String name) {
        return e.body().profiles().stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aCountGroupHasItsConditionsAndBounds() {
        List<ModifierDef> mods =
                ModifierDef.of(profile(entry("UNIT A"), "T16a").raw().modifiers());
        assertEquals(1, mods.size());
        Conditions c = mods.get(0).conditions();
        assertEquals(1, c.groups().size());
        ConditionGroup g = c.groups().get(0);
        assertEquals("count", g.type());
        assertEquals(3.0, TestData.required(g.min()));
        assertEquals(null, g.max());
        assertEquals(4, g.conditions().size());
        Condition first = g.conditions().get(0);
        assertEquals("atLeast", first.type());
        assertEquals("self", first.scope());
        assertEquals("selections", first.field());
        assertEquals(1.0, first.value());
        assertEquals(true, first.includeChildSelections());
    }

    @Test
    void aMinAndMaxAreBothKept() {
        ConditionGroup g = ModifierDef.of(profile(entry("UNIT A"), "T16b").raw().modifiers())
                .get(0)
                .conditions()
                .groups()
                .get(0);
        assertEquals(3.0, TestData.required(g.min()));
        assertEquals(3.0, TestData.required(g.max()));
    }

    @Test
    void aRepeatReadsItsQuery() {
        List<Repeat> repeats = profileRepeats();
        assertEquals("model", repeats.get(0).query().childId());
        assertEquals("self", repeats.get(0).query().scope());
        assertEquals(1.0, repeats.get(0).repeats());
        assertEquals(false, repeats.get(0).roundUp());
    }

    private List<Repeat> profileRepeats() {
        Entry unit = entry("UNIT A");
        for (Profile p : unit.body().profiles()) {
            for (ModifierDef m : ModifierDef.of(p.raw().modifiers())) {
                if (!m.repeats().isEmpty()) {
                    return m.repeats();
                }
            }
        }
        throw new IllegalStateException("no repeats found");
    }

    @Test
    void aLocalGroupInsideAConditionGroupKeepsItsInnerConditions() {
        Entry g1 = entry("G1 ");
        List<ModifierDef> mods = ModifierDef.of(g1.body().raw().modifiers());
        LocalGroup local = mods.stream()
                .flatMap(m -> m.conditions().groups().stream())
                .flatMap(g -> g.localGroups().stream())
                .findFirst()
                .orElseThrow();
        assertEquals("atLeast", local.type());
        assertEquals(2.0, local.value());
        assertEquals("parent", local.scope());
        assertEquals(
                List.of("before", "instanceOf"),
                local.conditions().stream().map(Condition::type).toList());
        assertEquals("any", local.conditions().get(0).childId());
    }

    @Test
    void anOrdinaryBeforeConditionIsParsed() {
        Entry unit = entry("UNIT A");
        boolean found = false;
        for (var model : unit.body().children()) {
            if (model instanceof Entry e && e.name().startsWith("Model")) {
                for (Profile p : e.body().profiles()) {
                    for (ModifierDef m : ModifierDef.of(p.raw().modifiers())) {
                        found |= m.conditions().conditions().stream()
                                .anyMatch(c -> c.type().equals("before"));
                    }
                }
            }
        }
        assertEquals(true, found);
    }
}
