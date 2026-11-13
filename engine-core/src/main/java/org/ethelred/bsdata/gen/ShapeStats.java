/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.gen;

import java.util.Map;
import java.util.TreeMap;
import org.ethelred.bsdata.DataNode;
import org.ethelred.bsdata.MinimalJsonSource;

/** Counts of the kinds of object in a set of parsed files, for checking a generated dataset's shape. */
public final class ShapeStats {
    private final Map<String, Integer> counts = new TreeMap<>();
    private int objectsWithId;

    private ShapeStats() {}

    public static ShapeStats of(Map<String, DataNode> files) {
        ShapeStats s = new ShapeStats();
        files.values().forEach(t -> s.walk(t, ""));
        return s;
    }

    /** Objects found in arrays, by the name of the array (for example {@code modifiers} or {@code entryLinks}). */
    public Map<String, Integer> counts() {
        return counts;
    }

    public int count(String key) {
        return counts.getOrDefault(key, 0);
    }

    public int objectsWithId() {
        return objectsWithId;
    }

    private void walk(DataNode node, String key) {
        switch (node) {
            case DataNode.Obj o -> {
                if (!key.isEmpty()) {
                    counts.merge(key, 1, Integer::sum);
                }
                if (o.fields().get("id") instanceof DataNode.Str) {
                    objectsWithId++;
                }
                o.fields().forEach((k, v) -> walk(v, k));
            }
            case DataNode.Arr a -> a.items().forEach(i -> walk(i, key));
            case DataNode.Str s -> {}
            case DataNode.Num n -> {}
            case DataNode.Bool b -> {}
            case DataNode.Null n -> {}
        }
    }

    /** A 32-bit FNV-1a hash of the compact JSON of every file in order, as hex: equal on any platform for equal data. */
    public static String fingerprint(Map<String, DataNode> files) {
        MinimalJsonSource json = new MinimalJsonSource();
        int h = 0x811c9dc5;
        for (var e : files.entrySet()) {
            String text = e.getKey() + "\n" + json.write(e.getValue());
            for (int i = 0; i < text.length(); i++) {
                h ^= text.charAt(i);
                h *= 0x01000193;
            }
        }
        return Integer.toHexString(h);
    }
}
