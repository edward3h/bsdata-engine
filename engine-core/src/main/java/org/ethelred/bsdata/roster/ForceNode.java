/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.roster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.ethelred.bsdata.model.Catalogue;
import org.ethelred.bsdata.model.Child;
import org.ethelred.bsdata.model.Parts.ForceEntry;
import org.jspecify.annotations.Nullable;

/** A force in a roster: a force entry taken from a catalogue, holding the selections made in it. */
public final class ForceNode {
    private final RosterModel model;
    private final String id;
    private final ForceEntry entry;
    private final Catalogue catalogue;
    private final List<Selection> selections = new ArrayList<>();

    ForceNode(RosterModel model, String id, ForceEntry entry, Catalogue catalogue) {
        this.model = model;
        this.id = id;
        this.entry = entry;
        this.catalogue = catalogue;
    }

    public String id() {
        return id;
    }

    public ForceEntry entry() {
        return entry;
    }

    /** The catalogue the force was added from; its scope decides how links resolve. */
    public Catalogue catalogue() {
        return catalogue;
    }

    public List<Selection> selections() {
        return Collections.unmodifiableList(selections);
    }

    /** Adds a unit (an entry or a link at the top of the catalogue), or returns null if a link cannot be followed. */
    public @Nullable Selection add(Child child, double amount) {
        return model.addUnder(null, catalogue.file(), child, amount, this);
    }

    void attach(Selection s) {
        selections.add(s);
    }

    /** The own costs of all selections in this force. */
    public Map<String, Double> costs() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Selection s : selections) {
            s.subtreeCosts().forEach((type, v) -> out.merge(type, v, Double::sum));
        }
        return out;
    }
}
