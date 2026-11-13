/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.ethelred.bsdata.DataNode;
import org.ethelred.bsdata.Scope;
import org.ethelred.bsdata.model.Parts.CostType;
import org.ethelred.bsdata.model.Parts.ForceEntry;
import org.jspecify.annotations.Nullable;

/**
 * The typed, immutable game data of one repository: the game system and its catalogues, with links resolved by the
 * scoped lookup of {@link Scope} (see {@code docs/engine-design.md}, section 11).
 */
public final class GameData {
    /** A resolved entry together with the link that led to it, and the file where the entry lives. */
    public record Resolved(Entry entry, @Nullable Link link, String file) {}

    private final Scope scope;
    private final Map<String, Catalogue> byFile = new TreeMap<>();
    private final Map<String, Catalogue> byRootId = new HashMap<>();
    private final Map<String, Map<String, Entry>> entriesByFile = new HashMap<>();
    private final Map<String, Map<String, ForceEntry>> forceEntriesByFile = new HashMap<>();
    private final Map<String, Map<String, CostType>> costTypesByFile = new HashMap<>();

    private GameData(Scope scope) {
        this.scope = scope;
    }

    /** Adds files one at a time, so only one parsed tree needs to be alive at once. */
    public static final class Builder {
        private final Scope scope = new Scope();
        private final GameData data = new GameData(scope);
        private final Pool pool = new Pool();

        public Builder add(String file, DataNode tree) {
            Catalogue c = Parse.catalogue(file, tree, pool);
            scope.add(
                    file,
                    c.gameSystem(),
                    c.id(),
                    c.catalogueLinks().stream()
                            .map(Parts.CatalogueLink::targetId)
                            .toList());
            data.add(c);
            return this;
        }

        /** Types a file from its text without building a tree of the whole file. */
        public Builder addText(String file, String text) {
            return register(Parse.catalogue(file, text, pool));
        }

        private Builder register(Catalogue c) {
            scope.add(
                    c.file(),
                    c.gameSystem(),
                    c.id(),
                    c.catalogueLinks().stream()
                            .map(Parts.CatalogueLink::targetId)
                            .toList());
            data.add(c);
            return this;
        }

        public GameData build() {
            return data;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** @param files file name to parsed tree; all are held by the caller at once */
    public static GameData load(Map<String, DataNode> files) {
        Builder b = builder();
        new TreeMap<>(files).forEach(b::add);
        return b.build();
    }

    /**
     * Loads every file of a source one at a time and keeps only the typed result. Each file is typed element by
     * element as it is parsed, so the largest thing alive at once is one entry.
     */
    public static GameData load(TextSource source) {
        Builder b = builder();
        for (String name : source.names()) {
            b.addText(name, source.read(name));
        }
        return b.build();
    }

    private void add(Catalogue c) {
        byFile.put(c.file(), c);
        byRootId.putIfAbsent(c.id(), c);
        Map<String, Entry> entries = new HashMap<>();
        c.rootEntries().forEach(e -> indexEntry(e, entries));
        c.sharedEntries().forEach(e -> indexEntry(e, entries));
        c.rootLinks().forEach(l -> indexLinkChildren(l.body(), entries));
        entriesByFile.put(c.file(), entries);
        Map<String, ForceEntry> forces = new HashMap<>();
        c.forceEntries().forEach(f -> indexForce(f, forces));
        forceEntriesByFile.put(c.file(), forces);
        Map<String, CostType> costTypes = new HashMap<>();
        c.costTypes().forEach(t -> costTypes.putIfAbsent(t.id(), t));
        costTypesByFile.put(c.file(), costTypes);
    }

    private static void indexEntry(Entry e, Map<String, Entry> out) {
        out.putIfAbsent(e.id(), e);
        indexLinkChildren(e.body(), out);
    }

    private static void indexLinkChildren(Body body, Map<String, Entry> out) {
        for (Child c : body.children()) {
            switch (c) {
                case Entry e -> indexEntry(e, out);
                case Link l -> indexLinkChildren(l.body(), out);
            }
        }
    }

    private static void indexForce(ForceEntry f, Map<String, ForceEntry> out) {
        out.putIfAbsent(f.id(), f);
        f.children().forEach(c -> indexForce(c, out));
    }

    public List<Catalogue> catalogues() {
        return new ArrayList<>(byFile.values());
    }

    public @Nullable Catalogue catalogue(String file) {
        return byFile.get(file);
    }

    /** The catalogue (or game system) with this root id, if loaded. */
    public @Nullable Catalogue catalogueById(String rootId) {
        return byRootId.get(rootId);
    }

    public @Nullable Catalogue gameSystem() {
        for (Catalogue c : byFile.values()) {
            if (c.gameSystem()) {
                return c;
            }
        }
        return null;
    }

    /** The entry or group with this id as seen from {@code file}, and the file that defines it. */
    public @Nullable Resolved entry(String file, String id) {
        for (String f : scope.scope(file)) {
            Entry e = entriesByFile.getOrDefault(f, Map.of()).get(id);
            if (e != null) {
                return new Resolved(e, null, f);
            }
        }
        return null;
    }

    /** The target of a link written in {@code file}, with the link recorded. */
    public @Nullable Resolved target(String file, Link link) {
        Resolved r = entry(file, link.targetId());
        return r == null ? null : new Resolved(r.entry(), link, r.file());
    }

    /** The force entry with this id as seen from {@code file}. */
    public @Nullable ForceEntry forceEntry(String file, String id) {
        for (String f : scope.scope(file)) {
            ForceEntry e = forceEntriesByFile.getOrDefault(f, Map.of()).get(id);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    public @Nullable CostType costType(String file, String id) {
        for (String f : scope.scope(file)) {
            CostType t = costTypesByFile.getOrDefault(f, Map.of()).get(id);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    /**
     * Resolves the {@code ::}-separated entry id path of a roster selection (see {@code docs/roster-format.md}). The
     * first segment is a link or entry at the top of the catalogue; each later one is a link followed or the entry
     * reached, with plain entries and groups on the way left out of the path.
     */
    public @Nullable Resolved resolvePath(String catalogueFile, List<String> segments) {
        if (segments.isEmpty()) {
            return null;
        }
        Child current = null;
        String file = catalogueFile;
        for (String f : scope.scope(catalogueFile)) {
            Catalogue c = byFile.get(f);
            Child top = c == null ? null : findTop(c, segments.get(0));
            if (top != null) {
                current = top;
                file = f;
                break;
            }
        }
        if (current == null) {
            return null;
        }
        int next = 1;
        while (true) {
            Entry entry;
            Link link = null;
            if (current instanceof Link l) {
                Resolved target = target(file, l);
                if (target == null) {
                    return null;
                }
                entry = target.entry();
                file = target.file();
                link = l;
                // the link's target id may follow the link id in the path
                if (next < segments.size() && segments.get(next).equals(l.targetId())) {
                    next++;
                }
            } else {
                entry = (Entry) current;
            }
            if (next >= segments.size()) {
                return new Resolved(entry, link, file);
            }
            Child found = search(entry.body(), segments.get(next));
            if (found == null) {
                return null;
            }
            current = found;
            next++;
        }
    }

    private static @Nullable Child findTop(Catalogue c, String id) {
        for (Link l : c.rootLinks()) {
            if (l.id().equals(id)) {
                return l;
            }
        }
        for (Entry e : c.rootEntries()) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    private static @Nullable Child search(Body body, String id) {
        for (Child c : body.children()) {
            if (c.id().equals(id)) {
                return c;
            }
            if (c instanceof Entry e) {
                Child deeper = search(e.body(), id);
                if (deeper != null) {
                    return deeper;
                }
            }
        }
        return null;
    }
}
