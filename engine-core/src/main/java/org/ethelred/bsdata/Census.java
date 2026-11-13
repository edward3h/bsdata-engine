/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

import java.util.HashSet;
import java.util.Set;

/** M0 spike: counts objects, ids and unresolved target ids in a data tree. */
public final class Census {
    public record Result(int objects, int ids, int targets, int unresolved) {}

    private int objects;
    private final Set<String> ids = new HashSet<>();
    private final Set<String> targetIds = new HashSet<>();

    public static Result of(DataNode root) {
        Census c = new Census();
        c.walk(root);
        int unresolved = 0;
        for (String t : c.targetIds) {
            if (!c.ids.contains(t)) {
                unresolved++;
            }
        }
        return new Result(c.objects, c.ids.size(), c.targetIds.size(), unresolved);
    }

    private void walk(DataNode node) {
        switch (node) {
            case DataNode.Obj o -> {
                objects++;
                o.fields().forEach((k, v) -> {
                    if (v instanceof DataNode.Str s) {
                        if (k.equals("id")) {
                            ids.add(s.value());
                        } else if (k.equals("targetId")) {
                            targetIds.add(s.value());
                        }
                    }
                    walk(v);
                });
            }
            case DataNode.Arr a -> a.items().forEach(this::walk);
            case DataNode.Str s -> {}
            case DataNode.Num n -> {}
            case DataNode.Bool b -> {}
            case DataNode.Null n -> {}
        }
    }
}
