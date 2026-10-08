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

    /** 进入摄像机编辑器：按用户要求恢复全开（避免缺插件等异常导致崩溃），不写盘 */
    public static void onEnterEditor() {
        ensureKeys();
        for (Map.Entry<String, Boolean> e : runtime.entrySet()) {
            e.setValue(true);
        }
        LOGGER.debug("[BCP] 进入编辑器，剪辑可见性已恢复全开");
    }

    /** 打开「可视性设置」面板：先应用用户保存的筛选 */
    public static void openVisibilityPanel(UIContext context) {
        if (blocked || context == null) {
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
     *  - 仅本插件提供 → 直接解除封锁；
     *  - 存在 BBS Cubed → 已选过按保存的选择恢复；未选过弹窗询问。
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
            // 实验版：无论保存的选择是哪个，本插件功能均保持激活
            blocked = false;
            LOGGER.info("[BCP] 实验版：按已保存选择 {} 恢复（模拟），本插件功能保持激活", chosen);
            return;
        }

        if (context != null) {
            UIOverlay.addOverlay(context, new BcpConflictOverlayPanel(choice -> {
                // 实验版（v1.2.1-Alpha）：弹窗仅为模拟与验证交互；
                // 无论选择「BBS Cubed」还是「Better camera perspective」，都激活本插件功能。
                // 选择仅本会话有效，不持久化 —— 重启后再次进入 BBS 会重新弹出。
                chosen = choice;
                blocked = false;
                LOGGER.info("[BCP] 实验版：弹窗选择 {}（模拟），本插件功能已激活（封锁={}）", choice, blocked);
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

            // 实验版（v1.2.1-Alpha）：冲突选择不持久化，重启后继续弹出模拟弹窗以便验证
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

            // 实验版：不再恢复已保存的冲突选择（chosen 保持 null），
            // 每次新会话进入 BBS 编辑器都会重新弹出模拟弹窗，便于验证交互。
            LOGGER.info("[BCP] 剪辑可见性配置加载成功：{} 条筛选（实验版：冲突选择不持久化）", saved.size());
        } catch (Exception e) {
            LOGGER.error("[BCP] 剪辑可见性配置加载异常", e);
        }
    }
}
