/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.ethelred.bsdata.TestData;
import org.junit.jupiter.api.Test;

class GameDataTest {
    private final GameData data = load();

    private static GameData load() {
        try {
            return TestData.gameData();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void loadsTheGameSystemAndCatalogues() {
        Catalogue gs = TestData.required(data.gameSystem());
        assertEquals("Extension Tests", gs.name());
        assertTrue(gs.gameSystem());
        assertEquals(
                List.of("pts", "Enh"),
                gs.costTypes().stream().map(Parts.CostType::name).toList());
        assertNull(data.catalogueById("no-such-id"));
        assertEquals(6, data.catalogues().stream().filter(c -> !c.gameSystem()).count());
    }

    @Test
    void parsesEntriesLinksAndCosts() {
        Catalogue m1 = data.catalogues().stream()
                .filter(c -> c.name().equals("M1 Costs"))
                .findFirst()
                .orElseThrow();
        assertEquals(3, m1.rootLinks().size());
        Entry squad = m1.sharedEntries().stream()
                .filter(e -> e.name().equals("M1 Squad"))
                .findFirst()
                .orElseThrow();
        assertEquals("unit", squad.type());
        assertEquals(10.0, squad.body().costs().get(0).value());
        // children: a model, a group, and a link that carries its own cost
        assertEquals(3, squad.body().children().size());
        Link banner = (Link) squad.body().children().get(2);
        assertEquals(3.0, banner.body().costs().get(0).value());
    }

    @Test
    void resolvesALinkTargetThroughTheScopedLookup() {
        Catalogue m1 = data.catalogues().stream()
                .filter(c -> c.name().equals("M1 Costs"))
                .findFirst()
                .orElseThrow();
        Link hero = m1.rootLinks().stream()
                .filter(l -> l.name().startsWith("Hero"))
                .findFirst()
                .orElseThrow();
        GameData.Resolved r = TestData.required(data.target(m1.file(), hero));
        assertEquals("M1 Hero", r.entry().name());
        assertEquals(hero, r.link());
    }
}
