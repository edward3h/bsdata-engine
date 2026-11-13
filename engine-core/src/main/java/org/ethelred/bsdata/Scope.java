/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Which files see which: the part of scoped lookup that depends only on how the files link to each other (see
 * {@code docs/engine-design.md}, section 11). A catalogue sees, in order of preference, itself, the game system, then
 * the catalogues it links, followed transitively (depth first, in link order, so the first link wins). The game system
 * sees only itself. It holds a few strings per file, so it can be built while files are loaded one at a time.
 */
public final class Scope {
    private record FileInfo(
            String name, boolean gameSystem, @Nullable String rootId, List<String> linkIds) {}

    private final Map<String, FileInfo> files = new HashMap<>();
    private final Map<String, String> fileByRootId = new HashMap<>();
    private final Map<String, List<String>> cache = new HashMap<>();
    private @Nullable String gameSystemFile;

    /** Registers a file. Call for every file before asking for a scope. */
    public void add(String file, boolean gameSystem, @Nullable String rootId, List<String> linkTargetIds) {
        files.put(file, new FileInfo(file, gameSystem, rootId, List.copyOf(linkTargetIds)));
        if (rootId != null) {
            fileByRootId.putIfAbsent(rootId, file);
        }
        if (gameSystem && gameSystemFile == null) {
            gameSystemFile = file;
        }
        cache.clear();
    }

    /** The files visible from {@code file}, most preferred first. Empty if the file is unknown. */
    public List<String> scope(String file) {
        List<String> cached = cache.get(file);
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
        cache.put(file, order);
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
}
