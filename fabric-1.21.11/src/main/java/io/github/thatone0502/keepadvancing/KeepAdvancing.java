package io.github.thatone0502.keepadvancing;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.thatone0502.keepadvancing.block.KeepAdvancingBlocks;
import io.github.thatone0502.keepadvancing.condition.ConfigResourceCondition;
import io.github.thatone0502.keepadvancing.config.KeepAdvancingConfig;
import io.github.thatone0502.keepadvancing.content.BuiltinContentPack;
import io.github.thatone0502.keepadvancing.logic.ClientGuiReports;
import io.github.thatone0502.keepadvancing.logic.ModScan;
import io.github.thatone0502.keepadvancing.network.GuiReportPayload;
import io.github.thatone0502.keepadvancing.trigger.KeepAdvancingTriggers;

/**
 * Keep Advancing 的主入口。
 *
 * <p>进度几乎全部由 {@code data/keepadvancing/advancement} 下的数据驱动 JSON 定义。
 * 本类只做接线：
 * <ol>
 *   <li>{@code keepadvancing:timer_test}——每 tick 采样一次，把「累加器 + 时间窗」这类
 *       数据格式表达不了的判定收进一个通用触发器里；</li>
 *   <li>{@code keepadvancing:mod_loaded}——玩家加入时判定一次，检查环境里是否加载了
 *       某个包名匹配的模组；</li>
 *   <li>{@code keepadvancing:advancements_made}——每秒评估一次，看某组进度的完成
 *       数量/比例是否达到要求；</li>
 *   <li>{@code keepadvancing:gui_open}——每 tick 读一次当前界面，按名字/正则匹配；</li>
 *   <li>{@code keepadvancing:structure_present}——每秒扫一次玩家周围，看有没有指定的结构。</li>
 * </ol>
 *
 * <p>本模组自己的进度与奖杯方块受 {@code config/keepadvancing.json} 的
 * {@code load_builtin_content} 开关控制（见 {@link KeepAdvancingConfig} 与
 * {@link ConfigResourceCondition}）：把它设成 {@code false}，本模组就只剩上面这五个触发器
 * 和公开 API，可以当纯前置库用。触发器永远注册，不受开关影响。
 *
 * <p>覆盖原版进度的那 6 个 JSON 单独放在内置数据包 {@link BuiltinContentPack} 里：用资源条件
 * 关掉它们会让原版进度整个 id 消失（低优先级副本不会接管），所以改成「包根本不注册」。
 */
public class KeepAdvancing implements ModInitializer {

    public static final String MOD_ID = "keepadvancing";
    public static final Logger LOGGER = LoggerFactory.getLogger("TOMC-Keep Advancing");

    @Override
    public void onInitialize() {
        // 配置要最先读：资源条件是在数据包重载时求值的，配置必须先就位。
        KeepAdvancingConfig.load();

        // 条件类型也一样，必须早于任何一次数据包加载。
        ConfigResourceCondition.init();

        // 覆盖原版进度的那 6 个 JSON 放在一个内置数据包里，只在开关打开时注册。
        // 用资源条件做不到这件事：条件跳过文件时，原版数据包里的同名副本不会接管，那个进度 id 会直接消失。
        BuiltinContentPack.init();

        // 方块与物品。必须早于数据包解析——进度 JSON 把 keepadvancing:tomc_trophy 当奖励物品引用。
        //
        // 关掉内置内容时方块、物品、创造页条目一起不注册：想拿本模组当纯前置库用的人，不该
        // 被迫接受一个永远拿不到的方块。数据层（models / blockstates / items / lang）仍留在
        // jar 里，只是没有任何东西引用它们；服务端本来也不加载这些客户端资源。
        if (KeepAdvancingConfig.loadBuiltInContent()) {
            KeepAdvancingBlocks.init();
        } else {
            LOGGER.info("Built-in content is disabled in the config; keepadvancing:tomc_trophy is not registered.");
        }

        // 注册五个自定义触发器（实例常驻，数据包按名字查表）。与开关无关，永远注册。
        KeepAdvancingTriggers.init();

        // 采样。触发器自己在没有监听者时立即返回，所以这里不需要额外开关。
        ServerTickEvents.END_SERVER_TICK.register(KeepAdvancingTriggers.TIMER_TEST::tick);

        // 「某组进度的完成数量/比例」判定。自己按 EVALUATION_INTERVAL_TICKS 取模，
        // 每 20 tick 才真正评估一次。
        ServerTickEvents.END_SERVER_TICK.register(KeepAdvancingTriggers.ADVANCEMENTS_MADE::tick);

        // 界面检测：读取客户端每 tick 上报的那份状态，本身几乎不花时间。
        ServerTickEvents.END_SERVER_TICK.register(KeepAdvancingTriggers.GUI_OPEN::tick);

        // 结构检测：自己按 EVALUATION_INTERVAL_TICKS 取模，每秒才真正扫一次。
        ServerTickEvents.END_SERVER_TICK.register(KeepAdvancingTriggers.STRUCTURE_PRESENT::tick);

        // 模组集合在服务器启动后就固定了，只在玩家加入时判定一次。
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> KeepAdvancingTriggers.MOD_LOADED.onPlayerJoin(handler.getPlayer()));

        // 客户端 → 服务端：当前开着哪个界面。载荷类型必须两端都注册，否则这个通道协商不上、
        // canSend 恒为 false，gui_open 就永远只能看到容器类界面。
        PayloadTypeRegistry.playC2S().register(GuiReportPayload.TYPE, GuiReportPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(GuiReportPayload.TYPE, (payload, context) ->
                ClientGuiReports.set(context.player(), payload.screenClass(), payload.customId()));

        // 断线时清掉那份状态，避免玩家对象/数据被留住。
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> ClientGuiReports.clear(handler.getPlayer()));

        // 每次开服重新扫描一次包名（开发环境下模组路径会变）。
        // 扫描要花几百毫秒（实测 43 个模组 221 ms，整合包可能更久），所以放到后台线程预热，
        // 绝不占用玩家登录或每 tick 的时间。
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ModScan.invalidate();
            ModScan.warmUpAsync();
        });

        LOGGER.info("TOMC-Keep Advancing initialized (advancements are data-driven).");
    }
}
