# Yconomics

Economy-themed mod for a modular Minecraft project (Minecraft Java Edition). Part of a suite of intercommunicating mods that expose public APIs so other mods — the user's own and third parties' — can integrate.

## Context directory — read this first

`context/` is meant to become a **separate private repo** (`yconomics-context`, mirroring `cartographyr-context`/`lyfe-context`), the same pattern used by the rest of the suite — **not yet created/pushed to GitHub as of 2026-09-25**, currently just a local subdirectory here. Once created, it should be gitignored from this repo and cloned as a subdirectory, same as the other two mods:

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
- Once `context/` is a real separate repo, it has its own git history, independent of this repo's commits — commit and push changes there separately.

## Status

Scaffolded 2026-09-25 (see [context/decisions.md](context/decisions.md)):
- Loader: **NeoForge**
- Minecraft version: **26.1.2**
- Java: **JDK 25** (standalone Eclipse Temurin, JAVA_HOME set) — same toolchain as the rest of the suite
- Group ID: `com.github.cerealklla.yconomics` / Mod ID: `yconomics`
- Project structure copied verbatim from Cartographyr/Lyfe's own MDK setup (`build.gradle`, `settings.gradle`, wrapper) — no Yconomics-specific build changes yet.

**First vertical slice: the "gambling" recipe** (design doc Section 3) — a real, working `GamblingRecipe extends CustomRecipe` (1 gold ingot → random 5–12 gold nuggets), registered, datapack-declared, unit tested (`GamblingRecipeTest`), boot-smoke-tested clean via `runServer`. **Live in-game confirmation (actually craft with it) is still pending.** The plain 9-nuggets-to-1-ingot direction needed no new code — it's already a real vanilla recipe.

Not yet started: the currency swap (villager/wandering-trader trades pricing in Gold Nuggets instead of Emeralds) and the dropped-item bag (a player's dropped items collecting into a single lootable, despawn-when-empty entity instead of scattering). Both are sketched at a high level in design-document.md Sections 2 and 4, with real open questions flagged in each — neither has a concrete implementation plan yet.

Not yet done, same as the rest of the suite at this stage: a GitHub repo for this code (public) and the `yconomics-context` repo (private) haven't been created — this has all been local-only work so far.

Next: **live confirmation of the gambling recipe** via `runClient`. After that: pick between the currency swap and the dropped-item bag as the next vertical slice, resolve that mechanic's open design questions (see design-document.md), and build it out to a real functional state rather than a stub — same "finish what you start" approach as the gambling recipe.

See [context/classes/](context/classes/) for per-class reference.
