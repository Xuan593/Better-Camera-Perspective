package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.KeyframeSelection;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.KeyframeType;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeGraph;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import perspective.better_camera_perspective.data.XyzFrameData;

import java.util.ArrayList;
import java.util.List;

/**
 * V1.7.22-Alpha：XYZ 轨道曲线编辑器（三子窗）。
 *
 * <p>只在 {@code UIKeyframes.editSheet()} 进入 XYZ 轨道（{@code bcp_xyz_frame}）曲线编辑时替换
 * {@code currentGraph}，不占用摄像机编辑器整块界面；其余轨道仍走 BBS 原生图。</p>
 *
 * <p>三窗对应 {@link XyzFrameData} 的三条真实曲线：游动-绝对 / 追踪-相对 / 追踪-绝对。
 * 每窗独立 Y 轴缩放，窗间可拖分隔条；拖关键帧只改本窗对应字段，不经过
 * {@code IKeyframeFactory.yToValue()} 整值替换，因此不会把 XyzFrameData 退化成 Double。</p>
 */
public class UIXyzTripleKeyframeGraph extends UIKeyframeGraph {
    private static final int LANE_COUNT = 3;
    private static final int HEADER = 17;
    private static final int GAP = 5;
    private static final int POINT = 5;
    private static final int POINT_SELECTED = 8;
    private static final int MIN_LANE_PX = 42;

    private static final int COLOR_PANEL = 0xFF111318;
    private static final int COLOR_LANE = 0xFF171A21;
    private static final int COLOR_LANE_ALT = 0xFF14171D;
    private static final int COLOR_HEADER = 0xFF232833;
    private static final int COLOR_BORDER = 0xFF343B48;
    private static final int COLOR_GRID = 0x22FFFFFF;
    private static final int COLOR_TEXT = 0xFFAEB8C8;
    private static final int COLOR_TEXT_DIM = 0xFF77808F;
    private static final int COLOR_SELECT = 0xFFFFD24A;

    private static final int COLOR_ROAM = 0xFFFF3B30;
    private static final int COLOR_TRACK_REL = 0xFF4FC3F7;
    private static final int COLOR_TRACK_ABS = 0xFF0040FF;

    private final Scale[] axes = new Scale[LANE_COUNT];
    private final float[] weights = {1F, 1F, 1F};
    private final int[] laneTop = new int[LANE_COUNT];
    private final int[] laneHeight = new int[LANE_COUNT];
    private final int[] curveTop = new int[LANE_COUNT];
    private final int[] curveHeight = new int[LANE_COUNT];

    private int layoutTop;
    private int layoutBottom;
    private int activeLane;
    private boolean fitted;
    private int splitDrag = -1;
    private int splitStartY;
    private float[] splitStartWeights;
    private long lastHeaderClick;
    private int lastHeaderLane = -1;

    public UIXyzTripleKeyframeGraph(UIKeyframes keyframes, UIKeyframeSheet sheet) {
        super(keyframes, sheet);

        this.axes[0] = this.yAxis;
        this.axes[1] = new Scale(keyframes.area, ScrollDirection.VERTICAL).inverse();
        this.axes[2] = new Scale(keyframes.area, ScrollDirection.VERTICAL).inverse();
    }

    /* ================= 布局 ================= */

    private void layout() {
        Area area = this.keyframes.graphArea;
        int top = area.y + TimelineRulerRenderer.getRulerBottom(this.keyframes.area);
        int bottom = area.y + area.h;

        this.layoutTop = top;
        this.layoutBottom = bottom;

        int usable = bottom - top - GAP * (LANE_COUNT - 1) - HEADER * LANE_COUNT;

        if (usable <= 0 || area.w <= 0) {
            for (int i = 0; i < LANE_COUNT; i ++) {
                this.laneTop[i] = top;
                this.laneHeight[i] = 0;
                this.curveTop[i] = top + HEADER;
                this.curveHeight[i] = 0;
                this.axes[i].area = new Area(area.x, top + HEADER, area.w, 1);
            }

            return;
        }

        float sum = 0F;

        for (float weight : this.weights) {
            sum += Math.max(0.01F, weight);
        }

        int y = top;

        for (int i = 0; i < LANE_COUNT; i ++) {
            float weight = Math.max(0.01F, this.weights[i]);
            int h = i == LANE_COUNT - 1
                ? bottom - y - HEADER
                : Math.max(MIN_LANE_PX, Math.round(usable * weight / sum));

            if (i == LANE_COUNT - 1) {
                h = Math.max(MIN_LANE_PX, h);
            }

            this.laneTop[i] = y;
            this.laneHeight[i] = h + HEADER;
            this.curveTop[i] = y + HEADER;
            this.curveHeight[i] = h;
            this.axes[i].area = new Area(area.x, y + HEADER, area.w, h);
            y += this.laneHeight[i] + GAP;
        }
    }

    private int laneAt(int y) {
        for (int i = 0; i < LANE_COUNT; i ++) {
            if (y >= this.laneTop[i] && y < this.laneTop[i] + this.laneHeight[i]) {
                return i;
            }
        }

        return -1;
    }

    private int curveLaneAt(int y) {
        for (int i = 0; i < LANE_COUNT; i ++) {
            if (y >= this.curveTop[i] && y < this.curveTop[i] + this.curveHeight[i]) {
                return i;
            }
        }

        return -1;
    }

    private int splitterAt(int y) {
        for (int i = 0; i < LANE_COUNT - 1; i ++) {
            int split = this.laneTop[i] + this.laneHeight[i];

            if (y >= split - 3 && y <= split + GAP + 3) {
                return i;
            }
        }

        return -1;
    }

    /* ================= 缩放 / 自适应 ================= */

    private static double valueOf(XyzFrameData data, int lane) {
        if (data == null) {
            return 0D;
        }

        switch (lane) {
            case 0: return data.roamValue;
            case 1: return data.trackRelative;
            case 2: return data.trackAbsolute;
            default: return 0D;
        }
    }

    private static void writeValue(XyzFrameData data, int lane, double value) {
        switch (lane) {
            case 0:
                data.roamValue = value;
                data.roamMode = 1;
                break;
            case 1: {
                double delta = value - data.trackRelative;

                data.trackRelative = value;
                data.trackAbsolute += delta;
                data.trackMode = 0;
                break;
            }
            case 2: {
                double delta = value - data.trackAbsolute;

                data.trackAbsolute = value;
                data.trackRelative += delta;
                data.trackMode = 1;
                break;
            }
            default:
                break;
        }
    }

    private void fitLane(int lane) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (Object object : this.sheet.channel.getKeyframes()) {
            Keyframe<?> keyframe = (Keyframe<?>) object;
            Object value = keyframe.getValue();

            if (!(value instanceof XyzFrameData)) {
                continue;
            }

            double v = valueOf((XyzFrameData) value, lane);

            if (!Double.isFinite(v)) {
                continue;
            }

            min = Math.min(min, v);
            max = Math.max(max, v);
        }

        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            this.axes[lane].view(-1D, 1D);
            return;
        }

        double range = max - min;
        double pad = range > 1.0E-4 ? range * 0.12D : Math.max(0.8D, Math.abs(max) * 0.08D);

        this.axes[lane].view(min - pad, max + pad);
    }

    private void fitAll() {
        this.layout();

        if (this.layoutBottom - this.layoutTop < HEADER * LANE_COUNT + GAP * (LANE_COUNT - 1) + 30) {
            return;
        }

        for (int i = 0; i < LANE_COUNT; i ++) {
            this.fitLane(i);
        }

        this.fitted = true;
    }

    @Override
    public void resetView() {
        this.fitAll();
    }

    @Override
    public void resize() {
        this.layout();

        if (!this.fitted) {
            this.fitAll();
        }
    }

    @Override
    public void updateZoom() {
        for (Scale axis : this.axes) {
            axis.updateZoom();
        }
    }

    @Override
    public void stopZoom() {
        for (Scale axis : this.axes) {
            axis.stopZoom();
        }
    }

    /* ================= 选择 / 拾取 ================= */

    @Override
    public Pair<Keyframe, KeyframeType> findKeyframe(int x, int y) {
        int lane = this.curveLaneAt(y);

        if (lane < 0) {
            return null;
        }

        this.activeLane = lane;
        Scale axis = this.axes[lane];

        for (Object object : this.sheet.channel.getKeyframes()) {
            Keyframe<?> keyframe = (Keyframe<?>) object;
            Object value = keyframe.getValue();

            if (!(value instanceof XyzFrameData)) {
                continue;
            }

            int px = (int) this.keyframes.toGraphX(keyframe.getTick());
            int py = (int) axis.to(valueOf((XyzFrameData) value, lane));

            if (Math.abs(px - x) <= POINT && Math.abs(py - y) <= POINT) {
                return new Pair<>(keyframe, KeyframeType.REGULAR);
            }
        }

        return null;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void selectKeyframe(Keyframe keyframe) {
        if (keyframe == null) {
            return;
        }

        KeyframeSelection selection = this.sheet.selection;

        selection.clear();
        selection.add(keyframe);
        this.keyframes.pickKeyframe(keyframe);
        this.keyframes.getXAxis().shiftIntoMiddle(keyframe.getTick());

        int lane = this.activeLane;

        if (lane >= 0 && lane < LANE_COUNT) {
            Object value = keyframe.getValue();

            if (value instanceof XyzFrameData) {
                this.axes[lane].shiftIntoMiddle(valueOf((XyzFrameData) value, lane));
            }
        }
    }

    @Override
    public void selectByX(int x) {
        Keyframe<?> best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Object object : this.sheet.channel.getKeyframes()) {
            Keyframe<?> keyframe = (Keyframe<?>) object;
            double px = this.keyframes.toGraphX(keyframe.getTick());
            double distance = Math.abs(px - x);

            if (distance < bestDistance) {
                bestDistance = distance;
                best = keyframe;
            }
        }

        this.sheet.selection.clear();

        if (best != null) {
            this.sheet.selection.add(best);
            this.keyframes.pickKeyframe(best);
        }
    }

    @Override
    public void selectInArea(Area area) {
        this.sheet.selection.clear();

        for (Object object : this.sheet.channel.getKeyframes()) {
            Keyframe<?> keyframe = (Keyframe<?>) object;
            Object value = keyframe.getValue();

            if (!(value instanceof XyzFrameData)) {
                continue;
            }

            int px = (int) this.keyframes.toGraphX(keyframe.getTick());

            for (int lane = 0; lane < LANE_COUNT; lane ++) {
                int py = (int) this.axes[lane].to(valueOf((XyzFrameData) value, lane));

                if (area.isInside(px, py)) {
                    this.sheet.selection.add(keyframe);
                    break;
                }
            }
        }

        Keyframe<?> selected = this.sheet.selection.getFirst();

        if (selected != null) {
            this.keyframes.pickKeyframe(selected);
        }
    }

    /* ================= 鼠标 ================= */

    @Override
    public boolean mouseClicked(UIContext context) {
        int split = this.splitterAt(context.mouseY);

        if (split >= 0 && context.mouseButton == 1) {
            this.weights[0] = 1F;
            this.weights[1] = 1F;
            this.weights[2] = 1F;
            this.splitStartWeights = null;
            this.fitAll();
            return true;
        }

        if (context.mouseButton != 0) {
            return false;
        }

        if (split >= 0) {
            this.splitDrag = split;
            this.splitStartY = context.mouseY;
            this.splitStartWeights = this.weights.clone();
            return true;
        }

        int lane = this.laneAt(context.mouseY);

        if (lane >= 0 && context.mouseY < this.curveTop[lane]) {
            long now = System.currentTimeMillis();

            if (now - this.lastHeaderClick < 420L && lane == this.lastHeaderLane) {
                this.weights[0] = 1F;
                this.weights[1] = 1F;
                this.weights[2] = 1F;
                this.splitStartWeights = null;
                this.fitAll();
            }

            this.lastHeaderClick = now;
            this.lastHeaderLane = lane;
            return true;
        }

        return false;
    }

    @Override
    public void mouseReleased(UIContext context) {
        this.splitDrag = -1;
        this.splitStartWeights = null;
    }

    @Override
    public void handleMouse(UIContext context, int lastX, int lastY) {
        if (this.splitDrag >= 0 && this.splitStartWeights != null) {
            int delta = context.mouseY - this.splitStartY;

            for (int i = 0; i < LANE_COUNT; i ++) {
                this.weights[i] = this.splitStartWeights[i];
            }

            this.weights[this.splitDrag] = Math.max(MIN_LANE_PX, this.splitStartWeights[this.splitDrag] + delta);
            this.weights[this.splitDrag + 1] = Math.max(MIN_LANE_PX, this.splitStartWeights[this.splitDrag + 1] - delta);
            this.layout();
            return;
        }

        if (this.keyframes.isNavigating()) {
            this.keyframes.dragTimeBy(context.mouseX - lastX);

            int dy = context.mouseY - lastY;

            for (Scale axis : this.axes) {
                if (axis.getZoom() != 0D) {
                    axis.setShift(axis.getShift() + dy / axis.getZoom());
                }
            }
        }
    }

    @Override
    public void mouseScrolled(UIContext context) {
        if (context.mouseWheelHorizontal != 0D) {
            this.keyframes.panTime(context.mouseWheelHorizontal);
            return;
        }

        if (context.mouseWheel == 0D) {
            return;
        }

        if (Window.isShiftPressed()) {
            this.keyframes.zoomTimeAt(context, context.mouseWheel);
            return;
        }

        int lane = this.laneAt(context.mouseY);

        if (lane < 0) {
            return;
        }

        this.axes[lane].animateZoom(
            Scale.getAnchorY(context, this.axes[lane].area),
            context.mouseWheel,
            this.keyframes.getZoomSpeed()
        );
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void dragKeyframes(UIContext context, Pair pair, int initialX, int initialY, float originalTick, Object originalValue) {
        if (pair == null || this.activeLane < 0 || this.activeLane >= LANE_COUNT) {
            return;
        }

        Keyframe keyframe = (Keyframe) pair.a;

        if (pair.b != KeyframeType.REGULAR || !(keyframe.getValue() instanceof XyzFrameData)) {
            return;
        }

        int lane = this.activeLane;
        Scale axis = this.axes[lane];
        XyzFrameData start = (XyzFrameData) originalValue;

        float tick = originalTick + (float) (this.keyframes.fromGraphX(context.mouseX) - this.keyframes.fromGraphX(initialX));

        if (Window.isCtrlPressed()) {
            tick = Math.round(tick);
        }

        tick = this.sheet.channel.constrainKeyframeTick(keyframe, tick);
        keyframe.setTick(tick, true);

        double delta = axis.from(context.mouseY) - axis.from(initialY);
        XyzFrameData data = ((XyzFrameData) keyframe.getValue()).copy();

        writeValue(data, lane, valueOf(start, lane) + delta);
        keyframe.setValue(data, true);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean addKeyframeAt(float tick, int y) {
        int lane = this.curveLaneAt(y);

        if (lane < 0) {
            lane = this.laneAt(y);
        }

        if (lane < 0) {
            lane = 0;
        }

        Keyframe keyframe = this.sheet.ensureKeyframe(tick);

        if (keyframe == null || !(keyframe.getValue() instanceof XyzFrameData)) {
            return false;
        }

        XyzFrameData data = ((XyzFrameData) keyframe.getValue()).copy();

        writeValue(data, lane, this.axes[lane].from(y));
        keyframe.setValue(data, true);
        this.activeLane = lane;
        this.selectKeyframe(keyframe);
        this.keyframes.cacheKeyframes();

        return true;
    }

    @Override
    public boolean addKeyframe(int x, int y) {
        return this.addKeyframeAt((float) this.keyframes.fromGraphX(x), y);
    }

    /* ================= 绘制 ================= */

    private String labelOf(int lane) {
        switch (lane) {
            case 0: return "游动 · 绝对";
            case 1: return "追踪 · 相对";
            case 2: return "追踪 · 绝对";
            default: return "";
        }
    }

    private int colorOf(int lane) {
        switch (lane) {
            case 0: return COLOR_ROAM;
            case 1: return COLOR_TRACK_REL;
            case 2: return COLOR_TRACK_ABS;
            default: return COLOR_TEXT;
        }
    }

    @Override
    public void render(UIContext context) {
        this.layout();

        if (!this.fitted) {
            this.fitAll();
        }

        this.updateZoom();

        Area area = this.keyframes.graphArea;

        if (area.w <= 0 || area.h <= 0) {
            return;
        }

        Batcher2D batcher = context.batcher;
        int panelY = this.layoutTop;
        int panelH = area.y + area.h - panelY;

        batcher.box(area.x, panelY, area.w, panelH, COLOR_PANEL);

        for (int lane = 0; lane < LANE_COUNT; lane ++) {
            int top = this.laneTop[lane];
            int height = this.laneHeight[lane];

            if (height <= HEADER) {
                continue;
            }

            batcher.box(area.x, top, area.w, height, lane % 2 == 0 ? COLOR_LANE : COLOR_LANE_ALT);
            batcher.box(area.x, top, area.w, 1, COLOR_BORDER);
            batcher.box(area.x, top, 3, height, this.colorOf(lane));
            batcher.textShadow(this.labelOf(lane), area.x + 8F, top + 3F, this.colorOf(lane));

            Scale axis = this.axes[lane];
            double min = axis.getMinValue();
            double max = axis.getMaxValue();

            if (min < 0D && max > 0D) {
                int zero = (int) axis.to(0D);

                if (zero >= this.curveTop[lane] && zero < this.curveTop[lane] + this.curveHeight[lane]) {
                    batcher.box(area.x, zero, area.w, 1, COLOR_GRID);
                }
            }

            double span = max - min;

            if (span > 1.0E-6D) {
                for (int step = 1; step <= 3; step ++) {
                    double value = min + span * step / 4D;
                    int y = (int) axis.to(value);

                    if (y >= this.curveTop[lane] && y < this.curveTop[lane] + this.curveHeight[lane]) {
                        batcher.box(area.x, y, area.w, 1, 0x18FFFFFF);
                        batcher.text(String.format("%.2f", value), area.x + 5F, y - 2F, COLOR_TEXT_DIM);
                    }
                }
            }

            String range = String.format("%.2f ~ %.2f", min, max);

            batcher.text(range, area.x + area.w - 78F, top + 3F, COLOR_TEXT_DIM);

            this.renderCurve(context, lane);
            this.renderPoints(context, lane);
        }

        for (int lane = 0; lane < LANE_COUNT - 1; lane ++) {
            int split = this.laneTop[lane] + this.laneHeight[lane];

            batcher.box(area.x, split, area.w, GAP, COLOR_HEADER);
            batcher.box(area.x, split + GAP / 2, area.w, 1, COLOR_BORDER);
        }
    }

    private void renderCurve(UIContext context, int lane) {
        Area area = this.keyframes.graphArea;
        int top = this.curveTop[lane];
        int bottom = top + this.curveHeight[lane];
        int color = this.colorOf(lane);
        Scale axis = this.axes[lane];
        int lastY = Integer.MIN_VALUE;

        for (int x = area.x; x < area.x + area.w; x += 2) {
            XyzFrameData data = (XyzFrameData) this.sheet.channel.interpolate((float) this.keyframes.fromGraphX(x));
            int y = (int) axis.to(valueOf(data, lane));

            if (lastY != Integer.MIN_VALUE && y >= top && y < bottom && lastY >= top && lastY < bottom) {
                context.batcher.box(x, y, 2, 1, color);
            }

            lastY = y;
        }
    }

    @SuppressWarnings("rawtypes")
    private void renderPoints(UIContext context, int lane) {
        Area area = this.keyframes.graphArea;
        int top = this.curveTop[lane];
        int bottom = top + this.curveHeight[lane];
        Scale axis = this.axes[lane];
        List<Keyframe> keyframes = new ArrayList<>();

        for (Object object : this.sheet.channel.getKeyframes()) {
            keyframes.add((Keyframe) object);
        }

        List<Keyframe> selectedList = this.sheet.selection.getSelected();

        for (Keyframe keyframe : keyframes) {
            Object value = keyframe.getValue();

            if (!(value instanceof XyzFrameData)) {
                continue;
            }

            int x = (int) this.keyframes.toGraphX(keyframe.getTick());
            int y = (int) axis.to(valueOf((XyzFrameData) value, lane));

            if (y < top - 2 || y > bottom + 2) {
                continue;
            }

            if (selectedList.contains(keyframe)) {
                context.batcher.box(x - POINT_SELECTED, y - POINT_SELECTED, POINT_SELECTED * 2 + 1, POINT_SELECTED * 2 + 1, COLOR_SELECT);
                context.batcher.box(x - POINT, y - POINT, POINT * 2 + 1, POINT * 2 + 1, 0xFF15171C);
            } else {
                context.batcher.box(x - POINT, y - POINT, POINT * 2 + 1, POINT * 2 + 1, 0xFFE8EDF5);
            }

            if (keyframe.getInterpolation().getInterp() == Interpolations.BEZIER) {
                context.batcher.box(x - 2, y - POINT - 4, 5, 2, this.colorOf(lane));
            }
        }
    }
}
