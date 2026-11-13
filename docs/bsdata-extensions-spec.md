# BSData format extensions: draft specification

Status: **draft**. First written 2026-11-10; revised 2026-11-12 with
evidence from `wh40k-11e`, the NR editor's own engine notes, and a first run of
the scratch tests on newrecruit.eu (marked **Observed in NR**).

## 1. Purpose and scope

This document describes the extensions to the BattleScribe data format that
appear in current BSData repositories and are supported by New Recruit (NR),
but not by the original BattleScribe schema or by older open-source readers
such as BlueScribe. It is intended as a starting point for an open-source
engine.

It is **not** an authoritative description of NR's behaviour. NR's engine
(the `nr-shared` submodule of `giloushaker/nr-editor`) is not publicly
available. Meanings below come from three kinds of evidence: the editor's
own engine notes (marked **Confirmed (NR notes)**; written by the NR authors
as documentation, not code, so accuracy is unverified), data usage, and
community rules text.

## 2. Sources and method

| Source | Used for |
|---|---|
| `Catalogue.xsd` from `30K-4-All` | Baseline: what the original schema allows |
| `giloushaker/nr-editor`, `assets/editor/fields.ts`, `components/catalogue/right_panel/fields/{Modifier,ComplexQuery,ConditionGroup}.vue` | The list of extended types and attributes, plus UI wording |
| `giloushaker/nr-editor`, `assets/editor/mcp_tools.ts` (engine notes, roughly lines 615-745) | Evaluation order, scopes, queries, repeats, constraints, modifier semantics |
| NR documentation site, `newrecruit-docs.pages.dev` (`guide/concepts/modifiers`, `guide/publishing.html`) | Official wording on modifiers, and how systems are loaded from GitHub |
| `BSData/horus-heresy-3rd-edition` (JSON, 38 files; HEAD as of 2026-11-10; the organisation was later removed from GitHub) | Usage counts and patterns ("HH3") |
| `BSData/wh40k-11e` (JSON, 46 files; HEAD `7eb33b2`, 2026-11-10; no longer available) | Usage counts and patterns ("11e") |
| Git history of both repos | When extensions appeared; commit text |
| BlueScribe (`BlueWinds/bluescribe`, last commit 2023-08-09) | Observed failures on the XML-era HH3 data |

Confidence labels used below (**Observed in NR** means the behaviour was seen
in New Recruit's roster builder on 2026-11-12, using
`https://github.com/edward3h/nr-extension-tests`):

- **Confirmed**: stated in a source or directly observed.
- **Likely**: strong inference from several consistent observations.
- **Unknown**: no good evidence.

## 3. File format

- Current BSData repositories such as `horus-heresy-3rd-edition` and
  `wh40k-11e` store data as `.json`, not `.cat`/`.gst`. Each file mirrors the
  XML schema, with `xmlns`, `battleScribeVersion` and `type` fields kept, and
  XML child lists turned into arrays.
- HH3 migrated from XML to JSON in commit `44d7f4e` (2026-07-14, "Migrate to
  JSON"). The last XML revision is its parent `44d7f4e^`.
- The HH3 extensions existed in the XML era, so they are not an artefact of
  the migration. Confirmed for `position` (introduced 2025-07-23 in commit
  `0331d41`, as an XML attribute).
- `wh40k-11e` has been JSON from its first commit (2026-05-12, 934 commits
  to date), so it offers no XML-era history to date its extensions by.
- An engine should therefore read both XML and JSON. Original BattleScribe
  tools read only XML, and BlueScribe additionally ignores `.json`.

## 4. How heavily the extensions are used

| | HH3 | 11e |
|---|---|---|
| Modifiers in total | 17,188 | 24,870 |
| Extended modifier **types** (`replace`, `prepend`, `ceil`, `floor`, `divide`, `multiply`) | 2,922 (17%) | 393 (1.6%) |
| Modifiers with `affects` | 3 | 2,673 (11%) |
| Modifiers with `scope` | 31 | 2,104 |
| Modifiers with `join` | 73 | 1,867 |
| Modifiers with `skipIfPresent` | 0 | 617 |
| Modifiers with `position` | 14 | 154 |
| `associations` blocks | 0 | 479 |
| `localConditionGroups` | 0 | 413 |
| `before` conditions | 0 | 413 |

HH3 leans on extended modifier **types**. 11e leans on extended modifier
**attributes**, especially `affects` and `scope`, which let a modifier edit
profiles that belong to other entries. An engine that supports only the new
types would handle HH3 reasonably and 11e badly.

## 5. Modifier extensions

### 5.1 Modifier types

The original schema allows `set`, `increment`, `decrement`, `append`, `add`,
`remove`, `set-primary` and `unset-primary`. NR's editor additionally lists
the types below. Meanings in the last column are from the NR notes unless
marked otherwise.

| Type | HH3 | 11e | Meaning |
|---|---|---|---|
| `replace` | 754 | 163 | Substitute `arg` with `value` in a text field. An empty `arg` sets the value only if the field is empty |
| `prepend` | 242 | 0 | Add `value` before the text, using `join` (default a space) |
| `ceil` | 1,896 | 8 | Values above `value` become `value` |
| `floor` | 30 | 37 | Values below `value` become `value` |
| `multiply` | 0 | 17 | Arithmetic stage; multiply by `value` (Observed in NR) |
| `divide` | 0 | 168 | Arithmetic stage; divide by `value`, not rounded (Observed in NR) |
| `modulo` | 0 | 0 | Arithmetic stage; remainder after dividing by `value` (Observed in NR) |
| `power` | 0 | 0 | Arithmetic stage; the number raised to `value` (Observed in NR) |
| `exponent` | 0 | 0 | Arithmetic stage; behaves exactly like `multiply`, on numbers and on dice or `N+` strings (Observed in NR) |
| `triangular` | 0 | 0 | Arithmetic stage; adds `value`, and with repeats adds a growing amount (see 5.6; Observed in NR) |
| `cumulative-add`, `cumulative-multiply`, `cumulative-power` | 0 | 0 | Last stage, with `replace`; formulas in 5.6 (Observed in NR) |

`unset-primary` is in the original schema. HH3 does not use it; 11e uses it
4 times (on `category`, conditionally removing primary status).

BlueScribe fails on `replace` and `prepend`, and stops at the first unknown
type, so it would probably fail on `ceil`, `floor`, `divide` and `multiply`
too.

### 5.2 `replace`

- `arg` is the text to find; `value` is the replacement. 754 of 754 in HH3 and
  310 `arg`s overall in 11e carry an `arg`.
- **A missing `value` means "replace with nothing".** 129 11e modifiers have
  no `value`; 68 are `replace` on `A` and 52 on `D` with `arg: "+0"`, which
  deletes a leftover "+0" suffix. Confirmed from data.
- **An empty `arg` sets the value only when the field is empty.** The editor
  tooltip says so, and 11e uses it. Example (Serpent Shield, field `InSv`):
  three modifiers with `arg: ""`, `arg: "-"` and `arg: "6+"`, all with
  `value: "5+"`. Together they mean "whatever the invulnerable save is, make
  it 5+". Confirmed.
- Observed targets: entry or link `name`, characteristic `Type`, `Special
  Rules` (HH3); weapon characteristics `A` and `D`, `Keywords`, `annotation`,
  `InSv` (11e).
- HH3 patterns: `arg: "X"`, `value: 1` turns "Line (X)" into "Line (1)";
  `arg: "Sergeant"`, `value: "Sergeant, Champion"`.
- `replace` runs in the last stage, after arithmetic and clamps (5.12), which
  is why the cleanup in the worked example (section 8) works whatever the array order.
- **Observed in NR:**
  - `arg` is matched **literally**, not as a pattern: `arg: "e."` on `Heavy` changed nothing.
  - **Every match** is replaced: `Heavy, Heavy` with `Heavy` -> `Light` gave `Light, Light`.
  - Empty `arg` on an empty field sets the value (`5+`); on a non-empty field (`6+`) it leaves it unchanged.
  - A missing `value` deletes the matched text: `D6+0` with `arg: "+0"` gave `D6`.

### 5.3 `append` and `prepend`

- HH3 targets `name`. 11e targets `Keywords` and `annotation` on weapon
  profiles (8,841 `append` modifiers, 35% of all 11e modifiers).
- `append` adds `value` after the current text; `prepend` before it.
- `join` is the separator between existing text and `value`. In 11e:
  - `", "` (305) and `", "` (comma plus non-breaking space; 834).
    Both are used, so the separator must be taken literally.
  - `""` (230): no separator.
  - `"\n\n"` on `set` (25): joins paragraphs.
- `join` also appears on `increment`, `decrement`, `add`, `set` and `floor`
  (for example 136 `increment` modifiers). Its meaning there is Unknown; it
  may be a stale default carried by the editor.
- The default separator when `join` is absent is a single space
  (Confirmed (NR notes); the editor writes no attribute for that default).
  11e supplies `join` almost everywhere.
- **Observed in NR** (`Keywords = "A"`, append/prepend `B`): no `join` gives
  `A B`; `", "` gives `A, B`; `""` gives `AB`; `prepend` with `", "` gives `B, A`; a
  newline `join` is kept (`A\nB`).
- Appending to an **empty** field adds no leading separator: `AFF-ALL`, not
  `, AFF-ALL` (observed on empty `Keywords`).

### 5.4 `skipIfPresent` (new in 11e)

- 617 modifiers, mostly `append` on `Keywords` and `annotation`.
- Meaning (Confirmed (NR notes)): `append` and `prepend` skip when the
  `skipIfPresent` text is already in the value. The editor shows "(always
  applies)" when it is empty. It is always the same text as `value` in the
  data. Examples: `append Keywords "Ignores Cover"` with `skipIfPresent:
  "Ignores Cover"`.
- Used to stop duplicate keywords when several upgrades or rules add the same
  one.
- **Observed in NR:** honoured on `append` (`Ignores Cover` stays single
  with it, doubles without it). **Ignored** on `replace` (`Sustained Hits 1`
  became `Sustained Hits 2` despite `skipIfPresent: "Sustained Hits"`) and
  on `increment` (`3` + 2 gave `5` despite `skipIfPresent: "3"`).
- On `replace` it is not described, so it was ignored, as predicted. In the
  data a `replace` of a keyword ("Ability 1" to "Ability 2") carried
  `skipIfPresent` with a value that would always skip if honoured, while the two
  `append` modifiers beside it used it correctly, so it was probably copied across.
- 11e also has it on `increment S` (12 times). It is ignored there, so those
  are inert (Observed in NR).

### 5.5 `ceil` and `floor`

- Meaning (Confirmed (NR notes)): "floor X: values below X become X; ceil X:
  values above X become X". They run after arithmetic (5.12).
- **HH3:** `ceil` on LD, IN, WP, CL; `floor` on SAV. Always inside a
  `modifierGroup`, next to an `increment` on the same field. Example
  (Saboteur): `increment LD +1`, then `ceil LD 10`.
- **11e:** `floor` on WS (14) and BS (13), with a few on OC, Sv, M and
  cost/constraint IDs; `ceil` on M (7) and OC (1). Examples: `floor WS 2`
  on a weapon profile (Knight Diabolus); `ceil M 9` under the condition
  "Boarding Actions" (Interceptor Squad); `ceil OC "0"` (value given as a
  string).
- **Values like `"3+"`:** WS values in 11e are strings (`"3+"` 969 times,
  `"2+"` 712 times). The NR notes say a modifier on a characteristic has its
  result "re-formatted by the characteristic's rules", so the clamp appears
  to read the number from `"3+"` and write it back in the same form. The
  details are Unknown.
- **Observed in NR** on `WS = "3+"`: `floor 2` -> `3+`; `floor 4` -> `4+`;
  `ceil 2` -> `2+`; `ceil 4` -> `3+`. The engine reads the number from `N+`
  and writes the result back in the same form. Resolves the earlier question.
- `value` may be a number or a numeric string.
- Because the engine applies types in a fixed order (5.12), a clamp always
  runs after the `increment` it sits beside, whatever the array order.

### 5.6 Arithmetic types

- `divide` (168 uses in 11e): all on the field `Enhancements`, conditioned on
  counts in the roster. Example: divide by 2 when exactly 2 of a given
  selection are in the roster, by 3 when more than 2 (Camouflaged Snipers).
- `multiply` (17 uses in 11e): on weapon `A`, cost type `pts` and some
  constraint IDs. Example: multiply by 2 when a "Houndpack Lance" is in the
  force (War Dog models).
- **Single application, observed in NR** on a field of `5`: `modulo 3` gives
  `2`; `multiply 3` gives `15`; `divide 2` gives `2.5` (no rounding); `power 2`
  gives `25`; `power 3` gives `125`; `power 0` gives `1`; `exponent 2` gives `10`
  and `exponent 3` gives `15` (so `exponent` is multiplication); `triangular 3`
  gives `8` (so a single `triangular` adds its value).
- **With repeats.** For most types, `k` repetitions equal applying the single
  rule `k` times. `triangular` and the `cumulative-*` types are the exceptions.
  Fitted to 64 observed readings (base `n`, value `v`; all matched; 5.12 explains
  how `k` arises):

  | Type | Result after k repetitions |
  |---|---|
  | `increment` | n + v*k |
  | `decrement` | n - v*k |
  | `multiply` | n * v^k |
  | `divide` | n / v^k |
  | `exponent` | n * v^k (same as `multiply`) |
  | `power` | n^(v^k) |
  | `triangular` | n + v*k(k+1)/2 |
  | `cumulative-add` | n + v*(k+1)/2 |
  | `cumulative-multiply` | n * (v + v^2 + ... + v^k) |
  | `cumulative-power` | n * (1 + v + ... + v^(k-1)) / k |

  With k = 0 the modifier is disabled and the value is unchanged. With k = 1
  `cumulative-add` reduces to n + v, `cumulative-multiply` to n*v and
  `cumulative-power` to n. `triangular` adds v, 2v, 3v, ... on successive
  repetitions, hence the name. The `cumulative-*` forms are unusual (an
  average for add and power, a sum for multiply), so treat the formulas as
  observed behaviour and not as an intended design.
- Results are not rounded: `11.666666666666666` was shown unchanged.
- **What counts as a number** (Observed in NR, 124 profiles on odd values): a run of
  digits, with a decimal part if a point sits between digits (`2.5` is one number),
  and with a minus sign if one comes directly before the digits **wherever it is**:
  `D6-3` has the numbers 6 and -3, so `increment 2` gives `D8-1`, `floor 5` gives
  `D65` and `power 2` gives `D369`. `1e3` has two numbers (1 and 3). A number the
  operation leaves unchanged keeps its original text (`floor 5` on `007` stays
  `007`). An empty value counts as 0 for arithmetic (`increment 2` gives `2`) but
  `floor` and `ceil` leave it empty. Text with no digits is left alone.
- **On compound strings** (no `position`) every number is changed: `D6+1` with `multiply 2` or
  `exponent 2` gives `D12+2`; `power 2` gives `D36+1`; `triangular 2` gives `D8+3`;
  `D6+5` with `modulo 2` gives `D0+1`. `N+` strings behave the same (`3+` gives `6+`, `9+`, `5+`).
- **Edge cases.** The `cumulative-*` formulas held for 42 further readings with
  bases 5, -3 and 2.5, values 2, 3, 0.5, -2, 1 and 0, and k from 2 to 10.
- **Costs** (modifiers on `pts`, 100 pts base): `divide 3` shows `33.33 pts`,
  `divide 7` shows `14.29 pts`, `multiply 2` shows `200`, `floor 150` shows
  `150`, `ceil 60` shows `60`. Costs are shown to two decimal places and are
  not rounded to whole numbers.

### 5.7 `position`

- **NR notes and docs:** "On a string value (a characteristic like "D6+1"),
  the arithmetic edits the number at "position" (0 = first number)", and "A
  `position` can pick which number to change inside a string value, for example
  the `3` in `D6+3`."
- **Observed in NR** (`increment A +2` on `D6+1`):

  | `position` | Result | Reading |
  |---|---|---|
  | unset | `D8+3` | every number in the string |
  | `0` | `D8+3` | **every number, same as unset** (not "first") |
  | `-1` | `D6+3` | last number only |
  | `-2` | `D8+1` | second-to-last, which is the first of two |

  So `0` behaves like "unset" (all numbers), contrary to the notes. A likely
  cause is a truthiness check on `position`, so a zero is treated as absent.
  Negative values index from the end, Python-style. **Positive values are
  1-based**: on `D6+1+2` (numbers 6, 1, 2), `increment 10` at position 1, 2 and
  3 changed the first, second and third number. Position -3 changed the first;
  an out-of-range position (-4) changed nothing. On a plain number such as `3`
  all variants give the same result (`5`).
- `position` applies to **clamps** and to `multiply`/`divide` as well: on `D6+1`,
  `ceil 4` gives `D4+1` (every number) but `ceil 4` at `-1` leaves `D6+1`; on
  `D6+4`, `divide 2` gives `D3+2`, at `-1` gives `D6+2`, and `multiply 2` at `-1`
  gives `D6+8`. Without `position`, clamps and arithmetic act on every number.
- The NR notes say `0 = first number`; in practice `0` means every number, and
  `1` is the first. Treat the notes as wrong on this point.
- **Consequences for the data:**
  - HH3's 14 uses of `position: 0` on a plain WP value such as `11` are
    inert. They do no harm.
  - 11e uses `-1` (147 uses), mostly on `increment`/`decrement` of weapon `A`
    and `D`, so that only the trailing number of a dice expression changes.
    See the worked example (section 8), which reproduced as `D6+2`.
  - Without `position`, `increment A +2` on `D6` gives `D8`, a dice-size
    change, which is why the data first appends `+0`.
- `position -1` on an `append` of `Keywords` has no effect (`A, B` as without
  it), so the `Keywords` uses in the data (12) are inert. Observed in NR.
- `""` (empty string) is probably the same as absent.
- Withdrawn: my earlier reading of `position` as an ordering key. The engine
  orders modifiers by type (5.12), confirmed by the T04 tests.
- Not in the baseline `Catalogue.xsd`.

### 5.8 `affects` (relative targeting)

- Meaning (Confirmed (NR notes)): "Relative modifiers ('affects') run on the
  targets they name rather than on their holder." HH3 uses it 3 times; 11e
  uses it 2,673 times. For example, an upgrade can add a keyword to all of a
  unit's ranged weapons.
- The editor builds the path from checkboxes and a "Filter By" field
  (`ComplexQuery.vue`), so the grammar is:
  ```
  [self.][entries.][forces.][group.][recursive.][<filter>.]<target>
  ```
  | Part | Editor label | Meaning |
  |---|---|---|
  | `self` | "Affect Self" / "Affect Scope" (or "Affect Associated Nodes" for associations) | Include the node the scope resolves to |
  | `entries` | "Affect child Selections" | Also affect child selections |
  | `forces` | "Affect child Forces" | Also affect child forces |
  | `group` | "Affect group associations" | Also affect the units in an attached group (leader plus unit); **not** selection groups |
  | `recursive` | "Recursive" | Descend through all levels |
  | `<filter>` | "Filter By" | An entry or category ID, or `any` (the default) |
  | `<target>` | the "Affects" picker | `entries` (default), `profiles.<profile type name>`, `rules`, or `associations...` |
- Token counts in 11e: `profiles` 2,403, `entries` 2,119, `self` 2,107,
  `recursive` 1,928, `group` 868, profile type names (`Melee Weapons` 881,
  `Unit` 804, `Ranged Weapons` 718), entry IDs 436, `associations` 93,
  `forces` 31, `rules` 1.
- Examples:
  - `self.entries.recursive.profiles.Ranged Weapons` (224 `append`): all
    ranged weapon profiles beneath this entry.
  - `self.entries.group.recursive.profiles.Melee Weapons`: the same, also
    reaching the attached group.
  - `profiles.Unit` (177 `set`): the unit's own stat line.
  - HH3: `entries.forces` with `field: "error"` adds an error message to forces.
- **Observed in NR** (`affects` on an upgrade, `scope: model-or-unit`):
  `self.entries.recursive.profiles.Melee Weapons` reached all 40 profiles on
  the unit and the weapons of two child models; with an entry ID in the path
  (`self.entries.recursive.<model id>.profiles.Melee Weapons`) only that
  model's weapon changed. The ID filter works and `recursive` descends into
  child models.
- **Observed in NR (M7 sweep, units attached as leader and bodyguard).** Which selections the words reach
  (own = the node's profiles, child = its child upgrades'):
  | Path | Alone | Attached |
  |---|---|---|
  | none, `self` | own | own |
  | `entries`, `entries.recursive` | child | child |
  | `group`, `self.group`, `group.recursive` | own | own and the attached units' own |
  | `group.entries` | child | nothing |
  | `self.entries.group` | own, child | own and the attached units' own (not children) |
  Read as: the node itself is taken if the path has `self` or lacks `entries`; `entries` adds children; when attached,
  `group` replaces both with the whole group's own nodes. The group is every unit joined by attachments, so a
  bodyguard's modifier reaches its leaders. A unit's own modifier comes first, then the others in roster order.
  Not tested: an id filter with `group`, and `forces`, `rules` and `associations` targets.
- The exact serialiser (`construct_affects_query` in the missing
  `nr-shared`) is not available, so edge cases (for example how names versus
  IDs are written) are Unknown.

### 5.9 Scopes

Scopes appear on modifiers, conditions, constraints and repeats. From the NR
notes, a scope resolves to a node in the roster tree relative to "here" (the
node the item sits on):

| Scope | Resolves to |
|---|---|
| `self` | here |
| `parent` | nearest ancestor that is an entry or the roster; groups skipped |
| `ancestor` | the whole chain including here, up to the roster (`instanceOf` is true if any node holds the ID) |
| `root-entry` | nearest ancestor-or-self whose parent is a category node (the unit as added to the army) |
| `primary-category` | nearest ancestor-or-self that is a category node |
| `force`, `primary-catalogue` | nearest ancestor-or-self that is a force (the two are the same node, since a force "is" its catalogue) |
| `roster` | the root |
| `group` | nearest ancestor group |
| `unit`, `model`, `upgrade` | nearest ancestor with that type |
| `model-or-unit` | nearest ancestor typed model or unit |
| `non-upgrade-entry` | nearest ancestor entry not typed upgrade |
| `link` | nearest ancestor that is a link |
| `<scope>-self` | as above, but here itself is a candidate; counts then include here's own amount |
| any entry or category ID | here if its ID, link ID or target ID matches, else the nearest ancestor that does, else a child of the force with that ID |

A scope that resolves to nothing is **not** disabled (as the NR notes imply):
**Observed in NR**, the count is 0 and the comparison is made with it, so `atMost 0`
holds and `atLeast 1` does not (the `atLeast` at `self` quirk below still applies).
The tree is `roster > force > category > root entry > model / group /
upgrade`.

**Observed in NR** (1,284 profiles whose `Num` is NR's count for a query; see
`nr-extension-tests/RESULTS.md`, "Scopes and counting"):

- **Counts are relative to the scope node.** Amounts multiply only below it. From a
  Qm of amount 3 holding Qu (2 each) holding Qv (2 each), `self` counts 2 for the
  Qu level and 6 with all depths (2 + 4), not 6 and 18. From the roster it is
  the full totals, since top-level amounts are 1 there.
- **`-self` adds the node itself, at its own amount** (not its total). It does not
  apply to `self` or `ancestor`: `self-self` counted nothing.
- **A cost query leaves out the scope node's own cost**, even with `-self`. Costs
  below it are the unit cost times the relative count.
- **Forces and categories are transparent** when counting the next level: the
  direct children of the roster are the top-level selections in its forces.
- **`non-upgrade-entry` falls back to the roster** when no entry above qualifies.
  The other type scopes (`unit`, `model`, `upgrade`) resolve to nothing in that case.
- **`group` is a scope.** It counts the members of the group (the entries in the
  same group below the same parent, including here).
- **Scope `self` with the node's own entry id (or its link id) as `childId` counts
  like `any`.** With another id the count is of matching selections below.
- **`ancestor` counting is not understood.** `instanceOf` at `ancestor` follows the
  chain; counts do not (most are the same from every host, as if it were the
  roster, with extra amounts for some hosts and ids).
- **The other rows of the table matched NR** once counts were relative: `parent`,
  `root-entry`, `primary-category`, `force`, `primary-catalogue`, `roster`, `unit`,
  `model`, `upgrade`, `model-or-unit`, entry-id scopes, and an unknown id (which
  resolves to nothing). `link` and the `-self` forms other than `root-entry`,
  `model`, `model-or-unit` and `upgrade` were not exercised.

- 11e modifier scopes: `root-entry` (708), `model` (530), `parent` (397),
  `model-or-unit` (240), `upgrade` (181), `roster` (18), `force` (12), `unit`
  (6), `root-entry-self` (5), `model-self` (4), `model-or-unit-self` (2), and
  once an entry ID. HH3: 31 uses, almost all `add` on `category`.
- On a modifier, `scope` (Likely) is the starting node for the `affects`
  path; the editor's "Include self" checkbox corresponds to the `-self`
  suffix. The exact relationship is not documented.

### 5.10 `add` on message and info fields

- `add` is in the baseline schema, but both repos use it with `field` set to
  `error` (and the editor lists `warning`, `info`), where `value` is message
  text. 11e also uses `field: "add-info"` (scope `parent` 68, `root-entry`
  61), which adds an info link.
- BlueScribe throws `modifier.type === 'add' while modifier.field !==
  'category'`. An engine must handle `add` on at least `category`, `error`,
  `warning`, `info` and `add-info`.

### 5.11 Other modifier attributes

- `id` on modifiers: 90 uses in 11e (`set` 72, `increment` 8, `add` 8,
  `decrement` 2). Likely a handle other modifiers refer to; purpose Unknown.
- `comment`: free text; ignore.

### 5.12 Evaluation rules (NR editor notes)

All of this is **Confirmed (NR notes)** unless stated.

**Modifier order.** "Applied to a field in this order, whatever the array
order":
1. `set`, `add`, `remove`, `set-primary`, `unset-primary`
2. `append`, `prepend`
3. `increment`, `decrement`, `multiply`, `divide`, `modulo`, `power`,
   `exponent`, `triangular`
4. `floor`, `ceil`
5. the `cumulative-*` types and `replace`

**Observed in NR:** the same `replace` and `append` listed in either array
order give the same result (`Pistol, Heavy`), so the order is by type.

**When a modifier applies.** Every condition group and every direct condition
must pass (AND). Inside a group, `or` needs one and `and` needs all; groups
nest. Conditions read current amounts, so a modifier can depend on another
modifier's result.

**Repeats.** A repeat counts its query like a condition, then computes
`floor(count / value) * repeats` (`ceil` if `roundUp`). A modifier with
repeats applies that many times: the NR notes say `increment`, `decrement`,
`multiply` and similar use `value * times`, but in practice `multiply` gives
`n * v^k` (observed; see 5.6 for every type); `set`, `append` and `hidden`
apply once when the count is above zero. Several repeats on one modifier add
their counts. A repeat counting 0 disables the modifier (Observed in NR:
values stayed unchanged with no models selected).

**Fields a modifier can change:** `hidden`, `name`, `annotation` (shown as
"Name (annotation)"), `page`, `description` (rules), `category` (add/remove,
set/unset primary), a cost type ID, a characteristic type ID (the result is
re-formatted by the characteristic's rules), a constraint ID (changes that
constraint's value), `defaultAmount`, `defaultSelectionEntryId`, and
`error`/`warning`/`info` (messages on the roster).

**Queries** (`field`, `childId`, ...). `field` is `selections`, `forces`,
`associations`, a cost type ID (sums that cost), or `limit::<cost id>` (the
roster's limit, only valid at roster scope; it reads -1 when there is no
limit). `childId` is `any`, an ID, or `unit`/`model`/`upgrade`/`mount`/`crew`.
`shared` is ignored on conditions and repeats.

**Quirks kept from BattleScribe.** `atLeast` with scope `self` and a count of
0 is false even for value 0. `includeChildSelections` is set whenever the
field is a cost, and implies `includeChildForces` unless the scope is a
force.

**Hidden info.** A rule, profile or info link is not rendered when hidden.
A link is hidden if the link or its target is hidden; a hidden info group
hides everything inside.

**Amounts.** A selection's amount multiplies through its parents (3 models x
2 units = 6). Costs propagate the same way.

## 6. Condition, constraint and association extensions

### 6.1 Conditions and groups

Condition types: the original `atLeast`, `atMost`, `lessThan`,
`greaterThan`, `equalTo`, `notEqualTo`, `instanceOf`, `notInstanceOf`, plus:

| Item | HH3 | 11e | Meaning |
|---|---|---|---|
| `before` | 0 | 413 | See below. The NR notes say it is true when the counted node precedes here in the tree; observed behaviour depends on the form |
| `always`, `never` | 0 | 0 | Confirmed (NR notes): `always` is true, `never` is false |

Condition group types in the editor (`ConditionGroup.vue`): `and`, `or`,
`not`, `count` (with `min` and `max`), numeric operators (`add`, `subtract`,
`multiply`, `divide`, `modulo`, `power`, `min`, `max`) and comparisons
(`greater`, `greaterOrEqual`, `less`, `lessOrEqual`, `equal`, `notEqual`). So
groups can form a small expression tree. Only `and`, `or` (HH3 and 11e) and
`count` (11e, 39 uses) are observed in the data. Semantics of the numeric and
comparison types are Unknown.

- `count` example (Abominant): `min: 3` over "at least 1 of Abominant /
  Benefictus / Biophagus / ... in the force". True when the number of passing
  child conditions is within `[min, max]`.
- **Observed in NR** (four conditions, 0 to 4 true): `min 3` passes at 3 and
  4; `min 3, max 3` passes only at 3; `max 1` (no `min`) passes only at 1, so
  `min` and `max` are **inclusive** and an unset `min` behaves like 1, not 0
  (it failed with none true). The last point is from one run.
- **`localConditionGroups`** (413 in 11e): a group of the form
  `{type, value, scope, field, includeChildSelections, includeChildForces,
  repeats, conditions: [...]}`. **Observed in NR:** it counts the selections
  within `scope` for which **all the inner conditions are true** (each such
  selection acts as "self" for the inner conditions), then compares that count
  with `value` using `type`. Three cost rules confirmed this:
  - `atLeast 2` over [`before any`, `instanceOf` this unit] with scope `parent`:
    four copies of the unit cost 100, 100, 110, 110, so the third and later
    copies pay (they have at least 2 earlier copies).
  - `atLeast 2` over [`instanceOf` this unit]: three copies all cost +10.
  - `lessThan 2` over [`instanceOf` this unit]: two copies get no surcharge, and
    a unit not yet added (zero copies) shows the surcharge.
  The role of `repeats` inside the group is untested (the data always uses 1).
- **`before`, two forms.**
  - **Inside a local group** (the form 11e uses: `childId: any`, `scope: self`):
    it is true for selections that come **earlier** in the tree, which is how
    G1 charges only later copies. Observed in NR.
  - **As an ordinary condition** with a specific `childId` (a sibling model) and
    `scope: parent`: it fired for every model present, **including the entry
    itself and nodes after it**, and not when the model was absent. So here it
    behaves like "exists", not like "precedes". This contradicts the NR notes
    and is probably unintended; do not rely on it.
- `percentValue` on a constraint means `value` is a percentage (25 = 25%) of
  the same query with `childId: any`. On a condition the engine compares the
  raw ratio, so write 0.25, not 25 (Confirmed (NR notes)).
- Condition scopes are as in section 5.9.

### 6.2 Constraints and repeats

| Item | HH3 | 11e | Confidence | Meaning |
|---|---|---|---|---|
| Constraint `automatic: true` | 1,277 | 144 | Confirmed (NR notes) | The builder satisfies the constraint itself (auto-select up to min, down to max) instead of only flagging it |
| Constraint `message` | 0 | 226 | Confirmed | Text shown when the constraint fails |
| Constraint `traverseAssociationGroup` | 0 | 279 (218 true) | Likely | Count across an attached group (leader plus unit) |
| Constraint `childId`/`childName` | 0 | 696/478 | Likely | Filters what is counted; value `"any"` for all |
| Constraint `negative` | 5 | 3 | Confirmed (NR notes) | A `value` of -1 normally means no limit; with `negative: true`, -1 is a real number |
| Constraint/repeat `includeChildForces` | 180/156 | 3,990/21 | Likely | Also count items in child forces |
| Repeat `childName` | 16 | 95 | Unknown | Next to a `childId` that holds a name |
| Constraint type `exactly` | 0 | 0 | Confirmed (NR notes) | `min`, `max` and `exactly` compare the query to `value` |
| Constraint `isLimit` | 0 | 0 | Unknown | Editor lists it; never observed |

- Other constraint rules (Confirmed (NR notes)): a `min` constraint on a hidden
  entry does not fire; a constraint's own modifiers (field = its ID) change its
  value, evaluated from the owning entry; `shared: false` remaps the scope
  (`parent`, `force`, `primary-catalogue` and `primary-category` become here or
  here's parent).
- **Observed in NR (M5 Constraints catalogue).**
  - `min` fails below the limit, `max` above, `exactly` on any difference. A `-1` limit is
    no limit; with `negative: true` it is a real number (`max -1` fails even at 0).
  - A constraint counts like a condition's query: `selections` or a cost type id (a unit's
    own cost is not counted), with `childId`, scope and `includeChildSelections`. A constraint
    with no `childId` counts the owner's own entry (not `any`).
  - `percentValue`: the limit is a percentage of everything in the scope (`max 50` passes at
    exactly half); 0 of 0 is 0%.
  - A child's constraint with scope `parent` is checked under every selected parent, whether or
    not the child is selected: an absent child fails its `min` (and `exactly`), but its `max`
    is not evaluated. A group's constraints are checked under each parent with `self` meaning
    the group's members and `parent` the whole parent.
  - A `min` on a hidden entry does not fire. A selected hidden entry is an error of its
    own: "{parent} {entry} cannot be selected while hidden".
  - A modifier whose field is the constraint's id changes its limit (`set 3` when a condition
    holds), evaluated from the owner.
  - Scopes `force`, `parent`, `primary-category` and `roster` counted two units of the same
    entry on each; `root-entry` and `self` (with the unit's own id) did not.
  - Roster cost limit: "Roster has N pts too many (max L)"; equal passes, -1 is none.
  - Default wording (no `message`): `{host} has N selections too many (max M)`,
    `{host} requires N selections more of X` (min) and `{host} requires N more selections of
    X` (exactly, below). " of X" is the constraint's `name` if it has one, else the counted
    entry's name unless that is the owner or `any`; for a cost field the unit word is the cost
    type (`1 pts too many of X`). A node's messages are listed sorted alphabetically.
  - Builder behaviour, not validation: NR adds root units and children up to a `min`
    whether or not `automatic` is set; `automatic` stops the UI going below it.
- **Instantiation (observed in NR, `M6 Instantiate` catalogue).** What NR adds by itself:
  - A new roster gets each visible root unit as many times (amount 1 each) as the largest
    `min` or `exactly` on that unit, ignoring `childId` and scope (`force`, `roster`, `parent`
    and `self` all gave the count); `min 3` with `max 2` gave 3; percentages, `-1` and `max`
    alone add nothing; a hidden unit is not added.
  - Adding a selection adds, recursively, each child with a `min` constraint **owned by the
    child** that counts the child itself (or `any`, or no `childId`) in scope `parent`, `self`
    or `root-entry`, to that amount. Scope `force`, `exactly`, and constraints on the unit
    that count one of its children add nothing. A hidden child is added too.
  - A group with a `min` (scope `self`, `any`) gets its `defaultSelectionEntryId` member, else
    its first member, that many times. A group member with its own `min` is added.
  - Constraints on the target entry and on the link both count.
  - `automatic` makes no difference to any of this.
- `message` example: "An Attached Unit can only take 1 Enhancement.",
  on a `max 1` constraint over `Enhancements` at `root-entry` scope with
  `traverseAssociationGroup: true`.
- Some references are by **name** rather than ID (`childId: "Siege Breaker"`),
  often with `childName` beside them.

### 6.3 Associations (11e; leader attachment)

- 479 `associations` blocks in 11e, for example (Asurmen):
  ```
  name "Leading", action "group", childId "unit", label "Leader",
  min 0, max 1, scope "force", includeChildSelections true,
  conditionGroups: [ or: instanceOf Dire Avengers ]
  ```
  The led unit carries constraints with `field: "associations"` and
  `childId: "Leader"` (or `"Support"`), `scope: self`, `max 1`, limiting how
  many of each role may attach.
- **Observed in NR** with a minimal scratch pair (leader E, bodyguard F, other
  unit G4):
  - E's panel shows a **"Leading (0/1)"** section, titled with the association
    `name` and the `min`/`max` count, listing the units that satisfy the
    association's `conditionGroups`. Only F was offered; G4 was not.
  - Ticking F changes it to "Leading (1/1)" and the roster **nests E under F**.
  - A modifier on E with `affects: self.entries.group.recursive.profiles.Melee Weapons`
    and `scope: root-entry` added its keyword to E's own weapon always (the
    path starts with `self`) and to F's weapon **only while E was attached**.
    So `group` in an `affects` path means "the units attached to this one".
  - A constraint with `traverseAssociationGroup: true` (max 1 of a cost type
    `Enh`, `scope: root-entry`, a `message`) counted across the attached
    group. With one `Enh` in the group there was no message. With one on E and
    one on F **while attached**, the total read `2 Enh` and the message "An
    attached unit can only take 1 Enh." appeared. **Detaching removed the
    message**, though both enhancements remained.
  - The message appears in a title/tooltip attribute and in validation text;
    it did not render as visible page text in the list view.
- **Observed in NR (M7 sweep, catalogue `M7 Associations`).**
  - Units offered: every unit in the scope that matches `childId` and passes the association's conditions (each acting
    as self), except the leader itself.
  - Limits: the interface does not stop going over `max`. The leader's row reports "{category} {leader} needs N more
    {childId} selected in {name} ({count}/{min})" or "... has N {childId} too many selected in {name} ({count}/{max})".
    The plural of `childId` was not observed (the engine adds "s").
  - A constraint on the `associations` field counts nothing in any form tried (`childId` `any`, the label, the
    association name, `unit`; scope `self` or `root-entry`): a `min` always fires, a `max` never does.
  - `traverseAssociationGroup` resolves the constraint's scope from each unit in the group and sums the counts, each
    node once: scopes `self` and `root-entry` count both units' own trees; `force` counts the force once. It works for
    `min` as well as `max`.
- Other association fields in the editor (`ids`, `of`, `labelMember`) were not
  exercised. The `childName` and `action` fields were copied from the data.
- The Leader/Support caps in the real data (`field: associations`,
  `childId: Leader`) were included on F as a `max 1` constraint, but a second
  leader was not tried.

## 7. Structural features the data relies on

From the repo READMEs and BlueScribe's behaviour:

- HH3 uses a **parent/child force model**: a detachment's faction is found
  with "Instance of → in roster → Child: ‹faction catalogue›". BlueScribe's
  README lists child forces as unsupported.
- References by **name** instead of ID (`childName`; names with leading
  whitespace are used for sort order).
- Generic library catalogues (weapons, wargear, rules) imported through
  links.
- 11e treats **weapon profiles as targets**: faction rules and upgrades
  modify them through `affects` instead of duplicating them.

## 8. Worked example: adding to a dice expression

This pattern occurs in real data (the 11e repository used it on a relic-like
upgrade). It is described here with neutral names, because the mechanics matter
and the item does not. It reproduced in NR as `D6+2`.

An upgrade "Example Item" that gives its bearer's melee weapons +2 Attacks.
Attacks (`A`) hold plain numbers on some weapons and dice expressions on
others (`D3`, `D6`). The upgrade carries one modifier group, with IDs resolved
to names:

| # | Modifier | Purpose |
|---|---|---|
| 1 | `append` an annotation "Example Item" to melee weapons (`affects: self.entries.recursive.profiles.Melee Weapons`) | Show the source on the weapon |
| 2 | `increment` S by 2, same targets | Strength +2 |
| 3 | `append` "+0" to `A` for weapons in the category "Dice Attack Weapon" (`arg: "+0"`, `join: ""`) | Give dice attacks (D3, D6) a trailing number |
| 4 | `replace` `A` "+0" with nothing, for weapons in the category "Dice Attack Marker" | Clean up |
| 5 | `increment` `A` by 2, `position: -1`, all melee weapons | Attacks +2 |
| 6 | `decrement` `A` by 2, `position: -1`, for weapons in another category | Undo the +2 for that category |
| 7 | `replace` `A` "+0" with nothing, all melee weapons | Final cleanup |
| 8 | `add` category "Dice Attack Marker" to the "Dice Attack Weapon" entries | Mark the weapons touched in step 3 |
| 9 | `append` Keywords "Ability 1" / "Ability 2" (conditional, `skipIfPresent`) | Grant an ability |
| 10 | `replace` Keywords "Ability 1" with "Ability 2", `position: 1`, conditional | Upgrade an existing 1 to 2 for a stronger bearer |

What it shows:

- **Dice attacks need a trailing number.** Steps 3 to 7 only make sense if
  `increment` acts on a number inside the string. `D6` would not take "+2",
  so the data first turns it into `D6+0`, increments the last number, then
  strips any leftover `+0`. This is why the `position: -1` modifiers exist.
  This matches the NR note on `position` (section 5.7).
- **Categories are used as flags** (steps 3, 4, 8): a modifier adds a
  category to the weapon, and later modifiers test for it.
- Plain integer values (Strength here) need no such trick. Attacks are where
  dice expressions occur.
- Several modifiers name entries by raw ID inside `affects`, so an engine has
  to resolve IDs within those paths.

## 9. Open questions

**Added with the scope experiments (M2):** how `ancestor` counts; `instanceOf` when its
scope resolves to nothing; `percentValue` on a condition; `includeChildForces` and nested forces; whether `-self` applies to every
type scope (seen for `root-entry`, `model`, `model-or-unit` and `upgrade`); and how a
condition is evaluated for an entry that is not yet in the roster (the add list showed
a cost surcharge for zero copies).

Resolved by the NR notes or by observation in NR: the meaning of `position`
(0 and unset mean every number; positive is 1-based; negative counts from the
end), the order of modifier evaluation, the default `join`, `skipIfPresent`
(only on `append`/`prepend`), `replace` (literal, all matches, empty `arg`,
missing `value`), clamps and arithmetic on `N+` and compound strings, `count`
bounds, `affects` with an ID filter and with `group`, the arithmetic and
`cumulative-*` formulas (including repeats and their rounding),
`localConditionGroups` with `before`, the leader association UI,
`traverseAssociationGroup`, `always`/`never`, `exactly`, `negative`,
`automatic`, the scope list and the `affects` grammar.

1. Why an unset `min` on a `count` group behaves like 1 (one run).
2. The ordinary-condition form of `before` (true even for itself and later
   nodes) versus the local-group form (earlier nodes only): is this one rule
   applied to different candidate sets, or two behaviours?
3. The role of `repeats` inside a `localConditionGroups` entry (always 1 in the
   data), and the numeric and comparison condition-group types.
4. Association fields `ids`, `of` and `labelMember`; a second leader or a
   "Support" role on the same unit; `action` values other than `group`;
   attaching across forces.
5. `affects` with `forces`, `associations` and `rules` targets, and the
   combination of `scope` and `affects` in other cases.
6. `childName` on repeats and constraints, `isLimit`, and the modifier `id`.
7. Behaviour of `cumulative-*` and `exponent` on values that are neither
   numbers nor `N+` strings.
8. How soon "Latest changes" picks up a push. Observed: a plain page reload a
   few minutes after each push showed the new data, with no re-adding of the
   system and no release. The NR docs imply a refresh is needed; reloading was
   enough here.

## 10. Suggested conformance tests

For each extension, a minimal data file and its expected output:

- `replace`: "Line (X)" with `arg: X`, `value: 1` becomes "Line (1)".
  Missing `value` deletes `arg`. Empty `arg` sets only when empty (the Serpent
  Shield trio).
- `append` with `join` `", "`, `", "` and `""`; `prepend` likewise.
- `skipIfPresent`: a second `append "Ignores Cover"` leaves the field
  unchanged.
- `increment` followed by `ceil`: base 9, +1, ceil 10 gives 10; base 10, +1,
  ceil 10 gives 10.
- `floor` on a `"3+"` string: `floor WS 2` leaves `"3+"`; `floor WS 4` gives
  `"4+"` (expected output to be confirmed in NR).
- `divide` and `multiply` on a constraint value.
- `increment` on a dice attack `D6` with `position: -1` after an `append "+0"`
  (expected `D6+2`), and on a plain `3` (expected `5`). Also `D6+1` with
  `position` `0` and `-1`, to check negative indexing.
- Modifier order: a `replace` and an `append` on the same field in either
  array order give the same result.
- `add` with `field: error` and `field: add-info`.
- `affects` with each observed path shape, and each `scope` value.
- `count` condition group with `min: 3`.
- Association with `max: 1` and a constraint using `traverseAssociationGroup`.
- Read the same data from JSON and from XML and compare the results.

The best source of expected outputs is New Recruit itself. The first run is
recorded in `RESULTS.md` of the repository `edward3h/nr-extension-tests` (28 of 37 single-value predictions
matched, 2 differed, 7 had none), with follow-up runs for the formulas.
A ready-made scratch system for most of these is in the repository
`edward3h/nr-extension-tests` (`README.md`, with machine-readable
`tests/expected.json`).

## 11. Known gaps in this draft

- HH3 and 11e are the only datasets analysed. Other repositories may use more
  extensions.
- I compared against `Catalogue.xsd` from the `30K-4-All` repo and assumed it
  matches the official BattleScribe schema. I did not check its version.
- Loading and caching (NR docs, Publishing page): NR loads a repository's latest
  release if it has one, otherwise its latest commit; systems added by a player
  are checked only when first added, and need a refresh to update. A repo with
  a stale release therefore will not show newer commits.
- The editor's "builder panel" is a spreadsheet-style bulk editor, not a roster
  builder; evaluated values can only be seen in the roster builder at
  newrecruit.eu.
- The NR notes (`mcp_tools.ts`) are documentation written for the editor's AI
  tooling, not engine code. The engine itself (`nr-shared`) is not accessible,
  so none of this is checked against the actual implementation.
- Counts come from JSON files at HEAD on 2026-11-10; the data moves quickly
  (934 commits in six months on 11e).
- The `before` and `affects` explanations are hypotheses from a handful of
  samples each.

## Appendix: scripts

Located in the session scratchpad, run with `python3 -I <script> <data dir>`:

- `extract.py`: per-type usage of the extended modifier types (HH3)
- `extract2.py`: `ceil`/`floor` groups, `scope`, `affects`, `position`,
  `automatic`, `childName` (HH3)
- `pos.py`: context around `position` (HH3)
- `x11.py`: all extensions in 11e (`skipIfPresent`, `affects`, `before`,
  `count`, `associations`, `divide`, `multiply`, constraints)
