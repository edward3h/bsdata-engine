/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import java.util.ArrayList;
import java.util.List;
import org.ethelred.bsdata.DataNode;
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

    static Catalogue catalogue(String file, DataNode tree) {
        Reader top = Reader.of(tree);
        boolean gameSystem = false;
        DataNode rootNode = new DataNode.Null();
        if (tree instanceof DataNode.Obj o && !o.fields().isEmpty()) {
            var first = o.fields().entrySet().iterator().next();
            gameSystem = first.getKey().equals("gameSystem");
            rootNode = first.getValue();
        }
        Reader r = Reader.of(rootNode);
        List<Entry> root = new ArrayList<>();
        r.objs("selectionEntries").forEach(e -> root.add(entry(e, false)));
        r.objs("selectionEntryGroups").forEach(e -> root.add(entry(e, true)));
        List<Entry> shared = new ArrayList<>();
        r.objs("sharedSelectionEntries").forEach(e -> shared.add(entry(e, false)));
        r.objs("sharedSelectionEntryGroups").forEach(e -> shared.add(entry(e, true)));
        List<Link> links = new ArrayList<>();
        r.objs("entryLinks").forEach(l -> links.add(link(l)));
        return new Catalogue(
                file,
                r.str("id"),
                r.str("name"),
                gameSystem,
                r.bool("library"),
                (int) r.num("revision", 0),
                r.objs("catalogueLinks").stream()
                        .map(l -> new CatalogueLink(
                                l.str("id"), l.str("name"), l.str("targetId"), l.bool("importRootEntries")))
                        .toList(),
                r.objs("costTypes").stream()
                        .map(c -> new CostType(
                                c.str("id"), c.str("name"), c.num("defaultCostLimit", -1), c.bool("hidden")))
                        .toList(),
                r.objs("profileTypes").stream().map(Parse::profileType).toList(),
                r.objs("categoryEntries").stream()
                        .map(c -> new Category(c.str("id"), c.str("name"), c.bool("hidden"), raw(c)))
                        .toList(),
                r.objs("forceEntries").stream().map(Parse::forceEntry).toList(),
                root,
                shared,
                links,
                r.objs("sharedProfiles").stream().map(Parse::profile).toList(),
                r.objs("sharedRules").stream().map(Parse::rule).toList());
    }

    private static Raw raw(Reader r) {
        return new Raw(r.arr("constraints"), r.arr("modifiers"), r.arr("modifierGroups"), r.arr("associations"));
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
                        .toList());
    }

    private static Rule rule(Reader r) {
        return new Rule(r.str("id"), r.str("name"), r.str("description"), r.bool("hidden"));
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
