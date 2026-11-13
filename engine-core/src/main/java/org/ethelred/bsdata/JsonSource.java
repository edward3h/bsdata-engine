/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

/** Parses JSON text into a {@link DataNode}. Implementations must not use reflection. */
public interface JsonSource {
    DataNode parse(String text);
}
