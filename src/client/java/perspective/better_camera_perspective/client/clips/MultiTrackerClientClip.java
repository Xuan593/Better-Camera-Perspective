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
    /** 相对模式所需的进入本剪辑前的相机位置（等效 EntityClip.position）。 */
    public Position position = new Position(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    /**
     * v1.1：XYZ 新建关键帧的「主继承模式」（UI 状态，不参与序列化）。
     * 0 = 相对值（默认）：下一帧新建关键帧继承「相对偏移」；
     * 1 = 绝对值：下一帧新建关键帧继承「绝对坐标」。
     * 由关键帧编辑界面的「将主继承相对值 / 将主继承绝对值」按钮切换。
     *
     * v1.5.4-Alpha 四版：拆分为「游动 / 追踪」两路线的继承小模式，
     * 本字段保留兼容（不再被 UI 写入）。
     */
    public int inheritMode = 0;

    /**
     * v1.5.4-Alpha 四版：「游动」路线（anchor 未绑定任何模型时自动生效）的继承小模式。
     * 0 = 相对（默认）：XYZ 关键帧存绝对坐标，播放再叠加 offset 相对调试值（最终 = 绝对 + 调试）；
     * 1 = 绝对：XYZ 关键帧存绝对坐标，播放直接用，offset 调试值不参与。
     * UI 状态，不参与序列化。
     */
    public int roamInheritMode = 0;

    /**
     * v1.5.4-Alpha 四版：「追踪」路线（anchor 绑定模型时自动生效）的继承小模式。
     * 0 = 相对（默认）：XYZ 关键帧存「相对锚点的偏移」，播放按「锚点 + 偏移」合成；
     * 1 = 绝对：XYZ 关键帧存「绝对坐标」，播放直接使用（不叠加锚点）。
     * UI 状态，不参与序列化。
     */
    public int trackInheritMode = 0;

    public MultiTrackerClientClip() {
        this.add(this.selector);
        this.add(this.offset);
        this.add(this.angle);
        this.add(this.group);
        this.add(this.lookAt);
        this.add(this.relative);
        this.add(this.active);
        this.add(this.anchor);
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

        // —— 3.5) 路线过渡（anchor 关键帧 <无> ⇄ 绑定模型之间的平滑混合）——
        // 游动视角（关键帧绝对 + offset 调试）→ 追踪视角（锚点 + 偏移 / 绝对）之间按段内进度 lerp，
        // 不再瞬间跳转。仅在相对模式（非 lookAt / 非叠加）生效，与步骤 4 / 5 同一前提。
        if (relativeMode) {
            RouteBlend rb = this.routeBlend(this.anchor, tick);
            if (rb != null && rb.t > 0.0F && rb.t < 1.0F) {
                // —— 游动端合成（同步骤 5 语义）——
                boolean roamAbs = this.roamInheritMode == 1;
                Point debug = this.offset.get();
                double roamX = safeValue(this.x, tick, basePosition.point.x);
                double roamY = safeValue(this.y, tick, basePosition.point.y);
                double roamZ = safeValue(this.z, tick, basePosition.point.z);
                if (!roamAbs) {
                    roamX += debug.x;
                    roamY += debug.y;
                    roamZ += debug.z;
                }
                float roamYaw = (float) safeValue(this.yaw, tick, (double) basePosition.angle.yaw);
                float roamPitch = (float) safeValue(this.pitch, tick, (double) basePosition.angle.pitch);
                float roamRoll = (float) safeValue(this.roll, tick, (double) basePosition.angle.roll);

                // —— 追踪端合成（同步骤 4 语义，锚点用绑定端 anchor 解析）——
                Anchor target = rb.a != null && rb.a.hasTarget() ? rb.a : rb.b;
                double tx = 0.0D, ty = 0.0D, tz = 0.0D;
                float tyaw = basePosition.angle.yaw, tpitch = basePosition.angle.pitch, troll = basePosition.angle.roll;
                if (target != null && target.hasTarget()) {
                    Position cur = this.track(context, position, target.replay, target.attachment, lookAt);
                    if (cur != null) {
                        tx = cur.point.x;
                        ty = cur.point.y;
                        tz = cur.point.z;
                        tyaw = cur.angle.yaw;
                        tpitch = cur.angle.pitch;
                        troll = cur.angle.roll;
                    }
                }
                boolean trackAbs = this.trackInheritMode == 1;
                double trackX = trackAbs ? safeValue(this.x, tick, tx) : tx + safeValue(this.x, tick, 0.0D);
                double trackY = trackAbs ? safeValue(this.y, tick, ty) : ty + safeValue(this.y, tick, 0.0D);
                double trackZ = trackAbs ? safeValue(this.z, tick, tz) : tz + safeValue(this.z, tick, 0.0D);
                trackYaw = (float) safeValue(this.yaw, tick, (double) tyaw);
                trackPitch = (float) safeValue(this.pitch, tick, (double) tpitch);
                trackRoll = (float) safeValue(this.roll, tick, (double) troll);

                // 混合方向：a 绑定 → b <无>（追踪→游动）反向；a <无> → b 绑定（游动→追踪）正向
                float t = rb.a != null && rb.a.hasTarget() ? 1.0F - rb.t : rb.t;
                if (this.isActive(0)) {
                    position.point.x = roamX + (trackX - roamX) * t;
                }
                if (this.isActive(1)) {
                    position.point.y = roamY + (trackY - roamY) * t;
                }
                if (this.isActive(2)) {
                    position.point.z = roamZ + (trackZ - roamZ) * t;
                }
                if (this.isActive(3)) {
                    position.angle.yaw = lerpAngle(roamYaw, trackYaw, t);
                }
                if (this.isActive(4)) {
                    position.angle.pitch = lerpAngle(roamPitch, trackPitch, t);
                }
                if (this.isActive(5)) {
                    position.angle.roll = lerpAngle(roamRoll, trackRoll, t);
                }
            } else if (tracked) {
                // —— 4) 追踪路线（anchor 绑定模型）：按「追踪继承小模式」合成 XYZ ——
                // 相对（默认）：锚点 + 关键帧偏移（通道空 / 播放头无帧段 = 纯追踪锚点）；
                // 绝对：关键帧值即绝对坐标（空 / 无帧段回退锚点）。
                // 角度：关键帧有值且播放头有帧段 → 保留插值；空 / 无帧段 → 回退追踪角度（不再被读成 0）。
                // 追踪未解析（trackResolved=false）：锚点 0，空通道 / 无帧段回退进入前位置（不飞 0 0 0）。
                boolean trackAbs = this.trackInheritMode == 1;
                if (this.isActive(0)) {
                    position.point.x = trackAbs
                        ? safeValue(this.x, tick, trackResolved ? anchorX : basePosition.point.x)
                        : anchorX + safeValue(this.x, tick, trackResolved ? 0.0D : basePosition.point.x);
                }
                if (this.isActive(1)) {
                    position.point.y = trackAbs
                        ? safeValue(this.y, tick, trackResolved ? anchorY : basePosition.point.y)
                        : anchorY + safeValue(this.y, tick, trackResolved ? 0.0D : basePosition.point.y);
                }
                if (this.isActive(2)) {
                    position.point.z = trackAbs
                        ? safeValue(this.z, tick, trackResolved ? anchorZ : basePosition.point.z)
                        : anchorZ + safeValue(this.z, tick, trackResolved ? 0.0D : basePosition.point.z);
                }
                if (this.isActive(3)) {
                    position.angle.yaw = (float) safeValue(this.yaw, tick, (double) trackYaw);
                }
                if (this.isActive(4)) {
                    position.angle.pitch = (float) safeValue(this.pitch, tick, (double) trackPitch);
                }
                if (this.isActive(5)) {
                    position.angle.roll = (float) safeValue(this.roll, tick, (double) trackRoll);
                }
            } else {
                // —— 5) 游动路线（anchor 未绑定模型）：XYZ 以绝对值为准 ——
                // 通道有值且播放头有帧段：关键帧值即绝对坐标；空 / 无帧段：回退进入本剪辑前的相机位置（不再飞回 0 0 0）。
                // 相对（默认）：再叠加 offset 相对调试值（最终 = 绝对 + 调试）；绝对：offset 不参与。
                // 角度：有值且播放头有帧段 → 保留插值；空 / 无帧段 → 回退进入前角度（不再被读成 0 / 平视跳变）。
                boolean roamAbs = this.roamInheritMode == 1;
                Point debug = this.offset.get();
                if (this.isActive(0)) {
                    double v = safeValue(this.x, tick, basePosition.point.x);
                    position.point.x = roamAbs ? v : v + debug.x;
                }
                if (this.isActive(1)) {
                    double v = safeValue(this.y, tick, basePosition.point.y);
                    position.point.y = roamAbs ? v : v + debug.y;
                }
                if (this.isActive(2)) {
                    double v = safeValue(this.z, tick, basePosition.point.z);
                    position.point.z = roamAbs ? v : v + debug.z;
                }
                if (this.isActive(3)) {
                    position.angle.yaw = (float) safeValue(this.yaw, tick, (double) basePosition.angle.yaw);
                }
                if (this.isActive(4)) {
                    position.angle.pitch = (float) safeValue(this.pitch, tick, (double) basePosition.angle.pitch);
                }
                if (this.isActive(5)) {
                    position.angle.roll = (float) safeValue(this.roll, tick, (double) basePosition.angle.roll);
                }
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
     * v1.5.4-Alpha 八版：检测 anchor 关键帧的「路线过渡段」。
     * tick 所在段的相邻两帧 anchor 值状态不同（<无> ⇄ 绑定模型）时返回混合信息；
     * 两端状态相同（同为 <无> 或同为绑定）返回 null（无过渡，走对应路线逻辑）。
     */
    private static RouteBlend routeBlend(KeyframeChannel<Anchor> channel, float tick) {
        if (channel == null || channel.isEmpty()) {
            return null;
        }
        Keyframe a = null;
        Keyframe b = null;
        for (Keyframe keyframe : channel.getKeyframes()) {
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
        return new RouteBlend(av, bv, t);
    }

    /** 路线过渡信息：段两端 anchor 值（绑定端用于解析追踪锚点）+ 段内进度 t（0~1）。 */
    private static final class RouteBlend {
        final Anchor a;
        final Anchor b;
        final float t;

        RouteBlend(Anchor a, Anchor b, float t) {
            this.a = a;
            this.b = b;
            this.t = t;
        }
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
