# TOMC-Keep Advancing — 数据包与触发器参考

> 这一页上的东西**全部写在 JSON 里**。除了 [§8](#8-公开的-gui-api) 那一个可选 API 之外，什么都不用写 Java —— 而那个 API 是给别的模组作者用的，不是给数据包作者用的。

这个模组存在的理由，是针对原版进度格式的一句抱怨：它能看见你手上的物品是什么、你站在哪里、你杀了什么，但它看不见**时间**，看不见**别的模组**，看不见**别的进度**，看不见**玩家开着哪个界面**，也看不见**一座建筑有没有被造出来**。

下面五个触发器正好补上这五个洞。本模组自带的 22 条进度是用它们写的；你的进度也可以。

- [1. 五个触发器分别补哪个洞](#1-五个触发器分别补哪个洞)
- [2. `keepadvancing:timer_test` —— 某个值在时间窗里怎么变的？](#2-keepadvancingtimer_test--某个值在时间窗里怎么变的)
- [3. `keepadvancing:mod_loaded` —— 环境里有没有那个模组？](#3-keepadvancingmod_loaded--环境里有没有那个模组)
- [4. `keepadvancing:advancements_made` —— 一组进度做完了多少？](#4-keepadvancingadvancements_made--一组进度做完了多少)
- [5. `keepadvancing:gui_open` —— 此刻开着哪个界面？](#5-keepadvancinggui_open--此刻开着哪个界面)
- [6. `keepadvancing:structure_present` —— 那座建筑正在这里吗？](#6-keepadvancingstructure_present--那座建筑正在这里吗)
- [7. 三项进度显示扩展](#7-三项进度显示扩展)
- [8. 公开的 GUI API](#8-公开的-gui-api)
- [9. 把它当纯前置库](#9-把它当纯前置库)
- [10. 兼容性与边界](#10-兼容性与边界)

---

## 1. 五个触发器分别补哪个洞

| 触发器 | 它回答的问题 | 求值时机 |
|---|---|---|
| `keepadvancing:timer_test` | 某个值在时间窗里怎么变的？ | 每 tick，且只对真正写了这条判据的玩家 |
| `keepadvancing:mod_loaded` | 环境里有没有包名匹配的模组？ | 玩家加入时一次 |
| `keepadvancing:advancements_made` | 一组进度做完了多少 / 占多大比例？ | 每 20 tick，且只对真正写了这条判据的玩家 |
| `keepadvancing:gui_open` | 这一瞬间开着哪个界面？ | 每 tick |
| `keepadvancing:structure_present` | 附近有没有 100% 一致的结构？ | 每 20 tick |

其中 `advancements_made` 与 `structure_present` 是**定时轮询**而不是事件驱动：原版在玩家完成进度时**不发出任何服务端事件**，而本模组刻意不在那条路径上挂 Mixin，所以只剩定时这一条路。代价是从条件成立到弹出提示**最多延迟 1 秒**。

写一条判据就是普通的进度 JSON，唯一不寻常的地方只有 `trigger` 这个名字：

```json
{
  "display": {
    "icon": { "id": "minecraft:crafting_table" },
    "title": "界面测试",
    "description": "打开工作台",
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

放进 `data/<你的命名空间>/advancement/`（数据包也好，你自己模组的资源里也好），它就和任何一份普通进度一样加载。没声明的触发器只是永远不触发；**写坏了的判据只会让那一份文件加载失败**，日志里会写明原因，绝不会连累整个数据包。

> **关于"空集合"的一条通则。** 这一页里出现的所有"解析结果为空"，含义都是**这条判据恒不成立**，而不是"0 项里完成 0 项，所以自动达成"。写错锚点 id 是**朝失败方向**倒的，而且会大声报一次警。

---

## 2. `keepadvancing:timer_test` —— 某个值在时间窗里怎么变的？

> 把任意「能读成整数的东西」放进时间窗里观测，然后问它这段时间里发生了什么。

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

| 字段 | 必填 | 说明 |
|---|---|---|
| `source` | ✅ | 观测对象，见下 |
| `check` | ✅ | 怎么把窗口总结成一个数，见下 |
| `operator` | ✅ | `>=` `>` `<=` `<` `==` `!=` |
| `threshold` | ✅ | 比较阈值 |
| `window` | 取决于 `check` | 时间窗长度，单位 **tick**（20 tick = 1 秒），范围 1 ~ 72000（1 小时） |

### 2.1 `source`：两种可观测对象

**原版统计准则名。** 就是你会在 `/scoreboard objectives add <名> <这个字符串>` 里写的那串。那条命令能建成功，这串在这里就合法。

- 注册表类：`minecraft.used:minecraft.totem_of_undying`、`minecraft.mined:minecraft.stone`、`minecraft.crafted:minecraft.crafting_table`、`minecraft.killed:minecraft.zombie`、`minecraft.broken:minecraft.diamond_pickaxe`、`minecraft.picked_up:…`、`minecraft.dropped:…`、`minecraft.killed_by:…`
- 自定义类：`minecraft.custom:minecraft.jump`、`…:minecraft.deaths`、`…:minecraft.play_time`、`…:minecraft.time_since_death`、`…:minecraft.damage_dealt`、`…:minecraft.mob_kills`、`…:minecraft.fish_caught` 等，约一百个

**内置实时仪表。** 不是统计，是当场从玩家身上读出来的值。

| id | 读的是什么 |
|---|---|
| `keepadvancing:health` | 生命值（取整到整数点血） |
| `keepadvancing:air` | 氧气值 |
| `keepadvancing:food` | 饥饿值 |
| `keepadvancing:xp_level` | 经验等级 |
| `keepadvancing:y` | 所在 Y 坐标 |
| `keepadvancing:sneak_press` | **潜行键按下次数**——按上升沿计数，所以低矮天花板、飞行、骑乘都灌不了水 |

### 2.2 `check`：七种把窗口总结成一个数的方式

设窗口内的采样序列为 `v(t₀…tₙ)`，`dᵢ = v(tᵢ) − v(tᵢ₋₁)`：

| `check` | 含义 | 需要 `window` |
|---|---|---|
| `value` | 当前值本身——读的是**统计文件里的终身值** | ❌ 不允许写 |
| `increase_count` | `#{dᵢ > 0}` ——"发生了几次" | ✅ |
| `decrease_count` | `#{dᵢ < 0}` ——"掉了几次" | ✅ |
| `change_count` | `#{dᵢ ≠ 0}` ——"变化了几次" | ✅ |
| `total_increase` | `Σ max(dᵢ, 0)` ——"一共涨了多少" | ✅ |
| `total_decrease` | `Σ max(−dᵢ, 0)` ——"一共掉了多少"，取**非负幅度** | ✅ |
| `delta` | `v(tₙ) − v(t₀)` ——净变化，**带符号** | ✅ |

### 2.3 现成配方

```json
{"source":"keepadvancing:sneak_press","check":"increase_count","operator":">=","threshold":5,"window":20}
```
1 秒内潜行 5 次。

```json
{"source":"keepadvancing:health","check":"decrease_count","operator":">=","threshold":3,"window":200}
```
10 秒内挨了 3 次伤害。

```json
{"source":"keepadvancing:air","check":"total_decrease","operator":">=","threshold":200,"window":600}
```
30 秒内一共憋掉 200 点氧气——把"溺水"这件事量化了。

```json
{"source":"minecraft.custom:minecraft.time_since_death","check":"total_decrease","operator":">=","threshold":1,"window":40}
```
最近 2 秒内死了一次（归零也算下降）。

```json
{"source":"minecraft.custom:minecraft.time_since_death","check":"value","operator":">=","threshold":72000}
```
死后已存活 1 小时。`value` 不需要 window，因为读的是终身值——也正因为如此，进入一个已经满足这个条件的存档时会**当场白送**这条进度。

### 2.4 会咬人的地方

- **`==` 在采样下不可靠。** 一个 tick 里跨越多个整数值会被整段跳过。**优先用 `>=` / `<=`。**
- **除了 `value`，每一种 `check` 都必须写 `window`。** 省略会让**这一份进度加载失败**——不会"退化成终身值比较"。这是刻意做响的。
- **`window` 写错**（缺失 / ≤0 / >72000）同样只让那一份进度加载失败，日志里会说明原因。
- **原版统计是单调不减的**，只有 `time_since_death` 与 `time_since_rest` 例外。所以在纯统计上，`decrease_count` 和 `total_decrease` 基本只有那两个能用。想要"会掉的值"，请用上面的内置仪表：`health`、`air`、`food`、`y`。

---

## 3. `keepadvancing:mod_loaded` —— 环境里有没有那个模组？

```json
{
  "trigger": "keepadvancing:mod_loaded",
  "conditions": {
    "package": {"regex": "io\\.github\\.thatone0502"},
    "exclude_self": true
  }
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `package` | ✅ | `"io.github.thatone0502"` = **包名精确相等**；`{"regex": "…"}` = 正则 |
| `exclude_self` | ❌ | 是否把 Keep Advancing 自己排除掉。默认 `true` |

- 判据是**真实 Java 包名**——打开每个已加载模组的 jar 或目录、枚举 `.class` 条目反推出来的。**不是 modid，也不是显示名。**
- **普通字符串是精确相等。** 包名是从 `.class` 路径反推的，所以 `"package": "io.github.thatone0502"` 要求真的有一个类**就在那一层**——类全在 `io.github.thatone0502.keepadvancing.*` 下的模组推不出父包这一层，**匹配不到**。想要"包树前缀"，老老实实写正则。
- **正则走 `find()`（子串查找），不是全匹配。** 所以 `{"regex": "io\\.github\\.thatone0502"}` 能命中这一层以及它下面的任何一层。要锚定就自己写 `^…$`。
- 正则里所有 `.` 都要转义成 `\\.`，否则 `.` 是"任意字符"。
- 判定时机是**玩家加入**——模组集合开服之后就固定了，不会每 tick 轮询。
- 扫描在服务器启动后的**后台线程**上预热，不占登录时间、也不占每 tick 时间。参考值：43 个模组耗时 221 ms。

---

## 4. `keepadvancing:advancements_made` —— 一组进度做完了多少？

> 先用选择器圈定一组进度，再看这组里已经完成了多少个（或占多少比例）。

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

| 字段 | 必填 | 说明 |
|---|---|---|
| `player` | ❌ | 标准玩家谓词，写法和其它原版进度的判据一样 |
| `list` | ✅ | 至少 1 个选择器；各项结果取**并集**并去重 |
| `require` | ✅ | `{type, operator, value}`，见下 |
| `include_hidden` | ❌ | 默认 `true`。为 `false` 时剔除 `"hidden": true` 的进度（原版 5 个标签页里共 9 条） |
| `require_display` | ❌ | 默认 `true`。为 `false` 时才把**没有 `display` 的进度**也纳入（原版共 1459 条，全是配方解锁节点） |

### 4.1 七种选择器

前六个与**原版命令** `/advancement grant <目标> <模式> <进度>` 逐位对齐；`after`、`before`、`between` 是原版没有的补充。

| 键 | 等价的原版写法 | 取到的集合 |
|---|---|---|
| `"X"` 或 `{"only":"X"}` | `only X` | `{X}` |
| `{"from":"X"}` | `from X` | `{X}` + X 的全部后代 |
| `{"through":"X"}` | `through X` | X 的祖先链 + `{X}` + X 的全部后代 |
| `{"until":"X"}` | `until X` | X 的祖先链 + `{X}` |
| `{"after":"X"}` | —— | X 的全部后代（**不含 X**） |
| `{"before":"X"}` | —— | X 的祖先链（**不含 X**） |
| `{"between":["A","B"]}` | —— | A 到 B 链路上的全部，**含两端** |

### 4.2 `require`

| 字段 | 必填 | 说明 |
|---|---|---|
| `type` | ✅ | `"proportion"`＝完成比例（0.0 ~ 1.0）／`"count"`＝完成个数 |
| `operator` | ❌ | `>=` `>` `<=` `<` `==` `!=`，默认 `>=` |
| `value` | ✅ | 阈值。`proportion` 必须落在 0.0~1.0，`count` 不得为负；写错会让**那一份进度**加载失败 |

### 4.3 实测解析条数

下面这些是拿本模组的服务端自检，在「原版 1.21.11 + 本模组」上打出来的，可以直接对着数：

| 选择器 | 条数 |
|---|---|
| `{"only":"minecraft:story/root"}` | 1 |
| `{"from":"minecraft:story/root"}` | 17 |
| `{"after":"minecraft:story/root"}` | 16 |
| `{"through":"minecraft:story/smelt_iron"}` | 17 |
| `{"until":"minecraft:story/smelt_iron"}` | 4 |
| `{"before":"minecraft:story/smelt_iron"}` | 3 |
| `{"between":["minecraft:story/root","minecraft:story/smelt_iron"]}` | 4 |
| `{"through":"minecraft:recipes/root"}`（`require_display: true`） | **0** |
| `{"through":"minecraft:recipes/root"}`（`require_display: false`） | **1459** |

（`from: story/root` 是 17 而不是原版数据里的 16：本模组往 story 标签页下挂了一条新进度。这正是"它统计的是整棵已加载的进度树"的体现。）

### 4.4 现成配方

```json
{"list":[{"through":"minecraft:adventure/root"}],"require":{"type":"proportion","value":1.0}}
```
通关整个「冒险」标签页。

```json
{"list":[{"through":"minecraft:story/root"},{"through":"minecraft:nether/root"},
         {"through":"minecraft:end/root"},{"through":"minecraft:adventure/root"},
         {"through":"minecraft:husbandry/root"}],
 "require":{"type":"count","operator":">=","value":100}}
```
五个原版标签页里任意完成 100 条。

```json
{"list":[{"only":"keepadvancing:tomc/root"}],"require":{"type":"count","value":1}}
```
拿到 TOMC 标签页的根进度。

### 4.5 会咬人的地方

- **`from` / `through` / `until` 都含锚点，`after` / `before` 不含**——和 `/advancement` 完全一致，别按直觉猜。
- **`between` 要求 A、B 在**同一条祖先链**上**，因为每个进度只有一个 parent。方向随便写（A 是 B 的祖先或反过来都行）。不在同一条链上就解析为空，并在日志里告警一次——比如 `between:["minecraft:adventure/root","minecraft:story/root"]`。
- **`minecraft:recipes/root` 是独立的第六个根**，底下 1459 条配方解锁进度**全都没有 display**。所以默认的 `require_display: true` 会把它们整体滤掉（`through: minecraft:recipes/root` 的解析结果是**空集**）；要数配方就显式写 `"require_display": false`。
- **解析结果为空 ⇒ 判据恒不成立**，不是"0 项里完成 0 项所以自动达成"。空的原因可能是锚点 id 写错，也可能是被两个过滤器滤空了。
- 锚点 id 不存在、或解析出的条数超过 **4096**，同样只**告警一次**并永不触发。缓存保证不会每 tick 刷屏。
- 评估时机是**每 20 tick（1 秒）一次**。原版没有"玩家完成了某进度"的服务端事件可挂，而本模组刻意不带 Mixin，所以只能定时轮询——从达成到弹出最多延迟 1 秒。
- 和 `timer_test` 一样，**只有真正写了这个判据的玩家才被评估**；没人听时一次都不算。
- 它统计的是**整个服务器加载的进度树**，所以天然把其它数据包 / 模组加的进度也算进去。这既是它作为"通用预设"的价值所在，也意味着上面那张表里的数字，只要别的东西加了一个节点就会跟着动。

---

## 5. `keepadvancing:gui_open` —— 此刻开着哪个界面？

> **界面开着的那些 tick 里判定为真，关掉的那一 tick 就不成立。**

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

| 字段 | 必填 | 说明 |
|---|---|---|
| `menu` | ❌ | **服务端侧**：比容器菜单 `MenuType` 的注册 id |
| `screen` | ❌ | **客户端上报**：比当前 `Screen` 的全限定类名 |
| `custom` | ❌ | **其它模组主动上报**的界面 id——见 [§8](#8-公开的-gui-api) |

- **三者取或**：任意一条命中即成立。
- **三个都写成空数组会在数据包加载期直接报错。** 那一定是写错了，不该静默地永不触发。
- 每个列表元素：字符串 = **精确相等**；`{"regex": "…"}` = 正则（`find()` 子串语义，要锚定自己写 `^…$`）。
- 语义是**"此刻正开着"**，每 tick 判一次。界面对着的那一 tick 就授予。
- **`menu` 覆盖不到物品栏。** `InventoryMenu` 的 `MenuType` 就是 `null`（它的构造是 `AbstractCraftingMenu.<init>(null, 0, 2, 2)`），而玩家什么都没开的时候 `containerMenu` 又正好**等于** `inventoryMenu`——服务端无法区分"空手"和"开着物品栏"。**物品栏只能靠 `screen`。**
- **`screen` 是客户端上报的**：只有装了本模组的客户端才有值（客户端半边用 Fabric 自带的 `ScreenEvents`，**没有 Mixin**）。服务端在没有客户端配合时，`gui_open` 只剩 `menu` 那一半能用。

常见 `menu` 值（`MenuType` 注册 id，1.21.11 实测）：

`minecraft:crafting`、`minecraft:enchantment`、`minecraft:anvil`、`minecraft:furnace`、`minecraft:blast_furnace`、`minecraft:smoker`、`minecraft:generic_9x1` … `generic_9x6`、`minecraft:generic_3x3`、`minecraft:crafter_3x3`、`minecraft:hopper`、`minecraft:lectern`、`minecraft:loom`、`minecraft:merchant`、`minecraft:shulker_box`、`minecraft:smithing`、`minecraft:cartography_table`、`minecraft:stonecutter`、`minecraft:brewing_stand`、`minecraft:beacon`、`minecraft:grindstone`。

常见 `screen` 值（1.21.11 客户端实测类名，都在 `net.minecraft.client.gui.screens` 下）：

- 容器类：`inventory.InventoryScreen`（物品栏 / 生存背包）、`inventory.CreativeModeInventoryScreen`（创造背包）、`inventory.CraftingScreen`、`inventory.EnchantmentScreen`、`inventory.AnvilScreen`、`inventory.FurnaceScreen`、`inventory.BlastFurnaceScreen`、`inventory.SmokerScreen`、`inventory.BrewingStandScreen`、`inventory.HopperScreen`、`inventory.DispenserScreen`、`inventory.ShulkerBoxScreen`、`inventory.MerchantScreen`、`inventory.LoomScreen`、`inventory.StonecutterScreen`、`inventory.SmithingScreen`、`inventory.GrindstoneScreen`、`inventory.CartographyTableScreen`、`inventory.BeaconScreen`、`inventory.LecternScreen`、`inventory.ContainerScreen`（通用容器）、`inventory.CrafterScreen`、`inventory.NautilusInventoryScreen`、`inventory.HorseInventoryScreen`
- 编辑类：`inventory.SignEditScreen`、`inventory.HangingSignEditScreen`、`inventory.BookEditScreen`、`inventory.BookViewScreen`、`inventory.BookSignScreen`、`inventory.CommandBlockEditScreen`、`inventory.StructureBlockEditScreen`、`inventory.JigsawBlockEditScreen`
- 其它：`ChatScreen`、`PauseScreen`、`DeathScreen`、`TitleScreen`、`advancements.AdvancementsScreen`
- 想一次匹配所有容器界面：`{"regex": "^net\\.minecraft\\.client\\.gui\\.screens\\.inventory\\..*Screen$"}`

**它看不见的**：纯 HUD 覆盖层——不是 `Screen`、直接画在 HUD 上的东西（某个模组的经验条面板、自定义信息页）。`Screen` 事件根本不会触发。这类只能靠 `custom` 字段，由画它的那个模组主动开口。

> `inventory.EffectsInInventory`（物品栏右侧那栏药水效果）**不是**独立 `Screen`，匹配不到。

---

## 6. `keepadvancing:structure_present` —— 那座建筑正在这里吗？

> 在玩家周围扫一圈，看有没有和模板 100% 一致的结构。

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

| 字段 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `structure` | ✅ | —— | 结构 id，对应 `data/<命名空间>/structure/<路径>.nbt` |
| `radius` | ❌ | 16 | 可写一个整数（水平 = 垂直），或 `{"horizontal": H, "vertical": V}` |
| `allow_rotation` | ❌ | `false` | 是否把 4 个旋转朝向都算作匹配 |
| `allow_mirror` | ❌ | `false` | 是否把 3 个镜像朝向都算作匹配 |
| `match_nbt` | ❌ | `false` | 是否连方块实体一起比（箱子里的东西、告示牌文字、熔炉进度……） |
| `include_air` | ❌ | `true` | `true` = 模板里的空气**也参与**比对；`false` = 忽略模板里的空气 |

- **必须 100% 完全一致，没有阈值字段。** 这是原版结构方块老"检测模式"的严格语义。
- **一条判据一个结构。** 想要"村庄或铁匠铺"就写两条判据 + `requirements` 把它们放进同一个 requirement 组。
- **朝向组合数**：rotation 4 × mirror 3 = 12 种。两个都关就是 1 种（最快）；只开 rotation 是 4 种；只开 mirror 是 3 种；都开最慢。
- 评估频率**每 20 tick（1 秒）一次**。
- **结构文件放在单数目录里**：`data/<命名空间>/structure/<名字>.nbt`。`.snbt`（人能读的文本格式）也认。
- 结构从哪来：游戏里用**结构方块**摆好 → 选 `SAVE` 模式存成 `.nbt`。注意 **1.21.11 的结构方块已经没有 `DETECT` 模式了**（`StructureMode` 只剩 `SAVE / LOAD / CORNER / DATA`）——这正是这个触发器存在的原因。
- **结构空位（structure void）与空气不是一回事。** 原版保存结构时会把结构空位**跳过、不写进文件**，而**普通空气是会写进去的**。所以 `include_air: true` 时，"模板里是空气的位置也必须有空气"。
- ⚠ **`include_air: true` 的代价**，是"结构明明摆对了却不触发"的头号原因：边界框里多放**任何**东西——一支火把、长出来的草、流进来的水、走过的脚印——都会判失败。结构没问题却死活不触发，先想这一点。想宽松点就设 `include_air: false`。
- **性能**用**锚点扫描**：从模板里挑一个最有辨识度的方块（非空气、在模板里出现次数最少）当锚点，只在半径区域内找这一个方块。每命中一次才反推结构原点、按预排顺序校验整份模板，**首个不符立刻放弃**。所以代价与"半径区域的体积"无关，而取决于锚点在区域内出现了几次。锚点命中数超过 **4096** 会告警一次并**跳过本 tick**。
- 结构 id 读不到（写错 / 文件不存在）→ **告警一次**，且这条判据**恒不成立**。

---

## 7. 三项进度显示扩展

三个可选的、加在原版进度格式上的扩展。原版写法在任何一种情况下都照旧可用，而且它们**都不可能让数据包加载失败**——写错值只会降级成默认值并打一条警告。

### 7.1 `icon` 可以是字符串

```json
"display": { "icon": "mymod:icon/star", "title": "…" }
```

字符串**直接当纹理 id**，解析规则和原版解析纹理完全一样：`<命名空间>:<路径>` → `assets/<命名空间>/textures/<路径>.png`。所以上面这行的真实文件是 `assets/mymod/textures/icon/star.png`。

> ⚠ **绝对不要写 `textures/` 前缀和 `.png` 后缀。** 写成 `mymod:textures/icon/star.png` 会去找 `assets/mymod/textures/textures/icon/star.png.png`，界面上就是一个紫黑格。这是 `background` 字段很多年前就踩过的同一个坑——字段换了，坑没换。

写成原版物品对象时行为完全不变。内部用的是 `either(Identifier, ItemStack)`，**left 优先**，所以字符串走纹理分支、对象走原版物品分支。

### 7.2 `background` 可以写对象

```json
"background": {
  "asset_id": "mymod:gui/backgrounds/grid",
  "mode": "cover",
  "attachment": "local",
  "width": 64,
  "height": 48
}
```

| 字段 | 取值 | 行为 |
|---|---|---|
| `asset_id` | 纹理 id | 必填。解析规则同 7.1——**同样不要写 `textures/` 与 `.png`** |
| `mode` | `tile`（默认） | 原版行为，16×16 平铺 |
| | `fill` | 非等比拉伸填满整区，**会变形** |
| | `cover` | 保持宽高比缩放到覆盖整区，多余裁切 |
| | `contain` | 保持宽高比完整显示，区域可能留边 |
| `attachment` | `fixed`（默认） | 背景固定于视口，内容滚动时不动 |
| | `local` | 背景随内容滚动，包围盒 = `max(视口, 内容)` 两轴各自取大 |
| `width` / `height` | 整数 | 像素尺寸；不写则运行时读纹理真实大小 |

`mode` / `attachment` 大小写不敏感；未知值**降级为默认值并打 WARN**，不会让数据包加载失败。写字符串等同 `mode=tile` + `attachment=fixed`。

### 7.3 `multi_parents` —— 一个进度多条上游连线

```json
{
  "parent": "mymod:converge/y",
  "multi_parents": ["mymod:converge/z", "mymod:converge/x"],
  "criteria": { "impossible": { "trigger": "minecraft:impossible" } }
}
```

`parent` 仍是**主父**——决定解锁链与纵向层级，根进度照旧不写 `parent`。`multi_parents` 只**多加连线**：不改变解锁关系，也不参与建树的成败判定。

- 指向不存在的 id、指向自己、或会成环的边都会被**跳过并打 WARN**，其余照常生效。
- 那些 id 必须是**同一个进度树内**的节点，而且通常应当是**兄弟或更上层**的节点。写成"下层的节点"就是环，会被拒。
- 含 `multi_parents` 的连通分量，布局由**服务端**算完随包下发，客户端一行布局代码都不用写。不含的分量保留原版坐标——**一个像素都不动**。
- ⚠ **连线重合很容易发生。** 若几个父恰好落在同一列，它们的连线会叠在一起，看起来像只有一条。搭树时请让父节点**横向铺开**。

### 7.4 装了 BetterAdvancements 之后

BetterAdvancements **不用原版进度界面**——它自己实现了 `BetterAdvancementsScreen` / `BetterAdvancementTab` / `BetterAdvancementWidget`，只留一个 Mixin 把原版界面重定向过去。所以只改原版 `AdvancementTab` / `AdvancementWidget` 的模组，在装了 BA 的实例上**等于没改**。本模组因此另带一套 BA 专用钩子，按是否加载 `betteradvancements` 选边，三项扩展在 BA 界面里同样生效。

一个刻意的取舍：BA 会给未达成的图标染灰，而字符串纹理分支**不着色**（当纯纹理画），所以未达成状态的字符串图标在 BA 下不会变灰。

---

## 8. 公开的 GUI API

`io.github.thatone0502.keepadvancing.api.KeepAdvancingGuiApi`

```java
// 客户端侧：我正在画自己的界面
KeepAdvancingGuiApi.reportCustomGui(Identifier.fromNamespaceAndPath("mymod", "xp_panel"));

// 不画了
KeepAdvancingGuiApi.reportCustomGui(null);
```

| | |
|---|---|
| **为什么需要它** | 不走 `Screen` 的界面——直接在 HUD 层画的覆盖层——服务端和 `Screen` 事件都看不见。唯一办法就是让画它的模组自己说一声 |
| **怎么依赖** | 把本模组作为 **`compileOnly`** 依赖即可。**不需要** Fabric entrypoint，因为这里全是静态方法 |
| **什么时候调** | 客户端侧，"正在显示 / 不再显示"状态变化时各调一次。本模组客户端入口每 tick 读一次，值不变就不发包 |
| **服务端调用** | 合法。值只存在一个静态字段里，只有本模组的客户端入口会去读它，专用服务器上调用没有副作用 |
| **对应的进度写法** | `"custom": ["mymod:xp_panel", {"regex": "mymod:.*"}]` |

这是本模组**唯一**的对外 Java API。五个触发器都是纯数据驱动，别人不需要写任何 Java。

---

## 9. 把它当纯前置库

只想用触发器、不想要我们的内容，改 `config/keepadvancing.json` 里的一个键：

```json
{
  "load_builtin_content": false
}
```

| | `true`（默认） | `false` |
|---|---|---|
| `data/keepadvancing/advancement/**`（24 个文件） | 加载，TOMC 选项卡存在 | 不加载，选项卡消失 |
| `keepadvancing:tomc_trophy` 方块与物品 | 注册，创造页里有 | 不注册，`mineable/pickaxe` 标签也不再引用它 |
| 被覆盖的那 6 处原版进度 | 重定父 / 换图标 | **原版照常加载它自己的，一条不少** |
| 五个触发器 | 可用 | **仍然可用** |
| `KeepAdvancingGuiApi` | 可用 | **仍然可用** |

改完需要 `/reload`（服务端）或重启才生效，因为资源条件是在**数据包解析期间**求值的。

> **一条值得抄走的规则。** 那 6 个覆盖原版的文件**没有**用资源条件，尽管它们看上去是最该用的。原因是：**资源条件判定为假时，低优先级数据包里的同名副本不会接管——那个注册表 id 会整个消失。** 实测把资源条件挂到那 6 个文件上，会凭空删掉 6 条**原版**进度（1584 → 1578）。所以它们被放进一个**只在开关打开时才注册的内置数据包**里：关掉时它根本不存在，原版自己的版本照常加载。**规则：资源条件只适合你自己命名空间里的文件；任何覆盖原版注册表条目的文件，都该放进一个"要么注册、要么不注册"的数据包。**

---

## 10. 兼容性与边界

- **两端都必须装本模组，且版本一致。** 两端都不做版本协商，而是对称地扩展了原版的进度网络格式；纯原版那一端会读到多出来的字节，反序列化失败。纯原版客户端连不上装本模组的服务端，反过来也一样。
- **支持 BetterAdvancements**（对 `BetterAdvancements-Fabric-1.21.11-0.4.8.54` 验过）。它的那套钩子声明为 `required: false`、注入点全部 `require = 0`，所以 BA 后续大改只会打一条日志警告，不会崩游戏。已知差异只有"未达成图标不着色"这一条。
- **`multi_parents` 必须无环**，且不参与解锁——它只影响**画线**和**布局**。
- **只有含 `multi_parents` 的分量会被重排**，所以给一条进度加 `multi_parents` 会轻微移动它整棵树的兄弟节点。这是设计如此：不然父没法居中于子。
- **`mode=local` 的包围盒**是 `max(视口, 内容)` 两轴各自取大。BA 会改视口尺寸，取值已按运行时视口处理；但**非 234×113 的视口只在 BA 下真实出现过**，原版视口恒为 234×113。
- **目前针对 Fabric 上的 Minecraft 1.21.11 构建与测试。** 五个触发器本身是"原版数据格式 + 本模组这一层 Java"；换加载器要重写的就是那一层 Java。

---

开发辅助：DeepSeek Harness（deepseek-flash）。
