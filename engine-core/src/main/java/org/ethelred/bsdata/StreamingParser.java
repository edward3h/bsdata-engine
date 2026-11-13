/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

import com.eclipsesource.json.JsonHandler;
import com.eclipsesource.json.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Parses JSON text into {@link DataNode}s without building the library's own tree first, and can hand over the
 * elements of arrays at a chosen depth one at a time instead of keeping them. For a data file that means one entry
 * is alive at once, not the whole file.
 */
public final class StreamingParser {
    /** Receives the elements of the streamed arrays. */
    public interface ItemConsumer {
        void accept(String arrayName, DataNode item);
    }

    private StreamingParser() {}

    /** Parses everything into a tree (no streaming). */
    public static DataNode parse(String text) {
        return parse(text, 0, (name, item) -> {});
    }

    /**
     * @param streamDepth the depth of the arrays whose elements go to {@code consumer} and are left out of the result;
     *     the outermost value is depth 1, so in {@code {"catalogue": {"entries": [ ... ]}}} the array is at depth 3.
     *     Zero streams nothing.
     * @return the tree without the streamed arrays
     */
    public static DataNode parse(String text, int streamDepth, ItemConsumer consumer) {
        Handler h = new Handler(streamDepth, consumer);
        new JsonParser(h).parse(text);
        return h.result();
    }

    private static final class Handler extends JsonHandler<List<DataNode>, Map<String, DataNode>> {
        private final int streamDepth;
        private final ItemConsumer consumer;
        private final List<String> keys = new ArrayList<>();
        private @Nullable DataNode value;
        private int depth;
        private boolean skipNext;

        Handler(int streamDepth, ItemConsumer consumer) {
            this.streamDepth = streamDepth;
            this.consumer = consumer;
        }

        DataNode result() {
            DataNode v = value;
            return v == null ? new DataNode.Null() : v;
        }

        private DataNode current() {
            DataNode v = value;
            return v == null ? new DataNode.Null() : v;
        }

        @Override
        public void endNull() {
            value = new DataNode.Null();
        }

        @Override
        public void endBoolean(boolean b) {
            value = new DataNode.Bool(b);
        }

        @Override
        public void endString(String s) {
            value = new DataNode.Str(s);
        }

        @Override
        public void endNumber(String s) {
            value = new DataNode.Num(Double.parseDouble(s));
        }

        @Override
        public List<DataNode> startArray() {
            depth++;
            return new ArrayList<>();
        }

        @Override
        public void endArray(List<DataNode> items) {
            // a streamed array is empty here: its elements went to the consumer
            skipNext = depth == streamDepth;
            depth--;
            value = new DataNode.Arr(items);
        }

        @Override
        public void endArrayValue(List<DataNode> items) {
            if (depth == streamDepth && !keys.isEmpty()) {
                consumer.accept(keys.get(keys.size() - 1), current());
            } else {
                items.add(current());
            }
        }

        @Override
        public Map<String, DataNode> startObject() {
            depth++;
            return new LinkedHashMap<>();
        }

        @Override
        public void endObject(Map<String, DataNode> fields) {
            depth--;
            value = new DataNode.Obj(fields);
        }

        @Override
        public void startObjectValue(Map<String, DataNode> fields, String name) {
            keys.add(name);
        }

        @Override
        public void endObjectValue(Map<String, DataNode> fields, String name) {
            keys.remove(keys.size() - 1);
            if (skipNext) {
                skipNext = false;
            } else {
                fields.put(name, current());
            }
        }
    }
}
