package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.settings.SettingsBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 设置页「可视化设置」分类（原「剪辑处理设置」，v1.5-Alpha 更名）。
 *
 * V1.5.2-Alpha：拆分为两个独立设置项 ——
 *  - 「预创建剪辑可视化」（clip_menu_layout）：原始 / 覆盖 —— 右键添加剪辑菜单的布局分类
 *    （由 UIClipsAddClipMixin 读取）；
 *  - 「可视化筛选排布」（visibility_layout）：动态 / 静态 —— 可视性设置面板的排布
 *    （静态 = 被隐藏的剪辑在筛选界面中的布局位置不变，由 BcpVisibilityOverlayPanel 读取）。
 *
 * V1.5.2-Alpha（第 3 次修订）：移除「混合」模式（实测不如不混合）。
 * V1.5.1-Alpha：剔除原剪辑条显示三开关（剪辑名称显示 / 剪辑边界线 / 剪辑类型配色），
 * 剪辑条渲染恢复 BBS 原版默认行为。
 */
@Environment(EnvType.CLIENT)
public class BcpClipProcessing {
    public static final String KEY_CLIP_MENU_LAYOUT = "clip_menu_layout";
    public static final String KEY_VISIBILITY_LAYOUT = "visibility_layout";

    public static final String LAYOUT_ORIGINAL = "original";
    public static final String LAYOUT_OVERRIDE = "override";
    public static final String LAYOUT_DYNAMIC = "dynamic";
    public static final String LAYOUT_STATIC = "static";

    public static final int LAYOUT_ORIGINAL_IDX = 0;
    public static final int LAYOUT_OVERRIDE_IDX = 1;
    public static final int LAYOUT_DYNAMIC_IDX = 0;
    public static final int LAYOUT_STATIC_IDX = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");
    private static final String MENU_LABEL_PREFIX = "conflict_features.config.clip_processing.clip_menu_layout";
    private static final String VISIBILITY_LABEL_PREFIX = "conflict_features.config.clip_processing.visibility_layout";
    private static BcpModeValue layoutValue;
    private static BcpModeValue visibilityValue;
    private static boolean registered = false;

    public static void registerValues(SettingsBuilder builder) {
        if (registered) return;
        try {
            builder.category("clip_processing");
            layoutValue = new BcpModeValue(KEY_CLIP_MENU_LAYOUT, LAYOUT_ORIGINAL,
                List.of(LAYOUT_ORIGINAL, LAYOUT_OVERRIDE), MENU_LABEL_PREFIX);
            builder.register(layoutValue);
            visibilityValue = new BcpModeValue(KEY_VISIBILITY_LAYOUT, LAYOUT_DYNAMIC,
                List.of(LAYOUT_DYNAMIC, LAYOUT_STATIC), VISIBILITY_LABEL_PREFIX);
            builder.register(visibilityValue);
            registered = true;
        } catch (Exception e) {
            LOGGER.error("[BCP] 设置页「可视化设置」注册失败", e);
        }
    }

    /** 预创建剪辑可视化：0=原始 1=覆盖 */
    public static int getClipMenuLayoutMode() {
        if (layoutValue == null) return LAYOUT_ORIGINAL_IDX;
        switch (layoutValue.get()) {
            case LAYOUT_OVERRIDE:
                return LAYOUT_OVERRIDE_IDX;
            default:
                return LAYOUT_ORIGINAL_IDX;
        }
    }

    /** 可视化筛选排布：0=动态（筛选后重排，隐藏沉底） 1=静态（隐藏剪辑保留原位） */
    public static int getVisibilityLayoutMode() {
        if (visibilityValue == null) return LAYOUT_DYNAMIC_IDX;
        switch (visibilityValue.get()) {
            case LAYOUT_STATIC:
                return LAYOUT_STATIC_IDX;
            default:
                return LAYOUT_DYNAMIC_IDX;
        }
    }
}
