/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.ethelred.bsdata.DataNode;
import org.ethelred.bsdata.Roster;
import org.ethelred.bsdata.TestData;
import org.ethelred.bsdata.model.Parts.Profile;
import org.ethelred.bsdata.roster.RosterBinder;
import org.ethelred.bsdata.roster.Selection;
import org.junit.jupiter.api.Test;

/**
 * Differential test against New Recruit. The M2 Scopes catalogue has profiles whose {@code Num} is incremented once per
 * unit a repeat's query counts, so the exported {@code Num} is NR's count. For every such profile this runs the same
 * query through the engine, at the selection that owns the profile, and compares.
 */
class ScopeDifferentialTest {
    private record Row(String host, String profile, Query query, double nr, double engine) {}

    private List<Row> rows() throws IOException {
        var data = TestData.gameData();
        Roster file = TestData.roster("m2-scopes.roster.json");
        RosterBinder.Bound bound = RosterBinder.bind(data, file);
        if (!bound.problems().isEmpty()) {
            throw new IllegalStateException(bound.problems().toString());
        }
        Evaluator ev = new Evaluator(bound.model());
        // NR's Num for each profile of each selection, by selection id and profile name
        Map<String, Map<String, Double>> nr = new TreeMap<>();
        file.forces().forEach(f -> f.selections().forEach(s -> collect(s, nr)));
        List<Row> out = new ArrayList<>();
        for (Selection s : bound.model().selections()) {
            Map<String, Double> nums = nr.getOrDefault(s.id(), Map.of());
            for (Profile p : s.entry().body().profiles()) {
                Double num = nums.get(p.name());
                var mods = Modifier.of(p.raw().modifiers());
                if (num == null || mods.isEmpty() || mods.get(0).repeats().isEmpty()) {
                    continue;
                }
                Query q = mods.get(0).repeats().get(0).query();
                Double count = ev.count(s, q);
                out.add(new Row(s.name(), p.name(), q, num, count == null ? 0 : Math.floor(count)));
            }
        }
        return out;
    }

    private static void collect(Roster.Selection s, Map<String, Map<String, Double>> out) {
        Map<String, Double> nums = new TreeMap<>();
        for (DataNode p : s.profiles()) {
            if (p instanceof DataNode.Obj o
                    && o.fields().get("name") instanceof DataNode.Str name
                    && o.fields().get("characteristics") instanceof DataNode.Arr chars) {
                for (DataNode c : chars.items()) {
                    if (c instanceof DataNode.Obj co
                            && co.fields().get("name") instanceof DataNode.Str cn
                            && cn.value().equals("Num")
                            && co.fields().get("$text") instanceof DataNode.Str text) {
                        nums.put(name.value(), Double.parseDouble(text.value()));
                    }
                }
            }
        }
        out.put(s.id(), nums);
        s.selections().forEach(c -> collect(c, out));
    }

    @Test
    void everyCountMatchesNewRecruitExceptAncestorCounting() throws IOException {
        List<Row> rows = rows();
        assertEquals(1284, rows.size(), "profiles compared");
        List<String> bad = new ArrayList<>();
        int ancestor = 0;
        for (Row r : rows) {
            if (r.query().scope().equals("ancestor")) {
                ancestor++; // known gap: NR's counts for this scope are not modelled yet (see the evaluator's
                // diagnostics)
                continue;
            }
            if (r.nr() != r.engine()) {
                bad.add(r.host() + " " + r.profile() + " " + r.query() + " NR=" + r.nr() + " engine=" + r.engine());
            }
        }
        assertEquals("", String.join("\n", bad));
        assertEquals(60, ancestor);
    }
}
