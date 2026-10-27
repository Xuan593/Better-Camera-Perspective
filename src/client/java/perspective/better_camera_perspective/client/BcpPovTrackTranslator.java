package perspective.better_camera_perspective.client;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * V1.7.5-Alpha：POV 轨道名中文翻译表。
 *
 * 当 Cubed 的「中文关键帧轨道名称」开关打开时，
 * 将 POV 时间轴子页的轨道 id 翻译为中文。
 *
 * 预防性逻辑：先调 Cubed 的 KeyframeLocalizer.localize()，
 * 如果 Cubed 已翻译（返回非 null）就用 Cubed 的，
 * 只有 Cubed 没覆盖到的才用我们的翻译表。
 */
public class BcpPovTrackTranslator {

    private static final Map<String, String> POV_CN = new HashMap<>();
    private static Boolean cubedChineseNamesEnabled = null;
    private static Method cubedLocalizeMethod = null;
    private static boolean cubedLocalizeResolved = false;

    static {
        // ===== hotbar 子页 =====
        POV_CN.put("hotbar_layout", "快捷栏布局");
        POV_CN.put("hotbar_slots_layout", "快捷栏槽位布局");
        POV_CN.put("hotbar_hearts_layout", "生命值布局");
        POV_CN.put("hotbar_food_layout", "饥饿值布局");
        POV_CN.put("hotbar_exp_layout", "经验条布局");
        POV_CN.put("hotbar_visible", "快捷栏可见");
        POV_CN.put("hotbar_status_bars_visible", "状态条可见");
        POV_CN.put("hotbar_crosshair", "准星");
        POV_CN.put("hotbar_cursor_layout", "光标布局");
        POV_CN.put("hotbar_cursor_visible", "光标可见");
        POV_CN.put("hotbar_cursor_item", "光标物品");
        POV_CN.put("selected_slot", "选中槽位");
        POV_CN.put("item_slot_0", "槽位 0");
        POV_CN.put("item_slot_1", "槽位 1");
        POV_CN.put("item_slot_2", "槽位 2");
        POV_CN.put("item_slot_3", "槽位 3");
        POV_CN.put("item_slot_4", "槽位 4");
        POV_CN.put("item_slot_5", "槽位 5");
        POV_CN.put("item_slot_6", "槽位 6");
        POV_CN.put("item_slot_7", "槽位 7");
        POV_CN.put("item_slot_8", "槽位 8");
        POV_CN.put("hotbar_inventory_slots", "背包槽位");
        POV_CN.put("item_off_hand", "副手物品");
        POV_CN.put("hotbar_health", "生命值");
        POV_CN.put("hotbar_previous_health", "上一帧生命值");
        POV_CN.put("hotbar_heart_flash", "心跳闪烁");
        POV_CN.put("hotbar_health_container", "生命值容器");
        POV_CN.put("hotbar_absorption", "伤害吸收");
        POV_CN.put("hotbar_absorption_container", "伤害吸收容器");
        POV_CN.put("hotbar_heart_type", "心形类型");
        POV_CN.put("hotbar_hardcore", "极限模式");
        POV_CN.put("hotbar_regeneration", "生命恢复");
        POV_CN.put("hotbar_armor", "护甲值");
        POV_CN.put("hotbar_hunger", "饥饿值");
        POV_CN.put("hotbar_hunger_effect", "饥饿效果");
        POV_CN.put("hotbar_mount_health", "坐骑生命值");
        POV_CN.put("hotbar_mount_health_container", "坐骑生命值容器");
        POV_CN.put("hotbar_air", "气泡值");
        POV_CN.put("hotbar_experience", "经验值");
        POV_CN.put("hotbar_experience_level", "经验等级");
        POV_CN.put("hotbar_absorption_flash", "吸收闪烁");

        // ===== Hand 子页 =====
        POV_CN.put("pov_hand_visible", "手部可见");
        POV_CN.put("pov_hand_model", "手部模型");
        POV_CN.put("pov_hand_texture", "手部纹理");
        POV_CN.put("pov_hand_color", "手部颜色");
        POV_CN.put("pov_hand_color_overlay", "手部颜色叠加");
        POV_CN.put("pov_hand_camera_offset", "手相机偏移");
        POV_CN.put("pov_hand_pose", "手部姿势");
        POV_CN.put("pov_hand_item_pose", "手部物品姿势");
        POV_CN.put("pov_hand_world_interaction", "手部世界交互");
        POV_CN.put("pov_hand_replay_interaction", "手部回放交互");
        POV_CN.put("pov_hand_right_hand_visible", "右手可见");
        POV_CN.put("pov_hand_left_hand_visible", "左手可见");
        POV_CN.put("pov_hand_right_swing_progress", "右手挥动进度");
        POV_CN.put("pov_hand_left_swing_progress", "左手挥动进度");
        POV_CN.put("pov_hand_main_equip", "主手装备");
        POV_CN.put("pov_hand_off_equip", "副手装备");
        POV_CN.put("pov_hand_active_hand", "当前手");
        POV_CN.put("pov_hand_active_item", "当前物品");
        POV_CN.put("pov_hand_show_use_particles", "使用粒子显示");
        POV_CN.put("pov_hand_use_time", "使用时间");
        POV_CN.put("pov_hand_bob_phase", "摆动相位");
        POV_CN.put("pov_hand_bob_strength", "摆动力度");
        POV_CN.put("pov_hand_render_yaw", "渲染偏航角");
        POV_CN.put("pov_hand_render_pitch", "渲染俯仰角");
        POV_CN.put("pov_hand_main_arm", "主手臂");

        // ===== POV Action 子页 =====
        POV_CN.put("actions", "POV动作");
        POV_CN.put("actions_overlay", "动作叠加");
        POV_CN.put("actions_overlay0", "动作叠加 0");
        POV_CN.put("actions_overlay1", "动作叠加 1");
        POV_CN.put("actions_overlay2", "动作叠加 2");
        POV_CN.put("actions_overlay3", "动作叠加 3");
        POV_CN.put("actions_overlay4", "动作叠加 4");
        POV_CN.put("actions_overlay5", "动作叠加 5");

        // ===== POV Action 右键菜单（9个action类型）=====
        // 注意：key 是实际传入的 original 格式（大写/带空格），不是小写id
        POV_CN.put("GUI", "GUI界面");
        POV_CN.put("Menu", "菜单");
        POV_CN.put("Camera Shake", "相机震动");
        POV_CN.put("Particle Effect", "粒子效果");
        POV_CN.put("Boss Bars", "Boss血条");
        POV_CN.put("Status Effects", "状态效果");
        POV_CN.put("Toasts", "弹窗通知");
        POV_CN.put("Chat", "聊天栏");
        POV_CN.put("Screen Effect", "屏幕特效");
    }

    /**
     * 检查 Cubed 的「中文关键帧轨道名称」开关是否打开。
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
            cubedChineseNamesEnabled = (Boolean) value.getClass().getMethod("get").invoke(value);
            return cubedChineseNamesEnabled;
        } catch (Exception e) {
            cubedChineseNamesEnabled = false;
            return false;
        }
    }

    /**
     * 尝试调用 Cubed 的 KeyframeLocalizer.localize(id)。
     * 如果 Cubed 已翻译，返回翻译结果；否则返回 null。
     */
    private static String cubedLocalize(String id) {
        if (!FabricLoader.getInstance().isModLoaded("bbsplusplus")) {
            return null;
        }
        try {
            if (!cubedLocalizeResolved) {
                Class<?> localizer = Class.forName("gbeic.bbsplusplus.KeyframeLocalizer");
                cubedLocalizeMethod = localizer.getMethod("localize", String.class);
                cubedLocalizeResolved = true;
            }
            if (cubedLocalizeMethod == null) {
                return null;
            }
            return (String) cubedLocalizeMethod.invoke(null, id);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 翻译轨道 id。
     * 优先用 Cubed 的翻译，如果 Cubed 没覆盖到才用我们的表。
     */
    public static String translate(String id) {
        if (id == null) {
            return null;
        }
        // 预防性补丁：先问 Cubed 有没有翻译
        String cubedResult = cubedLocalize(id);
        if (cubedResult != null && !cubedResult.equals(id)) {
            return cubedResult; // Cubed 已覆盖，以 Cubed 为主
        }
        // Cubed 没翻译，用我们的表
        return POV_CN.get(id);
    }

    /**
     * 清除缓存。
     */
    public static void clearCache() {
        cubedChineseNamesEnabled = null;
    }
}
