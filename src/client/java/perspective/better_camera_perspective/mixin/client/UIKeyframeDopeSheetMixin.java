package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import perspective.better_camera_perspective.client.clips.MultiTrackerClientClip;
import perspective.better_camera_perspective.client.ui.UIMultiTrackerClip;

/**
 * v1.1：多样追踪「关键帧打点」主继承模式注入。
 *
 * 在时间轴（DopeSheet）上点击新建关键帧时（addKeyframeAt 内部调用
 * addKeyframeManually(sheet, tick, null)），若目标通道是多样追踪剪辑的
 * X / Y / Z 通道，则把注入值替换为按剪辑当前「主继承模式」（inheritMode）计算的继承值：
 *  - 相对值模式：该 tick 的通道插值（相对偏移）；
 *  - 绝对值模式：插值 + 该 tick 追踪锚点（绝对坐标）。
 * 非多样追踪 XYZ 通道保持原逻辑（注入值为 null，走 BBS 默认继承链）。
 */
@Environment(EnvType.CLIENT)
@Mixin(UIKeyframeDopeSheet.class)
public class UIKeyframeDopeSheetMixin {
    @Shadow
    private UIKeyframes keyframes;

    @ModifyArg(
        method = "addKeyframeAt",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/graphs/IUIKeyframeGraph;addKeyframeManually(Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/UIKeyframeSheet;FLjava/lang/Object;)Lmchorse/bbs_mod/utils/keyframes/Keyframe;"
        ),
        index = 2
    )
    private Object bcp$inheritAddValue(Object value, UIKeyframeSheet sheet, float tick, int mouseY) {
        Object inherited = this.bcp$inheritValue(sheet, tick);
        return inherited == null ? value : inherited;
    }

    /** 多样追踪 X/Y/Z 通道 → 主继承值；其余返回 null（保持 BBS 原逻辑）。 */
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
        } else {
            return null;
        }

        KeyframeChannel<Double> channel = (KeyframeChannel<Double>) sheet.channel;
        double relative = channel.isEmpty() ? 0.0D : channel.interpolate(tick);
        if (multi.inheritMode == 0) {
            return relative;
        }
        UIFilmPanel panel = this.keyframes.getParent(UIFilmPanel.class);
        if (panel == null) {
            return relative;
        }
        return relative + UIMultiTrackerClip.anchorAxisAt(multi, panel, axis, tick);
    }
}
