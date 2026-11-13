/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.web;

import java.util.Map;
import java.util.TreeMap;
import org.ethelred.bsdata.DataNode;
import org.ethelred.bsdata.Linker;
import org.ethelred.bsdata.MinimalJsonSource;
import org.ethelred.bsdata.RosterReport;
import org.ethelred.bsdata.gen.ScaleReport;
import org.ethelred.bsdata.model.ModelReport;
import org.teavm.jso.JSBody;

public final class Main {
    private Main() {}

    /** Node entry point: reports id references across all JSON files in a directory (the last argument). */
    public static void main(String[] args) {
        String dir = args.length > 0 ? args[args.length - 1] : lastArgument();
        if (dir.startsWith("load:")) {
            String[] parts = dir.split(":", 3);
            LoadBench.run(parts[1], parts.length > 2 ? parts[2] : "stream");
            return;
        }
        if (dir.startsWith("scale:")) {
            String[] parts = dir.split(":");
            log(ScaleReport.of(Double.parseDouble(parts[1]), parts.length > 2 ? Integer.parseInt(parts[2]) : 1));
            return;
        }
        if (dir.endsWith(".roster.json")) {
            log(RosterReport.of(readFile(dir)));
            return;
        }
        var json = new MinimalJsonSource();
        Map<String, DataNode> files = new TreeMap<>();
        double t0 = now();
        for (String name : listJson(dir).split("\n")) {
            if (!name.isEmpty()) {
                files.put(name, json.parse(readFile(dir + "/" + name)));
            }
        }
        double t1 = now();
        String report = Linker.check(files).format(15);
        double t2 = now();
        log(report);
        log(ModelReport.of(files));
        logError("loadMs=" + (t1 - t0) + " linkMs=" + (t2 - t1));
    }

    @JSBody(script = "return process.argv[process.argv.length - 1];")
    private static native String lastArgument();

    static String[] listFiles(String dir) {
        return listJson(dir).split("\n");
    }

    @JSBody(
            params = "d",
            script =
                    "if (typeof window !== 'undefined') { var x = new XMLHttpRequest(); x.open('GET', d + '/manifest.txt', false);"
                            + " x.send(); return x.responseText.trim(); }"
                            + " return require('fs').readdirSync(d).filter(f => f.endsWith('.json')).sort().join('\\n');")
    private static native String listJson(String d);

    static String read(String path) {
        return readFile(path);
    }

    static double nowMs() {
        return now();
    }

    @JSBody(
            params = "p",
            script =
                    "if (typeof window !== 'undefined') { var x = new XMLHttpRequest(); x.open('GET', p, false); x.send();"
                            + " return x.responseText; }"
                            + " return require('fs').readFileSync(p, 'utf8');")
    private static native String readFile(String p);

    @JSBody(script = "return Date.now();")
    private static native double now();

    @JSBody(
            params = "s",
            script =
                    "if (typeof process !== 'undefined' && process.stdout) { process.stdout.write(s); } else { console.log(s); }")
    private static native void log(String s);

    static void print(String s) {
        log(s);
    }

    @JSBody(params = "s", script = "console.error(s);")
    private static native void logError(String s);
}
