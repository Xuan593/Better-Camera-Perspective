package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.settings.SettingsBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * V1.7.14-Alpha：设置页「功能冲突检测」分类。
 *
 * 自动检测：按功能独立统计来源数量。
 * - BCP 自身固定算 1 个来源
 * - 检测到其他插件也实现了同一功能就 +1
 * - 来源数 >= 2 时才显示该组优先级选择按钮
 *
 * 扩展说明：未来发现新插件实现了同一功能时，
 * 只需在对应 detectXxxSources() 方法里加一行检测即可，
 * 无需修改设置页注册逻辑。
 */
@Environment(EnvType.CLIENT)
public class BcpConflictDetection {
    public static final String CATEGORY = "conflict_detection";

    // POV 轨道名翻译
    public static final String KEY_POV_TRACK = "pov_track_translation";
    public static final String POV_TRACK_CUBED_FIRST = "cubed_first";
    public static final String POV_TRACK_BCP_FIRST = "bcp_first";
    public static final String POV_TRACK_CUBED_WITH_BCP = "cubed_with_bcp_supplement";

    public static final int POV_TRACK_CUBED_FIRST_IDX = 0;
    public static final int POV_TRACK_BCP_FIRST_IDX = 1;
    public static final int POV_TRACK_CUBED_WITH_BCP_IDX = 2;

    // POV Actions 属性面板翻译
    public static final String KEY_POV_ACTIONS = "pov_actions_translation";
    public static final String POV_ACTIONS_CUBED_FIRST = "cubed_first";
    public static final String POV_ACTIONS_BCP_FIRST = "bcp_first";
    public static final String POV_ACTIONS_CUBED_WITH_BCP = "cubed_with_bcp_supplement";

    public static final int POV_ACTIONS_CUBED_FIRST_IDX = 0;
    public static final int POV_ACTIONS_BCP_FIRST_IDX = 1;
    public static final int POV_ACTIONS_CUBED_WITH_BCP_IDX = 2;

    // 伪装选取（二选一，无主辅模式）
    public static final String KEY_DISGUISE = "disguise_selection";
    public static final String DISGUISE_CUBED_FIRST = "cubed_first";
    public static final String DISGUISE_BCP_FIRST = "bcp_first";

    public static final int DISGUISE_CUBED_FIRST_IDX = 0;
    public static final int DISGUISE_BCP_FIRST_IDX = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");
    private static final String LABEL_POV_TRACK = "conflict_features.config.conflict_detection.pov_track_translation";
    private static final String LABEL_POV_ACTIONS = "conflict_features.config.conflict_detection.pov_actions_translation";
    private static final String LABEL_DISGUISE = "conflict_features.config.conflict_detection.disguise_selection";

    private static BcpModeValue povTrackValue;
    private static BcpModeValue povActionsValue;
    private static BcpModeValue disguiseValue;
    private static boolean registered = false;

    /**
     * 检测 POV 轨道名翻译的来源数量。
     * BCP 自身 = 1，其他插件每检测到一个实现就 +1。
     * 未来扩展：在此处加新插件的检测条件即可。
     */
    private static int detectPovTrackSources() {
        int count = 1; // BCP 自身
        if (FabricLoader.getInstance().isModLoaded("bbsplusplus")) count++; // Cubed
        // 未来：if (其他 mod) count++;
        return count;
    }

    /**
     * 检测 POV Actions 属性面板翻译的来源数量。
     */
    private static int detectPovActionsSources() {
        int count = 1; // BCP 自身
        if (FabricLoader.getInstance().isModLoaded("bbsplusplus")) count++; // Cubed
        // 未来：if (其他 mod) count++;
        return count;
    }

    /**
     * 检测伪装选取功能的来源数量。
     */
    private static int detectDisguiseSources() {
        int count = 1; // BCP 自身
        if (FabricLoader.getInstance().isModLoaded("bbsplusplus")) count++; // Cubed
        // 未来：if (其他 mod) count++;
        return count;
    }

    public static void registerValues(SettingsBuilder builder) {
        if (registered) return;
        try {
            builder.category(CATEGORY);

            int povTrackCount = detectPovTrackSources();
            int povActionsCount = detectPovActionsSources();
            int disguiseCount = detectDisguiseSources();

            LOGGER.info("[BCP] 冲突检测：POV轨道名={}来源, POV Actions面板={}来源, 伪装选取={}来源",
                povTrackCount, povActionsCount, disguiseCount);

            // 只有来源数 >= 2 才注册对应的优先级选择（存在冲突才有意义）
            if (povTrackCount >= 2) {
                povTrackValue = new BcpModeValue(KEY_POV_TRACK, POV_TRACK_CUBED_FIRST,
                    List.of(POV_TRACK_CUBED_FIRST, POV_TRACK_BCP_FIRST, POV_TRACK_CUBED_WITH_BCP), LABEL_POV_TRACK);
                builder.register(povTrackValue);
            }

            if (povActionsCount >= 2) {
                povActionsValue = new BcpModeValue(KEY_POV_ACTIONS, POV_ACTIONS_CUBED_FIRST,
                    List.of(POV_ACTIONS_CUBED_FIRST, POV_ACTIONS_BCP_FIRST, POV_ACTIONS_CUBED_WITH_BCP), LABEL_POV_ACTIONS);
                builder.register(povActionsValue);
            }

            if (disguiseCount >= 2) {
                disguiseValue = new BcpModeValue(KEY_DISGUISE, DISGUISE_CUBED_FIRST,
                    List.of(DISGUISE_CUBED_FIRST, DISGUISE_BCP_FIRST), LABEL_DISGUISE);
                builder.register(disguiseValue);
            }
            // 来源数 < 2 时对应值保持 null，getXxxMode() 自动返回 BCP优先

            registered = true;
        } catch (Exception e) {
            LOGGER.error("[BCP] 设置页「功能冲突检测」注册失败", e);
        }
    }

    /** POV 轨道名翻译模式：0=Cubed优先 1=BCP优先 2=Cubed为主BCP辅助 */
    public static int getPovTrackMode() {
        if (povTrackValue == null) {
            return POV_TRACK_BCP_FIRST_IDX;
        }
        switch (povTrackValue.get()) {
            case POV_TRACK_BCP_FIRST:
                return POV_TRACK_BCP_FIRST_IDX;
            case POV_TRACK_CUBED_WITH_BCP:
                return POV_TRACK_CUBED_WITH_BCP_IDX;
            default:
                return POV_TRACK_CUBED_FIRST_IDX;
        }
    }

    /** POV Actions 属性面板翻译模式：0=Cubed优先 1=BCP优先 2=Cubed为主BCP辅助 */
    public static int getPovActionsMode() {
        if (povActionsValue == null) {
            return POV_ACTIONS_BCP_FIRST_IDX;
        }
        switch (povActionsValue.get()) {
            case POV_ACTIONS_BCP_FIRST:
                return POV_ACTIONS_BCP_FIRST_IDX;
            case POV_ACTIONS_CUBED_WITH_BCP:
                return POV_ACTIONS_CUBED_WITH_BCP_IDX;
            default:
                return POV_ACTIONS_CUBED_FIRST_IDX;
        }
    }

    /** 伪装选取模式：0=Cubed优先 1=BCP优先 */
    public static int getDisguiseMode() {
        if (disguiseValue == null) {
            return DISGUISE_BCP_FIRST_IDX;
        }
        switch (disguiseValue.get()) {
            case DISGUISE_BCP_FIRST:
                return DISGUISE_BCP_FIRST_IDX;
            default:
                return DISGUISE_CUBED_FIRST_IDX;
        }
    }
}
