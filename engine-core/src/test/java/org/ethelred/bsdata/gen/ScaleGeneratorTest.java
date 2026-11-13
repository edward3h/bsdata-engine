/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.ethelred.bsdata.DataNode;
import org.ethelred.bsdata.Linker;
import org.ethelred.bsdata.TestData;
import org.ethelred.bsdata.model.GameData;
import org.junit.jupiter.api.Test;

class ScaleGeneratorTest {
    private static final Shape SMALL = Shape.elevenSized().scaled(0.05);

    @Test
    void sameSeedGivesTheSameDataAndAnotherSeedDoesNot() {
        Map<String, DataNode> a = ScaleGenerator.generate(SMALL, 7);
        Map<String, DataNode> b = ScaleGenerator.generate(SMALL, 7);
        Map<String, DataNode> c = ScaleGenerator.generate(SMALL, 8);
        assertEquals(a, b);
        assertEquals(ShapeStats.fingerprint(a), ShapeStats.fingerprint(b));
        assertNotEquals(ShapeStats.fingerprint(a), ShapeStats.fingerprint(c));
    }

    @Test
    void hasAGameSystemAndTheRequestedCatalogues() {
        Map<String, DataNode> files = ScaleGenerator.generate(SMALL, 1);
        assertEquals(46, files.size());
        long libraries =
                files.keySet().stream().filter(n -> n.startsWith("Library")).count();
        assertEquals(9, libraries);
        assertTrue(files.containsKey("Game System.json"));
    }

    @Test
    void everyLinkResolvesUnderTheScopedLookup() {
        Map<String, DataNode> files = ScaleGenerator.generate(SMALL, 3);
        Linker.Report report = Linker.check(files);
        Linker.Stats targets = TestData.required(report.byField().get("targetId"));
        assertEquals(0, targets.unresolved());
        assertEquals(0, targets.outOfScope());
        assertEquals(0, TestData.required(report.byField().get("gameSystemId")).unresolved());
        // and the typed model finds every link target too
        GameData data = GameData.load(files);
        assertEquals(46, data.catalogues().size());
    }

    @Test
    void budgetsAreMetExactlyForTheKindsThatAreSharedOut() {
        Shape shape = SMALL;
        ShapeStats stats = ShapeStats.of(ScaleGenerator.generate(shape, 5));
        assertEquals(shape.costs(), stats.count("costs"));
        assertEquals(
                shape.profiles() + shape.sharedProfiles(), stats.count("profiles") + stats.count("sharedProfiles"));
        assertEquals(shape.constraints(), stats.count("constraints"));
        assertEquals(shape.modifiers(), stats.count("modifiers"));
        assertEquals(shape.modifierGroups(), stats.count("modifierGroups"));
        assertEquals(shape.conditionGroups(), stats.count("conditionGroups"));
        assertEquals(shape.repeats(), stats.count("repeats"));
        assertEquals(shape.associations(), stats.count("associations"));
        assertEquals(shape.rootLinks() + shape.nestedLinks(), stats.count("entryLinks"));
    }

    @Test
    void theFullSizeDatasetMatchesTheMeasuredShape() {
        ShapeStats stats = ShapeStats.of(ScaleGenerator.generate(Shape.elevenSized(), 1));
        // the real system had 103,765 objects with ids; allow a few percent either way
        assertTrue(stats.objectsWithId() > 98_000 && stats.objectsWithId() < 108_000, "" + stats.objectsWithId());
        int entries = stats.count("selectionEntries") + stats.count("sharedSelectionEntries");
        int groups = stats.count("selectionEntryGroups") + stats.count("sharedSelectionEntryGroups");
        assertTrue(entries > 11_500 && entries < 12_600, "entries " + entries);
        assertTrue(groups > 4_200 && groups < 4_800, "groups " + groups);
        assertEquals(17_869, stats.count("entryLinks"));
        assertEquals(24_870, stats.count("modifiers"));
        assertEquals(31_904, stats.count("conditions"));
        assertTrue(Math.abs(stats.count("characteristics") - 58_939) < 58_939 * 0.07, "characteristics");
        assertTrue(Math.abs(stats.count("catalogueLinks") - 109) < 109 * 0.15, "catalogueLinks");
    }
}
