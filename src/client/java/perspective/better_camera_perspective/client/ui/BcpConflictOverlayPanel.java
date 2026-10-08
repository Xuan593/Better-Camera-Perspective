package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.function.Consumer;

/**
 * v1.2.1-Alpha（实验版）：剪辑可见性筛选功能冲突选择弹窗（模拟）。
 *
 * 当检测到 BBS Cubed（bbsplusplus）与本插件同时提供剪辑可见性筛选功能时弹出：
 * 说明文字 + 两个模式选项按钮 + 「确定」按钮。
 * 选中某选项后（按钮文本带 ✓ 前缀），点击确定提交。
 * 实验版：无论选择哪个模式，本插件功能都会激活（弹窗仅用于模拟与验证交互）。
 */
@Environment(EnvType.CLIENT)
public class BcpConflictOverlayPanel extends UIOverlayPanel {
    private final UIButton cubedBtn;
    private final UIButton bcpBtn;
    private String selected = null;

    public BcpConflictOverlayPanel(Consumer<String> callback) {
        super(L10n.lang("bbs.ui.camera.conflict.title"));

        // 说明文字
        UILabel message = UI.label(L10n.lang("bbs.ui.camera.conflict.message"));
        message.relative(this.content).xy(8, 6).w(1.0F, -16).h(48);
        message.labelAnchor(0.0F, 0.0F);
        this.content.add(message);

        // 选项按钮（回调引用字段，点击时字段必然已赋值）
        this.cubedBtn = new UIButton(L10n.lang("bbs.ui.camera.conflict.cubed"), b -> this.select("cubed"));
        this.bcpBtn = new UIButton(L10n.lang("bbs.ui.camera.conflict.bcp"), b -> this.select("bcp"));
        this.cubedBtn.relative(this.content).x(0.15F).y(0.5F, -16).w(0.7F).h(24);
        this.bcpBtn.relative(this.content).x(0.15F).y(0.5F, 14).w(0.7F).h(24);
        this.content.add(this.cubedBtn);
        this.content.add(this.bcpBtn);

        // 确定按钮
        UIButton confirm = new UIButton(UIKeys.GENERAL_OK, b -> {
            if (this.selected != null) {
                this.close();
                callback.accept(this.selected);
            }
        });
        confirm.relative(this.content).x(1.0F, -90).y(1.0F, -34).w(80).h(24);
        this.content.add(confirm);
    }

    private void select(String mode) {
        this.selected = mode;
        UIButton current = "cubed".equals(mode) ? this.cubedBtn : this.bcpBtn;
        UIButton other = "cubed".equals(mode) ? this.bcpBtn : this.cubedBtn;
        current.label = IKey.raw("✓ " + current.label.get());
        if (other.label.get().startsWith("✓ ")) {
            other.label = IKey.raw(other.label.get().substring(2));
        }
    }
}
