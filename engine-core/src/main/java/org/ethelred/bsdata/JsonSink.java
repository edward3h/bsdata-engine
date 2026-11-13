/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

/** Writes a {@link DataNode} as JSON text. Implementations must not use reflection. */
public interface JsonSink {
    String write(DataNode node);
}
