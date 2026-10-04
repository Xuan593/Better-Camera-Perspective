package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.camera.clips.misc.TrackerFrame;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.film.clips.widgets.UIBitToggle;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Vector3d;
import org.joml.Vector3f;
import perspective.better_camera_perspective.client.clips.MultiTrackerClientClip;

/**
 * 多样追踪（MultiTracker）——剪辑 UI 面板。
 *
 * 组合了两套控件：
 *  - 「关键帧」剪辑：编辑按钮 + 关键帧编辑器 + additive 开关；
 *  - 「追踪器」剪辑：目标选取、附件选取、偏移、角度、看向目标、相对、启用位掩码。
 *
 * 拖动相机时：先按追踪器方式反解 offset/angle（保持跟随），再按关键帧方式写入曲线。
 */
@Environment(EnvType.CLIENT)
public class UIMultiTrackerClip extends UIClip<MultiTrackerClientClip> {
    // —— 关键帧侧 ——
    public UIButton edit;
    public UIKeyframeEditor keyframes;
    public UIToggle additive;

    // —— 追踪侧 ——
    public UIButton selector;
    public UIButton group;
    public UIPointModule point;
    public UIPointModule angle;
    public UIToggle lookAt;
    public UIToggle relative;
    public UIBitToggle active;
    public UICirculate axisMode;

    public UIMultiTrackerClip(MultiTrackerClientClip clip, IUIClipsDelegate editor) {
        super(clip, editor);
    }

    @Override
    protected void addEnvelopes() {
        super.addEnvelopes();
        this.additive = this.toggle(UIKeys.CAMERA_PANELS_ADDITIVE, this.clip.additive);
        this.panels.add(this.additive);
    }

    @Override
    protected void registerUI() {
        super.registerUI();

        // —— 关键帧编辑器 ——
        this.keyframes = new UIKeyframeEditor(consumer -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes
            .view
            .rulerRenderer(
                context -> UIReplaysEditor.renderRuler(context, this.keyframes.view, (UIClipsPanel) this.editor, (Clips) this.clip.getParent(), this.clip.tick.get())
            );
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("multi_tracker_keyframes");
        this.edit = new UIButton(UIKeys.GENERAL_EDIT, b -> {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            this.keyframes.view.getGraph().clearSelection();
        });
        this.edit.keys().register(Keys.FORMS_EDIT, () -> this.edit.clickItself());

        // —— 追踪目标 ——
        this.selector = new UIButton(
            UIKeys.CAMERA_PANELS_TARGET_TITLE,
            b -> {
                UIFilmPanel panel = this.getParent(UIFilmPanel.class);
                if (panel != null) {
                    UIAnchorKeyframeFactory.displayActors(
                        this.getContext(), panel.getController().getEntities(), this.clip.selector.get(), i -> this.clip.selector.set(i)
                    );
                }
            }
        );
        this.selector.tooltip(UIKeys.CAMERA_PANELS_TARGET_TOOLTIP);
        this.group = new UIButton(
            UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT,
            b -> UIAnchorKeyframeFactory.displayAttachments(
                this.getParent(UIFilmPanel.class), this.clip.selector.get(), this.clip.group.get(), attachment -> this.clip.group.set(attachment)
            )
        );
        this.point = (UIPointModule) this.bind(new UIPointModule(this.editor, UIKeys.CAMERA_PANELS_OFFSET).contextMenu(), () -> this.point.fill(this.clip.offset));
        this.angle = (UIPointModule) this.bind(new UIPointModule(this.editor, UIKeys.CAMERA_PANELS_ANGLE).contextMenu(), () -> this.angle.fill(this.clip.angle));
        this.lookAt = this.toggle(UIKeys.CAMERA_PANELS_LOOK_AT, this.clip.lookAt);
        this.relative = this.toggle(UIKeys.CAMERA_PANELS_RELATIVE, this.clip.relative);
        this.active = (UIBitToggle) this.bind(new UIBitToggle(value -> this.clip.active.set(value)).all(), () -> this.active.setValue(this.clip.active.get()));

        // —— 轴数值模式（v0.2）：相对值 / 绝对值 循环切换 ——
        this.axisMode = new UICirculate(c -> {
            String newMode = c.getValue() == 0 ? "relative" : "absolute";
            if (!newMode.equals(this.clip.axisMode.get())) {
                // 切换不影响摄像机视角状态本身：把 XYZ 关键帧数值在两种语义间相互转换
                this.convertAxisValues(newMode);
                this.clip.axisMode.set(newMode);
            }
        });
        this.axisMode.addLabel(L10n.lang("bbs.ui.camera.panels.axis_mode.relative"));
        this.axisMode.addLabel(L10n.lang("bbs.ui.camera.panels.axis_mode.absolute"));
        this.axisMode.tooltip(L10n.lang("bbs.ui.camera.panels.axis_mode"));
    }

    @Override
    protected void registerPanels() {
        super.registerPanels();
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_KEYFRAMES, new UIElement[]{this.edit}));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_TARGET, new UIElement[]{this.selector, this.group}));
        this.panels.add(this.point);
        this.panels.add(this.angle);
        this.panels.add(this.lookAt);
        this.panels.add(this.relative);
        this.panels.add(this.active);
        this.panels.add(this.section(L10n.lang("bbs.ui.camera.panels.axis_mode"), new UIElement[]{this.axisMode}));
    }

    @Override
    public void editClip(Position position) {
        // 1) 追踪求解：把当前相机位置反解为 offset/angle/fov（保持跟随语义）
        this.solveTracker(position);
        // 2) 关键帧写入：把当前相机位置写入关键帧通道
        this.insertKeyframes(position);
        super.editClip(position);
    }

    // ================= 追踪求解（复制自 UITrackerClip.editClip） =================

    private void solveTracker(Position position) {
        TrackerFrame frame = this.resolveFrame(position);
        if (frame != null) {
            Point offset = this.clip.offset.get();
            Point angle = this.clip.angle.get();
            boolean lookAt = this.clip.lookAt.get();
            if (!lookAt && this.isActive(0, 1, 2)) {
                Vector3d current = frame.position(offset);
                Point solved = frame.solveOffset(
                    this.pick(0, position.point.x, current.x), this.pick(1, position.point.y, current.y), this.pick(2, position.point.z, current.z)
                );
                if (solved != null) {
                    this.clip.offset.set(solved);
                }
            }

            if (this.isActive(3, 4, 5)) {
                Angle current = lookAt ? frame.lookAtAngles(offset, angle) : frame.angles(angle);
                float yaw = this.pick(3, position.angle.yaw, current.yaw);
                float pitch = this.pick(4, position.angle.pitch, current.pitch);
                float roll = this.pick(5, position.angle.roll, current.roll);
                this.clip.angle.set(lookAt ? frame.solveLookAtAngles(offset, yaw, pitch, roll) : frame.solveAngles(yaw, pitch, roll));
            }
        }
    }

    private TrackerFrame resolveFrame(Position position) {
        UIFilmPanel panel = this.getParent(UIFilmPanel.class);
        UIContext context = this.getContext();
        String selector = this.clip.selector.get();
        if (panel != null && context != null && !selector.isEmpty()) {
            TrackerFrame frame = TrackerFrame.resolve(
                panel.getController().getEntities(), panel.getController().getEntities().get(selector),
                this.clip.group.get(), position.point.x, position.point.y, position.point.z, context.getTransition()
            );
            if (frame != null && this.clip.relative.get()) {
                if (this.clip.getUnderneath() == null) {
                    return null;
                }

                frame.relative(this.clip.getUnderneath(), this.clip.position);
            }

            return frame;
        } else {
            return null;
        }
    }

    private boolean isActive(int... bits) {
        for (int bit : bits) {
            if (this.clip.isActive(bit)) {
                return true;
            }
        }

        return false;
    }

    private float pick(int bit, float camera, float current) {
        return this.clip.isActive(bit) ? camera : current;
    }

    private double pick(int bit, double camera, double current) {
        return this.clip.isActive(bit) ? camera : current;
    }

    // ================= 关键帧写入（复制自 UIKeyframeClip.editClip） =================

    private void insertKeyframes(Position position) {
        Position newPos = position.copy();
        UIContext context = this.getContext();
        float tick = this.editor.getKeyframeCursor(context == null ? 0.0F : context.getTransition()) - (float) this.clip.tick.get().intValue();
        if (this.clip.additive.get()) {
            Position underneath = this.clip.getUnderneath();
            float factor = this.clip.envelope.factorEnabled(this.clip.duration.get(), tick);
            if (underneath != null && !(tick <= 0.0F) && factor != 0.0F && this.clip.enabled.get()) {
                this.insertAdditiveKeyframe(tick, this.clip.x, (newPos.point.x - underneath.point.x) / (double) factor);
                this.insertAdditiveKeyframe(tick, this.clip.y, (newPos.point.y - underneath.point.y) / (double) factor);
                this.insertAdditiveKeyframe(tick, this.clip.z, (newPos.point.z - underneath.point.z) / (double) factor);
                this.insertAdditiveKeyframe(tick, this.clip.yaw, (double) ((newPos.angle.yaw - underneath.angle.yaw) / factor));
                this.insertAdditiveKeyframe(tick, this.clip.pitch, (double) ((newPos.angle.pitch - underneath.angle.pitch) / factor));
                this.insertAdditiveKeyframe(tick, this.clip.roll, (double) ((newPos.angle.roll - underneath.angle.roll) / factor));
                this.insertAdditiveKeyframe(tick, this.clip.fov, (double) ((newPos.angle.fov - underneath.angle.fov) / factor));
            }
        } else {
            if (!this.clip.distance.isEmpty()) {
                double distance = this.clip.distance.interpolate(tick);
                if (distance != 0.0) {
                    Vector3f rotation = Matrices.rotation(MathUtils.toRad(newPos.angle.pitch), MathUtils.toRad(-newPos.angle.yaw - 180.0F));
                    newPos.point.x = newPos.point.x - (double) rotation.x * distance;
                    newPos.point.y = newPos.point.y - (double) rotation.y * distance;
                    newPos.point.z = newPos.point.z - (double) rotation.z * distance;
                }
            }

            // 多样追踪：轴数值模式为「相对值」时，XYZ 关键帧值 = 相对追踪锚点的偏移
            //（「偏移栏」语义，播放时按「锚点 + 关键帧偏移」合成）；「绝对值」时写入相机绝对坐标。
            double ax = 0.0D, ay = 0.0D, az = 0.0D;
            boolean relativeMode = "relative".equals(this.clip.axisMode.get());
            TrackerFrame anchorFrame = this.resolveFrame(position);
            if (relativeMode && anchorFrame != null && !this.clip.lookAt.get() && this.isActive(0, 1, 2)) {
                Vector3d anchor = anchorFrame.position(this.clip.offset.get());
                ax = anchor.x;
                ay = anchor.y;
                az = anchor.z;
            }
            this.insertKeyframe(tick, this.clip.x, newPos.point.x - ax);
            this.insertKeyframe(tick, this.clip.y, newPos.point.y - ay);
            this.insertKeyframe(tick, this.clip.z, newPos.point.z - az);
            this.insertKeyframe(tick, this.clip.yaw, (double) newPos.angle.yaw);
            this.insertKeyframe(tick, this.clip.pitch, (double) newPos.angle.pitch);
            this.insertKeyframe(tick, this.clip.roll, (double) newPos.angle.roll);
            this.insertKeyframe(tick, this.clip.fov, (double) newPos.angle.fov);
        }
    }

    private void insertAdditiveKeyframe(float tick, KeyframeChannel<Double> channel, double offset) {
        double reference = channel.isEmpty() ? 0.0 : channel.interpolate(0.0F);
        if (channel.getKeyframes().stream().noneMatch(keyframe -> keyframe.getTick() == 0.0F)) {
            this.insertKeyframe(0.0F, channel, reference);
        }

        this.insertKeyframe(tick, channel, reference - offset);
    }

    private void insertKeyframe(float tick, KeyframeChannel<Double> channel, double x) {
        KeyframeSegment<Double> segment = channel.findSegment(tick);
        int insert = channel.insert(tick, x);
        if (segment != null) {
            channel.get(insert).copyOverExtra(segment.a);
        }
    }

    @Override
    public void fillData() {
        super.fillData();
        this.updateDuration(this.clip.duration.get());
        this.keyframes.setClip(this.clip);
        this.axisMode.setValue("relative".equals(this.clip.axisMode.get()) ? 0 : 1);
    }

    // ================= 轴数值模式切换（v0.2） =================

    /**
     * 切换 XYZ 关键帧的数值模式。两模式数值可相互转换，转换后摄像机视角状态不变：
     *  相对值 → 绝对值：每个关键帧值 + 该时刻的锚点位置；
     *  绝对值 → 相对值：每个关键帧值 − 该时刻的锚点位置。
     */
    private void convertAxisValues(String newMode) {
        UIFilmPanel panel = this.getParent(UIFilmPanel.class);
        if (panel == null) {
            return;
        }

        String selector = this.clip.selector.get();
        if (selector.isEmpty()) {
            return;
        }

        IEntity actor = panel.getController().getEntities().get(selector);
        if (actor == null) {
            return;
        }

        boolean toRelative = "relative".equals(newMode);
        double sign = toRelative ? -1.0D : 1.0D;
        KeyframeChannel<Double>[] xyz = new KeyframeChannel[]{this.clip.x, this.clip.y, this.clip.z};
        for (int axis = 0; axis < 3; axis++) {
            for (Keyframe<Double> keyframe : xyz[axis].getKeyframes()) {
                Vector3d anchor = this.anchorAt(panel, actor, keyframe.getTick());
                if (anchor == null) {
                    continue;
                }

                double delta = axis == 0 ? anchor.x : (axis == 1 ? anchor.y : anchor.z);
                keyframe.setValue(keyframe.getValue() + sign * delta, true);
            }
        }
    }

    /** 按剪辑内 tick 计算追踪锚点位置（与播放端的 TrackerFrame.resolve 对齐）。 */
    private Vector3d anchorAt(UIFilmPanel panel, IEntity actor, float tick) {
        TrackerFrame frame = TrackerFrame.resolve(
            panel.getController().getEntities(), actor, this.clip.group.get(),
            0.0F, 0.0F, 0.0F, tick
        );
        if (frame == null) {
            return null;
        }

        if (this.clip.relative.get()) {
            Position underneath = this.clip.getUnderneath();
            if (underneath == null) {
                return null;
            }

            frame.relative(underneath, this.clip.position);
        }

        return frame.position(this.clip.offset.get());
    }
}
