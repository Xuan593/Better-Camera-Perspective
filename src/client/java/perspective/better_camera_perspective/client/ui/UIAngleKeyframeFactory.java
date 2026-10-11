package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * 角度（偏航 / 俯仰 / 横滚）关键帧值「调试 + 正式」双值联动编辑工厂（v1.5.4-Alpha 三版）。
 *
 *  - 调试：(-360, 360] 内的度内值（正式对 360 取余归一，如正式 361 → 调试 1、正式 -722 → 调试 -2）；
 *  - 正式：不限（支持 361、-722 等多圈角度，直接写入关键帧通道，圈数自动换算）。
 *
 * 两个值任改其一，另一值按「整圈数」自动换算：
 *  - 编辑调试：正式 = 当前圈数 × 360 + 新调试（圈数保持不变）；
 *  - 编辑正式：直接写入通道，调试 = 正式 mod 360（圈数随之更新）。
 */
@Environment(EnvType.CLIENT)
public class UIAngleKeyframeFactory extends UIKeyframeFactory<Double> {
    private final UITrackpad debugValue;
    private final UITrackpad formalValue;
    private int turns;

    public UIAngleKeyframeFactory(Keyframe<Double> keyframe, UIKeyframes editor) {
        super(keyframe, editor);

        this.debugValue = new UITrackpad(this::setDebug);
        this.debugValue.tooltip(L10n.lang("bbs.ui.camera.panels.xyz.angle_debug"));
        this.formalValue = new UITrackpad(this::setFormal);
        this.formalValue.tooltip(L10n.lang("bbs.ui.camera.panels.xyz.angle_formal"));

        this.scroll.add(UI.row(2, 0, 0, this.debugValue));
        this.scroll.add(UI.row(2, 0, 0, this.formalValue));

        this.updateValues();
    }

    /** 调试值编辑：保持当前圈数，正式 = 圈数 × 360 + 新调试。 */
    private void setDebug(double value) {
        this.setValue(this.turns * 360.0D + value);
        this.formalValue.setValue(this.turns * 360.0D + value);
    }

    /** 正式值编辑：直接写入通道；调试与圈数随之更新。 */
    private void setFormal(double value) {
        this.setValue(value);
        this.updateValues();
    }

    private void updateValues() {
        double formal = this.keyframe.getValue();
        double debug = formal % 360.0D;
        this.turns = (int) Math.round((formal - debug) / 360.0D);
        this.debugValue.setValue(debug);
        this.formalValue.setValue(formal);
    }

    @Override
    public void update() {
        super.update();
        this.updateValues();
    }
}
