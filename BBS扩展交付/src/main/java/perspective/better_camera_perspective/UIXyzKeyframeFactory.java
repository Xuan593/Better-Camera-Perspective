package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
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
 * XYZ 关键帧值双值联动编辑工厂（v1.0）。
 *
 * 选中多样追踪剪辑的 X / Y / Z 关键帧时，同时显示两个数值：
 *  - 相对值：该视角相对于被绑定模型及部位（或默认位置）的相对偏移 —— 直接写入关键帧；
 *  - 绝对值：该视角在地图上的绝对坐标。
 * 两个值任改其一，另一个值按「该 tick 的追踪锚点」自动同步换算（绝对 = 相对 + 锚点），
 * 无需再手动切换「相对值 / 绝对值」模式。
 */
@Environment(EnvType.CLIENT)
public class UIXyzKeyframeFactory extends UIKeyframeFactory<Double> {
    private final int axis;
    private final UITrackpad relativeValue;
    private final UITrackpad absoluteValue;

    public UIXyzKeyframeFactory(Keyframe<Double> keyframe, UIKeyframes editor, int axis) {
        super(keyframe, editor);
        this.axis = axis;

        this.relativeValue = new UITrackpad(this::setRelative);
        this.relativeValue.tooltip(L10n.lang("bbs.ui.camera.panels.xyz.relative"));
        this.absoluteValue = new UITrackpad(this::setAbsolute);
        this.absoluteValue.tooltip(L10n.lang("bbs.ui.camera.panels.xyz.absolute"));

        this.scroll.add(UI.row(2, 0, 0, label(L10n.lang("bbs.ui.camera.panels.xyz.relative")), this.relativeValue));
        this.scroll.add(UI.row(2, 0, 0, label(L10n.lang("bbs.ui.camera.panels.xyz.absolute")), this.absoluteValue));

        this.updateValues();
    }

    private static UIElement label(IKey key) {
        UIButton label = new UIButton(key, b -> {
        });
        label.setEnabled(false);
        label.wh(72, 16);
        return label;
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
