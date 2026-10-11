package io.github.thatone0502.keepadvancing.layout;

import io.github.thatone0502.keepadvancing.KeepAdvancing;
import io.github.thatone0502.keepadvancing.api.KaAdvancementNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.DisplayInfo;

/**
 * DAG 布局（rev14 §5.6）。
 *
 * <p>输入是一棵已经带好 multi_parents 附加边的 {@code AdvancementTree}（见 {@link KaAdvancementTree}）。
 * 只对**至少含一个"有额外父"节点的连通分量**做覆盖；其余分量保留原版
 * {@code TreeNodePosition}（Reingold–Tilford）算出来的位置 —— 也就是说，没写 multi_parents
 * 的数据包观感逐像素不变。
 *
 * <p>算法：拓扑分层 rank（y = rank）→ 层内初值 → 6 轮上下行迭代松弛 + 层内去重叠（x 间距 1 单位
 * = 28px）→ 归一化到 min(x)=0 / min(rank)=0 → 写回 {@code DisplayInfo.setLocation}。
 *
 * <p><b>已知取舍</b>：退化到纯树时，本算法的结果与 Reingold–Tilford 的观感不保证逐像素一致，
 * 这正是我们只在含 multi_parents 的分量上覆盖的原因。
 */
public final class DagLayout {

    private static final int MAX_RELAX_ROUNDS = 6;
    private static final float MIN_DX = 1.0F;
    private static final float EPSILON = 0.5F;

    private DagLayout() {
    }

    public static void apply(AdvancementTree tree) {
        List<AdvancementNode> nodes = new ArrayList<>(tree.nodes());
        int n = nodes.size();
        if (n <= 1) {
            return;
        }

        Map<AdvancementNode, Integer> index = new IdentityHashMap<>();
        for (int i = 0; i < n; i++) {
            index.put(nodes.get(i), i);
        }

        // 参与布局的只有"有 display"的节点；没有 display 的节点不占位置（原版同理）。
        boolean[] drawable = new boolean[n];
        List<List<Integer>> parents = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            AdvancementNode node = nodes.get(i);
            drawable[i] = node.advancement().display().isPresent();
            List<Integer> list = new ArrayList<>(2);
            AdvancementNode main = node.parent();
            Integer mainIndex = main == null ? null : index.get(main);
            if (mainIndex != null && mainIndex != i) {
                list.add(mainIndex);
            }
            for (AdvancementNode extra : ((KaAdvancementNode) node).ka$extraParents()) {
                Integer extraIndex = index.get(extra);
                if (extraIndex != null && extraIndex != i && !list.contains(extraIndex)) {
                    list.add(extraIndex);
                }
            }
            parents.add(list);
        }

        // 拓扑排序（Kahn）。有环 ⇒ 整个树回退到原版位置（建边阶段已经防环，这里是兜底）。
        int[] rank = new int[n];
        int[] indegree = new int[n];
        List<List<Integer>> children = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            children.add(new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            for (int p : parents.get(i)) {
                children.get(p).add(i);
                indegree[i]++;
            }
        }
        Deque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            if (indegree[i] == 0) {
                queue.add(i);
            }
        }
        int sorted = 0;
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            sorted++;
            for (int child : children.get(cur)) {
                rank[child] = Math.max(rank[child], rank[cur] + 1);
                if (--indegree[child] == 0) {
                    queue.add(child);
                }
            }
        }
        if (sorted < n) {
            KeepAdvancing.LOGGER.warn("keepadvancing: advancement graph still contains a cycle; keeping vanilla layout");
            return;
        }

        // 连通分量（无向并查集，含附加边）。
        int[] dsu = new int[n];
        for (int i = 0; i < n; i++) {
            dsu[i] = i;
        }
        for (int i = 0; i < n; i++) {
            for (int p : parents.get(i)) {
                union(dsu, i, p);
            }
        }

        boolean[] componentHasExtraEdge = new boolean[n];
        Map<Integer, List<Integer>> componentNodes = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(dsu, i);
            componentNodes.computeIfAbsent(root, key -> new ArrayList<>()).add(i);
            if (!((KaAdvancementNode) nodes.get(i)).ka$extraParents().isEmpty()) {
                componentHasExtraEdge[root] = true;
            }
        }

        int laidOut = 0;
        for (Map.Entry<Integer, List<Integer>> entry : componentNodes.entrySet()) {
            if (!componentHasExtraEdge[entry.getKey()]) {
                continue;
            }
            List<Integer> members = entry.getValue();
            if (members.size() > 1 && layoutComponent(nodes, members, parents, children, drawable, rank)) {
                laidOut++;
            }
        }
        if (laidOut > 0) {
            KeepAdvancing.LOGGER.info("keepadvancing: applied DAG layout to {} advancement component(s)", laidOut);
        }
    }

    /** @return true 表示这个分量真的被重排了（含至少两个可见节点）。 */
    private static boolean layoutComponent(List<AdvancementNode> nodes, List<Integer> members,
                                           List<List<Integer>> parents, List<List<Integer>> children,
                                           boolean[] drawable, int[] rank) {
        int topRank = 0;
        int visibleCount = 0;
        for (int i : members) {
            if (drawable[i]) {
                visibleCount++;
                topRank = Math.max(topRank, rank[i]);
            }
        }
        if (visibleCount < 2) {
            return false;
        }

        int levels = topRank + 1;
        List<List<Integer>> byLevel = new ArrayList<>(levels);
        for (int level = 0; level < levels; level++) {
            byLevel.add(new ArrayList<>());
        }
        for (int i : members) {
            if (drawable[i]) {
                byLevel.get(rank[i]).add(i);
            }
        }

        float[] x = new float[nodes.size()];
        Set<Integer> memberSet = new HashSet<>(members);
        for (List<Integer> level : byLevel) {
            level.sort(Comparator.comparingDouble(node -> meanParentX(parents, x, node)));
            for (int k = 0; k < level.size(); k++) {
                x[level.get(k)] = 2.0F * k;
            }
        }

        // 迭代松弛：一轮下行（父 → 子）+ 一轮上行（子 → 父），层内去重叠后看最大位移。
        for (int round = 0; round < MAX_RELAX_ROUNDS; round++) {
            float maxShift = 0.0F;
            for (List<Integer> level : byLevel) {
                for (int node : level) {
                    List<Integer> ps = parents.get(node);
                    if (!ps.isEmpty()) {
                        float sum = 0.0F;
                        int count = 0;
                        for (int p : ps) {
                            if (memberSet.contains(p) && drawable[p]) {
                                sum += x[p];
                                count++;
                            }
                        }
                        if (count > 0) {
                            float target = sum / count;
                            maxShift = Math.max(maxShift, Math.abs(target - x[node]));
                            x[node] = target;
                        }
                    }
                }
                maxShift = Math.max(maxShift, separate(level, x));
            }

            for (int level = levels - 1; level >= 0; level--) {
                List<Integer> nodesOnLevel = byLevel.get(level);
                for (int node : nodesOnLevel) {
                    float sum = 0.0F;
                    int count = 0;
                    for (int c : children.get(node)) {
                        if (memberSet.contains(c) && drawable[c]) {
                            sum += x[c];
                            count++;
                        }
                    }
                    if (count > 0) {
                        float target = sum / count;
                        maxShift = Math.max(maxShift, Math.abs(target - x[node]));
                        x[node] = target;
                    }
                }
                maxShift = Math.max(maxShift, separate(nodesOnLevel, x));
            }

            if (maxShift < EPSILON) {
                break;
            }
        }

        // 收尾：补一轮**纯下行**（子 ← 父）居中，不再走上行。
        // 上面每轮以"上行"（父 ← 子）收尾，父被移走之后子仍停在**上一轮的中点**上，
        // 差了一轮；几何表现就是"双父汇合节点跑到其中一个父的正下方"，
        // 而 DAG 语义要求它落在两个父之间。这一步用父的**最终**位置重新居中。
        for (List<Integer> level : byLevel) {
            for (int node : level) {
                List<Integer> ps = parents.get(node);
                if (ps.isEmpty()) {
                    continue;
                }
                float sum = 0.0F;
                int count = 0;
                for (int p : ps) {
                    if (memberSet.contains(p) && drawable[p]) {
                        sum += x[p];
                        count++;
                    }
                }
                if (count > 0) {
                    x[node] = sum / count;
                }
            }
            separate(level, x);
        }

        // 归一化：整个分量平移到 min(x) == 0（rank 天然从 0 起）。
        float minX = Float.MAX_VALUE;
        for (int i : members) {
            if (drawable[i] && Float.isFinite(x[i])) {
                minX = Math.min(minX, x[i]);
            }
        }
        if (minX == Float.MAX_VALUE) {
            return false;
        }

        int changed = 0;
        for (int i : members) {
            DisplayInfo display = nodes.get(i).advancement().display().orElse(null);
            if (display == null || !Float.isFinite(x[i])) {
                continue;
            }
            display.setLocation(x[i] - minX, rank[i]);
            changed++;
        }
        return changed > 0;
    }

    private static float meanParentX(List<List<Integer>> parents, float[] x, int node) {
        List<Integer> ps = parents.get(node);
        if (ps.isEmpty()) {
            return x[node];
        }
        float sum = 0.0F;
        for (int p : ps) {
            sum += x[p];
        }
        return sum / ps.size();
    }

    /**
     * 层内按 x 排序后强制最小间距，返回本次需要移动的最大距离。
     *
     * <p><b>必须左右各推一次再取中位</b>，不能只右推。只右推会把整组兄弟单调向右挤，
     * 破坏父子居中的对称性，实测后果是：双父汇合节点掉到其中一个父的正下方、
     * 父节点掉到某个子的正上方（菱形退化成 L 形）。取中位后整组是"居中分开"，
     * 于是"父居中于子 / 子居中于父"能同时成立，布局才有稳定不动点。
     */
    private static float separate(List<Integer> level, float[] x) {
        int size = level.size();
        if (size <= 1) {
            return 0.0F;
        }
        level.sort(Comparator.comparingDouble(node -> x[node]));

        float[] right = new float[size];
        float cursor = x[level.get(0)];
        right[0] = cursor;
        for (int k = 1; k < size; k++) {
            cursor = Math.max(cursor + MIN_DX, x[level.get(k)]);
            right[k] = cursor;
        }

        float[] left = new float[size];
        cursor = x[level.get(size - 1)];
        left[size - 1] = cursor;
        for (int k = size - 2; k >= 0; k--) {
            cursor = Math.min(cursor - MIN_DX, x[level.get(k)]);
            left[k] = cursor;
        }

        float maxShift = 0.0F;
        for (int k = 0; k < size; k++) {
            int node = level.get(k);
            float target = (right[k] + left[k]) * 0.5F;
            maxShift = Math.max(maxShift, Math.abs(target - x[node]));
            x[node] = target;
        }
        return maxShift;
    }

    private static int find(int[] dsu, int i) {
        int root = i;
        while (dsu[root] != root) {
            root = dsu[root];
        }
        while (dsu[i] != root) {
            int next = dsu[i];
            dsu[i] = root;
            i = next;
        }
        return root;
    }

    private static void union(int[] dsu, int a, int b) {
        int rootA = find(dsu, a);
        int rootB = find(dsu, b);
        if (rootA != rootB) {
            dsu[rootB] = rootA;
        }
    }
}
