/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.jvm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.ethelred.bsdata.DataNode;
import org.ethelred.bsdata.Linker;
import org.ethelred.bsdata.MinimalJsonSource;
import org.ethelred.bsdata.RosterReport;

/**
 * Command-line checker: {@code Main <directory>} reports id references across all JSON files in it; {@code Main
 * <file>.roster.json} reads and rewrites a roster and summarises it.
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("usage: Main <directory of .json files> | <file>.roster.json");
            System.exit(2);
        }
        if (args[0].endsWith(".roster.json")) {
            System.out.print(RosterReport.of(Files.readString(Path.of(args[0]), StandardCharsets.UTF_8)));
            return;
        }
        var json = new MinimalJsonSource();
        Map<String, DataNode> files = new TreeMap<>();
        try (Stream<Path> list = Files.list(Path.of(args[0]))) {
            for (Path p : list.filter(f -> f.toString().endsWith(".json")).toList()) {
                Path name = p.getFileName();
                if (name != null) {
                    files.put(name.toString(), json.parse(Files.readString(p, StandardCharsets.UTF_8)));
                }
            }
        }
        System.out.print(Linker.check(files).format(15));
    }
}
