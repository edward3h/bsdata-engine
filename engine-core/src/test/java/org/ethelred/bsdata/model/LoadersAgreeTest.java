/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.ethelred.bsdata.DataNode;
import org.ethelred.bsdata.MinimalJsonSource;
import org.ethelred.bsdata.TestData;
import org.ethelred.bsdata.gen.ScaleGenerator;
import org.ethelred.bsdata.gen.Shape;
import org.junit.jupiter.api.Test;

/** The loaders differ only in memory use: all three must give the same typed model. */
class LoadersAgreeTest {
    private static final MinimalJsonSource JSON = new MinimalJsonSource();

    private static TextSource source(Map<String, String> texts) {
        return new TextSource() {
            @Override
            public List<String> names() {
                return List.copyOf(new TreeMap<>(texts).keySet());
            }

            @Override
            public String read(String name) {
                return TestData.required(texts.get(name));
            }
        };
    }

    private static void assertSame(GameData a, GameData b) {
        assertEquals(a.catalogues(), b.catalogues());
    }

    @Test
    void agreeOnTheSyntheticSystem() throws IOException {
        Map<String, String> texts = new TreeMap<>();
        for (String f : List.of("Extension Tests", "Extension Tests Catalogue", "M1 Costs")) {
            texts.put(f + ".json", TestData.resource("/data/extension-tests/" + f + ".json"));
        }
        TextSource source = source(texts);
        GameData trees = GameData.loadTrees(source, JSON);
        assertSame(trees, GameData.loadPerFile(source, JSON));
        assertSame(trees, GameData.loadStreaming(source));
    }

    @Test
    void agreeOnAGeneratedDataset() {
        Map<String, DataNode> files =
                ScaleGenerator.generate(Shape.elevenSized().scaled(0.05), 4);
        Map<String, String> texts = new TreeMap<>();
        files.forEach((name, tree) -> texts.put(name, JSON.writePretty(tree)));
        TextSource source = source(texts);
        GameData trees = GameData.loadTrees(source, JSON);
        assertSame(trees, GameData.loadStreaming(source));
        assertEquals(46, trees.catalogues().size());
    }
}
