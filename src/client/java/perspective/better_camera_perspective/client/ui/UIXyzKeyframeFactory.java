package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
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

/**
 * XYZ 关键帧值双值联动编辑工厂（v1.0 / v1.1 / v1.5.4-Alpha 八版修订）。
 *
 * 选中多样追踪剪辑的 X / Y / Z 关键帧时，同时显示两组「相对 / 绝对」数值：
 *
 *   组1 · 游动（只影响 anchor 关键帧值为 <无> 的视角）—— 按钮品红 0xFFFF6666
 *     相对（ffff6666）：值
 *     绝对（ffff6666）：值
 *   组2 · 追踪（只影响 anchor 关键帧绑定模型的视角）—— 按钮淡蓝 0x87CEFA
 *     相对（淡蓝）：值
 *     绝对（淡蓝）：值
 *
 * 游动按钮写入 {@link MultiTrackerClientClip#roamInheritMode}（0 相对 / 1 绝对）；
 * 追踪按钮写入 {@link MultiTrackerClientClip#trackInheritMode}（0 相对 / 1 绝对）。
 * 当前路线（按 anchor 关键帧值判定）对应组内的选中按钮 = 标识色背景 + 白字。
 *
 * v1.5.4-Alpha 八版修订（组间独立）：
 *  - 游动组与追踪组的值**各自独立**：四个数值框各绑独立回调 ——
 *    改「游动·相对」只转化「游动·绝对」，不动追踪组；
 *    改「追踪·相对」只转化「追踪·绝对」，不动游动组；
 *  - 被编辑的组保持编辑态（update 不重算），另一组**冻结**显示快照，
 *    直到切换关键帧（工厂重建 → 两组按该帧语义全刷）。
 */
@Environment(EnvType.CLIENT)
public class UIXyzKeyframeFactory extends UIKeyframeFactory<Double> {
    /** 游动路线标识色（用户指定 0xFFFF6666，品红偏亮红）。 */
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
    /** 编辑游动组后冻结追踪组显示快照（换帧重建时解冻）。 */
    private boolean roamFrozen = false;
    /** 编辑追踪组后冻结游动组显示快照。 */
    private boolean trackFrozen = false;

    public UIXyzKeyframeFactory(Keyframe<Double> keyframe, UIKeyframes editor, int axis) {
        super(keyframe, editor);
        this.axis = axis;

        this.roamRelativeValue = new UITrackpad(this::setRoamRelative);
        this.roamRelativeValue.tooltip(L10n.lang("bbs.ui.camera.panels.xyz.relative"));
        this.roamAbsoluteValue = new UITrackpad(this::setRoamAbsolute);
        this.roamAbsoluteValue.tooltip(L10n.lang("bbs.ui.camera.panels.xyz.absolute"));
        this.trackRelativeValue = new UITrackpad(this::setTrackRelative);
        this.trackRelativeValue.tooltip(L10n.lang("bbs.ui.camera.panels.xyz.relative"));
        this.trackAbsoluteValue = new UITrackpad(this::setTrackAbsolute);
        this.trackAbsoluteValue.tooltip(L10n.lang("bbs.ui.camera.panels.xyz.absolute"));

        this.roamRelativeButton = this.modeButton(b -> this.setRoamMode(0));
        this.roamAbsoluteButton = this.modeButton(b -> this.setRoamMode(1));
        this.trackRelativeButton = this.modeButton(b -> this.setTrackMode(0));
        this.trackAbsoluteButton = this.modeButton(b -> this.setTrackMode(1));

        // 组1 · 游动
        UILabel roamTitle = new UILabel(IKey.raw("游动"), COLOR_ROAM);
        this.scroll.add(roamTitle);
        this.scroll.add(UI.row(2, 0, 0, this.roamRelativeButton, this.roamRelativeValue));
        this.scroll.add(UI.row(2, 0, 0, this.roamAbsoluteButton, this.roamAbsoluteValue));

        // 组2 · 追踪
        UILabel trackTitle = new UILabel(IKey.raw("追踪"), COLOR_TRACK);
        this.scroll.add(trackTitle);
        this.scroll.add(UI.row(2, 0, 0, this.trackRelativeButton, this.trackRelativeValue));
        this.scroll.add(UI.row(2, 0, 0, this.trackAbsoluteButton, this.trackAbsoluteValue));

        this.refreshButtons();
        this.updateValues();
    }

    /** 主继承模式按钮（可点击开关）。 */
    private UIButton modeButton(java.util.function.Consumer<UIButton> callback) {
        UIButton button = new UIButton(IKey.raw(""), callback);
        button.wh(64, 16);
        button.tooltip(L10n.lang("bbs.ui.camera.panels.xyz.inherit_tooltip"));
        return button;
    }

    /** 设置游动路线继承模式：0 = 相对，1 = 绝对。 */
    private void setRoamMode(int mode) {
        MultiTrackerClientClip clip = this.findClip();
        if (clip != null) {
            clip.roamInheritMode = mode;
        }
        this.refreshButtons();
    }

    /** 设置追踪路线继承模式：0 = 相对，1 = 绝对。 */
    private void setTrackMode(int mode) {
        MultiTrackerClientClip clip = this.findClip();
        if (clip != null) {
            clip.trackInheritMode = mode;
        }
        this.refreshButtons();
    }

    /** 当前帧是否处于「追踪」路线（anchor 关键帧值绑定目标）。 */
    private boolean isTracked() {
        MultiTrackerClientClip clip = this.findClip();
        if (clip == null) {
            return false;
        }
        return UIMultiTrackerClip.hasTrackTarget(clip, this.keyframe.getTick());
    }

    /** 刷新四组按钮的文本与标识状态（游动 = 品红 / 追踪 = 淡蓝；选中 = 背景色 + 白字）。 */
    private void refreshButtons() {
        MultiTrackerClientClip clip = this.findClip();
        boolean tracked = this.isTracked();
        int roamMode = clip == null ? 0 : clip.roamInheritMode;
        int trackMode = clip == null ? 0 : clip.trackInheritMode;

        this.roamRelativeButton.label = IKey.raw("相对");
        this.roamAbsoluteButton.label = IKey.raw("绝对");
        this.trackRelativeButton.label = IKey.raw("相对");
        this.trackAbsoluteButton.label = IKey.raw("绝对");

        this.styleButton(this.roamRelativeButton, COLOR_ROAM, !tracked && roamMode == 0);
        this.styleButton(this.roamAbsoluteButton, COLOR_ROAM, !tracked && roamMode == 1);
        this.styleButton(this.trackRelativeButton, COLOR_TRACK, tracked && trackMode == 0);
        this.styleButton(this.trackAbsoluteButton, COLOR_TRACK, tracked && trackMode == 1);
    }

    /** 按钮样式：选中 = 标识色背景 + 白字；未选中 = 标识色文字 + 透明底（区分两组归属）。 */
    private void styleButton(UIButton button, int color, boolean active) {
        if (active) {
            button.color(color).background(true).textColor(0xFFFFFF, true);
        } else {
            button.background(false).textColor(color, false);
        }
    }

    // —— 编辑回调：组间独立，只转化同类；另一组冻结快照 ——

    /** 编辑游动·相对：关键帧值 = 输入；只联动游动·绝对；追踪组冻结不动。 */
    private void setRoamRelative(double value) {
        this.setValue(value);
        this.roamAbsoluteValue.setValue(value + this.anchor());
        this.roamFrozen = false;
        this.trackFrozen = true;
    }

    /** 编辑游动·绝对：关键帧值 = 输入 − 锚点；只联动游动·相对；追踪组冻结不动。 */
    private void setRoamAbsolute(double value) {
        double anchor = this.anchor();
        this.setValue(value - anchor);
        this.roamRelativeValue.setValue(value - anchor);
        this.roamFrozen = false;
        this.trackFrozen = true;
    }

    /** 编辑追踪·相对：关键帧值 = 输入；只联动追踪·绝对；游动组冻结不动。 */
    private void setTrackRelative(double value) {
        this.setValue(value);
        this.trackAbsoluteValue.setValue(value + this.anchor());
        this.roamFrozen = true;
        this.trackFrozen = false;
    }

    /** 编辑追踪·绝对：关键帧值 = 输入 − 锚点；只联动追踪·相对；游动组冻结不动。 */
    private void setTrackAbsolute(double value) {
        double anchor = this.anchor();
        this.setValue(value - anchor);
        this.trackRelativeValue.setValue(value - anchor);
        this.roamFrozen = true;
        this.trackFrozen = false;
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

    private void refreshRoam() {
        double value = this.keyframe.getValue();
        this.roamRelativeValue.setValue(value);
        this.roamAbsoluteValue.setValue(value + this.anchor());
    }

    private void refreshTrack() {
        double value = this.keyframe.getValue();
        this.trackRelativeValue.setValue(value);
        this.trackAbsoluteValue.setValue(value + this.anchor());
    }

    /** 构造时全刷两组（未冻结）。 */
    private void updateValues() {
        this.refreshRoam();
        this.refreshTrack();
    }

    @Override
    public void update() {
        super.update();
        this.refreshButtons();
        // 组间独立：仅刷新未冻结的组；被编辑的组保持编辑态、另一组保持快照
        if (!this.roamFrozen) {
            this.refreshRoam();
        }
        if (!this.trackFrozen) {
            this.refreshTrack();
        }
    }

    private MultiTrackerClientClip findClip() {
        if (this.editor instanceof UIFilmKeyframes film && film.editor != null) {
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
