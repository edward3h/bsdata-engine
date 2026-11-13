/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import java.util.List;

/** Where the text of the data files comes from: a directory, or files fetched over the network. */
public interface TextSource {
    /** File names, in a fixed order. */
    List<String> names();

    String read(String name);
}
