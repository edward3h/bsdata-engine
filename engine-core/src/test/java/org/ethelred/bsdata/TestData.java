/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import org.ethelred.bsdata.model.GameData;
import org.jspecify.annotations.Nullable;

/** Loads the synthetic Extension Tests game system and the rosters exported from New Recruit. */
public final class TestData {
    private static final MinimalJsonSource JSON = new MinimalJsonSource();
    private static final String[] FILES = {
        "Extension Tests", "Extension Tests Catalogue", "M1 Costs", "M2 Scopes", "M5 Constraints"
    };

    private TestData() {}

    /** Fails the test if the value is null, and tells the nullness checker it is not. */
    public static <T> T required(@Nullable T value) {
        if (value == null) {
            throw new IllegalStateException("unexpected null");
        }
        return value;
    }

    public static String resource(String path) throws IOException {
        try (InputStream in = TestData.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static GameData gameData() throws IOException {
        Map<String, DataNode> files = new TreeMap<>();
        for (String f : FILES) {
            files.put(f + ".json", JSON.parse(resource("/data/extension-tests/" + f + ".json")));
        }
        return GameData.load(files);
    }

    public static Roster roster(String name) throws IOException {
        return Roster.read(JSON.parse(resource("/rosters/" + name)));
    }
}
