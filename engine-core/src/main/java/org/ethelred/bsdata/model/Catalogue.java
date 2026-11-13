/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import java.util.List;
import org.ethelred.bsdata.model.Parts.CatalogueLink;
import org.ethelred.bsdata.model.Parts.Category;
import org.ethelred.bsdata.model.Parts.CostType;
import org.ethelred.bsdata.model.Parts.ForceEntry;
import org.ethelred.bsdata.model.Parts.Profile;
import org.ethelred.bsdata.model.Parts.ProfileType;
import org.ethelred.bsdata.model.Parts.Rule;

/**
 * A game system or a catalogue file.
 *
 * @param rootEntries entries and groups defined at the top (not shared)
 * @param sharedEntries shared entries and groups, targets of links
 * @param rootLinks the entry links at the top, which is how units are usually added
 */
public record Catalogue(
        String file,
        String id,
        String name,
        boolean gameSystem,
        boolean library,
        int revision,
        List<CatalogueLink> catalogueLinks,
        List<CostType> costTypes,
        List<ProfileType> profileTypes,
        List<Category> categories,
        List<ForceEntry> forceEntries,
        List<Entry> rootEntries,
        List<Entry> sharedEntries,
        List<Link> rootLinks,
        List<Profile> sharedProfiles,
        List<Rule> sharedRules) {}
