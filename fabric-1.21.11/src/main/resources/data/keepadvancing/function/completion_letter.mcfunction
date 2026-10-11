# TOMC 全内置进度达成奖励 —— 由 keepadvancing:tomc/completionist 的 rewards.function 触发。
# 执行者 = 该玩家，权限等级 2（AdvancementRewards 的 'function' 走 player.createCommandSourceStack()
# .withSuppressedOutput().withPermission(2)），tellraw / playsound 都在这个等级内。
# withSuppressedOutput() 只吞掉"命令回执"，不会影响 tellraw 与 playsound 的实际效果。

# 挑战达成音效（原版挑战进度弹窗用的那一个）。这条进度没有 display，所以不会自己响，需要手动补。
# ⚠ 1.21.11 的 /playsound 语法是 <sound> <source> <targets> <pos> [volume] [pitch] [minVolume]
#   —— 给了 targets 之后 pos 是**必填**的（Brigadier 树里 pos 挂在 targets 下面且无 executes 兜底），
#   少写就会被解析成「expected 3 coordinates」而整份 function 加载失败。~ ~ ~ = 玩家自己脚下。
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0

# ThatOne 的留言。用 translatable 组件发送：客户端会用「玩家自己的语言」解析这个键，
# 所以同一条命令在中英文客户端各显示各的；%s 由 with 里的 {"selector":"@s"} 填成玩家名。
tellraw @s {"translate":"keepadvancing.message.completion_letter","with":[{"selector":"@s"}],"color":"gold"}
