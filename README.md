# bsdata-engine

An open-source engine for BSData game system files: load a game system,
build a roster, and evaluate it (costs, profiles, rules, validation), with
behaviour that matches what the data actually uses today.

**Status: design only. There is no code yet.**

## Why

[New Recruit](https://www.newrecruit.eu) is currently the only app that works
well with current BSData files, and it is closed source. The BSData repositories
now use format extensions that the original BattleScribe format and older
open-source readers do not support. This project aims to document those
extensions from observed behaviour and build an independent, open
implementation.

The design is deliberately not based on New Recruit's engine code, which is not
public. It comes from New Recruit's public documentation, the data editor's
published UI code, the BSData data itself, and tests run against New Recruit as
a user.

## Contents

| File | What it is |
|---|---|
| [`docs/bsdata-extensions-spec.md`](docs/bsdata-extensions-spec.md) | Draft specification of the BSData format extensions and how they evaluate, with a confidence level for each claim |
| [`docs/engine-design.md`](docs/engine-design.md) | Sketch of a minimal engine: layers, milestones, risks, and the decisions still open |

## Related repositories

- [`edward3h/nr-extension-tests`](https://github.com/edward3h/nr-extension-tests):
  a scratch game system used to check behaviour in New Recruit, with results
  and a machine-readable list of expected values (`tests/expected.json`). It is
  the starting point for the conformance suite.

## Open decisions

Language and platform, version 1 scope, whether to match New Recruit's
behaviour or the original BattleScribe's, and the licence. No licence has been
chosen yet, so until one is added all rights are reserved.
