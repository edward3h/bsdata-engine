/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import java.util.List;
import org.ethelred.bsdata.model.Parts.CategoryLink;
import org.ethelred.bsdata.model.Parts.Cost;
import org.ethelred.bsdata.model.Parts.InfoLink;
import org.ethelred.bsdata.model.Parts.Profile;
import org.ethelred.bsdata.model.Parts.Rule;

/** What an entry, a group or a link carries. A link's body overlays its target's. */
public record Body(
        List<Cost> costs,
        List<Profile> profiles,
        List<Rule> rules,
        List<InfoLink> infoLinks,
        List<CategoryLink> categoryLinks,
        List<Child> children,
        Raw raw) {}
