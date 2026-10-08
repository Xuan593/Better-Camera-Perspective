package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UI;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import perspective.better_camera_perspective.client.BcpConflictManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v1.3.1：「冲突功能」选择窗口（减少崩溃退出几率的第一道墙）。
 *
 * 检测到同一功能有多个 MOD 来源时弹出，与「冲突功能」设置页共用同一布局：
 *  - 逐功能分隔排列，每项列出功能名 + 该功能的所有来源 Mod 按钮；
 *  - 点击来源按钮选中（✓ 前缀），未点击则沿用已保存的选择；
 *  - 底部居中「完成选择，开始应用」：统一应用全部选择并关闭窗口。
 */
@Environment(EnvType.CLIENT)
public class BcpConflictOverlayPanel extends UIOverlayPanel {
    /** 功能 id → 本次会话中用户点选的来源 mod id（null = 沿用已保存） */
    private final Map<String, String> selected = new HashMap<>();
    /** 功能 id → 该功能的来源按钮（刷新高亮用） */
    private final Map<String, List<UIButton>> featureButtons = new HashMap<>();

    public BcpConflictOverlayPanel() {
        super(L10n.lang("bbs.ui.camera.conflict.list_title"));

        // 说明文字
        UILabel message = UI.label(L10n.lang("bbs.ui.camera.conflict.list_message"));
        message.relative(this.content).xy(8, 6).w(1.0F, -16).h(20);
        message.labelAnchor(0.0F, 0.0F);
        message.color(0xFF8B8B8B, true);
        this.content.add(message);

        // 滚动列表：逐功能分隔排列
        UIScrollView scroll = new UIScrollView(ScrollDirection.VERTICAL);
        scroll.scroll.scrollSpeed = 24;
        scroll.relative(this.content).xy(8, 30).w(1.0F, -16).h(1.0F, -66);
        scroll.column().vertical().stretch().scroll().padding(4).height(22);
        this.content.add(scroll);

        List<String> conflicted = BcpConflictManager.getConflictedFeatures();
        boolean first = true;
        for (String featureId : conflicted) {
            if (!first) {
                UIElement divider = new UIElement();
                divider.relative(scroll).w(1.0F).h(8);
                scroll.add(divider);
            }
            first = false;

            // 功能名
            UILabel label = UI.label(BcpConflictManager.getFeatureLabel(featureId));
            label.relative(scroll).w(1.0F).h(16);
            label.labelAnchor(0.0F, 0.5F);
            label.color(0xFFE8C96A, true);
            scroll.add(label);

            // 来源按钮组（同一功能一排）
            List<String> sources = BcpConflictManager.getSources(featureId);
            UIElement row = new UIElement();
            row.relative(scroll).w(1.0F).h(22);
            row.row(4).preferred(0).height(22);
            List<UIButton> buttons = new ArrayList<>();
            float btnW = 1.0F / Math.max(1, sources.size());
            for (String modId : sources) {
                UIButton btn = new UIButton(null, b -> this.select(featureId, modId));
                btn.relative(row).w(btnW).h(22);
                buttons.add(btn);
                row.add(btn);
            }
            this.featureButtons.put(featureId, buttons);
            scroll.add(row);
        }

        this.refreshLabels();

        // 底部居中：完成选择，开始应用
        UIButton apply = new UIButton(L10n.lang("bbs.ui.camera.conflict.apply"), b -> this.apply());
        apply.relative(this.content).x(0.5F, -70).y(1.0F, -34).w(140).h(24).anchor(0.5F, 0.0F);
        this.content.add(apply);
    }

    private void select(String featureId, String modId) {
        this.selected.put(featureId, modId);
        this.refreshLabels();
    }

    private void apply() {
        for (Map.Entry<String, String> e : this.selected.entrySet()) {
            BcpConflictManager.setChosen(e.getKey(), e.getValue());
        }
        this.close();
    }

    /** 刷新所有来源按钮：已选（含已保存）带 ✓ 前缀 */
    private void refreshLabels() {
        for (Map.Entry<String, List<UIButton>> e : this.featureButtons.entrySet()) {
            String featureId = e.getKey();
            String current = this.selected.get(featureId);
            if (current == null) {
                current = BcpConflictManager.getChosen(featureId);
            }
            List<String> sources = BcpConflictManager.getSources(featureId);
            for (int i = 0; i < e.getValue().size(); i++) {
                UIButton btn = e.getValue().get(i);
                String modId = sources.get(i);
                String name = BcpConflictManager.displayName(modId);
                boolean active = modId.equals(current);
                btn.label = IKey.raw((active ? "✓ " : "") + name);
            }
        }
    }
}
