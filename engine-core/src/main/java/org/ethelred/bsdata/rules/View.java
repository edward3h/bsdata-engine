/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.ethelred.bsdata.model.Parts.Characteristic;
import org.ethelred.bsdata.model.Parts.Profile;
import org.ethelred.bsdata.model.Raw;
import org.ethelred.bsdata.roster.RosterModel;
import org.ethelred.bsdata.roster.Selection;
import org.jspecify.annotations.Nullable;

/**
 * What a roster looks like once modifiers have been applied: the cost of a selection and the characteristics of its
 * profiles. Values are worked out on demand and remembered, so a cost that a condition reads is computed once, and a
 * modifier whose condition depends on itself is caught (the base value is used and a diagnostic says so).
 *
 * <p>A view is a snapshot: build a new one after the roster changes.
 *
 * <p>Covered: modifiers on a selection's entry or link that change a cost, a constraint's value or {@code hidden}, and
 * modifiers on a profile that change one of its characteristics, with conditions, condition groups, local groups and
 * repeats. Not yet: {@code affects}
 * (M7) and other fields such as {@code name}. Those are reported in
 * {@link #diagnostics()}.
 */
public final class View {
    private record Located(Modifier modifier, List<Conditions> inherited, boolean inGroupWithRepeats) {}

    private final RosterModel model;
    private @Nullable Evaluator evaluator;
    private final Set<String> diagnostics = new LinkedHashSet<>();
    private final Map<String, Double> costCache = new HashMap<>();
    private final Set<String> costInProgress = new HashSet<>();

    public View(RosterModel model) {
        this.model = model;
    }

    /** The evaluator the view uses (it reads costs through the view), so its diagnostics can be read too. */
    public Evaluator evaluator() {
        Evaluator e = evaluator;
        if (e == null) {
            e = new Evaluator(model, this::unitCost);
            evaluator = e;
        }
        return e;
    }

    /** Everything that could not be applied so far, each once, in the order met (including the evaluator's). */
    public List<String> diagnostics() {
        List<String> out = new ArrayList<>(diagnostics);
        for (String d : evaluator().diagnostics()) {
            if (!out.contains(d)) {
                out.add(d);
            }
        }
        return out;
    }

    // ---- costs

    /** The cost of one of a selection, after modifiers, for a cost type id. */
    public double unitCost(Selection s, String costTypeId) {
        String key = s.id() + "|" + costTypeId;
        Double cached = costCache.get(key);
        if (cached != null) {
            return cached;
        }
        double base = s.unitCosts().getOrDefault(costTypeId, 0.0);
        if (!costInProgress.add(key)) {
            diagnostics.add(
                    "a cost modifier's condition depends on the cost it changes (" + s.name() + "); base value used");
            return base;
        }
        try {
            List<String> local = new ArrayList<>();
            List<ModifierEngine.Applied> applied = applicable(s, costModifiers(s, costTypeId));
            double value = ModifierEngine.applyNumber(base, applied, local);
            diagnostics.addAll(local);
            costCache.put(key, value);
            return value;
        } finally {
            costInProgress.remove(key);
        }
    }

    /** The cost types a selection has a cost in: those of its entry and link, and any a modifier on it changes. */
    private Set<String> costTypes(Selection s) {
        Set<String> out = new LinkedHashSet<>(s.unitCosts().keySet());
        for (Located l : allModifiers(s)) {
            String field = l.modifier().field();
            if (model.data().costType(s.file(), field) != null) {
                out.add(field);
            }
        }
        return out;
    }

    /** A selection's own cost: its cost per one, times its total number. Zero costs are left out. */
    public Map<String, Double> costs(Selection s) {
        Map<String, Double> out = new LinkedHashMap<>();
        double total = s.total();
        for (String type : costTypes(s)) {
            double v = unitCost(s, type) * total;
            if (v != 0) {
                out.put(type, v);
            }
        }
        return out;
    }

    /** The roster's cost for each cost type: the plain sum of every selection's own cost. */
    public Map<String, Double> totalCosts() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Selection s : model.selections()) {
            costs(s).forEach((type, v) -> out.merge(type, v, Double::sum));
        }
        return out;
    }

    private List<Located> costModifiers(Selection s, String costTypeId) {
        List<Located> out = new ArrayList<>();
        for (Located l : allModifiers(s)) {
            if (l.modifier().field().equals(costTypeId)) {
                out.add(l);
            }
        }
        return out;
    }

    // ---- constraints and hidden entries

    /**
     * A constraint's limit for a selection after modifiers: those on the selection's entry or link whose field is the
     * constraint's id, evaluated from the owner (observed in New Recruit).
     */
    public double constraintValue(Selection owner, Constraint c) {
        if (c.id().isEmpty()) {
            return c.value();
        }
        List<Located> mine = new ArrayList<>();
        for (Located l : allModifiers(owner)) {
            if (l.modifier().field().equals(c.id())) {
                mine.add(l);
            }
        }
        if (mine.isEmpty()) {
            return c.value();
        }
        List<String> local = new ArrayList<>();
        double value = ModifierEngine.applyNumber(c.value(), applicable(owner, mine), local);
        diagnostics.addAll(local);
        return value;
    }

    /** Whether the selection's entry is hidden: its own flag (or its link's), then any {@code hidden} modifiers. */
    public boolean hidden(Selection s) {
        var link = s.link();
        boolean base = s.entry().hidden() || link != null && link.hidden();
        List<Located> mine = new ArrayList<>();
        for (Located l : allModifiers(s)) {
            if (l.modifier().field().equals("hidden")) {
                mine.add(l);
            }
        }
        if (mine.isEmpty()) {
            return base;
        }
        List<String> local = new ArrayList<>();
        String value = ModifierEngine.applyText(String.valueOf(base), applicable(s, mine), local);
        diagnostics.addAll(local);
        return value.equals("true");
    }

    // ---- profiles

    /** The characteristics of a profile of a selection after modifiers, by characteristic type id, in order. */
    public Map<String, String> characteristics(Selection s, Profile p) {
        if (hasAffects(s.rootEntry())) {
            diagnostics.add("affects: not supported until M7 (a modifier in unit "
                    + s.rootEntry().name() + " may change this profile)");
        }
        Map<String, String> out = new LinkedHashMap<>();
        List<Located> mods = collect(p.raw());
        for (Characteristic c : p.characteristics()) {
            List<Located> mine = new ArrayList<>();
            for (Located l : mods) {
                if (l.modifier().field().equals(c.typeId())) {
                    mine.add(l);
                }
            }
            List<String> local = new ArrayList<>();
            String value = ModifierEngine.applyText(c.text(), applicable(s, mine), local);
            diagnostics.addAll(local);
            out.put(c.typeId(), value);
        }
        return out;
    }

    private final Map<Selection, Boolean> affectsCache = new HashMap<>();

    /** Whether any selection in the unit has a modifier that acts on other nodes through {@code affects}. */
    private boolean hasAffects(Selection root) {
        Boolean cached = affectsCache.get(root);
        if (cached != null) {
            return cached;
        }
        boolean found = false;
        List<Selection> stack = new ArrayList<>(List.of(root));
        while (!stack.isEmpty() && !found) {
            Selection x = stack.remove(stack.size() - 1);
            for (Located l : allModifiers(x)) {
                String a = l.modifier().affects();
                found |= a != null && !a.isEmpty();
            }
            stack.addAll(x.children());
        }
        affectsCache.put(root, found);
        return found;
    }

    // ---- collecting and gating modifiers

    private List<Located> allModifiers(Selection s) {
        List<Located> out = new ArrayList<>(collect(s.entry().body().raw()));
        var link = s.link();
        if (link != null) {
            out.addAll(collect(link.body().raw()));
        }
        return out;
    }

    private static List<Located> collect(Raw raw) {
        List<Located> out = new ArrayList<>();
        if (raw.isEmpty()) {
            return out;
        }
        for (Modifier m : Modifier.of(raw.modifiers())) {
            out.add(new Located(m, List.of(), false));
        }
        flatten(Modifier.Group.of(raw.modifierGroups()), List.of(), false, out);
        return out;
    }

    private static void flatten(
            List<Modifier.Group> groups, List<Conditions> inherited, boolean repeats, List<Located> out) {
        for (Modifier.Group g : groups) {
            List<Conditions> conds = new ArrayList<>(inherited);
            if (!g.conditions().isEmpty()) {
                conds.add(g.conditions());
            }
            boolean r = repeats || !g.repeats().isEmpty();
            for (Modifier m : g.modifiers()) {
                out.add(new Located(m, conds, r));
            }
            flatten(g.groups(), conds, r, out);
        }
    }

    /** The modifiers whose conditions hold at {@code here}, with how many times each applies. */
    private List<ModifierEngine.Applied> applicable(Selection here, List<Located> mods) {
        List<ModifierEngine.Applied> out = new ArrayList<>();
        for (Located l : mods) {
            Modifier m = l.modifier();
            String affects = m.affects();
            if (affects != null && !affects.isEmpty()) {
                diagnostics.add("affects: not supported until M7");
                continue;
            }
            if (l.inGroupWithRepeats()) {
                diagnostics.add("repeats on a modifier group: not supported");
            }
            if (!holds(here, l)) {
                continue;
            }
            double times = times(here, m);
            out.add(new ModifierEngine.Applied(m, times));
        }
        return out;
    }

    /** All conditions must be true. A condition whose scope resolves to nothing compares a count of 0. */
    private boolean holds(Selection here, Located l) {
        for (Conditions c : l.inherited()) {
            if (evaluator().eval(here, c) != Evaluator.Result.TRUE) {
                return false;
            }
        }
        return evaluator().eval(here, l.modifier().conditions()) == Evaluator.Result.TRUE;
    }

    /** One application, or the sum of the repeats' counts; a repeat that counts nothing disables the modifier. */
    private double times(Selection here, Modifier m) {
        if (m.repeats().isEmpty()) {
            return 1;
        }
        double k = 0;
        for (Repeat r : m.repeats()) {
            Double t = evaluator().times(here, r);
            k += t == null ? 0 : t;
        }
        return k;
    }
}
