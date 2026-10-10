package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.settings.ui.UIValueMap;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * v1.5-Alpha：为 {@link BcpModeValue} 注册 BBS 设置页 UI —— 循环按钮。
 *
 * 点击在候选模式间循环（默认/单体/混合 或 原始/覆盖/混合），
 * 当前模式高亮显示；回调时 UICirculate 已先更新内部值，getValue() 即新索引。
 */
@Environment(EnvType.CLIENT)
public class BcpModeValueUi {
    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");
    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        try {
            UIValueMap.register(BcpModeValue.class, (value, row) -> {
                UICirculate button = new UICirculate((b) -> {
                    value.set(value.getModes().get(b.getValue()));
                });
                for (String mode : value.getModes()) {
                    button.addLabel(L10n.lang(value.getLabelPrefix() + "." + mode));
                }
                button.setValue(value.getModeIndex());
                button.relative(row).w(120).h(20);
                return Collections.singletonList((UIElement) button);
            });
            registered = true;
        } catch (Exception e) {
            LOGGER.error("[BCP] 模式设置 UI 注册失败", e);
        }
    }
}
