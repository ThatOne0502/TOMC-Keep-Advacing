package io.github.thatone0502.keepadvancing.trigger.advancement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code advancements_made} 的选择器键——{@code list} 里每个条目声明自己用哪一种选法。
 *
 * <p>前六个与<b>原版命令</b>{@code /advancement grant &lt;targets&gt; &lt;only|from|through|until&gt; &lt;advancement&gt;}
 * 的遍历模式逐位对齐（{@code after} / {@code before} 是去掉锚点后的两个变体），
 * 详见 {@code net.minecraft.server.commands.AdvancementCommands}：
 *
 * <pre>
 *   键        等价物          parents  children  锚点  结果集
 *   only      ONLY            ✗        ✗         含    {X}
 *   from      FROM            ✗        ✓         含    {X} ∪ 后代
 *   through   THROUGH         ✓        ✓         含    祖先 ∪ {X} ∪ 后代
 *   until     UNTIL           ✓        ✗         含    祖先 ∪ {X}
 *   after     FROM 去锚点      ✗        ✓        不含   后代
 *   before    UNTIL 去锚点     ✓        ✗        不含   祖先
 *   between   （原版没有）      ✓        ✓         含    A 到 B 链路上的全部，含两端
 * </pre>
 *
 * <p>注意 {@code through} 不是"子树"：它是<b>祖先链 + 自身 + 子树</b>。这正是原版
 * {@code THROUGH} 的定义（{@code parents=true, children=true}），不要想当然。
 */
public enum SelectorMode {

    ONLY("only"),
    FROM("from"),
    THROUGH("through"),
    UNTIL("until"),
    AFTER("after"),
    BEFORE("before"),
    BETWEEN("between");

    private static final Map<String, SelectorMode> BY_NAME;

    static {
        Map<String, SelectorMode> byName = new LinkedHashMap<>();
        for (SelectorMode mode : values()) {
            byName.put(mode.jsonName, mode);
        }
        BY_NAME = Collections.unmodifiableMap(byName);
    }

    /** 供错误信息用的键名清单，顺序稳定。 */
    public static String keyList() {
        return String.join("/", BY_NAME.keySet());
    }

    private final String jsonName;

    SelectorMode(String jsonName) {
        this.jsonName = jsonName;
    }

    public String jsonName() {
        return jsonName;
    }

    /** 只有 {@code between} 需要第二个锚点。 */
    public boolean isBetween() {
        return this == BETWEEN;
    }
}
