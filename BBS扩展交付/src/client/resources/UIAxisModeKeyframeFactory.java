package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import perspective.better_camera_perspective.client.clips.MultiTrackerClientClip;

/**
 * 三向轴模式（v0.3.1）关键帧值 UI 工厂。
 *
 * 「三向轴模式」轴的关键帧值采用用户选择式编辑（相对 / 绝对 / 自定义），
 * 类似 anchor 轴的「选中演员及部位绑定」的用户选择方式，而非 0/1/2 循环切换。
 * 选择某个模式时：
 *  - 把当前关键帧值写入三向轴通道（只读取最新关键帧值，优先级最高）；
 *  - 同步转换 XYZ 关键帧的取值语义（相对 ↔ 绝对，按该时刻锚点换算，画面不跳变）。
 */
@Environment(EnvType.CLIENT)
public class UIAxisModeKeyframeFactory extends UIKeyframeFactory<String> {
    private final UIButton mode;

    public UIAxisModeKeyframeFactory(Keyframe<String> keyframe, UIKeyframes editor) {
        super(keyframe, editor);
        this.mode = new UIButton(this.modeLabel(keyframe.getValue()), b -> this.openMenu());
        this.mode.tooltip(L10n.lang("bbs.ui.camera.panels.axis_axis_mode"));
        this.scroll.add(this.mode);
    }

    private mchorse.bbs_mod.l10n.keys.IKey modeLabel(String value) {
        return L10n.lang("bbs.ui.camera.panels.axis_axis_mode." + value);
    }

    private void openMenu() {
        this.getContext().replaceContextMenu(menu -> {
            menu.autoKeys();
            menu.action(Icons.ADD, L10n.lang("bbs.ui.camera.panels.axis_axis_mode.relative"), 0, () -> this.pick("relative"));
            menu.action(Icons.ADD, L10n.lang("bbs.ui.camera.panels.axis_axis_mode.absolute"), 0, () -> this.pick("absolute"));
            menu.action(Icons.ADD, L10n.lang("bbs.ui.camera.panels.axis_axis_mode.custom"), 0, () -> this.pick("custom"));
        });
    }

    private void pick(String value) {
        // 写入当前关键帧值（播放端只读取最新关键帧值，优先级最高）
        this.editor.getGraph().setValue(value, true, true);
        // 同步转换 XYZ 关键帧语义（相对 ↔ 绝对），画面不跳变
        MultiTrackerClientClip clip = this.findClip();
        UIFilmPanel panel = this.getPanel();
        if (clip != null && panel != null) {
            UIMultiTrackerClip.convertAll(clip, panel, value);
        }
        this.mode.label = this.modeLabel(value);
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
