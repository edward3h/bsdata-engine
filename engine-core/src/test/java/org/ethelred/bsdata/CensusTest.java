/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CensusTest {
    @Test
    void countsObjectsIdsAndUnresolvedTargets() {
        var json = "{\"catalogue\":{\"id\":\"a\",\"selectionEntries\":[{\"id\":\"b\"},{\"id\":\"c\"}],"
                + "\"entryLinks\":[{\"id\":\"d\",\"targetId\":\"b\"},{\"id\":\"e\",\"targetId\":\"zzz\"}]}}";
        var r = Census.of(new MinimalJsonSource().parse(json));
        assertEquals(new Census.Result(6, 5, 2, 1), r);
    }
}
