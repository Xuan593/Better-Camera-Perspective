package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.resizers.layout.GridResizer;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.MapFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import perspective.better_camera_perspective.client.BcpVisibility;

import java.util.function.Consumer;

/**
 * v1.2.2-Alpha：摄像机编辑器右键添加剪辑 —— 多列网格选择面板。
 *
 * 取代 BBS 原一长竖串的右键剪辑菜单：整体仿「可视性设置」面板，
 * 剪辑以多列网格排列（默认 2 列，列数开关 1/2/3 循环切换），
 * 顶部提供「可视性设置」入口（原菜单顶部条目并入面板顶栏）。
 * 点击某个剪辑立即以右键位置添加该剪辑并关闭面板。
 */
@Environment(EnvType.CLIENT)
public class BcpClipGridPanel extends UIOverlayPanel {
    private static final int[] COLUMNS = {1, 2, 3};

    private final UIContext context;
    private final Consumer<Link> onPick;
    private final UIScrollView scroll;
    private final UIButton columnsButton;
    private int columnIndex = 1; // 默认 2 列

    public BcpClipGridPanel(UIContext context, Consumer<Link> onPick) {
        super(L10n.lang("bbs.ui.camera.clip_grid_title"));
        this.context = context;
        this.onPick = onPick;

        // 顶栏：可视性设置入口（左侧）
        UIButton visibility = new UIButton(
            L10n.lang("bbs.ui.camera.visibility_settings"),
            b -> BcpVisibility.openVisibilityPanel(this.context)
        );
        visibility.relative(this.content).xy(0, 0).wh(130, 20);
        this.content.add(visibility);

        // 顶栏：列数循环开关（右侧）
        this.columnsButton = new UIButton(
            L10n.lang("bbs.ui.camera.panel.columns").format(COLUMNS[this.columnIndex]),
            b -> {
                this.columnIndex = (this.columnIndex + 1) % COLUMNS.length;
                b.label = L10n.lang("bbs.ui.camera.panel.columns").format(COLUMNS[this.columnIndex]);
                this.rebuild();
            }
        );
        this.columnsButton.relative(this.content).x(1.0F, -90).y(0).wh(90, 20);
        this.columnsButton.tooltip(L10n.lang("bbs.ui.camera.panel.columns.tooltip"));
        this.content.add(this.columnsButton);

        // 剪辑网格（可滚动）
        this.scroll = UI.scrollView(4, 6);
        this.scroll.relative(this.content).xy(0, 24).w(1.0F).h(1.0F, -52);
        this.content.add(this.scroll);

        // 返回（取消添加）
        UIButton back = new UIButton(L10n.lang("bbs.ui.camera.panels.back"), b -> this.close());
        back.relative(this.content).x(1.0F, -90).y(1.0F, -26).w(80).h(20);
        this.content.add(back);

        this.rebuild();
    }

    /** 重建网格（初始构建 / 切换列数时） */
    private void rebuild() {
        this.scroll.removeAll();

        UIElement grid = new UIElement();
        grid.relative(this.scroll).w(1.0F);
        GridResizer.apply(grid, 4).items(COLUMNS[this.columnIndex]);

        MapFactory<Clip, ClipFactoryData> factory = BBSMod.getFactoryCameraClips();
        if (factory != null) {
            for (Link key : factory.getKeys()) {
                if (!BcpVisibility.isClipVisible(key)) {
                    continue;
                }
                ClipFactoryData data = factory.getData(key);
                UIButton button = new UIButton(UIKeys.C_CLIP.get(key), b -> {
                    this.close();
                    this.onPick.accept(key);
                });
                button.relative(grid).w(1.0F).h(20);
                if (data.color != 0) {
                    button.color(data.color);
                }
                grid.add(button);
            }
        }

        this.scroll.add(grid);
        this.scroll.resize();
    }
}
