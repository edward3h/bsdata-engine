/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ethelred.bsdata.TestData;
import org.ethelred.bsdata.gen.ScaleGenerator;
import org.ethelred.bsdata.gen.Shape;
import org.ethelred.bsdata.model.Catalogue;
import org.ethelred.bsdata.model.GameData;
import org.ethelred.bsdata.roster.RosterModel;
import org.junit.jupiter.api.Test;

/** M6's check on generated data: every unit of every catalogue can be added, instantiated and validated. */
class ScaleInstantiationTest {
    @Test
    void everyUnitInstantiatesAndValidatesWithoutErrors() {
        GameData data =
                GameData.load(ScaleGenerator.generate(Shape.elevenSized().scaled(0.05), 5));
        var forceEntry = TestData.required(data.gameSystem()).forceEntries().get(0);
        int units = 0;
        int selections = 0;
        for (Catalogue cat : data.catalogues()) {
            if (cat.gameSystem() || cat.library()) {
                continue;
            }
            RosterModel model = new RosterModel(data, cat.name());
            var force = model.addForce(forceEntry, cat);
            Instantiator inst = new Instantiator(model);
            inst.populateForce(force);
            for (var link : cat.rootLinks()) {
                if (inst.addUnit(force, link) != null) {
                    units++;
                }
            }
            View view = new View(model);
            new Validator(model, view).validate();
            selections += model.selections().size();
        }
        assertTrue(units > 50, "units added: " + units);
        assertTrue(selections >= units, "selections: " + selections);
    }
}
