package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.settings.ui.UISettingsLayout;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import perspective.better_camera_perspective.client.BcpConflictManager;

import java.util.List;

/**
 * v1.3.1：设置页「冲突功能」——页末「应用」按钮行。
 *
 * 点击后把设置页当前全部选择统一应用（BcpConflictManager.applyAll），
 * 对应功能模式随即生效。
 */
@Environment(EnvType.CLIENT)
public class BcpConflictApplyRow implements UISettingsLayout.IValueRow {
    private final ValueString placeholder;

    public BcpConflictApplyRow(ValueString placeholder) {
        this.placeholder = placeholder;
    }

    @Override
    public List<BaseValue> getValues() {
        return List.of(this.placeholder);
    }

    @Override
    public List<UIElement> create(UIElement ui) {
        UIElement row = new UIElement();
        row.h(24);
        UIButton apply = new UIButton(L10n.lang("bbs.ui.camera.conflict.settings_apply"), b -> {
            BcpConflictManager.applyAll();
            b.label = L10n.lang("bbs.ui.camera.conflict.settings_applied");
        });
        apply.relative(row).x(0.5F, -70).w(140).h(24);
        row.add(apply);
        return List.of(row);
    }
}
