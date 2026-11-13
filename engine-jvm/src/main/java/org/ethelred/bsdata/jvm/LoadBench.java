/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.jvm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.ethelred.bsdata.MinimalJsonSource;
import org.ethelred.bsdata.model.GameData;
import org.ethelred.bsdata.model.LoadStrategies;
import org.ethelred.bsdata.model.ModelReport;
import org.ethelred.bsdata.model.TextSource;

/** Loads a directory of data files into the typed model and reports time and memory: {@code load <dir> [strategy]}. */
final class LoadBench {
    private LoadBench() {}

    static void run(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: load <directory> [tree|file|stream]");
            System.exit(2);
        }
        Path dir = Path.of(args[1]);
        String strategy = args.length > 2 ? args[2] : "stream";
        TextSource source = new TextSource() {
            @Override
            public List<String> names() {
                try (Stream<Path> list = Files.list(dir)) {
                    return list.map(p -> String.valueOf(p.getFileName()))
                            .filter(n -> n.endsWith(".json"))
                            .sorted()
                            .toList();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public String read(String name) {
                try {
                    return Files.readString(dir.resolve(name), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        long t0 = System.nanoTime();
        GameData data = LoadStrategies.load(strategy, source, new MinimalJsonSource());
        long t1 = System.nanoTime();
        double peak = peakHeapMb();
        System.gc();
        double retained = usedMb();
        System.out.print(ModelReport.of(data));
        System.out.println("strategy=" + strategy + " loadMs=" + (t1 - t0) / 1_000_000 + " peakHeapMB="
                + Math.round(peak) + " retainedMB=" + Math.round(retained));
    }

    private static double usedMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / 1048576.0;
    }

    /** The largest heap in use at any time, summed over the heap pools (an upper bound on the true peak). */
    private static double peakHeapMb() {
        long sum = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                sum += pool.getPeakUsage().getUsed();
            }
        }
        return sum / 1048576.0;
    }
}
