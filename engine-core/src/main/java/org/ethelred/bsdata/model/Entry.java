/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import org.jspecify.annotations.Nullable;

/**
 * A selection entry or a selection entry group.
 *
 * @param type {@code unit}, {@code model} or {@code upgrade} for an entry; null for a group
 * @param defaultSelectionEntryId a group's default choice, if any
 */
public record Entry(
        String id,
        String name,
        boolean group,
        @Nullable String type,
        boolean hidden,
        boolean collective,
        @Nullable String defaultSelectionEntryId,
        Body body)
        implements Child {}
