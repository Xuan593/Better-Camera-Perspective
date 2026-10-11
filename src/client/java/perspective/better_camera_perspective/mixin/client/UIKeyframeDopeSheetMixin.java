package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.KeyframeType;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import perspective.better_camera_perspective.client.BcpKeyframeProcessing;
import perspective.better_camera_perspective.client.clips.MultiTrackerClientClip;
import perspective.better_camera_perspective.client.ui.UIMultiTrackerClip;

import java.util.HashMap;
import java.util.Map;

/**
 * v1.1：多样追踪「关键帧打点」主继承模式注入。
 *
 * 在时间轴（DopeSheet）上点击新建关键帧时（addKeyframeAt），若目标通道是
 * 多样追踪剪辑的 X / Y / Z 通道，则按剪辑当前「主继承模式」（inheritMode）计算继承值：
 *  - 相对值模式：该 tick 的通道插值（相对偏移）；
 *  - 绝对值模式：插值 + 该 tick 追踪锚点（绝对坐标）。
 * 非多样追踪 XYZ 通道保持 BBS 原逻辑（继承值 = null，走默认继承链）。
 *
 * 实现约束：addKeyframeManually / addKeyframe 是 IUIKeyframeGraph 的接口 default 方法，
 * @Shadow 无法绑定（sponge 只查类继承链），因此这里在 HEAD 取消后按原逻辑手动重放打点
 * （全部调用均为类 / 字段的 public 成员），避免 @ModifyArg 描述符（依赖 refmap）与
 * @Shadow 接口方法两种运行时失败。
 */
@Environment(EnvType.CLIENT)
@Mixin(UIKeyframeDopeSheet.class)
public class UIKeyframeDopeSheetMixin {
    @Shadow
    private UIKeyframes keyframes;

    @Shadow
    public UIKeyframeSheet getSheet(int mouseY) {
        return null;
    }

    @Shadow
    public void clearSelection() {
    }

    @Shadow
    public Pair<Keyframe, KeyframeType> findKeyframe(int mouseX, int mouseY) {
        return null;
    }

    /**
     * v1.5-Alpha：混合模式下记录「每个通道上次新建的关键帧索引」，
     * 新建时只解除该帧的选中（保留手动选中的帧），实现前两者的智能融合。
     */
    @Unique
    private final Map<UIKeyframeSheet, Integer> bcp$lastCreatedIndexes = new HashMap<>();

    /**
     * v1.5.4-Alpha 四版：左键点击「已选中关键帧所在轨道」的空白处 → 取消选中（不打点）。
     *
     * 背景：原版 BBS 的「点击空白取消选中」仅存在于 UIKeyframes.subMouseClicked 的
     * pickOrStartSelectingKeyframes（需点击 graph 纯空白且 DopeSheet 返回 false）；
     * 而点击轨道行内空白会走 clickSheets → addKeyframeManually 新建关键帧，且该路径
     * 不经 addKeyframeAt（BCP 的选中三模式注入也不生效），新建帧直接进入选中态——
     * 用户在轨道空白点击只会不断打点 / 保持选中，无法取消。
     *
     * 本注入：命中「轨道行内、非关键帧」且该轨道已有选中帧时，取消全部选中并拦截
     * （clearSelection 内部会 pickKeyframe(null) 同步关闭值面板）；否则放行原逻辑
     * （无选中的轨道空白 → 原版打点；graph 纯空白 → 上层 pickOrStartSelectingKeyframes 清空）。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void bcp$mouseClicked(UIContext context, CallbackInfoReturnable<Boolean> cir) {
        if (context.mouseButton != 0) {
            return;
        }
        if (!this.keyframes.area.isInside(context) || context.mouseX <= this.keyframes.area.x + this.keyframes.getLabelWidth()) {
            return;
        }
        if (this.findKeyframe(context.mouseX, context.mouseY) != null) {
            return;
        }
        UIKeyframeSheet sheet = this.getSheet(context.mouseY);
        if (sheet != null && sheet.selection.hasAny()) {
            this.clearSelection();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "addKeyframeAt", at = @At("HEAD"), cancellable = true)
    private void bcp$addKeyframeAt(float tick, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        UIKeyframeSheet sheet = this.getSheet(mouseY);
        if (sheet == null || sheet.channel == null) {
            cir.setReturnValue(false);
            return;
        }

        Object value = this.bcp$inheritValue(sheet, tick);
        KeyframeChannel<Object> channel = (KeyframeChannel<Object>) sheet.channel;
        boolean inherits = value == null && !channel.isEmpty();
        int index;

        if (value == null) {
            // —— 与 IUIKeyframeGraph.addKeyframe 的默认继承链一致 ——
            KeyframeSegment<Object> segment = channel.find(tick);
            Keyframe extra = null;
            if (segment != null) {
                value = segment.createInterpolated();
                extra = segment.a;
            } else if (sheet.seed != null) {
                value = sheet.seed.get();
            } else if (sheet.property != null) {
                value = channel.getFactory().copy(sheet.property.get());
            } else {
                value = channel.getFactory().createEmpty();
            }
            channel.preNotify(1);
            index = channel.insert(tick, value);
            Keyframe keyframe = channel.get(index);
            if (extra != null) {
                keyframe.copyOverExtra(extra);
            }
        } else {
            channel.preNotify(1);
            index = channel.insert(tick, value);
        }

        Keyframe keyframe = channel.get(index);
        if (keyframe != null && !inherits) {
            keyframe.getInterpolation().setInterp(BBSSettings.getDefaultKeyframeInterpolation());
        }
        if (keyframe != null) {
            // v1.5-Alpha：关键帧被选中状态三模式（默认 / 单体 / 混合）
            int selectionMode = BcpKeyframeProcessing.getSelectionMode();
            if (selectionMode == BcpKeyframeProcessing.MODE_SINGLE_IDX) {
                sheet.selection.clear();
            } else if (selectionMode == BcpKeyframeProcessing.MODE_MIXED_IDX) {
                Integer last = this.bcp$lastCreatedIndexes.get(sheet);
                if (last != null && sheet.selection.has(last)) {
                    sheet.selection.remove(last);
                }
            }
            sheet.selection.add(index);
            this.bcp$lastCreatedIndexes.put(sheet, index);
        }
        cir.setReturnValue(true);
    }

    /** 多样追踪 X/Y/Z/偏航/俯仰/横滚/视野 通道 → 主继承值；其余返回 null（保持 BBS 原逻辑）。 */
    private Object bcp$inheritValue(UIKeyframeSheet sheet, float tick) {
        if (!(this.keyframes instanceof UIFilmKeyframes film) || film.editor == null) {
            return null;
        }
        IUIClipsDelegate delegate = film.editor;
        Clip clip = delegate.getClip();
        if (!(clip instanceof MultiTrackerClientClip multi)) {
            return null;
        }

        int axis;
        if (sheet.channel == multi.x) {
            axis = 0;
        } else if (sheet.channel == multi.y) {
            axis = 1;
        } else if (sheet.channel == multi.z) {
            axis = 2;
        } else if (sheet.channel == multi.yaw) {
            axis = 3;
        } else if (sheet.channel == multi.pitch) {
            axis = 4;
        } else if (sheet.channel == multi.roll) {
            axis = 5;
        } else if (sheet.channel == multi.fov) {
            axis = 6;
        } else {
            return null;
        }

        KeyframeChannel<Double> channel = (KeyframeChannel<Double>) sheet.channel;
        double value;
        if (channel.isEmpty()) {
            // v1.4.1：删除所有帧后新建关键帧 → 继承摄像机的默认视角，不跳变
            UIFilmPanel panel = this.keyframes.getParent(UIFilmPanel.class);
            value = panel == null
                ? 0.0D
                : UIMultiTrackerClip.defaultInheritValue(multi, panel, axis, tick);
        } else {
            value = channel.interpolate(tick);
        }
        // v1.4.1：X/Y/Z 通道值恒为「相对偏移」——相对模式显示相对值；
        // 绝对模式显示 = 相对 + 锚点 = 摄像机位置（两模式同值，修正 v1.1 绝对模式双倍锚点问题）
        return value;
    }
}
