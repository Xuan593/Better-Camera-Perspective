package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.settings.ui.UIValueMap;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * V1.5.1-Alpha：为 {@link BcpModeValue} 注册 BBS 设置页 UI —— 说明文本 + 黄色循环按钮。
 *
 * 布局（参考早期「冲突检测」的「文本说明 + 按钮」样式）：
 *  - 按钮上方为 UILabel 说明文本（按钮是用于干什么）；
 *  - 下方为 UICirculate 循环按钮，当前模式以黄色标识状态（color 0xFFFFC107）；
 *  - 点击在候选模式间循环，回调时 UICirculate 已先更新内部值，getValue() 即新索引。
 */
@Environment(EnvType.CLIENT)
public class BcpModeValueUi {
    public static final int STATUS_COLOR = 0xFFFFC107;

    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");
    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        try {
            UIValueMap.register(BcpModeValue.class, (value, row) -> {
                UIElement wrapper = new UIElement();
                wrapper.column(2).vertical();

                UILabel caption = new UILabel(L10n.lang(value.getLabelPrefix()));
                caption.relative(wrapper).w(1.0F).h(12);
                wrapper.add(caption);

                UICirculate button = new UICirculate((b) -> {
                    value.set(value.getModes().get(b.getValue()));
                });
                for (String mode : value.getModes()) {
                    button.addLabel(L10n.lang(value.getLabelPrefix() + "." + mode));
                }
                button.setValue(value.getModeIndex());
                button.color(STATUS_COLOR);
                button.relative(wrapper).w(120).h(20);
                wrapper.add(button);

                wrapper.relative(row).w(1.0F);
                return Collections.singletonList((UIElement) wrapper);
            });
            registered = true;
        } catch (Exception e) {
            LOGGER.error("[BCP] 模式设置 UI 注册失败", e);
        }
    }
}
