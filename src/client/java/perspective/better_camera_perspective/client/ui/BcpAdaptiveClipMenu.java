package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;

/**
 * v1.3：自适应多列右键剪辑菜单。
 *
 * 取代 BBS 一长竖串右键菜单：每列最多 10 个剪辑，多出的自动排到下一列，
 * 各列数量尽量平均（列优先：前几列多 1 个）。
 * 列数 = 剪辑数量 / 10 四舍五入（最小 1 列）；若平均后每列仍超过 10 个则加 1 列。
 * 仍为右键菜单形态（跟随鼠标弹出、外部点击 / ESC 关闭）。
 */
@Environment(EnvType.CLIENT)
public class BcpAdaptiveClipMenu extends UIContextMenu {
    public static final int CELL_WIDTH = 132;
    public static final int CELL_HEIGHT = 22;
    public static final int MAX_PER_COLUMN = 10;
    public static final int PADDING = 4;

    private final UIElement columns;
    private final int columnCount;
    private final int rowCount;
    private final int actionCount;

    public BcpAdaptiveClipMenu(List<ContextAction> actions) {
        this.actionCount = actions.size();
        int k = Math.max(1, Math.round(actions.size() / (float) MAX_PER_COLUMN));
        while (k > 1 && (actions.size() + k - 1) / k > MAX_PER_COLUMN) {
            k++;
        }
        this.columnCount = k;
        int base = actions.size() / this.columnCount;
        int extra = actions.size() % this.columnCount;
        this.rowCount = base + (extra > 0 ? 1 : 0);

        this.columns = new UIElement();
        this.columns.relative(this).wh(1.0F, 1.0F);
        this.columns.row(PADDING);

        int index = 0;
        for (int c = 0; c < this.columnCount; c++) {
            int count = base + (c < extra ? 1 : 0);
            UIElement column = new UIElement();
            column.column(PADDING);
            for (int i = 0; i < count; i++) {
                GridActionCell cell = new GridActionCell(actions.get(index++));
                cell.relative(column).w(1.0F).h(CELL_HEIGHT);
                column.add(cell);
            }
            this.columns.add(column);
        }

        this.add(this.columns);
    }

    @Override
    public boolean isEmpty() {
        return this.actionCount == 0;
    }

    @Override
    public void setMouse(UIContext context) {
        int w = this.columnCount * CELL_WIDTH + PADDING * 2;
        int h = this.rowCount * CELL_HEIGHT + PADDING * 2;
        this.set(context.mouseX(), context.mouseY(), w, h);
        this.bounds(context.menu.overlay, 5);
    }
}
