/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

/** Reads a roster file, writes it back, and summarises the result. Used by the command-line checkers. */
public final class RosterReport {
    private RosterReport() {}

    public static String of(String text) {
        MinimalJsonSource json = new MinimalJsonSource();
        DataNode parsed = json.parse(text);
        Roster roster = Roster.read(parsed);
        int selections = 0;
        for (Roster.Force f : roster.forces()) {
            for (Roster.Selection s : f.selections()) {
                selections += count(s);
            }
        }
        boolean roundTrip = parsed.equals(json.parse(json.write(roster.toNode())));
        StringBuilder sb = new StringBuilder();
        sb.append("roster ")
                .append(roster.name())
                .append(" (")
                .append(roster.gameSystemName())
                .append(")\n");
        sb.append("forces=").append(roster.forces().size());
        sb.append(" selections=").append(selections);
        for (Roster.Cost c : roster.costs()) {
            sb.append(' ').append(c.name()).append('=').append(c.value());
        }
        sb.append(" roundTrip=").append(roundTrip).append('\n');
        return sb.toString();
    }

    private static int count(Roster.Selection s) {
        int n = 1;
        for (Roster.Selection child : s.selections()) {
            n += count(child);
        }
        return n;
    }
}
