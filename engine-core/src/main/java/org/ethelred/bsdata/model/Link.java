/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

/**
 * An entry link: a reference to a selection entry or group, with its own overlay in the body.
 *
 * @param targetIsGroup whether the target is a selection entry group
 */
public record Link(String id, String name, String targetId, boolean targetIsGroup, boolean hidden, Body body)
        implements Child {}
