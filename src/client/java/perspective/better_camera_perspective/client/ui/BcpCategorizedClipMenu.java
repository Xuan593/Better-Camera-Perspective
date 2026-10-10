package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * v1.5-Alpha：预新建剪辑可视化「覆盖」模式菜单 —— 参考 FS2.8 的三列分类布局。
 *
 * 顶部搜索框 + 「覆盖 / 修改器 / 杂项」三列；每列分类标题 + 剪辑项；
 * 搜索输入时按剪辑显示名过滤并即时重建三列。
 */
@Environment(EnvType.CLIENT)
public class BcpCategorizedClipMenu extends UIContextMenu {
    public static final int CELL_WIDTH = 66;
    public static final int CELL_HEIGHT = 22;
    public static final int MAX_ROWS = 10;
    public static final int PADDING = 4;
    public static final int SEARCH_H = 18;
    public static final int TITLE_H = 16;
    public static final int SCROLLBAR_W = 6;

    private static final int[] TITLE_COLORS = { 0xFFB74D, 0xFF4DB6AC, 0xFFB0BEC5 };

    private final List<ContextAction> actions;
    private final int[] categories;
    private final UITextbox search;
    private final List<UIElement> columns = new ArrayList<>();
    private final int[] rowsPerColumn = new int[3];
    private String filter = "";

    public BcpCategorizedClipMenu(List<ContextAction> actions, int[] categories) {
        this.actions = actions;
        this.categories = categories;

        this.search = new UITextbox((text) -> {
            this.filter = text == null ? "" : text.trim().toLowerCase();
            this.buildColumns();
        });
        this.search.placeholder(L10n.lang("bbs.ui.camera.search"));
        this.search.delayedInput();
        this.search.background(true);
        this.add(this.search);

        this.buildColumns();
    }

    private void buildColumns() {
        for (UIElement column : this.columns) {
            this.remove(column);
        }
        this.columns.clear();

        List<ContextAction>[] grouped = new List[]{ new ArrayList<>(), new ArrayList<>(), new ArrayList<>() };
        for (int i = 0; i < this.actions.size(); i++) {
            ContextAction action = this.actions.get(i);
            if (!this.filter.isEmpty() && action.label != null
                && !action.label.get().toLowerCase().contains(this.filter)) {
                continue;
            }
            int cat = this.categories[i];
            if (cat < 0 || cat > 2) {
                cat = ClipCategory.MISC;
            }
            grouped[cat].add(action);
        }

        for (int c = 0; c < 3; c++) {
            UIElement column = new UIElement();
            column.column(PADDING).vertical();

            UILabel title = new UILabel(L10n.lang("bbs.ui.camera.clip_category."
                + (c == ClipCategory.COVER ? "cover" : c == ClipCategory.MODIFIER ? "modifier" : "misc")),
                TITLE_COLORS[c]);
            title.relative(column).w(1.0F).h(TITLE_H);
            column.add(title);

            List<ContextAction> list = grouped[c];
            int rows = Math.min(list.size(), MAX_ROWS);
            this.rowsPerColumn[c] = rows;
            for (int r = 0; r < rows; r++) {
                GridActionCell cell = new GridActionCell(list.get(r));
                cell.relative(column).w(1.0F).h(CELL_HEIGHT);
                column.add(cell);
            }
            column.relative(this).x(c * (CELL_WIDTH + PADDING) + PADDING).y(PADDING + SEARCH_H)
                .w(CELL_WIDTH).h(TITLE_H + PADDING + rows * CELL_HEIGHT);
            this.add(column);
            this.columns.add(column);
        }
    }

    @Override
    public boolean isEmpty() {
        return this.actions.isEmpty();
    }

    private int menuWidth() {
        return 3 * CELL_WIDTH + 4 * PADDING + SCROLLBAR_W;
    }

    private int menuHeight() {
        int maxRows = 0;
        for (int rows : this.rowsPerColumn) {
            maxRows = Math.max(maxRows, rows);
        }
        return PADDING + SEARCH_H + PADDING + TITLE_H + PADDING + maxRows * CELL_HEIGHT + PADDING;
    }

    @Override
    public void setMouse(UIContext context) {
        this.set(context.mouseX(), context.mouseY(), this.menuWidth(), this.menuHeight());
        this.bounds(context.menu.overlay, 5);
    }
}
