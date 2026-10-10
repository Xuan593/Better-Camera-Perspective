package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.settings.ui.UIValueMap;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * V1.5.2-Alpha：为 {@link BcpModeValue} 注册 BBS 设置页 UI —— 说明文本 + 三个可选按钮（选中即应用）。
 *
 * 布局：
 *  - 按钮上方为 UILabel 说明文本（按钮是用于干什么）；
 *  - 下方为三个并列 UIButton，分别对应候选模式；
 *  - 点击即选中并应用（value 立即写入配置）；
 *  - 选中按钮以黄色背景标识（STATUS_COLOR），未选中为深灰背景（NORMAL_COLOR），一眼区分。
 */
@Environment(EnvType.CLIENT)
public class BcpModeValueUi {
    /** 选中态背景色（黄色标识） */
    public static final int STATUS_COLOR = 0xFFFFC107;
    /** 未选中背景色（深灰） */
    public static final int NORMAL_COLOR = 0xFF2A2A2A;

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

                UIElement buttons = new UIElement();
                buttons.row(2);

                List<UIButton> btnList = new ArrayList<>();
                List<String> modes = value.getModes();
                for (int i = 0; i < modes.size(); i++) {
                    final int idx = i;
                    UIButton btn = new UIButton(L10n.lang(value.getLabelPrefix() + "." + modes.get(i)), b -> {
                        value.set(modes.get(idx));
                        for (int j = 0; j < btnList.size(); j++) {
                            btnList.get(j).color(j == idx ? STATUS_COLOR : NORMAL_COLOR);
                        }
                    });
                    btn.relative(buttons).w(58).h(20);
                    buttons.add(btn);
                    btnList.add(btn);
                }

                // 初始选中态：按当前值高亮对应按钮
                int cur = value.getModeIndex();
                for (int j = 0; j < btnList.size(); j++) {
                    btnList.get(j).color(j == cur ? STATUS_COLOR : NORMAL_COLOR);
                }

                buttons.relative(wrapper).w(1.0F).h(20);
                wrapper.add(buttons);

                wrapper.relative(row).w(1.0F);
                return Collections.singletonList((UIElement) wrapper);
            });
            registered = true;
        } catch (Exception e) {
            LOGGER.error("[BCP] 模式设置 UI 注册失败", e);
        }
    }
}
