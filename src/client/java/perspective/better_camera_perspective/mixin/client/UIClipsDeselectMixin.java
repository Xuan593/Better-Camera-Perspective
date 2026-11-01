package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Scale;
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
 */
@Environment(EnvType.CLIENT)
@Mixin(UIClips.class)
public abstract class UIClipsDeselectMixin {

    @Shadow public IUIClipsDelegate delegate;
    @Shadow public Clips clips;
    @Shadow public Scale xAxis;

    @Shadow public abstract boolean isInRuler(int mouseY);

    @Shadow public abstract int fromLayerY(int mouseY);

    @Inject(method = "subMouseClicked", at = @At("HEAD"), cancellable = true)
    private void bcp$deselectOnEmptyClick(UIContext context, CallbackInfoReturnable<Boolean> cir) {
        if (context.mouseButton != 0) return;
        if (!((UIClips)(Object)this).area.isInside(context)) return;

        int mouseX = context.mouseX;
        int mouseY = context.mouseY;

        if (this.isInRuler(mouseY)) return;

        int tick = (int) Math.floor(this.xAxis.from(mouseX));
        int layerY = this.fromLayerY(mouseY);
        Clip clip = this.clips.getClipAt(tick, layerY);

        if (clip != null) return;
        if (this.delegate.getClip() == null) return;

        this.delegate.pickClip(null);
    }
}

