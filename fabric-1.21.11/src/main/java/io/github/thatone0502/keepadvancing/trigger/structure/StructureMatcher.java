package io.github.thatone0502.keepadvancing.trigger.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * 一份结构模板的"可匹配形态"。
 *
 * <p><b>为什么不直接用 {@code StructureTemplate}。</b>它的调色板字段是 private，没有任何公开
 * 访问器（只有 {@code filterBlocks(...)}，而那个要求你先说出想找哪个方块）。所以这里走
 * {@code template.save(new CompoundTag())} 把它序列化成标准结构 NBT，自己解析
 * {@code palettes} + {@code blocks} —— 格式稳、全是公开 API，而且副产品是方格级别的方块实体
 * NBT 也一并拿到了。
 *
 * <p><b>朝向的处理。</b>每个元素预计算一个"相对锚点方块"的偏移 {@code delta}，于是匹配时
 * 只要在世界里发现一个锚点方块，就能直接算出整份结构的每一格坐标，不需要再推原点：
 * {@code world(o) = anchorWorld + delta(o)}。这对旋转天然成立，因为
 * {@code calculateRelativePosition} 是仿射的，原点被平移多少，所有格子就一起平移多少。
 *
 * <p><b>锚点为什么能省这么多。</b>暴力做法是拿区域内每一个位置当原点去对整份模板，代价是
 * 区域体积 × 模板格数。改成"先找锚点方块"后，代价降到区域内该方块的出现次数 × 模板格数 ——
 * 一栋房子可能只有几个石头基座，而区域里有一百多万个位置。
 */
public final class StructureMatcher {

    /** 一条待检查的模板方块，坐标是相对锚点方块的偏移。 */
    public record Entry(int dx, int dy, int dz, BlockState state, CompoundTag nbt) {
    }

    /** 一种朝向（镜像 × 旋转）下预先算好的检查表。 */
    public record Orientation(List<Entry> entries) {
    }

    private final Block anchor;
    private final List<Orientation> orientations;
    private final int entryCount;

    private StructureMatcher(Block anchor, List<Orientation> orientations, int entryCount) {
        this.anchor = anchor;
        this.orientations = orientations;
        this.entryCount = entryCount;
    }

    /** 世界里需要先找到的那个方块。扫描区域时只对它做判断。 */
    public Block anchor() {
        return anchor;
    }

    /** 模板里参与比对的格子数（所有朝向共用），用来估算一次命中要花多少代价。 */
    public int entryCount() {
        return entryCount;
    }

    /**
     * 编译一份模板。
     *
     * @param blocks        方块注册表查找器（{@code level.registryAccess().lookupOrThrow(Registries.BLOCK)}）
     * @param allowRotation 是否额外尝试 4 种旋转
     * @param allowMirror   是否额外尝试 3 种镜像
     * @param matchNbt      是否连方块实体 NBT 一起比（子集语义，见 {@link #nbtContains}）
     * @param includeAir    true＝连空气一起精确比对（原版语义）；false＝忽略模板里的空气格
     * @return 编译结果；模板为空、只有空气、或只有一个非空气方块时返回 empty（调用方负责告警）
     */
    public static Optional<StructureMatcher> build(StructureTemplate template, HolderGetter<Block> blocks,
            boolean allowRotation, boolean allowMirror, boolean matchNbt, boolean includeAir) {

        CompoundTag tag = template.save(new CompoundTag());
        List<BlockState> palette = readPalette(tag, blocks);
        if (palette.isEmpty()) {
            return Optional.empty();
        }

        List<RawEntry> raw = new ArrayList<>();
        ListTag blocksTag = tag.getListOrEmpty("blocks");
        for (int index = 0; index < blocksTag.size(); index++) {
            CompoundTag entry = blocksTag.getCompoundOrEmpty(index);
            ListTag posTag = entry.getListOrEmpty("pos");
            int state = entry.getInt("state").orElse(0);
            if (state < 0 || state >= palette.size()) {
                continue;
            }
            BlockState blockState = palette.get(state);
            if (!includeAir && blockState.isAir()) {
                continue;
            }
            CompoundTag nbt = matchNbt ? entry.getCompound("nbt").orElse(null) : null;
            raw.add(new RawEntry(
                    new BlockPos(posTag.getIntOr(0, 0), posTag.getIntOr(1, 0), posTag.getIntOr(2, 0)),
                    blockState, nbt));
        }
        if (raw.isEmpty()) {
            return Optional.empty();
        }

        // 锚点 = 模板里出现次数最少的非空气方块。它在世界里命中的次数最少，扫描最便宜。
        Map<Block, Integer> counts = new HashMap<>();
        for (RawEntry entry : raw) {
            if (!entry.state().isAir()) {
                counts.merge(entry.state().getBlock(), 1, Integer::sum);
            }
        }
        Block anchor = null;
        BlockPos anchorPos = null;
        int fewest = Integer.MAX_VALUE;
        for (Map.Entry<Block, Integer> candidate : counts.entrySet()) {
            if (candidate.getValue() >= fewest) {
                continue;
            }
            for (RawEntry entry : raw) {
                if (entry.state().getBlock() == candidate.getKey() && !entry.state().isAir()) {
                    anchor = candidate.getKey();
                    anchorPos = entry.pos();
                    fewest = candidate.getValue();
                    break;
                }
            }
        }
        if (anchor == null || anchorPos == null) {
            // 整份模板都是空气/结构空位，没有可扫描的东西。
            return Optional.empty();
        }

        List<Mirror> mirrors = allowMirror
                ? List.of(Mirror.NONE, Mirror.LEFT_RIGHT, Mirror.FRONT_BACK)
                : List.of(Mirror.NONE);
        List<Rotation> rotations = allowRotation
                ? List.of(Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90)
                : List.of(Rotation.NONE);

        List<Orientation> orientations = new ArrayList<>(mirrors.size() * rotations.size());
        for (Mirror mirror : mirrors) {
            for (Rotation rotation : rotations) {
                StructurePlaceSettings settings = new StructurePlaceSettings()
                        .setMirror(mirror)
                        .setRotation(rotation)
                        .setRotationPivot(BlockPos.ZERO);
                BlockPos relativeAnchor = StructureTemplate.calculateRelativePosition(settings, anchorPos);
                List<Entry> entries = new ArrayList<>(raw.size());
                for (RawEntry entry : raw) {
                    BlockPos relative = StructureTemplate.calculateRelativePosition(settings, entry.pos());
                    entries.add(new Entry(
                            relative.getX() - relativeAnchor.getX(),
                            relative.getY() - relativeAnchor.getY(),
                            relative.getZ() - relativeAnchor.getZ(),
                            entry.state(), entry.nbt()));
                }
                orientations.add(new Orientation(List.copyOf(entries)));
            }
        }
        return Optional.of(new StructureMatcher(anchor, List.copyOf(orientations), raw.size()));
    }

    /** 把 {@code build} 的输入先简化成中间形态，避免后面反复读 record。 */
    private record RawEntry(BlockPos pos, BlockState state, CompoundTag nbt) {
    }

    /**
     * 世界里的 {@code anchorWorld} 处有一个锚点方块时，整份结构是否成立。
     * 任意一种启用中的朝向完全一致即通过。
     */
    public boolean matchesAt(ServerLevel level, BlockPos anchorWorld) {
        HolderLookup.Provider registries = level.registryAccess();
        for (Orientation orientation : orientations) {
            if (matchesOrientation(level, anchorWorld, orientation, registries)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesOrientation(ServerLevel level, BlockPos anchorWorld,
            Orientation orientation, HolderLookup.Provider registries) {
        for (Entry entry : orientation.entries()) {
            BlockPos at = anchorWorld.offset(entry.dx(), entry.dy(), entry.dz());
            // BlockState 是按组合池化（interned）的，同一状态全局只有一个实例，
            // 所以 equals 就是"方块相同且所有属性相同"。
            if (!level.getBlockState(at).equals(entry.state())) {
                return false;
            }
            if (entry.nbt() != null && !nbtContains(level, at, entry.nbt(), registries)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 方块实体 NBT 的<b>子集</b>比对：模板里写到的每一个键，世界里都必须存在且深度相等；
     * 世界里多出来的键不管。
     *
     * <p>不用"完全相等"是因为两边的 NBT 来源不同：模板里的那份来自结构文件，世界里的那份来自
     * {@code saveWithoutMetadata}，会带上一些结构文件里根本没有的键（版本、组件等）。完全相等
     * 会让这个可选功能几乎永远为假，而作者想要表达的其实是"这一格的方块实体确实是模板里那个
     * 样子"。
     */
    private static boolean nbtContains(ServerLevel level, BlockPos pos, CompoundTag expected,
            HolderLookup.Provider registries) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }
        CompoundTag actual = blockEntity.saveWithoutMetadata(registries);
        for (String key : expected.keySet()) {
            Tag wanted = expected.get(key);
            if (wanted == null || !wanted.equals(actual.get(key))) {
                return false;
            }
        }
        return true;
    }

    /** 读调色板。结构文件要么写单个 {@code palette}，要么写 {@code palettes}（多调色板）——后者只用第一个。 */
    private static List<BlockState> readPalette(CompoundTag tag, HolderGetter<Block> blocks) {
        ListTag paletteTag = null;
        Optional<ListTag> palettes = tag.getList("palettes");
        if (palettes.isPresent() && palettes.get().size() > 0) {
            Tag first = palettes.get().get(0);
            if (first instanceof ListTag listTag) {
                paletteTag = listTag;
            }
        }
        if (paletteTag == null) {
            paletteTag = tag.getListOrEmpty("palette");
        }
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int index = 0; index < paletteTag.size(); index++) {
            palette.add(NbtUtils.readBlockState(blocks, paletteTag.getCompoundOrEmpty(index)));
        }
        return palette;
    }
}
