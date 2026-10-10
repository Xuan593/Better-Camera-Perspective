package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.settings.SettingsBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * v1.5-Alpha：设置页「关键帧处理设置」分类。
 *
 * 「关键帧被选中状态」三模式（由 UIKeyframeDopeSheetMixin 读取）：
 *  - 默认：原始处理设置（新建关键帧后全部保持选中）；
 *  - 单体：每新建一个关键帧自动解除上一个关键帧的选中状态；
 *  - 混合：前两者的智能融合（连续新建只保留最新帧选中，手动选中的帧不受影响）。
 */
@Environment(EnvType.CLIENT)
public class BcpKeyframeProcessing {
    public static final String CATEGORY = "keyframe_processing";
    public static final String KEY_SELECTION = "keyframe_selection";
    public static final String MODE_DEFAULT = "default";
    public static final String MODE_SINGLE = "single";
    public static final String MODE_MIXED = "mixed";

    public static final int MODE_DEFAULT_IDX = 0;
    public static final int MODE_SINGLE_IDX = 1;
    public static final int MODE_MIXED_IDX = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");
    private static final String LABEL_PREFIX = "conflict_features.config.keyframe_processing.keyframe_selection";
    private static BcpModeValue selectionValue;
    private static boolean registered = false;

    public static void registerValues(SettingsBuilder builder) {
        if (registered) return;
        try {
            builder.category(CATEGORY);
            selectionValue = new BcpModeValue(KEY_SELECTION, MODE_DEFAULT,
                List.of(MODE_DEFAULT, MODE_SINGLE, MODE_MIXED), LABEL_PREFIX);
            builder.register(selectionValue);
            registered = true;
        } catch (Exception e) {
            LOGGER.error("[BCP] 设置页「关键帧处理设置」注册失败", e);
        }
    }

    /** 0=默认 1=单体 2=混合 */
    public static int getSelectionMode() {
        if (selectionValue == null) return MODE_DEFAULT_IDX;
        switch (selectionValue.get()) {
            case MODE_SINGLE:
                return MODE_SINGLE_IDX;
            case MODE_MIXED:
                return MODE_MIXED_IDX;
            default:
                return MODE_DEFAULT_IDX;
        }
    }
}
