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
import org.ethelred.bsdata.gen.ScaleGenerator;
import org.ethelred.bsdata.gen.ScaleReport;
import org.ethelred.bsdata.gen.Shape;
import org.ethelred.bsdata.model.ModelReport;

/**
 * Command-line checker: {@code Main <directory>} reports id references across all JSON files in it; {@code Main
 * <file>.roster.json} reads and rewrites a roster and summarises it.
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println(
                    "usage: Main <directory of .json files> | <file>.roster.json | scale [scale [seed]] | generate <dir> [scale [seed]]");
            System.exit(2);
        }
        if (args[0].equals("scale")) {
            double scale = args.length > 1 ? Double.parseDouble(args[1]) : 1.0;
            int seed = args.length > 2 ? Integer.parseInt(args[2]) : 1;
            System.out.print(ScaleReport.of(scale, seed));
            return;
        }
        if (args[0].equals("generate")) {
            generate(args);
            return;
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
        System.out.print(ModelReport.of(files));
    }

    /** Writes a generated dataset as pretty-printed JSON files, like the real data. */
    private static void generate(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: Main generate <dir> [scale [seed]]");
            System.exit(2);
        }
        double scale = args.length > 2 ? Double.parseDouble(args[2]) : 1.0;
        int seed = args.length > 3 ? Integer.parseInt(args[3]) : 1;
        Path dir = Path.of(args[1]);
        Files.createDirectories(dir);
        var json = new MinimalJsonSource();
        Map<String, DataNode> files =
                ScaleGenerator.generate(Shape.elevenSized().scaled(scale), seed);
        for (var e : files.entrySet()) {
            Files.writeString(dir.resolve(e.getKey()), json.writePretty(e.getValue()), StandardCharsets.UTF_8);
        }
        System.out.println("wrote " + files.size() + " files to " + dir);
    }
}
