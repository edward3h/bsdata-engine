/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import java.util.Map;
import org.ethelred.bsdata.DataNode;

/** Loads the typed model and counts what it holds; used by the command-line checkers. */
public final class ModelReport {
    private int entries;
    private int groups;
    private int links;
    private int unresolvedLinks;
    private int forceEntries;
    private int costed;

    private ModelReport() {}

    public static String of(Map<String, DataNode> files) {
        return of(GameData.load(files));
    }

    public static String of(GameData data) {
        ModelReport r = new ModelReport();
        for (Catalogue c : data.catalogues()) {
            c.rootEntries().forEach(r::entry);
            c.sharedEntries().forEach(r::entry);
            c.rootLinks().forEach(l -> r.link(data, c.file(), l));
            r.forceEntries += c.forceEntries().size();
        }
        // links inside entries are resolved from the file that defines them
        for (Catalogue c : data.catalogues()) {
            c.rootEntries().forEach(e -> r.nested(data, c.file(), e));
            c.sharedEntries().forEach(e -> r.nested(data, c.file(), e));
            c.rootLinks().forEach(l -> r.nested(data, c.file(), l.body()));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("model catalogues=").append(data.catalogues().size());
        sb.append(" entries=").append(r.entries);
        sb.append(" groups=").append(r.groups);
        sb.append(" costed=").append(r.costed);
        sb.append(" links=").append(r.links);
        sb.append(" unresolvedLinks=").append(r.unresolvedLinks);
        sb.append(" forceEntries=").append(r.forceEntries).append('\n');
        return sb.toString();
    }

    private void entry(Entry e) {
        if (e.group()) {
            groups++;
        } else {
            entries++;
        }
        if (!e.body().costs().isEmpty()) {
            costed++;
        }
        for (Child c : e.body().children()) {
            if (c instanceof Entry child) {
                entry(child);
            }
        }
    }

    private void link(GameData data, String file, Link l) {
        links++;
        if (data.target(file, l) == null) {
            unresolvedLinks++;
        }
    }

    private void nested(GameData data, String file, Entry e) {
        nested(data, file, e.body());
    }

    private void nested(GameData data, String file, Body body) {
        for (Child c : body.children()) {
            switch (c) {
                case Entry e -> nested(data, file, e.body());
                case Link l -> {
                    link(data, file, l);
                    nested(data, file, l.body());
                }
            }
        }
    }
}
