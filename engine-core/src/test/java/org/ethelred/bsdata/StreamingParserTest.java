/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreamingParserTest {
    private static final String TEXT = """
            {"catalogue": {
              "id": "c", "n": 1.5, "ok": true, "none": null,
              "entries": [{"id": "a", "kids": [{"id": "a1"}]}, {"id": "b"}],
              "links": [{"id": "l"}],
              "plain": [1, 2, 3]
            }}
            """;

    @Test
    void withoutStreamingItBuildsTheSameTreeAsTheLibrary() {
        assertEquals(new MinimalJsonSource().parse(TEXT), StreamingParser.parse(TEXT));
    }

    @Test
    void streamedArraysGoToTheConsumerAndAreLeftOutOfTheResult() {
        List<String> seen = new ArrayList<>();
        DataNode rest = StreamingParser.parse(TEXT, 3, (name, item) -> {
            if (item instanceof DataNode.Obj o && o.fields().get("id") instanceof DataNode.Str s) {
                seen.add(name + ":" + s.value());
            }
        });
        // elements of the arrays directly in the catalogue object, in order; "plain" holds numbers, not objects
        assertEquals(List.of("entries:a", "entries:b", "links:l"), seen);
        DataNode expected = new MinimalJsonSource().parse("""
                {"catalogue": {"id": "c", "n": 1.5, "ok": true, "none": null}}
                """);
        assertEquals(expected, rest);
    }

    @Test
    void nestedArraysAreNotStreamed() {
        List<String> names = new ArrayList<>();
        StreamingParser.parse(TEXT, 3, (name, item) -> names.add(name));
        assertEquals(List.of("entries", "entries", "links", "plain", "plain", "plain"), names);
    }
}
