package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.settings.SettingsBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 设置页「可视化设置」分类（原「剪辑处理设置」，v1.5-Alpha 更名）：
 * 「预新建剪辑可视化」三模式（原始 / 覆盖 / 混合，由 UIClipsAddClipMixin 读取）。
 *
 * V1.5.1-Alpha：剔除原剪辑条显示三开关（剪辑名称显示 / 剪辑边界线 / 剪辑类型配色），
 * 剪辑条渲染恢复 BBS 原版默认行为。
 */
@Environment(EnvType.CLIENT)
public class BcpClipProcessing {
    public static final String KEY_CLIP_MENU_LAYOUT = "clip_menu_layout";

    public static final String LAYOUT_ORIGINAL = "original";
    public static final String LAYOUT_OVERRIDE = "override";
    public static final String LAYOUT_MIXED = "mixed";

    public static final int LAYOUT_ORIGINAL_IDX = 0;
    public static final int LAYOUT_OVERRIDE_IDX = 1;
    public static final int LAYOUT_MIXED_IDX = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");
    private static final String LAYOUT_LABEL_PREFIX = "conflict_features.config.clip_processing.clip_menu_layout";
    private static BcpModeValue layoutValue;
    private static boolean registered = false;

    public static void registerValues(SettingsBuilder builder) {
        if (registered) return;
        try {
            builder.category("clip_processing");
            layoutValue = new BcpModeValue(KEY_CLIP_MENU_LAYOUT, LAYOUT_ORIGINAL,
                List.of(LAYOUT_ORIGINAL, LAYOUT_OVERRIDE, LAYOUT_MIXED), LAYOUT_LABEL_PREFIX);
            builder.register(layoutValue);
            registered = true;
        } catch (Exception e) {
            LOGGER.error("[BCP] 设置页「可视化设置」注册失败", e);
        }
    }

    /** 0=原始 1=覆盖 2=混合 */
    public static int getClipMenuLayoutMode() {
        if (layoutValue == null) return LAYOUT_ORIGINAL_IDX;
        switch (layoutValue.get()) {
            case LAYOUT_OVERRIDE:
                return LAYOUT_OVERRIDE_IDX;
            case LAYOUT_MIXED:
                return LAYOUT_MIXED_IDX;
            default:
                return LAYOUT_ORIGINAL_IDX;
        }
    }
}
