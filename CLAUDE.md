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

**Hostile mob drops + Coin Purse built** (design doc Section 2) — every hostile mob (`Enemy` interface) drops 1-3 Gold Nuggets on death (`mob.HostileMobDrops`). Every player always carries one unstackable `currency.CoinPurseItem` that auto-collects loose nuggets on pickup and on any container close (deliberately trigger-based, not continuous, so withdrawn nuggets survive long enough to actually use), auto-refills for villager/wandering-trader trading, survives being tossed, and on death is stripped before vanilla's drop logic runs — 10% of its balance drops as loose nuggets, 90% is returned on respawn (`currency.CoinPurseListener`). Boot-smoke-tested clean (one real bug caught: `Item.Properties` needs an explicit `.setId(...)` in this MC version or registration crashes). **Live confirmation is still pending.**

**Dropped-item bag built and playtested through several rounds** (design doc Section 4) — `bag.LootBagEntity`, a world entity (not a block) holding a 54-slot container. `bag.LootBagListener` handles capture: voluntary drops are watched via `ItemTossEvent` and swept in once they actually touch the ground (`ServerTickEvent.Post` checking `onGround()`), while death drops are captured directly from the inventory at `LivingDeathEvent` (before vanilla's own drop logic runs), skipping the fall entirely. Clustering is a 3-block-radius search for an existing bag before creating a new one. 54 slots isn't literally unbounded, but the design's own "if there's no room, scatter on the ground" fallback covers that exactly. First live playtest confirmed the clustering/capture mechanics genuinely work, then surfaced three real, client-only bugs in a row — none catchable by `runServer` boot-smoke-testing, since a dedicated server never renders anything:
1. A missing `EntityRenderer` registration crashed the client the instant a bag spawned in view.
2. `isPickable()` defaulting to `false` left the entity with a floating nametag but nothing to actually click on.
3. A `static final ItemStack` field (added while giving the bag a visible icon) was built too early in the boot sequence and crashed resource-pack loading entirely — reported as "the client never loaded."

All three fixed. Along the way, three playtest-requested UI improvements landed too: a visible floating `Items.BUNDLE` icon (`bag.client.LootBagRenderer`), auto-closing the screen once the bag empties (`stillValid(Player)` returning `!isRemoved()`, leaning on `ServerPlayer#doTick()`'s existing generic auto-close check rather than new tracking code), and a "Take All" button — which needed a real custom menu/screen of our own (`bag.LootBagMenu`, `bag.client.LootBagScreen`, `registration.ModMenus`) since vanilla's reused `ChestMenu.sixRows` binds to a built-in `MenuType` with no way to add a button. Boot-smoke-tested via a real `runClient` session this time (learned the hard way that's required for anything client-visible). **Live confirmation of the UI polish (icon, auto-close, Take All) is still pending** — the underlying mechanics were already confirmed live.

Newly flagged, ownership undecided: an NPC gossip/paid-tips mechanic (Section 5) that touches Lyfe's Cartographyr-skill `KnowledgeFactor` system — not started, needs a decision on which mod owns it first.

Next: **live confirmation of the bag's UI polish**, then a full pass over everything built this session together — the gambling recipe, the full currency swap, hostile mob drops, the Coin Purse, and the dropped-item bag (the user's own stated test plan).

See [context/classes/](context/classes/) for per-class reference.
