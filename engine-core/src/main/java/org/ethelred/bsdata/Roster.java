/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A roster in New Recruit's JSON export format (see {@code docs/roster-format.md}). Reading keeps every field it does
 * not model in {@code extra}, so that writing the roster back reproduces the file. Empty lists are not written, since
 * NR omits them.
 *
 * <p>{@code profiles} and {@code rules} on forces and selections are NR's computed output (values after modifiers).
 * They are kept as raw nodes: the engine will compute them, and compare, rather than read them.
 */
public record Roster(
        String id,
        String name,
        double battleScribeVersion,
        String generatedBy,
        String gameSystemId,
        String gameSystemName,
        double gameSystemRevision,
        String xmlns,
        List<Cost> costs,
        List<Force> forces,
        Map<String, DataNode> extra) {

    public record Cost(String name, String typeId, double value, Map<String, DataNode> extra) {}

    /**
     * Attachment between two selections, stored on both: on the source as {@code type "outgoing"} with {@code to}, on
     * the target as {@code "incoming"} with {@code from} and an {@code amount}. {@code selectionId} holds whichever of
     * {@code to} or {@code from} is present.
     */
    public record Association(
            String type,
            String selectionId,
            String associationId,
            String name,
            String action,
            @Nullable Double amount,
            Map<String, DataNode> extra) {}

    public record Category(String id, String entryId, String name, boolean primary, Map<String, DataNode> extra) {}

    public record Force(
            String id,
            String name,
            String entryId,
            String catalogueId,
            double catalogueRevision,
            String catalogueName,
            List<Category> categories,
            List<Selection> selections,
            List<DataNode> profiles,
            List<DataNode> rules,
            List<Force> forces,
            Map<String, DataNode> extra) {}

    /**
     * @param entryId the path of ids from the catalogue to the entry, separated by {@code ::} (the id of an entry
     *     link, then of its target, and so on)
     * @param entryGroupId the path of the group this selection was chosen from, if any
     * @param from where the selection came from: {@code entry} or {@code group}
     * @param group the names of the groups, separated by {@code ::}, if {@code from} is {@code group}
     */
    public record Selection(
            String id,
            String name,
            String entryId,
            @Nullable String entryGroupId,
            double number,
            String type,
            String from,
            @Nullable String group,
            List<Cost> costs,
            List<Category> categories,
            List<DataNode> profiles,
            List<DataNode> rules,
            List<Association> associations,
            List<Association> incomingAssociations,
            List<Selection> selections,
            Map<String, DataNode> extra) {}

    // ---- reading

    /** @param tree a parsed roster file, such as {@code {"roster": {...}}} */
    public static Roster read(DataNode tree) {
        Fields top = Fields.of(tree);
        Fields r = Fields.of(top.node("roster"));
        return new Roster(
                r.str("id"),
                r.str("name"),
                r.num("battleScribeVersion"),
                r.str("generatedBy"),
                r.str("gameSystemId"),
                r.str("gameSystemName"),
                r.num("gameSystemRevision"),
                r.str("xmlns"),
                costs(r),
                list(r, "forces", Roster::force),
                r.rest());
    }

    private static <T> List<T> list(Fields f, String key, java.util.function.Function<Fields, T> read) {
        List<T> out = new ArrayList<>();
        for (DataNode n : f.array(key)) {
            out.add(read.apply(Fields.of(n)));
        }
        return out;
    }

    private static List<Cost> costs(Fields f) {
        return list(f, "costs", c -> new Cost(c.str("name"), c.str("typeId"), c.num("value"), c.rest()));
    }

    private static List<Category> categories(Fields f) {
        return list(
                f,
                "categories",
                c -> new Category(c.str("id"), c.str("entryId"), c.str("name"), c.bool("primary"), c.rest()));
    }

    private static Force force(Fields f) {
        return new Force(
                f.str("id"),
                f.str("name"),
                f.str("entryId"),
                f.str("catalogueId"),
                f.num("catalogueRevision"),
                f.str("catalogueName"),
                categories(f),
                list(f, "selections", Roster::selection),
                f.array("profiles"),
                f.array("rules"),
                list(f, "forces", Roster::force),
                f.rest());
    }

    private static Selection selection(Fields f) {
        return new Selection(
                f.str("id"),
                f.str("name"),
                f.str("entryId"),
                f.optStr("entryGroupId"),
                f.num("number"),
                f.str("type"),
                f.str("from"),
                f.optStr("group"),
                costs(f),
                categories(f),
                f.array("profiles"),
                f.array("rules"),
                list(f, "associations", Roster::association),
                list(f, "incomingAssociations", Roster::association),
                list(f, "selections", Roster::selection),
                f.rest());
    }

    private static Association association(Fields f) {
        String type = f.str("type");
        String selectionId = type.equals("incoming") ? f.str("from") : f.str("to");
        return new Association(
                type,
                selectionId,
                f.str("associationId"),
                f.str("name"),
                f.str("action"),
                f.optNum("amount"),
                f.rest());
    }

    // ---- writing

    /** The roster as a tree, ready for a {@link JsonSink}. */
    public DataNode toNode() {
        Map<String, DataNode> r = new LinkedHashMap<>();
        if (!costs.isEmpty()) {
            r.put("costs", array(costs.stream().map(Roster::node).toList()));
        }
        r.put("forces", array(forces.stream().map(Roster::node).toList()));
        r.put("id", new DataNode.Str(id));
        r.put("name", new DataNode.Str(name));
        r.put("battleScribeVersion", new DataNode.Num(battleScribeVersion));
        r.put("generatedBy", new DataNode.Str(generatedBy));
        r.put("gameSystemId", new DataNode.Str(gameSystemId));
        r.put("gameSystemName", new DataNode.Str(gameSystemName));
        r.put("gameSystemRevision", new DataNode.Num(gameSystemRevision));
        r.put("xmlns", new DataNode.Str(xmlns));
        r.putAll(extra);
        return new DataNode.Obj(Map.of("roster", new DataNode.Obj(r)));
    }

    private static DataNode array(List<DataNode> items) {
        return new DataNode.Arr(items);
    }

    private static DataNode node(Cost c) {
        Map<String, DataNode> m = new LinkedHashMap<>();
        m.put("name", new DataNode.Str(c.name()));
        m.put("typeId", new DataNode.Str(c.typeId()));
        m.put("value", new DataNode.Num(c.value()));
        m.putAll(c.extra());
        return new DataNode.Obj(m);
    }

    private static DataNode node(Association a) {
        Map<String, DataNode> m = new LinkedHashMap<>();
        m.put("type", new DataNode.Str(a.type()));
        m.put(a.type().equals("incoming") ? "from" : "to", new DataNode.Str(a.selectionId()));
        if (a.amount() != null) {
            m.put("amount", new DataNode.Num(a.amount()));
        }
        m.put("associationId", new DataNode.Str(a.associationId()));
        m.put("name", new DataNode.Str(a.name()));
        m.put("action", new DataNode.Str(a.action()));
        m.putAll(a.extra());
        return new DataNode.Obj(m);
    }

    private static DataNode node(Category c) {
        Map<String, DataNode> m = new LinkedHashMap<>();
        m.put("id", new DataNode.Str(c.id()));
        m.put("entryId", new DataNode.Str(c.entryId()));
        m.put("name", new DataNode.Str(c.name()));
        m.put("primary", new DataNode.Bool(c.primary()));
        m.putAll(c.extra());
        return new DataNode.Obj(m);
    }

    private static DataNode node(Force f) {
        Map<String, DataNode> m = new LinkedHashMap<>();
        putNonEmpty(m, "profiles", f.profiles());
        putNonEmpty(m, "rules", f.rules());
        putNonEmpty(m, "selections", f.selections().stream().map(Roster::node).toList());
        putNonEmpty(m, "categories", f.categories().stream().map(Roster::node).toList());
        putNonEmpty(m, "forces", f.forces().stream().map(Roster::node).toList());
        m.put("id", new DataNode.Str(f.id()));
        m.put("name", new DataNode.Str(f.name()));
        m.put("entryId", new DataNode.Str(f.entryId()));
        m.put("catalogueId", new DataNode.Str(f.catalogueId()));
        m.put("catalogueRevision", new DataNode.Num(f.catalogueRevision()));
        m.put("catalogueName", new DataNode.Str(f.catalogueName()));
        m.putAll(f.extra());
        return new DataNode.Obj(m);
    }

    private static DataNode node(Selection s) {
        Map<String, DataNode> m = new LinkedHashMap<>();
        putNonEmpty(m, "profiles", s.profiles());
        putNonEmpty(m, "rules", s.rules());
        putNonEmpty(
                m, "associations", s.associations().stream().map(Roster::node).toList());
        putNonEmpty(
                m,
                "incomingAssociations",
                s.incomingAssociations().stream().map(Roster::node).toList());
        putNonEmpty(m, "selections", s.selections().stream().map(Roster::node).toList());
        putNonEmpty(m, "costs", s.costs().stream().map(Roster::node).toList());
        putNonEmpty(m, "categories", s.categories().stream().map(Roster::node).toList());
        m.put("id", new DataNode.Str(s.id()));
        m.put("name", new DataNode.Str(s.name()));
        m.put("entryId", new DataNode.Str(s.entryId()));
        if (s.entryGroupId() != null) {
            m.put("entryGroupId", new DataNode.Str(s.entryGroupId()));
        }
        m.put("number", new DataNode.Num(s.number()));
        m.put("type", new DataNode.Str(s.type()));
        m.put("from", new DataNode.Str(s.from()));
        if (s.group() != null) {
            m.put("group", new DataNode.Str(s.group()));
        }
        m.putAll(s.extra());
        return new DataNode.Obj(m);
    }

    private static void putNonEmpty(Map<String, DataNode> m, String key, List<DataNode> items) {
        if (!items.isEmpty()) {
            m.put(key, new DataNode.Arr(items));
        }
    }

    /** Typed access to the fields of one object, remembering which were read. */
    private static final class Fields {
        private final Map<String, DataNode> fields;
        private final Map<String, DataNode> unread;

        private Fields(Map<String, DataNode> fields) {
            this.fields = fields;
            this.unread = new LinkedHashMap<>(fields);
        }

        static Fields of(DataNode node) {
            if (node instanceof DataNode.Obj o) {
                return new Fields(o.fields());
            }
            throw new IllegalArgumentException("expected an object");
        }

        DataNode node(String key) {
            DataNode n = unread.remove(key);
            if (n == null) {
                throw new IllegalArgumentException("missing field: " + key);
            }
            return n;
        }

        String str(String key) {
            if (node(key) instanceof DataNode.Str s) {
                return s.value();
            }
            throw new IllegalArgumentException("not a string: " + key);
        }

        @Nullable
        String optStr(String key) {
            DataNode n = unread.remove(key);
            if (n == null) {
                return null;
            }
            if (n instanceof DataNode.Str s) {
                return s.value();
            }
            throw new IllegalArgumentException("not a string: " + key);
        }

        double num(String key) {
            if (node(key) instanceof DataNode.Num n) {
                return n.value();
            }
            throw new IllegalArgumentException("not a number: " + key);
        }

        @Nullable
        Double optNum(String key) {
            DataNode n = unread.remove(key);
            if (n == null) {
                return null;
            }
            if (n instanceof DataNode.Num num) {
                return num.value();
            }
            throw new IllegalArgumentException("not a number: " + key);
        }

        boolean bool(String key) {
            if (node(key) instanceof DataNode.Bool b) {
                return b.value();
            }
            throw new IllegalArgumentException("not a boolean: " + key);
        }

        List<DataNode> array(String key) {
            DataNode n = unread.remove(key);
            if (n == null) {
                return List.of();
            }
            if (n instanceof DataNode.Arr a) {
                return a.items();
            }
            throw new IllegalArgumentException("not an array: " + key);
        }

        Map<String, DataNode> rest() {
            return new LinkedHashMap<>(unread);
        }
    }
}
