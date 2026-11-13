/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link JsonSource} backed by minimal-json. */
public final class MinimalJsonSource implements JsonSource {
    @Override
    public DataNode parse(String text) {
        return convert(Json.parse(text));
    }

    private static DataNode convert(JsonValue v) {
        if (v.isObject()) {
            Map<String, DataNode> fields = new LinkedHashMap<>();
            v.asObject().forEach(m -> fields.put(m.getName(), convert(m.getValue())));
            return new DataNode.Obj(fields);
        }
        if (v.isArray()) {
            List<DataNode> items = new ArrayList<>();
            v.asArray().forEach(i -> items.add(convert(i)));
            return new DataNode.Arr(items);
        }
        if (v.isString()) {
            return new DataNode.Str(v.asString());
        }
        if (v.isNumber()) {
            return new DataNode.Num(v.asDouble());
        }
        if (v.isBoolean()) {
            return new DataNode.Bool(v.asBoolean());
        }
        return new DataNode.Null();
    }
}
