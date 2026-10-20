package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import perspective.better_camera_perspective.data.PhaseFrameData;

import java.util.function.Consumer;

/**
 * 相位追踪轴帧值编辑工厂（V1.6.1-Alpha → V1.6.1 转正）。
 *
 * 选中多样追踪剪辑的「相位追踪」帧级关键帧时，显示（亮紫 0xAA66CC）：
 *
 *   相位追踪
 *   [关 / 开] 跟随强度 [0.9]
 *
 *  - 开关：点击切换本帧开关（默认关），事件性（某帧开 → 播放端持续生效直到某帧关）；
 *  - 跟随强度：数值框限制 0~1（默认 0.9：完全盯住演员；调低 = 部分跟随、更平滑）。
 *
 * 值只写当前选中帧（PhaseFrameData），不污染 xd/yd/zd 四值存储与 BBS 全局 PROPERTIES。
 */
@Environment(EnvType.CLIENT)
public class UIPhaseKeyframeFactory extends UIKeyframeFactory<PhaseFrameData> {
    /** 相位追踪轴标识色（亮紫，区别于游动品红 / 追踪淡蓝）。 */
    private static final int COLOR_PHASE = 0xAA66CC;

    private final UIButton toggleButton;
    private final UITrackpad smoothValue;

    public UIPhaseKeyframeFactory(Keyframe<PhaseFrameData> keyframe, UIKeyframes editor) {
        super(keyframe, editor);

        this.toggleButton = new UIButton(IKey.raw(""), this::toggle);
        this.toggleButton.wh(64, 16);
        this.smoothValue = new UITrackpad(this::setSmooth);
        this.smoothValue.h(20);
        // V1.6.1：跟随强度取值范围限制 0~1（播放端同样 clamp，避免 -3.85 之类越界值）
        this.smoothValue.limit(0, 1);

        UILabel title = new UILabel(IKey.raw("相位追踪"), COLOR_PHASE);
        title.relative(this.scroll).w(1.0F).h(20);
        this.scroll.add(title);

        // 两行对齐式：固定宽度标签 + 控件（与 XYZ 值面板行式风格统一，消除拥挤错位）
        UILabel onLabel = new UILabel(IKey.raw("开关"), COLOR_PHASE);
        onLabel.wh(88, 16);
        UILabel smoothLabel = new UILabel(IKey.raw("跟随强度"), COLOR_PHASE);
        smoothLabel.wh(88, 16);
        this.scroll.add(UI.row(2, 0, 0, onLabel, this.toggleButton));
        this.scroll.add(UI.row(2, 0, 0, smoothLabel, this.smoothValue));

        this.refresh();
    }

    /** 取当前帧的帧级数据（可变引用）。 */
    private PhaseFrameData frameData() {
        return (PhaseFrameData) this.keyframe.getValue();
    }

    /** 开关点击：切换本帧 enabled（false ⇄ true）。 */
    private void toggle(UIButton button) {
        PhaseFrameData data = this.frameData();
        if (data != null) {
            data.enabled = !data.enabled;
        }
        this.refresh();
    }

    /** 跟随强度编辑（0~1；写当前帧，播放端会 clamp）。 */
    private void setSmooth(double value) {
        PhaseFrameData data = this.frameData();
        if (data != null) {
            data.smooth = value;
        }
        this.refresh();
    }

    /** 全刷开关按钮与强度框（选中态着色：开 = 标识色背景 + 白字；关 = 标识色文字 + 透明底）。 */
    private void refresh() {
        PhaseFrameData data = this.frameData();
        boolean enabled = data != null && data.enabled;
        double smooth = data == null ? 0.9D : data.smooth;

        this.toggleButton.label = IKey.raw(enabled ? "开" : "关");
        if (enabled) {
            this.toggleButton.color(COLOR_PHASE).background(true).textColor(0xFFFFFF, true);
        } else {
            this.toggleButton.background(false).textColor(COLOR_PHASE, false);
        }
        this.smoothValue.setValue(smooth);
    }

    @Override
    public void update() {
        super.update();
        this.refresh();
    }
}
