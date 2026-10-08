package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.api.client.events.RegisterClientSettingsEvent;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * v1.3.2：BBS 设置新增「剪辑处理设置」页。
 *
 * 注册设置模块 clip_processing（图标沿用摄像机视角图标），
 * 提供剪辑处理的可视化开关（默认全部开启）：
 *  - reorganize_animation：整理剪辑动画（点击「整理剪辑」后剪辑条平滑移动到新层位）；
 *  - clip_names：剪辑条上的名称显示；
 *  - clip_borders：剪辑条边界线；
 *  - clip_colors：剪辑条使用剪辑类型配色。
 *
 * 页面行为由 {@code UIClipsMixin} 与 {@code UIClipRendererMixin} 读取开关实时生效。
 */
@Environment(EnvType.CLIENT)
public class BcpClipProcessing {
    public static final String MODULE_ID = "clip_processing";

    /** 整理剪辑动画（默认开） */
    public static final String KEY_REORGANIZE_ANIMATION = "reorganize_animation";
    /** 剪辑名称显示（默认开） */
    public static final String KEY_CLIP_NAMES = "clip_names";
    /** 剪辑边界线（默认开） */
    public static final String KEY_CLIP_BORDERS = "clip_borders";
    /** 剪辑类型配色（默认开） */
    public static final String KEY_CLIP_COLORS = "clip_colors";

    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");
    private static final Map<String, ValueBoolean> VALUES = new HashMap<>();

    /** 由 BBS 客户端入口在 RegisterClientSettingsEvent 中调用 */
    public static void register(RegisterClientSettingsEvent event) {
        try {
            BBSMod.setupConfig(
                Icons.FRUSTUM,
                MODULE_ID,
                new File(BBSMod.getSettingsFolder(), MODULE_ID + ".json"),
                builder -> {
                    builder.category("clips");
                    VALUES.put(KEY_REORGANIZE_ANIMATION, builder.getBoolean(KEY_REORGANIZE_ANIMATION, true));
                    VALUES.put(KEY_CLIP_NAMES, builder.getBoolean(KEY_CLIP_NAMES, true));
                    VALUES.put(KEY_CLIP_BORDERS, builder.getBoolean(KEY_CLIP_BORDERS, true));
                    VALUES.put(KEY_CLIP_COLORS, builder.getBoolean(KEY_CLIP_COLORS, true));
                }
            );
            LOGGER.info("[BCP] 设置页「剪辑处理设置」注册成功，共 {} 个设置项", VALUES.size());
        } catch (Exception e) {
            LOGGER.error("[BCP] 设置页「剪辑处理设置」注册失败", e);
        }
    }

    /** 读取某开关：未注册或未持久化时按默认（开启）处理 */
    public static boolean isEnabled(String key) {
        ValueBoolean value = VALUES.get(key);
        return value == null || value.get();
    }
}
