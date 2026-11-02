# Mindcraft Android App — Archived Repair Target

This document preserves the agreed target for repairing the existing Android application in this repository. It is a repair of the existing app, not a replacement or redesign.

## Product target

Restore the intended Mindcraft-style Minecraft bot experience on Android:
- Connect to real Minecraft servers using explicitly supported protocol versions.
- Reflect actual server and bot state in the UI.
- Execute real, protocol-backed game actions rather than simulated successes.
- Support configured AI providers honestly, and add true on-device inference only when an Android runtime and model path are integrated.
- Keep the app usable from an Android phone; a PC must not be required for ordinary use.

## Non-negotiable correctness rules

- Never invent successful AI responses, game actions, inventory changes, coordinates, health, hunger, or connection state.
- Distinguish local socket writes from server-confirmed outcomes.
- Do not claim a server session is ready until the protocol's login and configuration phases have completed.
- Do not claim compatibility with protocol versions that have not been implemented and tested.
- Surface unsupported features and provider/protocol errors clearly.
- Preserve user settings and credentials safely; never log secrets.

## Repair milestones

1. **Truthful baseline** — remove fake AI/gameplay success, expose explicit connection phases, and show unknown values as unavailable. Initial work is in PR #1.
2. **Protocol foundation** — implement and packet-test the supported version registry, handshake/login/configuration/play lifecycle, keepalive, teleport confirmation, chat, disconnect, and server address handling.
3. **Real game state** — decode server-backed position, health, hunger, inventory, and world state before displaying them.
4. **Real gameplay** — implement and test navigation, mining, block placement, inventory, crafting, eating/equipment, combat, and task planning as protocol-backed capabilities.
5. **AI integration** — verify API providers and add a genuine Android local inference runtime/model path before advertising offline AI.
6. **Android reliability** — foreground session service and notification, lifecycle/cleanup, secure credential storage, Room migrations, and error recovery.
7. **Release validation** — unit and protocol integration tests, Gradle build, APK archive integrity check, and installable debug APK artifact. A successful build is not proof of real-server gameplay compatibility.

## Current implementation status (updated 2026-11-02)

- PR #1 contains the first-stage honesty and connection-state repairs.
- Protocol registry and packet handling are currently limited to protocol 767; this is not broad Minecraft version support.
- Protocol-767 movement, mining-at-coordinate, and block-placement packet writers exist. Mining and placement are requests only; server block updates are not decoded, so success is not confirmed.
- Coordinate movement now emits bounded 0.25-block straight-line waypoints (up to 64 blocks) rather than a single large position jump. This is not collision-aware pathfinding; no chunk/block collision map is available yet.
- Automated tests now cover the waypoint planner and protocol-767 movement/mining/placement packet encoders, alongside host/port parsing tests.
- Full game-state decoding (inventory, health, hunger, chunks and block updates), obstacle-aware navigation, crafting, chests, equipment, food, combat, survival, SRV DNS, broader protocol support, local AI inference, foreground service, secure credential storage, and proper database migrations remain unfinished.
- The current branch head has not yet been confirmed by a successful test/build run. Do not describe the app as finished or release-ready until the remaining milestones are implemented and validated.

## Working rule

Continue incrementally on the existing repair branch and preserve the PR review boundary. Do not merge the PR or archive the GitHub repository itself without the owner's explicit instruction.
