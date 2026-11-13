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
  use the same unless the licence decision says otherwise.

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
  while parsing and drop the raw tree.

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
  and scopes all refer to IDs, and because links need resolving.
- Resolves **links**: `catalogueLinks`, `entryLinks`, `infoLinks`,
  `categoryLinks`, with the link's own modifiers and constraints kept
  separately from the target's (a link and its target both contribute).
- Reports unresolved references as diagnostics instead of failing. The editor
  itself fails on a missing catalogue link; a library should say which one.

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
| 5.8 `affects`, 5.9 scopes | Query + relative modifiers | T20; AFF-ALL, AFF-ONE; Wulfen Stone replica |
| 6.1 conditions, `count`, local groups | Conditions | T16; G1 to G3 rows in RESULTS.md |
| 6.2 constraints | Validator | NR notes section on constraints; HH3/11e real rosters |
| 6.3 associations | Roster + Query | E, F, G4 observations |
| 7 structural features | Loader + Instantiate | HH3 child forces, 11e leader rules |

## 6. Milestones

Each ends with something that runs and is checked.

| # | Milestone | Done when |
|---|---|---|
| M0 | Toolchain spike, loader, ID index, link resolution | Gradle skeleton builds with Java 25 and TeaVM 0.16; one test runs identically on the JVM and in Node; loads `wh40k-11e` and HH3 with zero unresolved references and reports the counts in section 2; records, sealed types and the JSON library confirmed under TeaVM; browser parse time and memory measured |
| M1 | Roster tree, instantiation, costs without modifiers | A unit's base cost sums correctly; a hand-made roster totals match NR |
| M2 | Query engine, scopes, conditions, groups | Condition truth tables match the NR notes; T16 passes |
| M3 | Modifier pipeline for plain numbers and text | T04, T05, T08, T12, T14, T09 pass |
| M4 | Number tokens, `position`, clamps, repeats, `cumulative-*` | All of F01 to F12 in `expected.json` pass except the fractional F08a (informational); Wulfen replica gives `D6+2` |
| M5 | Constraints and validation | The G1 to G3 cost rules and the leader/bodyguard messages reproduce |
| M6 | Force and catalogue linking, hidden entries, categories | HH3 and 11e rosters instantiate without errors |
| M7 | Associations, `affects` with `group`, `traverseAssociationGroup` | E, F, G4 results match |
| M8 | Differential testing against NR | A corpus of real rosters shows the same totals and validation as NR |

M0 to M4 are mostly mechanical and fully specified by the tests. M5 to M7 need
the most judgement. M8 is the honest measure of success.

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
but copying code would bind the result to GPL-3.0. Decide the project licence
(GPL-3.0, MIT or Apache-2.0) before taking any code from it. Phalanx (BSData,
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

Still needed:

1. **Licence** for the engine (the licence header in kv2 is "(C) Edward Harman and contributors").
2. **Scope of version 1**: headless library and command-line checker (M0 to M7),
   or include a minimal browser UI through `engine-web`.
3. **Parity bar**: *decided.* Match NR where it differs from original
   BattleScribe, in compat mode (see section 8).
4. **Repository layout**: put the Gradle project in this repository next to
   `docs/`, or in a new one. The sketch assumes this repository.
5. **Whether to start now.** The cheapest next step is the M0 spike: a Gradle
   skeleton following kv2, one pure-Java class compiled by both javac and
   TeaVM, a loader for the JSON data, and the conformance runner executing
   `tests/expected.json` against an empty evaluator so every test fails for a
   known reason.

## 11. M0 spike results

First pass, on the `m0-spike` branch (Gradle skeleton following kv2; `engine-core`
and `engine-web`; a `Census` that counts objects, ids and `targetId` references).

| Question | Result |
|---|---|
| Java 25 records, sealed interface, pattern-matching `switch` under TeaVM 0.16 | Compile and run correctly |
| `minimal-json` 0.9.5 under TeaVM | Works unchanged |
| Spotless, EISOP nullness checker and JUnit on `engine-core` | Pass (`./gradlew build`) |
| JVM and Node results agree | The Node counts match an independent Python count exactly, for three files |
| TeaVM `long` and JSO | `long` is a BigInt in TeaVM, so JSO bindings must use `double` or `int` |

Measurements, Node 24 (compiled JavaScript), single file each:

| File | Size | Objects | Parse | Peak RSS |
|---|---|---|---|---|
| `wh40k-11e/Imperium - Space Marines.json` | 5.1 MB | 28,549 | 209 ms | 231 MB |
| `horus-heresy-3rd-edition/Alpha Legion.json` | small | 1,296 | 30 ms | 68 MB |

Not yet done: loading a whole repository and resolving links across files
(unresolved references within one file are expected, since links cross files);
browser (as opposed to Node) timing and heap; a JVM-side comparison of the same
files; the exit criterion that wh40k-11e and HH3 load with zero unresolved
references.
