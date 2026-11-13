/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.ethelred.bsdata.DataNode;
import org.jspecify.annotations.Nullable;

/** Lenient reading of parsed JSON objects: absent fields give defaults. */
final class Json {
    private static final Map<String, DataNode> NONE = Map.of();
    private final Map<String, DataNode> fields;

    private Json(Map<String, DataNode> fields) {
        this.fields = fields;
    }

    static Json of(DataNode node) {
        return new Json(node instanceof DataNode.Obj o ? o.fields() : NONE);
    }

    String str(String key, String def) {
        return fields.get(key) instanceof DataNode.Str s ? s.value() : def;
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

    @Nullable
    Double optNum(String key) {
        return fields.containsKey(key) ? Double.valueOf(num(key, 0)) : null;
    }

    List<Json> objs(String key) {
        List<Json> out = new ArrayList<>();
        if (fields.get(key) instanceof DataNode.Arr a) {
            for (DataNode n : a.items()) {
                if (n instanceof DataNode.Obj) {
                    out.add(of(n));
                }
            }
        }
        return out;
    }

    boolean has(String key) {
        return fields.containsKey(key);
    }
}
