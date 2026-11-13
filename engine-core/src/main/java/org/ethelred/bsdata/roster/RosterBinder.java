/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.roster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.ethelred.bsdata.Roster;
import org.ethelred.bsdata.model.Catalogue;
import org.ethelred.bsdata.model.GameData;
import org.ethelred.bsdata.model.Parts.ForceEntry;
import org.jspecify.annotations.Nullable;

/** Attaches a roster file (an export from New Recruit) to the game data it was made from. */
public final class RosterBinder {
    /** The bound roster, and what could not be bound. A selection that fails to bind is left out. */
    public record Bound(RosterModel model, List<String> problems) {}

    private RosterBinder() {}

    public static Bound bind(GameData data, Roster file) {
        RosterModel model = new RosterModel(data, file.name());
        List<String> problems = new ArrayList<>();
        for (Roster.Force f : file.forces()) {
            Catalogue catalogue = data.catalogueById(f.catalogueId());
            if (catalogue == null) {
                problems.add("force " + f.name() + ": catalogue " + f.catalogueId() + " not loaded");
                continue;
            }
            ForceEntry entry = data.forceEntry(catalogue.file(), f.entryId());
            if (entry == null) {
                problems.add("force " + f.name() + ": force entry " + f.entryId() + " not found");
                continue;
            }
            ForceNode force = model.addForce(f.id(), entry, catalogue);
            for (Roster.Selection s : f.selections()) {
                bindSelection(data, model, catalogue.file(), s, null, force, problems);
            }
        }
        return new Bound(model, problems);
    }

    private static void bindSelection(
            GameData data,
            RosterModel model,
            String catalogueFile,
            Roster.Selection s,
            @Nullable Selection parent,
            @Nullable ForceNode force,
            List<String> problems) {
        GameData.Resolved r =
                data.resolvePath(catalogueFile, Arrays.asList(s.entryId().split("::")));
        if (r == null) {
            problems.add("selection " + s.name() + ": entry " + s.entryId() + " not found");
            return;
        }
        // the file gives the total number; amounts are per parent
        double parentTotal = parent == null ? 1 : parent.total();
        double amount = parentTotal == 0 ? s.number() : s.number() / parentTotal;
        Selection bound = model.addResolved(s.id(), parent, r, amount, force);
        for (Roster.Selection child : s.selections()) {
            bindSelection(data, model, catalogueFile, child, bound, null, problems);
        }
    }
}
