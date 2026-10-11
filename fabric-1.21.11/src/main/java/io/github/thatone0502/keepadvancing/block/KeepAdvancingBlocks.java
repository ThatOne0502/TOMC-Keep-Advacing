package io.github.thatone0502.keepadvancing.block;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * 本模组的方块与对应物品。
 *
 * <p>注册必须在 {@code Bootstrap.bootStrap()}（注册表冻结）之前完成，也就是在
 * {@code KeepAdvancing.onInitialize()} 里；进度 JSON 会引用 {@code keepadvancing:tomc_trophy}
 * 作为奖励，所以这一句要排在数据包解析之前。
 *
 * <p>{@code CreativeModeTabs.FUNCTIONAL_BLOCKS} 在 1.21.11 里是 <b>private</b>（整个
 * {@code CreativeModeTabs} 没有任何 public 常量），所以这里自己拼出那个 {@code ResourceKey}，
 * 再用 Fabric 的 {@code ItemGroupEvents} 往原版「功能方块」页里加条目。
 */
public final class KeepAdvancingBlocks {

    public static final Identifier TOMC_TROPHY_ID =
            Identifier.fromNamespaceAndPath("keepadvancing", "tomc_trophy");

    public static final ResourceKey<Block> TOMC_TROPHY_KEY = ResourceKey.create(Registries.BLOCK, TOMC_TROPHY_ID);
    public static final ResourceKey<Item> TOMC_TROPHY_ITEM_KEY = ResourceKey.create(Registries.ITEM, TOMC_TROPHY_ID);

    /** 属性对齐金块：硬度 3 / 抗爆 6、任意镐子可挖、note block 音色为钟。 */
    public static final TomcTrophyBlock TOMC_TROPHY = new TomcTrophyBlock(
            BlockBehaviour.Properties.of()
                    .setId(TOMC_TROPHY_KEY)
                    .mapColor(MapColor.GOLD)
                    .instrument(NoteBlockInstrument.BELL)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    // 模型在腰部收细、局部伸出方块外，开遮挡剔除会让相邻方块的面朝"空洞"被剔掉。
                    .noOcclusion()
                    .requiresCorrectToolForDrops());

    public static final BlockItem TOMC_TROPHY_ITEM =
            new BlockItem(TOMC_TROPHY, new Item.Properties().setId(TOMC_TROPHY_ITEM_KEY).useBlockDescriptionPrefix());

    /** 原版「功能方块」创造模式标签页。字段是 private，只能自己构造。 */
    private static final ResourceKey<CreativeModeTab> FUNCTIONAL_BLOCKS =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("functional_blocks"));

    private KeepAdvancingBlocks() {
    }

    public static void init() {
        Registry.register(BuiltInRegistries.BLOCK, TOMC_TROPHY_ID, TOMC_TROPHY);
        Registry.register(BuiltInRegistries.ITEM, TOMC_TROPHY_ID, TOMC_TROPHY_ITEM);
        ItemGroupEvents.modifyEntriesEvent(FUNCTIONAL_BLOCKS).register(entries -> entries.accept(TOMC_TROPHY_ITEM));
    }
}
