# TOMC-Keep Advancing — Datapack & Trigger Reference

> Everything on this page is declared in **JSON**. Nothing here requires writing Java, except the one optional API in [§8](#8-the-public-gui-api) — and that is for another mod's author, not for a datapack.

This mod exists to answer a specific complaint about vanilla's advancement format: it can see what an item is, where a player is standing and what they killed, but it cannot see **time**, it cannot see **other mods**, it cannot see **other advancements**, it cannot tell **which GUI is open**, and it cannot check **whether a build was built**.

The five triggers below close exactly those five gaps. The mod's own 22 advancements are written with them; so can yours be.

- [1. What the triggers are for](#1-what-the-triggers-are-for)
- [2. `keepadvancing:timer_test` — how did a value move inside a time window?](#2-keepadvancingtimer_test--how-did-a-value-move-inside-a-time-window)
- [3. `keepadvancing:mod_loaded` — is that mod in the environment?](#3-keepadvancingmod_loaded--is-that-mod-in-the-environment)
- [4. `keepadvancing:advancements_made` — how much of a set is done?](#4-keepadvancingadvancements_made--how-much-of-a-set-is-done)
- [5. `keepadvancing:gui_open` — which GUI is open right now?](#5-keepadvancinggui_open--which-gui-is-open-right-now)
- [6. `keepadvancing:structure_present` — is that build standing here?](#6-keepadvancingstructure_present--is-that-build-standing-here)
- [7. Three display extensions](#7-three-display-extensions)
- [8. The public GUI API](#8-the-public-gui-api)
- [9. Library mode](#9-library-mode)
- [10. Compatibility and limits](#10-compatibility-and-limits)

---

## 1. What the triggers are for

| Trigger | The question it answers | Evaluated |
| --- | --- | --- |
| `keepadvancing:timer_test` | How did a value change inside a time window? | every tick, for listening players only |
| `keepadvancing:mod_loaded` | Is a mod with a matching package present? | once, when the player joins |
| `keepadvancing:advancements_made` | How many / what proportion of a set is complete? | every 20 ticks, for listening players only |
| `keepadvancing:gui_open` | Which GUI is open at this instant? | every tick |
| `keepadvancing:structure_present` | Is a 100 %-identical structure nearby? | every 20 ticks |

Two of them evaluate on a timer rather than on an event: `advancements_made` and `structure_present`. Vanilla fires nothing when a player completes an advancement, and this mod deliberately carries no mixin on that path, so both are polled at 1 Hz. The consequence is a **worst-case one-second delay** between the condition becoming true and the toast appearing.

Writing a trigger is ordinary advancement JSON — the only unusual part is the `trigger` name:

```json
{
  "display": {
    "icon": { "id": "minecraft:crafting_table" },
    "title": "GUI test",
    "description": "Open a crafting table",
    "frame": "task",
    "show_toast": true,
    "announce_to_chat": false
  },
  "criteria": {
    "open_crafting": {
      "trigger": "keepadvancing:gui_open",
      "conditions": { "menu": ["minecraft:crafting"] }
    }
  }
}
```

Drop it in `data/<your_namespace>/advancement/` — in a datapack or in your own mod's resources — and it loads like any other advancement. A trigger you have not declared simply does not fire; a trigger condition that is malformed makes **that one file** fail to load, with the reason in the log. It never takes the rest of the datapack down with it.

> **A note on empty results.** Throughout this page, an empty resolution means **the criterion is permanently false**, never "0 out of 0, therefore complete". A mistyped anchor id fails closed, loudly, once.

---

## 2. `keepadvancing:timer_test` — how did a value move inside a time window?

> Put anything that reads as an integer into a time window, then ask what happened to it.

```json
{
  "trigger": "keepadvancing:timer_test",
  "conditions": {
    "source": "minecraft.used:minecraft.totem_of_undying",
    "check": "increase_count",
    "operator": ">=",
    "threshold": 10,
    "window": 600
  }
}
```

| Field | Required | Meaning |
| --- | --- | --- |
| `source` | ✅ | What to observe — see below |
| `check` | ✅ | How to summarise the window — see below |
| `operator` | ✅ | `>=` `>` `<=` `<` `==` `!=` |
| `threshold` | ✅ | The value to compare against |
| `window` | depends on `check` | Window length in **ticks** (20 = 1 second), 1 – 72000 (1 hour) |

### 2.1 `source` — two kinds of thing you can observe

**Vanilla statistic criterion names.** This is literally the string you would type in `/scoreboard objectives add <name> <this string>`. If that command works, the string is valid here.

- Registry-shaped: `minecraft.used:minecraft.totem_of_undying`, `minecraft.mined:minecraft.stone`, `minecraft.crafted:minecraft.crafting_table`, `minecraft.killed:minecraft.zombie`, `minecraft.broken:minecraft.diamond_pickaxe`, `minecraft.picked_up:…`, `minecraft.dropped:…`, `minecraft.killed_by:…`
- Custom-shaped: `minecraft.custom:minecraft.jump`, `…:minecraft.deaths`, `…:minecraft.play_time`, `…:minecraft.time_since_death`, `…:minecraft.damage_dealt`, `…:minecraft.mob_kills`, `…:minecraft.fish_caught` — about a hundred of these exist

**Built-in live gauges.** Not statistics: values read out of the player at that moment.

| id | What it reads |
| --- | --- |
| `keepadvancing:health` | Health, rounded to whole points |
| `keepadvancing:air` | Air |
| `keepadvancing:food` | Food level |
| `keepadvancing:xp_level` | Experience level |
| `keepadvancing:y` | Y coordinate |
| `keepadvancing:sneak_press` | **Sneak presses** — counted on the rising edge, so standing under a low ceiling, flying or riding cannot inflate it |

### 2.2 `check` — seven ways to summarise a window

Given samples `v(t₀…tₙ)` in the window and `dᵢ = v(tᵢ) − v(tᵢ₋₁)`:

| `check` | Meaning | Needs `window` |
| --- | --- | --- |
| `value` | The current value itself — read from the **lifetime** statistic | ❌ not allowed |
| `increase_count` | `#{dᵢ > 0}` — how many times it went up | ✅ |
| `decrease_count` | `#{dᵢ < 0}` — how many times it went down | ✅ |
| `change_count` | `#{dᵢ ≠ 0}` — how many times it moved | ✅ |
| `total_increase` | `Σ max(dᵢ, 0)` — how much it gained | ✅ |
| `total_decrease` | `Σ max(−dᵢ, 0)` — how much it lost, as a **positive** magnitude | ✅ |
| `delta` | `v(tₙ) − v(t₀)` — the signed net change | ✅ |

### 2.3 Recipes

```json
{"source":"keepadvancing:sneak_press","check":"increase_count","operator":">=","threshold":5,"window":20}
```
Five sneaks inside one second.

```json
{"source":"keepadvancing:health","check":"decrease_count","operator":">=","threshold":3,"window":200}
```
Took damage three times in ten seconds.

```json
{"source":"keepadvancing:air","check":"total_decrease","operator":">=","threshold":200,"window":600}
```
Lost 200 air in thirty seconds — drowning, measured.

```json
{"source":"minecraft.custom:minecraft.time_since_death","check":"total_decrease","operator":">=","threshold":1,"window":40}
```
Died inside the last two seconds (a reset to zero counts as a drop).

```json
{"source":"minecraft.custom:minecraft.time_since_death","check":"value","operator":">=","threshold":72000}
```
Alive for an hour since the last death. `value` needs no window because it reads the lifetime statistic — and note that this therefore fires **immediately on joining** an existing world where that is already true.

### 2.4 Things that will bite you

- **`==` is unreliable under sampling.** A value that crosses several integers inside one tick is skipped whole. Prefer `>=` and `<=`.
- **Every `check` except `value` requires `window`.** Omitting it makes the advancement **fail to load** — it does not silently degrade to a lifetime comparison. Deliberately loud.
- **A bad `window`** (missing, ≤ 0, or > 72000) also fails that one advancement to load, with the reason in the log.
- **Vanilla statistics are monotonically non-decreasing** — except `time_since_death` and `time_since_rest`. So on a plain statistic, `decrease_count` and `total_decrease` are effectively limited to those two. If you want a value that can fall, use a gauge: `health`, `air`, `food`, `y`.

---

## 3. `keepadvancing:mod_loaded` — is that mod in the environment?

```json
{
  "trigger": "keepadvancing:mod_loaded",
  "conditions": {
    "package": {"regex": "io\\.github\\.thatone0502"},
    "exclude_self": true
  }
}
```

| Field | Required | Meaning |
| --- | --- | --- |
| `package` | ✅ | `"io.github.thatone0502"` = **exact package equality**; `{"regex": "…"}` = a regular expression |
| `exclude_self` | ❌ | Whether to exclude Keep Advancing itself. Default `true` |

- The evidence is the **actual Java package name**, derived by opening each loaded mod's jar or directory and enumerating its `.class` entries. It is not the mod id, and not the display name.
- **A plain string is exact equality.** Because package names are reconstructed from `.class` paths, `"package": "io.github.thatone0502"` requires a class to exist *at that level* — a mod whose classes all live under `io.github.thatone0502.keepadvancing.*` does not produce the parent package, and will **not** match. For a package-tree prefix, write a regex.
- **A regex uses `find()`, not a full match.** `{"regex": "io\\.github\\.thatone0502"}` therefore matches that prefix and anything below it. Anchor it yourself with `^…$` if that is what you want.
- Escape every `.` in a regex as `\\.` — an unescaped `.` means "any character".
- The check happens **when the player joins**, because the mod set is fixed once the server is up. There is no per-tick polling.
- Scanning is done on a **background thread** after server start, so it costs nothing at login or per tick. As a reference point, 43 mods took 221 ms.

---

## 4. `keepadvancing:advancements_made` — how much of a set is done?

> Pick a set of advancements with a selector, then count how many of them (or what fraction) are complete.

```json
{
  "trigger": "keepadvancing:advancements_made",
  "conditions": {
    "list": [
      { "through": "minecraft:story/root" },
      { "only": "minecraft:adventure/root" }
    ],
    "require": { "type": "proportion", "operator": ">=", "value": 1.0 }
  }
}
```

| Field | Required | Meaning |
| --- | --- | --- |
| `player` | ❌ | A standard player predicate, written exactly as in any other vanilla criterion |
| `list` | ✅ | At least one selector; the results are **unioned** and de-duplicated |
| `require` | ✅ | `{type, operator, value}` — see below |
| `include_hidden` | ❌ | Default `true`. With `false`, advancements marked `"hidden": true` are dropped (9 of them across vanilla's five tabs) |
| `require_display` | ❌ | Default `true`. With `false`, advancements **with no `display`** are counted too (1459 of them in vanilla, all recipe-unlock nodes) |

### 4.1 The seven selectors

The first six line up, position for position, with vanilla's `/advancement grant <target> <mode> <advancement>`; `after`, `before` and `between` are additions vanilla does not have.

| Key | Vanilla equivalent | The set it yields |
| --- | --- | --- |
| `"X"` or `{"only":"X"}` | `only X` | `{X}` |
| `{"from":"X"}` | `from X` | `{X}` plus every descendant of X |
| `{"through":"X"}` | `through X` | X's ancestor chain + `{X}` + every descendant |
| `{"until":"X"}` | `until X` | X's ancestor chain + `{X}` |
| `{"after":"X"}` | — | Every descendant of X, **excluding X** |
| `{"before":"X"}` | — | X's ancestor chain, **excluding X** |
| `{"between":["A","B"]}` | — | Every node on the path from A to B, **including both ends** |

### 4.2 `require`

| Field | Required | Meaning |
| --- | --- | --- |
| `type` | ✅ | `"proportion"` (0.0 – 1.0) or `"count"` |
| `operator` | ❌ | `>=` `>` `<=` `<` `==` `!=`, default `>=` |
| `value` | ✅ | The threshold. `proportion` must be inside 0.0 – 1.0; `count` must not be negative. Out-of-range values make that one advancement fail to load |

### 4.3 Measured set sizes

These were produced by the mod's own server-side self-check on vanilla 1.21.11 with the mod installed, so they can be counted against directly:

| Selector | Size |
| --- | --- |
| `{"only":"minecraft:story/root"}` | 1 |
| `{"from":"minecraft:story/root"}` | 17 |
| `{"after":"minecraft:story/root"}` | 16 |
| `{"through":"minecraft:story/smelt_iron"}` | 17 |
| `{"until":"minecraft:story/smelt_iron"}` | 4 |
| `{"before":"minecraft:story/smelt_iron"}` | 3 |
| `{"between":["minecraft:story/root","minecraft:story/smelt_iron"]}` | 4 |
| `{"through":"minecraft:recipes/root"}` with `require_display: true` | **0** |
| `{"through":"minecraft:recipes/root"}` with `require_display: false` | **1459** |

(`from: story/root` is 17 rather than vanilla's own 16 because this mod hangs one advancement under the story tab — which is the point: it counts the *whole loaded tree*.)

### 4.4 Recipes

```json
{"list":[{"through":"minecraft:adventure/root"}],"require":{"type":"proportion","value":1.0}}
```
Finish the entire Adventure tab.

```json
{"list":[{"through":"minecraft:story/root"},{"through":"minecraft:nether/root"},
         {"through":"minecraft:end/root"},{"through":"minecraft:adventure/root"},
         {"through":"minecraft:husbandry/root"}],
 "require":{"type":"count","operator":">=","value":100}}
```
Any 100 advancements across vanilla's five tabs.

```json
{"list":[{"only":"keepadvancing:tomc/root"}],"require":{"type":"count","value":1}}
```
The TOMC tab's root node.

### 4.5 Things that will bite you

- **`from` / `through` / `until` include the anchor; `after` / `before` do not.** This matches `/advancement` exactly — do not go by intuition.
- **`between` requires A and B to be on the same ancestor chain**, because every advancement has exactly one parent. Either order works. If they are not on one chain the result is empty and a warning is logged once — for example `between:["minecraft:adventure/root","minecraft:story/root"]`.
- **`minecraft:recipes/root` is a sixth root of its own**, and its 1459 recipe-unlock descendants have no `display` at all. The default `require_display: true` filters the entire set away, which is why `through: minecraft:recipes/root` resolves to **empty**. To count recipes, say `"require_display": false` explicitly.
- **An empty resolution means the criterion is never true**, not "0 of 0, so trivially complete". The cause may be a mistyped anchor or one of the two filters.
- An anchor id that does not exist, or a resolution larger than **4096** entries, logs **one** warning and then never fires. The result is cached; the log will not fill up.
- Evaluation is **every 20 ticks**. Vanilla has no server-side "player completed an advancement" event, and this mod deliberately carries no mixin, so a timer is what is left. Expect up to one second of lag before the toast.
- As with `timer_test`, **only players who actually have a criterion written against this trigger are evaluated.** Nobody listening means nothing is computed.
- It counts the **entire advancement tree the server has loaded** — including advancements from other datapacks and mods. That is what makes it useful as a general-purpose preset, and it is also why the numbers above move as soon as anything else adds a node.

---

## 5. `keepadvancing:gui_open` — which GUI is open right now?

> True for as long as the GUI is open; false the tick it closes.

```json
{
  "trigger": "keepadvancing:gui_open",
  "conditions": {
    "menu":   ["minecraft:crafting", {"regex": "minecraft:generic_9x[1-6]"}],
    "screen": ["net.minecraft.client.gui.screens.inventory.InventoryScreen", {"regex": ".*EditScreen"}],
    "custom": [{"regex": "leveling:"}]
  }
}
```

| Field | Required | Meaning |
| --- | --- | --- |
| `menu` | ❌ | **Server-side**: matches the container menu's `MenuType` registry id |
| `screen` | ❌ | **Reported by the client**: matches the current `Screen`'s fully-qualified class name |
| `custom` | ❌ | A GUI id reported by **another mod** — see [§8](#8-the-public-gui-api) |

- **The three are OR-ed.** Any one match makes the criterion true.
- **All three empty is a datapack load error.** That is always a mistake and should not silently become a criterion that never fires.
- Each list element: a string is **exact equality**; `{"regex": "…"}` is a regex with `find()` (substring) semantics — anchor it yourself if you need to.
- The semantics are **"open right now"**, evaluated every tick. The tick it matches is the tick it is granted.
- **`menu` cannot see the player's inventory.** `InventoryMenu`'s `MenuType` is `null` (it is constructed as `AbstractCraftingMenu.<init>(null, 0, 2, 2)`), and when a player has nothing open, `containerMenu` is exactly the `inventoryMenu` — the server cannot distinguish "empty-handed" from "inventory open". **The inventory is reachable only through `screen`.**
- **`screen` comes from the client**, so it only has a value when the client has this mod. Half of `gui_open` — the `menu` half — still works on a server whose clients do not.

Common `menu` values (MenuType registry ids, measured on 1.21.11):

`minecraft:crafting`, `minecraft:enchantment`, `minecraft:anvil`, `minecraft:furnace`, `minecraft:blast_furnace`, `minecraft:smoker`, `minecraft:generic_9x1` … `generic_9x6`, `minecraft:generic_3x3`, `minecraft:crafter_3x3`, `minecraft:hopper`, `minecraft:lectern`, `minecraft:loom`, `minecraft:merchant`, `minecraft:shulker_box`, `minecraft:smithing`, `minecraft:cartography_table`, `minecraft:stonecutter`, `minecraft:brewing_stand`, `minecraft:beacon`, `minecraft:grindstone`.

Common `screen` values (client class names under `net.minecraft.client.gui.screens`, measured on 1.21.11):

- Containers — `inventory.InventoryScreen`, `inventory.CreativeModeInventoryScreen`, `inventory.CraftingScreen`, `inventory.EnchantmentScreen`, `inventory.AnvilScreen`, `inventory.FurnaceScreen`, `inventory.BlastFurnaceScreen`, `inventory.SmokerScreen`, `inventory.BrewingStandScreen`, `inventory.HopperScreen`, `inventory.DispenserScreen`, `inventory.ShulkerBoxScreen`, `inventory.MerchantScreen`, `inventory.LoomScreen`, `inventory.StonecutterScreen`, `inventory.SmithingScreen`, `inventory.GrindstoneScreen`, `inventory.CartographyTableScreen`, `inventory.BeaconScreen`, `inventory.LecternScreen`, `inventory.ContainerScreen`, `inventory.CrafterScreen`, `inventory.NautilusInventoryScreen`, `inventory.HorseInventoryScreen`
- Editing — `inventory.SignEditScreen`, `inventory.HangingSignEditScreen`, `inventory.BookEditScreen`, `inventory.BookViewScreen`, `inventory.BookSignScreen`, `inventory.CommandBlockEditScreen`, `inventory.StructureBlockEditScreen`, `inventory.JigsawBlockEditScreen`
- Others — `ChatScreen`, `PauseScreen`, `DeathScreen`, `TitleScreen`, `advancements.AdvancementsScreen`
- To match every container screen at once: `{"regex": "^net\\.minecraft\\.client\\.gui\\.screens\\.inventory\\..*Screen$"}`

**What it cannot see:** a pure HUD overlay — anything drawn straight onto the HUD without being a `Screen` (another mod's experience panel, a custom info page). The screen events never fire for those. They are reachable only through the `custom` field, which means the mod that draws them has to say so out loud.

> `inventory.EffectsInInventory` — the potion-effect column on the right of the inventory — is **not** a separate `Screen` and will not match.

---

## 6. `keepadvancing:structure_present` — is that build standing here?

> Sweep the area around the player for a structure that matches a template 100 %.

```json
{
  "trigger": "keepadvancing:structure_present",
  "conditions": {
    "structure": "mymod:hut",
    "radius": { "horizontal": 32, "vertical": 16 },
    "allow_rotation": false,
    "allow_mirror": false,
    "match_nbt": false,
    "include_air": true
  }
}
```

| Field | Required | Default | Meaning |
| --- | --- | --- | --- |
| `structure` | ✅ | — | Structure id, i.e. `data/<namespace>/structure/<path>.nbt` |
| `radius` | ❌ | 16 | Either one integer (horizontal = vertical) or `{"horizontal": H, "vertical": V}` |
| `allow_rotation` | ❌ | `false` | Count all four rotations as matches |
| `allow_mirror` | ❌ | `false` | Count all three mirrors as matches |
| `match_nbt` | ❌ | `false` | Compare block entities too — chest contents, sign text, furnace progress |
| `include_air` | ❌ | `true` | `true`: the template's air must be air as well. `false`: air in the template is ignored |

- **It has to match 100 %, and there is no tolerance field.** This is the strict semantics of the structure block's old detect mode.
- **One criterion, one structure.** For "a village *or* a smithy", write two criteria and put them in the same `requirements` group.
- **Orientation count** is rotation 4 × mirror 3 = 12. Both off is 1 (fastest); rotation only is 4; mirror only is 3; both is the slowest.
- Evaluated **every 20 ticks**.
- **The file lives in a singular `structure` directory**: `data/<namespace>/structure/<name>.nbt`. `.snbt` (the human-readable form) is accepted too.
- To produce one: build it in game with a structure block and save it in `SAVE` mode. Note that **1.21.11's structure block no longer has a `DETECT` mode** (`StructureMode` is only `SAVE / LOAD / CORNER / DATA`), which is exactly why this trigger exists.
- **Structure void and air are not the same thing.** Vanilla skips structure voids when saving and does not write them to the file, but it does write ordinary air. So with `include_air: true`, "a position that is air in the template must be air in the world".
- ⚠ **The cost of `include_air: true`** is the single most common reason a correct-looking build fails to match: put **anything** extra inside the bounding box — a torch, grown grass, flowing water, footprints — and it fails. If the structure is clearly right and it will not fire, look here first. Use `include_air: false` if you want it looser.
- **Performance** is anchor-scanned: one highly identifying block is chosen from the template (non-air, rarest in the template) and the search looks only for *that* block inside the radius. Each hit back-projects a candidate origin and verifies the template in a pre-computed order, giving up at the first mismatch. The cost therefore does not scale with the volume of the radius, only with how often the anchor appears inside it. More than **4096** anchor hits logs one warning and **skips that tick**.
- A structure id that cannot be read (typo, missing file) logs a warning **once**, and the criterion is then permanently false.

---

## 7. Three display extensions

Three optional additions to vanilla's advancement format. Vanilla's own syntax keeps working unchanged in every case, and none of them can make a datapack fail to load — a bad value degrades to the default and logs a warning.

### 7.1 `icon` may be a string

```json
"display": { "icon": "mymod:icon/star", "title": "…" }
```

A string is **the texture id directly**, resolved exactly as vanilla resolves textures: `<namespace>:<path>` → `assets/<namespace>/textures/<path>.png`. The line above therefore means `assets/mymod/textures/icon/star.png`.

> ⚠ **Never write the `textures/` prefix or the `.png` suffix.** `mymod:textures/icon/star.png` resolves to `assets/mymod/textures/textures/icon/star.png.png`, and you get the purple-and-black missing texture. This is the same trap the `background` field fell into years earlier — the field changed, the trap did not.

Writing the vanilla item object still behaves identically. Internally the codec is `either(Identifier, ItemStack)`, **left first**, so a string takes the texture branch and an object takes the item branch.

### 7.2 `background` may be an object

```json
"background": {
  "asset_id": "mymod:gui/backgrounds/grid",
  "mode": "cover",
  "attachment": "local",
  "width": 64,
  "height": 48
}
```

| Field | Values | Behaviour |
| --- | --- | --- |
| `asset_id` | texture id | Required. Same resolution rule as 7.1 — **no `textures/`, no `.png`** |
| `mode` | `tile` (default) | Vanilla behaviour: a 16×16 tile |
| | `fill` | Stretch to fill the region, **distorting** the image |
| | `cover` | Scale uniformly until it covers the region, cropping the excess |
| | `contain` | Scale uniformly to fit entirely, possibly leaving margins |
| `attachment` | `fixed` (default) | Pinned to the viewport; the background does not move when content scrolls |
| | `local` | Moves with the content; the bounding box is `max(viewport, content)` on both axes |
| `width` / `height` | integer | Pixel size. Omit to read the real texture size at runtime |

`mode` and `attachment` are case-insensitive; an unknown value falls back to the default with a `WARN` and does **not** fail the datapack. Writing a plain string is the same as `mode=tile` + `attachment=fixed`.

### 7.3 `multi_parents` — more than one upstream line

```json
{
  "parent": "mymod:converge/y",
  "multi_parents": ["mymod:converge/z", "mymod:converge/x"],
  "criteria": { "impossible": { "trigger": "minecraft:impossible" } }
}
```

`parent` remains the **main parent** — it still decides the unlock chain and the vertical level, and a root advancement still has no `parent` at all. `multi_parents` only adds drawn edges: it does not change what unlocks what, and it does not participate in the tree insertion's success or failure.

- An edge pointing at an id that does not exist, at itself, or that would create a cycle is **skipped with a warning**; the rest still apply.
- The ids must be nodes **in the same advancement tree**, and should normally be **siblings or higher up**. Pointing at a node below is a cycle and will be rejected.
- Layout for a connected component containing `multi_parents` is computed **on the server** and shipped with the advancement, so the client runs no layout code at all. Components without it keep vanilla's exact coordinates — not a pixel moves.
- ⚠ **Overlapping lines are easy to produce.** If several parents happen to land in the same column, their edges coincide and look like a single line. Spread the parents horizontally when you build the tree.

### 7.4 Under BetterAdvancements

BetterAdvancements does not use vanilla's advancement screen — it implements its own `BetterAdvancementsScreen` / `BetterAdvancementTab` / `BetterAdvancementWidget` and leaves a single mixin redirecting vanilla's screen to it. A mod that only patches vanilla's widgets therefore has no effect at all while BA is installed. This mod ships a second set of hooks for BA's own classes, selected by whether `betteradvancements` is loaded, so all three extensions work there too.

One deliberate trade-off: BA tints an unearned icon grey, while the string-texture branch draws a plain texture and **does not tint**. An unearned string icon therefore does not go grey under BA.

---

## 8. The public GUI API

`io.github.thatone0502.keepadvancing.api.KeepAdvancingGuiApi`

```java
// Client side: I am drawing my own GUI right now
KeepAdvancingGuiApi.reportCustomGui(Identifier.fromNamespaceAndPath("mymod", "xp_panel"));

// Not any more
KeepAdvancingGuiApi.reportCustomGui(null);
```

| | |
| --- | --- |
| **Why it exists** | A GUI that is not a `Screen` — an overlay drawn straight onto the HUD — is invisible to both the server and the screen events. The only way to see it is to have the mod that draws it say so |
| **How to depend on it** | Add this mod as a **`compileOnly`** dependency. No Fabric entrypoint is needed: these are static methods |
| **When to call it** | Client side, once whenever the "am I showing it" state changes. The mod's client entrypoint reads the field once per tick and sends nothing when the value has not changed |
| **Calling it on a server** | Legal. The value lives in one static field and only the client entrypoint reads it, so there are no side effects on a dedicated server |
| **The matching criterion** | `"custom": ["mymod:xp_panel", {"regex": "mymod:.*"}]` |

This is the only Java API this mod exposes. The five triggers are pure data; nobody needs to write Java for those.

---

## 9. Library mode

If you want the triggers and none of the content, set one key in `config/keepadvancing.json`:

```json
{
  "load_builtin_content": false
}
```

| | With `true` (default) | With `false` |
| --- | --- | --- |
| `data/keepadvancing/advancement/**` (24 files) | Loaded — the TOMC tab exists | Not loaded; the tab disappears |
| `keepadvancing:tomc_trophy` block and item | Registered, present in the creative tab | Not registered; the `mineable/pickaxe` tag no longer references it |
| The 6 overwritten vanilla advancements | Re-parented / re-iconed | **Vanilla loads all of its own, unmodified** |
| The five triggers | Available | **Still available** |
| `KeepAdvancingGuiApi` | Available | **Still available** |

The change takes effect on `/reload` or a restart, since the resource condition is evaluated while datapacks are being parsed.

> **A rule worth stealing.** The six overwritten files do *not* use a resource condition, even though they are the obvious candidates. When a resource condition is false, a lower-priority pack's copy of the same id does **not** take over — the registry id vanishes. Applying the condition to those six measurably deleted six *vanilla* advancements (1584 → 1578). They instead live in a built-in data pack that is only registered when the switch is on, so with it off they simply are not there and vanilla's own versions load untouched. **Rule: resource conditions are for files in your own namespace. Anything that overwrites a vanilla registry id belongs in a data pack you register or do not.**

---

## 10. Compatibility and limits

- **Both sides need the mod, at the same version.** Neither half negotiates a protocol version; vanilla's advancement network format is extended symmetrically on both ends, so a vanilla peer reads bytes it does not expect and fails to deserialise. A vanilla client cannot join a server running this mod, and vice versa.
- **BetterAdvancements is supported** (verified against `BetterAdvancements-Fabric-1.21.11-0.4.8.54`). Its hooks are declared `required: false` with every injection point at `require = 0`, so a future rewrite of BA produces a log warning rather than a crash. The unearned-icon tint is the one known difference.
- **`multi_parents` edges must be acyclic**, and they do not participate in unlocking — they affect only the drawn lines and the layout.
- **Only components containing `multi_parents` are re-laid-out**, so adding one to an advancement slightly moves its siblings. That is by design: the parent has to be able to sit centred between its children.
- **`mode=local`'s bounding box** is `max(viewport, content)` on both axes. BA changes the viewport size and the value is read at runtime, but a viewport other than 234×113 only occurs under BA; vanilla's is always 234×113.
- **Currently built and tested against Minecraft 1.21.11 on Fabric.** The five triggers themselves are expressed in vanilla data format plus this mod's Java layer; the Java layer is the part that would be rewritten for another loader.

---

Development assisted by DeepSeek Harness (deepseek-flash).
