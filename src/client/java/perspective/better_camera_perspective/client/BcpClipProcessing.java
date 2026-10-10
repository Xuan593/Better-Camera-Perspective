package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设置页「可视化设置」分类（原「剪辑处理设置」，v1.5-Alpha 更名）：
 * 剪辑条可视化开关（名称/边界线/类型配色，由 UIClipRendererMixin 读取）
 * + 「预新建剪辑可视化」三模式（原始 / 覆盖 / 混合，由 UIClipsAddClipMixin 读取）。
 */
@Environment(EnvType.CLIENT)
public class BcpClipProcessing {
    public static final String KEY_CLIP_NAMES = "clip_names";
    public static final String KEY_CLIP_BORDERS = "clip_borders";
    public static final String KEY_CLIP_COLORS = "clip_colors";
    public static final String KEY_CLIP_MENU_LAYOUT = "clip_menu_layout";

    public static final String LAYOUT_ORIGINAL = "original";
    public static final String LAYOUT_OVERRIDE = "override";
    public static final String LAYOUT_MIXED = "mixed";

    public static final int LAYOUT_ORIGINAL_IDX = 0;
    public static final int LAYOUT_OVERRIDE_IDX = 1;
    public static final int LAYOUT_MIXED_IDX = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");
    private static final Map<String, ValueBoolean> VALUES = new HashMap<>();
    private static final String LAYOUT_LABEL_PREFIX = "conflict_features.config.clip_processing.clip_menu_layout";
    private static BcpModeValue layoutValue;
    private static boolean registered = false;

    public static void registerValues(SettingsBuilder builder) {
        if (registered) return;
        try {
            builder.category("clip_processing");
            VALUES.put(KEY_CLIP_NAMES, builder.getBoolean(KEY_CLIP_NAMES, true));
            VALUES.put(KEY_CLIP_BORDERS, builder.getBoolean(KEY_CLIP_BORDERS, true));
            VALUES.put(KEY_CLIP_COLORS, builder.getBoolean(KEY_CLIP_COLORS, true));
            layoutValue = new BcpModeValue(KEY_CLIP_MENU_LAYOUT, LAYOUT_ORIGINAL,
                List.of(LAYOUT_ORIGINAL, LAYOUT_OVERRIDE, LAYOUT_MIXED), LAYOUT_LABEL_PREFIX);
            builder.register(layoutValue);
            registered = true;
        } catch (Exception e) {
            LOGGER.error("[BCP] 设置页「可视化设置」注册失败", e);
        }
    }

    public static boolean isEnabled(String key) {
        ValueBoolean value = VALUES.get(key);
        return value == null || value.get();
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
