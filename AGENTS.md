# Rules for agents working in this repository

This is the Android port of TripTrack. The iPhone app is the source of truth for behaviour,
numbers, wire contract, strings and look. Read `docs/port-plan.md` before your first change.

## Where the truth lives

- iPhone app: the local checkout `~/OneZeeProjects/trip-track` (public:
  github.com/OneZee23/trip-track-ios). Read only. Its `CLAUDE.md` records the product decisions and
  the traps behind them; read the sections for the feature you port before writing code. Swift
  sources are under `TripTrack/`, tests under `TripTrackTests/`.
- Backend: `~/OneZeeProjects/trip-track-backend`. Read only. When the backend and the iPhone client
  disagree about a payload, what the iPhone app sends and accepts today wins.
- `docs/api-contract.md` (once written) is the wire contract for this client.

## Porting rules

- Same behaviour and the same numbers. A threshold, a rounding rule or a key name that differs from
  the iPhone app is a bug unless `docs/divergences.md` records why Android has to differ.
- Port the tests together with the code: the iPhone test cases are the behaviour spec. Keep their
  inputs and expected values.
- Every ported type names its counterpart in a short comment: `// iOS: TripDistanceGate.swift`.
- Comments explain why, like the iPhone code does. Do not narrate what the code obviously does.
- Wire and storage values never change: enum raw values, JSON keys, UUID namespaces, geohash
  precision, units in storage (metres, m/s, km for odometers) are contracts with the server and
  with the second phone.
- UI text comes from string resources generated from the iPhone strings (`tools/strings`). Never
  hardcode visible text. Distances, speeds and heights are formatted only by the `Measure` port.
- The look follows the iPhone design system (`AppTheme`, `AppType` with Inter), not Material
  defaults. Use the house dialogs and sheets; no system `AlertDialog` for app-authored questions.
  Android permission prompts stay native.
- Android-specific behaviour (foreground service, notification channels, permissions, back
  gesture, battery restrictions) follows current Android guidance for target SDK 36.

## Building

- Use the Gradle wrapper with the JDK 17 toolchain. Build and test only the modules you touch,
  for example `./gradlew :core:logic:test`. One Gradle build at a time in your checkout.
- The machine has 16 GB of RAM and other agents build in parallel: never raise the Gradle or
  Kotlin daemon memory, never start an emulator unless your task says so, and shut down any
  emulator you started before you finish.

## Git

- Work only in the checkout and branch your task names. Never push, never run `git push --mirror`,
  never touch other repositories.
- Commit messages: one line, at most four words including the prefix (`feat: port distance gate`),
  no body, and no trailers of any kind. A `Co-Authored-By` line is forbidden.
- This repository is public. No IP addresses, ssh users, keys, tokens, real names or personal data,
  in code and in docs alike. Before your last commit run
  `git grep -nE 'ssh [a-z]+@|[0-9]{1,3}(\.[0-9]{1,3}){3}|BEGIN [A-Z ]*PRIVATE KEY'` and fix any hit.

## When you finish

Write `docs/reports/<your task>.md`: what was ported (with iOS file names), what is left, test
results with the exact commands, and every place where Android differs from the iPhone app and
why. Do not edit `docs/port-status.md`; the orchestrator keeps it.
