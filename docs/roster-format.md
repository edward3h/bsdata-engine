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
             "profiles": [ ... ], "rules": [ ... ], "selections": [ selection ] }

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
- **`number`** is the quantity. A copy of a unit added twice is two selections
  with `number` 1, not one with 2 (the G1 test shows four separate selections).
- **`type`** is `unit`, `model` or `upgrade`, copied from the entry.
- **`from`** is `entry` for a selection taken directly from an entry's children
  and `group` for one chosen from a selection entry group. For `group`, there is
  an `entryGroupId` (path to the group) and a `group` text holding the group names
  joined by `::` (for example `Options::May exchange bolter for`).
- **Computed values are exported.** `costs` hold the values after modifiers (four
  copies of G1 export as 100, 100, 110, 110), and profile characteristics hold the
  modified text (T01a exports `D8+3`). The roster's `costs` equal the sum over all
  selections of each cost times its `number` (checked on the fixture).
  `rules` entries carry `id`, `name`, `description`, `hidden` and `page`.
- **Zero or empty values are omitted.** A unit whose only cost is 0 (UNIT A in the
  fixture has `pts` 0) has no `costs` key, and empty lists are left out entirely.
- **Catalogue names are copied from the data as is**, including leading
  whitespace (`catalogueName` of Thousand Sons begins with spaces).
- **Not present in exports** (as far as seen): cost limits, notes, custom names,
  attachment state for leaders and bodyguards (associations), and hidden or
  collapsed state.

## Open questions

- Where, if anywhere, NR stores which unit a leader is **attached** to. The
  fixture roster has no association field. The association tests in
  `nr-extension-tests` involve attaching in the UI; export a roster with an
  attachment to see.
- The meaning of cost limits and `costLimits` when a limit is set at list creation.
- Whether `.ros`/`.rosz` (XML) carry anything the JSON does not, such as the
  per-selection `publications` or `customName`. Comparing a `.ros` export of the
  same list would show this.
- How NR reads a file back (what it validates, and whether it recalculates costs).
