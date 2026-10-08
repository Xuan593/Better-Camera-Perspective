package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * v1.3.2：「剪辑处理设置」设置项。
 *
 * 属于「摄像机编辑设置」模块（conflict_features）下的第二个分类（clip_processing），
 * 位于「冲突功能」分类下方（白圈内容迁移到红圈后归入同一模块）。
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
    /** 在「摄像机编辑设置」模块内的分类 id（位于「冲突功能」分类下方） */
    public static final String CATEGORY_ID = "clip_processing";

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
    private static boolean registered = false;

    /** 由 BcpConflictSettingsModule 在模块 builder 中调用：注册「剪辑处理设置」分类 */
    public static void registerValues(SettingsBuilder builder) {
        if (registered) {
            return;
        }
        registered = true;
        try {
            builder.category(CATEGORY_ID);
            VALUES.put(KEY_REORGANIZE_ANIMATION, builder.getBoolean(KEY_REORGANIZE_ANIMATION, true));
            VALUES.put(KEY_CLIP_NAMES, builder.getBoolean(KEY_CLIP_NAMES, true));
            VALUES.put(KEY_CLIP_BORDERS, builder.getBoolean(KEY_CLIP_BORDERS, true));
            VALUES.put(KEY_CLIP_COLORS, builder.getBoolean(KEY_CLIP_COLORS, true));
            LOGGER.info("[BCP] 设置页「剪辑处理设置」分类注册成功（共 {} 个设置项）", VALUES.size());
        } catch (Exception e) {
            LOGGER.error("[BCP] 设置页「剪辑处理设置」分类注册失败", e);
        }
    }

    /** 读取某开关：未注册或未持久化时按默认（开启）处理 */
    public static boolean isEnabled(String key) {
        ValueBoolean value = VALUES.get(key);
        return value == null || value.get();
    }
}
