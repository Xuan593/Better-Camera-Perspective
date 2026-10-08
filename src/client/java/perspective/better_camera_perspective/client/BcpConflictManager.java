package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perspective.better_camera_perspective.client.ui.BcpConflictOverlayPanel;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v1.3.1：通用「冲突功能」管理。
 *
 * 同一功能若由多个 mod 提供（如剪辑可见性筛选由本插件与 BBS Cubed 同时提供），
 * 需要用户逐一选择由哪个 mod 的模式运行：
 *  - 自动检测所有已注册功能及其来源 mod；
 *  - 未解决的冲突 → 该功能封锁（本插件不运行），并在进入编辑器时弹出
 *    「冲突功能」选择窗口（与设置页同一布局：逐功能 + 来源按钮 + 完成应用）；
 *  - BBS 设置新增「冲突功能」设置页，同样逐功能列出来源并允许选择；
 *  - 选择即时持久化（config 的 conflicts 映射），后续会话按选择恢复。
 */
@Environment(EnvType.CLIENT)
public class BcpConflictManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");

    /** 本插件 mod id */
    public static final String BCP_MOD_ID = "better_camera_perspective";
    /** BBS Cubed mod id */
    public static final String CUBED_MOD_ID = "bbsplusplus";

    /** 剪辑可见性筛选功能 */
    public static final String FEATURE_CLIPS_VISIBILITY = "clips_visibility";

    private static final File CONFIG = new File(BBSMod.getSettingsFolder(), "camera_perspective_clips.json");

    /** 已注册冲突功能：id → 功能名（语言键） */
    private static final Map<String, IKey> FEATURES = new LinkedHashMap<>();
    /** 已注册冲突功能：id → 设置页 ValueString（读写选择的通道） */
    private static final Map<String, ValueString> CHOICES = new LinkedHashMap<>();
    /** 各功能检测到的来源 mod id（bcp 恒在 + 其他已加载 mod） */
    private static final Map<String, List<String>> SOURCES = new LinkedHashMap<>();
    /** 各功能已持久化的选择（config 的 conflicts 映射） */
    private static final Map<String, String> CHOSEN = new LinkedHashMap<>();
    /** 本会话是否已做过冲突检测 */
    private static boolean checked = false;

    static {
        FEATURES.put(FEATURE_CLIPS_VISIBILITY, L10n.lang("bbs.ui.camera.conflict.clips_visibility"));
        loadConfig();
    }

    // ---------------------------------------------------------------
    // 注册与查询
    // ---------------------------------------------------------------

    /** 设置模块注册时绑定某功能的 ValueString（设置页读写通道） */
    public static void bindChoice(String featureId, ValueString value) {
        CHOICES.put(featureId, value);
    }

    public static ValueString getChoiceValue(String featureId) {
        return CHOICES.get(featureId);
    }

    public static List<String> getFeatureIds() {
        return new ArrayList<>(FEATURES.keySet());
    }

    public static IKey getFeatureLabel(String featureId) {
        return FEATURES.get(featureId);
    }

    /**
     * 检测某功能的所有来源 mod（本插件恒提供，另加其他已加载的 mod）。
     * 动态注册表：目前注册了剪辑可见性筛选（本插件 vs BBS Cubed）。
     */
    public static List<String> getSources(String featureId) {
        List<String> sources = SOURCES.get(featureId);
        if (sources == null) {
            sources = new ArrayList<>();
            sources.add(BCP_MOD_ID);
            if (FabricLoader.getInstance().isModLoaded(CUBED_MOD_ID)) {
                sources.add(CUBED_MOD_ID);
            }
            SOURCES.put(featureId, sources);
        }
        return sources;
    }

    /** 某功能是否已被解决（来源唯一，或已有有效选择） */
    public static boolean isResolved(String featureId) {
        List<String> sources = getSources(featureId);
        if (sources.size() <= 1) {
            return true;
        }
        String chosen = getChosen(featureId);
        return chosen != null && sources.contains(chosen);
    }

    /** 某功能是否封锁：存在多来源且尚未选择 → 本插件该功能不运行 */
    public static boolean isBlocked(String featureId) {
        return !isResolved(featureId);
    }

    public static boolean isBlocked() {
        for (String featureId : FEATURES.keySet()) {
            if (isBlocked(featureId)) {
                return true;
            }
        }
        return false;
    }

    /** 已持久化的选择（config 优先；其次设置页 ValueString 当前值） */
    public static String getChosen(String featureId) {
        String chosen = CHOSEN.get(featureId);
        if (chosen != null) {
            return chosen;
        }
        ValueString value = CHOICES.get(featureId);
        if (value != null && !value.get().isEmpty()) {
            return value.get();
        }
        return null;
    }

    /** 选择某功能的来源 mod：即时持久化并生效（解除该功能封锁） */
    public static void setChosen(String featureId, String modId) {
        if (modId == null || modId.isEmpty()) {
            return;
        }
        CHOSEN.put(featureId, modId);
        ValueString value = CHOICES.get(featureId);
        if (value != null && !modId.equals(value.get())) {
            value.set(modId);
        }
        saveConfig();
        LOGGER.info("[BCP] 冲突功能「{}」选择来源 {} 并生效", featureId, modId);
    }

    /** 应用设置页当前的全部选择（逐个生效） */
    public static void applyAll() {
        for (String featureId : CHOICES.keySet()) {
            ValueString value = CHOICES.get(featureId);
            if (value != null && !value.get().isEmpty()) {
                setChosen(featureId, value.get());
            }
        }
    }

    /** 全部冲突功能是否都已解决（供弹窗/设置页判定） */
    public static List<String> getConflictedFeatures() {
        List<String> conflicted = new ArrayList<>();
        for (String featureId : FEATURES.keySet()) {
            if (isBlocked(featureId)) {
                conflicted.add(featureId);
            }
        }
        return conflicted;
    }

    /** mod id → 显示名 */
    public static String displayName(String modId) {
        if (BCP_MOD_ID.equals(modId)) {
            return "Better camera perspective";
        }
        if (CUBED_MOD_ID.equals(modId)) {
            return "BBS Cubed";
        }
        return FabricLoader.getInstance()
            .getModContainer(modId)
            .map(c -> c.getMetadata().getName())
            .orElse(modId);
    }

    // ---------------------------------------------------------------
    // 冲突检测与弹窗（进入编辑器时调用）
    // ---------------------------------------------------------------

    /**
     * 冲突检测（本会话只弹一次窗）：
     *  - 无冲突或全部已解决 → 不弹窗；
     *  - 存在未解决冲突 → 弹「冲突功能」选择窗口（逐功能 + 来源按钮 + 完成应用）。
     */
    public static void checkAndShow(UIContext context) {
        if (checked) {
            return;
        }
        checked = true;

        List<String> conflicted = getConflictedFeatures();
        if (conflicted.isEmpty()) {
            LOGGER.info("[BCP] 未检测到未解决的冲突功能，全部功能直接启用");
            return;
        }

        StringBuilder names = new StringBuilder();
        for (String featureId : conflicted) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(featureId);
        }
        LOGGER.info("[BCP] 检测到冲突功能（未解决）：{}，弹出选择窗口", names);

        if (context != null) {
            UIOverlay.addOverlay(context, new BcpConflictOverlayPanel(), 500, 380);
        }
    }

    // ---------------------------------------------------------------
    // 配置读写（conflicts 映射，兼容旧 conflict 字段）
    // ---------------------------------------------------------------

    private static void saveConfig() {
        try {
            MapType map = new MapType(true);

            // 保留可见性筛选数据（由 BcpVisibility 维护，此处回写）
            BcpVisibility.storeInto(map);
            persistInto(map);

            if (!DataToString.writeSilently(CONFIG, map)) {
                LOGGER.warn("[BCP] 冲突功能配置写入失败：{}", CONFIG);
            }
        } catch (Exception e) {
            LOGGER.error("[BCP] 冲突功能配置写入异常", e);
        }
    }

    /** 由 BcpVisibility 在保存筛选时调用：把 conflicts 组装进共用 map 并写盘 */
    public static void persistInto(MapType map) {
        MapType conflicts = new MapType(true);
        for (Map.Entry<String, String> e : CHOSEN.entrySet()) {
            MapType feature = new MapType(true);
            feature.putBool("resolved", true);
            feature.putString("chosen", e.getValue());
            conflicts.put(e.getKey(), feature);
        }
        map.put("conflicts", conflicts);

        if (!DataToString.writeSilently(CONFIG, map)) {
            LOGGER.warn("[BCP] 冲突功能配置写入失败：{}", CONFIG);
        }
    }

    private static void loadConfig() {
        try {
            if (!CONFIG.exists()) {
                return;
            }
            BaseType base = DataToString.read(CONFIG);
            if (!(base instanceof MapType)) {
                return;
            }
            MapType map = (MapType) base;

            // 可见性筛选数据由 BcpVisibility 加载
            BcpVisibility.loadFrom(map);

            // 新结构：conflicts.<featureId>.chosen
            MapType conflicts = map.get("conflicts") instanceof MapType ? (MapType) map.get("conflicts") : null;
            if (conflicts != null) {
                for (String featureId : FEATURES.keySet()) {
                    MapType feature = conflicts.get(featureId) instanceof MapType ? (MapType) conflicts.get(featureId) : null;
                    if (feature != null && feature.getBool("resolved", false)) {
                        String mode = feature.getString("chosen", "");
                        if (!mode.isEmpty()) {
                            CHOSEN.put(featureId, mode);
                        }
                    }
                }
            }

            // 兼容旧结构：conflict.{resolved,chosen}（v1.2.1~v1.3 的全局二选一）→ 迁移到 clips_visibility
            MapType conflict = map.get("conflict") instanceof MapType ? (MapType) map.get("conflict") : null;
            if (conflict != null && conflict.getBool("resolved", false)) {
                String mode = conflict.getString("chosen", "");
                if (!mode.isEmpty() && !CHOSEN.containsKey(FEATURE_CLIPS_VISIBILITY)) {
                    String migrated = "cubed".equals(mode) ? CUBED_MOD_ID : BCP_MOD_ID;
                    CHOSEN.put(FEATURE_CLIPS_VISIBILITY, migrated);
                    LOGGER.info("[BCP] 迁移旧冲突选择 {} → {}（功能 clips_visibility）", mode, migrated);
                }
            }

            LOGGER.info("[BCP] 冲突功能配置加载成功：{}", CHOSEN);
        } catch (Exception e) {
            LOGGER.error("[BCP] 冲突功能配置加载异常", e);
        }
    }
}
