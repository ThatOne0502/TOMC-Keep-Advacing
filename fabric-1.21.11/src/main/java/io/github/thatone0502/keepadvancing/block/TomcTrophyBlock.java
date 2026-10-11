package io.github.thatone0502.keepadvancing.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * {@code keepadvancing:tomc_trophy}——全模组的收藏品奖杯。
 *
 * <p><b>双层方块。</b>模型自带的包围盒是 y 0…31.85（约两格）、x −5.4…21.4，本来就放不进一格。
 * 所以做成原版高花/门那样的双层：{@code half=lower} 渲染**整个**模型（模型自己向上伸进第二格，
 * 上半格不需要单独的模型），{@code half=upper} 用 {@link RenderShape#INVISIBLE} 不渲染、
 * 纯占位（挡住别人往奖杯里放方块），两半碰撞箱都是完整立方体。
 *
 * <p><b>行为约定：</b>
 * <ul>
 *   <li><b>潜行 + 空手右键</b>：把整座奖杯收走（两半一起消失、不产生掉落物实体），优先放进
 *       <b>当前选中的快捷栏槽位</b>；占用时退到整个背包，全满才在方块处正常掉落。</li>
 *   <li><b>普通右键</b>：紫水晶放置音效 + 10 分钟幸运（amplifier 1，游戏内显示「幸运 II」）。
 *       可重复点击，无冷却。</li>
 *   <li>破坏：任一半被破坏 → 两半一起消失，**只掉一个**（掉落条件写在战利品表的
 *       {@code half=lower} 上，破上半时由 {@link #playerWillDestroy} 主动让下半走正常破坏流程）。</li>
 * </ul>
 *
 * <p>为什么 {@code useWithoutItem} 就能兼顾两件事：原版 {@code ServerPlayerGameMode.useItemOn}
 * 只在 {@code !(潜行 && 手上有物品)} 时才调 {@code BlockState.useItemOn}，而
 * {@code BlockBehaviour.useItemOn} 默认返回 {@link InteractionResult#TRY_WITH_EMPTY_HAND}，
 * 紧接着就会调 {@code useWithoutItem}。潜行且手上有物品时原版会去放那个物品、根本不走这里——
 * 所以"潜行 + 空手"天然是唯一能触发拿起的操作，不需要额外判断主副手是否为空。
 *
 * <p>声音：按用户要求**全部采用金属声**（{@link SoundType#METAL}），不用钟声。
 */
public class TomcTrophyBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<TomcTrophyBlock> CODEC = BlockBehaviour.simpleCodec(TomcTrophyBlock::new);

    /** 直接复用原版的 half 属性（取值 {@code upper} / {@code lower}），与门、高花同一套语义。 */
    public static final EnumProperty<DoubleBlockHalf> HALF = DoublePlantBlock.HALF;

    /** 幸运持续时间：10 分钟（20 tick/秒）。 */
    public static final int LUCK_DURATION_TICKS = 10 * 60 * 20;

    /** 幸运 amplifier。原版等级显示 = amplifier + 1，所以 1 显示为「幸运 II」。 */
    public static final int LUCK_AMPLIFIER = 1;

    public TomcTrophyBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, FACING);
    }

    /** 站的下方必须是空的，否则放不下——和门/高花一样。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (pos.getY() < level.getMaxY() && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return true;
    }

    /**
     * 两半互相依赖：竖直方向上的邻居不是另一半时，自己变成空气。原版 {@code DoublePlantBlock}
     * 就是这么写的——它同时覆盖了"上半没了"和"下半没了"两个方向，所以两个方向都试。
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
            return neighbourState.is(this) && neighbourState.getValue(HALF) != half
                    ? super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random)
                    : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    /** 整个模型由下半格负责画出来，上半格不可见。 */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    /**
     * 破上半时，主动让下半走一次正常破坏流程（掉落一次）。上半自己的掉落由战利品表的
     * {@code half=lower} 条件挡住，所以无论从哪半边下手都只掉一个。
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.is(this) && belowState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.destroyBlock(below, !player.isCreative(), player, Block.UPDATE_ALL);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (player.isSecondaryUseActive()) {
                pickUp(level, pos, state, serverPlayer);
            } else {
                bless(level, pos, serverPlayer);
            }
        }
        // 客户端也要返回 SUCCESS，否则手臂不会挥动。
        return InteractionResult.SUCCESS;
    }

    /** 潜行 + 空手：整座奖杯（两半）直接进背包，失败才掉地上。 */
    private static void pickUp(Level level, BlockPos pos, BlockState state, ServerPlayer player) {
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockPos upper = lower.above();

        // 先清上半：它变成空气时会顺带把下半也带走，且两者都不经过玩家破坏路径，不会掉东西。
        level.setBlock(upper, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundType.METAL.getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);

        ItemStack trophy = new ItemStack(KeepAdvancingBlocks.TOMC_TROPHY_ITEM);
        Inventory inventory = player.getInventory();
        if (inventory.getSelectedItem().isEmpty()) {
            inventory.setSelectedItem(trophy);
        } else if (!inventory.add(trophy)) {
            // add() 可能只装下一部分，剩下的仍留在 trophy 里；一个都装不下时它原封不动。
            // 两种情况都只需要把剩余的丢出来。
            Block.popResource(level, pos, trophy);
        }
    }

    /** 普通右键：紫水晶放置音效 + 幸运。可重复点击，无冷却。 */
    private static void bless(Level level, BlockPos pos, ServerPlayer player) {
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        player.addEffect(new MobEffectInstance(MobEffects.LUCK, LUCK_DURATION_TICKS, LUCK_AMPLIFIER));
    }
}
