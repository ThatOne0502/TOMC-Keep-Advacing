# TOMC-Keep Advancing

Keep Advancing adds new advancements for Vanilla and some other mods from TOMC.

一个**几乎纯数据包驱动**的 Fabric 模组：进度本体全是 JSON，Java 侧只提供数据格式表达不了的
通用触发器，方便其它作者复用。

- **Minecraft** 1.21.11 · **Fabric**（Loader ≥ 0.19.3、Fabric API 0.141.6+1.21.11）· **Java 21**
- modid `keepadvancing` · 作者 ThatOne · 协议 LGPL-3.0-or-later

## 构建

```sh
# Windows
gradlew.bat build

# Linux / macOS
./gradlew build
```

首次构建会下载 Gradle、JDK 21 工具链与依赖，需要 5–15 分钟。

产物名遵循固定格式：

```
tomc-<modid>-<主版本>.<次版本>.<修订号>.<构建次数>-<平台>+mc<MC版本号>.jar
→ build/libs/tomc-keepadvancing-0.1.0.0-fabric+mc1.21.11.jar
```

- 前三段版本号在 `gradle.properties` 的 `mod_version`（当前 `0.1.0`）。
- **第四段是构建次数，每次构建自动 +1**，计数存在工程根目录的 `build-number.txt`（纯文本**一个整数**；内容为空或损坏会按 `0` 处理并打印一条警告）。想归零就把它改成 `0` 或删掉它。
- 计数在 `jar` 的 `doLast` 里写回，所以编译失败或 `--dry-run` **不消耗**编号；反过来，编号是 `jar` 的输入，所以每次 `build` 都会重跑 `jar` 并 +1，`build/libs` 里只留最新那份。
- 不想消耗编号地出一次包：`gradlew jar -PbuildNumber=7`。

> 本仓库的 Java 用的是 JDK 21。若你的默认 `java` 不是 21，请在
> `gradle.properties` 里设置 `org.gradle.java.home`。

## 安装

把构建出的 jar 丢进 `.minecraft/mods/`（客户端）和 服务端 `mods/`，**两端都要，且版本必须一致**。

只装一边会出问题，原因有两条：

- **客户端半边**负责把「玩家此刻开着哪个界面」上报给服务端（`keepadvancing:gui_open` 需要它）。
- **两端都改了原版进度数据包**（rev14）：`Advancement` 追加 `multi_parents`，`DisplayInfo` 追加
  图标纹理与背景 `mode`/`attachment`。原版端会读到多出来的字节 ⇒ 反序列化失败。

所以**纯原版客户端连不上装本模组的服务端**，反过来也一样。这是 rev14 为支持 `multi_parents`
和背景显示模式主动接受的代价。

## 配置

首次启动会在 `config/keepadvancing.json` 生成：

```json
{
  "_readme": "Set load_builtin_content to false to use TOMC-Keep Advancing as a trigger library only.",
  "load_builtin_content": true
}
```

| 值 | 行为 |
|---|---|
| `true`（默认） | 加载全部内置内容：24 条进度、TOMC 选项卡、`keepadvancing:tomc_trophy` 方块，以及覆盖原版的那 6 处调整 |
| `false` | **只想把它当触发器库用**：内置内容一律不加载，本模组只剩五个触发器 + 公开 API |

关掉时**原版进度一条不少**（实测 1584 条；作对比，直接给那 6 个覆盖文件挂资源条件会掉到 1578）。五个触发器与公开 API 永远可用，不受开关影响。改完需要重启服务端（或 `/reload`）才生效。

> 那 6 个覆盖原版的进度文件（外加 `mineable/pickaxe` 标签）被放在一个**内置数据包**里（`resourcepacks/content-data/`），只在开关打开时注册。原因是资源条件跳过文件时，低优先级数据包里的同名副本并不会接管 —— 那个进度 id 会整个消失。

## 目录结构

```
src/main/java/io/github/thatone0502/keepadvancing/
  KeepAdvancing.java            服务端入口（注册触发器、接线）
  KeepAdvancingClient.java      客户端入口（上报当前界面）
  api/                          对外/跨 Mixin 的接口
    KeepAdvancingGuiApi.java      给别的模组用的公开 API
    KaAdvancement.java            duck 接口：multi_parents
    KaAdvancementNode.java        duck 接口：附加的 DAG 边
    KaDisplayInfo.java            duck 接口：图标纹理 / 背景扩展字段
  block/                        方块（TOMC 奖杯）
  condition/                    自定义资源条件（配置驱动的加载开关）
  config/                       模组配置读写
  content/                      进度显示内容模型
    BgMode.java / BgAttachment.java / BackgroundSpec.java
    BuiltinContentPack.java       内置数据包（承载覆盖原版的进度）
  trigger/                      五个通用触发器
  logic/                        包名扫描、界面状态缓存、背景与图标渲染
  layout/                       DAG 布局（multi_parents 的几何）
    KaAdvancementTree.java        带附加边的建图
    DagLayout.java                含 multi_parents 分量的重排
  mixin/                        rev14 起：数据 / 协议 / 布局层 Mixin
    client/                       原版渲染路径（AdvancementTab / Widget / TabType / Toast）
    ba/                           BetterAdvancements 渲染路径
    KeepAdvancingMixinPlugin.java  按是否装载 BA 互斥选边
  network/                      客户端 → 服务端载荷

src/main/resources/
  fabric.mod.json                   模组元数据
  data/keepadvancing/advancement/   进度本体（数据驱动）
  data/keepadvancing/structure/     结构检测用的结构文件（.nbt / .snbt）
  data/keepadvancing/function/      奖励里跑的 mcfunction
  data/keepadvancing/loot_table/    奖励战利品表
  assets/keepadvancing/             lang / 模型 / 纹理 / 物品定义
  resourcepacks/content-data/       可整体开关的内置数据包（覆盖原版的进度 + 标签）
```

## 进度显示扩展（rev14）

三个纯 JSON 的扩展，装了本模组的客户端才认（见上面「安装」）。原版写法一律照旧可用。

### `icon` 可以是字符串

```json
"display": { "icon": "katest:icon/star", "title": "…" }
```

字符串**直接当成纹理 id**，解析规则和原版一样：`<命名空间>:<路径>` 对应
`assets/<命名空间>/textures/<路径>.png`。所以上面这行的真实文件是
`assets/katest/textures/icon/star.png`。

> ⚠ **不要再写 `textures/` 前缀和 `.png` 后缀**。写成 `katest:textures/icon/star.png`
> 会去找 `assets/katest/textures/textures/icon/star.png.png` —— 界面上就是一个紫黑格。
> 这条坑 rev4 在 `background` 上踩过一次，别再踩第二次。
>
> **rev16**：上面这段规则一直是文档里的正确契约，是模组侧的 `IconRenderer` 早期漏了拼前缀/后缀
> （背景没事，因为它走原版 `ClientAsset.ResourceTexture`）⇒ 字符串图标渲染成紫黑格。现已修正：
> 解析规则与原版逐字一致，构建期有 `verify_jar.py` 的 `# 3e)`/`# 3f)` 两道静态检查兜底，
> 运行期遇到"已经写全了"的路径也会在日志里 warn 一次。完整规则见 [`../docs/datapack.zh-CN.md`](../docs/datapack.zh-CN.md) 的显示扩展一节。

写成原版物品对象时行为完全不变。

### `background` 的 `mode` 与 `attachment`

`background` 既能是字符串（等同 `mode=tile` + `attachment=fixed`），也能是对象：

```json
"background": {
  "asset_id": "katest:gui/backgrounds/grid",
  "mode": "cover",
  "attachment": "local",
  "width": 64,
  "height": 48
}
```

`asset_id` 的解析规则与上面的 `icon` 字符串相同（**同样不要写 `textures/` 与 `.png`**）。

| 字段 | 取值 | 说明 |
|---|---|---|
| `asset_id` | 纹理 id | 必填 |
| `mode` | `tile`（默认） | 原版行为，16×16 平铺 |
| | `fill` | 非等比拉伸填满整区，**会变形** |
| | `cover` | 保持宽高比缩放到覆盖整区，多余裁切 |
| | `contain` | 保持宽高比完整显示，区域可能留边 |
| `attachment` | `fixed`（默认） | 背景固定于视口，内容滚动时不动 |
| | `local` | 背景随内容滚动，包围盒 = `max(视口, 内容) × max(视口, 内容)` |
| `width` / `height` | 整数 | 像素尺寸；不写则运行时读取纹理真实大小 |

`mode` / `attachment` 大小写不敏感，写错值会降级成默认值并打一条警告，**不会**让数据包加载失败。

### `multi_parents`：一个进度多条上游连线

`parent` 仍是**主父**（决定解锁链与纵向层级；根进度照旧不写 `parent`），
`multi_parents` 是**额外的上游**，只多加线、不改变解锁链：

```json
{
  "parent": "katest:converge/y",
  "multi_parents": ["katest:converge/z", "katest:converge/x"],
  "criteria": { "impossible": { "trigger": "minecraft:impossible" } }
}
```

指向不存在的进度、指向自己、或会成环的边都会被**跳过并打警告**，其余照常生效。

布局由服务端算完随包下发：含 `multi_parents` 的连通分量会走一遍 DAG 排版
（父居中于子、层内对称分离），**不含的进度一个像素都不动**。

> ⚠ 多父布局下，"三条上游连线重合"很容易发生 —— 若几个父彼此在同一列，
> 连线会叠在一起，看起来像只有一条。测试时请让父节点**横向铺开**。

### BetterAdvancements

**已兼容。** BetterAdvancements（0.4.8.54 实测）**不用原版进度界面** —— 它有自己的
`BetterAdvancementsScreen` / `BetterAdvancementTab` / `BetterAdvancementWidget`，只留一个 Mixin
把原版界面重定向过去。所以只改原版的 `AdvancementTab` / `AdvancementWidget` 在装了 BA 的实例上
**等于没改**。本模组因此另带一套 BA 专用渲染 Mixin，由 `IMixinConfigPlugin` 按
`FabricLoader.isModLoaded("betteradvancements")` 决定加不加载 —— 三项功能在 BA 界面里同样生效。
那套 Mixin 声明为 `required: false` 且注入点全部 `require = 0`，BA 后续大改也只会打警告、不会崩游戏。

> 已知取舍：BA 会在未达成时给图标染色（置灰），而字符串纹理分支**不着色**（当纯纹理画），
> 所以未达成状态的字符串图标不会变灰。

### 测试数据

`src/testdata/katest/` 里是**验收用**的测试进度，共 **7 个标签页**：
字符串图标 ① / 三种背景缩放模式 ②×3 / `attachment=local` ③ / 菱形双父 ④ / 三父汇合 ⑤。

它们**不是模组内容**，默认**不会打进 jar**（rev17 起）。要实机验收才挂上：

```sh
gradlew.bat build                  # 交付物：不含 katest（默认）
gradlew.bat build -PwithTestData   # 验收用：含 14 条进度 + 2 张纹理
```

实现只有一行：`-PwithTestData` 在配置期把 `src/testdata/katest` 追加成额外的资源目录
（`sourceSets.main.resources.srcDir`），jar 内的条目路径与以前**完全一致**
（`data/katest/…`、`assets/katest/…`）。旧的 `-PnoTestData` 已废弃 —— 那是"默认打进、构建时排除"的旧模型。

## 五个通用触发器

别人不需要写任何 Java，全部由进度 JSON 声明：

| 触发器 | 一句话 |
|---|---|
| `keepadvancing:timer_test` | 限时观测器：看某个统计/状态量在时间窗内的值、增量、变化次数 |
| `keepadvancing:mod_loaded` | 环境里有没有加载某个包名匹配的模组 |
| `keepadvancing:advancements_made` | 某组进度的完成数量 / 比例 |
| `keepadvancing:gui_open` | 玩家此刻是不是开着指定界面 |
| `keepadvancing:structure_present` | 玩家附近有没有和模板 100% 一致的结构 |

完整的字段表、示例与注意事项见 [`../docs/datapack.zh-CN.md`](../docs/datapack.zh-CN.md)。

## 公开 API

只有一处，给那些**不走 `Screen`** 的界面（HUD 覆盖层）用：

```java
KeepAdvancingGuiApi.reportCustomGui(Identifier.fromNamespaceAndPath("mymod", "xp_panel"));
KeepAdvancingGuiApi.reportCustomGui(null); // 不显示了
```

把本模组作为 `compileOnly` 依赖即可。详见 [`../docs/datapack.zh-CN.md`](../docs/datapack.zh-CN.md) 的公开 API 一节。

---

Development assisted by DeepSeek Harness (deepseek-flash).
