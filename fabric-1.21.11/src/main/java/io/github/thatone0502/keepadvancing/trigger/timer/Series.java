package io.github.thatone0502.keepadvancing.trigger.timer;

/**
 * 单个 (玩家, source) 的采样序列。
 *
 * <p>原版的统计是**累计计数器**，不是一个事件流：{@code minecraft.used:minecraft.totem_of_undying}
 * 等于 12 只说明"一共用了 12 个"，不说明那 12 次分别发生在什么时候。而 timer_test 要判定的
 * 恰恰是时间窗口内的事情，所以只能在 Java 侧按 tick 采样、再做差分。
 *
 * <p>核心存储策略：**只在值发生变化时记一笔增量**。绝大多数统计在绝大多数 tick 上是静止的，
 * 所以稀疏表的大小正比于"变化次数"而不是正比于时间。有了增量之后：
 * <ul>
 *   <li>{@code delta}（窗口净变化）= 窗口内增量之和；</li>
 *   <li>各种 count / total = 对窗口内增量做筛选求和；</li>
 *   <li>{@code value}（当前绝对值）= 最后一次采样值。</li>
 * </ul>
 * 全部 O(窗口内事件数)，且不需要保存"窗口起点那一刻的值"。
 *
 * <p>环上只在尾部追加、只在头部裁剪，因此查询一律从新到旧扫描、遇到窗口外的 tick 立即中断。
 */
public final class Series {

    /** 事件上限。window 很大 + source 每 tick 都在变时，必须有个兜底，否则内存会失控。 */
    public static final int MAX_EVENTS = 4096;

    private long[] ticks = new long[64];
    private int[] deltas = new int[64];
    /** 最老元素的下标。 */
    private int head;
    private int size;

    private int current;
    private boolean initialized;

    /** 是否已经拿到过至少一次采样。没有基线就没有任何可比性。 */
    public boolean initialized() {
        return initialized;
    }

    /** window 内"净变化"的终点：最后一次采样到的绝对值。 */
    public int current() {
        return current;
    }

    /** 记录一次采样；仅在首次或值发生变化时产生事件。 */
    public void accept(long tick, int value) {
        if (!initialized) {
            initialized = true;
            current = value;
            return;
        }
        int delta = value - current;
        if (delta != 0) {
            push(tick, delta);
            current = value;
        }
    }

    private void push(long tick, int delta) {
        if (size == ticks.length) {
            if (ticks.length >= MAX_EVENTS) {
                // 环已经到顶：牺牲最老的一笔，保证新事件一定进得来。
                head = (head + 1) % ticks.length;
                size--;
            } else {
                grow();
            }
        }
        int index = (head + size) % ticks.length;
        ticks[index] = tick;
        deltas[index] = delta;
        size++;
    }

    private void grow() {
        int capacity = Math.min(ticks.length * 2, MAX_EVENTS);
        long[] newTicks = new long[capacity];
        int[] newDeltas = new int[capacity];
        for (int i = 0; i < size; i++) {
            int index = (head + i) % ticks.length;
            newTicks[i] = ticks[index];
            newDeltas[i] = deltas[index];
        }
        ticks = newTicks;
        deltas = newDeltas;
        head = 0;
    }

    /** 丢弃 tick <= minTick 的事件（它们已经滑出所有相关窗口）。 */
    public void trim(long minTick) {
        while (size > 0 && ticks[head] <= minTick) {
            head = (head + 1) % ticks.length;
            size--;
        }
    }

    // ---- 窗口内查询：一律从新到旧，越界即停 ----

    public int countPositiveSince(long minTick) {
        int count = 0;
        for (int i = size - 1; i >= 0; i--) {
            int index = (head + i) % ticks.length;
            if (ticks[index] <= minTick) {
                break;
            }
            if (deltas[index] > 0) {
                count++;
            }
        }
        return count;
    }

    public int countNegativeSince(long minTick) {
        int count = 0;
        for (int i = size - 1; i >= 0; i--) {
            int index = (head + i) % ticks.length;
            if (ticks[index] <= minTick) {
                break;
            }
            if (deltas[index] < 0) {
                count++;
            }
        }
        return count;
    }

    public int countChangedSince(long minTick) {
        int count = 0;
        for (int i = size - 1; i >= 0; i--) {
            int index = (head + i) % ticks.length;
            if (ticks[index] <= minTick) {
                break;
            }
            count++;
        }
        return count;
    }

    /** 窗口内净变化。等于"当前值 − 窗口起点时的值"。 */
    public int sumSince(long minTick) {
        int sum = 0;
        for (int i = size - 1; i >= 0; i--) {
            int index = (head + i) % ticks.length;
            if (ticks[index] <= minTick) {
                break;
            }
            sum += deltas[index];
        }
        return sum;
    }

    public int sumPositiveSince(long minTick) {
        int sum = 0;
        for (int i = size - 1; i >= 0; i--) {
            int index = (head + i) % ticks.length;
            if (ticks[index] <= minTick) {
                break;
            }
            if (deltas[index] > 0) {
                sum += deltas[index];
            }
        }
        return sum;
    }

    /** 窗口内负向增量之和，返回负数（例如 -7 表示"一共降了 7"）。 */
    public int sumNegativeSince(long minTick) {
        int sum = 0;
        for (int i = size - 1; i >= 0; i--) {
            int index = (head + i) % ticks.length;
            if (ticks[index] <= minTick) {
                break;
            }
            if (deltas[index] < 0) {
                sum += deltas[index];
            }
        }
        return sum;
    }
}
