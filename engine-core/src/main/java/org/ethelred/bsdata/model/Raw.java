/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import java.util.List;
import org.ethelred.bsdata.DataNode;

/** Parts of an object that M1 does not interpret yet, kept as parsed nodes (typed by M2 and M3). */
public record Raw(
        List<DataNode> constraints,
        List<DataNode> modifiers,
        List<DataNode> modifierGroups,
        List<DataNode> associations) {}
