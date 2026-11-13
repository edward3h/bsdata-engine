/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.web;

import java.util.Map;
import java.util.TreeMap;
import org.ethelred.bsdata.DataNode;
import org.ethelred.bsdata.Linker;
import org.ethelred.bsdata.MinimalJsonSource;
import org.ethelred.bsdata.RosterReport;
import org.ethelred.bsdata.model.ModelReport;
import org.teavm.jso.JSBody;

public final class Main {
    private Main() {}

    /** Node entry point: reports id references across all JSON files in a directory (the last argument). */
    public static void main(String[] args) {
        String dir = lastArgument();
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

    @JSBody(
            params = "d",
            script = "return require('fs').readdirSync(d).filter(f => f.endsWith('.json')).sort().join('\\n');")
    private static native String listJson(String d);

    @JSBody(params = "p", script = "return require('fs').readFileSync(p, 'utf8');")
    private static native String readFile(String p);

    @JSBody(script = "return Date.now();")
    private static native double now();

    @JSBody(params = "s", script = "process.stdout.write(s);")
    private static native void log(String s);

    @JSBody(params = "s", script = "console.error(s);")
    private static native void logError(String s);
}
