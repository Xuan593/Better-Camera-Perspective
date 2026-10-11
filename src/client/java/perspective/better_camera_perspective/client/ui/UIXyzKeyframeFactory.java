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
 * XYZ 关键帧值双值联动编辑工厂（v1.0 / v1.1 / v1.5.4-Alpha 六版修订）。
 *
 * 选中多样追踪剪辑的 X / Y / Z 关键帧时，同时显示两个数值：
 *  - 相对值：该视角相对于被绑定模型及部位（或默认位置）的相对偏移 —— 直接写入关键帧；
 *  - 绝对值：该视角在地图上的绝对坐标。
 * 两个值任改其一，另一个值按「该 tick 的追踪锚点」自动同步换算（绝对 = 相对 + 锚点）。
 *
 * v1.5.4-Alpha 六版修订（用户指定布局）：两组并列，每组标题 + 「相对 / 绝对」按钮 + 数值框：
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
 * 两组共 4 个数值框始终显示同一关键帧的相对 / 绝对（任一编辑全部同步，联动换算不变）。
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

    public UIXyzKeyframeFactory(Keyframe<Double> keyframe, UIKeyframes editor, int axis) {
        super(keyframe, editor);
        this.axis = axis;

        this.roamRelativeValue = new UITrackpad(this::setRelative);
        this.roamRelativeValue.tooltip(L10n.lang("bbs.ui.camera.panels.xyz.relative"));
        this.roamAbsoluteValue = new UITrackpad(this::setAbsolute);
        this.roamAbsoluteValue.tooltip(L10n.lang("bbs.ui.camera.panels.xyz.absolute"));
        this.trackRelativeValue = new UITrackpad(this::setRelative);
        this.trackRelativeValue.tooltip(L10n.lang("bbs.ui.camera.panels.xyz.relative"));
        this.trackAbsoluteValue = new UITrackpad(this::setAbsolute);
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

    /** 相对值编辑：关键帧值 = 输入；全部绝对值框同步显示 = 输入 + 锚点。 */
    private void setRelative(double value) {
        this.setValue(value);
        double anchor = this.anchor();
        this.roamRelativeValue.setValue(value);
        this.trackRelativeValue.setValue(value);
        this.roamAbsoluteValue.setValue(value + anchor);
        this.trackAbsoluteValue.setValue(value + anchor);
    }

    /** 绝对值编辑：关键帧值 = 输入 − 锚点（相对偏移）；全部相对值框同步显示。 */
    private void setAbsolute(double value) {
        double anchor = this.anchor();
        this.setValue(value - anchor);
        this.roamRelativeValue.setValue(value - anchor);
        this.trackRelativeValue.setValue(value - anchor);
        this.roamAbsoluteValue.setValue(value);
        this.trackAbsoluteValue.setValue(value);
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

    private void updateValues() {
        double value = this.keyframe.getValue();
        double anchor = this.anchor();
        this.roamRelativeValue.setValue(value);
        this.trackRelativeValue.setValue(value);
        this.roamAbsoluteValue.setValue(value + anchor);
        this.trackAbsoluteValue.setValue(value + anchor);
    }

    @Override
    public void update() {
        super.update();
        this.refreshButtons();
        this.updateValues();
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
