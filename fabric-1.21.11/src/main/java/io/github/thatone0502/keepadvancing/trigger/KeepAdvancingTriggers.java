package io.github.thatone0502.keepadvancing.trigger;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import io.github.thatone0502.keepadvancing.trigger.gui.GuiOpenTrigger;
import io.github.thatone0502.keepadvancing.trigger.structure.StructurePresentTrigger;

/**
 * 五个自定义触发器的实例容器。
 *
 * <p>1.21.11 中 {@code CriteriaTriggers.register(String, T)} 是 private，但触发器本身
 * 已经是一个注册表：{@code BuiltInRegistries.TRIGGER_TYPES}，而
 * {@code CriteriaTriggers.CODEC == BuiltInRegistries.TRIGGER_TYPES.byNameCodec()}，
 * {@code Criterion} 又用 {@code ExtraCodecs.dispatchOptionalValue("trigger", "conditions", ...)}
 * 按 trigger 名字查表。
 *
 * <p>因此：进度 JSON 里写 {@code "trigger": "keepadvancing:xxx"} 时必须先把实例写进
 * {@code TRIGGER_TYPES}，否则整份进度会解析失败。Fabric 的 main entrypoint 在
 * {@code Bootstrap.bootStrap()}（注册表冻结）之前执行，所以 {@link #init()} 里注册是安全的。
 */
public final class KeepAdvancingTriggers {

    public static final Identifier TIMER_TEST_ID = Identifier.fromNamespaceAndPath("keepadvancing", "timer_test");
    public static final Identifier MOD_LOADED_ID = Identifier.fromNamespaceAndPath("keepadvancing", "mod_loaded");
    public static final Identifier ADVANCEMENTS_MADE_ID =
            Identifier.fromNamespaceAndPath("keepadvancing", "advancements_made");
    public static final Identifier GUI_OPEN_ID = Identifier.fromNamespaceAndPath("keepadvancing", "gui_open");
    public static final Identifier STRUCTURE_PRESENT_ID =
            Identifier.fromNamespaceAndPath("keepadvancing", "structure_present");

    public static final TimerTestTrigger TIMER_TEST = new TimerTestTrigger();
    public static final ModLoadedTrigger MOD_LOADED = new ModLoadedTrigger();
    public static final AdvancementsMadeTrigger ADVANCEMENTS_MADE = new AdvancementsMadeTrigger();
    public static final GuiOpenTrigger GUI_OPEN = new GuiOpenTrigger();
    public static final StructurePresentTrigger STRUCTURE_PRESENT = new StructurePresentTrigger();

    private KeepAdvancingTriggers() {
    }

    /** 必须在 {@code Bootstrap.bootStrap()} 之前调用（{@code KeepAdvancing.onInitialize()} 第一句）。 */
    public static void init() {
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, TIMER_TEST_ID, TIMER_TEST);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, MOD_LOADED_ID, MOD_LOADED);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, ADVANCEMENTS_MADE_ID, ADVANCEMENTS_MADE);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, GUI_OPEN_ID, GUI_OPEN);
        Registry.register(BuiltInRegistries.TRIGGER_TYPES, STRUCTURE_PRESENT_ID, STRUCTURE_PRESENT);
    }
}
