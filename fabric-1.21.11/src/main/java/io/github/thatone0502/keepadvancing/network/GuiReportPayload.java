package io.github.thatone0502.keepadvancing.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：报告"这一刻客户端正开着哪个界面"。
 *
 * <p>两个字段都允许是空串，空串表示"没有"。之所以要两个字段而不是一个：{@code screen} 装的是
 * 自动采集的全限定类名，{@code custom} 装的是别的模组通过
 * {@code KeepAdvancingGuiApi.reportCustomGui(Identifier)} 主动上报的 id。两者可以同时存在
 * ——比如玩家开着工作台，而某个模组的 HUD 覆盖层也正贴着屏幕画。
 *
 * <p>只在状态变化时发送，不是每 tick 都发。数据是客户端自报的，服务端只把它当字符串去匹配，
 * 不做任何信任性使用。
 */
public record GuiReportPayload(String screenClass, String customId) implements CustomPacketPayload {

    /**
     * 通道 id。
     *
     * <p><b>不要用 {@code CustomPacketPayload.createType(String)}</b>：它内部走的是
     * {@code Identifier.withDefaultNamespace(path)}，会把参数当成<b>路径</b>再拼上
     * {@code minecraft:} 前缀。传 {@code "keepadvancing:gui_report"} 会得到
     * {@code minecraft:keepadvancing:gui_report}，直接抛
     * {@code IdentifierException: Non [a-z0-9/._-] character in path}，
     * 而且是在类初始化时炸，整个服务端起不来。要自己的命名空间就手搓 {@code Type}。
     */
    public static final CustomPacketPayload.Type<GuiReportPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("keepadvancing", "gui_report"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GuiReportPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, GuiReportPayload::screenClass,
            ByteBufCodecs.STRING_UTF8, GuiReportPayload::customId,
            GuiReportPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
