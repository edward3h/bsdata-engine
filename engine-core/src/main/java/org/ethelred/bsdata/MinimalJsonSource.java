/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link JsonSource} and {@link JsonSink} backed by minimal-json. */
public final class MinimalJsonSource implements JsonSource, JsonSink {
    @Override
    public DataNode parse(String text) {
        return convert(Json.parse(text));
    }

    @Override
    public String write(DataNode node) {
        return toJson(node).toString();
    }

    private static JsonValue toJson(DataNode node) {
        return switch (node) {
            case DataNode.Obj o -> {
                JsonObject obj = new JsonObject();
                o.fields().forEach((k, v) -> obj.add(k, toJson(v)));
                yield obj;
            }
            case DataNode.Arr a -> {
                JsonArray arr = new JsonArray();
                a.items().forEach(i -> arr.add(toJson(i)));
                yield arr;
            }
            case DataNode.Str s -> Json.value(s.value());
            case DataNode.Num n -> Json.value(n.value());
            case DataNode.Bool b -> Json.value(b.value());
            case DataNode.Null n -> Json.NULL;
        };
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
