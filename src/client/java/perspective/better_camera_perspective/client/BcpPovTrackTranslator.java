package perspective.better_camera_perspective.client;

import net.fabricmc.loader.api.FabricLoader;

import java.util.HashMap;
import java.util.Map;

/**
 * V1.7.3-Alpha：POV 轨道名中文翻译表。
 *
 * 当 Cubed 的「中文关键帧轨道名称」开关打开时，
 * 将 POV 时间轴子页的轨道 id 翻译为中文。
 *
 * 翻译表：轨道 id（英文） → 中文显示名
 */
public class BcpPovTrackTranslator {

    private static final Map<String, String> POV_CN = new HashMap<>();
    private static Boolean cubedChineseNamesEnabled = null;

    static {
        // POV Action 子页（PovActionType）
        POV_CN.put("gui", "GUI界面");
        POV_CN.put("menu", "菜单");
        POV_CN.put("camera_shake", "相机震动");
        POV_CN.put("particle_effect", "粒子效果");
        POV_CN.put("boss_bars", "Boss血条");
        POV_CN.put("status_effects", "状态效果");
        POV_CN.put("toasts", "弹窗通知");
        POV_CN.put("chat", "聊天栏");
        POV_CN.put("screen_effect", "屏幕特效");

        // hotbar 子页（待补充）
        // POV_CN.put("hotbar_selected", "快捷栏选中槽");
        // POV_CN.put("hotbar_item", "快捷栏物品");
        // POV_CN.put("hotbar_transform", "快捷栏变换");

        // Hand 子页（待补充）
        // POV_CN.put("hand_x", "手部X");
        // POV_CN.put("hand_y", "手部Y");
        // POV_CN.put("hand_z", "手部Z");
    }

    /**
     * 检查 Cubed 的「中文关键帧轨道名称」开关是否打开。
     * 通过反射访问 gbeic.bbsplusplus.settings.ModSettings.chineseKeyframeNames。
     */
    public static boolean isCubedChineseNamesEnabled() {
        if (cubedChineseNamesEnabled != null) {
            return cubedChineseNamesEnabled;
        }
        try {
            if (!FabricLoader.getInstance().isModLoaded("bbsplusplus")) {
                cubedChineseNamesEnabled = false;
                return false;
            }
            Class<?> modSettings = Class.forName("gbeic.bbsplusplus.settings.ModSettings");
            Object value = modSettings.getField("chineseKeyframeNames").get(null);
            // ValueBoolean 继承自 ValueBase，get() 返回 Boolean
            cubedChineseNamesEnabled = (Boolean) value.getClass().getMethod("get").invoke(value);
            return cubedChineseNamesEnabled;
        } catch (Exception e) {
            cubedChineseNamesEnabled = false;
            return false;
        }
    }

    /**
     * 翻译轨道 id。
     * @param id 轨道 id（英文）
     * @return 中文显示名，如果没有匹配返回 null
     */
    public static String translate(String id) {
        if (id == null) {
            return null;
        }
        return POV_CN.get(id);
    }

    /**
     * 清除缓存（设置切换时调用）。
     */
    public static void clearCache() {
        cubedChineseNamesEnabled = null;
    }
}
