package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.ui.UISettingsLayout;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import perspective.better_camera_perspective.client.BcpConflictManager;

import java.util.ArrayList;
import java.util.List;

/**
 * v1.3.1：设置页「冲突功能」——单个冲突功能的选择行。
 *
 * 渲染两行：
 *  - 功能名（标题行）；
 *  - 该功能全部来源 Mod 按钮（同一排，点击选中，✓ 前缀标记当前模式）。
 * 点击即写入设置值并即时生效（BcpConflictManager.setChosen）。
 */
@Environment(EnvType.CLIENT)
public class BcpConflictChoiceRow implements UISettingsLayout.IValueRow {
    private final String featureId;
    private final ValueString value;
    /** 来源按钮（点击后刷新 ✓ 高亮） */
    private final List<UIButton> buttons = new ArrayList<>();

    public BcpConflictChoiceRow(String featureId, ValueString value) {
        this.featureId = featureId;
        this.value = value;
    }

    @Override
    public List<BaseValue> getValues() {
        return List.of(this.value);
    }

    @Override
    public List<UIElement> create(UIElement ui) {
        List<UIElement> elements = new ArrayList<>();

        // 功能名
        UILabel label = UI.label(BcpConflictManager.getFeatureLabel(this.featureId), 0);
        label.labelAnchor(0.0F, 0.5F);
        label.color(0xFFE8C96A, true);
        UIElement labelRow = new UIElement();
        labelRow.row(0).preferred(0).height(20);
        labelRow.add(label);
        elements.add(labelRow);

        // 来源按钮组（同一排）
        List<String> sources = BcpConflictManager.getSources(this.featureId);
        UIElement row = new UIElement();
        row.row(4).preferred(0).height(22);
        row.w(1.0F);
        this.buttons.clear();
        float btnW = 1.0F / Math.max(1, sources.size());
        for (String modId : sources) {
            UIButton btn = new UIButton(null, b -> this.select(modId));
            btn.relative(row).w(btnW).h(22);
            this.buttons.add(btn);
            row.add(btn);
        }
        elements.add(row);

        this.refresh();
        return elements;
    }

    private void select(String modId) {
        this.value.set(modId);
        BcpConflictManager.setChosen(this.featureId, modId);
        this.refresh();
    }

    private void refresh() {
        String current = BcpConflictManager.getChosen(this.featureId);
        List<String> sources = BcpConflictManager.getSources(this.featureId);
        for (int i = 0; i < this.buttons.size(); i++) {
            String modId = sources.get(i);
            String name = BcpConflictManager.displayName(modId);
            boolean active = modId.equals(current);
            this.buttons.get(i).label = IKey.raw((active ? "✓ " : "") + name);
        }
    }
}
