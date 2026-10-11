package io.github.thatone0502.keepadvancing.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import io.github.thatone0502.keepadvancing.KeepAdvancing;
import io.github.thatone0502.keepadvancing.trigger.matcher.Matcher;

/**
 * 已加载模组的 Java 包名清单。
 *
 * <p>Fabric 的模组元数据里没有包名信息，所以只能打开模组根路径（开发目录或 jar）枚举
 * {@code .class} 条目，把目录部分还原成包名。扫描结果按模组缓存，整场游戏只做一次。
 */
public final class ModScan {

    private static volatile Snapshot cached;

    private ModScan() {
    }

    /** 已扫描结果的快照；首次调用时做一次全量扫描。 */
    public static Snapshot snapshot() {
        Snapshot local = cached;
        if (local == null) {
            synchronized (ModScan.class) {
                local = cached;
                if (local == null) {
                    local = scan();
                    cached = local;
                }
            }
        }
        return local;
    }

    /** 丢弃缓存，下次判定重新扫描（服务器启动时调用一次）。 */
    public static void invalidate() {
        cached = null;
    }

    /**
     * 在后台线程预热缓存。
     *
     * <p>实测 43 个模组要 221 ms，整合包会更久。登录事件是同步的，直接在里面扫描会给玩家
     * 加一段可感知的卡顿，所以这里提前在服务器启动后异步做掉。线程是 daemon，不会拖住关闭。
     */
    public static void warmUpAsync() {
        Thread thread = new Thread(ModScan::snapshot, "keepadvancing-modscan");
        thread.setDaemon(true);
        thread.start();
    }

    private static Snapshot scan() {
        long started = System.nanoTime();
        Map<String, Set<String>> packagesByMod = new LinkedHashMap<>();
        try {
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                Set<String> packages = packages(mod);
                if (!packages.isEmpty()) {
                    packagesByMod.put(mod.getMetadata().getId(), packages);
                }
            }
        } catch (Throwable t) {
            KeepAdvancing.LOGGER.warn("[keepadvancing] cannot enumerate loaded mods", t);
        }
        KeepAdvancing.LOGGER.info(
                "[keepadvancing] scanned {} mods' Java packages in {} ms",
                packagesByMod.size(), (System.nanoTime() - started) / 1_000_000L);
        return new Snapshot(packagesByMod);
    }

    private static Set<String> packages(ModContainer mod) {
        Set<String> found = new HashSet<>();
        for (Path root : rootPaths(mod)) {
            try {
                if (Files.isDirectory(root)) {
                    try (Stream<Path> walk = Files.walk(root)) {
                        walk.filter(Files::isRegularFile)
                                .forEach(path -> addPackage(root.relativize(path).toString(), found));
                    }
                } else if (Files.isRegularFile(root)) {
                    try (ZipFile zip = new ZipFile(root.toFile())) {
                        zip.stream().map(ZipEntry::getName).forEach(name -> addPackage(name, found));
                    }
                }
            } catch (Exception e) {
                // 内建模组（minecraft / java）与部分嵌套挂载点没有可枚举的磁盘路径，属正常情况。
                KeepAdvancing.LOGGER.debug("[keepadvancing] skipped mod path {}: {}", root, e.toString());
            }
        }
        return found;
    }

    private static void addPackage(String path, Set<String> out) {
        String normalized = path.replace('\\', '/');
        if (!normalized.endsWith(".class")) {
            return;
        }
        int slash = normalized.lastIndexOf('/');
        if (slash <= 0) {
            return; // 默认包里的类没有包名，不参与匹配。
        }
        out.add(normalized.substring(0, slash).replace('/', '.'));
    }

    private static Set<Path> rootPaths(ModContainer mod) {
        try {
            return new HashSet<>(mod.getRootPaths());
        } catch (Throwable t) {
            return Set.of();
        }
    }

    /** 扫描结果：模组 id → 该模组拥有的 Java 包名集合。 */
    public static final class Snapshot {

        private final Map<String, Set<String>> packagesByMod;

        Snapshot(Map<String, Set<String>> packagesByMod) {
            this.packagesByMod = Map.copyOf(packagesByMod);
        }

        public boolean isEmpty() {
            return packagesByMod.isEmpty();
        }

        public Set<String> modIds() {
            return packagesByMod.keySet();
        }

        /**
         * @param matcher     {@code mod_loaded} 的 {@code package} 字段：字符串＝包名精确相等，
         *                    {@code {"regex": ...}}＝正则（{@code find()} 子串语义）
         * @param excludeSelf 是否排除本模组
         * @return 命中的模组 id，未命中返回 null
         */
        public String match(Matcher matcher, boolean excludeSelf) {
            for (Map.Entry<String, Set<String>> entry : packagesByMod.entrySet()) {
                if (excludeSelf && KeepAdvancing.MOD_ID.equals(entry.getKey())) {
                    continue;
                }
                for (String packageName : entry.getValue()) {
                    if (matcher.matches(packageName)) {
                        return entry.getKey();
                    }
                }
            }
            return null;
        }
    }
}
