package io.github.thatone0502.keepadvancing.api;

import java.util.List;
import net.minecraft.resources.Identifier;

/**
 * {@code net.minecraft.advancements.Advancement} 的 Duck 接口（rev14）。
 *
 * <p>由 {@code AdvancementMixin} 实现。{@code Advancement} 是 record，构造后无法 setter，
 * 所以 multi_parents 只能在重建后的 CODEC 的 apply lambda 里、或协议 read 的 RETURN 处写入。
 */
public interface KaAdvancement {

    /** 额外上游 id 列表；默认空列表（不可变）。 */
    List<Identifier> ka$multiParents();

    void ka$setMultiParents(List<Identifier> ids);
}
