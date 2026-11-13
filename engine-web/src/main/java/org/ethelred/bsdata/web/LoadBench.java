/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.web;

import java.util.ArrayList;
import java.util.List;
import org.ethelred.bsdata.MinimalJsonSource;
import org.ethelred.bsdata.model.GameData;
import org.ethelred.bsdata.model.LoadStrategies;
import org.ethelred.bsdata.model.ModelReport;
import org.ethelred.bsdata.model.TextSource;
import org.teavm.jso.JSBody;

/** Loads a directory into the typed model in Node and reports time and memory ({@code load:<dir>:<strategy>}). */
final class LoadBench {
    private LoadBench() {}

    static void run(String dir, String strategy) {
        TextSource source = new TextSource() {
            @Override
            public List<String> names() {
                List<String> out = new ArrayList<>();
                for (String n : Main.listFiles(dir)) {
                    if (!n.isEmpty()) {
                        out.add(n);
                    }
                }
                return out;
            }

            @Override
            public String read(String name) {
                sample();
                return Main.read(dir + "/" + name);
            }
        };
        double t0 = Main.nowMs();
        GameData data = LoadStrategies.load(strategy, source, new MinimalJsonSource());
        double t1 = Main.nowMs();
        Main.print(ModelReport.of(data));
        Main.print("strategy=" + strategy + " loadMs=" + Math.round(t1 - t0) + " " + memory() + "\n");
    }

    private static double maxUsed;
    private static double maxTotal;

    /** Records the JavaScript heap now; called as each file is read, so the largest values seen are kept. */
    static void sample() {
        maxUsed = Math.max(maxUsed, heapUsed());
        maxTotal = Math.max(maxTotal, heapTotal());
    }

    private static String memory() {
        sample();
        return "sampledPeakUsedMB=" + Math.round(maxUsed / 1048576) + " sampledPeakTotalMB="
                + Math.round(maxTotal / 1048576) + " " + extra();
    }

    @JSBody(
            script =
                    "if (typeof process !== 'undefined' && process.memoryUsage) { return process.memoryUsage().heapUsed; }"
                            + " return performance.memory ? performance.memory.usedJSHeapSize : 0;")
    private static native double heapUsed();

    @JSBody(
            script =
                    "if (typeof process !== 'undefined' && process.memoryUsage) { return process.memoryUsage().heapTotal; }"
                            + " return performance.memory ? performance.memory.totalJSHeapSize : 0;")
    private static native double heapTotal();

    /** Node only: peak resident size, and the heap after a forced collection (run node with --expose-gc). */
    @JSBody(
            script = "if (typeof process === 'undefined' || !process.resourceUsage) { return ''; }"
                    + " var peak = Math.round(process.resourceUsage().maxRSS / 1024); if (global.gc) { global.gc(); }"
                    + " var m = process.memoryUsage(); return 'peakRssMB=' + peak + ' retainedHeapMB='"
                    + " + Math.round(m.heapUsed / 1048576) + ' rssMB=' + Math.round(m.rss / 1048576);")
    private static native String extra();
}
