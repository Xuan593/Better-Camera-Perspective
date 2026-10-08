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
 * 取代 BBS 一长竖串右键菜单：每列最多 10 个剪辑（顺序填充，取消平均分配），
 * 列数最多 5 列（「可视性设置」占 1 格 → 理论可容纳 49 个剪辑）。
 * 仍为右键菜单形态（跟随鼠标弹出、外部点击 / ESC 关闭）。
 */
@Environment(EnvType.CLIENT)
public class BcpAdaptiveClipMenu extends UIContextMenu {
    public static final int CELL_WIDTH = 132;
    public static final int CELL_HEIGHT = 22;
    public static final int MAX_PER_COLUMN = 10;
    public static final int MAX_COLUMNS = 5;
    public static final int PADDING = 4;

    private final UIElement columns;
    private final int columnCount;
    private final int rowCount;
    private final int actionCount;

    public BcpAdaptiveClipMenu(List<ContextAction> actions) {
        this.actionCount = actions.size();
        // 顺序填充：每列最多 MAX_PER_COLUMN 个，列数最多 MAX_COLUMNS（取消平均分配）
        int k = (actions.size() + MAX_PER_COLUMN - 1) / MAX_PER_COLUMN;
        this.columnCount = Math.min(k, MAX_COLUMNS);

        this.columns = new UIElement();
        this.columns.relative(this).wh(1.0F, 1.0F);
        this.columns.row(PADDING);

        int index = 0;
        int rows = 0;
        for (int c = 0; c < this.columnCount; c++) {
            int count = Math.min(MAX_PER_COLUMN, actions.size() - index);
            if (count <= 0) {
                break;
            }
            rows = Math.max(rows, count);
            UIElement column = new UIElement();
            column.column(PADDING).vertical();
            for (int i = 0; i < count; i++) {
                GridActionCell cell = new GridActionCell(actions.get(index++));
                cell.relative(column).w(1.0F).h(CELL_HEIGHT);
                column.add(cell);
            }
            this.columns.add(column);
        }
        this.rowCount = rows;

        this.add(this.columns);
    }

    @Override
    public boolean isEmpty() {
        return this.actionCount == 0;
    }

    @Override
    public void setMouse(UIContext context) {
        // 高度/宽度需包含单元格之间的间距（(n-1)*PADDING）+ 外边距（2*PADDING），
        // 否则每列第 9、10 个会溢出列容器高度，被 ColumnResizer 自动换到下一列造成重叠
        int w = this.columnCount * CELL_WIDTH + (this.columnCount + 1) * PADDING;
        int h = this.rowCount * CELL_HEIGHT + (this.rowCount + 1) * PADDING;
        this.set(context.mouseX(), context.mouseY(), w, h);
        this.bounds(context.menu.overlay, 5);
    }
}
