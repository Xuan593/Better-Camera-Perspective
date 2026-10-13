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
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Vector3d;
import org.joml.Vector3f;
import perspective.better_camera_perspective.client.data.XyzFrameData;
import perspective.better_camera_perspective.client.data.XyzFrameDataKeyframeFactory;
import perspective.better_camera_perspective.client.ui.UIMultiTrackerClip;

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
    /**
     * anchor 追踪轴（v0.3）：追踪目标（演员+部位）的时间轴覆盖通道，原理同模型数据的 anchor 轴。
     * 无关键帧 / 空值 → 回退剪辑属性 selector/group；有关键帧非空 → 以 anchor 轴为准；
     * 相邻关键帧目标不同时按 previous/x 过渡插值，跨演员平滑切换。
     */
    public final KeyframeChannel<Anchor> anchor = new KeyframeChannel<>("anchor", KeyframeFactories.ANCHOR);

    /**
     * 帧级小类存储通道（V1.5.4-Alpha 十版重构）：
     * 每个 XYZ 关键帧独立携带「游动值 / 追踪相对 / 追踪绝对 / 帧级游动模式 / 帧级追踪模式」，
     * 游动与追踪各存各的、模式每帧独立，不再共用 double 池或剪辑级共享模式，
     * 杜绝「游动段 ⇄ 追踪段」相互污染导致的坐标偏差。
     */
    public final KeyframeChannel<XyzFrameData> xd = new KeyframeChannel<>("xd", XyzFrameDataKeyframeFactory.INSTANCE);
    public final KeyframeChannel<XyzFrameData> yd = new KeyframeChannel<>("yd", XyzFrameDataKeyframeFactory.INSTANCE);
    public final KeyframeChannel<XyzFrameData> zd = new KeyframeChannel<>("zd", XyzFrameDataKeyframeFactory.INSTANCE);

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
        this.add(this.anchor);
        this.add(this.xd);
        this.add(this.yd);
        this.add(this.zd);
    }

    @Override
    public void applyClip(ClipContext context, Position position) {
        boolean additive = this.additive.get();
        boolean lookAt = this.lookAt.get();
        // v1.0：XYZ 关键帧固定为「相对值」语义（锚点 + 关键帧偏移合成；lookAt / 叠加除外）
        boolean relativeMode = !additive && !lookAt;
        // v1.5.4-Alpha 二版：进入本剪辑前的相机位置快照，供无锚点空通道回退（不飞回 0 0 0）
        Position basePosition = position.copy();

        // —— 1) 追踪目标解析（anchor 轴优先，v0.3）——
        String sel = this.selector.get();
        String grp = this.group.get();
        String fromSel = null;
        String fromGrp = null;
        float blend = 1.0F;
        boolean blendSource = false;

        if (context instanceof CameraClipContext) {
            Anchor anchor = this.anchor.isEmpty() ? null : this.anchor.interpolate((float) context.relativeTick + context.transition);
            if (anchor != null) {
                if (anchor.hasTarget()) {
                    sel = anchor.replay;
                    grp = anchor.attachment;
                    if (anchor.previous != null && anchor.previous.hasTarget() && !anchor.previous.hasSameTarget(anchor)) {
                        fromSel = anchor.previous.replay;
                        fromGrp = anchor.previous.attachment;
                        blend = anchor.x;
                        blendSource = true;
                    } else if (anchor.previous != null && !anchor.previous.hasTarget()) {
                        fromSel = this.selector.get();
                        fromGrp = this.group.get();
                        blend = anchor.x;
                        blendSource = true;
                    }
                } else if (anchor.previous != null && anchor.previous.hasTarget()) {
                    sel = this.selector.get();
                    grp = this.group.get();
                    fromSel = anchor.previous.replay;
                    fromGrp = anchor.previous.attachment;
                    blend = 1.0F - anchor.x;
                    blendSource = true;
                }
            }
        }

        // —— 2) 追踪求值（含过渡混合）——
        // 路线由 anchor 关键帧值判定（<无> = 游动；绑定模型 / 伪装 = 追踪；anchor 无关键帧回落剪辑属性）。
        float tick = (float) context.relativeTick + context.transition;
        boolean trackRoute = UIMultiTrackerClip.hasTrackTarget(this, tick);
        boolean tracked = false;
        boolean trackResolved = false;
        double anchorX = 0.0D, anchorY = 0.0D, anchorZ = 0.0D;
        // v1.5.4-Alpha 四版：记录追踪角度，供「关键帧空 / 无帧段」时回退（不把角度读成 0）
        float trackYaw = 0.0F, trackPitch = 0.0F, trackRoll = 0.0F;

        if (trackRoute && !sel.isEmpty()) {
            Position current = this.track(context, position, sel, grp, lookAt);
            tracked = true;
            if (current != null) {
                trackResolved = true;
                if (blendSource && blend < 1.0F) {
                    Position from = fromSel == null || fromSel.isEmpty() ? null : this.track(context, position, fromSel, fromGrp, lookAt);
                    Position base = from != null ? from : position.copy();
                    current = lerp(base, current, blend);
                }

                if (relativeMode) {
                    // 相对值语义（默认）：存追踪锚点，角度照常覆盖；XYZ 在关键帧插值后合成
                    anchorX = current.point.x;
                    anchorY = current.point.y;
                    anchorZ = current.point.z;
                    trackYaw = current.angle.yaw;
                    trackPitch = current.angle.pitch;
                    trackRoll = current.angle.roll;
                    position.angle.yaw = this.isActive(3) ? current.angle.yaw : position.angle.yaw;
                    position.angle.pitch = this.isActive(4) ? current.angle.pitch : position.angle.pitch;
                    position.angle.roll = this.isActive(5) ? current.angle.roll : position.angle.roll;
                } else {
                    // lookAt / 叠加：追踪直接覆盖对应轴（lookAt 时 XYZ 未被覆盖，保持关键帧绝对值）
                    position.point.x = this.isActive(0) ? current.point.x : position.point.x;
                    position.point.y = this.isActive(1) ? current.point.y : position.point.y;
                    position.point.z = this.isActive(2) ? current.point.z : position.point.z;
                    position.angle.yaw = this.isActive(3) ? current.angle.yaw : position.angle.yaw;
                    position.angle.pitch = this.isActive(4) ? current.angle.pitch : position.angle.pitch;
                    position.angle.roll = this.isActive(5) ? current.angle.roll : position.angle.roll;
                }
            } else {
                // 追踪目标解析失败（如绑定的伪装未实例化进影片）：锚点视为 0（关键帧值即绝对坐标），
                // 角度回退进入前值 —— 不把角度读成 0 / 平视跳变。
                trackYaw = basePosition.angle.yaw;
                trackPitch = basePosition.angle.pitch;
                trackRoll = basePosition.angle.roll;
            }
        }

        // —— 3) 关键帧插值（KeyframeClip 的全部逻辑）—— 关键帧优先。
        super.applyClip(context, position);

        // —— 3.5) XYZ 帧级小类存储求值 + 游动⇄追踪过渡（V1.5.4-Alpha 十版）——
        // 每帧独立的「游动值 / 追踪相对 / 追踪绝对 + 帧级模式」，三轴分别求最终世界坐标；
        // 游动 ⇄ 追踪段（anchor 相邻帧状态不同）两端按段内进度 lerp，
        // 不再瞬间跳转，也不再共用池子相互污染。仅在相对模式（非 lookAt / 非叠加）生效。
        if (relativeMode) {
            RouteBlend rb = this.routeBlend(context, tick, lookAt);
            Point debug = this.offset.get();

            if (this.isActive(0)) {
                position.point.x = this.resolvePointAxis(0, tick, tracked, trackResolved, anchorX, basePosition.point.x, debug.x, rb);
            }
            if (this.isActive(1)) {
                position.point.y = this.resolvePointAxis(1, tick, tracked, trackResolved, anchorY, basePosition.point.y, debug.y, rb);
            }
            if (this.isActive(2)) {
                position.point.z = this.resolvePointAxis(2, tick, tracked, trackResolved, anchorZ, basePosition.point.z, debug.z, rb);
            }
            if (this.isActive(3)) {
                position.angle.yaw = this.resolveAngleAxis(0, tick, tracked, trackYaw, basePosition.angle.yaw, rb);
            }
            if (this.isActive(4)) {
                position.angle.pitch = this.resolveAngleAxis(1, tick, tracked, trackPitch, basePosition.angle.pitch, rb);
            }
            if (this.isActive(5)) {
                position.angle.roll = this.resolveAngleAxis(2, tick, tracked, trackRoll, basePosition.angle.roll, rb);
            }
        }

        // —— 6) 视野：空 / 播放头无帧段回退进入前值（两条路线一致，不再被读成 0）——
        if (this.isActive(6)) {
            position.angle.fov = (float) safeValue(this.fov, tick, (double) basePosition.angle.fov);
        }
    }

    /**
     * v1.5.4-Alpha 四版：安全读取关键帧通道在当前 tick 的值。
     * 通道为空、或播放头不在任何关键帧段（此时 interpolate 会返回 0）时返回 fallback，
     * 否则返回插值——杜绝「空通道 / 无帧段被读成 0，相机与角度飞回原点」的跳变。
     */
    private static double safeValue(KeyframeChannel<Double> channel, float tick, double fallback) {
        return channel.isEmpty() || channel.findSegment(tick) == null ? fallback : channel.interpolate(tick);
    }

    /**
     * V1.5.4-Alpha 十版：检测 anchor 关键帧的「游动⇄追踪过渡段」。
     * tick 所在段相邻两帧 anchor 状态不同（<无> ⇄ 绑定模型）时返回混合信息，
     * 并解析追踪端（绑定端）在当前 tick 的锚点 Position（不与其他 anchor 帧混合）；
     * 两端状态相同返回 null（无过渡，走对应路线）。
     */
    private RouteBlend routeBlend(ClipContext context, float tick, boolean lookAt) {
        if (this.anchor == null || this.anchor.isEmpty()) {
            return null;
        }
        Keyframe a = null;
        Keyframe b = null;
        for (Keyframe keyframe : this.anchor.getKeyframes()) {
            if (keyframe.getTick() <= tick) {
                a = keyframe;
            } else if (b == null) {
                b = keyframe;
            }
        }
        if (a == null || b == null) {
            return null;
        }
        Anchor av = (Anchor) a.getValue();
        Anchor bv = (Anchor) b.getValue();
        boolean aTarget = av != null && av.hasTarget();
        boolean bTarget = bv != null && bv.hasTarget();
        if (aTarget == bTarget) {
            return null;
        }
        float span = b.getTick() - a.getTick();
        if (span <= 0.0F) {
            return null;
        }
        float t = Math.min(1.0F, Math.max(0.0F, (tick - a.getTick()) / span));
        RouteBlend rb = new RouteBlend(t);
        // 追踪端 = 绑定模型的 anchor 帧，单独解析其当前 tick 位置 / 角度
        Anchor trackEnd = aTarget ? av : bv;
        Position cur = this.track(context, this.position, trackEnd.replay, trackEnd.attachment, lookAt);
        if (cur != null) {
            rb.trackPoint[0] = (float) cur.point.x;
            rb.trackPoint[1] = (float) cur.point.y;
            rb.trackPoint[2] = (float) cur.point.z;
            rb.trackAngle[0] = cur.angle.yaw;
            rb.trackAngle[1] = cur.angle.pitch;
            rb.trackAngle[2] = cur.angle.roll;
            rb.trackResolved = true;
        }
        return rb;
    }

    /** 路线过渡信息：段内进度 t + 追踪端锚点在当前 tick 的位置 / 角度。 */
    private static final class RouteBlend {
        final float t;
        final float[] trackPoint = new float[3];
        final float[] trackAngle = new float[3];
        boolean trackResolved;

        RouteBlend(float t) {
            this.t = t;
        }
    }

    /** 取某轴当前 tick 的帧级数据（新通道优先；空时回退父类老 Double 通道，兼容老存档）。 */
    private static XyzFrameData fdAt(KeyframeChannel<XyzFrameData> data, KeyframeChannel<Double> old, float tick) {
        if (!data.isEmpty() && data.findSegment(tick) != null) {
            return data.interpolate(tick);
        }
        if (!old.isEmpty() && old.findSegment(tick) != null) {
            double v = old.interpolate(tick);
            return new XyzFrameData(v, v, v, 0, 0);
        }
        return null;
    }

    /** 游动端世界坐标：帧 roamValue（相对模式再叠加 offset 调试；绝对模式直接用）。无数据回退 baseAxis。 */
    private double roamWorld(KeyframeChannel<XyzFrameData> data, KeyframeChannel<Double> old, float tick,
                             double baseAxis, double debugAxis) {
        XyzFrameData fd = fdAt(data, old, tick);
        if (fd == null) {
            return baseAxis;
        }
        return fd.roamMode == 1 ? fd.roamValue : fd.roamValue + debugAxis;
    }

    /** 追踪端世界坐标：绝对模式用帧 trackAbsolute；相对模式用「锚点 + trackRelative」。无数据回退锚点 / baseAxis。 */
    private double trackWorld(KeyframeChannel<XyzFrameData> data, KeyframeChannel<Double> old, float tick,
                              double anchorAxis, boolean anchorResolved, double baseAxis) {
        XyzFrameData fd = fdAt(data, old, tick);
        if (fd == null) {
            return anchorResolved ? anchorAxis : baseAxis;
        }
        if (fd.trackMode == 1) {
            return fd.trackAbsolute;
        }
        return anchorResolved ? anchorAxis + fd.trackRelative : baseAxis;
    }

    /** 求一个位置轴的最终世界坐标（含游动⇄追踪过渡）。 */
    private double resolvePointAxis(int axis, float tick, boolean tracked, boolean trackResolved,
                                    double anchorAxis, double baseAxis, double debugAxis, RouteBlend rb) {
        KeyframeChannel<XyzFrameData> data = axis == 0 ? this.xd : axis == 1 ? this.yd : this.zd;
        KeyframeChannel<Double> old = axis == 0 ? this.x : axis == 1 ? this.y : this.z;
        boolean inBlend = rb != null && rb.t > 0.0F && rb.t < 1.0F;
        if (inBlend) {
            double roam = this.roamWorld(data, old, tick, baseAxis, debugAxis);
            double track = this.trackWorld(data, old, tick, rb.trackPoint[axis], rb.trackResolved, baseAxis);
            return roam + (track - roam) * (double) rb.t;
        }
        if (tracked) {
            return this.trackWorld(data, old, tick, anchorAxis, trackResolved, baseAxis);
        }
        return this.roamWorld(data, old, tick, baseAxis, debugAxis);
    }

    /** 求一个角度轴的最终角度（过渡段：游动端与追踪端按 t 走最短弧；否则关键帧插值，回退对应路线角度）。 */
    private float resolveAngleAxis(int axis, float tick, boolean tracked, float trackFallback,
                                   float baseFallback, RouteBlend rb) {
        KeyframeChannel<Double> ch = axis == 0 ? this.yaw : axis == 1 ? this.pitch : this.roll;
        boolean inBlend = rb != null && rb.t > 0.0F && rb.t < 1.0F;
        if (inBlend) {
            float roam = (float) safeValue(ch, tick, (double) baseFallback);
            float track = (float) safeValue(ch, tick, (double) rb.trackAngle[axis]);
            return lerpAngle(roam, track, rb.t);
        }
        return (float) safeValue(ch, tick, tracked ? (double) trackFallback : (double) baseFallback);
    }

    /** 追踪求值：返回按 active 位过滤后的 Position 副本；目标实体或帧无效时返回 null。 */
    private Position track(ClipContext context, Position position, String sel, String grp, boolean lookAt) {
        if (!(context instanceof CameraClipContext camera)) {
            return null;
        }

        IEntity entity = camera.entities.get(sel);
        if (entity == null) {
            return null;
        }

        if (!context.applyUnderneath(this.tick.get(), 0.0F, this.position)) {
            this.position.copy(position);
        }

        TrackerFrame frame = TrackerFrame.resolve(
            camera.entities, entity, grp, position.point.x, position.point.y, position.point.z, context.transition
        );
        if (frame == null) {
            return null;
        }

        if (this.relative.get()) {
            frame.relative(position, this.position);
        }

        Point offset = this.offset.get();
        Point angle = this.angle.get();
        Angle newAngle = lookAt ? frame.lookAtAngles(offset, angle) : frame.angles(angle);
        Position result = position.copy();
        if (lookAt) {
            // lookAt：XYZ 不追踪（由关键帧绝对值驱动），仅角度看向目标
            result.angle.yaw = this.isActive(3) ? newAngle.yaw : result.angle.yaw;
            result.angle.pitch = this.isActive(4) ? newAngle.pitch : result.angle.pitch;
            result.angle.roll = this.isActive(5) ? newAngle.roll : result.angle.roll;
        } else {
            Vector3d newPosition = frame.position(offset);
            result.point.x = this.isActive(0) ? newPosition.x : result.point.x;
            result.point.y = this.isActive(1) ? newPosition.y : result.point.y;
            result.point.z = this.isActive(2) ? newPosition.z : result.point.z;
            result.angle.yaw = this.isActive(3) ? newAngle.yaw : result.angle.yaw;
            result.angle.pitch = this.isActive(4) ? newAngle.pitch : result.angle.pitch;
            result.angle.roll = this.isActive(5) ? newAngle.roll : result.angle.roll;
        }
        return result;
    }

    /** 两个追踪结果按 t 混合（位置/角度逐分量 lerp，角度按最短弧插值）。 */
    private static Position lerp(Position a, Position b, float t) {
        Position result = new Position();
        result.point.x = a.point.x + (b.point.x - a.point.x) * (double) t;
        result.point.y = a.point.y + (b.point.y - a.point.y) * (double) t;
        result.point.z = a.point.z + (b.point.z - a.point.z) * (double) t;
        result.angle.yaw = lerpAngle(a.angle.yaw, b.angle.yaw, t);
        result.angle.pitch = lerpAngle(a.angle.pitch, b.angle.pitch, t);
        result.angle.roll = lerpAngle(a.angle.roll, b.angle.roll, t);
        return result;
    }

    /** 角度最短弧插值（BBS 角度为弧度制）。 */
    private static float lerpAngle(float a, float b, float t) {
        float diff = (float) Math.atan2(Math.sin(b - a), Math.cos(b - a));
        return a + diff * t;
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
