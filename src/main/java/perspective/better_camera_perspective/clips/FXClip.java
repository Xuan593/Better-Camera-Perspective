package perspective.better_camera_perspective.clips;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * 特效移植（FX Port）——服务端剪辑类（v1.2-Alpha 实验性更新）。
 *
 * 把「后期」工作搬到「前期」：在时间轴上为本剪辑打关键帧，即可为画面叠加
 * 色度转场（单色度 / 渐变双色度）或模糊转场（全面指向模糊 / 点扩散模糊）。
 *
 * 所有可动画参数均以关键帧轴（KeyframeChannel）形式暴露，经
 * 关键帧编辑界面（编辑按钮 → 轴关键帧 → 关键帧属性）逐帧设置：
 *  - 色度转场：color1（起始色度）、color2（结束色度，空/同色 = 单色度）、amount（覆盖度）；
 *  - 模糊转场：blur（模糊强度）、blurX / blurY（点扩散中心偏移像素）、
 *    spreadMode（0=基础 1=扩散 2=收缩）、spreadType（0=圆形 1=百分比 2=表达式）、
 *    expr（扩散表达式，GLSL 片段，变量 x / y / r）。
 *
 * 剪辑自身仅带一个属性：mode（0=色度转场，1=模糊转场）。
 */
public class FXClip extends CameraClip {
    /** 特效类型：0=色度转场，1=模糊转场。 */
    public final ValueInt mode = new ValueInt("mode", 0, 0, 1);

    // —— 色度转场轴 ——
    public final KeyframeChannel<Color> color1 = new KeyframeChannel<>("color1", KeyframeFactories.COLOR);
    public final KeyframeChannel<Color> color2 = new KeyframeChannel<>("color2", KeyframeFactories.COLOR);
    public final KeyframeChannel<Double> amount = new KeyframeChannel<>("amount", KeyframeFactories.DOUBLE);

    // —— 模糊转场轴 ——
    public final KeyframeChannel<Double> blur = new KeyframeChannel<>("blur", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> blurX = new KeyframeChannel<>("blurX", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> blurY = new KeyframeChannel<>("blurY", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> spreadMode = new KeyframeChannel<>("spreadMode", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> spreadType = new KeyframeChannel<>("spreadType", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<String> expr = new KeyframeChannel<>("expr", KeyframeFactories.STRING);

    public FXClip() {
        this.add(this.mode);
        this.add(this.color1);
        this.add(this.color2);
        this.add(this.amount);
        this.add(this.blur);
        this.add(this.blurX);
        this.add(this.blurY);
        this.add(this.spreadMode);
        this.add(this.spreadType);
        this.add(this.expr);
    }

    @Override
    protected void applyClip(ClipContext context, Position position) {
        FXData data = new FXData();
        float tick = (float) context.relativeTick;

        data.mode = this.mode.get();
        data.layer = this.layer.get();

        // 色度转场：color1 / color2 / amount 按当前 tick 插值；通道无关键帧时回退默认
        data.color1 = this.color1.isEmpty() ? null : this.color1.interpolate(tick);
        data.color2 = this.color2.isEmpty() ? null : this.color2.interpolate(tick);
        data.amount = this.amount.isEmpty() ? 1.0D : this.amount.interpolate(tick);

        // 模糊转场：blur / 中心 / 模式 / 类型 / 表达式按当前 tick 插值
        data.blur = this.blur.isEmpty() ? 0.0D : this.blur.interpolate(tick);
        data.blurX = this.blurX.isEmpty() ? 0.0D : this.blurX.interpolate(tick);
        data.blurY = this.blurY.isEmpty() ? 0.0D : this.blurY.interpolate(tick);
        data.spreadMode = this.spreadMode.isEmpty() ? 0 : (int) (double) this.spreadMode.interpolate(tick);
        data.spreadType = this.spreadType.isEmpty() ? 0 : (int) (double) this.spreadType.interpolate(tick);
        data.expr = this.expr.isEmpty() ? "" : this.expr.interpolate(tick);

        FXData.get(context).add(data);
    }

    @Override
    protected Clip create() {
        return new FXClip();
    }
}
