/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

import java.util.List;
import java.util.Map;

/** Neutral tree for one parsed data file; independent of the JSON library. */
public sealed interface DataNode {
    record Obj(Map<String, DataNode> fields) implements DataNode {}

    record Arr(List<DataNode> items) implements DataNode {}

    record Str(String value) implements DataNode {}

    record Num(double value) implements DataNode {}

    record Bool(boolean value) implements DataNode {}

    record Null() implements DataNode {}
}
