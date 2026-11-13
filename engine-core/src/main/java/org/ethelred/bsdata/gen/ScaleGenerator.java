/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.ethelred.bsdata.DataNode;
import org.jspecify.annotations.Nullable;

/**
 * Generates a synthetic game system of a chosen size and shape: one game system file and a number of catalogue files,
 * in the JSON form of the real data. It contains no real names or text; only the sizes and shapes come from real
 * systems (see {@link Shape}). Output is fully determined by the shape and the seed, on the JVM and in JavaScript.
 *
 * <p>Every link target is visible from where the link is written under the scoped lookup, and links only point at
 * entries created earlier, so the data has no cycles. A few ids are repeated, as in real data.
 */
public final class ScaleGenerator {
    private static final String[] WORDS = {
        "alpha", "bravo", "cinder", "delta", "ember", "forge", "garnet", "harbor", "ivory", "juniper", "keystone",
        "lantern", "marble", "nimbus", "onyx", "prairie", "quartz", "ridge", "sable", "timber", "umber", "vessel",
        "willow", "xenon", "yarrow", "zephyr"
    };
    private static final String[] SCOPES = {"parent", "self", "ancestor", "roster", "force", "primary-catalogue"};
    private static final String[] CONDITION_TYPES = {"atLeast", "atMost", "equalTo", "instanceOf", "notInstanceOf"};
    private static final String[] MODIFIER_TYPES = {
        "set",
        "set",
        "set",
        "increment",
        "increment",
        "decrement",
        "append",
        "append",
        "add",
        "remove",
        "replace",
        "set-primary"
    };
    private static final String[] KEYWORDS = {"model", "unit", "any", "upgrade"};

    private final Shape shape;
    private final Rng rng;
    private int nameCounter;

    private final List<File> files = new ArrayList<>();
    private final List<Node> allNodes = new ArrayList<>();
    private final List<Holder> linkHolders = new ArrayList<>();
    private final List<Holder> profileHolders = new ArrayList<>();
    private final List<Holder> categoryHolders = new ArrayList<>();
    private final List<Holder> categoryLinkHolders = new ArrayList<>();
    private final List<Holder> modifierHosts = new ArrayList<>();
    private final List<Holder> modifierMaps = new ArrayList<>();
    private final List<Holder> modifierGroupMaps = new ArrayList<>();
    private final List<String> crossFileConstraintIds = new ArrayList<>();

    private File gameSystem;
    private final List<String> costTypeIds = new ArrayList<>();
    private final List<String> costTypeNames = new ArrayList<>();
    private final List<String[]> profileTypes = new ArrayList<>(); // id, name, then characteristic type ids and names
    private final List<String> characteristicTypeIds = new ArrayList<>();

    /** Something that can hold arrays of children, costs and so on. */
    private static class Holder {
        final Map<String, DataNode> m;
        final File file;

        Holder(Map<String, DataNode> m, File file) {
            this.m = m;
            this.file = file;
        }
    }

    private static final class Node extends Holder {
        final String id;
        final boolean group;
        private final @Nullable Node topNode;
        final String type;

        Node(Map<String, DataNode> m, File file, String id, boolean group, @Nullable Node top, String type) {
            super(m, file);
            this.id = id;
            this.group = group;
            this.topNode = top;
            this.type = type;
        }

        /** The shared entry at the top of the tree this node belongs to (itself, if it is one). */
        Node top() {
            Node t = topNode;
            return t == null ? this : t;
        }
    }

    private static final class File {
        final String name;
        final int index;
        final boolean gameSystem;
        final boolean library;
        final String rootId;
        final Map<String, DataNode> root = new LinkedHashMap<>();
        final List<Node> tops = new ArrayList<>();
        final List<Node> nodes = new ArrayList<>();
        final List<String> categoryIds = new ArrayList<>();
        final List<String> sharedRuleIds = new ArrayList<>();
        final List<String> sharedProfileIds = new ArrayList<>();
        final List<String> constraintIds = new ArrayList<>();
        final List<File> links = new ArrayList<>();
        double weight;

        File(String name, int index, boolean gameSystem, boolean library, String rootId) {
            this.name = name;
            this.index = index;
            this.gameSystem = gameSystem;
            this.library = library;
            this.rootId = rootId;
        }
    }

    private ScaleGenerator(Shape shape, int seed) {
        this.shape = shape;
        this.rng = new Rng(seed);
        this.gameSystem = new File("Game System.json", 0, true, false, "");
    }

    /** @return file name to parsed tree, in the order the files were created */
    public static Map<String, DataNode> generate(Shape shape, int seed) {
        return new ScaleGenerator(shape, seed).run();
    }

    private Map<String, DataNode> run() {
        createFiles();
        createCostAndProfileTypes();
        createTopLevelNodes();
        createNestedNodes();
        createSharedItems();
        createCatalogueLinks();
        createEntryLinks();
        attachCosts();
        attachProfiles();
        attachRules();
        attachInfoLinks();
        attachCategoryLinks();
        attachConstraints();
        attachModifiers();
        attachConditions();
        attachAssociations();
        createForceEntries();
        Map<String, DataNode> out = new LinkedHashMap<>();
        for (File f : files) {
            Map<String, DataNode> top = new LinkedHashMap<>();
            top.put(f.gameSystem ? "gameSystem" : "catalogue", new DataNode.Obj(f.root));
            out.put(f.name, new DataNode.Obj(top));
        }
        return out;
    }

    // ---- files

    private void createFiles() {
        gameSystem = new File("Game System.json", 0, true, false, rng.id());
        files.add(gameSystem);
        int libs = Math.min(shape.libraries(), shape.catalogues());
        for (int i = 1; i <= shape.catalogues(); i++) {
            boolean lib = i <= libs;
            String name = (lib ? "Library " : "Catalogue ") + (i < 10 ? "0" : "") + i + ".json";
            files.add(new File(name, i, false, lib, rng.id()));
        }
        for (File f : files) {
            Map<String, DataNode> r = f.root;
            r.put("name", str(f.name.substring(0, f.name.length() - 5)));
            r.put("id", str(f.rootId));
            r.put("authorName", str("generator"));
            r.put("battleScribeVersion", new DataNode.Num(2.03));
            if (f.gameSystem) {
                r.put("revision", new DataNode.Num(1));
                r.put("type", str("gameSystem"));
                r.put("xmlns", str("http://www.battlescribe.net/schema/gameSystemSchema"));
            } else {
                r.put("gameSystemId", str(gameSystem.rootId));
                r.put("gameSystemRevision", new DataNode.Num(1));
                r.put("library", new DataNode.Bool(f.library));
                r.put("revision", new DataNode.Num(1));
                r.put("type", str("catalogue"));
                r.put("xmlns", str("http://www.battlescribe.net/schema/catalogueSchema"));
            }
            // weight of the file when sharing out top-level entries: the game system and libraries are larger
            f.weight = (f.gameSystem ? 0.23 * 36 : f.library ? 0.28 * 36 / Math.max(1, libs) * 1.0 : 0.49)
                    * (0.7 + rng.below(60) / 100.0);
        }
    }

    private void createCostAndProfileTypes() {
        List<DataNode> costTypes = new ArrayList<>();
        String[] names = {"pts", "extra", "limit"};
        for (String n : names) {
            String id = rng.id();
            costTypeIds.add(id);
            costTypeNames.add(n);
            Map<String, DataNode> m = new LinkedHashMap<>();
            m.put("name", str(n));
            m.put("id", str(id));
            m.put("defaultCostLimit", new DataNode.Num(-1));
            costTypes.add(new DataNode.Obj(m));
        }
        gameSystem.root.put("costTypes", new DataNode.Arr(costTypes));
        List<DataNode> types = new ArrayList<>();
        int[] sizes = {3, 4, 4, 5, 5, 7};
        for (int s = 0; s < sizes.length; s++) {
            String[] type = new String[2 + 2 * sizes[s]];
            type[0] = rng.id();
            type[1] = "Profile Type " + (s + 1);
            List<DataNode> chars = new ArrayList<>();
            for (int c = 0; c < sizes[s]; c++) {
                type[2 + 2 * c] = rng.id();
                type[3 + 2 * c] = "Stat " + (c + 1);
                characteristicTypeIds.add(type[2 + 2 * c]);
                Map<String, DataNode> cm = new LinkedHashMap<>();
                cm.put("name", str(type[3 + 2 * c]));
                cm.put("id", str(type[2 + 2 * c]));
                chars.add(new DataNode.Obj(cm));
            }
            profileTypes.add(type);
            Map<String, DataNode> tm = new LinkedHashMap<>();
            tm.put("name", str(type[1]));
            tm.put("id", str(type[0]));
            tm.put("hidden", new DataNode.Bool(false));
            tm.put("characteristicTypes", new DataNode.Arr(chars));
            types.add(new DataNode.Obj(tm));
        }
        gameSystem.root.put("profileTypes", new DataNode.Arr(types));
        List<DataNode> pubs = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            Map<String, DataNode> m = new LinkedHashMap<>();
            m.put("name", str("Publication " + i));
            m.put("id", str(rng.id()));
            m.put("hidden", new DataNode.Bool(false));
            pubs.add(new DataNode.Obj(m));
        }
        gameSystem.root.put("publications", new DataNode.Arr(pubs));
    }

    // ---- entries

    private File pickFile() {
        double total = 0;
        for (File f : files) {
            total += f.weight;
        }
        double x = rng.below(1_000_000) / 1_000_000.0 * total;
        for (File f : files) {
            x -= f.weight;
            if (x <= 0) {
                return f;
            }
        }
        return files.get(files.size() - 1);
    }

    private String entryType(boolean top) {
        int r = rng.below(100);
        if (top) {
            return r < 55 ? "unit" : r < 80 ? "model" : "upgrade";
        }
        return r < 5 ? "unit" : r < 40 ? "model" : "upgrade";
    }

    private Node newNode(File file, @Nullable Node parent, boolean group, boolean top) {
        Map<String, DataNode> m = new LinkedHashMap<>();
        String id = rng.id();
        String type = group ? "" : entryType(top);
        m.put("name", str((group ? "Group " : "Entry ") + (++nameCounter)));
        m.put("id", str(id));
        m.put("hidden", new DataNode.Bool(false));
        if (!group) {
            m.put("type", str(type));
        }
        Node n = new Node(m, file, id, group, parent == null ? null : parent.top(), type);
        allNodes.add(n);
        file.nodes.add(n);
        return n;
    }

    private void createTopLevelNodes() {
        int total = shape.topEntries() + shape.topGroups();
        List<Node> created = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            File f = pickFile();
            boolean group = rng.below(total) < shape.topGroups();
            created.add(newNode(f, null, group, true));
        }
        // ranks follow file order, so links to "earlier" entries cannot form cycles
        int rank = 0;
        for (File f : files) {
            for (Node n : created) {
                if (n.file == f) {
                    f.tops.add(n);
                    ranks.put(n, rank++);
                }
            }
            for (Node n : f.tops) {
                arrAdd(
                        f.root,
                        n.group ? "sharedSelectionEntryGroups" : "sharedSelectionEntries",
                        new DataNode.Obj(n.m));
            }
        }
    }

    private final Map<Node, Integer> ranks = new java.util.IdentityHashMap<>();

    private int rankOf(Node n) {
        Integer r = ranks.get(n.top());
        return r == null ? 0 : r;
    }

    private void createNestedNodes() {
        List<Node> previous = new ArrayList<>();
        for (File f : files) {
            previous.addAll(f.tops);
        }
        for (int depth = 0; depth < shape.depthCounts().length; depth++) {
            if (previous.isEmpty()) {
                break;
            }
            List<Node> current = new ArrayList<>();
            for (int i = 0; i < shape.depthCounts()[depth]; i++) {
                Node parent = rng.pick(previous);
                boolean group = rng.chance(shape.nestedGroupFraction());
                Node n = newNode(parent.file, parent, group, false);
                arrAdd(parent.m, group ? "selectionEntryGroups" : "selectionEntries", new DataNode.Obj(n.m));
                current.add(n);
            }
            previous = current;
        }
    }

    // ---- shared items: categories, profiles, rules

    private void createSharedItems() {
        for (int i = 0; i < shape.categoryEntries(); i++) {
            File f = i < Math.max(2, shape.categoryEntries() / 7) ? gameSystem : pickFile();
            Map<String, DataNode> m = new LinkedHashMap<>();
            String id = rng.id();
            m.put("name", str("Category " + (++nameCounter)));
            m.put("id", str(id));
            m.put("hidden", new DataNode.Bool(false));
            arrAdd(f.root, "categoryEntries", new DataNode.Obj(m));
            f.categoryIds.add(id);
            Holder h = new Holder(m, f);
            categoryHolders.add(h);
            modifierHosts.add(h);
        }
        for (int i = 0; i < shape.sharedProfiles(); i++) {
            File f = i < Math.max(2, shape.sharedProfiles() / 5) ? gameSystem : pickFile();
            String id = rng.id();
            f.sharedProfileIds.add(id);
            arrAdd(f.root, "sharedProfiles", new DataNode.Obj(profile(id)));
        }
        for (int i = 0; i < shape.sharedRules(); i++) {
            File f = i < Math.max(2, shape.sharedRules() / 5) ? gameSystem : pickFile();
            String id = rng.id();
            f.sharedRuleIds.add(id);
            arrAdd(f.root, "sharedRules", new DataNode.Obj(rule(id)));
        }
    }

    private Map<String, DataNode> profile(String id) {
        String[] type = rng.pick(profileTypes);
        Map<String, DataNode> m = new LinkedHashMap<>();
        m.put("name", str("Profile " + (++nameCounter)));
        m.put("id", str(id));
        m.put("typeId", str(type[0]));
        m.put("typeName", str(type[1]));
        m.put("hidden", new DataNode.Bool(false));
        List<DataNode> chars = new ArrayList<>();
        for (int c = 2; c < type.length; c += 2) {
            Map<String, DataNode> cm = new LinkedHashMap<>();
            cm.put("name", str(type[c + 1]));
            cm.put("typeId", str(type[c]));
            cm.put("$text", str(characteristicText()));
            chars.add(new DataNode.Obj(cm));
        }
        m.put("characteristics", new DataNode.Arr(chars));
        return m;
    }

    private String characteristicText() {
        int r = rng.below(100);
        if (r < 70) {
            return String.valueOf(rng.between(1, 12)) + (rng.chance(0.3) ? "+" : "");
        }
        if (r < 95) {
            return text(rng.between(15, 60));
        }
        return text(rng.between(150, 1100));
    }

    private Map<String, DataNode> rule(String id) {
        Map<String, DataNode> m = new LinkedHashMap<>();
        m.put("name", str("Rule " + (++nameCounter)));
        m.put("id", str(id));
        m.put("description", str(text(rng.chance(0.05) ? rng.between(1000, 2700) : rng.between(80, 700))));
        m.put("hidden", new DataNode.Bool(false));
        return m;
    }

    private String text(int length) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < length) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(WORDS[rng.below(WORDS.length)]);
        }
        sb.setLength(length);
        return sb.toString();
    }

    // ---- links

    private void createCatalogueLinks() {
        for (File f : files) {
            if (f.gameSystem) {
                continue;
            }
            int wanted = f.library ? rng.between(0, 3) : rng.between(1, 5);
            for (int k = 0; k < wanted && f.index > 1; k++) {
                File target = files.get(rng.between(1, f.index - 1));
                if (target == f || f.links.contains(target)) {
                    continue;
                }
                f.links.add(target);
                Map<String, DataNode> m = new LinkedHashMap<>();
                m.put("name", str(target.name.substring(0, target.name.length() - 5)));
                m.put("id", str(rng.id()));
                m.put("importRootEntries", new DataNode.Bool(rng.chance(0.4)));
                m.put("targetId", str(target.rootId));
                m.put("type", str("catalogue"));
                arrAdd(f.root, "catalogueLinks", new DataNode.Obj(m));
            }
        }
    }

    /** The files visible from {@code f} besides itself and the game system: its links, followed transitively. */
    private List<File> visible(File f) {
        List<File> out = new ArrayList<>();
        collectVisible(f, out);
        return out;
    }

    private void collectVisible(File f, List<File> out) {
        for (File l : f.links) {
            if (!out.contains(l)) {
                out.add(l);
                collectVisible(l, out);
            }
        }
    }

    private @Nullable Node linkTarget(Node from) {
        File f = from.file;
        int rank = rankOf(from);
        List<Node> own = new ArrayList<>();
        for (Node t : f.tops) {
            if (ranks.getOrDefault(t, 0) < rank) {
                own.add(t);
            }
        }
        List<Node> gs = f.gameSystem ? List.of() : gameSystem.tops;
        List<Node> libs = new ArrayList<>();
        List<Node> others = new ArrayList<>();
        for (File v : visible(f)) {
            (v.library ? libs : others).addAll(v.tops);
        }
        double wOwn = own.isEmpty() ? 0 : 0.555;
        double wGs = gs.isEmpty() ? 0 : 0.362;
        double wLib = libs.isEmpty() ? 0 : 0.042;
        double wOther = others.isEmpty() ? 0 : 0.040;
        double total = wOwn + wGs + wLib + wOther;
        if (total == 0) {
            return null;
        }
        double x = rng.below(1_000_000) / 1_000_000.0 * total;
        if ((x -= wOwn) < 0) {
            return rng.pick(own);
        }
        if ((x -= wGs) < 0) {
            return rng.pick(gs);
        }
        if ((x -= wLib) < 0) {
            return rng.pick(libs);
        }
        return rng.pick(others);
    }

    private Map<String, DataNode> linkTo(Node target) {
        Map<String, DataNode> m = new LinkedHashMap<>();
        m.put("name", str("Link " + (++nameCounter)));
        m.put("id", str(rng.id()));
        m.put("hidden", new DataNode.Bool(false));
        m.put("import", new DataNode.Bool(true));
        m.put("targetId", str(target.id));
        m.put("type", str(target.group ? "selectionEntryGroup" : "selectionEntry"));
        return m;
    }

    private void createEntryLinks() {
        List<File> factions = new ArrayList<>();
        for (File f : files) {
            if (!f.gameSystem && !f.library) {
                factions.add(f);
            }
        }
        int made = 0;
        for (int tries = 0; made < shape.rootLinks() && tries < shape.rootLinks() * 5 && !factions.isEmpty(); tries++) {
            File f = rng.pick(factions);
            Node source = f.tops.isEmpty() ? null : f.tops.get(f.tops.size() - 1);
            Node target = pickRootTarget(f);
            if (target == null || source == null) {
                continue;
            }
            Map<String, DataNode> m = linkTo(target);
            arrAdd(f.root, "entryLinks", new DataNode.Obj(m));
            Holder h = new Holder(m, f);
            linkHolders.add(h);
            modifierHosts.add(h);
            // a root link is a unit added to the force: give it its primary category now
            if (!f.categoryIds.isEmpty() || !gameSystem.categoryIds.isEmpty()) {
                addCategoryLink(h, true);
            }
            made++;
        }
        List<Node> nodes = new ArrayList<>(allNodes);
        made = 0;
        for (int tries = 0; made < shape.nestedLinks() && tries < shape.nestedLinks() * 3; tries++) {
            Node parent = rng.pick(nodes);
            Node target = linkTarget(parent);
            if (target == null || target.top() == parent.top()) {
                continue;
            }
            Map<String, DataNode> m = linkTo(target);
            arrAdd(parent.m, "entryLinks", new DataNode.Obj(m));
            Holder h = new Holder(m, parent.file);
            linkHolders.add(h);
            modifierHosts.add(h);
            made++;
        }
    }

    private @Nullable Node pickRootTarget(File f) {
        List<Node> pool = new ArrayList<>(f.tops);
        pool.addAll(gameSystem.tops);
        for (File v : visible(f)) {
            pool.addAll(v.tops);
        }
        return pool.isEmpty() ? null : rng.pick(pool);
    }

    // ---- budgets over existing holders

    private List<Node> entryNodes() {
        List<Node> out = new ArrayList<>();
        for (Node n : allNodes) {
            if (!n.group) {
                out.add(n);
            }
        }
        return out;
    }

    private void attachCosts() {
        // in real data only some entries carry costs, so choose from a fixed subset
        List<Node> entries = new ArrayList<>();
        for (Node n : entryNodes()) {
            if (rng.chance(0.44)) {
                entries.add(n);
            }
        }
        if (entries.isEmpty()) {
            entries = entryNodes();
        }
        for (int i = 0; i < shape.costs(); i++) {
            Holder h = rng.chance(0.85) && !entries.isEmpty() || linkHolders.isEmpty()
                    ? rng.pick(entries)
                    : rng.pick(linkHolders);
            Map<String, DataNode> c = new LinkedHashMap<>();
            int t = rng.chance(0.9) ? 0 : rng.between(1, costTypeIds.size() - 1);
            c.put("name", str(costTypeNames.get(t)));
            c.put("typeId", str(costTypeIds.get(t)));
            c.put("value", new DataNode.Num(rng.between(0, 300)));
            arrAdd(h.m, "costs", new DataNode.Obj(c));
        }
    }

    private void attachProfiles() {
        List<Node> eligible = new ArrayList<>();
        for (Node n : allNodes) {
            if (!n.group && !n.type.equals("upgrade")) {
                eligible.add(n);
            }
        }
        if (eligible.isEmpty()) {
            eligible = entryNodes();
        }
        for (int i = 0; i < shape.profiles(); i++) {
            Node n = rng.pick(eligible);
            Map<String, DataNode> p = profile(rng.id());
            arrAdd(n.m, "profiles", new DataNode.Obj(p));
            Holder h = new Holder(p, n.file);
            profileHolders.add(h);
            modifierHosts.add(h);
        }
    }

    private void attachRules() {
        for (int i = 0; i < shape.rules(); i++) {
            Node n = rng.pick(allNodes);
            arrAdd(n.m, "rules", new DataNode.Obj(rule(rng.id())));
        }
    }

    private void attachInfoLinks() {
        for (int i = 0; i < shape.infoLinks(); i++) {
            Node n = rng.pick(allNodes);
            boolean wantRule = rng.chance(0.4);
            List<String> pool = infoPool(n.file, wantRule);
            if (pool.isEmpty()) {
                pool = infoPool(n.file, !wantRule);
                wantRule = !wantRule;
            }
            if (pool.isEmpty()) {
                continue;
            }
            Map<String, DataNode> m = new LinkedHashMap<>();
            m.put("name", str("Info " + (++nameCounter)));
            m.put("id", str(rng.id()));
            m.put("hidden", new DataNode.Bool(false));
            m.put("targetId", str(rng.pick(pool)));
            m.put("type", str(wantRule ? "rule" : "profile"));
            arrAdd(n.m, "infoLinks", new DataNode.Obj(m));
            Holder h = new Holder(m, n.file);
            modifierHosts.add(h);
        }
    }

    private List<String> infoPool(File f, boolean rule) {
        List<String> pool = new ArrayList<>(rule ? f.sharedRuleIds : f.sharedProfileIds);
        if (!f.gameSystem) {
            pool.addAll(rule ? gameSystem.sharedRuleIds : gameSystem.sharedProfileIds);
        }
        return pool;
    }

    private void addCategoryLink(Holder h, boolean primary) {
        List<String> pool = new ArrayList<>(h.file.categoryIds);
        if (!h.file.gameSystem) {
            pool.addAll(gameSystem.categoryIds);
        }
        if (pool.isEmpty()) {
            return;
        }
        Map<String, DataNode> m = new LinkedHashMap<>();
        m.put("name", str("Category link " + (++nameCounter)));
        m.put("id", str(rng.id()));
        m.put("targetId", str(rng.pick(pool)));
        m.put("primary", new DataNode.Bool(primary));
        arrAdd(h.m, "categoryLinks", new DataNode.Obj(m));
        Holder link = new Holder(m, h.file);
        categoryLinkHolders.add(link);
    }

    private void attachCategoryLinks() {
        int remaining = shape.categoryLinks() - categoryLinkHolders.size();
        List<Node> entries = entryNodes();
        for (int i = 0; i < remaining; i++) {
            Holder h = rng.chance(0.85) || linkHolders.isEmpty() ? rng.pick(entries) : rng.pick(linkHolders);
            addCategoryLink(h, !h.m.containsKey("categoryLinks"));
        }
    }

    private void attachConstraints() {
        for (int i = 0; i < shape.constraints(); i++) {
            Holder h;
            int r = rng.below(100);
            if (r < 70) {
                h = rng.pick(allNodes);
            } else if (r < 90 && !linkHolders.isEmpty()) {
                h = rng.pick(linkHolders);
            } else if (r < 95 && !categoryLinkHolders.isEmpty()) {
                h = rng.pick(categoryLinkHolders);
            } else if (!categoryHolders.isEmpty()) {
                h = rng.pick(categoryHolders);
            } else {
                h = rng.pick(allNodes);
            }
            String id;
            if (!crossFileConstraintIds.isEmpty() && rng.chance(0.0075)) {
                id = rng.pick(crossFileConstraintIds);
            } else if (!h.file.constraintIds.isEmpty() && rng.chance(0.025)) {
                id = rng.pick(h.file.constraintIds);
            } else {
                id = rng.id();
                h.file.constraintIds.add(id);
                if (rng.chance(0.02)) {
                    crossFileConstraintIds.add(id);
                }
            }
            Map<String, DataNode> c = new LinkedHashMap<>();
            c.put("id", str(id));
            c.put("field", str("selections"));
            c.put("scope", str(SCOPES[rng.below(SCOPES.length)]));
            c.put("shared", new DataNode.Bool(true));
            c.put("type", str(rng.chance(0.5) ? "min" : "max"));
            c.put("value", new DataNode.Num(rng.between(0, 5)));
            arrAdd(h.m, "constraints", new DataNode.Obj(c));
        }
    }

    private Holder newModifier(File f) {
        Map<String, DataNode> m = new LinkedHashMap<>();
        String type = MODIFIER_TYPES[rng.below(MODIFIER_TYPES.length)];
        m.put("type", str(type));
        int r = rng.below(100);
        String field;
        if (r < 50) {
            field = rng.pick(characteristicTypeIds);
        } else if (r < 65) {
            field = rng.pick(costTypeIds);
        } else if (r < 85 && !f.constraintIds.isEmpty()) {
            field = rng.pick(f.constraintIds);
        } else {
            field = rng.chance(0.5) ? "name" : "hidden";
        }
        m.put("field", str(field));
        if (field.equals("hidden")) {
            m.put("value", new DataNode.Bool(rng.chance(0.5)));
        } else if (type.equals("append") || type.equals("replace") || type.equals("set")) {
            m.put("value", str(characteristicText()));
        } else {
            m.put("value", new DataNode.Num(rng.between(0, 10)));
        }
        Holder h = new Holder(m, f);
        modifierMaps.add(h);
        return h;
    }

    private void attachModifiers() {
        int grouped = Math.min(shape.modifiers() / 3, shape.modifierGroups() * 3);
        int direct = shape.modifiers() - grouped;
        for (int g = 0; g < shape.modifierGroups(); g++) {
            Holder h = pickModifierHost();
            Map<String, DataNode> group = new LinkedHashMap<>();
            List<DataNode> mods = new ArrayList<>();
            for (int k = 0; k < 3 && grouped > 0; k++, grouped--) {
                mods.add(new DataNode.Obj(newModifier(h.file).m));
            }
            group.put("modifiers", new DataNode.Arr(mods));
            arrAdd(h.m, "modifierGroups", new DataNode.Obj(group));
            modifierGroupMaps.add(new Holder(group, h.file));
        }
        for (int i = 0; i < direct; i++) {
            Holder h = pickModifierHost();
            arrAdd(h.m, "modifiers", new DataNode.Obj(newModifier(h.file).m));
        }
    }

    private Holder pickModifierHost() {
        if (modifierHosts.isEmpty() || rng.chance(0.45)) {
            return rng.pick(allNodes);
        }
        return rng.pick(modifierHosts);
    }

    private Map<String, DataNode> newCondition(File f) {
        Map<String, DataNode> c = new LinkedHashMap<>();
        int r = rng.below(100);
        String child;
        if (r < 60 && !f.nodes.isEmpty()) {
            child = rng.pick(f.nodes).id;
        } else if (r < 85) {
            child = KEYWORDS[rng.below(KEYWORDS.length)];
        } else {
            child = gameSystem.nodes.isEmpty() ? "any" : rng.pick(gameSystem.nodes).id;
        }
        c.put("childId", str(child));
        c.put("field", str("selections"));
        c.put("scope", str(SCOPES[rng.below(SCOPES.length)]));
        c.put("shared", new DataNode.Bool(true));
        c.put("type", str(CONDITION_TYPES[rng.below(CONDITION_TYPES.length)]));
        c.put("value", new DataNode.Num(rng.between(0, 4)));
        return c;
    }

    private void attachConditions() {
        List<Holder> hosts = new ArrayList<>(modifierMaps);
        hosts.addAll(modifierGroupMaps);
        if (hosts.isEmpty()) {
            return;
        }
        int perGroup = shape.conditionGroups() > 0 && shape.conditions() >= 2 * shape.conditionGroups() ? 2 : 1;
        int used = 0;
        for (int i = 0; i < shape.conditionGroups(); i++) {
            Holder host = rng.pick(hosts);
            Map<String, DataNode> group = new LinkedHashMap<>();
            group.put("type", str(rng.chance(0.5) ? "or" : "and"));
            List<DataNode> inner = new ArrayList<>();
            for (int k = 0; k < perGroup; k++) {
                inner.add(new DataNode.Obj(newCondition(host.file)));
                used++;
            }
            group.put("conditions", new DataNode.Arr(inner));
            arrAdd(host.m, "conditionGroups", new DataNode.Obj(group));
        }
        for (int i = used; i < shape.conditions(); i++) {
            Holder host = rng.pick(hosts);
            arrAdd(host.m, "conditions", new DataNode.Obj(newCondition(host.file)));
        }
        List<Holder> repeatHosts = modifierMaps.isEmpty() ? hosts : modifierMaps;
        for (int i = 0; i < shape.repeats(); i++) {
            Holder host = rng.pick(repeatHosts);
            Map<String, DataNode> rep = new LinkedHashMap<>();
            rep.put("value", new DataNode.Num(1));
            rep.put("repeats", new DataNode.Num(rng.between(1, 3)));
            rep.put("field", str("selections"));
            rep.put("scope", str(SCOPES[rng.below(SCOPES.length)]));
            rep.put("childId", str(KEYWORDS[rng.below(KEYWORDS.length)]));
            rep.put("shared", new DataNode.Bool(true));
            rep.put("roundUp", new DataNode.Bool(false));
            arrAdd(host.m, "repeats", new DataNode.Obj(rep));
        }
    }

    private void attachAssociations() {
        List<Node> units = new ArrayList<>();
        for (Node n : allNodes) {
            if (n.top() == n && !n.group) {
                units.add(n);
            }
        }
        if (units.isEmpty()) {
            return;
        }
        for (int i = 0; i < shape.associations(); i++) {
            Node n = rng.pick(units);
            Map<String, DataNode> a = new LinkedHashMap<>();
            a.put("name", str("Association " + (i + 1)));
            a.put("action", str("group"));
            a.put("childId", str("unit"));
            a.put("includeChildSelections", new DataNode.Bool(true));
            a.put("label", str("Label"));
            a.put("max", new DataNode.Num(1));
            a.put("min", new DataNode.Num(0));
            a.put("scope", str("force"));
            arrAdd(n.m, "associations", new DataNode.Obj(a));
        }
    }

    private void createForceEntries() {
        List<DataNode> forces = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, DataNode> m = new LinkedHashMap<>();
            m.put("name", str("Force " + i));
            m.put("id", str(rng.id()));
            m.put("hidden", new DataNode.Bool(false));
            List<DataNode> links = new ArrayList<>();
            for (int k = 0; k < 3 && !gameSystem.categoryIds.isEmpty(); k++) {
                Map<String, DataNode> l = new LinkedHashMap<>();
                l.put("name", str("Force category " + (k + 1)));
                l.put("id", str(rng.id()));
                l.put("targetId", str(rng.pick(gameSystem.categoryIds)));
                l.put("primary", new DataNode.Bool(false));
                links.add(new DataNode.Obj(l));
            }
            m.put("categoryLinks", new DataNode.Arr(links));
            forces.add(new DataNode.Obj(m));
        }
        gameSystem.root.put("forceEntries", new DataNode.Arr(forces));
    }

    // ---- small helpers

    private static DataNode.Str str(String s) {
        return new DataNode.Str(s);
    }

    private static void arrAdd(Map<String, DataNode> m, String key, DataNode item) {
        DataNode existing = m.get(key);
        if (existing instanceof DataNode.Arr a) {
            a.items().add(item);
        } else {
            List<DataNode> items = new ArrayList<>();
            items.add(item);
            m.put(key, new DataNode.Arr(items));
        }
    }
}
