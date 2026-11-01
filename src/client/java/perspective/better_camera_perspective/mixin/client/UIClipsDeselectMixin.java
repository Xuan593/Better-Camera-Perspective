package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * V1.7.18-Alpha：点击剪辑时间轴空白处 → 取消剪辑选中。
 *
 * 原版 BBS 的 handleLeftClick 在 clip == null（点击空白）时只做框选 / 时间轴拖动，
 * 没有清除选中的逻辑，导致选中剪辑后点击空白无法取消。
 *
 * 注意：xAxis 定义在父类 UITimelineCanvas（protected final），@Shadow 无法在无 refMap
 * 环境下定位，故改用 public getter getXAxis()。
 */
@Environment(EnvType.CLIENT)
@Mixin(UIClips.class)
public abstract class UIClipsDeselectMixin {

    @Shadow public IUIClipsDelegate delegate;
    @Shadow public Clips clips;

    @Shadow public abstract boolean isInRuler(int mouseY);

    @Shadow public abstract int fromLayerY(int mouseY);

    @Inject(method = "subMouseClicked", at = @At("HEAD"), cancellable = true)
    private void bcp$deselectOnEmptyClick(UIContext context, CallbackInfoReturnable<Boolean> cir) {
        UIClips self = (UIClips) (Object) this;

        if (context.mouseButton != 0) return;
        if (!self.area.isInside(context)) return;

        int mouseX = context.mouseX;
        int mouseY = context.mouseY;

        if (this.isInRuler(mouseY)) return;

        int tick = (int) Math.floor(self.getXAxis().from(mouseX));
        int layerY = this.fromLayerY(mouseY);
        Clip clip = this.clips.getClipAt(tick, layerY);

        if (clip != null) return;
        if (this.delegate.getClip() == null) return;

        this.delegate.pickClip(null);
    }
}
