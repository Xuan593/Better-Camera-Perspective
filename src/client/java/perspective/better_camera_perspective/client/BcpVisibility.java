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
import mchorse.bbs_mod.utils.factory.IFactory;
import mchorse.bbs_mod.utils.factory.MapFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import perspective.better_camera_perspective.client.ui.BcpVisibilityOverlayPanel;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 剪辑添加菜单的可见性筛选，持久化到 config/camera_perspective_clips.json。
 *
 * <p>V1.7.20-Alpha：按「剪辑工厂」分池。BBS 里摄像机编辑器与 POV 动作编辑器各有独立的剪辑工厂，
 * 原来只按 Link 字符串建键，导致两边共用同一池（在 FOV / POV 动作编辑器里打开「可视性设置」，
 * 看到的仍是摄像机剪辑列表，且关闭某项会连带影响另一个编辑器）。</p>
 *
 * <p>键口径：</p>
 * <ul>
 *   <li>摄像机剪辑工厂（{@code BBSMod.getFactoryCameraClips()}）→ 裸 Link 字符串，兼容旧配置；</li>
 *   <li>其他工厂 → {@code <工厂类型键>#<Link>}，与旧配置互不干扰。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class BcpVisibility {
    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");
    private static final File CONFIG = new File(BBSMod.getSettingsFolder(), "camera_perspective_clips.json");

    /** 摄像机剪辑池的固定标识（裸键，兼容旧配置文件）。 */
    public static final String POOL_CAMERA = "camera";

    private static final Map<String, Boolean> runtime = new HashMap<>();
    private static final Map<String, Boolean> saved = new HashMap<>();

    /** 当前打开面板的工厂（面板重建时使用） */
    private static IFactory<Clip, ClipFactoryData> activeFactory;

    static {
        loadConfig();
    }

    /** 工厂所属池：摄像机工厂 → "camera"，其余用工厂类型键。 */
    public static String poolOf(IFactory<Clip, ClipFactoryData> factory) {
        if (factory == null) {
            return POOL_CAMERA;
        }
        if (factory == BBSMod.getFactoryCameraClips()) {
            return POOL_CAMERA;
        }
        String typeKey = factory.getTypeKey();
        if (typeKey == null || typeKey.isEmpty() || POOL_CAMERA.equals(typeKey)) {
            // 类型键缺失或与摄像机池同名时，退回工厂类名，保证两池不会意外合并
            typeKey = "factory_" + factory.getClass().getSimpleName();
        }
        return typeKey;
    }

    /** 剪辑在该工厂池里的配置键。 */
    public static String keyOf(IFactory<Clip, ClipFactoryData> factory, Link type) {
        String pool = poolOf(factory);
        return POOL_CAMERA.equals(pool) ? type.toString() : pool + "#" + type.toString();
    }

    public static boolean isClipVisible(IFactory<Clip, ClipFactoryData> factory, Link type) {
        Boolean v = runtime.get(keyOf(factory, type));
        return v == null || v;
    }

    public static boolean getVisible(IFactory<Clip, ClipFactoryData> factory, Link type) {
        return isClipVisible(factory, type);
    }

    /** 摄像机池的旧入口（保留给外部/旧调用）。 */
    public static boolean getVisible(Link type) {
        return isClipVisible(BBSMod.getFactoryCameraClips(), type);
    }

    /** 进入编辑器时按保存值刷新运行时表（只保证当前工厂的键存在）。 */
    public static void onEnterEditor(UIContext context) {
        ensureKeys(BBSMod.getFactoryCameraClips());
        applySaved(BBSMod.getFactoryCameraClips());
    }

    public static void openVisibilityPanel(UIContext context) {
        openVisibilityPanel(context, BBSMod.getFactoryCameraClips());
    }

    /** 打开「可视性设置」面板：内容取自调用方编辑器自己的剪辑工厂。 */
    public static void openVisibilityPanel(UIContext context, IFactory<Clip, ClipFactoryData> factory) {
        if (context == null) return;
        IFactory<Clip, ClipFactoryData> target = factory != null ? factory : BBSMod.getFactoryCameraClips();
        activeFactory = target;
        applySaved(target);
        // V1.5.2-Alpha：面板水平 / 垂直尺寸各削减 40%（460→276，440→264）
        UIOverlay.addOverlay(context, new BcpVisibilityOverlayPanel(target), 276, 264);
    }

    public static IFactory<Clip, ClipFactoryData> getActiveFactory() {
        return activeFactory;
    }

    public static void setVisible(IFactory<Clip, ClipFactoryData> factory, Link type, boolean visible) {
        String key = keyOf(factory, type);
        runtime.put(key, visible);
        saved.put(key, visible);
        saveConfig();
    }

    public static void setVisible(Link type, boolean visible) {
        setVisible(BBSMod.getFactoryCameraClips(), type, visible);
    }

    private static void ensureKeys(IFactory<Clip, ClipFactoryData> factory) {
        if (factory == null) return;
        for (Link key : factory.getKeys()) {
            runtime.putIfAbsent(keyOf(factory, key), true);
        }
    }

    private static void applySaved(IFactory<Clip, ClipFactoryData> factory) {
        ensureKeys(factory);
        if (factory == null) return;
        runtime.clear();
        runtime.putAll(saved);
        // 另一池可能还没被 ensureKeys 补过默认值；这里只确保当前池的键存在
        for (Link key : factory.getKeys()) {
            runtime.putIfAbsent(keyOf(factory, key), true);
        }
    }

    private static void saveConfig() {
        try {
            MapType visibility = new MapType(true);
            for (Map.Entry<String, Boolean> e : saved.entrySet()) {
                visibility.putBool(e.getKey(), e.getValue());
            }
            MapType map = new MapType(true);
            map.put("visibility", visibility);
            DataToString.writeSilently(CONFIG, map);
        } catch (Exception e) {
            LOGGER.error("[BCP] 剪辑可见性配置写入异常", e);
        }
    }

    private static void loadConfig() {
        try {
            if (!CONFIG.exists()) return;
            BaseType base = DataToString.read(CONFIG);
            if (!(base instanceof MapType map)) return;
            if (map.get("visibility") instanceof MapType visibility) {
                saved.clear();
                for (String key : visibility.keys()) {
                    saved.put(key, visibility.getBool(key, true));
                }
            }
        } catch (Exception e) {
            LOGGER.error("[BCP] 剪辑可见性配置加载异常", e);
        }
    }
}
