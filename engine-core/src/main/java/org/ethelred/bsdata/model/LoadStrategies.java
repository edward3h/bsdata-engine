/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import java.util.Map;
import java.util.TreeMap;
import org.ethelred.bsdata.DataNode;
import org.ethelred.bsdata.JsonSource;

/**
 * The ways of loading a {@link TextSource} that the memory pass compared, by name, for the benchmarks and for the test
 * that checks they give the same model. Use {@link GameData#load(TextSource)} in real code; the other two are the
 * earlier versions, kept as baselines.
 */
public final class LoadStrategies {
    private LoadStrategies() {}

    /**
     * @param strategy {@code tree}: parse every file into a tree, then type them all; {@code file}: one tree per file,
     *     typed and dropped; {@code stream}: no tree, typed as it is parsed (the real loader)
     */
    public static GameData load(String strategy, TextSource source, JsonSource json) {
        return switch (strategy) {
            case "tree" -> tree(source, json);
            case "file" -> perFile(source, json);
            case "stream" -> GameData.load(source);
            default -> throw new IllegalArgumentException("unknown strategy " + strategy);
        };
    }

    private static GameData tree(TextSource source, JsonSource json) {
        Map<String, DataNode> files = new TreeMap<>();
        for (String name : source.names()) {
            files.put(name, json.parse(source.read(name)));
        }
        return GameData.load(files);
    }

    private static GameData perFile(TextSource source, JsonSource json) {
        GameData.Builder b = GameData.builder();
        for (String name : source.names()) {
            b.add(name, json.parse(source.read(name)));
        }
        return b.build();
    }
}
