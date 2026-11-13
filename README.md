# bsdata-engine

An open-source engine for BSData game system files: load a game system,
build a roster, and evaluate it (costs, profiles, rules, validation), with
behaviour that matches what the data actually uses today.

**Status: design only. There is no code yet.** The engine will be written in
Java 25 and compiled to JavaScript with [TeaVM](https://teavm.org) for the
browser, following the author's `kv2` project.

## Why

[New Recruit](https://www.newrecruit.eu) is currently the only app that works
well with current BSData files, and it is closed source. The BSData repositories
now use format extensions that the original BattleScribe format and older
open-source readers do not support. This project aims to document those
extensions from observed behaviour and build an independent, open
implementation.

**Direction (2026-11-13).** The BSData organisation's repositories are no longer
available on GitHub. This project is a **game-agnostic engine**: it contains no
game data and depends on none. The conformance suite is a small synthetic game
system and rosters exported from New Recruit for it
([`edward3h/nr-extension-tests`](https://github.com/edward3h/nr-extension-tests)),
and scale is tested with a generated dataset. Anyone can point the command-line
checker at their own data directory; nothing in the repository or CI uses real
game data.

The design is deliberately not based on New Recruit's engine code, which is not
public. It comes from New Recruit's public documentation, the data editor's
published UI code, the BSData data itself, and tests run against New Recruit as
a user.

## Contents

| File | What it is |
|---|---|
| [`docs/bsdata-extensions-spec.md`](docs/bsdata-extensions-spec.md) | Draft specification of the BSData format extensions and how they evaluate, with a confidence level for each claim |
| [`docs/roster-format.md`](docs/roster-format.md) | New Recruit's JSON roster export format as observed, with open questions |
| [`docs/engine-design.md`](docs/engine-design.md) | Sketch of a minimal engine: layers, milestones, risks, and the decisions still open |

## Related repositories

- [`edward3h/nr-extension-tests`](https://github.com/edward3h/nr-extension-tests):
  a scratch game system used to check behaviour in New Recruit, with results
  and a machine-readable list of expected values (`tests/expected.json`). It is
  the starting point for the conformance suite.

## Decisions

Decided: Java 25 with TeaVM; match New Recruit's behaviour (not the original
BattleScribe's), in compat mode; Apache-2.0 (see `LICENSE`); version 1 is a
headless library and command-line checker plus a roster file format, with no
browser UI; the Gradle project lives in this repository. Still open: which roster
formats come first. See `docs/engine-design.md`, section 10.

## Licence

Apache License 2.0. See [`LICENSE`](LICENSE).
