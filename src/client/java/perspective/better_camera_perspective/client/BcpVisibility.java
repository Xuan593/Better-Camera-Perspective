package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.MapFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perspective.better_camera_perspective.client.ui.BcpVisibilityOverlayPanel;

import java.util.HashMap;
import java.util.Map;

/**
 * v1.3.1：剪辑可见性筛选（菜单内「可视性设置」）。
 *
 *  - 入口为摄像机编辑器右键添加剪辑菜单中的「可视性设置」项（恒定 1 号位）；
 *  - 每次进入摄像机编辑器自动恢复全开（防因缺少本插件/异常状态导致崩溃）；
 *  - 打开「可视性设置」面板时应用用户先前保存的筛选；用户调整即时生效并持久化；
 *  - 与 BBS Cubed 的同类功能冲突由 {@link BcpConflictManager} 统一管理
 *    （通用「冲突功能」设置页 + 逐功能选择窗口），本类只负责查询封锁状态。
 */
@Environment(EnvType.CLIENT)
public class BcpVisibility {
    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");

    /** 当前生效的可见性（进入编辑器时恢复全开） */
    private static final Map<String, Boolean> runtime = new HashMap<>();
    /** 用户保存的筛选（磁盘持久化） */
    private static final Map<String, Boolean> saved = new HashMap<>();

    // ---------------------------------------------------------------
    // 查询
    // ---------------------------------------------------------------

    /**
     * 剪辑是否在添加菜单中可见。
     * 功能封锁（冲突未解决或用户选择了其他模组模式）期间一律可见（本插件筛选不运行）。
     */
    public static boolean isClipVisible(Link type) {
        if (BcpConflictManager.isBlocked(BcpConflictManager.FEATURE_CLIPS_VISIBILITY)) {
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
        return BcpConflictManager.isBlocked(BcpConflictManager.FEATURE_CLIPS_VISIBILITY);
    }

    // ---------------------------------------------------------------
    // 生命周期
    // ---------------------------------------------------------------

    /** 进入摄像机编辑器：应用用户保存的筛选；并做一次冲突检测（未解决时弹窗） */
    public static void onEnterEditor(UIContext context) {
        ensureKeys();
        applySaved();
        BcpConflictManager.checkAndShow(context);
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
    // 配置读写（独立 json：camera_perspective_clips.json，visibility 部分）
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

    /** 由冲突管理器回写：把可见性筛选数据存入共用 config（不写盘，仅组装） */
    public static void storeInto(MapType map) {
        MapType visibility = new MapType(true);
        for (Map.Entry<String, Boolean> e : saved.entrySet()) {
            visibility.putBool(e.getKey(), e.getValue());
        }
        map.put("visibility", visibility);
    }

    /** 从共用 config 读可见性部分（由 BcpConflictManager 在加载后回调） */
    public static void loadFrom(MapType map) {
        try {
            MapType visibility = map.get("visibility") instanceof MapType ? (MapType) map.get("visibility") : null;
            if (visibility != null) {
                saved.clear();
                for (String key : visibility.keys()) {
                    saved.put(key, visibility.getBool(key, true));
                }
            }
            LOGGER.info("[BCP] 剪辑可见性筛选加载成功：{} 条", saved.size());
        } catch (Exception e) {
            LOGGER.error("[BCP] 剪辑可见性筛选加载异常", e);
        }
    }

    private static void saveConfig() {
        try {
            MapType map = new MapType(true);
            storeInto(map);
            // 冲突部分由 BcpConflictManager 统一写盘
            BcpConflictManager.persistInto(map);
        } catch (Exception e) {
            LOGGER.error("[BCP] 剪辑可见性配置写入异常", e);
        }
    }
}
