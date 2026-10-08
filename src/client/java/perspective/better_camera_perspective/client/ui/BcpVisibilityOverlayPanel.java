package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.resizers.layout.GridResizer;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.MapFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import perspective.better_camera_perspective.client.BcpVisibility;

/**
 * v1.2.1-Alpha：「可视性设置」面板（overlay）。
 *
 * 从摄像机编辑器右键添加剪辑菜单中的「可视性设置」项打开。
 * 每个剪辑一行开关；列表按「列数」开关以多列网格排列（默认 2 列，1/2/3 循环切换），
 * 剪辑多时不再是一长竖串，界面更紧凑。
 * 调整即时生效并写盘；底部「返回」关闭面板。
 * 进入编辑器时会恢复全开，再次打开本面板会重新应用用户保存的筛选。
 */
@Environment(EnvType.CLIENT)
public class BcpVisibilityOverlayPanel extends UIOverlayPanel {
    private static final int[] COLUMNS = {1, 2, 3};

    private final UIScrollView scroll;
    private final UIButton columnsButton;
    private int columnIndex = 1; // 默认 2 列

    public BcpVisibilityOverlayPanel() {
        super(L10n.lang("bbs.ui.camera.visibility_settings"));

        // 列数循环开关（1 → 2 → 3 → 1）
        this.columnsButton = new UIButton(
            L10n.lang("bbs.ui.camera.panel.columns").format(COLUMNS[this.columnIndex]),
            b -> {
                this.columnIndex = (this.columnIndex + 1) % COLUMNS.length;
                b.label = L10n.lang("bbs.ui.camera.panel.columns").format(COLUMNS[this.columnIndex]);
                this.rebuild();
            }
        );
        this.columnsButton.relative(this.content).xy(0, 0).wh(90, 20);
        this.columnsButton.tooltip(L10n.lang("bbs.ui.camera.panel.columns.tooltip"));
        this.content.add(this.columnsButton);

        // 剪辑列表（多列网格 + 可滚动）：网格容器挂在 scrollView 上
        this.scroll = UI.scrollView(4, 6);
        this.scroll.relative(this.content).xy(0, 24).w(1.0F).h(1.0F, -52);
        this.content.add(this.scroll);

        // 返回按钮
        UIButton back = new UIButton(L10n.lang("bbs.ui.camera.panels.back"), b -> this.close());
        back.relative(this.content).x(1.0F, -90).y(1.0F, -26).w(80).h(20);
        this.content.add(back);

        this.rebuild();
    }

    /** 重建列表（初始构建 / 切换列数时） */
    private void rebuild() {
        this.scroll.removeAll();

        UIElement grid = new UIElement();
        grid.relative(this.scroll).w(1.0F);
        GridResizer.apply(grid, 4).items(COLUMNS[this.columnIndex]);

        MapFactory<Clip, ClipFactoryData> factory = BBSMod.getFactoryCameraClips();
        if (factory != null) {
            for (Link key : factory.getKeys()) {
                UIToggle toggle = new UIToggle(
                    UIKeys.C_CLIP.get(key),
                    BcpVisibility.getVisible(key),
                    t -> BcpVisibility.setVisible(key, t.getValue())
                );
                toggle.relative(grid).w(1.0F).h(20);
                grid.add(toggle);
            }
        }

        this.scroll.add(grid);
        this.scroll.resize();
    }
}
