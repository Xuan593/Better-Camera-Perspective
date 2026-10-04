package perspective.better_camera_perspective.client.clips;

import java.util.Collections;
import java.util.List;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.misc.TrackerFrame;
import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.joml.Matrices;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * 多样追踪（MultiTracker）——客户端剪辑类。
 *
 * 继承 {@link KeyframeClip}（拥有 8 个关键帧通道 + additive），
 * 并复制了 {@code TrackerClientClip} 的骨骼追踪求值逻辑（位移位 0-2、角度位 3-5）。
 *
 * 播放求值顺序：
 *   1. 追踪求值（若设置了追踪目标，按 active 位掩码用目标骨骼矩阵覆盖对应的位置/角度轴）；
 *   2. 关键帧插值（X/Y/Z/yaw/pitch/roll/fov/distance 由曲线驱动，等效「关键帧」剪辑）——关键帧优先。
 *
 * 默认 active = 63（六位全开）时行为接近原版「追踪器」；关掉某一位后，
 * 该轴改由关键帧曲线驱动——这就是「多样追踪」的核心玩法。
 */
@Environment(EnvType.CLIENT)
public class MultiTrackerClientClip extends KeyframeClip {
    /** 追踪目标演员的 replay 稳定 ID。 */
    public final ValueString selector = new ValueString("selector", "");
    /** 相对追踪目标的偏移。 */
    public final ValuePoint offset = new ValuePoint("offset", new Point(0.0, 0.0, 0.0));
    /** 追踪视角角度。 */
    public final ValuePoint angle = new ValuePoint("angle", new Point(0.0, 0.0, 0.0));
    /** 追踪的骨骼/附件路径。 */
    public final ValueString group = new ValueString("group", "");
    /** 看向目标。 */
    public final ValueBoolean lookAt = new ValueBoolean("look_at", false);
    /** 相对模式。 */
    public final ValueBoolean relative = new ValueBoolean("relative");
    /** 位掩码（0-2 位移，3-5 角度；默认 63 全开）。 */
    public final ValueInt active = new ValueInt("active", 63, 0, 63);
    /** 轴数值模式（v0.2）："relative"（默认）= XYZ 关键帧为相对锚点的偏移；"absolute" = 绝对值。 */
    public final ValueString axisMode = new ValueString("axis_mode", "relative");
    /** 相对模式所需的进入本剪辑前的相机位置（等效 EntityClip.position）。 */
    public Position position = new Position(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

    public MultiTrackerClientClip() {
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
    public void applyClip(ClipContext context, Position position) {
        // 1) 追踪求值（TrackerClientClip 的逻辑，去掉 fov 位——fov 始终由关键帧通道驱动）。
        boolean additive = this.additive.get();
        boolean absolute = "absolute".equals(this.axisMode.get());
        boolean tracked = false;
        double anchorX = 0.0D, anchorY = 0.0D, anchorZ = 0.0D;

        List<IEntity> entities = this.getEntities(context);
        if (!entities.isEmpty()) {
            if (!context.applyUnderneath(this.tick.get(), 0.0F, this.position)) {
                this.position.copy(position);
            }

            TrackerFrame frame = TrackerFrame.resolve(
                ((CameraClipContext) context).entities, entities.get(0), this.group.get(),
                position.point.x, position.point.y, position.point.z, context.transition
            );
            if (frame != null) {
                if (this.relative.get()) {
                    frame.relative(position, this.position);
                }

                Point offset = this.offset.get();
                Point angle = this.angle.get();
                boolean lookAt = this.lookAt.get();
                Angle newAngle = lookAt ? frame.lookAtAngles(offset, angle) : frame.angles(angle);

                if (lookAt) {
                    // lookAt：XYZ 不追踪（由关键帧绝对值驱动），仅角度看向目标
                    position.angle.yaw = this.isActive(3) ? newAngle.yaw : position.angle.yaw;
                    position.angle.pitch = this.isActive(4) ? newAngle.pitch : position.angle.pitch;
                    position.angle.roll = this.isActive(5) ? newAngle.roll : position.angle.roll;
                } else if (additive || absolute) {
                    // 叠加模式 或 绝对值模式：追踪直接覆盖 XYZ（absolute 下最终由关键帧绝对值覆盖，
                    // 即与「关键帧」剪辑一致的取值方式；未打关键帧的轴仍停留在追踪锚点）
                    Vector3d newPosition = frame.position(offset);
                    position.point.x = this.isActive(0) ? newPosition.x : position.point.x;
                    position.point.y = this.isActive(1) ? newPosition.y : position.point.y;
                    position.point.z = this.isActive(2) ? newPosition.z : position.point.z;
                    position.angle.yaw = this.isActive(3) ? newAngle.yaw : position.angle.yaw;
                    position.angle.pitch = this.isActive(4) ? newAngle.pitch : position.angle.pitch;
                    position.angle.roll = this.isActive(5) ? newAngle.roll : position.angle.roll;
                } else {
                    // 相对值模式（默认）：先存锚点位置，角度照常覆盖；XYZ 在关键帧插值后合成
                    Vector3d newPosition = frame.position(offset);
                    anchorX = newPosition.x;
                    anchorY = newPosition.y;
                    anchorZ = newPosition.z;
                    tracked = true;
                    position.angle.yaw = this.isActive(3) ? newAngle.yaw : position.angle.yaw;
                    position.angle.pitch = this.isActive(4) ? newAngle.pitch : position.angle.pitch;
                    position.angle.roll = this.isActive(5) ? newAngle.roll : position.angle.roll;
                }
            }
        }

        // 2) 关键帧插值（KeyframeClip 的全部逻辑）—— 关键帧优先。
        //    additive=false：非空通道直接覆盖对应轴；additive=true：叠加 underneath。
        super.applyClip(context, position);

        // 3) 相对值模式 + 追踪生效：XYZ = 锚点 + 关键帧偏移（关键帧通道为空 = 纯追踪锚点）
        if (tracked) {
            if (this.isActive(0)) {
                position.point.x = this.x.isEmpty() ? anchorX : anchorX + position.point.x;
            }
            if (this.isActive(1)) {
                position.point.y = this.y.isEmpty() ? anchorY : anchorY + position.point.y;
            }
            if (this.isActive(2)) {
                position.point.z = this.z.isEmpty() ? anchorZ : anchorZ + position.point.z;
            }
        }
    }

    public boolean isActive(int bit) {
        return (this.active.get() >> bit & 1) == 1;
    }

    public List<IEntity> getEntities(ClipContext context) {
        String id = this.selector.get();
        if (context instanceof CameraClipContext cameraClipContext && !id.isEmpty()) {
            IEntity entity = cameraClipContext.entities.get(id);
            if (entity != null) {
                return Collections.singletonList(entity);
            }
        }

        return Collections.emptyList();
    }

    @Override
    public Clip create() {
        return new MultiTrackerClientClip();
    }
}
