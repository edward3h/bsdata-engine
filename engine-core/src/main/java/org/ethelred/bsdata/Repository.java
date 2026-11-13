/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The parsed files of one game system repository, with scoped id lookup as observed in New Recruit (see
 * {@code docs/engine-design.md}, section 11). A catalogue sees, in order of preference: its own objects, the game
 * system's, then the catalogues it links, followed transitively (depth first, in link order, so the first link wins).
 * The game system sees only itself.
 */
public final class Repository {
    private record FileInfo(
            String name, boolean gameSystem, @Nullable String rootId, List<String> linkIds, Set<String> ids) {}

    private final Map<String, FileInfo> files = new TreeMap<>();
    private final Map<String, String> fileByRootId = new HashMap<>();
    private final Map<String, Set<String>> filesById = new HashMap<>();
    private @Nullable String gameSystemFile;
    private final Map<String, List<String>> scopeCache = new HashMap<>();

    /** @param trees file name to parsed tree; iteration order does not affect the result */
    public Repository(Map<String, DataNode> trees) {
        new TreeMap<>(trees).forEach((name, tree) -> {
            FileInfo info = describe(name, tree);
            files.put(name, info);
            if (info.rootId() != null) {
                fileByRootId.putIfAbsent(info.rootId(), name);
            }
            if (info.gameSystem() && gameSystemFile == null) {
                gameSystemFile = name;
            }
            for (String id : info.ids()) {
                filesById.computeIfAbsent(id, k -> new HashSet<>()).add(name);
            }
        });
    }

    private static FileInfo describe(String name, DataNode tree) {
        boolean gameSystem = false;
        String rootId = null;
        List<String> links = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        if (tree instanceof DataNode.Obj top && !top.fields().isEmpty()) {
            var first = top.fields().entrySet().iterator().next();
            gameSystem = first.getKey().equals("gameSystem");
            if (first.getValue() instanceof DataNode.Obj root) {
                if (root.fields().get("id") instanceof DataNode.Str s) {
                    rootId = s.value();
                }
                if (root.fields().get("catalogueLinks") instanceof DataNode.Arr arr) {
                    for (DataNode item : arr.items()) {
                        if (item instanceof DataNode.Obj link
                                && link.fields().get("targetId") instanceof DataNode.Str t) {
                            links.add(t.value());
                        }
                    }
                }
            }
        }
        collectIds(tree, ids);
        return new FileInfo(name, gameSystem, rootId, links, ids);
    }

    private static void collectIds(DataNode node, Set<String> ids) {
        switch (node) {
            case DataNode.Obj o ->
                o.fields().forEach((k, v) -> {
                    if (k.equals("id") && v instanceof DataNode.Str s) {
                        ids.add(s.value());
                    }
                    collectIds(v, ids);
                });
            case DataNode.Arr a -> a.items().forEach(i -> collectIds(i, ids));
            case DataNode.Str s -> {}
            case DataNode.Num n -> {}
            case DataNode.Bool b -> {}
            case DataNode.Null n -> {}
        }
    }

    public int fileCount() {
        return files.size();
    }

    /** Number of distinct ids, and how many of them are defined in more than one file. */
    public int idCount() {
        return filesById.size();
    }

    public int idsInSeveralFiles() {
        int n = 0;
        for (Set<String> f : filesById.values()) {
            if (f.size() > 1) {
                n++;
            }
        }
        return n;
    }

    /** True if some file defines the id, whether or not it is visible from anywhere. */
    public boolean isDefined(String id) {
        return filesById.containsKey(id);
    }

    /** The files visible from {@code file}, most preferred first. Empty if the file is unknown. */
    public List<String> scope(String file) {
        List<String> cached = scopeCache.get(file);
        if (cached != null) {
            return cached;
        }
        List<String> order = new ArrayList<>();
        FileInfo self = files.get(file);
        if (self != null) {
            order.add(file);
            String gs = gameSystemFile;
            if (gs != null && !gs.equals(file) && !self.gameSystem()) {
                order.add(gs);
            }
            if (!self.gameSystem()) {
                follow(self, order);
            }
        }
        scopeCache.put(file, order);
        return order;
    }

    private void follow(FileInfo from, List<String> order) {
        for (String targetId : from.linkIds()) {
            String target = fileByRootId.get(targetId);
            if (target != null && !order.contains(target)) {
                order.add(target);
                FileInfo next = files.get(target);
                if (next != null) {
                    follow(next, order);
                }
            }
        }
    }

    /** The file whose object an id means when used in {@code file}, or null if it is not visible there. */
    public @Nullable String resolve(String file, String id) {
        Set<String> definers = filesById.get(id);
        if (definers == null) {
            return null;
        }
        for (String f : scope(file)) {
            if (definers.contains(f)) {
                return f;
            }
        }
        return null;
    }

    /** File names in sorted order. */
    public List<String> fileNames() {
        return new ArrayList<>(files.keySet());
    }
}
