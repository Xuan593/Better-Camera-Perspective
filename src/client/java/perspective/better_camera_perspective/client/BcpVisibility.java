package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.MapFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perspective.better_camera_perspective.client.ui.BcpConflictOverlayPanel;
import perspective.better_camera_perspective.client.ui.BcpVisibilityOverlayPanel;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * v1.2.1：剪辑可见性筛选（菜单内「可视性设置」+ 冲突预防）。
 *
 * 取代 v1.2 的设置页「camera perspective」：
 *  - 入口改为摄像机编辑器右键添加剪辑菜单中的「可视性设置」项（静止上方）；
 *  - 每次进入摄像机编辑器自动恢复全开（防因缺少本插件/异常状态导致崩溃）；
 *  - 打开「可视性设置」面板时应用用户先前保存的筛选；
 *  - 用户调整即时生效并持久化（独立 json，不依赖 BBS 设置系统）。
 *
 * 冲突预防（与 BBS Cubed 的剪辑可见性筛选合并冲突）：
 *  - 功能默认封锁；打开编辑器/构建菜单时做一次来源检测；
 *  - 仅本插件提供该功能 → 直接解除封锁；
 *  - 检测到 BBS Cubed（bbsplusplus）也提供该功能 → 弹出询问窗口，
 *    用户选择倾向的模组模式并点击确定后，恢复被选中一方的功能；
 *  - 选择持久化，后续会话按保存的选择恢复对应模式。
 */
@Environment(EnvType.CLIENT)
public class BcpVisibility {
    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");

    /** BBS Cubed（bbsplusplus）mod id */
    private static final String CUBED_MOD_ID = "bbsplusplus";
    /** 本插件 mod id */
    private static final String BCP_MOD_ID = "better_camera_perspective";
    /** 本插件模式标识（写入配置） */
    private static final String MODE_BCP = "bcp";
    /** Cubed 模式标识（写入配置） */
    private static final String MODE_CUBED = "cubed";

    private static final File CONFIG = new File(BBSMod.getSettingsFolder(), "camera_perspective_clips.json");

    /** 当前生效的可见性（进入编辑器时恢复全开） */
    private static final Map<String, Boolean> runtime = new HashMap<>();
    /** 用户保存的筛选（磁盘持久化） */
    private static final Map<String, Boolean> saved = new HashMap<>();

    /** 功能封锁：冲突未解决或用户选择了其他模组模式时，本插件筛选功能不运行 */
    private static boolean blocked = true;
    /** 本会话是否已做过冲突检测 */
    private static boolean conflictChecked = false;
    /** 用户选择的模式："bcp" / "cubed" / null（未选择） */
    private static String chosen = null;

    static {
        loadConfig();
    }

    // ---------------------------------------------------------------
    // 查询
    // ---------------------------------------------------------------

    /**
     * 剪辑是否在添加菜单中可见。
     * 封锁期间一律可见（本插件筛选不运行）。
     */
    public static boolean isClipVisible(Link type) {
        if (blocked) {
            return true;
        }
        Boolean v = runtime.get(type.toString());
        return v == null || v;
    }

    /** 面板行当前值（不受封锁影响，仅供「可视性设置」面板显示） */
    public static boolean getVisible(Link type) {
        Boolean v = runtime.get(type.toString());
        return v == null || v;
    }

    public static boolean isBlocked() {
        return blocked;
    }

    // ---------------------------------------------------------------
    // 生命周期
    // ---------------------------------------------------------------

    /** 进入摄像机编辑器：应用用户保存的筛选（筛选立即生效，避免"筛选被吞"）；无保存时全部可见 */
    public static void onEnterEditor() {
        ensureKeys();
        applySaved();
        LOGGER.debug("[BCP] 进入编辑器，已应用保存的剪辑可见性筛选");
    }

    /** 打开「可视性设置」面板：入口始终可用（菜单恒定 1 号位），先应用用户保存的筛选 */
    public static void openVisibilityPanel(UIContext context) {
        if (context == null) {
            return;
        }
        applySaved();
        UIOverlay.addOverlay(context, new BcpVisibilityOverlayPanel(), 460, 440);
    }

    /** 用户调整单项可见性：即时生效并写盘 */
    public static void setVisible(Link type, boolean visible) {
        runtime.put(type.toString(), visible);
        saved.put(type.toString(), visible);
        saveConfig();
    }

    // ---------------------------------------------------------------
    // 冲突检测（与 BBS Cubed 同功能）
    // ---------------------------------------------------------------

    /**
     * 检测剪辑可见性筛选功能的功能来源（本会话只检测一次）：
     *  - 仅本插件提供 → 直接解除封锁（不弹窗）；
     *  - 存在 BBS Cubed（bbsplusplus）同功能 → 已选过按保存的选择恢复；
     *    未选过 → 封锁 + 弹窗询问，用户选择后按所选模组模式运行并持久化。
     */
    public static void checkConflict(UIContext context) {
        if (conflictChecked) {
            return;
        }
        conflictChecked = true;

        boolean hasCubed = FabricLoader.getInstance().isModLoaded(CUBED_MOD_ID);
        if (!hasCubed) {
            blocked = false;
            LOGGER.info("[BCP] 未检测到其他剪辑可见性筛选来源，功能直接启用");
            return;
        }

        String cubedVersion = FabricLoader.getInstance()
            .getModContainer(CUBED_MOD_ID)
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("?");
        LOGGER.info("[BCP] 检测到 {}（{} {}）提供相同功能，剪辑可见性筛选已封锁", CUBED_MOD_ID, CUBED_MOD_ID, cubedVersion);

        if (chosen != null) {
            // 按已保存的选择恢复对应模组模式：选 bcp → 本插件激活；选 cubed → 本插件保持封锁
            blocked = !MODE_BCP.equals(chosen);
            LOGGER.info("[BCP] 按已保存选择 {} 恢复：本插件功能{}", chosen, blocked ? "保持封锁" : "已激活");
            return;
        }

        // 未选过且存在多来源冲突：封锁 + 弹窗询问
        blocked = true;
        if (context != null) {
            UIOverlay.addOverlay(context, new BcpConflictOverlayPanel(choice -> {
                chosen = choice;
                blocked = !MODE_BCP.equals(choice);
                saveConfig();
                LOGGER.info("[BCP] 弹窗选择 {}：本插件功能{}", choice, blocked ? "保持封锁" : "已激活");
            }), 460, 300);
        }
    }

    // ---------------------------------------------------------------
    // 配置读写（独立 json：camera_perspective_clips.json）
    // ---------------------------------------------------------------

    /** 用工厂当前全部剪辑键填满 runtime（默认可见），保证菜单/面板遍历稳定 */
    private static void ensureKeys() {
        MapFactory<Clip, ClipFactoryData> factory = BBSMod.getFactoryCameraClips();
        if (factory == null) {
            return;
        }
        for (Link key : factory.getKeys()) {
            runtime.putIfAbsent(key.toString(), true);
        }
    }

    private static void applySaved() {
        ensureKeys();
        runtime.clear();
        for (String key : saved.keySet()) {
            runtime.put(key, saved.get(key));
        }
        for (Map.Entry<String, Boolean> e : runtime.entrySet()) {
            if (!saved.containsKey(e.getKey())) {
                e.setValue(true);
            }
        }
    }

    private static void saveConfig() {
        try {
            MapType map = new MapType(true);
            MapType visibility = new MapType(true);
            for (Map.Entry<String, Boolean> e : saved.entrySet()) {
                visibility.putBool(e.getKey(), e.getValue());
            }
            map.put("visibility", visibility);

            MapType conflict = new MapType(true);
            conflict.putBool("resolved", chosen != null);
            if (chosen != null) {
                conflict.putString("chosen", chosen);
            }
            map.put("conflict", conflict);

            if (!DataToString.writeSilently(CONFIG, map)) {
                LOGGER.warn("[BCP] 剪辑可见性配置写入失败：{}", CONFIG);
            }
        } catch (Exception e) {
            LOGGER.error("[BCP] 剪辑可见性配置写入异常", e);
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

            MapType visibility = map.get("visibility") instanceof MapType ? (MapType) map.get("visibility") : null;
            if (visibility != null) {
                saved.clear();
                for (String key : visibility.keys()) {
                    saved.put(key, visibility.getBool(key, true));
                }
            }

            MapType conflict = map.get("conflict") instanceof MapType ? (MapType) map.get("conflict") : null;
            if (conflict != null && conflict.getBool("resolved", false)) {
                String mode = conflict.getString("chosen", "");
                chosen = MODE_BCP.equals(mode) ? MODE_BCP : (MODE_CUBED.equals(mode) ? MODE_CUBED : null);
            }
            LOGGER.info("[BCP] 剪辑可见性配置加载成功：{} 条筛选，冲突选择={}", saved.size(), chosen);
        } catch (Exception e) {
            LOGGER.error("[BCP] 剪辑可见性配置加载异常", e);
        }
    }
}
