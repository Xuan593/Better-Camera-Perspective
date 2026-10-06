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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import perspective.better_camera_perspective.client.clips.MultiTrackerClientClip;
import perspective.better_camera_perspective.client.ui.UIMultiTrackerClip;

/**
 * v1.1：多样追踪「关键帧打点」主继承模式注入。
 *
 * 在时间轴（DopeSheet）上点击新建关键帧时（addKeyframeAt），若目标通道是
 * 多样追踪剪辑的 X / Y / Z 通道，则把注入值替换为按剪辑当前「主继承模式」
 * （inheritMode）计算的继承值：
 *  - 相对值模式：该 tick 的通道插值（相对偏移）；
 *  - 绝对值模式：插值 + 该 tick 追踪锚点（绝对坐标）。
 * 非多样追踪 XYZ 通道保持原逻辑（注入值为 null，走 BBS 默认继承链）。
 *
 * 注入方式：HEAD + 取消 + @Shadow 调用（method 用简单名，不依赖 refmap，
 * 与工程内其他 BBS mixin 机制一致，避免描述符 target 的 refmap 注入失败）。
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
    public void addKeyframeManually(UIKeyframeSheet sheet, float tick, Object value) {
    }

    @Inject(method = "addKeyframeAt", at = @At("HEAD"), cancellable = true)
    private void bcp$addKeyframeAt(float tick, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        UIKeyframeSheet sheet = this.getSheet(mouseY);
        if (sheet == null) {
            cir.setReturnValue(false);
            return;
        }
        this.addKeyframeManually(sheet, tick, this.bcp$inheritValue(sheet, tick));
        cir.setReturnValue(true);
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

