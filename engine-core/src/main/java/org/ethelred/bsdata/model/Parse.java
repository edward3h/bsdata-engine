/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import java.util.ArrayList;
import java.util.List;
import org.ethelred.bsdata.DataNode;
import org.ethelred.bsdata.StreamingParser;
import org.ethelred.bsdata.model.Parts.CatalogueLink;
import org.ethelred.bsdata.model.Parts.Category;
import org.ethelred.bsdata.model.Parts.CategoryLink;
import org.ethelred.bsdata.model.Parts.Characteristic;
import org.ethelred.bsdata.model.Parts.CharacteristicType;
import org.ethelred.bsdata.model.Parts.Cost;
import org.ethelred.bsdata.model.Parts.CostType;
import org.ethelred.bsdata.model.Parts.ForceEntry;
import org.ethelred.bsdata.model.Parts.InfoLink;
import org.ethelred.bsdata.model.Parts.Profile;
import org.ethelred.bsdata.model.Parts.ProfileType;
import org.ethelred.bsdata.model.Parts.Rule;

/** Builds the typed model from parsed JSON. Unknown fields are ignored; see {@link Raw} for the deferred ones. */
final class Parse {
    private Parse() {}

    /** Collects the parts of one file as they arrive, whether from a finished tree or from a streaming parse. */
    static final class Assembler {
        private final String file;
        private final Pool pool;
        private final List<Entry> root = new ArrayList<>();
        private final List<Entry> shared = new ArrayList<>();
        private final List<Link> links = new ArrayList<>();
        private final List<CatalogueLink> catalogueLinks = new ArrayList<>();
        private final List<CostType> costTypes = new ArrayList<>();
        private final List<ProfileType> profileTypes = new ArrayList<>();
        private final List<Category> categories = new ArrayList<>();
        private final List<ForceEntry> forceEntries = new ArrayList<>();
        private final List<Profile> sharedProfiles = new ArrayList<>();
        private final List<Rule> sharedRules = new ArrayList<>();

        Assembler(String file, Pool pool) {
            this.file = file;
            this.pool = pool;
        }

        /** One element of one of the catalogue's top-level arrays. */
        void item(String key, DataNode node) {
            Reader r = Reader.of(node, pool);
            switch (key) {
                case "selectionEntries" -> root.add(entry(r, false));
                case "selectionEntryGroups" -> root.add(entry(r, true));
                case "sharedSelectionEntries" -> shared.add(entry(r, false));
                case "sharedSelectionEntryGroups" -> shared.add(entry(r, true));
                case "entryLinks" -> links.add(link(r));
                case "catalogueLinks" ->
                    catalogueLinks.add(new CatalogueLink(
                            r.str("id"), r.str("name"), r.str("targetId"), r.bool("importRootEntries")));
                case "costTypes" ->
                    costTypes.add(
                            new CostType(r.str("id"), r.str("name"), r.num("defaultCostLimit", -1), r.bool("hidden")));
                case "profileTypes" -> profileTypes.add(profileType(r));
                case "categoryEntries" ->
                    categories.add(new Category(r.str("id"), r.str("name"), r.bool("hidden"), raw(r)));
                case "forceEntries" -> forceEntries.add(forceEntry(r));
                case "sharedProfiles" -> sharedProfiles.add(profile(r));
                case "sharedRules" -> sharedRules.add(rule(r));
                default -> {}
            }
        }

        /**
         * @param tree the file's tree, with or without the top-level arrays (their elements arrive through {@link
         *     #item})
         */
        Catalogue finish(DataNode tree) {
            boolean gameSystem = false;
            DataNode rootNode = new DataNode.Null();
            if (tree instanceof DataNode.Obj o && !o.fields().isEmpty()) {
                var first = o.fields().entrySet().iterator().next();
                gameSystem = first.getKey().equals("gameSystem");
                rootNode = first.getValue();
            }
            Reader r = Reader.of(rootNode, pool);
            return new Catalogue(
                    file,
                    r.str("id"),
                    r.str("name"),
                    gameSystem,
                    r.bool("library"),
                    (int) r.num("revision", 0),
                    catalogueLinks,
                    costTypes,
                    profileTypes,
                    categories,
                    forceEntries,
                    root,
                    shared,
                    links,
                    sharedProfiles,
                    sharedRules);
        }
    }

    /** Types a whole parsed file. */
    static Catalogue catalogue(String file, DataNode tree, Pool pool) {
        Assembler a = new Assembler(file, pool);
        if (tree instanceof DataNode.Obj o && !o.fields().isEmpty()) {
            if (o.fields().values().iterator().next() instanceof DataNode.Obj root) {
                root.fields().forEach((key, value) -> {
                    if (value instanceof DataNode.Arr arr) {
                        arr.items().forEach(item -> a.item(key, item));
                    }
                });
            }
        }
        return a.finish(tree);
    }

    /** Types a file from its text, one top-level element at a time, so no tree of the whole file is built. */
    static Catalogue catalogue(String file, String text, Pool pool) {
        Assembler a = new Assembler(file, pool);
        DataNode partial = StreamingParser.parse(text, 3, a::item);
        return a.finish(partial);
    }

    private static Raw raw(Reader r) {
        return Raw.of(r.arr("constraints"), r.arr("modifiers"), r.arr("modifierGroups"), r.arr("associations"));
    }

    static Entry entry(Reader r, boolean group) {
        return new Entry(
                r.str("id"),
                r.str("name"),
                group,
                group ? null : r.optStr("type"),
                r.bool("hidden"),
                r.bool("collective"),
                r.optStr("defaultSelectionEntryId"),
                body(r));
    }

    static Link link(Reader r) {
        return new Link(
                r.str("id"),
                r.str("name"),
                r.str("targetId"),
                "selectionEntryGroup".equals(r.optStr("type")),
                r.bool("hidden"),
                body(r));
    }

    private static Body body(Reader r) {
        List<Child> children = new ArrayList<>();
        r.objs("selectionEntries").forEach(e -> children.add(entry(e, false)));
        r.objs("selectionEntryGroups").forEach(e -> children.add(entry(e, true)));
        r.objs("entryLinks").forEach(l -> children.add(link(l)));
        return new Body(
                r.objs("costs").stream()
                        .map(c -> new Cost(c.str("typeId"), c.str("name"), c.num("value", 0)))
                        .toList(),
                r.objs("profiles").stream().map(Parse::profile).toList(),
                r.objs("rules").stream().map(Parse::rule).toList(),
                r.objs("infoLinks").stream()
                        .map(l -> new InfoLink(
                                l.str("id"), l.str("name"), l.str("targetId"), l.str("type"), l.bool("hidden")))
                        .toList(),
                categoryLinks(r),
                children,
                raw(r));
    }

    private static List<CategoryLink> categoryLinks(Reader r) {
        return r.objs("categoryLinks").stream()
                .map(l -> new CategoryLink(
                        l.str("id"), l.str("name"), l.str("targetId"), l.bool("primary"), l.bool("hidden")))
                .toList();
    }

    private static Profile profile(Reader r) {
        return new Profile(
                r.str("id"),
                r.str("name"),
                r.str("typeId"),
                r.str("typeName"),
                r.bool("hidden"),
                r.objs("characteristics").stream()
                        .map(c -> new Characteristic(c.str("name"), c.str("typeId"), c.str("$text")))
                        .toList(),
                raw(r));
    }

    private static Rule rule(Reader r) {
        return new Rule(r.str("id"), r.str("name"), r.text("description"), r.bool("hidden"));
    }

    private static ProfileType profileType(Reader r) {
        return new ProfileType(
                r.str("id"),
                r.str("name"),
                r.objs("characteristicTypes").stream()
                        .map(c -> new CharacteristicType(c.str("id"), c.str("name")))
                        .toList());
    }

    private static ForceEntry forceEntry(Reader r) {
        return new ForceEntry(
                r.str("id"),
                r.str("name"),
                r.bool("hidden"),
                categoryLinks(r),
                r.objs("forceEntries").stream().map(Parse::forceEntry).toList(),
                raw(r));
    }
}
