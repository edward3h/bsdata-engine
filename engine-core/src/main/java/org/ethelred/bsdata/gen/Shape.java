/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.gen;

/**
 * Target counts for a generated game system. {@link #elevenSized()} holds the figures measured on a real 46-file
 * system (counts of kinds of object only, no content); {@link #scaled(double)} shrinks or grows it.
 *
 * @param catalogues catalogue files besides the game system
 * @param libraries how many of the catalogues are libraries
 * @param depthCounts entries and groups at each nesting depth below the shared ones at the top (index 0 is depth 1)
 * @param topEntries shared entries at the top of files (not groups)
 * @param topGroups shared groups at the top of files
 */
public record Shape(
        int catalogues,
        int libraries,
        int topEntries,
        int topGroups,
        int[] depthCounts,
        double nestedGroupFraction,
        int rootLinks,
        int nestedLinks,
        int categoryEntries,
        int sharedProfiles,
        int sharedRules,
        int profiles,
        int rules,
        int infoLinks,
        int categoryLinks,
        int costs,
        int constraints,
        int modifiers,
        int modifierGroups,
        int conditions,
        int conditionGroups,
        int repeats,
        int associations) {

    /** Figures measured on a real 46-file system of about 104,000 objects with ids and 46 MB of JSON. */
    public static Shape elevenSized() {
        return new Shape(
                45,
                9,
                2699,
                332,
                new int[] {4275, 4844, 3052, 1124, 190, 40, 4},
                0.31,
                1830,
                16039,
                1796,
                334,
                103,
                12828,
                354,
                11317,
                14486,
                27238,
                26160,
                24870,
                2542,
                31904,
                10395,
                2813,
                480);
    }

    /** All counts multiplied by {@code factor}; the number of files stays, so small scales still have many files. */
    public Shape scaled(double factor) {
        int[] depth = new int[depthCounts.length];
        for (int i = 0; i < depth.length; i++) {
            depth[i] = s(depthCounts[i], factor);
        }
        return new Shape(
                catalogues,
                libraries,
                s(topEntries, factor),
                s(topGroups, factor),
                depth,
                nestedGroupFraction,
                s(rootLinks, factor),
                s(nestedLinks, factor),
                s(categoryEntries, factor),
                s(sharedProfiles, factor),
                s(sharedRules, factor),
                s(profiles, factor),
                s(rules, factor),
                s(infoLinks, factor),
                s(categoryLinks, factor),
                s(costs, factor),
                s(constraints, factor),
                s(modifiers, factor),
                s(modifierGroups, factor),
                s(conditions, factor),
                s(conditionGroups, factor),
                s(repeats, factor),
                s(associations, factor));
    }

    private static int s(int n, double factor) {
        return Math.max(1, (int) Math.round(n * factor));
    }
}
