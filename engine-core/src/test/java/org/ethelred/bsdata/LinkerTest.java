/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LinkerTest {
    private static final JsonSource JSON = new MinimalJsonSource();

    private static final DataNode GAME_SYSTEM = JSON.parse("""
            {"gameSystem": {"id": "gs", "costTypes": [{"id": "pts"}]}}
            """);

    @Test
    void classifiesReferencesBySharedScope() {
        var lib = JSON.parse("""
                {"catalogue": {"id": "lib", "library": true, "selectionEntries": [{"id": "b"}]}}
                """);
        var linked = JSON.parse("""
                {"catalogue": {
                  "id": "a",
                  "catalogueLinks": [{"id": "l", "targetId": "lib"}],
                  "entryLinks": [
                    {"id": "d", "targetId": "b"},
                    {"id": "e", "targetId": "zzz"}
                  ],
                  "costs": [{"typeId": "pts"}],
                  "conditions": [{"childId": "model"}, {"childId": "d"}]
                }}
                """);
        var unlinked = JSON.parse("""
                {"catalogue": {"id": "f", "entryLinks": [{"id": "g", "targetId": "b"}]}}
                """);
        var r = Linker.check(Map.of("gs.json", GAME_SYSTEM, "lib.json", lib, "a.json", linked, "f.json", unlinked));
        assertEquals(4, r.files());
        // a: b resolves, zzz is undefined; f: b exists but is not linked
        assertEquals(new Linker.Stats(4, 2, 0, 1, 1), r.byField().get("targetId"));
        assertEquals(new Linker.Stats(2, 1, 1, 0, 0), r.byField().get("childId"));
        assertEquals(new Linker.Stats(1, 1, 0, 0, 0), r.byField().get("typeId"));
        assertEquals(2, r.problems().size());
        // both groups occur once, so they sort by value
        assertEquals("b", r.problems().get(0).value());
        assertEquals(true, r.problems().get(0).outOfScope());
        assertEquals("zzz", r.problems().get(1).value());
        assertEquals(false, r.problems().get(1).outOfScope());
    }

    @Test
    void fieldsOtherThanLinkTargetsMatchAnywhere() {
        var elsewhere = JSON.parse("""
                {"catalogue": {"id": "other", "costTypes": [{"id": "mp"}], "selectionEntries": [{"id": "unit"}]}}
                """);
        var cat = JSON.parse("""
                {"catalogue": {
                  "id": "c",
                  "costs": [{"typeId": "mp"}, {"typeId": "nowhere"}],
                  "conditions": [{"childId": "unit"}, {"childId": "other"}]
                }}
                """);
        var r = Linker.check(Map.of("other.json", elsewhere, "c.json", cat));
        assertEquals(new Linker.Stats(2, 1, 0, 0, 1), r.byField().get("typeId"));
        assertEquals(new Linker.Stats(2, 2, 0, 0, 0), r.byField().get("childId"));
    }

    @Test
    void reportTextIsStable() {
        var cat = JSON.parse("""
                {"catalogue": {"id": "c", "entryLinks": [{"id": "x", "targetId": "missing"}]}}
                """);
        var text = Linker.check(Map.of("c.json", cat)).format(5);
        assertEquals("""
                files=1 objects=3 ids=2 idsInSeveralFiles=0
                targetId total=1 resolved=0 keyword=0 outOfScope=0 unresolved=1
                problem groups=1
                  targetId in entryLinks missing (undefined) x1
                """, text);
    }
}
