package perspective.better_camera_perspective.clips;

import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * 特效移植（FX Port）——单帧特效数据（v1.2-Alpha 实验性更新）。
 *
 * 由 {@link FXClip#applyClip} 在每帧求值时写入 context.clipData（键 "fx"），
 * 客户端渲染层（FXOverlays）读取后执行：
 *  - 色度转场（mode=0）：全屏单色度 / 渐变双色度覆盖层；
 *  - 模糊转场（mode=1）：对当前帧画面执行点扩散模糊后期（GL）。
 *
 * 取多个活动特效剪辑时按 layer（剪辑层级）取最高者，低层剪辑让位。
 */
public class FXData {
    /** 特效类型：0=色度转场，1=模糊转场。 */
    public int mode = 0;
    /** 色度转场：起始色度（null = 无效通道，按黑色处理）。 */
    public Color color1 = null;
    /** 色度转场：结束色度（null 或与 color1 相同 = 单色度）。 */
    public Color color2 = null;
    /** 色度转场：覆盖度 0..1（由 amount 关键帧轴驱动）。 */
    public double amount = 1.0;
    /** 模糊转场：模糊强度 0..1（由 blur 关键帧轴驱动）。 */
    public double blur = 0.0;
    /** 模糊转场：点扩散中心 X 偏移（像素，0 = 预览画面正中心）。 */
    public double blurX = 0.0;
    /** 模糊转场：点扩散中心 Y 偏移（像素，0 = 预览画面正中心）。 */
    public double blurY = 0.0;
    /** 模糊转场：扩散模式 0=基础（全面指向模糊） 1=扩散 2=收缩。 */
    public int spreadMode = 0;
    /** 模糊转场：扩散类型 0=圆形 1=百分比 2=表达式。 */
    public int spreadType = 0;
    /** 模糊转场：扩散表达式（spreadType=2 时生效，GLSL 片段，变量 x/y/r）。 */
    public String expr = "";
    /** 剪辑层级，用于多个特效剪辑同时活动时取最高层。 */
    public int layer = 0;

    public static List<FXData> get(ClipContext context) {
        return context.clipData.get("fx", ArrayList::new);
    }
}
