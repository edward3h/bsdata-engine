/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.ethelred.bsdata.DataNode;
import org.jspecify.annotations.Nullable;

/** Lenient typed access to the fields of one parsed object: absent fields give defaults. */
final class Reader {
    private static final Map<String, DataNode> NONE = Map.of();
    private final Map<String, DataNode> fields;

    private Reader(Map<String, DataNode> fields) {
        this.fields = fields;
    }

    static Reader of(DataNode node) {
        return new Reader(node instanceof DataNode.Obj o ? o.fields() : NONE);
    }

    String str(String key) {
        String s = optStr(key);
        return s == null ? "" : s;
    }

    @Nullable
    String optStr(String key) {
        return fields.get(key) instanceof DataNode.Str s ? s.value() : null;
    }

    boolean bool(String key) {
        return fields.get(key) instanceof DataNode.Bool b && b.value();
    }

    /** A number, accepting a numeric string; {@code def} if absent or not a number. */
    double num(String key, double def) {
        DataNode n = fields.get(key);
        if (n instanceof DataNode.Num num) {
            return num.value();
        }
        if (n instanceof DataNode.Str s) {
            try {
                return Double.parseDouble(s.value().trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    List<DataNode> arr(String key) {
        return fields.get(key) instanceof DataNode.Arr a ? a.items() : List.of();
    }

    List<Reader> objs(String key) {
        List<Reader> out = new ArrayList<>();
        for (DataNode n : arr(key)) {
            if (n instanceof DataNode.Obj) {
                out.add(of(n));
            }
        }
        return out;
    }
}
