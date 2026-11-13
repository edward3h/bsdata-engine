# Open-source BSData roster engine: minimal design sketch

Status: sketch, written 2026-11-12; language decided (Java 25 with TeaVM) 2026-11-12. Companion to `bsdata-extensions-spec.md`
(what the data means) and the separate repository
[`edward3h/nr-extension-tests`](https://github.com/edward3h/nr-extension-tests)
(how to check it). This
document says how an engine could be built from them, in what order, and what
is still undecided.

## 1. Goals and non-goals

**Goals**

1. A library that loads BSData game systems (JSON; XML later), builds rosters, and
   evaluates them: costs, rendered profiles and rules, categories, validation
   messages.
2. Results that match New Recruit (NR) on the behaviour the data actually uses.
   The extensions in the spec are the target, not just the original
   BattleScribe schema.
3. Runs headless on the JVM and, compiled with TeaVM, in the browser and Node,
   from one Java code base. A UI is a separate, later project.
4. A conformance suite anyone can run: `tests/expected.json` in `edward3h/nr-extension-tests`,
   `RESULTS.md`, plus differential tests against NR on real rosters.

**Non-goals (first version)**

- A user interface, accounts, sync, sharing, tournaments.
- Editing data files (NR's data editor already does that).
- Pixel-compatible output such as NR's text export format.
- Bug-for-bug parity where behaviour looks accidental (see section 8).

## 2. Size of the problem

*Measured on two real repositories before they became unavailable (see section 14).
They remain the scale target; the project now reproduces that scale with a
generated dataset.*

| Dataset | Files | Size | Objects with IDs | Links | Modifiers |
|---|---|---|---|---|---|
| `wh40k-11e` | 46 | 49.0 MB | 103,765 | 43,786 | 24,870 |
| `horus-heresy-3rd-edition` | 42 | 20.3 MB | 40,116 | 19,980 | 17,188 |

This is large enough that a naive approach (re-evaluating every modifier for
every node on every change) will be slow, and small enough to hold in memory.
Design for lazy, memoised evaluation. Load once; build indexes once.

## 3. Language and platform

**Decided: Java 25, compiled to JavaScript with TeaVM for the browser.** This
follows the setup in the author's `kv2` project, which already builds a shared
Java library (`rosterParser`) for both a server and a TeaVM client.

### 3.1 Toolchain (copied from kv2)

- Java: Temurin 25, pinned in `.tool-versions` and managed with `mise`; Gradle
  toolchain `JavaLanguageVersion.of(25)`.
- TeaVM 0.16.0. The shared library uses the `org.teavm.library` plugin; the
  browser module uses the `org.teavm` plugin with the `jso` libraries. The
  TeaVM Maven repository is `https://teavm.org/maven/repository`.
- Quality gates from kv2's conventions plugin: Spotless (`palantirJavaFormat`,
  import order, licence header), the EISOP Checker Framework **nullness**
  checker with JSpecify annotations (`@NullMarked` in `package-info.java`),
  JUnit 6 on the JUnit Platform, and a `pre-commit` git hook that runs `build`.
- The licence header text in kv2 is `(C) Edward Harman and contributors $YEAR`;
  it is kept (the licence is Apache-2.0, see section 10; per-file headers are not
  required by the licence).

### 3.2 Modules

```
engine-core     pure Java 25; no platform dependencies; compiled by javac and by TeaVM
engine-jvm      JVM loaders (files), command-line checker, JUnit conformance runner
engine-web      TeaVM entry point: JSO bindings, browser loaders (fetch, DOMParser)
conformance     test data and runner shared by the JVM and JavaScript builds
```

`engine-core` is the whole engine from section 4: data model, roster, queries,
conditions, modifiers, validation, rendering to plain data. It never touches
files, the network, threads or reflection. Everything platform-specific sits
behind small interfaces (`DataSource`, `DataNode`).

### 3.3 Constraints that TeaVM puts on `engine-core`

TeaVM compiles bytecode to JavaScript and ships its **own subset of the Java
class library**; its docs say reflection, resources, class loaders and JNI are
restricted. The following are consequences for the design. Items marked
*verify* need a short spike (milestone M0) because I have not confirmed them.

- **No reflection, no class-path resources, no reflection-based libraries**
  (Jackson, Gson, JAXB) in the core. Immutable data comes in through the
  `DataNode` interface.
- **JSON parsing**: use a small existing pure-Java library with no reflection
  (candidate: `minimal-json`) behind a `JsonSource` interface in `engine-core`,
  producing `DataNode`s. The same code compiled by javac and TeaVM gives the same
  behaviour on both, so no custom parser is needed for that. A hand-written
  parser would need its own edge-case tests (escapes, surrogate pairs, number
  grammar, duplicate keys, nesting) and upkeep, for no engine benefit. Write one
  only if the M0 spike shows a concrete failure or a measured memory problem;
  the interface makes it a swap. Browsers could use `JSON.parse` via JSO as an
  optimisation. *Verify* under TeaVM, and measure parse time and heap for 49 MB
  in the browser (see the memory note below).
- **XML parsing**: out of scope for version 1. Both current repositories are
  JSON, and `javax.xml` is probably not available under TeaVM. Older `.gst` and
  `.cat` files could be supported later through a converter or a library.
- **Numbers**: the games in scope use integer values, so exact matching of NR's
  floating-point rounding and display is not a goal. Use `double` internally,
  print whole numbers without a decimal point, and accept whatever the platform
  prints for fractions. Only two of the 135 cases in `expected.json` have fractional
  results (T11e and F08a); they are informational, not pass or fail. No custom number formatter is needed.
- **Java class library coverage**: TeaVM implements `BigDecimal` almost fully
  and `java.util.regex` only partly. Prefer plain collections, `String` and
  `StringBuilder`, avoid regex in hot paths and avoid `java.time`, locale
  behaviour and `String.format`. Run each new dependency through the
  compatibility report before using it. *Verify* records, sealed interfaces and
  pattern-matching `switch` under TeaVM 0.16 with Java 25 bytecode (kv2 builds
  with both, but the spike should confirm these constructs specifically).
- **No threads or blocking I/O** in the core. It is single-threaded and
  synchronous; loading is the caller's job.
- **Determinism**: iteration order must not depend on hash ordering. Use
  insertion-ordered collections or sort explicitly, so both builds agree.
- **Memory**: the 11e data is about 104,000 objects. Do not keep both a raw
  `DataNode` tree and a typed model. Convert straight into the typed model
  while parsing and drop the raw tree. M1 does not do this yet (it measured about
  930 MB peak for `wh40k-11e` in Node); the memory pass is milestone M1b.

### 3.4 What kv2 already gives

- A tested pattern for a Java library shared by a server and a TeaVM client
  (`rosterParser`, an interface plus implementation with a plain JUnit test).
- A working Gradle setup, nullness checking and formatting to copy.
- The TeaVM `jso` and `interop` libraries for the browser glue.

## 4. Architecture

Five layers, each testable alone.

```
files (JSON/XML)
   |  Loader
   v
GameData  (immutable: entries, links, categories, profile types, cost types, ...)
   |  Instantiate (add force / selection)
   v
Roster    (mutable tree of Selections)
   |  Evaluator  (queries, conditions, repeats, modifiers)
   v
View      (derived, memoised: effective name, costs, characteristics, hidden, ...)
   |  Validator, Renderer
   v
Diagnostics, totals, profile tables
```

### 4.1 Loader

- Reads each file into a neutral tree from JSON (current repos). XML (older data
  and original BattleScribe) is deferred; its shape mirrors the JSON, with child
  lists as arrays and attributes as fields. Characteristic text is `$text` in
  the JSON.
- Normalises: tolerant of missing arrays, `hidden`, `import`, numeric vs string
  values (`value` may be `"0"`, `0` or absent).
- Builds an **ID index** across all files: every object with an `id`, plus its
  owning file and path. Needed because modifiers, conditions, `affects` paths
  and scopes all refer to IDs, and because links need resolving. Lookup is
  **scoped**, not global (see section 11): a catalogue sees itself, then the
  game system, then the catalogues it links, followed transitively, with the first
  link winning. Modifiers address constraints through their own entry.
- Resolves **links**: `catalogueLinks`, `entryLinks`, `infoLinks`,
  `categoryLinks`, with the link's own modifiers and constraints kept
  separately from the target's (a link and its target both contribute).
- Reports unresolved references as diagnostics instead of failing. NR fails the
  whole catalogue when a link target is not reachable ("This Army Book could not
  be loaded"); a library should say which one. Dangling `typeId`,
  `publicationId` and `defaultSelectionEntryId` values are warnings, since the
  real data contains them.

Output type, roughly:

```java
// immutable records; sealed hierarchies for the variants
record Entry(String id, String name, EntryType type,
             List<Constraint> constraints, List<Modifier> modifiers,
             List<ModifierGroup> modifierGroups, List<Cost> costs,
             List<Profile> profiles, List<Rule> rules, List<InfoLink> infoLinks,
             List<Child> children, List<CategoryLink> categoryLinks,
             List<Association> associations) {}
sealed interface Child permits Entry, EntryLink {}
```

### 4.2 Roster model

```java
final class Selection {            // mutable roster node
    final String id; final Entry entry; final @Nullable EntryLink link;
    double amount; final List<Selection> children; @Nullable Selection parent;
    final Catalogue catalogue;     // whose data it came from
}
final class Force { ForceEntry entry; Catalogue catalogue; List<CategoryNode> categories;
                    List<Force> childForces; }
final class Roster { List<Force> forces; Map<String, Double> costLimits; /* ... */ }
```

The tree shape matters for scopes: `roster > force > category node > root entry
> model/group/upgrade`. Keep categories as explicit nodes (the NR notes rely on
it for `primary-category` and `root-entry`).

Attached groups (leader plus unit) are a relation on top of the tree:
`Selection.attachedTo` / `attachedGroup` computed from the roster's association
choices.

**Instantiation** (adding a force or a selection) is its own step and has rules
the spec only partly covers: default amounts, `min` constraints with
`automatic`, `defaultSelectionEntryId` on groups, which children are created.
See section 8.

### 4.3 Evaluator

The core. Three sub-parts.

**Queries.** Given a node ("here"), a scope, a field, a `childId` and flags,
return a number. Pieces:

- *Scope resolution*: the table in spec 5.9 (`self`, `parent`, `ancestor`,
  `root-entry`, `force`, `roster`, `model-or-unit`, `<scope>-self`, any ID...).
- *"Is" index* per node: the set of IDs a node counts as for `instanceOf` and
  `childId` (own ID, link ID and target ID, ancestors' group IDs, categories,
  force and catalogue IDs).
- *Counting*: `selections`, `forces`, `associations`, a cost type, or
  `limit::<cost>`; `includeChildSelections`, `includeChildForces`,
  `percentValue`; amounts multiply through parents.

**Conditions.** `atLeast`, `atMost`, `lessThan`, `greaterThan`, `equalTo`,
`notEqualTo`, `instanceOf`, `notInstanceOf`, `always`, `never`, plus `before`;
condition groups `and`, `or`, `count` (and `not`); `localConditionGroups`
(count the candidates in a scope that satisfy inner conditions, then compare).
A modifier applies when every direct condition and every group passes.

**Modifiers and repeats.** The pipeline, in the order the NR notes give and
the tests confirmed:

1. Collect the modifiers that apply to a field of a node: from the entry, from
   its link, from a shared target, from modifier groups (nested), and from
   *relative* modifiers elsewhere whose `affects` path reaches this node.
2. Evaluate each modifier's conditions; compute its repetition count `k` from
   its repeats (`floor(count / value) * repeats`, ceiling if `roundUp`, counts
   added together, `k = 0` disables it).
3. Order the survivors by type stage: set/add/remove/primary flags; append and
   prepend; arithmetic; floor and ceil; `cumulative-*` and `replace`.
4. Apply each to the field's value with the type-specific rule, honouring `k`
   with the formulas in spec 5.6.

Values are one of: number, string, boolean, ID list (categories), text. Strings
that contain numbers (`D6+1`, `3+`) are tokenised; arithmetic and clamps act on
every number token unless `position` selects one (0 or unset: all; positive:
1-based; negative: from the end). A characteristic result is written back in
the characteristic's own form (`N+`).

### 4.4 View (derived state, memoised)

For each selection, lazily computed and cached:

- effective `name`, `annotation`, `hidden`, `page`, `description`;
- costs per cost type (own, then summed up the tree);
- characteristics for each profile (so `A`, `S`, `WS`...);
- categories (primary and others);
- constraint values (constraints can themselves be modified);
- `defaultAmount`, `defaultSelectionEntryId`.

**The dependency problem.** Conditions read current amounts and costs, and
modifiers change costs and hidden flags, so evaluation is not a simple
pipeline. The NR notes only say "conditions read the current amounts, so a
modifier can depend on another modifier's result". Proposed approach:

- Evaluate each (node, field) lazily with memoisation.
- Detect cycles with an in-progress marker; on a cycle, use the field's base
  value and record a diagnostic.
- Invalidate caches by node and field when the roster changes (a dependency
  graph, or simply clear everything and recompute lazily, which is fine at
  roster sizes of hundreds of nodes).

### 4.5 Validator

Produces diagnostics from:

- constraints (`min`, `max`, `exactly`; `-1` means no limit unless `negative`);
  `percentValue`; `shared: false` scope remapping; a `min` on a hidden entry
  does not fire;
- `automatic` constraints (instantiate or flag);
- association limits and `traverseAssociationGroup` counts across attached
  groups;
- messages from modifiers on `error`, `warning`, `info` fields;
- roster-level cost limits.

### 4.6 Renderer

Produces data, not pixels: a list of units with effective names, costs and
profile tables, rules and abilities (respecting hidden info and link hiding),
and the validation list. A text exporter can come later.

## 5. How the spec maps to the design

| Spec section | Engine module | Main tests |
|---|---|---|
| 3 file format | Loader | Parse all of HH3 and 11e; counts match section 2 |
| 5.1 to 5.6, 5.12 | Modifier pipeline | `tests/expected.json` T01 to T19, F01 to F12 |
| 5.7 `position` | Number-token ops | T01 to T03, T19, F04 to F07 |
| 5.8 `affects`, 5.9 scopes | Query + relative modifiers | T20; AFF-ALL, AFF-ONE; the worked example in the spec |
| 6.1 conditions, `count`, local groups | Conditions | T16; G1 to G3 rows in RESULTS.md |
| 6.2 constraints | Validator | NR notes section on constraints; HH3/11e real rosters |
| 6.3 associations | Roster + Query | E, F, G4 observations |
| 7 structural features | Loader + Instantiate | HH3 child forces, 11e leader rules |

## 6. Milestones

Each ends with something that runs and is checked.

| # | Milestone | Done when |
|---|---|---|
| M0 | Toolchain spike, loader, ID index, link resolution | Gradle skeleton builds with Java 25 and TeaVM 0.16; one test runs identically on the JVM and in Node; loads `wh40k-11e` and HH3 with zero unresolved references and reports the counts in section 2; records, sealed types and the JSON library confirmed under TeaVM; browser parse time and memory measured |
| M1 | Typed game data, roster tree with explicit selections, costs without modifiers | A unit's base cost sums correctly; hand-made and exported rosters total as NR does (done, section 13). Automatic instantiation is left to M5 |
| M1a | **Synthetic scale generator** | A deterministic generator writes a game system and catalogues of about 100,000 objects with the shape ratios measured on the real data (section 2); it loads through `Linker` and `GameData` with 0 unresolved links, and JVM and JavaScript reports are byte-identical. It contains no real game content |
| M1b | **Memory pass** on the loader and typed model | The generated 11e-sized dataset loads in a browser tab within the agreed budget (300 MB peak, agreed 2026-11-13; the real 11e data measured about 930 MB in Node), measured in a real browser and in Node; a half-sized set well inside it; reports unchanged; a regression check records the peak so later milestones cannot quietly undo it |
| M2 | Query engine, scopes, conditions, groups | Condition truth tables match the NR notes; T16 passes |
| M3 | Modifier pipeline for plain numbers and text | T04, T05, T08, T12, T14, T09 pass |
| M4 | Number tokens, `position`, clamps, repeats, `cumulative-*` | All of F01 to F12 in `expected.json` pass except the fractional F08a (informational); the worked-example replica gives `D6+2` |
| M5 | Constraints and validation | The G1 to G3 cost rules and the leader/bodyguard messages reproduce |
| M6 | Force and catalogue linking, hidden entries, categories | The synthetic scale dataset and the exported conformance rosters instantiate without errors |
| M7 | Associations, `affects` with `group`, `traverseAssociationGroup` | E, F, G4 results match |
| M8 | Differential testing against NR | A corpus of rosters exported from NR for the synthetic systems shows the same totals, values and validation as NR. Real data can be checked locally by anyone who holds it, outside the repository and CI |
| M9 | Roster file format: read and write | A roster exported from NR loads, evaluates to the same totals, and writes back so that NR opens it; the format is documented in the spec |

M0 to M4 are mostly mechanical and fully specified by the tests. M5 to M7 need
the most judgement. M8 is the honest measure of success. M9 depends on M6, since
a roster refers to catalogue entries by id, and on documenting the format first.

M1b is a planned step, not optional, and comes before M2: typing conditions and
modifiers (M2, M3) replaces the raw nodes with more objects, so memory only gets
worse, and the loader's shape is easiest to change now. It is also a precondition for
any browser UI. The memory budget is agreed: 300 MB peak for the 11e-sized dataset. See
section 13 for the measurements and the changes it should make.

**Planned task outside the milestones: command line on picocli.** The command line
(`engine-jvm`) parses its arguments by hand and picks a mode by guessing (a
`.roster.json` suffix, a directory, or the words `scale` and `generate`). That is fine
for a handful of one-argument modes. Switch to [picocli](https://picocli.info) with
subcommands (`check`, `roster`, `scale`, `generate`, and whatever comes next) as the
first step of whichever change adds either of these:

- a command that takes two inputs, such as `bind <data directory> <roster file>`; or
- the first real options (a seed, a report type, `--max-problems`, an output format).

Scope and constraints: picocli goes in `engine-jvm` only. The TeaVM entry point in
`engine-web` stays hand-written, because picocli needs reflection that TeaVM does not
support and that entry point is only a test harness. Use picocli's annotation
processor to avoid reflection, and check first that annotated fields work with the
EISOP nullness checker, which the build enforces (not yet tried). Done when each
subcommand has `--help`, a bad number or missing file gives a one-line error and a
non-zero exit code instead of a stack trace, and the existing modes behave as before
(their reports are compared byte for byte).

## 7. Testing strategy

1. **Unit tests per rule**, run on both the JVM (JUnit) and as compiled JavaScript (Node), generated from `tests/expected.json` (each entry has
   its base value, modifiers and the value NR showed; the two fractional cases, T11e and F08a, are reported but do not fail the run). The runner builds the
   scratch roster programmatically (UNIT A, select `APPLY`, read the profile)
   so the same files serve as engine tests. Repeat tests need models toggled to
   give `k` = 0, 1 and 2.
2. **Golden tests** for G1 to G3 costs and the E/F/G4 association behaviour,
   recorded in `RESULTS.md`.
3. **Smoke tests on real data**: load the full dataset, instantiate every
   unit alone, evaluate, and require no exceptions or unresolved references.
   (This is the test I ran against BlueScribe; it found its failures quickly.)
4. **Differential tests**: build the same rosters in NR and in the engine, then
   compare totals and validation messages. Open question: whether NR's roster
   export includes computed costs and messages. If it does, exports are the
   oracle; if not, the NR page has to be read, as I did for the scratch
   system. Check this early (M1), since it decides the cost of M8.
5. **Property tests** for the number-token arithmetic (a plain number behaves
   like a one-token string; `position` out of range changes nothing).

## 8. Risks and decisions

**Behaviours that may be bugs.** Some observed NR behaviour looks accidental:
`position 0` meaning "all numbers"; `before` as an ordinary condition being
true for itself and later nodes; `atLeast 0` in `self` being false (documented
as kept from BattleScribe). **Decided:** *compat mode* by default (copy
observed NR behaviour exactly), not *spec mode* (implement the apparent intent).
The odd cases live in one list so each can be switched to spec behaviour later.
The data was written and tested against NR, so a "fixed" engine could change
roster results.

**Unknown behaviours.** The open questions in the spec section 9: instantiation
rules, how `automatic` constraints add selections, `localConditionGroups`
`repeats`, association fields beyond those tested, `affects` targets beyond
`profiles`. Each should become a test before it becomes code.

**Source of truth.** NR's engine (`nr-shared`) is not public, and this design
deliberately comes from the NR docs, the editor's UI code and observed
behaviour, not from NR's engine code. Keep it that way: do not look at or copy
leaked or decompiled sources. That makes the project defensible as an
independent implementation.

**Licence and prior art.** BlueScribe is GPL-3.0; reading it for ideas is fine
but copying code would bind the result to GPL-3.0. The project is Apache-2.0,
so no code is taken from BlueScribe. Phalanx (BSData,
.NET) is reportedly abandoned; check its licence if you want to borrow its
loader design.

**Data drift.** The data repos change daily (934 commits in six months for
11e). The conformance suite must pin dataset versions or commits.

**Performance.** 25,000 modifiers and 100,000 objects are fine with lazy
evaluation and indexes. Avoid evaluating every modifier for every node; index
modifiers by the field and node they can affect, and index relative modifiers
by the nodes their `affects` path can reach.

## 9. Rough effort (a guess, not a measurement)

| Part | Order of magnitude |
|---|---|
| Loader, index, links | about 1,000 to 1,500 lines |
| Roster model and instantiation | about 800 to 1,200 lines |
| Query engine, scopes, conditions | about 1,200 to 1,800 lines |
| Modifier pipeline and number-token arithmetic | about 1,000 to 1,500 lines |
| Constraints, validation, associations | about 1,000 to 1,500 lines |
| Test harness and conformance runner | about 600 to 1,000 lines |

That is roughly 5,000 to 8,000 lines of core code. Instantiation rules and the
dependency problem are where the real uncertainty sits. M0 to M4 look
tractable as a part-time project; M5 to M8 depend on how closely NR's
instantiation and validation have to be matched.

## 10. Decisions

Resolved:

- **Language and platform**: Java 25, with TeaVM for the browser, following
  `kv2` (sections 3.1 to 3.4).
- **Parity bar**: match NR where it differs from original BattleScribe, in compat
  mode (section 8).
- **Licence**: Apache-2.0 (`LICENSE`). Permissive with an explicit patent grant;
  no code is copied from GPL projects such as BlueScribe.
- **Scope of version 1**: a headless library and command-line checker (M0 to M7)
  plus a **roster file format** (M9): reading and writing rosters so lists can move
  between tools. No browser UI in version 1; `engine-web` only proves the engine
  runs in a browser and serves as the base for a later UI.
- **Repository layout**: the Gradle project lives in this repository next to
  `docs/` (as built).
- **Whether to start**: yes; M0 is done (section 11).

- **Roster formats**: NR's JSON roster format first (the JSON parser is there),
  then BattleScribe `.ros`/`.rosz` (XML in a zip), which needs the deferred XML
  parser and a zip reader that work under TeaVM. The JSON format is documented in
  `docs/roster-format.md`; M9 is under way (see section 12).

Still needed: nothing blocking.

## 11. M0 spike results

On the `m0-spike` branch: Gradle skeleton following kv2 (Kotlin DSL), with
`engine-core` (JSON library behind `JsonSource`, `DataNode`, `Linker`),
`engine-jvm` (command-line checker) and `engine-web` (TeaVM, run in Node). The
`Linker` loads every `*.json` file in a repository, indexes all `id`s, and
checks every string field ending in `Id` (except `id`).

| Question | Result |
|---|---|
| Java 25 records, sealed interface, pattern-matching `switch` under TeaVM 0.16 | Compile and run correctly |
| `minimal-json` 0.9.5 under TeaVM | Works unchanged |
| Spotless, EISOP nullness checker and JUnit | Pass (`./gradlew build`) |
| JVM and compiled-JavaScript results agree | The reports are byte-identical on both full repositories |
| TeaVM `long` and JSO | `long` is a BigInt in TeaVM, so JSO bindings must use `double` or `int` |

Reference resolution (all files of the repository, 46 files for `wh40k-11e`, 42 for HH3):

| Field | wh40k-11e unresolved | HH3 unresolved |
|---|---|---|
| `targetId` (entry, info, category and catalogue links) | **0** of 43,786 | **0** of 19,980 |
| `childId` (conditions, constraints, repeats, associations) | **0** of 35,894 | **0** of 18,676 |
| `gameSystemId` | 0 | 0 |
| `typeId` | 37 of 99,340 | 844 of 26,382 |
| `publicationId` | 80 of 490 | 78 of 773 |
| `defaultSelectionEntryId` | 23 of 1,328 | 2 of 675 |

Findings:

- **Links resolve.** Every `targetId` and `childId` in both repositories resolves
  when all files are searched together, so the M0 criterion holds for the
  references that matter to the engine.
- **Keywords stand in for ids.** `childId` takes `any`, `model`, `unit`,
  `model-or-unit` and `upgrade`; `defaultSelectionEntryId` takes `none`. They
  account for 1,933 and 180 `childId` values in the two repositories.
- **Remaining misses are dangling definitions in the data.** Examples: HH3 has 841
  cost values (all 0) for a cost type "Asset Point(s)" that no game system
  defines; 11e has 37 for a missing "Commendation Points" type (Crusade
  content, ignored); some `publicationId`s name publications that do not exist.
  The engine must treat these as ignorable, and report them as warnings, not
  errors.
- **Duplicate ids exist in 11e**: 144 ids appear in more than one file, and 651
  more repeat inside one file; HH3 has none. They are mostly copied constraints
  and entry links. The only referenced one is a category "Tyrant Guard" copied
  between two Tyranid catalogues, and the modifier `field` use of a constraint id
  affects 21 of them. So a global index is ambiguous but rarely matters.
- **Resolution is by scope, not global (observed in NR, tests D1 and D2 in
  `nr-extension-tests`).** For an id defined in several places: the first
  catalogue link wins (links X then Y gave X, Y then X gave Y); a catalogue's own
  copy beats linked ones; the game system's copy beat a linked catalogue's copy;
  catalogue links are followed transitively; and a link to an id that no linked
  catalogue defines makes the whole catalogue fail to load. A modifier whose
  `field` is a constraint id changed only the constraint on its own entry, though
  another entry had a constraint with the same id. Untested: own copy against
  the game system copy.
- **The real data agrees.** With the game system and the transitive closure of
  `catalogueLinks`, every `targetId` in both repositories resolves; with direct
  links only, 167 in 11e and 182 in HH3 do not.
- **Only link targets need scope.** Other id fields are matched at run time or
  ignored, and the data uses them across catalogues that are not linked: cost types
  defined in another faction's catalogue (872 values in 11e), publications
  defined elsewhere, and a condition `childId` that is another catalogue's root id
  (6,112 in HH3). So `Linker` applies the scoped lookup to `targetId` only, and for
  every other field any definition anywhere counts as resolved.
- **Time:** Node loads and parses all 46 `wh40k-11e` files in about 1.4 s and links
  them in about 0.2 s; HH3 takes 0.7 s and 0.1 s. Peak memory for the
  whole-repository runs is in section 13 (about 930 MB for 11e).

Scoped resolution is implemented (`Repository`, `Linker`), with unit tests that mirror
tests D1a to D1g. On both full repositories it reports 0 out-of-scope `targetId`s,
and the JVM and compiled-JavaScript reports are byte-identical.

Not yet done: browser (as opposed to Node) timing and heap; whole-repository
memory; own copy against game-system copy (untested in NR); the other half of
the loader (normalisation and the typed model).

## 12. M9 progress: roster JSON

Exported rosters were observed and documented in `docs/roster-format.md`. `Roster`
(in `engine-core`) reads NR's JSON roster export into records and writes it back; a
round trip reproduces the file exactly, on the JVM and compiled to JavaScript, for
both synthetic fixtures (and for a local Horus Heresy export containing groups and
rules, which is not committed because it contains rules text). Unknown fields are
kept in an `extra` map so files survive a read and write.

Findings that affect the engine:

- **Exports carry NR's computed values** (costs and profile text after modifiers),
  and the roster total equals the sum of the selection costs. That makes exports a
  ready-made corpus for M8: instantiate the same selections, evaluate, and compare.
  Fixtures: `edward3h/nr-extension-tests/rosters/`.
- **A selection's `entryId` is a path** of ids from the catalogue's entry link to
  the entry, so reading a roster needs the scoped link resolution of section 11.
- **Attachment state is in the export**, stored on both selections as `associations`
  (outgoing, naming the target selection's id) and `incomingAssociations`. So an
  attachment is roster state between two selections, and the evaluator's
  `attachedTo` / `attachedGroup` (section 4) can be read directly from a roster, and
  derived from the data's association when a roster is built. `Roster.Association`
  models it; the `associationId` rule is a hypothesis from one sample.

Not yet done: the engine side of reading (resolving each selection's `entryId`
against a `Repository`, which needs the typed model of M1), writing rosters from
scratch, and checking that NR opens a written file.

## 13. M1: typed model, roster tree, base costs

Done on branch `typed-model`. Decisions: explicit selections only (what NR adds
automatically when a unit is added waits for M5, because `min` constraints can be
modified); the typed model is incremental (structure, links, categories, profiles,
costs and forces are typed; constraints, modifiers, modifier groups and
associations are kept as raw nodes in `Raw` until M2 and M3 type them); tests use
synthetic copies of the Extension Tests data, with the real repositories loaded
from a local path by the command-line checker.

**Code.** `model` (immutable records: `Catalogue`, `Entry`, `Link`, `Body`, `Parts`,
`GameData` with scoped lookup and `::` path resolution) and `roster` (`RosterModel`,
`ForceNode`, `Selection`, and `RosterBinder`, which attaches an exported roster file
to the data).

**Cost rules observed in NR** (M1 Costs test data, `nr-extension-tests/RESULTS.md`):

- An entry link's own cost **replaces** its target's cost of the same cost type
  (Hero: target 30 pts, link 5 pts, shown 5). Cost types the link does not mention
  come from the target (Shared unit: link 3 pts, target 20 pts and 2 Enh, shown 3 pts
  and 2 Enh). This corrects the earlier assumption that "a link and its target both
  contribute".
- A selection's cost is its effective cost times its **total number**; a child's
  amount is per parent, so totals multiply down the tree (Gun 2 under Trooper 3 is 6
  guns, 12 pts).
- The roster total is the plain sum of the selections' own costs.

**Results.**

| Check | Result |
|---|---|
| Exported M1 Costs roster binds | no problems; every selection's own cost equals the exported cost |
| Roster total | 50 pts and 2 Enh, as NR |
| Hand-built roster (explicit adds) | 42 pts at the point NR showed 42 |
| `entryId` paths in the two larger exports | all resolve (no problems) |
| Real repositories loaded into the typed model | `wh40k-11e`: 12,035 entries, 4,278 groups, 17,869 links, **0 unresolved**; HH3: 2,912 entries, 1,429 groups, 8,568 links, **0 unresolved** |
| JVM and compiled-JavaScript reports | byte-identical on both repositories |

**Memory (a concern).** The whole `wh40k-11e` repository peaks at about 930 MB RSS in
Node and 590 MB on the JVM; HH3 about 430 MB and 330 MB. That is with the parsed
JSON trees still held (for `Raw` and for the id sets in `Repository`). A browser tab
will not take 930 MB, so the loader needs a memory pass (milestone M1b, planned
next, before M2 and before any UI): drop the
tree after typing, intern repeated strings (type ids, names), keep `Raw` as compact
nodes, and build `Repository` id sets from the typed model instead of a second tree
walk.

**The memory pass (M1b) should include**, in order of expected effect:

1. Parse straight into the typed model and release the `DataNode` tree (the
   largest single cost, since both trees are held now).
2. Keep `Raw` as compact node trees, or a lazily parsed string, until M2 and M3
   type them.
3. Intern repeated strings (cost and profile type ids, entry names, type names).
4. Build `Repository`'s id sets from the typed model instead of a second walk of
   the tree, and use primitive-friendly or sorted-array maps where `HashMap` is
   heavy.
5. Measure in a browser tab (heap snapshot), not only RSS in Node, against the
   agreed 300 MB budget.

**Not done:** automatic instantiation (M5); categories as explicit roster nodes
(needed for `primary-category`, M2); writing a `RosterModel` back to a roster file;
profile and rule evaluation.

## 14. Direction: a game-agnostic engine (2026-11-13)

The BSData GitHub organisation, which held the data the project was shaped by, is
no longer available. The engine never depended on it: it implements a file format.
What changes is how it is tested and what the repository may contain.

- **No real game data** in this repository, its fixtures, or CI. Only synthetic data.
- **Conformance** is the Extension Tests system and the rosters exported from NR
  for it (`edward3h/nr-extension-tests`). Every observed NR behaviour in the spec
  has a synthetic test; new behaviour questions are answered with new synthetic
  tests, as before.
- **Scale** is tested with a generated dataset (milestone M1a) that reproduces the
  measured size and shape of the real systems: object counts, link and modifier
  ratios, nesting depth, id duplication. It contains no real names or text.
- **Real data**, for anyone who holds a copy, stays a local option: the
  command-line checker accepts any directory. Results from it are not committed.
- **Earlier measurements** in this document (sections 2, 11 and 13) were taken on
  the real repositories while they were available, and are kept as the record of
  what the engine had to handle. They cannot be re-run from this repository.
- **Open question for users:** which game systems the engine should be checked
  against next is theirs to decide; the data format is the same.
