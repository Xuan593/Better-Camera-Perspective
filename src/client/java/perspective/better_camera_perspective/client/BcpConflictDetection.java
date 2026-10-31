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
 * 自动检测：只有当对应功能存在多个来源（Cubed + BCP）时，
 * 才显示该组按钮让用户选择优先级。只装 BCP 时自动隐藏对应分组。
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

    /** 自动检测：Cubed mod 是否已加载 */
    public static boolean isCubedLoaded() {
        return FabricLoader.getInstance().isModLoaded("bbsplusplus");
    }

    public static void registerValues(SettingsBuilder builder) {
        if (registered) return;
        try {
            builder.category(CATEGORY);

            boolean cubedLoaded = isCubedLoaded();
            LOGGER.info("[BCP] 冲突检测：Cubed mod 加载状态 = {}", cubedLoaded ? "已加载（存在冲突）" : "未加载（无冲突）");

            // 只有装了 Cubed 才注册对应的优先级选择（存在两个来源才有冲突）
            if (cubedLoaded) {
                povTrackValue = new BcpModeValue(KEY_POV_TRACK, POV_TRACK_CUBED_FIRST,
                    List.of(POV_TRACK_CUBED_FIRST, POV_TRACK_BCP_FIRST, POV_TRACK_CUBED_WITH_BCP), LABEL_POV_TRACK);
                builder.register(povTrackValue);

                povActionsValue = new BcpModeValue(KEY_POV_ACTIONS, POV_ACTIONS_CUBED_FIRST,
                    List.of(POV_ACTIONS_CUBED_FIRST, POV_ACTIONS_BCP_FIRST, POV_ACTIONS_CUBED_WITH_BCP), LABEL_POV_ACTIONS);
                builder.register(povActionsValue);

                disguiseValue = new BcpModeValue(KEY_DISGUISE, DISGUISE_CUBED_FIRST,
                    List.of(DISGUISE_CUBED_FIRST, DISGUISE_BCP_FIRST), LABEL_DISGUISE);
                builder.register(disguiseValue);
            }
            // 没装 Cubed 时：povTrackValue / povActionsValue / disguiseValue 保持 null
            // getXxxMode() 会返回 BCP优先（只有 BCP 一个来源，无需选择）

            registered = true;
        } catch (Exception e) {
            LOGGER.error("[BCP] 设置页「功能冲突检测」注册失败", e);
        }
    }

    /** POV 轨道名翻译模式：0=Cubed优先 1=BCP优先 2=Cubed为主BCP辅助 */
    public static int getPovTrackMode() {
        if (povTrackValue == null) {
            // 没装 Cubed，只有 BCP 一个来源，默认 BCP优先
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
