# [KAD] 进取 Keep Advancing — 发布页文案 / Store Page Copy

> 中英对照 · 混装版。每一小节先 `**EN**` 再 `**中文**`，方便按平台挑一种贴；文末有拼好的**纯英文整篇**与**纯中文整篇**，可直接全选复制。
>
> 这份文件是**给玩家看的**。开发文档不在里面——数据包与触发器的完整参考在仓库的 `docs/`，想接接口的直接去 GitHub 看。

---

## 关于 AI 辅助 / On AI Assistance

**EN** — This mod was developed with the help of AI (DeepSeek Harness, deepseek-flash). AI wrote and reviewed code; the design, the trade-offs and the final responsibility are the author's. Not every line, doc and design decision has been reviewed by a human.

**中文** — 本模组在开发过程中使用了 AI 辅助（DeepSeek Harness，deepseek-flash）。AI 参与了代码编写与审查；设计、取舍与最终责任在作者。且并不是所有代码、文档与设计均经过人工审查与测试。

---

## 一句话简介 / One-liner

**EN** — **TOMC-Keep Advancing** is the advancement layer of the TOMC series: a **TOMC tab** in your advancement screen, **22 new advancements** that slot straight into vanilla's own tabs, and a **trophy block** to put in your base. Every TOMC mod needs it to have an advancement system at all.

**中文** — **TOMC-Keep Advancing** 是 TOMC 系列的进度层：进度界面里多出一个 **「TOMC」选项卡**，**22 条新进度**直接长在原版自己的标签页里，还有一座能摆在基地里的 **TOMC奖杯**。本作者的每一款 TOMC 系列模组，都要靠它才拥有进度系统。

---

## 它是什么 / What It Is

**EN** — A small mod about *doing things*, not about adding a machine or a dimension. It fills in the part of Minecraft that never got finished: the advancement screen gets a new tab for the TOMC series, and 22 new advancements get hung off vanilla's existing chains — the eye of ender, the crow's nest, the spyglass, the temples. A few vanilla advancements get new parents or new icons so the new chains have somewhere to land. There is also a golden trophy you can earn, and a letter waiting at the end if you finish the series.

It is also, deliberately, **the shared foundation of the whole series** — every TOMC mod's advancement content is built on top of it.

**中文** — 它是个讲「你做了什么」的小模组，而不是又加一台机器或一个维度。它补的是《我的世界》里一直没做完的那一块：进度界面多出一个属于 TOMC 系列的选项卡，22 条新进度挂在原版既有的链条上——末影之眼、瞭望塔、望远镜、神庙。几处原版进度被换了前置或换了图标，好让新链条有地方落脚。此外还有一座能挣到的金色奖杯，以及把整个系列做完之后等着你的那封信。

它同时**刻意是整个系列共享的地基**——本作者的每一款 TOMC 模组，进度内容都建立在它之上。

---

## 怎么玩 / How It Plays

### 🔭 拿望远镜看一圈 / Point a spyglass at things

**EN** — The spyglass is a whole comedy chain now. Look at a wandering trader, a creeper, a piglin, a fireball, an end crystal — each one has a name that is funnier than the last, and the last one is hidden.

**中文** — 望远镜现在是一整条相声。看看流浪商人、苦力怕、猪灵、火球、末地水晶——每一条的名字都比上一条更好笑，最后一条还藏着。

### 🏛️ 把五座神庙走一遍 / Walk all five temples

**EN** — A chain that starts in any village and ends in an ocean monument: desert pyramid, jungle temple, woodland mansion, ocean monument. Finish it and the chain reaches back around to re-parent vanilla's own *Adventuring Time* and *The Far Lands* — so the temples are now part of finishing the vanilla tab.

**中文** — 一条从任意村庄开始、到海底神殿结束的连环：沙漠神殿、丛林神庙、林地府邸、海底神殿。走完它，链条会**绕回去**接上原版自己的**探索的时光**与**边境之地**——于是这五座神庙也成了「通关原版标签页」的一部分。

### 🥚 藏起来的四条 / The four you won't see coming

**EN** — Join the space program. Cast a totem ten times in thirty seconds. Hit a dolphin. Kill the undead with a plant in your hand. They stay invisible until you trip them.

**中文** — 当上太空人。30 秒内用掉十个不死图腾。打一次海豚。手持植物干掉亡灵生物。在你踩中之前，它们一直不会露脸。

### 🏅 挣一座 TOMC奖杯 / Earn the TOMC Trophy

**EN** — A golden two-block trophy. Right-click it for ten minutes of Luck II, or sneak-right-click empty-handed to fold the whole thing into your backpack. Leave it standing in your base as a receipt.

**中文** — 一座两格高的金色奖杯。右键它，获得十分钟幸运 II；潜行空手右键，把整座奖杯叠起来直接收进背包。留在基地里，当作一张收据。

### 💌 系列全收集 / Series completion

**EN** — One hidden node only lights up when the **whole TOMC series** is done — any advancement that a TOMC mod hangs under the shared TOMC root counts, so it grows as the series does. Run this mod on its own and there is nothing to count yet, which is fine. On the day you do clear it, the trophy carries a letter from the author as its description — with *your* name baked into the first line, so you can hand it to a friend and they will read about you.

**中文** — 有一个隐藏节点，只在整个 **TOMC 系列**都做完时才亮——凡是 TOMC 系列模组挂在同一个 TOMC 根节点下的进度都算在里面，所以它会跟着系列一起长大。单独装本模组时它还没有可统计的东西，这没问题。真做到那一天，你拿到的奖杯会把作者写的一封信作为自己的描述——**你的名字**会被烘进第一行，所以你可以把它丢给朋友，让对方读到的是你。

---

## 兼容性与边界 / Compatibility & Limits

**EN** — **Both sides need this mod, at the same version.** That is not a soft requirement: this mod extends vanilla's own advancement network format, and neither side negotiates versions — a vanilla client reading the extra bytes simply fails to decode. So a vanilla client cannot join a server running it, and the other way round.

**BetterAdvancements** is supported; if you use it, the new tab, the new icons and the multi-parent lines all render there too. Everything else is plain vanilla data — no other mod is required.

**中文** — **两端都要装本模组，且版本必须一致。** 这不是软要求：本模组扩展了原版自己的进度网络格式，而两端都不做版本协商——原版客户端读到多出来的字节会直接反序列化失败。所以纯原版客户端连不上装它的服务端，反过来也一样。

**BetterAdvancements** 已兼容；你要是装它，新选项卡、新图标、多条上游连线在它下面同样正常。除此之外全是原版数据，不依赖任何其它模组。

---

## 给整合包作者 / For Modpack Authors

**EN** — One config key, `load_builtin_content`, turns the content off and leaves a pure library: no tab, no trophy, no touched vanilla files — just the advancement machinery, ready for your own pack's advancement tree. And because every TOMC mod depends on this one, **if you include any TOMC mod, include this one**.

**中文** — 一个配置项 `load_builtin_content` 就能把内容全部关掉，只留一个纯前置库：没有选项卡、没有奖杯、不碰任何原版文件，只剩下进度机制，供你自己的整合包使用。而且因为每一款 TOMC 模组都依赖它，**只要你装了任何一款 TOMC 模组，就请一并装上它**。

---

## 关于本项目 / About This Project

**EN** — Published as **[KAD] 进取 Keep Advancing** (mod id `keepadvancing`). **TOMC** stands for *Tales of Minecraft* — and also for *ThatOne's Minecraft*. Author: **ThatOne**. Licensed **LGPL-3.0-or-later**. Development assisted by **DeepSeek Harness (deepseek-flash)**; the design, the trade-offs and the final responsibility are the author's.

The build in the download is for **Fabric**. It is a library at heart, so it is built to be extended to other targets later — if and when that happens, it will be announced here.

Interested developers: the datapack and trigger reference lives in the repository's `docs/`, and the public API is documented there. Feel free to take a look on GitHub.

**中文** — 发布用模组名 **[KAD] 进取 Keep Advancing**（mod id `keepadvancing`）。**TOMC** = *Tales of Minecraft*，也是 *ThatOne's Minecraft*。作者 **ThatOne**。许可 **LGPL-3.0-or-later**。开发辅助：**DeepSeek Harness（deepseek-flash）**；设计、取舍与最终责任在作者。

下载到的这一版是 **Fabric** 版。它本质上是套库，所以从结构上就是奔着「以后能扩到别的平台」去写的——真到那一天，会在这里说。

有兴趣的开发者：数据包与触发器的参考文档在仓库的 `docs/` 里，公开接口也在那写着，可以去 GitHub 看看。

---

## 复制用 · 纯英文 / Copy-paste: English only

[KAD] 进取 Keep Advancing — the advancement layer of the TOMC series.

It adds a TOMC tab to your advancement screen, 22 new advancements that slot straight into vanilla's own tabs, and a golden trophy block for your base. The spyglass becomes a comedy chain you can point at a trader, a creeper, a piglin, a fireball or an end crystal. A five-temple chain runs from any village to an ocean monument and then loops back to re-parent vanilla's own Adventuring Time and The Far Lands, so those temples count toward finishing the vanilla tab. Four achievements stay hidden until you trip them. Finish the whole TOMC series and the trophy you earn carries a letter from the author as its description — with your name baked into the first line.

Every TOMC mod needs this one to have an advancement system at all, so if you run any of them, run this.

Both sides need it, at the same version — it extends vanilla's advancement network format, and a vanilla client cannot decode the extra bytes. BetterAdvancements is supported. One config key turns all the content off and leaves a pure library for your own advancement tree.

Fabric. By ThatOne. LGPL-3.0-or-later. Development assisted by DeepSeek Harness (deepseek-flash); the design, the trade-offs and the final responsibility are the author's. Not every line, doc and design decision has been reviewed by a human. The build is for Fabric today; it is a library at heart and built to be extended later.

Developers: the datapack and trigger reference and the public API are documented in the repository's docs/ on GitHub.

---

## 复制用 · 纯中文 / Copy-paste: 中文 only

[KAD] 进取 Keep Advancing —— TOMC 系列的进度层。

它在进度界面里加出一个「TOMC」选项卡、22 条直接长在原版标签页里的新进度，还有一座能摆在基地里的金色奖杯。望远镜成了一整条相声，你可以拿它对准流浪商人、苦力怕、猪灵、火球和末地水晶。一条五连环从任意村庄走到海底神殿，再绕回去接上原版的**探索的时光**与**边境之地**——于是那五座神庙也算进了「通关原版标签页」。四条成就藏起来，直到你自己踩中。把整个 TOMC 系列做完之后，你拿到的奖杯会把作者写的一封信作为自己的描述，而**你的名字**烘在第一行。

本作者的每一款 TOMC 模组，都要靠它才拥有进度系统，所以你装了其中任何一款，就请一并装上它。

两端都要装，且版本必须一致——它扩展了原版的进度网络格式，纯原版客户端读到多出来的字节无法解码。BetterAdvancements 已兼容。一个配置项就能把内容全部关掉，只留一个纯前置库供你自己的进度树使用。

Fabric。作者 ThatOne。LGPL-3.0-or-later。开发辅助：DeepSeek Harness（deepseek-flash），设计、取舍与最终责任在作者。且并不是所有代码、文档与设计均经过人工审查与测试。今天是 Fabric 版；它本质上是套库，从结构上就是奔着以后能扩展去写的。

开发者：数据包与触发器的参考文档、以及公开接口，都写在仓库的 docs/ 里，可以去 GitHub 看看。
