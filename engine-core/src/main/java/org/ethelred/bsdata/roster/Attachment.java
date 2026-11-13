/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.roster;

/**
 * A leader attached to a unit (an association the user ticked). It is kept on both selections.
 *
 * @param leader the selection that holds the association
 * @param target the selection it is attached to
 * @param associationId the association's id: its own {@code id}, or {@code <name>-<childId>} when the data gives none
 */
public record Attachment(Selection leader, Selection target, String associationId) {}
