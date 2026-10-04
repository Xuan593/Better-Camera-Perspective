package perspective.better_camera_perspective.clips;

import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;

/**
 * 多样追踪（MultiTracker）——服务端剪辑类。
 *
 * 继承 {@link KeyframeClip}：天然拥有 X / Y / Z / yaw / pitch / roll / fov / distance
 * 八个关键帧通道与 additive 开关（即「关键帧」剪辑的全部能力）。
 *
 * 同时携带「追踪器」剪辑的全部追踪参数（selector / offset / angle / group / look_at / relative / active），
 * 使剪辑既有追踪能力、又可对每个要素打关键帧曲线——即「有追踪能力的关键帧」。
 *
 * 服务端 applyClip 沿用 KeyframeClip 的关键帧插值；真正的骨骼追踪求值发生在
 * 客户端 {@code MultiTrackerClientClip} 中（与原版 TrackerClip / TrackerClientClip 的职责划分一致）。
 */
public class MultiTrackerClip extends KeyframeClip {
    /** 追踪目标演员的 replay 稳定 ID（与 TrackerClip.selector 相同）。 */
    public final ValueString selector = new ValueString("selector", "");
    /** 相对追踪目标的偏移。 */
    public final ValuePoint offset = new ValuePoint("offset", new Point(0.0, 0.0, 0.0));
    /** 追踪视角角度。 */
    public final ValuePoint angle = new ValuePoint("angle", new Point(0.0, 0.0, 0.0));
    /** 追踪的骨骼/附件路径（空 = 根骨骼）。 */
    public final ValueString group = new ValueString("group", "");
    /** 看向目标（lookAt 模式）。 */
    public final ValueBoolean lookAt = new ValueBoolean("look_at", false);
    /** 相对模式。 */
    public final ValueBoolean relative = new ValueBoolean("relative");
    /**
     * 位掩码（默认 63 = 0b111111，6 位全开）：
     * 0/1/2 = X/Y/Z 位移跟随；3/4/5 = yaw/pitch/roll 角度跟随。
     * 关闭某一位后，对应要素改由关键帧通道驱动。
     * 注：fov 不参与位掩码，始终由 fov 关键帧通道驱动（比原版追踪器更灵活）。
     */
    public final ValueInt active = new ValueInt("active", 63, 0, 63);
    /**
     * 轴数值模式（v0.2）：XYZ 关键帧的取值模式。
     * "relative"（默认）= 相对追踪锚点的偏移（播放时合成「锚点 + 关键帧偏移」）；
     * "absolute" = 与「关键帧」剪辑一致的绝对值。
     */
    public final ValueString axisMode = new ValueString("axis_mode", "relative");

    public MultiTrackerClip() {
        this.add(this.selector);
        this.add(this.offset);
        this.add(this.angle);
        this.add(this.group);
        this.add(this.lookAt);
        this.add(this.relative);
        this.add(this.active);
        this.add(this.axisMode);
    }

    @Override
    public Clip create() {
        return new MultiTrackerClip();
    }
}
