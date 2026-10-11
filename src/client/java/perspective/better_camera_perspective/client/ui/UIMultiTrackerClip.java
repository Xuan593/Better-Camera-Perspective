package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.camera.clips.misc.TrackerFrame;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
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
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
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
                        this.getContext(), panel.getController().getEntities(), this.clip.selector.get(),
                        // v1.4.1：选中锚点目标后自动归零当前帧 XYZ 相对值（摄像机贴回锚点，避免旧相对偏移把相机甩远）
                        // v1.5.4-Alpha：只归零当前播放头处，不再破坏其他帧的关键帧值
                        // v1.5.4-Alpha 四版：锚点感知归零——锚点无效时保留原值（不跳 0 0 0）
                        i -> {
                            this.clip.selector.set(i);
                            resetXyzRelative(this.clip, panel, this.cursorTick());
                        }
                    );
                }
            }
        );
        this.selector.tooltip(UIKeys.CAMERA_PANELS_TARGET_TOOLTIP);
        this.group = new UIButton(
            UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT,
            b -> UIAnchorKeyframeFactory.displayAttachments(
                this.getParent(UIFilmPanel.class), this.clip.selector.get(), this.clip.group.get(),
                // v1.4.1：切换绑定部位后同样归零当前帧 XYZ 相对值
                // v1.5.4-Alpha 四版：锚点感知归零
                attachment -> {
                    this.clip.group.set(attachment);
                    resetXyzRelative(this.clip, this.getParent(UIFilmPanel.class), this.cursorTick());
                }
            )
        );
        this.point = (UIPointModule) this.bind(new UIPointModule(this.editor, UIKeys.CAMERA_PANELS_OFFSET).contextMenu(), () -> this.point.fill(this.clip.offset));
        this.angle = (UIPointModule) this.bind(new UIPointModule(this.editor, UIKeys.CAMERA_PANELS_ANGLE).contextMenu(), () -> this.angle.fill(this.clip.angle));
        this.lookAt = this.toggle(UIKeys.CAMERA_PANELS_LOOK_AT, this.clip.lookAt);
        this.relative = this.toggle(UIKeys.CAMERA_PANELS_RELATIVE, this.clip.relative);
        this.active = (UIBitToggle) this.bind(new UIBitToggle(value -> this.clip.active.set(value)).all(), () -> this.active.setValue(this.clip.active.get()));

        // —— v1.0：XYZ 关键帧值面板改为「绝对值 + 相对值」双值联动编辑，注册 BCP 专用工厂 ——
        BcpKeyframeFactories.register();
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

            // 多样追踪（v1.0）：XYZ 关键帧固定「相对值」语义——存相对锚点的偏移
            //（「偏移栏」语义，播放时按「锚点 + 关键帧偏移」合成）。
            double ax = 0.0D, ay = 0.0D, az = 0.0D;
            if (!this.clip.lookAt.get() && this.isActive(0, 1, 2)) {
                TrackerFrame anchorFrame = this.resolveFrame(position);
                if (anchorFrame != null) {
                    Vector3d anchor = anchorFrame.position(this.clip.offset.get());
                    ax = this.clip.isActive(0) ? anchor.x : 0.0D;
                    ay = this.clip.isActive(1) ? anchor.y : 0.0D;
                    az = this.clip.isActive(2) ? anchor.z : 0.0D;
                }
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
        // v0.3：UIKeyframeEditor.setClip 只遍历 channels 数组（8 个数值通道），这里手动补挂 anchor 通道
        this.appendSheet("anchor", L10n.lang("bbs.ui.camera.panels.axis_anchor"), 0xFF42A5F5, this.clip.anchor);
        // v1.5.4-Alpha 四版：新建剪辑默认在 tick 0 放一个「空 anchor 关键帧」（未绑定任何模型）。
        // 作为「游动 / 追踪」两条路线的分派锚点：anchor 无绑定 → 游动路线自动生效；
        // 有绑定目标 → 追踪路线自动生效（游动的两值与追踪的两值互不串用）。
        if (this.clip.anchor.isEmpty()) {
            this.clip.anchor.insert(0, KeyframeFactories.ANCHOR.createEmpty());
        }
        // v1.1：给 X / Y / Z 通道设置 seed —— 新建关键帧（无插值段可继承）时按「主继承模式」供给继承值。
        // v1.4.1：扩展覆盖 偏航/俯仰/横滚/视野（无关键帧新建帧不再默认 0，继承摄像机默认视角）。
        for (UIKeyframeSheet sheet : this.keyframes.view.getGraph().getSheets()) {
            if (sheet.channel == this.clip.x) {
                this.setSeed(sheet, 0);
            } else if (sheet.channel == this.clip.y) {
                this.setSeed(sheet, 1);
            } else if (sheet.channel == this.clip.z) {
                this.setSeed(sheet, 2);
            } else if (sheet.channel == this.clip.yaw) {
                this.setSeed(sheet, 3);
            } else if (sheet.channel == this.clip.pitch) {
                this.setSeed(sheet, 4);
            } else if (sheet.channel == this.clip.roll) {
                this.setSeed(sheet, 5);
            } else if (sheet.channel == this.clip.fov) {
                this.setSeed(sheet, 6);
            }
        }
    }

    private void appendSheet(String id, IKey title, int color, KeyframeChannel<?> channel) {
        boolean exists = this.keyframes.view.getGraph().getSheets().stream().anyMatch(sheet -> sheet.channel == channel);
        if (!exists) {
            this.keyframes.view.addSheet(new UIKeyframeSheet(id, title, color, channel, null));
        }
    }

    /** v1.1：为 X/Y/Z 通道 sheet 挂「主继承值」seed（无插值段新建关键帧时使用）。 */
    private void setSeed(UIKeyframeSheet sheet, int axis) {
        sheet.seed(() -> this.inheritSeedValue(axis));
    }

    /** 按剪辑主继承模式计算当前播放头处的继承值：通道值恒为「相对偏移」；绝对显示 = 相对 + 锚点。 */
    private Object inheritSeedValue(int axis) {
        KeyframeChannel<Double> channel = this.channelAt(axis);
        if (channel == null) {
            return null;
        }
        float tick = ((UIFilmKeyframes) this.keyframes.view).getOffset();
        if (!channel.isEmpty()) {
            return channel.interpolate(tick);
        }
        UIFilmPanel panel = this.getParent(UIFilmPanel.class);
        return panel == null ? 0.0D : defaultInheritValue(this.clip, panel, axis, tick);
    }

    /**
     * v1.4.1：无关键帧时各轴的默认继承值（摄像机当前视角换算）。
     * v1.5.4-Alpha 四版：XYZ 按「游动 / 追踪」路线区分——
     *   追踪路线（anchor 绑定模型）：相对 = 相机 − 锚点；绝对 = 相机（存绝对坐标，播放直接用）；
     *   游动路线（anchor 未绑定）：两小模式同存绝对坐标。
     */
    public static double defaultInheritValue(MultiTrackerClientClip clip, UIFilmPanel panel, int axis, float tick) {
        if (axis <= 2) {
            // X / Y / Z：按当前路线决定继承值
            double camera = cameraAxisAt(panel, axis);
            if (isTracked(clip, panel, tick)) {
                // 追踪路线：相对 = 相机 − 锚点（偏移）；绝对 = 相机（绝对坐标）
                return clip.trackInheritMode == 1 ? camera : camera - anchorAxisAt(clip, panel, axis, tick);
            }
            // 游动路线：绝对坐标（相对 / 绝对小模式同值，播放时靠 offset 调试值区分）
            return camera;
        }
        CameraController controller = panel.getCameraController();
        if (axis == 3) {
            // v1.5.4-Alpha：归一化到 (-180, 180]，避免摄像机连续角度值（可能超 ±180）与
            // 关键帧通道历史值域混用，导致「方位相同、数值天差地别」及播放走超长旋转
            return normalizeAngle(controller.getYaw());
        }
        if (axis == 4) {
            return normalizeAngle(controller.getPitch());
        }
        if (axis == 5) {
            return normalizeAngle(controller.getRoll());
        }
        if (axis == 6) {
            return controller.getFOV();
        }
        return 0.0D;
    }

    /** v1.5.4-Alpha：角度值归一化到 (-180, 180]。 */
    private static double normalizeAngle(double angle) {
        while (angle > 180.0D) {
            angle -= 360.0D;
        }
        while (angle <= -180.0D) {
            angle += 360.0D;
        }
        return angle;
    }

    /** v1.4.1：轴序号 → 通道映射（0=X 1=Y 2=Z 3=偏航 4=俯仰 5=横滚 6=视野）。 */
    private KeyframeChannel<Double> channelAt(int axis) {
        switch (axis) {
            case 0:
                return this.clip.x;
            case 1:
                return this.clip.y;
            case 2:
                return this.clip.z;
            case 3:
                return this.clip.yaw;
            case 4:
                return this.clip.pitch;
            case 5:
                return this.clip.roll;
            case 6:
                return this.clip.fov;
            default:
                return null;
        }
    }

    // ================= XYZ 双值编辑锚点工具（v1.0） =================

    /**
     * v1.4.1：当前预览摄像机位置（未取到或异常时返回 0）。
     * 供「删除所有帧后新建关键帧继承默认视角」使用。
     */
    public static double cameraAxisAt(UIFilmPanel panel, int axis) {
        if (panel == null) {
            return 0.0D;
        }
        try {
            Vector3d pos = panel.getCameraController().getPosition();
            return axis == 0 ? pos.x : (axis == 1 ? pos.y : pos.z);
        } catch (Exception e) {
            return 0.0D;
        }
    }

    /**
     * v1.4.1：将 XYZ 相对值通道在「当前播放头 tick」处的关键帧值归零。
     * 锚点绑定（模型/部位）变更后调用：当前帧摄像机立即贴回锚点，
     * 避免旧相对偏移把摄像机甩到较远位置。
     * v1.5.4-Alpha：不再破坏其他帧的关键帧值；且**绝不插入新帧**——
     * 当前 tick 处没有关键帧（如新建剪辑 XYZ 通道为空）时保持原状，
     * 避免「锁 0」现象（插入 0 值帧会覆盖默认继承值，摄像机被读回 XYZ=0 区域）。
     * v1.5.4-Alpha 四版：改为「锚点感知归零」——仅当当前帧有有效的追踪锚点时，
     * 把该帧值写为「相机位置 − 锚点」（贴回锚点，偏移为 0）；
     * 锚点无效（模型缺失 / 部位解析失败）时**保留原值**，不再把帧写成 0
     * （否则无锚点游动时该 0 值帧被当作绝对坐标 0,0,0，摄像机仍会飞回原点）。
     */
    public static void resetXyzRelative(MultiTrackerClientClip clip, UIFilmPanel panel, float tick) {
        if (clip == null) {
            return;
        }
        resetChannel(clip.x, clip, panel, 0, tick);
        resetChannel(clip.y, clip, panel, 1, tick);
        resetChannel(clip.z, clip, panel, 2, tick);
    }

    /** v1.5.4-Alpha 四版：仅当 tick 处已有关键帧且追踪锚点有效时才「贴回锚点」，绝不新增关键帧。 */
    private static void resetChannel(KeyframeChannel<Double> channel, MultiTrackerClientClip clip, UIFilmPanel panel, int axis, float tick) {
        for (Keyframe keyframe : channel.getKeyframes()) {
            if (keyframe.getTick() == tick) {
                if (isTracked(clip, panel, tick)) {
                    // 追踪锚点有效：相机贴回锚点（相对偏移归零）
                    keyframe.setValue(cameraAxisAt(panel, axis) - anchorAxisAt(clip, panel, axis, tick));
                }
                // 锚点无效：保留原值（游动语义，不跳 0）
                return;
            }
        }
    }

    /**
     * v1.5.4-Alpha 四版：判断当前播放头处是否处于「追踪」路线（anchor 绑定模型且锚点可解析）。
     * 追踪目标按 anchor 轴在该 tick 的插值优先，回退剪辑属性 selector/group；
     * 无追踪目标（未绑定 / 模型缺失 / 部位解析失败）→ 游动路线。
     */
    public static boolean isTracked(MultiTrackerClientClip clip, UIFilmPanel panel, float tick) {
        if (clip == null || panel == null) {
            return false;
        }
        String sel = clip.selector.get();
        String grp = clip.group.get();
        if (!clip.anchor.isEmpty()) {
            Anchor anchor = clip.anchor.interpolate(tick);
            if (anchor == null || !anchor.hasTarget()) {
                anchor = nearestAnchorAtOrBefore(clip.anchor, tick);
            }
            if (anchor != null && anchor.hasTarget()) {
                sel = anchor.replay;
                grp = anchor.attachment;
            }
        }
        if (sel.isEmpty()) {
            return false;
        }
        IEntity actor = panel.getController().getEntities().get(sel);
        if (actor == null) {
            return false;
        }
        return anchorPos(clip, panel, actor, grp, tick) != null;
    }

    /** v1.5.4-Alpha：当前播放头在剪辑内的 tick（与 insertKeyframes 口径一致）。 */
    private float cursorTick() {
        UIContext context = this.getContext();
        return this.editor.getKeyframeCursor(context == null ? 0.0F : context.getTransition()) - (float) this.clip.tick.get().intValue();
    }

    /**
     * 计算剪辑内 tick 处某轴（0=X / 1=Y / 2=Z）的追踪锚点坐标（供 UIXyzKeyframeFactory 双值联动）。
     * 追踪目标按 anchor 轴在该 tick 的插值优先，回退剪辑属性 selector/group；
     * 无追踪目标时返回 0（相对值 = 绝对值）。
     *
     * v1.4.1：anchor 关键帧与 XYZ 关键帧通常不在同一 tick 新建——tick 处 anchor 插值无目标时，
     * 回退到 tick 之前最近的非空 anchor 关键帧，保证跨 tick 场景也能读到锚点（摄像机不再被旧相对偏移甩远）。
     */
    public static double anchorAxisAt(MultiTrackerClientClip clip, UIFilmPanel panel, int axis, float tick) {
        String sel = clip.selector.get();
        String grp = clip.group.get();
        if (!clip.anchor.isEmpty()) {
            Anchor anchor = clip.anchor.interpolate(tick);
            if (anchor == null || !anchor.hasTarget()) {
                anchor = nearestAnchorAtOrBefore(clip.anchor, tick);
            }
            if (anchor != null && anchor.hasTarget()) {
                sel = anchor.replay;
                grp = anchor.attachment;
            }
        }

        if (sel.isEmpty()) {
            return 0.0D;
        }

        IEntity actor = panel.getController().getEntities().get(sel);
        if (actor == null) {
            return 0.0D;
        }

        Vector3d pos = anchorPos(clip, panel, actor, grp, tick);
        if (pos == null && !grp.isEmpty()) {
            // 部位路径解析失败 → 回退到模型根骨骼，保证锚点能读到（摄像机不再被旧相对偏移甩远）
            pos = anchorPos(clip, panel, actor, "", tick);
        }
        if (pos == null) {
            return 0.0D;
        }

        return axis == 0 ? pos.x : (axis == 1 ? pos.y : pos.z);
    }

    /** v1.4.1：tick 之前最近的非空 anchor 关键帧（anchor 轴跨 tick 回退读取）。 */
    private static Anchor nearestAnchorAtOrBefore(KeyframeChannel<Anchor> channel, float tick) {
        Anchor best = null;
        float bestTick = -1.0F;
        for (Keyframe keyframe : channel.getKeyframes()) {
            Anchor anchor = (Anchor) keyframe.getValue();
            if (anchor != null && anchor.hasTarget() && keyframe.getTick() <= tick && keyframe.getTick() >= bestTick) {
                best = anchor;
                bestTick = keyframe.getTick();
            }
        }
        return best;
    }

    private static Vector3d anchorPos(MultiTrackerClientClip clip, UIFilmPanel panel, IEntity actor, String group, float tick) {
        TrackerFrame frame = TrackerFrame.resolve(
            panel.getController().getEntities(), actor, group, 0.0F, 0.0F, 0.0F, tick
        );
        if (frame == null) {
            return null;
        }

        if (clip.relative.get()) {
            Position underneath = clip.getUnderneath();
            if (underneath == null) {
                return null;
            }

            frame.relative(underneath, clip.position);
        }

        return frame.position(clip.offset.get());
    }
}
