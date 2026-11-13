/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

/** Something that can sit under a catalogue or an entry: a selection entry, a group, or a link to one. */
public sealed interface Child permits Entry, Link {
    String id();

    String name();

    boolean hidden();

    Body body();
}
