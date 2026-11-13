/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.io.IOException;
import java.util.List;
import org.ethelred.bsdata.TestData;
import org.ethelred.bsdata.model.Catalogue;
import org.ethelred.bsdata.model.Child;
import org.ethelred.bsdata.model.Entry;
import org.ethelred.bsdata.model.GameData;
import org.ethelred.bsdata.model.Link;
import org.ethelred.bsdata.roster.ForceNode;
import org.ethelred.bsdata.roster.RosterModel;
import org.ethelred.bsdata.roster.Selection;
import org.jspecify.annotations.Nullable;

/** A roster built by hand from the synthetic Extension Tests data, for the evaluator tests. */
final class Fixture {
    final GameData data;
    final Catalogue catalogue;
    final RosterModel model;
    final ForceNode force;

    Fixture(String catalogueName) {
        try {
            data = TestData.gameData();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        catalogue = data.catalogues().stream()
                .filter(c -> c.name().equals(catalogueName))
                .findFirst()
                .orElseThrow();
        model = new RosterModel(data, "test");
        force = model.addForce(
                TestData.required(data.gameSystem()).forceEntries().get(0), catalogue);
    }

    /** Adds a unit by the name of the link or entry at the top of the catalogue. */
    Selection unit(String namePrefix) {
        for (Link l : catalogue.rootLinks()) {
            var target = data.target(catalogue.file(), l);
            if (target != null && target.entry().name().startsWith(namePrefix)) {
                return TestData.required(force.add(l, 1));
            }
        }
        throw new IllegalArgumentException(namePrefix);
    }

    /** The entry named {@code name} below {@code parent}, at any depth through groups and plain entries. */
    static Entry entry(Entry parent, String name) {
        for (Child c : parent.body().children()) {
            if (c instanceof Entry e) {
                if (e.name().startsWith(name)) {
                    return e;
                }
                try {
                    return entry(e, name);
                } catch (IllegalArgumentException notBelowThisOne) {
                    // keep looking
                }
            }
        }
        throw new IllegalArgumentException(name);
    }

    Selection add(Selection parent, String name, double amount) {
        Child child = child(parent.entry().body().children(), parent.file(), name);
        if (child == null) {
            throw new IllegalArgumentException(name);
        }
        return TestData.required(parent.add(child, amount));
    }

    /** The first entry or link named {@code name} (a link also by its target's name), looking through groups. */
    private @Nullable Child child(List<Child> children, String file, String name) {
        for (Child c : children) {
            if (c instanceof Link l) {
                var target = data.target(file, l);
                if (l.name().startsWith(name)
                        || target != null && target.entry().name().startsWith(name)) {
                    return l;
                }
            } else if (c instanceof Entry e) {
                if (e.name().startsWith(name)) {
                    return e;
                }
                Child deeper = child(e.body().children(), file, name);
                if (deeper != null) {
                    return deeper;
                }
            }
        }
        return null;
    }

    String costTypeId(String name) {
        return TestData.required(data.gameSystem()).costTypes().stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElseThrow()
                .id();
    }

    static List<ModifierDef> modifiers(Entry e) {
        return ModifierDef.of(e.body().raw().modifiers());
    }
}
