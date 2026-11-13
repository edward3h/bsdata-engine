/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.web;

import org.ethelred.bsdata.Census;
import org.ethelred.bsdata.MinimalJsonSource;
import org.teavm.jso.JSBody;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        // Node entry point: node engine.js <file>
        String text = readFile(args.length > 0 ? args[0] : argv());
        double t0 = now();
        var tree = new MinimalJsonSource().parse(text);
        double t1 = now();
        var r = Census.of(tree);
        double t2 = now();
        log("objects=" + r.objects() + " ids=" + r.ids() + " targets=" + r.targets() + " unresolved=" + r.unresolved()
                + " parseMs=" + (t1 - t0) + " censusMs=" + (t2 - t1));
    }

    @JSBody(script = "return process.argv[process.argv.length - 1];")
    private static native String argv();

    @JSBody(params = "p", script = "return require('fs').readFileSync(p, 'utf8');")
    private static native String readFile(String p);

    @JSBody(script = "return Date.now();")
    private static native double now();

    @JSBody(params = "s", script = "console.log(s);")
    private static native void log(String s);
}
