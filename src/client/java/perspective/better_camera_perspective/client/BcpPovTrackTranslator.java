package perspective.better_camera_perspective.client;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * POV 轨道名中文翻译表。
 *
 * 根据 BcpConflictDetection 设置决定翻译优先级：
 * Cubed优先 / BCP优先 / Cubed为主BCP辅助
 */
public class BcpPovTrackTranslator {

    private static final Logger LOGGER = LoggerFactory.getLogger("BCP-PovTranslator");
    private static final Map<String, String> POV_CN = new HashMap<>();
    private static final java.util.Set<String> UNTRANSLATED_LOGGED = new java.util.HashSet<>();
    private static Boolean cubedChineseNamesEnabled = null;
    private static Method cubedLocalizeMethod = null;
    private static boolean cubedLocalizeResolved = false;
    private static boolean firstTranslateLogged = false;

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

        // ===== POV Action 属性面板显示名 =====
        POV_CN.put("Edit Keyframes", "编辑关键帧");
        POV_CN.put("GUI Type", "GUI类型");
        POV_CN.put("GUI Layout", "GUI布局");
        POV_CN.put("GUI Opacity", "GUI透明度");
        POV_CN.put("GUI Settings", "GUI设置");
        POV_CN.put("GUI Keyframes", "GUI关键帧");
        POV_CN.put("Saddle", "鞍");
        POV_CN.put("Donkey Slots", "驴槽位");
        POV_CN.put("Donkey Chest", "驴箱子");
        POV_CN.put("Horse Variant", "马变种");
        POV_CN.put("Crafting 2x2", "合成2x2");
        POV_CN.put("Crafting 3x3", "合成3x3");
        POV_CN.put("Recipe Book", "配方书");
        POV_CN.put("Recipe Button", "配方按钮");
        POV_CN.put("Recipe Category", "配方分类");
        POV_CN.put("Recipe Page", "配方页");
        POV_CN.put("Recipe Search", "配方搜索");
        POV_CN.put("Recipe Search Focus", "配方搜索光标");
        POV_CN.put("Selected Recipe", "选中配方");
        POV_CN.put("Showing Craftable", "显示可合成");
        POV_CN.put("Furnace Fire", "熔炉火焰");
        POV_CN.put("Cook Arrow", "烹饪箭头");
        POV_CN.put("Brew Arrow", "酿造箭头");
        POV_CN.put("Fuel Bar", "燃料条");
        POV_CN.put("Bubbles", "气泡");
        POV_CN.put("Anvil Name", "铁砧名称");
        POV_CN.put("Anvil Caret", "铁砧光标");
        POV_CN.put("Anvil Cross", "铁砧错误");
        POV_CN.put("Gamemode", "游戏模式");
        POV_CN.put("Beacon Level", "信标等级");
        POV_CN.put("Primary Power", "主效果");
        POV_CN.put("Secondary Power", "副效果");
        POV_CN.put("Creative Tab", "创造模式标签");
        POV_CN.put("Creative Page", "创造模式页");
        POV_CN.put("Creative Scroll Row", "创造模式滚动行");
        POV_CN.put("Creative Search", "创造模式搜索");
        POV_CN.put("Creative Search Focus", "创造模式搜索光标");
        POV_CN.put("Loom Scroll Row", "织布机滚动行");
        POV_CN.put("Stonecutter Scroll Row", "切石机滚动行");
        POV_CN.put("Book Page", "书页");
        POV_CN.put("Book Pages", "书页数");
        POV_CN.put("Writable", "可书写");
        POV_CN.put("Signing", "签名中");
        POV_CN.put("Book Title", "书名");
        POV_CN.put("Book Author", "书作者");
        POV_CN.put("Profession", "职业");
        POV_CN.put("Villager Level", "村民等级");
        POV_CN.put("Villager XP", "村民经验");
        POV_CN.put("Show XP Bar", "显示经验条");
        POV_CN.put("Selected Trade", "选中交易");
        POV_CN.put("Trade Scroll", "交易滚动");
        POV_CN.put("Menu Keyframes", "菜单关键帧");
        POV_CN.put("Menu Settings", "菜单设置");
        POV_CN.put("Menu Type", "菜单类型");
        POV_CN.put("Game Menu", "游戏菜单");
        POV_CN.put("Leave Bed", "离开床");
        POV_CN.put("Camera Shake Keyframes", "相机震动关键帧");
        POV_CN.put("Chat Scroll", "聊天滚动");
        POV_CN.put("Chat Text", "聊天文字");
        POV_CN.put("Executed Text", "已执行文本");
        POV_CN.put("Command Recommendations", "命令建议");
        POV_CN.put("Boss Bar", "Boss血条");
        POV_CN.put("Boss", "Boss");
        POV_CN.put("Status Effects", "状态效果");
        POV_CN.put("Beneficial Effects", "有益效果");
        POV_CN.put("Harmful Effects", "有害效果");
        POV_CN.put("Harmless Effects", "无害效果");
        POV_CN.put("Selected Effect", "选中效果");
        POV_CN.put("Effect amplifier / level (0 = Level I, 1 = Level II, etc.)", "效果放大器/等级（0=I级，1=II级，以此类推）");
        POV_CN.put("Make this status effect duration infinite (∞)", "使此状态效果持续时间无限（∞）");
        POV_CN.put("Unlimited (∞)", "无限（∞）");
        POV_CN.put("Toast Preset", "弹窗预设");
        POV_CN.put("Pick Toast", "选择弹窗");
        POV_CN.put("Pick Toast Type", "选择弹窗类型");
        POV_CN.put("Choose a preset toast from Advancements, Recipes, Tutorials, or System notifications", "从进度、配方、教程或系统通知中选择预设弹窗");
        POV_CN.put("Optional custom title override (leave empty to use preset title)", "可选的自定义标题覆盖（留空使用预设标题）");
        POV_CN.put("Optional custom description override (leave empty to use preset description)", "可选的自定义描述覆盖（留空使用预设描述）");
        POV_CN.put("Select custom item icon for this toast", "为此弹窗选择自定义物品图标");
        POV_CN.put("Pick Item", "选择物品");
        POV_CN.put("Pick Texture", "选择纹理");
        POV_CN.put("Choose a custom texture (ignores item icon if selected)", "选择自定义纹理（选中后忽略物品图标）");
        POV_CN.put("Remove custom texture", "移除自定义纹理");
        POV_CN.put("Reset custom item", "重置自定义物品");
        POV_CN.put("Totem Item", "图腾物品");
        POV_CN.put("Totem Particles", "图腾粒子");
        POV_CN.put("Select item to display during totem popup", "选择图腾弹出时显示的物品");
        POV_CN.put("Reset to default Totem of Undying", "重置为默认不死图腾");
        POV_CN.put("Screen Effects", "屏幕特效");
        POV_CN.put("Blindness Opacity", "失明透明度");
        POV_CN.put("Darkness Opacity", "黑暗透明度");
        POV_CN.put("Darkness Opacity / Pulse", "黑暗透明度/脉冲");
        POV_CN.put("Frost Progress", "霜冻进度");
        POV_CN.put("Nausea", "反胃");
        POV_CN.put("Night Vision", "夜视");
        POV_CN.put("Portal Opacity", "传送门透明度");
        POV_CN.put("Pumpkin Opacity", "南瓜透明度");
        POV_CN.put("Suffocation Opacity", "窒息透明度");
        POV_CN.put("Underwater Opacity", "水下透明度");
        POV_CN.put("Zoom", "缩放");
        POV_CN.put("Zoom Multiplier", "缩放倍数");
        POV_CN.put("Spyglass Scale", "望远镜缩放");
        POV_CN.put("Death Message", "死亡消息");
        POV_CN.put("Death Time", "死亡时间");
        POV_CN.put("Hurt Time", "受击时间");
        POV_CN.put("Max Hurt Time", "最大受击时间");
        POV_CN.put("Damage Tilt Yaw", "伤害倾斜偏航");
        POV_CN.put("Flipped (Left Hand)", "翻转（左手）");
        POV_CN.put("Health", "生命值");
        POV_CN.put("Score", "分数");
        POV_CN.put("Score: 0", "分数：0");
        POV_CN.put("Name", "名称");
        POV_CN.put("Color", "颜色");
        POV_CN.put("Style", "样式");
        POV_CN.put("Count", "数量");
        POV_CN.put("Block ID", "方块ID");
        POV_CN.put("Particle", "粒子");
        POV_CN.put("Choose Particle", "选择粒子");
        POV_CN.put("Circle Radius (Blocks)", "圆形半径（方块）");
        POV_CN.put("Spread", "扩散");
        POV_CN.put("Pos X", "位置X");
        POV_CN.put("Pos Y", "位置Y");
        POV_CN.put("Pos Z", "位置Z");
        POV_CN.put("Vel X", "速度X");
        POV_CN.put("Vel Y", "速度Y");
        POV_CN.put("Vel Z", "速度Z");
        POV_CN.put("Seed", "种子");
        POV_CN.put("Brightness / Opacity", "亮度/透明度");
        POV_CN.put("Overlay Opacity", "叠加层透明度");
        POV_CN.put("Tint Color", "着色颜色");
        POV_CN.put("Custom Overrides", "自定义覆盖");
        POV_CN.put("Flash", "闪烁");
        POV_CN.put("Pop Progress", "弹出进度");
        POV_CN.put("Distortion", "扭曲");
        POV_CN.put("Space", "空间");
        POV_CN.put("Space: Local", "空间：局部");
        POV_CN.put("Space: View", "空间：视图");
        POV_CN.put("Space: World", "空间：世界");
        POV_CN.put("Environment & Vision", "环境与视野");
        POV_CN.put("Hazards & Environment", "危险与环境");
        POV_CN.put("Status & Popups", "状态与弹窗");
        POV_CN.put("Buttons Active", "按钮激活");
        POV_CN.put("Background Bar / Cursor", "背景条/光标");
        POV_CN.put("Cursor Position", "光标位置");
        POV_CN.put("Visible", "可见");
        POV_CN.put("Active", "激活");
        POV_CN.put("None", "无");
        POV_CN.put("Red Background", "红色背景");
        POV_CN.put("Ender Dragon", "末影龙");
        // Boss Bar 属性面板
        POV_CN.put("Boss: Ender Dragon", "Boss：末影龙");
        POV_CN.put("Color: Pink", "颜色：粉色");
        POV_CN.put("Style: Progress", "样式：进度条");
        // Toast 属性面板
        POV_CN.put("Advancement Made!", "进度达成！");
        POV_CN.put("Diamonds!", "获得钻石！");
        // GUI 类型选择弹窗
        POV_CN.put("Inventory", "物品栏");
        POV_CN.put("Creative Inventory", "创造模式物品栏");
        POV_CN.put("Crafting Table", "合成台");
        POV_CN.put("Smithing Table", "锻造台");
        POV_CN.put("Grindstone", "砂轮");
        POV_CN.put("Stonecutter", "切石机");
        POV_CN.put("Player", "玩家");
        POV_CN.put("Crafting", "合成");
        // 菜单/GUI 设置按钮
        POV_CN.put("Menu: Sleep", "菜单：睡觉");
        POV_CN.put("GUI: Inventory", "GUI：物品栏");

        // ===== 其他 mod 自定义状态效果 =====
        POV_CN.put("Satisfaction", "满足");
        POV_CN.put("Sweet Heart", "甜心");
        POV_CN.put("Tipsy", "微醺");
        POV_CN.put("No WM Cave Maps", "洞穴地图不可用");
        POV_CN.put("No World Map", "世界地图不可用");
        POV_CN.put("实体雷达不可用", "实体雷达不可用");
        POV_CN.put("小地图不可用", "小地图不可用");
        POV_CN.put("路径点不可用", "路径点不可用");

        // ===== GUI 类型列表（GUI Type 弹窗选项）=====
        POV_CN.put("Chest (Large 9x6)", "大箱子（9x6）");
        POV_CN.put("Chest (Small 9x3)", "小箱子（9x3）");
        POV_CN.put("Large Chest", "大箱子");
        POV_CN.put("Donkey Inventory", "驴物品栏");
        POV_CN.put("Horse Inventory", "马物品栏");
        POV_CN.put("Gamemode Switcher", "游戏模式切换器");
        POV_CN.put("Repair & Disenchant", "修复与卸魔");
        POV_CN.put("Repair & Name", "修复与命名");
        POV_CN.put("Enchant", "附魔");
        POV_CN.put("Enchanting", "附魔台");
        POV_CN.put("Enchanting Table", "附魔台");
        POV_CN.put("Brewing", "酿造");
        POV_CN.put("Brewing Stand", "酿造台");
        POV_CN.put("Cartography Table", "制图台");
        POV_CN.put("Loom", "织布机");
        POV_CN.put("Blast Furnace", "高炉");
        POV_CN.put("Smoker", "烟熏炉");
        POV_CN.put("Dropper", "投掷器");
        POV_CN.put("Dispenser", "发射器");
        POV_CN.put("Hopper", "漏斗");
        POV_CN.put("Item Hopper", "物品漏斗");
        POV_CN.put("Shulker Box", "潜影盒");
        POV_CN.put("Ender Chest", "末影箱");
        POV_CN.put("Barrel", "木桶");
        POV_CN.put("Merchant", "商人");
        POV_CN.put("Villager Trading", "村民交易");
        POV_CN.put("Trading", "交易");
        POV_CN.put("Containers", "容器");
        POV_CN.put("Entities", "实体");
        POV_CN.put("Redstone", "红石");
        POV_CN.put("Special", "特殊");
        POV_CN.put("Progress", "进度");

        // ===== 面板提示文本 =====
        POV_CN.put("Right-click to add effects", "右键添加效果");
        POV_CN.put("Right-click to add screen effects", "右键添加屏幕特效");

        // ===== Toast 类型与预设名 =====
        POV_CN.put("Advancements", "进度");
        POV_CN.put("Recipes", "配方");
        POV_CN.put("Tutorials", "教程");
        POV_CN.put("Tutorial", "教程");
        POV_CN.put("System", "系统");
        POV_CN.put("Challenge Complete!", "挑战完成！");
        POV_CN.put("Goal Reached!", "目标达成！");
        POV_CN.put("Recipe Unlocked!", "配方解锁！");

        // ===== 教程步骤名 =====
        POV_CN.put("Movement Keys (WASD)", "移动键（WASD）");
        POV_CN.put("Look Around with Mouse", "鼠标环视");
        POV_CN.put("Open Inventory (E)", "打开物品栏（E）");
        POV_CN.put("Punch a Tree Trunk", "击打树干");
        POV_CN.put("Craft a Workbench", "合成工作台");

        // ===== Boss 血条样式 =====
        POV_CN.put("Notched 6", "分段 6");
        POV_CN.put("Notched 10", "分段 10");
        POV_CN.put("Notched 12", "分段 12");
        POV_CN.put("Notched 20", "分段 20");
        POV_CN.put("Wither", "凋灵");

        // ===== 系统通知类 =====
        POV_CN.put("Narrator", "旁白");
        POV_CN.put("Narrator is now active", "旁白已激活");
        POV_CN.put("Screenshot", "截图");
        POV_CN.put("Saved as screenshot.png", "已保存为 screenshot.png");
        POV_CN.put("World Saved", "世界已保存");
        POV_CN.put("All chunks have been saved", "所有区块已保存");

        // ===== GUI 装备槽位 =====
        POV_CN.put("Head Armor", "头盔护甲");
        POV_CN.put("Chest Armor", "胸甲护甲");
        POV_CN.put("Leg Armor", "护腿护甲");
        POV_CN.put("Feet Armor", "靴子护甲");

        // ===== 菜单设置按钮（Menu: xxx 格式）=====
        POV_CN.put("Menu: Death", "菜单：死亡");
        POV_CN.put("Menu: Game Menu", "菜单：游戏菜单");
        POV_CN.put("Menu: Sleep", "菜单：睡觉");

        // ===== Boss血条设置按钮（Boss: xxx / Color: xxx / Style: xxx 格式）=====
        POV_CN.put("Boss: Raid", "Boss：袭击");
        POV_CN.put("Boss: Ender Dragon", "Boss：末影龙");
        POV_CN.put("Raid", "袭击");
        POV_CN.put("Color: Red", "颜色：红色");
        POV_CN.put("Color: Pink", "颜色：粉色");
        POV_CN.put("Color: Blue", "颜色：蓝色");
        POV_CN.put("Color: Green", "颜色：绿色");
        POV_CN.put("Color: Yellow", "颜色：黄色");
        POV_CN.put("Color: Purple", "颜色：紫色");
        POV_CN.put("Color: White", "颜色：白色");
        POV_CN.put("Style: Progress", "样式：进度条");
        POV_CN.put("Style: Notched 6", "样式：分段 6");
        POV_CN.put("Style: Notched 10", "样式：分段 10");
        POV_CN.put("Style: Notched 12", "样式：分段 12");
        POV_CN.put("Style: Notched 20", "样式：分段 20");

        LOGGER.info("[BCP-PovTranslator] 翻译表初始化完成，共 {} 条条目", POV_CN.size());
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
     * 尝试调用第三方插件的 KeyframeLocalizer.localize(id)。
     * 如果外部插件已翻译，返回翻译结果；否则返回 null。
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
                LOGGER.info("[BCP-PovTranslator] 第三方 KeyframeLocalizer 反射绑定成功（bbsplusplus）");
            }
            if (cubedLocalizeMethod == null) {
                return null;
            }
            return (String) cubedLocalizeMethod.invoke(null, id);
        } catch (Exception e) {
            LOGGER.debug("[BCP-PovTranslator] 第三方 localize 调用失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 翻译轨道 id。
     * 根据 BcpConflictDetection 设置决定优先级：
     * - 0 (Cubed优先)：完全用 Cubed 翻译，BCP 表不介入
     * - 1 (BCP优先)：完全用 BCP 表，不调 Cubed
     * - 2 (Cubed为主，BCP辅助)：先 Cubed，Cubed 没覆盖的用 BCP 补充
     */
    public static String translate(String id) {
        if (id == null) {
            return null;
        }
        int mode = BcpConflictDetection.getPovTrackMode();
        if (!firstTranslateLogged) {
            String modeName = switch (mode) {
                case BcpConflictDetection.POV_TRACK_BCP_FIRST_IDX -> "BCP优先";
                case BcpConflictDetection.POV_TRACK_CUBED_WITH_BCP_IDX -> "第三方为主，BCP辅助";
                default -> "第三方优先";
            };
            LOGGER.info("[BCP-PovTranslator] 首次翻译调用，当前模式 = {}（id={}）", modeName, id);
            firstTranslateLogged = true;
        }
        switch (mode) {
            case BcpConflictDetection.POV_TRACK_BCP_FIRST_IDX:
                // BCP优先：直接查 BCP 表
                String bcpResult = POV_CN.get(id);
                if (bcpResult == null && UNTRANSLATED_LOGGED.add(id)) {
                    LOGGER.debug("[BCP-PovTranslator] 未翻译字符串: {}", id);
                }
                return bcpResult;
            case BcpConflictDetection.POV_TRACK_CUBED_FIRST_IDX:
                // Cubed优先：只调 Cubed，Cubed 没翻译就返回原文
                String cubedOnly = cubedLocalize(id);
                if (cubedOnly != null && !cubedOnly.equals(id)) {
                    return cubedOnly;
                }
                return id; // 保持英文
            case BcpConflictDetection.POV_TRACK_CUBED_WITH_BCP_IDX:
            default:
                // Cubed为主，BCP辅助：先 Cubed，没覆盖的用 BCP 补充
                String cubedResult = cubedLocalize(id);
                if (cubedResult != null && !cubedResult.equals(id)) {
                    return cubedResult;
                }
                return POV_CN.get(id);
        }
    }

    /**
     * 清除缓存。
     */
    public static void clearCache() {
        cubedChineseNamesEnabled = null;
    }
}
