/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Checks id references across all the files of a repository. A reference is any string field whose name ends in
 * {@code Id} (other than {@code id} itself). It is a <em>keyword</em> if it is one of the values BattleScribe allows
 * in place of an id; otherwise it is <em>resolved</em>, <em>out of scope</em> or <em>unresolved</em>.
 *
 * <p>Link targets ({@code targetId}) are looked up with the scoped lookup of {@link Repository}: out of scope means
 * some file defines the id but it is not visible from the referencing file, and New Recruit then refuses to load the
 * catalogue. Every other field (conditions' {@code childId}, cost {@code typeId}, {@code publicationId} and so on) is
 * matched at run time or ignored, and the real data uses them across catalogues that are not linked, so for them any
 * definition anywhere counts as resolved. For those fields only an undefined id is a problem.
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

    /** Fields whose ids are link targets and must be visible from the referencing file. */
    static final Set<String> SCOPED_FIELDS = Set.of("targetId");

    public record Stats(int total, int resolved, int keyword, int outOfScope, int unresolved) {}

    /** One distinct bad reference: the field, the array it sits in, its value, whether it is out of scope, and how often. */
    public record Problem(String field, String container, String value, boolean outOfScope, int count) {}

    public record Report(
            int files,
            int objects,
            int ids,
            int idsInSeveralFiles,
            Map<String, Stats> byField,
            List<Problem> problems) {

        /** Deterministic text: whole numbers only, fixed order. */
        public String format(int maxProblems) {
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
                    .append(" outOfScope=")
                    .append(s.outOfScope())
                    .append(" unresolved=")
                    .append(s.unresolved())
                    .append('\n'));
            int n = Math.min(maxProblems, problems.size());
            sb.append("problem groups=").append(problems.size()).append('\n');
            for (int i = 0; i < n; i++) {
                Problem u = problems.get(i);
                sb.append("  ")
                        .append(u.field())
                        .append(" in ")
                        .append(u.container())
                        .append(" ")
                        .append(u.value())
                        .append(u.outOfScope() ? " (out of scope)" : " (undefined)")
                        .append(" x")
                        .append(u.count())
                        .append('\n');
            }
            return sb.toString();
        }
    }

    private final Repository repo;
    private int objects;
    private final Map<String, int[]> counts = new TreeMap<>();
    private final Map<String, Integer> groups = new HashMap<>();

    private Linker(Repository repo) {
        this.repo = repo;
    }

    /** @param files file name to parsed tree; iteration order does not affect the result */
    public static Report check(Map<String, DataNode> files) {
        return check(files, new Repository(files));
    }

    public static Report check(Map<String, DataNode> files, Repository repo) {
        Linker l = new Linker(repo);
        new TreeMap<>(files).forEach((name, tree) -> l.checkRefs(name, tree, ""));
        Map<String, Stats> byField = new TreeMap<>();
        l.counts.forEach((k, c) -> byField.put(k, new Stats(c[0] + c[1] + c[2] + c[3], c[0], c[1], c[2], c[3])));
        List<Problem> list = new ArrayList<>();
        l.groups.forEach((g, n) -> {
            String[] p = g.split("\u0000", 4);
            list.add(new Problem(p[0], p[1], p[2], p[3].equals("s"), n));
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
            if (c != 0) {
                return c;
            }
            c = a.value().compareTo(b.value());
            return c != 0 ? c : Boolean.compare(a.outOfScope(), b.outOfScope());
        });
        return new Report(repo.fileCount(), l.objects, repo.idCount(), repo.idsInSeveralFiles(), byField, list);
    }

    private void checkRefs(String file, DataNode node, String container) {
        switch (node) {
            case DataNode.Obj o -> {
                objects++;
                o.fields().forEach((k, v) -> {
                    if (v instanceof DataNode.Str s && k.endsWith("Id") && !k.equals("id")) {
                        classify(file, k, container, s.value());
                    }
                    checkRefs(file, v, k);
                });
            }
            case DataNode.Arr a -> a.items().forEach(i -> checkRefs(file, i, container));
            case DataNode.Str s -> {}
            case DataNode.Num n -> {}
            case DataNode.Bool b -> {}
            case DataNode.Null n -> {}
        }
    }

    private void classify(String file, String field, String container, String value) {
        int[] c = counts.computeIfAbsent(field, x -> new int[4]);
        boolean found = SCOPED_FIELDS.contains(field) ? repo.resolve(file, value) != null : repo.isDefined(value);
        if (found) {
            c[0]++;
        } else if (KEYWORDS.contains(value)) {
            c[1]++;
        } else if (repo.isDefined(value)) {
            c[2]++;
            groups.merge(field + "\u0000" + container + "\u0000" + value + "\u0000s", 1, Integer::sum);
        } else {
            c[3]++;
            groups.merge(field + "\u0000" + container + "\u0000" + value + "\u0000u", 1, Integer::sum);
        }
    }
}
