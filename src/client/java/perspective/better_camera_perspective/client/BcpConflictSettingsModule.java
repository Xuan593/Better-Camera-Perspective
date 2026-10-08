package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.api.client.events.RegisterClientSettingsEvent;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * v1.3.1：BBS 设置新增「冲突功能」设置页。
 *
 * 注册设置模块 conflict_features（图标沿用摄像机视角图标），
 * 每个冲突功能绑定一个 ValueString 作为设置页的读写通道；
 * 页面行渲染由 {@code UISettingsLayoutMixin} 注入的自定义行完成：
 *  - 每个功能一行标题 + 一行来源 Mod 按钮（点击即选择）；
 *  - 页末「应用」按钮统一应用全部选择。
 */
@Environment(EnvType.CLIENT)
public class BcpConflictSettingsModule {
    public static final String MODULE_ID = "conflict_features";

    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");

    /** 由 BBS 客户端入口在 RegisterClientSettingsEvent 中调用 */
    public static void register(RegisterClientSettingsEvent event) {
        try {
            BBSMod.setupConfig(
                Icons.FRUSTUM,
                MODULE_ID,
                new File(BBSMod.getSettingsFolder(), MODULE_ID + ".json"),
                builder -> {
                    builder.category("conflicts");
                    for (String featureId : BcpConflictManager.getFeatureIds()) {
                        ValueString value = builder.getString(featureId, "");
                        // 以已有选择初始化（迁移旧全局二选一或历史选择）
                        String chosen = BcpConflictManager.getChosen(featureId);
                        if (chosen != null && value.get().isEmpty()) {
                            value.set(chosen);
                        }
                        BcpConflictManager.bindChoice(featureId, value);
                    }
                }
            );
            LOGGER.info("[BCP] 设置页「冲突功能」注册成功，共 {} 个功能", BcpConflictManager.getFeatureIds().size());
        } catch (Exception e) {
            LOGGER.error("[BCP] 设置页「冲突功能」注册失败", e);
        }
    }
}
