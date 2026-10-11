package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import perspective.better_camera_perspective.client.clips.MultiTrackerClientClip;

/**
 * XYZ 关键帧值双值联动编辑工厂（v1.0 / v1.1 / v1.5.4-Alpha 五版修订）。
 *
 * 选中多样追踪剪辑的 X / Y / Z 关键帧时，同时显示两个数值：
 *  - 相对值：该视角相对于被绑定模型及部位（或默认位置）的相对偏移 —— 直接写入关键帧；
 *  - 绝对值：该视角在地图上的绝对坐标。
 * 两个值任改其一，另一个值按「该 tick 的追踪锚点」自动同步换算（绝对 = 相对 + 锚点）。
 *
 * v1.1：两个行首的锁定标签升级为「主继承模式」开关。
 * v1.5.4-Alpha 四版：主继承模式拆分为「游动 / 追踪」两条路线（anchor 是否绑定模型自动路由）。
 * v1.5.4-Alpha 五版修订：改为 **四个独立按钮**，不再随路线切换文本：
 *  - 游动·相对 / 游动·绝对（品红色标识）—— anchor 关键帧值为 <无> 时生效，
 *    分别写入 {@link MultiTrackerClientClip#roamInheritMode} 0 / 1；
 *  - 追踪·相对 / 追踪·绝对（淡蓝色标识）—— anchor 关键帧值绑定模型 / 伪装时生效，
 *    分别写入 {@link MultiTrackerClientClip#trackInheritMode} 0 / 1。
 * 当前路线按 anchor 关键帧值判定，对应路线的选中按钮高亮（背景色 + 白字）。
 */
@Environment(EnvType.CLIENT)
public class UIXyzKeyframeFactory extends UIKeyframeFactory<Double> {
    /** 游动路线标识色（品红）。 */
    private static final int COLOR_ROAM = 0xFF00FF;
    /** 追踪路线标识色（淡蓝）。 */
    private static final int COLOR_TRACK = 0x87CEFA;

    private final int axis;
    private final UITrackpad relativeValue;
    private final UITrackpad absoluteValue;
    private final UIButton roamRelativeButton;
    private final UIButton roamAbsoluteButton;
    private final UIButton trackRelativeButton;
    private final UIButton trackAbsoluteButton;

    public UIXyzKeyframeFactory(Keyframe<Double> keyframe, UIKeyframes editor, int axis) {
        super(keyframe, editor);
        this.axis = axis;

        this.relativeValue = new UITrackpad(this::setRelative);
        this.relativeValue.tooltip(L10n.lang("bbs.ui.camera.panels.xyz.relative"));
        this.absoluteValue = new UITrackpad(this::setAbsolute);
        this.absoluteValue.tooltip(L10n.lang("bbs.ui.camera.panels.xyz.absolute"));

        this.roamRelativeButton = this.modeButton(b -> this.setRoamMode(0));
        this.roamAbsoluteButton = this.modeButton(b -> this.setRoamMode(1));
        this.trackRelativeButton = this.modeButton(b -> this.setTrackMode(0));
        this.trackAbsoluteButton = this.modeButton(b -> this.setTrackMode(1));

        this.scroll.add(UI.row(2, 0, 0, this.roamRelativeButton, this.roamAbsoluteButton));
        this.scroll.add(UI.row(2, 0, 0, this.trackRelativeButton, this.trackAbsoluteButton));
        this.scroll.add(UI.row(2, 0, 0, this.relativeValue));
        this.scroll.add(UI.row(2, 0, 0, this.absoluteValue));

        this.refreshButtons();
        this.updateValues();
    }

    /** 主继承模式按钮（可点击开关）。 */
    private UIButton modeButton(java.util.function.Consumer<UIButton> callback) {
        UIButton button = new UIButton(IKey.raw(""), callback);
        button.wh(104, 16);
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

    /** v1.5.4-Alpha 四版：当前帧是否处于「追踪」路线（anchor 关键帧值绑定目标）。 */
    private boolean isTracked() {
        MultiTrackerClientClip clip = this.findClip();
        if (clip == null) {
            return false;
        }
        return UIMultiTrackerClip.hasTrackTarget(clip, this.keyframe.getTick());
    }

    /** 刷新四个模式按钮的文本与标识状态（游动 = 品红 / 追踪 = 淡蓝；激活 = 背景色 + 白字）。 */
    private void refreshButtons() {
        MultiTrackerClientClip clip = this.findClip();
        boolean tracked = this.isTracked();
        int roamMode = clip == null ? 0 : clip.roamInheritMode;
        int trackMode = clip == null ? 0 : clip.trackInheritMode;

        this.roamRelativeButton.label = IKey.raw(L10n.lang("bbs.ui.camera.panels.xyz.roam_relative").get());
        this.roamAbsoluteButton.label = IKey.raw(L10n.lang("bbs.ui.camera.panels.xyz.roam_absolute").get());
        this.trackRelativeButton.label = IKey.raw(L10n.lang("bbs.ui.camera.panels.xyz.track_relative").get());
        this.trackAbsoluteButton.label = IKey.raw(L10n.lang("bbs.ui.camera.panels.xyz.track_absolute").get());

        this.styleButton(this.roamRelativeButton, COLOR_ROAM, !tracked && roamMode == 0);
        this.styleButton(this.roamAbsoluteButton, COLOR_ROAM, !tracked && roamMode == 1);
        this.styleButton(this.trackRelativeButton, COLOR_TRACK, tracked && trackMode == 0);
        this.styleButton(this.trackAbsoluteButton, COLOR_TRACK, tracked && trackMode == 1);
    }

    /** 按钮样式：激活 = 标识色背景 + 白字；未激活 = 标识色文字 + 透明底（区分两组归属）。 */
    private void styleButton(UIButton button, int color, boolean active) {
        if (active) {
            button.color(color).background(true).textColor(0xFFFFFF, true);
        } else {
            button.background(false).textColor(color, false);
        }
    }

    /** 相对值编辑：关键帧值 = 输入；绝对值同步显示 = 输入 + 锚点。 */
    private void setRelative(double value) {
        this.setValue(value);
        this.absoluteValue.setValue(value + this.anchor());
    }

    /** 绝对值编辑：关键帧值 = 输入 − 锚点（相对偏移）；相对值同步显示。 */
    private void setAbsolute(double value) {
        double anchor = this.anchor();
        this.setValue(value - anchor);
        this.relativeValue.setValue(value - anchor);
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
        this.relativeValue.setValue(value);
        this.absoluteValue.setValue(value + anchor);
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
