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
import perspective.better_camera_perspective.data.XyzFrameData;

import java.util.function.Consumer;

/**
 * XYZ 帧级数值编辑工厂（V1.5.4-Alpha 十版：存储重构；V1.8.0-Alpha：移除游动相对态）。
 *
 * 选中多样追踪剪辑的 xd / yd / zd 帧级关键帧时，显示两组数值：
 *
 *   组1 · 游动（只影响 anchor 关键帧值为 <无> 的视角）—— 品红 0xFFFF6666
 *     绝对（唯一语义）：世界 = roamValue，roamValue 即相机世界绝对坐标
 *     注：V1.8.0-Alpha 起不再提供「相对」子模式——游动本就读取绝对坐标，
 *         原先的「相对」只是叠加剪辑「偏移」调试值，属冗余状态。「绝对」标识保留为静态文本。
 *   组2 · 追踪（只影响 anchor 关键帧绑定模型的视角）—— 淡蓝 0x87CEFA
 *     相对：世界 = 锚点 + trackRelative
 *     绝对：世界 = trackAbsolute
 *
 * 所有值均为**每帧独立存储**（XyzFrameData）。追踪的「相对 / 绝对」切换仍按当前锚点互转，
 * 世界坐标保持不变。
 */
@Environment(EnvType.CLIENT)
public class UIXyzKeyframeFactory extends UIKeyframeFactory<XyzFrameData> {
    /** 游动路线标识色（品红偏亮红）。 */
    private static final int COLOR_ROAM = 0xFFFF6666;
    /** 追踪路线标识色（淡蓝）。 */
    private static final int COLOR_TRACK = 0x87CEFA;

    private final int axis;
    private final UITrackpad roamAbsoluteValue;
    private final UITrackpad trackRelativeValue;
    private final UITrackpad trackAbsoluteValue;
    private final UIButton roamAbsoluteButton;
    private final UIButton trackRelativeButton;
    private final UIButton trackAbsoluteButton;

    public UIXyzKeyframeFactory(Keyframe<XyzFrameData> keyframe, UIKeyframes editor, int axis) {
        super(keyframe, editor);
        this.axis = axis;

        this.roamAbsoluteValue = new UITrackpad(this::setRoamAbsolute);
        this.roamAbsoluteValue.h(20);
        this.trackRelativeValue = new UITrackpad(this::setTrackRelative);
        this.trackRelativeValue.h(20);
        this.trackAbsoluteValue = new UITrackpad(this::setTrackAbsolute);
        this.trackAbsoluteValue.h(20);

        // 游动只剩「绝对」一种语义：按钮保留为静态标识（不可切换），文本恒为「绝对」
        this.roamAbsoluteButton = this.modeButton(b -> {});
        this.trackRelativeButton = this.modeButton(b -> this.setTrackMode(0));
        this.trackAbsoluteButton = this.modeButton(b -> this.setTrackMode(1));

        // 组1 · 游动
        UILabel roamTitle = new UILabel(IKey.raw("游动"), COLOR_ROAM);
        roamTitle.relative(this.scroll).w(1.0F).h(20);
        this.scroll.add(roamTitle);
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

    // —— 模式切换（仅追踪保留相对 / 绝对，游动恒为绝对）——

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

    /** 刷新按钮文本与选中态（游动恒为绝对并仅在该路线下高亮；追踪按模式高亮）。 */
    private void refreshButtons() {
        XyzFrameData data = this.frameData();
        boolean tracked = this.isTracked();
        int trackMode = data == null ? 0 : data.trackMode;

        this.roamAbsoluteButton.label = IKey.raw("绝对");
        this.trackRelativeButton.label = IKey.raw("相对");
        this.trackAbsoluteButton.label = IKey.raw("绝对");

        // 游动只有绝对一种语义：本路线下常亮，非本路线时仅保留文字色（不可点）
        this.styleButton(this.roamAbsoluteButton, COLOR_ROAM, !tracked);
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

    // —— 数值编辑回调（只改当前帧，改完全刷数值框）——

    /**
     * 游动·绝对（唯一入口）：直接写世界绝对坐标，并把该帧归一为绝对模式。
     * 旧存档中 roamMode == 0（历史相对态）的帧一经用户编辑即静默迁移为 1，
     * 迁移瞬间世界坐标保持不变（旧值按 roamValue + offset 读出，写回时已是绝对值）。
     */
    private void setRoamAbsolute(double value) {
        XyzFrameData data = this.frameData();
        if (data != null) {
            data.roamValue = value;
            data.roamMode = 1;
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

    /** 剪辑级「偏移」调试值（该轴）：仅用于读取旧存档 roamMode == 0 的历史相对态帧。 */
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

    /** 全刷数值框（游动按绝对口径读；追踪按当前模式换算相对 / 绝对表示）。 */
    private void updateValues() {
        XyzFrameData data = this.frameData();
        if (data == null) {
            return;
        }
        // 旧存档 roamMode == 0 的帧按「roamValue + offset」读出，保证升级前后画面零偏移
        this.roamAbsoluteValue.setValue(data.roamMode == 1 ? data.roamValue : data.roamValue + this.debugOffset());

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
