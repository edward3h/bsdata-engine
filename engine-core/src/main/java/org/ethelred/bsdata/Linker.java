/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Checks id references across all the files of a repository. A reference is any string field whose name ends in
 * {@code Id} (other than {@code id} itself). It resolves if some object in the repository has that {@code id}, or if
 * it is one of the keywords BattleScribe allows in place of an id.
 */
public final class Linker {
    /** Values allowed where an id is expected, such as {@code childId: "model"}. */
    static final Set<String> KEYWORDS = Set.of(
            "any",
            "model",
            "unit",
            "upgrade",
            "model-or-unit",
            "none",
            "self",
            "parent",
            "ancestor",
            "roster",
            "force",
            "primary-catalogue",
            "primary-category");

    public record Stats(int total, int resolved, int keyword, int unresolved) {}

    /** One distinct unresolved reference: the field, the array it sits in, its value, and how often. */
    public record Unresolved(String field, String container, String value, int count) {}

    public record Report(
            int files,
            int objects,
            int ids,
            int idsInSeveralFiles,
            Map<String, Stats> byField,
            List<Unresolved> unresolved) {

        /** Deterministic text: whole numbers only, fixed order. */
        public String format(int maxUnresolved) {
            StringBuilder sb = new StringBuilder();
            sb.append("files=").append(files);
            sb.append(" objects=").append(objects);
            sb.append(" ids=").append(ids);
            sb.append(" idsInSeveralFiles=").append(idsInSeveralFiles).append('\n');
            byField.forEach((k, s) -> sb.append(k)
                    .append(" total=")
                    .append(s.total())
                    .append(" resolved=")
                    .append(s.resolved())
                    .append(" keyword=")
                    .append(s.keyword())
                    .append(" unresolved=")
                    .append(s.unresolved())
                    .append('\n'));
            int n = Math.min(maxUnresolved, unresolved.size());
            sb.append("unresolved groups=").append(unresolved.size()).append('\n');
            for (int i = 0; i < n; i++) {
                Unresolved u = unresolved.get(i);
                sb.append("  ")
                        .append(u.field())
                        .append(" in ")
                        .append(u.container())
                        .append(" ")
                        .append(u.value())
                        .append(" x")
                        .append(u.count())
                        .append('\n');
            }
            return sb.toString();
        }
    }

    private final Map<String, String> idFile = new HashMap<>();
    private final Set<String> severalFiles = new HashSet<>();
    private int objects;
    private final Map<String, int[]> counts = new TreeMap<>();
    private final Map<String, Integer> groups = new HashMap<>();

    private Linker() {}

    /** @param files file name to parsed tree; iteration order does not affect the result */
    public static Report check(Map<String, DataNode> files) {
        Linker l = new Linker();
        files.forEach((name, tree) -> l.collectIds(name, tree));
        files.forEach((name, tree) -> l.checkRefs(tree, ""));
        Map<String, Stats> byField = new TreeMap<>();
        l.counts.forEach((k, c) -> byField.put(k, new Stats(c[0] + c[1] + c[2], c[0], c[1], c[2])));
        List<Unresolved> list = new ArrayList<>();
        l.groups.forEach((g, n) -> {
            String[] p = g.split("\u0000", 3);
            list.add(new Unresolved(p[0], p[1], p[2], n));
        });
        list.sort((a, b) -> {
            int c = Integer.compare(b.count(), a.count());
            if (c != 0) {
                return c;
            }
            c = a.field().compareTo(b.field());
            if (c != 0) {
                return c;
            }
            c = a.container().compareTo(b.container());
            return c != 0 ? c : a.value().compareTo(b.value());
        });
        return new Report(files.size(), l.objects, l.idFile.size(), l.severalFiles.size(), byField, list);
    }

    private void collectIds(String file, DataNode node) {
        switch (node) {
            case DataNode.Obj o -> {
                objects++;
                o.fields().forEach((k, v) -> {
                    if (k.equals("id") && v instanceof DataNode.Str s) {
                        String first = idFile.putIfAbsent(s.value(), file);
                        if (first != null && !first.equals(file)) {
                            severalFiles.add(s.value());
                        }
                    }
                    collectIds(file, v);
                });
            }
            case DataNode.Arr a -> a.items().forEach(i -> collectIds(file, i));
            case DataNode.Str s -> {}
            case DataNode.Num n -> {}
            case DataNode.Bool b -> {}
            case DataNode.Null n -> {}
        }
    }

    private void checkRefs(DataNode node, String container) {
        switch (node) {
            case DataNode.Obj o ->
                o.fields().forEach((k, v) -> {
                    if (v instanceof DataNode.Str s && k.endsWith("Id") && !k.equals("id")) {
                        int[] c = counts.computeIfAbsent(k, x -> new int[3]);
                        if (idFile.containsKey(s.value())) {
                            c[0]++;
                        } else if (KEYWORDS.contains(s.value())) {
                            c[1]++;
                        } else {
                            c[2]++;
                            groups.merge(k + "\u0000" + container + "\u0000" + s.value(), 1, Integer::sum);
                        }
                    }
                    checkRefs(v, k);
                });
            case DataNode.Arr a -> a.items().forEach(i -> checkRefs(i, container));
            case DataNode.Str s -> {}
            case DataNode.Num n -> {}
            case DataNode.Bool b -> {}
            case DataNode.Null n -> {}
        }
    }
}
