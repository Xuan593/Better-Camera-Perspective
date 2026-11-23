package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import perspective.better_camera_perspective.client.clips.MultiTrackerClientClip;
import perspective.better_camera_perspective.data.XyzFrameData;

import java.util.List;
import java.util.Locale;

/**
 * V1.7.22-Alpha · P1：多样追踪「三子窗」曲线面板。
 *
 * <p>每个 XYZ 轨道（X / Y / Z）的三条数值曲线纵向排成三个子窗：</p>
 * <ul>
 *   <li>子窗 1 · 游动·绝对（红 {@code #FF3B30}）—— 只写 {@code roamValue}，</li>
 *   <li>子窗 2 · 追踪·相对（浅蓝 {@code #4FC3F7}）—— 与绝对窗互转同步，</li>
 *   <li>子窗 3 · 追踪·绝对（深蓝 {@code #FF0040FF}，用户指定）。</li>
 * </ul>
 *
 * <p>交互口径：</li>
 * <ul>
 *   <li>三窗各自独立的 Y 轴缩放（滚轮悬停缩放，右键分隔条恢复等高），互不影响；</li>
 *   <li>三窗高度由两条可拖分隔条控制；</li>
 *   <li>拖动关键帧只改本窗所属数值；追踪两窗按当前锚点互相换算（世界坐标保持一致）。</li>
 * </ul>
 *
 * <p>本面板自绘曲线，不经过 BBS / posecurve 的 {@code UIKeyframeGraph}，
 * 因此不会被 posecurve 的单轴标量逻辑污染（配合工厂的 P0 垫片）。</p>
 */
@Environment(EnvType.CLIENT)
public class UIXyzTripleCurvePanel extends UIElement {
    /** 游动·绝对：红。 */
    public static final int COLOR_ROAM = 0xFFFF3B30;
    /** 追踪·相对：浅蓝。 */
    public static final int COLOR_TRACK_REL = 0xFF4FC3F7;
    /** 追踪·绝对：深蓝（用户指定 #FF0040FF）。 */
    public static final int COLOR_TRACK_ABS = 0xFF0040FF;

    private static final int[] COLORS = {COLOR_ROAM, COLOR_TRACK_REL, COLOR_TRACK_ABS};
    private static final String[] LANE_NAMES = {"游动·绝对", "追踪·相对", "追踪·绝对"};
    private static final String[] AXIS_NAMES = {"X", "Y", "Z"};

    private static final int HEADER_H = 22;
    private static final int RULER_H = 16;
    private static final int LANE_HEAD = 14;
    private static final int SPLITTER_H = 5;
    private static final int LABEL_W = 62;
    private static final int RIGHT_PAD = 10;
    private static final int PAD_Y = 4;
    /** 曲线采样步进（像素）：越小越顺滑，越大越省。 */
    private static final int CURVE_STEP = 2;

    private MultiTrackerClientClip clip;
    private final IUIClipsDelegate editor;
    private final Runnable openAllTracks;

    /** 当前编辑轨道：0=X / 1=Y / 2=Z。 */
    private int axis;

    /** 三个子窗的高度权重（仅用于分隔条拖动）。 */
    private final float[] weights = {1.0F, 1.0F, 1.0F};

    /** 每个子窗的 Y 轴视图范围（互相独立）。 */
    private final double[] viewMin = new double[3];
    private final double[] viewMax = new double[3];

    private final Area body = new Area();
    private final Area ruler = new Area();
    private final Area[] lanes = {new Area(), new Area(), new Area()};
    private final Area[] tabs = {new Area(), new Area(), new Area()};
    private final Area allTracks = new Area();

    /** 选中关键帧与所在子窗。 */
    private Keyframe<XyzFrameData> selected;
    private int selectedLane = -1;

    /** 拖动状态。 */
    private int dragSplitter = -1;
    private int dragStartY;
    private final float[] dragStartWeights = new float[3];
    private int dragLane = -1;
    private int lastDragY;
    private int dragStartX;
    private int lastDragX;

    /** 分隔条双击回等高。 */
    private long lastSplitterClick;
    private int lastSplitterIndex = -1;

    /** 刻度步进（tick）。 */
    private float tickStep = 20.0F;

    public UIXyzTripleCurvePanel(MultiTrackerClientClip clip, IUIClipsDelegate editor, Runnable openAllTracks) {
        this.clip = clip;
        this.editor = editor;
        this.openAllTracks = openAllTracks;

        for (int i = 0; i < 3; i++) {
            this.viewMin[i] = -1.0D;
            this.viewMax[i] = 1.0D;
        }

        this.noCulling();
        this.fitAll();
    }

    public void setClip(MultiTrackerClientClip clip) {
        if (this.clip != clip) {
            this.clip = clip;
            this.selected = null;
            this.selectedLane = -1;
            this.fitAll();
        } else if (this.clip == null) {
            this.clip = clip;
        }
    }

    /** 打开面板时调用：恢复等高与自动 Y 范围。 */
    public void resetView() {
        for (int i = 0; i < 3; i++) {
            this.weights[i] = 1.0F;
        }
        this.fitAll();
        this.selected = null;
        this.selectedLane = -1;
    }

    /** 切换到某条轨道（0=X / 1=Y / 2=Z）。 */
    public void setAxis(int axis) {
        if (this.axis != axis) {
            this.axis = axis;
            this.selected = null;
            this.selectedLane = -1;
            this.fitAll();
        }
    }

    // —— 数据访问 ——

    private KeyframeChannel<XyzFrameData> channel() {
        if (this.clip == null) {
            return null;
        }
        if (this.axis == 0) {
            return this.clip.xd;
        }
        if (this.axis == 1) {
            return this.clip.yd;
        }
        return this.clip.zd;
    }

    private float duration() {
        return this.clip == null ? 1.0F : Math.max(1.0F, this.clip.duration.get().floatValue());
    }

    private static double laneValue(XyzFrameData data, int lane) {
        if (data == null) {
            return 0.0D;
        }
        if (lane == 0) {
            return data.roamValue;
        }
        if (lane == 1) {
            return data.trackRelative;
        }
        return data.trackAbsolute;
    }

    /** 该 tick 的锚点坐标（无追踪目标 = 0，此时相对与绝对同值）。 */
    private double anchorAt(float tick) {
        UIFilmPanel panel = this.getParent(UIFilmPanel.class);
        if (this.clip == null || panel == null || !UIMultiTrackerClip.hasTrackTarget(this.clip, tick)) {
            return 0.0D;
        }
        return UIMultiTrackerClip.anchorAxisAt(this.clip, panel, this.axis, tick);
    }

    /** 写某子窗数值：游动只写自身；追踪两窗按锚点互转（世界坐标不变）。 */
    private void setLaneValue(Keyframe<XyzFrameData> keyframe, int lane, double value) {
        XyzFrameData data = keyframe.getValue();
        if (data == null) {
            return;
        }

        if (lane == 0) {
            data.roamValue = value;
            data.roamMode = 1;
            return;
        }

        double anchor = this.anchorAt(keyframe.getTick());
        if (lane == 1) {
            data.trackRelative = value;
            data.trackAbsolute = value + anchor;
        } else {
            data.trackAbsolute = value;
            data.trackRelative = value - anchor;
        }
    }

    /** 自动拟合三窗 Y 范围（空通道给 ±1，单值给 ±0.5）。 */
    private void fitAll() {
        KeyframeChannel<XyzFrameData> channel = this.channel();

        if (channel == null || channel.isEmpty()) {
            for (int i = 0; i < 3; i++) {
                this.viewMin[i] = -1.0D;
                this.viewMax[i] = 1.0D;
            }
            return;
        }

        for (int lane = 0; lane < 3; lane++) {
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;

            for (Keyframe<XyzFrameData> keyframe : channel.getKeyframes()) {
                double value = laneValue(keyframe.getValue(), lane);
                min = Math.min(min, value);
                max = Math.max(max, value);
            }

            if (min > max) {
                min = -1.0D;
                max = 1.0D;
            } else if (max - min < 0.001D) {
                min -= 0.5D;
                max += 0.5D;
            } else {
                double pad = (max - min) * 0.12D;
                min -= pad;
                max += pad;
            }

            this.viewMin[lane] = min;
            this.viewMax[lane] = max;
        }
    }

    // —— 布局 ——

    private void layout() {
        int x = this.area.x + 6;
        int top = this.area.y + 4;
        int w = Math.max(120, this.area.w - 12);

        int tabW = 24;
        for (int i = 0; i < 3; i++) {
            this.tabs[i].set(x + i * (tabW + 4), top, tabW, 16);
        }
        this.allTracks.set(this.area.x + this.area.w - 88, top, 82, 16);

        this.ruler.set(x, top + HEADER_H, w, RULER_H);

        int bodyTop = this.ruler.y + this.ruler.h;
        int bodyBottom = this.area.y + this.area.h - 6;
        this.body.set(x, bodyTop, w, Math.max(60, bodyBottom - bodyTop));

        float sum = this.weights[0] + this.weights[1] + this.weights[2];
        int usable = this.body.h - SPLITTER_H * 2;
        int y = this.body.y;

        for (int i = 0; i < 3; i++) {
            int h = (int) Math.max(20.0F, usable * this.weights[i] / sum);
            this.lanes[i].set(this.body.x, y, this.body.w, h);
            y += h + SPLITTER_H;
        }

        // 末窗补齐（避免取整缝隙）
        this.lanes[2].h = Math.max(20, this.body.y + this.body.h - this.lanes[2].y);

        this.computeTickStep();
    }

    private void computeTickStep() {
        float dur = this.duration();
        float usable = Math.max(40, this.body.w - LABEL_W - RIGHT_PAD);
        float pxPerTick = usable / dur;
        float raw = 46.0F / Math.max(0.0001F, pxPerTick);

        float[] steps = {1.0F, 2.0F, 5.0F, 10.0F, 20.0F, 40.0F, 80.0F, 100.0F, 200.0F, 400.0F, 800.0F, 1000.0F};
        this.tickStep = steps[steps.length - 1];

        for (float step : steps) {
            if (step >= raw) {
                this.tickStep = step;
                break;
            }
        }
    }

    private int curveX0(Area lane) {
        return lane.x + LABEL_W;
    }

    private int curveX1(Area lane) {
        return lane.x + lane.w - RIGHT_PAD;
    }

    private int tickToX(float tick, Area lane) {
        float dur = this.duration();
        float progress = tick / dur;
        progress = Math.max(0.0F, Math.min(1.0F, progress));
        int x0 = this.curveX0(lane);
        int x1 = Math.max(x0 + 1, this.curveX1(lane));
        return (int) (x0 + progress * (x1 - x0));
    }

    private float xToTick(int x, Area lane) {
        int x0 = this.curveX0(lane);
        int x1 = Math.max(x0 + 1, this.curveX1(lane));
        float progress = (float) (x - x0) / (float) (x1 - x0);
        progress = Math.max(0.0F, Math.min(1.0F, progress));
        return progress * this.duration();
    }

    private int valueToY(Area lane, int index, double value) {
        double min = this.viewMin[index];
        double max = this.viewMax[index];
        double span = Math.max(0.0001D, max - min);
        double progress = (value - min) / span;
        progress = Math.max(0.0D, Math.min(1.0D, progress));

        int top = lane.y + LANE_HEAD + PAD_Y;
        int bottom = lane.y + lane.h - PAD_Y;
        int h = Math.max(2, bottom - top);
        return (int) (top + (1.0D - progress) * h);
    }

    private double yToValue(Area lane, int index, int y) {
        int top = lane.y + LANE_HEAD + PAD_Y;
        int bottom = lane.y + lane.h - PAD_Y;
        int h = Math.max(2, bottom - top);
        double progress = 1.0D - (double) (y - top) / (double) h;
        progress = Math.max(0.0D, Math.min(1.0D, progress));
        return this.viewMin[index] + progress * (this.viewMax[index] - this.viewMin[index]);
    }

    // —— 渲染 ——

    @Override
    public void render(UIContext context) {
        this.layout();
        this.updateDrag(context);
        this.layout();

        context.batcher.box(this.area.x, this.area.y, this.area.w, this.area.h, 0xF014161A);
        this.renderHeader(context);
        this.renderRuler(context);
        this.renderLanes(context);
        this.renderSplitters(context);
        this.renderPlayhead(context);
    }

    private void renderHeader(UIContext context) {
        int y = this.area.y + 4;

        context.batcher.box(this.area.x, this.area.y, this.area.w, HEADER_H, 0xFF20242B);

        for (int i = 0; i < 3; i++) {
            Area tab = this.tabs[i];
            boolean active = this.axis == i;
            context.batcher.box(tab.x, tab.y, tab.w, tab.h, active ? 0xFF3B4350 : 0xFF262B33);
            context.batcher.outline(tab.x, tab.y, tab.w, tab.h, active ? 0xFFFFFFFF : 0x30FFFFFF);
            context.batcher.text(AXIS_NAMES[i], tab.x + 9, tab.y + 4, active ? 0xFFFFFFFF : 0xFFB0B6C0);
        }

        String title = "多样追踪 · " + AXIS_NAMES[this.axis] + " 轨 · 三子窗曲线";
        context.batcher.text(title, this.tabs[2].x + this.tabs[2].w + 10, y + 4, 0xFFD8DEE9);

        Area all = this.allTracks;
        context.batcher.box(all.x, all.y, all.w, all.h, 0xFF2E3541);
        context.batcher.outline(all.x, all.y, all.w, all.h, 0x40FFFFFF);
        context.batcher.text("全部轨道", all.x + 8, all.y + 4, 0xFFE6EAF0);
    }

    private void renderRuler(UIContext context) {
        Area r = this.ruler;
        context.batcher.box(r.x, r.y, r.w, r.h, 0xFF181B20);
        context.batcher.outline(r.x, r.y, r.w, r.h, 0x24FFFFFF);

        float dur = this.duration();
        for (float tick = 0.0F; tick <= dur + 0.001F; tick += this.tickStep) {
            int x = this.tickToX(tick, r);
            context.batcher.text(formatTick(tick), x + 2, r.y + 3, 0xFF8B93A0);
        }
    }

    private void renderLanes(UIContext context) {
        KeyframeChannel<XyzFrameData> channel = this.channel();
        float dur = this.duration();

        for (int lane = 0; lane < 3; lane++) {
            Area area = this.lanes[lane];
            int color = COLORS[lane];

            context.batcher.box(area.x, area.y, area.w, area.h, lane == 1 ? 0xFF1C2027 : 0xFF191C22);
            context.batcher.outline(area.x, area.y, area.w, area.h, 0x38FFFFFF);
            context.batcher.box(area.x, area.y, 3, area.h, color);

            context.batcher.text(LANE_NAMES[lane], area.x + 8, area.y + 4, color);

            context.batcher.clip(area, context);

            // 横向网格 + Y 轴刻度
            for (int i = 0; i <= 4; i++) {
                int y = area.y + LANE_HEAD + PAD_Y + (int) ((area.h - LANE_HEAD - PAD_Y * 2) * i / 4.0F);
                int lineColor = i == 2 ? 0x40FFFFFF : 0x18FFFFFF;
                context.batcher.box(area.x + LABEL_W - 6, y, area.w - LABEL_W + 6 - RIGHT_PAD, 1, lineColor);

                double value = this.viewMax[lane] - (this.viewMax[lane] - this.viewMin[lane]) * i / 4.0D;
                String label = formatValue(value);
                context.batcher.text(label, area.x + 4, y - 3, 0xFF6F7885);
            }

            // 纵向网格
            for (float tick = 0.0F; tick <= dur + 0.001F; tick += this.tickStep) {
                int x = this.tickToX(tick, area);
                context.batcher.box(x, area.y + LANE_HEAD, 1, area.h - LANE_HEAD - PAD_Y, 0x18FFFFFF);
            }

            if (channel == null || channel.isEmpty()) {
                context.batcher.text("此轴暂无关键帧（在原编辑器打点后显示）", area.x + LABEL_W + 8, area.y + area.h / 2 - 4, 0xFF717A87);
            } else {
                this.renderCurve(context, channel, lane, area);
                this.renderKeyframes(context, channel, lane, area);

                // 当前播放头处的数值
                float playheadTick = this.playheadTick(context);
                XyzFrameData data = channel.interpolate(playheadTick);
                double value = laneValue(data, lane);
                String text = "当前 " + formatValue(value);
                context.batcher.text(text, area.x + area.w - RIGHT_PAD - 82, area.y + 4, 0xFFAAB2BF);
            }

            context.batcher.unclip(context);
        }
    }

    private void renderCurve(UIContext context, KeyframeChannel<XyzFrameData> channel, int lane, Area area) {
        int x0 = this.curveX0(area);
        int x1 = this.curveX1(area);
        int color = COLORS[lane];

        int step = Math.max(1, CURVE_STEP);
        int lastX = x0;
        int lastY = 0;
        boolean first = true;

        for (int x = x0; x <= x1; x += step) {
            float tick = this.xToTick(x, area);
            XyzFrameData data = channel.interpolate(tick);
            int y = this.valueToY(area, lane, laneValue(data, lane));

            if (first) {
                first = false;
            } else {
                // 用竖直短块连接相邻采样点，避免断线
                int top = Math.min(lastY, y);
                int h = Math.max(1, Math.abs(y - lastY));
                context.batcher.box(lastX, top, Math.max(1, x - lastX), h, color);
            }

            context.batcher.box(x, y, step, 1, color);
            lastX = x;
            lastY = y;
        }
    }

    private void renderKeyframes(UIContext context, KeyframeChannel<XyzFrameData> channel, int lane, Area area) {
        int color = COLORS[lane];

        for (Keyframe<XyzFrameData> keyframe : channel.getKeyframes()) {
            int x = this.tickToX(keyframe.getTick(), area);
            int y = this.valueToY(area, lane, laneValue(keyframe.getValue(), lane));

            context.batcher.box(x - 3, y - 3, 7, 7, 0xFF121417);
            context.batcher.box(x - 2, y - 2, 5, 5, color);

            if (this.selected == keyframe && this.selectedLane == lane) {
                context.batcher.outline(x - 5, y - 5, 11, 11, 0xFFFFFFFF);
            }
        }
    }

    private void renderSplitters(UIContext context) {
        for (int i = 0; i < 2; i++) {
            Area splitter = this.splitterArea(i);
            boolean hover = this.dragSplitter == i;
            context.batcher.box(splitter.x, splitter.y, splitter.w, splitter.h, hover ? 0xFF4C5564 : 0xFF323844);

            int midX = splitter.x + splitter.w / 2;
            context.batcher.box(midX - 14, splitter.y + 2, 6, 1, 0xFF8A93A2);
            context.batcher.box(midX - 4, splitter.y + 2, 6, 1, 0xFF8A93A2);
            context.batcher.box(midX + 6, splitter.y + 2, 6, 1, 0xFF8A93A2);
        }
    }

    private void renderPlayhead(UIContext context) {
        Area area = this.lanes[0];
        float tick = this.playheadTick(context);
        int x = this.tickToX(tick, area);

        context.batcher.box(x, this.ruler.y, 1, this.ruler.h, 0xFFFFE082);
        context.batcher.box(x, this.body.y, 1, this.body.h, 0x99FFE082);
    }

    private float playheadTick(UIContext context) {
        if (this.clip == null) {
            return 0.0F;
        }
        float cursor = this.editor.getKeyframeCursor(context.getTransition());
        float tick = cursor - this.clip.tick.get().intValue();
        return Math.max(0.0F, Math.min(this.duration(), tick));
    }

    private Area splitterArea(int index) {
        Area lane = this.lanes[index];
        return new Area(this.body.x, lane.y + lane.h, this.body.w, SPLITTER_H);
    }

    // —— 鼠标 ——

    @Override
    protected boolean subMouseClicked(UIContext context) {
        if (this.clip == null) {
            return false;
        }

        this.layout();
        int mx = context.mouseX;
        int my = context.mouseY;

        if (!this.area.isInside(mx, my)) {
            return false;
        }

        for (int i = 0; i < 3; i++) {
            if (this.tabs[i].isInside(mx, my)) {
                this.setAxis(i);
                return true;
            }
        }

        if (this.allTracks.isInside(mx, my)) {
            if (this.openAllTracks != null) {
                this.openAllTracks.run();
            }
            return true;
        }

        for (int i = 0; i < 2; i++) {
            if (this.splitterArea(i).isInside(mx, my)) {
                long now = System.currentTimeMillis();

                // 双击分隔条（350ms 内两次点击同一根）：恢复三窗等高
                if (this.lastSplitterIndex == i && now - this.lastSplitterClick <= 350L) {
                    for (int k = 0; k < 3; k++) {
                        this.weights[k] = 1.0F;
                    }
                    this.lastSplitterIndex = -1;
                    this.dragSplitter = -1;
                    return true;
                }

                this.lastSplitterClick = now;
                this.lastSplitterIndex = i;
                this.dragSplitter = i;
                this.dragStartY = my;
                System.arraycopy(this.weights, 0, this.dragStartWeights, 0, 3);

                // 右键分隔条：同样恢复等高
                if (context.mouseButton == 1) {
                    for (int k = 0; k < 3; k++) {
                        this.weights[k] = 1.0F;
                    }
                    this.dragSplitter = -1;
                }
                return true;
            }
        }

        for (int lane = 0; lane < 3; lane++) {
            if (!this.lanes[lane].isInside(mx, my)) {
                continue;
            }

            Keyframe<XyzFrameData> keyframe = this.findKeyframe(lane, mx, my);

            this.selected = keyframe;
            this.selectedLane = keyframe == null ? -1 : lane;

            if (keyframe != null) {
                this.dragLane = lane;
                this.dragStartX = mx;
                this.lastDragX = mx;
                this.lastDragY = my;
            }
            return true;
        }

        return false;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context) {
        if (this.clip == null) {
            return false;
        }

        this.layout();

        for (int lane = 0; lane < 3; lane++) {
            if (!this.lanes[lane].isInside(context.mouseX, context.mouseY)) {
                continue;
            }

            double value = this.yToValue(this.lanes[lane], lane, context.mouseY);
            double factor = context.mouseWheel > 0 ? 0.86D : 1.0D / 0.86D;

            this.viewMin[lane] = value + (this.viewMin[lane] - value) * factor;
            this.viewMax[lane] = value + (this.viewMax[lane] - value) * factor;
            return true;
        }

        return false;
    }

    @Override
    protected boolean subMouseReleased(UIContext context) {
        boolean active = this.dragSplitter >= 0 || this.dragLane >= 0;

        this.dragSplitter = -1;
        this.dragLane = -1;

        return active;
    }

    /** 拖动更新（渲染前轮询鼠标位置，BBS 的 UIElement 没有独立的拖拽回调）。 */
    private void updateDrag(UIContext context) {
        if (this.clip == null) {
            return;
        }

        if (this.dragSplitter >= 0) {
            int delta = context.mouseY - this.dragStartY;
            int usable = Math.max(60, this.body.h - SPLITTER_H * 2);
            float deltaWeight = (float) delta / (float) usable;
            // 单窗最小高度约 60px（用户口径）
            float minWeight = Math.max(0.08F, 60.0F / (float) usable);

            float a = Math.max(minWeight, this.dragStartWeights[this.dragSplitter] + deltaWeight);
            float b = Math.max(minWeight, this.dragStartWeights[this.dragSplitter + 1] - deltaWeight);
            float total = a + b;
            float combined = this.dragStartWeights[this.dragSplitter] + this.dragStartWeights[this.dragSplitter + 1];

            this.weights[this.dragSplitter] = a / total * combined;
            this.weights[this.dragSplitter + 1] = b / total * combined;
            this.normalizeWeights();
            return;
        }

        if (this.dragLane >= 0 && this.selected != null) {
            boolean changed = false;

            // 纵向 → 改本子窗数值
            if (context.mouseY != this.lastDragY) {
                double value = this.yToValue(this.lanes[this.dragLane], this.dragLane, context.mouseY);
                this.setLaneValue(this.selected, this.dragLane, value);
                this.lastDragY = context.mouseY;
                changed = true;
            }

            // 横向（超过 2px）→ 改关键帧 tick，三条子窗同步移动（同一个关键帧）
            if (Math.abs(context.mouseX - this.dragStartX) > 2 && context.mouseX != this.lastDragX) {
                float tick = this.xToTick(context.mouseX, this.lanes[this.dragLane]);
                this.selected.setTick(Math.max(0.0F, Math.min(this.duration(), tick)), true);
                this.lastDragX = context.mouseX;
                changed = true;
            }

            if (changed) {
                KeyframeChannel<XyzFrameData> channel = this.channel();
                if (channel != null) {
                    channel.sort();
                }
            }
        }
    }

    private void normalizeWeights() {
        float sum = this.weights[0] + this.weights[1] + this.weights[2];
        if (sum > 0.0001F) {
            for (int i = 0; i < 3; i++) {
                this.weights[i] /= sum;
            }
        }
    }

    /** 命中某子窗内最近的关键帧（8px 内）。 */
    private Keyframe<XyzFrameData> findKeyframe(int lane, int mouseX, int mouseY) {
        KeyframeChannel<XyzFrameData> channel = this.channel();
        if (channel == null || channel.isEmpty()) {
            return null;
        }

        Area area = this.lanes[lane];
        Keyframe<XyzFrameData> best = null;
        int bestDistance = 9 * 9;
        List<Keyframe<XyzFrameData>> keyframes = channel.getKeyframes();

        for (Keyframe<XyzFrameData> keyframe : keyframes) {
            int x = this.tickToX(keyframe.getTick(), area);
            int y = this.valueToY(area, lane, laneValue(keyframe.getValue(), lane));
            int dx = x - mouseX;
            int dy = y - mouseY;
            int distance = dx * dx + dy * dy;

            if (distance <= bestDistance) {
                bestDistance = distance;
                best = keyframe;
            }
        }

        return best;
    }

    // —— 文本 ——

    private static String formatValue(double value) {
        if (Math.abs(value) >= 1000.0D) {
            return String.format(Locale.US, "%.0f", value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private static String formatTick(float tick) {
        if (tick == 0.0F) {
            return "0";
        }
        if (Math.abs(tick - Math.round(tick)) < 0.001F) {
            return String.valueOf((int) Math.round(tick));
        }
        return String.format(Locale.US, "%.1f", tick);
    }
}
