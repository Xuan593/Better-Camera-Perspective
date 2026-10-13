package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import perspective.better_camera_perspective.client.clips.MultiTrackerClientClip;
import perspective.better_camera_perspective.client.data.XyzFrameData;

import java.util.function.Consumer;

/**
 * XYZ 帧级四值编辑工厂（V1.5.4-Alpha 十版：存储重构）。
 *
 * 选中多样追踪剪辑的 xd / yd / zd 帧级关键帧时，显示两组「相对 / 绝对」数值：
 *
 *   组1 · 游动（只影响 anchor 关键帧值为 <无> 的视角）—— 品红 0xFFFF6666
 *     相对：世界 = roamValue + offset（offset 为剪辑「偏移」调试值）
 *     绝对：世界 = roamValue
 *   组2 · 追踪（只影响 anchor 关键帧绑定模型的视角）—— 淡蓝 0x87CEFA
 *     相对：世界 = 锚点 + trackRelative
 *     绝对：世界 = trackAbsolute
 *
 * 所有值均为**每帧独立存储**（XyzFrameData），游动与追踪不再共用同一个池；
 * 切换相对 / 绝对模式时按当前基准（offset / 锚点）相互转换，世界坐标保持不变。
 */
@Environment(EnvType.CLIENT)
public class UIXyzKeyframeFactory extends UIKeyframeFactory<XyzFrameData> {
    /** 游动路线标识色（品红偏亮红）。 */
    private static final int COLOR_ROAM = 0xFFFF6666;
    /** 追踪路线标识色（淡蓝）。 */
    private static final int COLOR_TRACK = 0x87CEFA;

    private final int axis;
    private final UITrackpad roamRelativeValue;
    private final UITrackpad roamAbsoluteValue;
    private final UITrackpad trackRelativeValue;
    private final UITrackpad trackAbsoluteValue;
    private final UIButton roamRelativeButton;
    private final UIButton roamAbsoluteButton;
    private final UIButton trackRelativeButton;
    private final UIButton trackAbsoluteButton;

    public UIXyzKeyframeFactory(Keyframe<XyzFrameData> keyframe, UIKeyframes editor, int axis) {
        super(keyframe, editor);
        this.axis = axis;

        this.roamRelativeValue = new UITrackpad(this::setRoamRelative);
        this.roamRelativeValue.h(20);
        this.roamAbsoluteValue = new UITrackpad(this::setRoamAbsolute);
        this.roamAbsoluteValue.h(20);
        this.trackRelativeValue = new UITrackpad(this::setTrackRelative);
        this.trackRelativeValue.h(20);
        this.trackAbsoluteValue = new UITrackpad(this::setTrackAbsolute);
        this.trackAbsoluteValue.h(20);

        this.roamRelativeButton = this.modeButton(b -> this.setRoamMode(0));
        this.roamAbsoluteButton = this.modeButton(b -> this.setRoamMode(1));
        this.trackRelativeButton = this.modeButton(b -> this.setTrackMode(0));
        this.trackAbsoluteButton = this.modeButton(b -> this.setTrackMode(1));

        // 组1 · 游动
        UILabel roamTitle = new UILabel(IKey.raw("游动"), COLOR_ROAM);
        roamTitle.relative(this.scroll).w(1.0F).h(20);
        this.scroll.add(roamTitle);
        this.scroll.add(UI.row(2, 0, 0, this.roamRelativeButton, this.roamRelativeValue));
        this.scroll.add(UI.row(2, 0, 0, this.roamAbsoluteButton, this.roamAbsoluteValue));

        // 组2 · 追踪
        UILabel trackTitle = new UILabel(IKey.raw("追踪"), COLOR_TRACK);
        trackTitle.relative(this.scroll).w(1.0F).h(20);
        this.scroll.add(trackTitle);
        this.scroll.add(UI.row(2, 0, 0, this.trackRelativeButton, this.trackRelativeValue));
        this.scroll.add(UI.row(2, 0, 0, this.trackAbsoluteButton, this.trackAbsoluteValue));

        this.refreshButtons();
        this.updateValues();
    }

    /** 模式短按钮（可点击，选中态由 refreshButtons 着色）。 */
    private UIButton modeButton(Consumer<UIButton> callback) {
        UIButton button = new UIButton(IKey.raw(""), callback);
        button.wh(64, 16);
        return button;
    }

    /** 取当前帧的帧级数据（可变引用）。 */
    private XyzFrameData frameData() {
        return (XyzFrameData) this.keyframe.getValue();
    }

    // —— 模式切换（世界坐标保持不变：相对 ⇄ 绝对相互转换）——

    /** 游动模式切换：0 相对 / 1 绝对；按 offset 调试值转换 roamValue。 */
    private void setRoamMode(int mode) {
        XyzFrameData data = this.frameData();
        if (data != null && data.roamMode != mode) {
            double offset = this.debugOffset();
            if (mode == 0) {
                // 绝对 → 相对：roamValue = roamValue − offset
                data.roamValue -= offset;
            } else {
                // 相对 → 绝对：roamValue = roamValue + offset
                data.roamValue += offset;
            }
            data.roamMode = mode;
        }
        this.refreshButtons();
        this.updateValues();
    }

    /** 追踪模式切换：0 相对 / 1 绝对；按当前锚点转换相对 / 绝对值。 */
    private void setTrackMode(int mode) {
        XyzFrameData data = this.frameData();
        if (data != null && data.trackMode != mode) {
            double anchor = this.anchor();
            if (mode == 0) {
                // 绝对 → 相对：trackRelative = trackAbsolute − 锚点
                data.trackRelative = data.trackAbsolute - anchor;
            } else {
                // 相对 → 绝对：trackAbsolute = trackRelative + 锚点
                data.trackAbsolute = data.trackRelative + anchor;
            }
            data.trackMode = mode;
        }
        this.refreshButtons();
        this.updateValues();
    }

    /** 当前帧是否处于「追踪」路线（anchor 关键帧值绑定目标）。 */
    private boolean isTracked() {
        MultiTrackerClientClip clip = this.findClip();
        return clip != null && UIMultiTrackerClip.hasTrackTarget(clip, this.keyframe.getTick());
    }

    /** 刷新四按钮文本与选中态（当前路线组高亮其选中模式，另一组不高亮）。 */
    private void refreshButtons() {
        XyzFrameData data = this.frameData();
        boolean tracked = this.isTracked();
        int roamMode = data == null ? 0 : data.roamMode;
        int trackMode = data == null ? 0 : data.trackMode;

        this.roamRelativeButton.label = IKey.raw("相对");
        this.roamAbsoluteButton.label = IKey.raw("绝对");
        this.trackRelativeButton.label = IKey.raw("相对");
        this.trackAbsoluteButton.label = IKey.raw("绝对");

        this.styleButton(this.roamRelativeButton, COLOR_ROAM, !tracked && roamMode == 0);
        this.styleButton(this.roamAbsoluteButton, COLOR_ROAM, !tracked && roamMode == 1);
        this.styleButton(this.trackRelativeButton, COLOR_TRACK, tracked && trackMode == 0);
        this.styleButton(this.trackAbsoluteButton, COLOR_TRACK, tracked && trackMode == 1);
    }

    /** 按钮样式：选中 = 标识色背景 + 白字；未选中 = 标识色文字 + 透明底。 */
    private void styleButton(UIButton button, int color, boolean active) {
        if (active) {
            button.color(color).background(true).textColor(0xFFFFFF, true);
        } else {
            button.background(false).textColor(color, false);
        }
    }

    // —— 四值编辑回调（只改当前帧，改完全刷四框）——

    /** 游动·相对：按当前模式反推 roamValue（相对模式直接写；绝对模式 = value + offset）。 */
    private void setRoamRelative(double value) {
        XyzFrameData data = this.frameData();
        if (data != null) {
            data.roamValue = data.roamMode == 0 ? value : value + this.debugOffset();
            this.updateValues();
        }
    }

    /** 游动·绝对：绝对模式直接写；相对模式 = value − offset。 */
    private void setRoamAbsolute(double value) {
        XyzFrameData data = this.frameData();
        if (data != null) {
            data.roamValue = data.roamMode == 1 ? value : value - this.debugOffset();
            this.updateValues();
        }
    }

    /** 追踪·相对：相对模式直接写 trackRelative；绝对模式写 trackAbsolute = value + anchor。 */
    private void setTrackRelative(double value) {
        XyzFrameData data = this.frameData();
        if (data != null) {
            if (data.trackMode == 0) {
                data.trackRelative = value;
            } else {
                data.trackAbsolute = value + this.anchor();
            }
            this.updateValues();
        }
    }

    /** 追踪·绝对：绝对模式直接写 trackAbsolute；相对模式写 trackRelative = value − anchor。 */
    private void setTrackAbsolute(double value) {
        XyzFrameData data = this.frameData();
        if (data != null) {
            if (data.trackMode == 1) {
                data.trackAbsolute = value;
            } else {
                data.trackRelative = value - this.anchor();
            }
            this.updateValues();
        }
    }

    /** 剪辑级「偏移」调试值（该轴），游动相对模式下叠加到 roamValue。 */
    private double debugOffset() {
        MultiTrackerClientClip clip = this.findClip();
        if (clip == null) {
            return 0.0D;
        }
        Point offset = clip.offset.get();
        return this.axis == 0 ? offset.x : (this.axis == 1 ? offset.y : offset.z);
    }

    /** 该 tick 的追踪锚点坐标（anchor 轴优先，回退剪辑属性；无目标 = 0）。 */
    private double anchor() {
        MultiTrackerClientClip clip = this.findClip();
        UIFilmPanel panel = this.getPanel();
        if (clip == null || panel == null) {
            return 0.0D;
        }
        return UIMultiTrackerClip.anchorAxisAt(clip, panel, this.axis, this.keyframe.getTick());
    }

    /** 全刷四框（按当前模式把帧值换算成相对 / 绝对表示）。 */
    private void updateValues() {
        XyzFrameData data = this.frameData();
        if (data == null) {
            return;
        }
        double offset = this.debugOffset();
        double roamAbsolute = data.roamMode == 1 ? data.roamValue : data.roamValue + offset;
        double roamRelative = data.roamMode == 0 ? data.roamValue : data.roamValue - offset;
        this.roamRelativeValue.setValue(roamRelative);
        this.roamAbsoluteValue.setValue(roamAbsolute);

        double anchor = this.anchor();
        double trackAbsolute = data.trackMode == 1 ? data.trackAbsolute : anchor + data.trackRelative;
        double trackRelative = data.trackMode == 0 ? data.trackRelative : data.trackAbsolute - anchor;
        this.trackRelativeValue.setValue(trackRelative);
        this.trackAbsoluteValue.setValue(trackAbsolute);
    }

    @Override
    public void update() {
        super.update();
        this.refreshButtons();
        this.updateValues();
    }

    private MultiTrackerClientClip findClip() {
        if (this.editor instanceof mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes film && film.editor != null) {
            Clip clip = film.editor.getClip();
            if (clip instanceof MultiTrackerClientClip multi) {
                return multi;
            }
        }
        return null;
    }

    private UIFilmPanel getPanel() {
        return this.getParent(UIFilmPanel.class);
    }
}
