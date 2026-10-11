<p align="center">
  <img src="fabric-1.21.11/src/main/resources/assets/keepadvancing/logo.png" alt="TOMC-Keep Advancing" width="800">
</p>

<p align="center">
  <a href="docs/README.en.md">English</a> &nbsp;·&nbsp; <a href="docs/README.zh-CN.md">简体中文</a>
</p>

> [!WARNING]
> **早期开发阶段 / Early Development**
>
> 本项目仍处于早期开发阶段。进度内容、功能与配置项仍可能调整，数据格式与模组行为不保证向后兼容。升级前请备份存档与 `config/keepadvancing.json`。
>
> This project is still in an early stage of development. Advancement content, features and options may still change, and neither the data format nor the mod's behaviour is guaranteed to stay backward compatible. Back up your world and `config/keepadvancing.json` before updating.
>
> **AI 辅助创作 / AI-Assisted**
>
> 本项目在开发过程中使用了 AI 辅助（deepseek-flash）。且并不是所有代码、文档与设计均经过人工审查与测试。
>
> This project was developed with assistance from AI (deepseek-flash). NOT all code, documentation, and design have been reviewed and tested by humans.

# TOMC-Keep Advancing

> 📘 **Datapack & trigger reference** — [docs/datapack.en.md](docs/datapack.en.md)

**TOMC-Keep Advancing** is the advancement layer of the TOMC series — a datapack-driven advancement mod for **Fabric**. It adds a **TOMC tab** and **22 advancements that hang inside vanilla's own tabs**, plus **five triggers** that let an ordinary JSON file express what vanilla's advancement format cannot: a timer window, a loaded mod, a group of advancements, an open GUI, a nearby structure. The content is JSON; the Java side exists only for the parts JSON cannot say — and every piece of it can be switched off, leaving behind a trigger library that other mods can depend on.

## Features

### 🏆 A tab that finds the rest of the series

The TOMC tab appears the moment you are running any other mod from this series — and it decides that by **Java package name**, not by mod id:

- Its root node's criterion is `keepadvancing:mod_loaded` with the regex `io\.github\.thatone0502`. A mod only has to be *from this author* to count; **a future TOMC mod needs no edit to any file here** to be recognised.
- The judgement runs **when the player joins**. The mod set is fixed for the life of the server, so this is not a per-tick poll, and the answer is cached.
- `exclude_self` is on by default, so the mod does not grant its own root node to itself. It is a mod about the *rest* of the series.
- The tab draws its own **16×16 tiled background** rather than referencing a vanilla block texture, so a vanilla texture reshuffle cannot move it.
- Vanilla has **no separate field for a tab's name** — the label under the tab is the *root node's* title. That is why it reads 「欢迎来到“TOMC”」/ *Welcome to TOMC* rather than a bare "TOMC". It is also why the root node is the only entry whose title is doing two jobs.

### 🥚 22 advancements woven into vanilla's own tabs

These are not collected under the TOMC tab. They are **children of vanilla's existing chains**, so the vanilla tab shows them where you already look:

- The spyglass set grows out of `minecraft:adventure/root` and out of vanilla's own `spyglass_at_*` nodes: **What Is It?** → **Is It a Lead?** → **Holy Creep**, and **Is It a Girl?**, **Turn It back!**, **Is It a Terminal?** → **Stop There!**
- The temple set is a five-link chain that starts on a vanilla node and then feeds back into one: **Raid** (village) → **Egyptian Wisdom** (desert pyramid) → **Mayan Wisdom** (jungle temple) → **Woodland Explorer** (mansion) → **Ocean Explorer** (monument) → and *that* re-parents vanilla's own **Adventuring Time** and **The Far Lands**.
- Four of them are **hidden until earned**, exactly as vanilla's hidden advancements behave: **Spaceman**, **You Monster!**, **Plants vs. Zombies** and **Ascended**.
- Six carry **experience rewards**: 500 for Serious Dedication… Again…, Woodland Explorer and Ocean Explorer; 12 000 for Spaceman, Ascended and The Far Lands.

| Advancement | Hangs off | What unlocks it |
| --- | --- | --- |
| **Sticky Keys** | `adventure/root` | Sneak pressed 5 times inside any 20-tick window |
| **Acquire Software** | `story/smelt_iron` | A Gold Ingot appears in your inventory |
| **Serious Dedication… Again…** | `husbandry/obtain_netherite_hoe` | Break a Netherite Hoe |
| **Oops** | `end/elytra` | Glide with an Elytra at exactly 431 damage |
| **Spaceman** *(hidden)* | `end/elytra` | Reach y ≥ 100 000 |
| **You Monster!** *(hidden)* | `adventure/kill_a_mob` | Hit a dolphin |
| **Plants vs. Zombies** *(hidden)* | `adventure/kill_a_mob` | Attack an undead mob while holding a plant |
| **Overpowered** | `husbandry/root` | Eat an enchanted golden apple |
| **What Is It?** | `adventure/root` | Look at any entity through a spyglass |
| **Is It a Lead?** | What Is It? | Spyglass a Wandering Trader |
| **Holy Creep** | Is It a Lead? | Spyglass a Creeper |
| **Is It a Girl?** | What Is It? | Spyglass a Piglin |
| **Turn It back!** | `adventure/spyglass_at_ghast` | Spyglass a fireball |
| **Is It a Terminal?** | `adventure/spyglass_at_dragon` | Spyglass an End Crystal |
| **Stop There!** | Is It a Terminal? | Spyglass a shulker bullet |
| **Raid** | `adventure/sleep_in_bed` | Stand inside any village |
| **Egyptian Wisdom** | Raid | Stand inside a desert pyramid |
| **Mayan Wisdom** | Egyptian Wisdom | Stand inside a jungle temple |
| **Woodland Explorer** | Mayan Wisdom | Stand inside a woodland mansion |
| **Ocean Explorer** | Woodland Explorer | Stand inside an ocean monument |
| **Ascended** *(hidden)* | `adventure/totem_of_undying` | Use 10 Totems of Undying inside any 30-second window |
| **The Far Lands** | `adventure/adventuring_time` | Reach \|x\| or \|z\| ≥ 29 999 950 |

### ✂️ Six vanilla advancements re-pointed

Six vanilla files are deliberately **overwritten**, and only two of them are cosmetic. The other four exist so the new chain has somewhere to hang:

| Vanilla advancement | Change |
| --- | --- |
| Those Were the Days *(spyglass_at_parrot)* | Parent → **Holy Creep** |
| Is It a Balloon? *(spyglass_at_ghast)* | Parent → **Is It a Girl?** |
| Is It a Plane? *(spyglass_at_dragon)* | Parent → **What Is It?** |
| Adventuring Time | Parent → **Ocean Explorer** |
| Very Very Frightening | Icon → a Channeling I trident |
| A Complete Catalogue *(obtain_netherite_hoe)* | Icon → a Netherite Hoe at damage 2031 |

Because vanilla's own nodes now sit *below* the new ones, finishing a TOMC entry is part of finishing the vanilla tab — and because these are overwrites of the same registry id, **another datapack or mod that edits the same six files will fight over them** (last one loaded wins). They are kept in a separate built-in data pack precisely so they can be dropped in one move; see [Configuration](#configuration).

### 🏅 The TOMC Trophy

One block, two blocks tall, with two interactions and no GUI:

- **Right-click** it — empty-handed or holding anything — for **Luck II for 10 minutes**, with an amethyst place sound. No cooldown, so you can refresh it as often as you like.
- **Sneak-right-click with an empty hand** takes the whole trophy — both halves — straight into your inventory, no dropped item entity. If the hotbar slot is taken it falls through to the rest of your inventory, and only if *that* is full does it drop in the world.
- Sneak-right-click *holding an item* is left to vanilla: it places the item and does not touch the trophy.
- It is a **metal** block all the way through — place, break and step sounds, a pickaxe requirement for drops, and a blast-resistant model that is drawn entirely by its lower half so the upper half can never desync from it.

### 💌 The completionist letter

One hidden node exists to close out the **whole TOMC series**, not just this mod:

- It is counted by `keepadvancing:advancements_made` against `after: keepadvancing:tomc/root` — meaning **every advancement any TOMC mod hangs under the shared TOMC root**, with no id list to maintain. A future sibling mod joins the count just by parenting its content there.
- Run this mod alone and the set resolves to **zero**, so the node never fires. That is the design rather than a missing piece: the root node is granted by mod-load, so the tab still shows and nothing is broken — there is simply nothing to be a completionist *about* yet.
- When it does fire, the reward runs a **function** as the player, which plays vanilla's challenge-complete jingle and posts an eight-line letter to chat, signed *—— ThatOne*.
- The **trophy that comes with it carries the same letter as its lore**, in gold and upright instead of the usual grey italics.
- The letter is a **translatable component**, so a Chinese client and an English client read the same item in their own language. The **name in the first line is not** — it is resolved at the instant the trophy is granted and baked in as literal text, which is what lets you hand the trophy to someone else and have them read *your* name on it.
- A trophy obtained any other way — creative mode, picked up by sneak-right-click, mined after being placed — has **no lore**, because there was no granting moment to bake a name into.

### 🧰 Five triggers for datapack authors

This is the part that is meant to be reused by someone who is not the author. All five are declared entirely from JSON; nobody has to write Java:

| Trigger | The question it answers |
| --- | --- |
| `keepadvancing:timer_test` | How did a value change inside a time window? (7 checks, 6 built-in gauges, any vanilla statistic) |
| `keepadvancing:mod_loaded` | Is a mod whose package matches this regex in the environment? |
| `keepadvancing:advancements_made` | How many (or what proportion) of a *set* of advancements are done? |
| `keepadvancing:gui_open` | Which GUI is the player looking at right now? |
| `keepadvancing:structure_present` | Is a 100 %-identical structure nearby? |

They are what make the shipped content possible — "5 sneaks inside one second" and "10 totems inside 30 seconds" are not expressible in vanilla's format at all — but they work just as well for someone else's advancement tree. Full field tables and worked examples: [docs/datapack.en.md](docs/datapack.en.md).

### 🎨 Three display extensions

Three additions to the vanilla advancement format, all optional, all backwards compatible:

- **`icon` as a string.** Write `"icon": "mymod:icon/star"` and the advancement draws a texture instead of an item. The item form still works exactly as before.
- **`background` as an object.** `mode` (`tile` / `fill` / `cover` / `contain`) and `attachment` (`fixed` / `local`) turn the tab background from a fixed 16×16 tile into something that can scale, crop or scroll with the content. A plain string still means "tile, fixed", which is what vanilla does.
- **`multi_parents`.** `parent` stays the main parent and keeps deciding the unlock chain, while `multi_parents` adds extra drawn lines — so a node can visually converge from several branches without gaining several unlock paths. Position is computed on the **server** and shipped with the advancement, so it costs the client nothing.

### 📚 Library mode

One config key — `load_builtin_content` — decides whether this mod is *content* or *infrastructure*:

- `true` (default): the TOMC tab, the 22 advancements, the trophy block and the six vanilla adjustments all load.
- `false`: **none of our content loads at all** — no tab, no block, no overwritten vanilla file — and what is left is the five triggers and the public GUI API, as a plain prerequisite library.

The five triggers and the API are *always* registered; the switch never touches them. And with the switch off, **vanilla keeps all of its own advancements** (1584 of them, verified), including the four that our built-in data pack would otherwise have re-parented.

## Design philosophy

Four rules shape everything in this mod:

1. **Content is data; Java is only what JSON cannot say.** Every advancement here is a JSON file that a datapack could have written. The Java side ships five triggers, and nothing else is asked of an author who wants a new condition.
2. **Append to vanilla; when we overwrite, say so out loud.** Twenty-two advancements are ordinary children of vanilla's chains. The six files we do overwrite are listed in a table, kept in a data pack that can be dropped in one move, and are the reason the config switch exists at all.
3. **A prerequisite, not a content mod you have to want.** The point of the five triggers is that another mod can depend on this one, turn its content off, and get an advancement system without getting somebody else's achievements.
4. **Degrade, never crash.** An unknown `mode` or `attachment` falls back to the default and logs a warning. A bad `multi_parents` edge is skipped and logged. The BetterAdvancements support is declared `required: false` with every injection point at `require = 0`, so a future rewrite of that mod costs a log line rather than a startup failure.

## Requirements

- **Fabric** (this repository currently builds and is tested against **Minecraft 1.21.11**, Fabric Loader **0.19.3+**, Java **21+**)
- [Fabric API](https://modrinth.com/mod/fabric-api) — required
- [BetterAdvancements](https://modrinth.com/mod/betteradvancements) — optional, and **supported**. BetterAdvancements replaces vanilla's advancement screen with its own, so a mod that only patches the vanilla widgets does nothing under it; this one ships a second set of hooks for it, and all three display extensions work there too.

**Both sides need the mod, at the same version.** This is not a soft requirement:

- The client half is what reports *which GUI is open*, which `keepadvancing:gui_open` needs.
- Both halves extend vanilla's own advancement **network format** — `multi_parents` on the advancement, and the icon/background fields on the display. Neither side negotiates a version; the other side simply reads bytes it does not expect.

So a vanilla client cannot join a server running this mod, and the reverse is equally true. It is the price of supporting `multi_parents` and background modes without opening a channel of our own.

> The source layout is one directory per loader × version (`fabric-1.21.11/` today), each with its own wrapper and build. Minecraft 1.21.11 on Fabric is the target that exists right now — not a statement that it is the only one that ever will.

## Configuration

`config/keepadvancing.json`, written on first launch:

```json
{
  "_readme": "Set load_builtin_content to false to use TOMC-Keep Advancing as a trigger library only.",
  "load_builtin_content": true
}
```

| Value | Behaviour |
| --- | --- |
| `true` (default) | Everything: the TOMC tab, 24 advancement files, the TOMC Trophy, and the six adjusted vanilla advancements |
| `false` | None of our content, anywhere. Only the five triggers and the public API remain |

A missing file, a missing key or a value of the wrong type all fall back to the default rather than failing, and a corrupt file is reported and replaced with a clean one. The change takes effect on a datapack reload (`/reload`) or a restart, because the data files are filtered by a resource condition evaluated during datapack loading.

The six overwritten vanilla files are **not** filtered that way — they live in a built-in data pack (`resourcepacks/content-data/`) that is only registered when the switch is on. The reason is a rule worth knowing if you ship data of your own: when a resource condition skips a file, a lower-priority pack's copy of the same id does **not** take over — the id disappears entirely. With the naive approach, turning the switch off silently deleted six *vanilla* advancements (1584 → 1578).

## Building from source

The build for the current target lives in `fabric-1.21.11/`, which carries its own Gradle wrapper; the repository root holds the shared version pins in `root.gradle.properties`. All builds go through the wrapper:

```sh
cd fabric-1.21.11

# Windows
gradlew.bat build

# Linux / macOS
./gradlew build
```

The first build downloads Minecraft, a JDK 21 toolchain and the Fabric dependencies, and takes far longer than the ones after it. Two details are deliberate and should not be "cleaned up":

- `gradle.properties` pins `org.gradle.java.home` at a **JDK 21** installation, because the `java` on the development machine's `PATH` is Java 8 and Gradle 9 will not start under it.
- `org.gradle.configuration-cache` is set to `false`, which is the documented workaround for a known fabric-loom issue.

The artifact name carries a build counter:

```
tomc-keepadvancing-0.1.0.<build>-fabric+mc1.21.11.jar   →  build/libs/
```

The first three version segments come from `mod_version`; the fourth increments on every build that actually produces a jar (a failed compile does not consume a number). Use `-PbuildNumber=7` to reproduce a specific name without spending one, and `-PwithTestData` to additionally bundle the acceptance-test datapack under `src/testdata/katest/`, which is **not** part of the released jar.

The per-target build detail — the directory map, the datapack layout, the display extensions and the log of every trap already stepped in — is in [fabric-1.21.11/README.md](fabric-1.21.11/README.md).

## License

[LGPL-3.0-or-later](LICENSE)

---

> 📘 **Datapack & trigger reference** — [docs/datapack.en.md](docs/datapack.en.md)
