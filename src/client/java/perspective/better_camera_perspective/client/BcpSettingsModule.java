package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.api.client.events.RegisterClientSettingsEvent;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.MapFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 设置页「camera perspective」（v1.2-Alpha 实验性更新）。
 *
 * 在 BBS 设置界面新增一个设置模块（图标沿用摄像机编辑器的视角图标），
 * 用于控制摄像机编辑器右键添加剪辑菜单中各剪辑的可见性：
 * 每个剪辑类型对应一个开关，关闭后该剪辑将不再出现在右键菜单中，
 * 减少过多样式剪辑占用的视觉空间。既有的剪辑数据不受影响。
 */
@Environment(EnvType.CLIENT)
public class BcpSettingsModule {
    public static final String MODULE_ID = "camera_perspective";

    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");
    /** 剪辑类型（Link.toString，如 "bbs:keyframe"）→ 可见性开关。 */
    private static final Map<String, ValueBoolean> CLIP_VISIBILITY = new HashMap<>();

    /**
     * 由 BBS 客户端入口（BBSClientAddonEntry）在 RegisterClientSettingsEvent 中调用，
     * 注册设置模块并收集各剪辑的可见性开关。
     */
    public static void register(RegisterClientSettingsEvent event) {
        try {
            MapFactory<Clip, ClipFactoryData> factory = BBSMod.getFactoryCameraClips();
            if (factory == null) {
                LOGGER.warn("[BCP] 相机剪辑工厂为空，无法注册设置页「camera perspective」");
                return;
            }

            BBSMod.setupConfig(
                Icons.FRUSTUM,
                MODULE_ID,
                new File(BBSMod.getSettingsFolder(), MODULE_ID + ".json"),
                builder -> {
                    builder.category("clips");
                    for (Link key : factory.getKeys()) {
                        CLIP_VISIBILITY.put(key.toString(), builder.getBoolean(key.toString(), true));
                    }
                }
            );
            LOGGER.info("[BCP] 设置页「camera perspective」（剪辑可见性）注册成功，共 {} 个剪辑开关", CLIP_VISIBILITY.size());
        } catch (Exception e) {
            LOGGER.error("[BCP] 设置页「camera perspective」注册失败", e);
        }
    }

    /**
     * 查询剪辑在右键添加菜单中的可见性（未配置的剪辑默认可见）。
     */
    public static boolean isClipVisible(Link type) {
        ValueBoolean value = CLIP_VISIBILITY.get(type.toString());
        return value == null || value.get();
    }
}
