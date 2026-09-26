# Yconomics

Economy-themed mod for a modular Minecraft project (Minecraft Java Edition). Part of a suite of intercommunicating mods that expose public APIs so other mods — the user's own and third parties' — can integrate.

## Context directory — read this first

`context/` is a **separate private repo** (https://github.com/CerealKlla/yconomics-context), not part of this one — it's listed in `.gitignore` here and must never be committed to this repo. It's cloned as a subdirectory at `context/` for local convenience. If this directory is missing (e.g. a fresh clone of just this repo), restore it with:

```
git clone https://github.com/CerealKlla/yconomics-context.git context
```

Before searching source for architecture, ownership boundaries, API shape, or "why does this work this way," check `context/` first. It's maintained specifically to answer those questions cheaply:

- `context/design-document.md` — the authoritative design spec: scope, mechanics, open questions. Start here for anything about intended shape or scope.
- `context/decisions.md` — dated log of decisions made during implementation that extend or override the design document, with rationale. Check this for anything that looks like it contradicts design-document.md — the doc should already reflect the current decision, but this explains why.
- `context/classes/` — one short markdown file per implemented class: public surface, key state, collaborators. Read the relevant file here before opening the actual source, and before editing a class update its file to match.

**Keep this system current as you work:**
- When a design decision is made that conflicts with or is absent from design-document.md, update design-document.md directly and add a dated entry to decisions.md explaining the change.
- When a class is added or its public surface changes, add or update its file in `context/classes/`.
- Don't let source and these docs drift — treat updating them as part of finishing the change, not optional cleanup.
- `context/` has its own git history, independent of this repo's commits. Commit and push changes there separately (`git -C context add . && git -C context commit -m "..." && git -C context push`) — editing the files alone doesn't back them up.

## Status

Scaffolded 2026-09-25 (see [context/decisions.md](context/decisions.md)):
- Loader: **NeoForge**
- Minecraft version: **26.1.2**
- Java: **JDK 25** (standalone Eclipse Temurin, JAVA_HOME set) — same toolchain as the rest of the suite
- Group ID: `com.github.cerealklla.yconomics` / Mod ID: `yconomics`
- Project structure copied verbatim from Cartographyr/Lyfe's own MDK setup (`build.gradle`, `settings.gradle`, wrapper) — no Yconomics-specific build changes yet.
- Public code repo (https://github.com/CerealKlla/yconomics) and private context repo (https://github.com/CerealKlla/yconomics-context) both created and pushed.

**First vertical slice: the "gambling" recipe** (design doc Section 3) — a real, working `GamblingRecipe extends CustomRecipe` (1 gold ingot → random 5–12 gold nuggets), registered, datapack-declared, unit tested (`GamblingRecipeTest`), boot-smoke-tested clean via `runServer`. **Live in-game confirmation (actually craft with it) is still pending.** The plain 9-nuggets-to-1-ingot direction needed no new code — it's already a real vanilla recipe.

**Currency swap complete** (design doc Section 2) — real trade system researched against the decompiled source (a genuinely different, fully registry/datapack-driven model than the design doc originally assumed; no `VillagerTradesEvent`-style hook exists in this NeoForge version). All 13 professions (5 levels each, 2 nugget-priced trades per level) plus the wandering trader's 3 categories now trade in Gold Nuggets instead of Emeralds — 199 total datapack JSON files (trade entries + matching vanilla tag overrides), no Java code needed anywhere in this mechanic. Boot-smoke-tested clean (two invalid item ids caught and fixed along the way: `scute`→`turtle_scute`, `coral_block`→`tube_coral_block`). **Live confirmation (actually trade with villagers in-game) is still pending.**

Design settled but not yet built: the dropped-item bag (design doc Section 4) — every dropped item including death drops, 3-block clustering, right-click-only chest-style container, no capacity cap, despawns on the normal item-entity timer. Also newly flagged, ownership undecided: an NPC gossip/paid-tips mechanic (Section 5) that touches Lyfe's Cartographyr-skill `KnowledgeFactor` system.

Next: **live confirmation of the gambling recipe and the full currency swap** via `runClient` (spawn/find villagers of a few different professions, confirm nugget pricing). After that: start the dropped-item bag — the only remaining designed-but-unbuilt mechanic. The gossip mechanic's mod ownership needs deciding before it can be built at all.

See [context/classes/](context/classes/) for per-class reference.
