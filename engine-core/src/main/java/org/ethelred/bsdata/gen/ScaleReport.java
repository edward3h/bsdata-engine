/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.gen;

import java.util.Map;
import org.ethelred.bsdata.DataNode;
import org.ethelred.bsdata.Linker;
import org.ethelred.bsdata.model.ModelReport;

/** Generates a dataset in memory and reports on it; the same text comes out on the JVM and in JavaScript. */
public final class ScaleReport {
    private ScaleReport() {}

    public static String of(double scale, int seed) {
        Map<String, DataNode> files =
                ScaleGenerator.generate(Shape.elevenSized().scaled(scale), seed);
        return of(files);
    }

    public static String of(Map<String, DataNode> files) {
        ShapeStats stats = ShapeStats.of(files);
        StringBuilder sb = new StringBuilder();
        sb.append("fingerprint ").append(ShapeStats.fingerprint(files)).append('\n');
        sb.append("objectsWithId ").append(stats.objectsWithId()).append('\n');
        stats.counts().forEach((k, v) -> sb.append(k).append(' ').append(v).append('\n'));
        sb.append(Linker.check(files).format(10));
        sb.append(ModelReport.of(files));
        return sb.toString();
    }
}
