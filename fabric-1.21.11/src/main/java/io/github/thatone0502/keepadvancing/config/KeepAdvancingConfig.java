package io.github.thatone0502.keepadvancing.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.thatone0502.keepadvancing.KeepAdvancing;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 本模组唯一的配置文件：{@code config/keepadvancing.json}。
 *
 * <p>存在的理由只有一个：让本模组可以被当成<b>纯前置库</b>使用。别的创作者若只想用
 * {@code keepadvancing} 命名空间下的五个触发器与公开 API {@code KeepAdvancingGuiApi}，
 * 而不想要我们自己的进度、奖杯方块，把 {@code load_builtin_content} 设成 {@code false}
 * 即可 —— 那时本模组不向游戏注册任何一件「内容」。
 *
 * <p><b>不</b>受这个开关影响的东西（这是「当库用」的前提）：
 * <ul>
 *   <li>五个触发器（{@code timer_test} / {@code mod_loaded} / {@code advancements_made}
 *       / {@code gui_open} / {@code structure_present}）—— 永远注册；</li>
 *   <li>公开 API {@code KeepAdvancingGuiApi} —— 永远可用。</li>
 * </ul>
 *
 * <p>读取时机很关键：资源条件（见 {@code ConfigResourceCondition}）是在<b>数据包重载</b>时
 * 求值的，所以 {@link #load()} 必须由 {@code onInitialize()} 在任何数据包解析之前调用。
 * 也正因为如此，改完这个文件后需要 {@code /reload}（服务端）或重启才会生效。
 */
public final class KeepAdvancingConfig {

    /** 配置文件在 {@code config/} 下的文件名。 */
    public static final String FILE_NAME = "keepadvancing.json";

    /** 内置内容总开关的字段名。 */
    public static final String KEY_LOAD_BUILT_IN_CONTENT = "load_builtin_content";

    /** 缺文件、缺字段、类型不对时都退回这个值：开箱即用，与加开关之前的行为完全一致。 */
    private static final boolean DEFAULT_LOAD_BUILT_IN_CONTENT = true;

    // disableHtmlEscaping：Gson 默认会把 ' = " & < > 转成 Unicode 转义序列，_readme 那句人话会被转得没法读。
    // （注意：这里绝不能出现反斜杠加 u 的字面写法——Java 在注释里也处理 Unicode 转义，会直接编译失败。）
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static volatile boolean loadBuiltInContent = DEFAULT_LOAD_BUILT_IN_CONTENT;
    private static volatile boolean loaded;

    private KeepAdvancingConfig() {
    }

    /** 是否加载本模组自带的内容（进度、奖杯方块）。默认为 {@code true}。 */
    public static boolean loadBuiltInContent() {
        return loadBuiltInContent;
    }

    /**
     * 读一次配置。幂等，重复调用只有第一次生效。
     *
     * <p>文件不存在时会写出一份带说明的默认文件 —— 这样用户不用去翻文档就知道有哪些选项。
     */
    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;

        final Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        final JsonObject root = readIfPresent(path);

        if (root == null) {
            writeDefault(path);
            KeepAdvancing.LOGGER.info(
                    "Wrote a default config to {} (built-in content: ENABLED).", path);
            return;
        }

        final JsonElement value = root.get(KEY_LOAD_BUILT_IN_CONTENT);

        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            KeepAdvancing.LOGGER.warn(
                    "Config {} has no boolean \"{}\"; keeping the default ({}).",
                    path, KEY_LOAD_BUILT_IN_CONTENT, DEFAULT_LOAD_BUILT_IN_CONTENT);
            return;
        }

        loadBuiltInContent = value.getAsBoolean();
        KeepAdvancing.LOGGER.info(
                "Built-in content (advancements + TOMC Trophy block): {}.",
                loadBuiltInContent ? "ENABLED" : "DISABLED");
    }

    /** 读并解析；文件不存在、读不动或不是 JSON 对象都返回 {@code null}。 */
    private static JsonObject readIfPresent(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            final JsonElement root = JsonParser.parseReader(reader);
            if (root != null && root.isJsonObject()) {
                return root.getAsJsonObject();
            }
            KeepAdvancing.LOGGER.warn("Config {} is not a JSON object; keeping the defaults.", path);
        } catch (Exception e) {
            // 坏掉的配置文件绝不能让游戏起不来 —— 退回默认值，并把原因说清楚。
            KeepAdvancing.LOGGER.warn("Failed to read config {}; keeping the defaults.", path, e);
        }
        return null;
    }

    /** 写出默认配置。写不进去也只是少一份便利，不影响游戏。 */
    private static void writeDefault(Path path) {
        final JsonObject root = new JsonObject();
        root.addProperty("_readme",
                "Keep Advancing config. load_builtin_content=true loads this mod's own advancements "
                        + "(the TOMC tab and the 22 vanilla-line advancements) and the TOMC Trophy block. "
                        + "Set it to false to load nothing but the five triggers and the public GUI API, "
                        + "so this mod can be used as a pure library. "
                        + "Changing it needs a datapack reload (/reload) or a restart.");
        root.addProperty(KEY_LOAD_BUILT_IN_CONTENT, DEFAULT_LOAD_BUILT_IN_CONTENT);

        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            KeepAdvancing.LOGGER.warn("Failed to write the default config to {}.", path, e);
        }
    }
}
