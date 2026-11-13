# New Recruit roster file format (JSON export)

Observed from rosters exported with Export, then json, on newrecruit.eu, for the
synthetic test system and for Horus Heresy 3rd Edition. Nothing here comes from
NR's source. The file shape mirrors BattleScribe's `.ros` XML (`xmlns` is
`rosterSchema`), with child lists as arrays. Fixtures are in
`edward3h/nr-extension-tests/rosters/`. Read and write support is `Roster` in
`engine-core`; a round trip reproduces the exported file exactly.

## Shape

```
{"roster": {
  "id", "name",
  "battleScribeVersion": 2.03, "generatedBy": "https://newrecruit.eu",
  "gameSystemId", "gameSystemName", "gameSystemRevision", "xmlns",
  "costs":  [ {"name", "typeId", "value"} ],
  "forces": [ force ]
}}
```

```
force:  { "id", "name", "entryId",
          "catalogueId", "catalogueRevision", "catalogueName",
          "categories": [ category ], "selections": [ selection ],
          "profiles": [ ... ], "rules": [ ... ] }

selection: { "id", "name", "entryId", "number", "type", "from",
             "entryGroupId"?, "group"?,
             "costs": [ cost ], "categories": [ category ],
             "associations"?: [ association ], "incomingAssociations"?: [ association ],
             "profiles": [ ... ], "rules": [ ... ], "selections": [ selection ] }

association (outgoing):  { "type": "outgoing", "to": <selection id>,
                           "associationId", "name", "action" }
association (incoming):  { "type": "incoming", "from": <selection id>, "amount",
                           "associationId", "name", "action" }

category: { "id", "entryId", "name", "primary" }
```

## Observations

- **Ids.** Nodes of the roster (roster, force, selection, and the force's category
  nodes) have short random base-36 ids such as `p2ylr5i`. Ids of catalogue
  objects (`typeId`, `catalogueId`, `gameSystemId`, a category's `entryId`) are the
  data's ids.
- **`entryId` is a path.** It is a list of ids separated by `::`. A top-level
  unit is the id of its entry link in the catalogue, then the id of the link's
  target (`b524-452b-af85-2fd5::01a4-413c-7bd0-8b5e`: link, then entry). A direct
  child of that unit keeps the link id and replaces the target with its own id (an
  upgrade in the unit is `b524-452b-af85-2fd5::dc46-8ea1-6b6c-9369`); deeper
  children, for example inside a selection entry group, add one more segment
  (three segments in the Horus Heresy export). A force's `entryId` is the force
  entry's id with no path. The first segment is the catalogue-level link, so the
  same unit linked from two catalogues has two different ids.
- **`number`** is the **total** quantity, not per parent: a Gun entered as 2 under a
  Trooper entered as 3 exports with `number` 6. A unit added twice is two
  selections with `number` 1, not one with 2 (the G1 test shows four separate
  selections).
- **`type`** is `unit`, `model` or `upgrade`, copied from the entry.
- **`from`** is `entry` for a selection taken directly from an entry's children
  and `group` for one chosen from a selection entry group. For `group`, there is
  an `entryGroupId` (path to the group) and a `group` text holding the group names
  joined by `::` (for example `Options::May exchange bolter for`).
- **Computed values are exported.** `costs` hold the values after modifiers (four
  copies of G1 export as 100, 100, 110, 110), and profile characteristics hold the
  modified text (T01a exports `D8+3`). A selection's exported cost is already its
  total (the cost of one times its total `number`: Trooper 5 pts, `number` 3,
  cost 15), so the roster's `costs` are the plain sum over all selections
  (5 + 3 + 10 + 3 + 15 + 12 + 2 = 50 in the M1 fixture). `rules` entries carry
  `id`, `name`, `description`, `hidden` and `page`.
- **Zero or empty values are omitted.** A unit whose only cost is 0 (UNIT A in the
  fixture has `pts` 0) has no `costs` key, and empty lists are left out entirely.
- **Catalogue names are copied from the data as is**, including leading
  whitespace (one catalogue name in the real data began with spaces).
- **Attachment (associations) is stored on both selections.** Ticking "Leading" on
  leader E for bodyguard F gave E `associations: [{"type": "outgoing", "to": F's
  selection id, "associationId": "Leading-unit", "name": "Leading", "action":
  "group"}]` and F `incomingAssociations: [{"type": "incoming", "from": E's
  selection id, "amount": 1, "associationId": "Leading-unit", "name": "Leading",
  "action": "group"}]`. The keys are absent when nothing is attached, and detaching
  is expected to remove them (the tests show the error message going away; the
  export was not re-checked). `name` and `action` come from the data's association
  (`Leading`, `group`); `associationId` here is `Leading-unit`, which fits
  `<name>-<childId>` (the data's association has `childId: "unit"` and no id of its
  own), from a single sample. Two leaders of the same type, only one attached,
  export correctly: only the attached one has the key. The target is addressed by
  the roster selection id, not an entry id. Not yet seen: `amount` other than 1, a
  leader attached to several units, and the `traverseAssociationGroup` constraint's
  view of this state (it ran in NR and counts selections in the attached group).
- **Not present in exports** (as far as seen): cost limits, notes, custom names,
  and hidden or collapsed state.

## Open questions

- How the `associationId` is built when the data's association has an id of its
  own, and what `amount` means beyond 1.
- The meaning of cost limits and `costLimits` when a limit is set at list creation.
- Whether `.ros`/`.rosz` (XML) carry anything the JSON does not, such as the
  per-selection `publications` or `customName`. Comparing a `.ros` export of the
  same list would show this.
- How NR reads a file back (what it validates, and whether it recalculates costs).
