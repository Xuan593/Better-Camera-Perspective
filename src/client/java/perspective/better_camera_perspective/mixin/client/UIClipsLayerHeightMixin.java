package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.utils.MathUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v1.3.3：削减时间轴剪辑条（轨道）占用长度 —— layerHeight 默认与缩放范围整体减半。
 *
 * 轨道条厚度由 {@code UIClips.layerHeight} 控制（默认 20px，Alt+滚轮可调，范围 12~48）。
 * 本次调整：
 *  - 默认厚度 20 → 10（约减半，重进编辑器即生效）；
 *  - Alt+滚轮缩放范围 12~48 → 6~24（同样整体减半）。
 */
@Environment(EnvType.CLIENT)
@Mixin(UIClips.class)
public class UIClipsLayerHeightMixin {
    @Shadow
    private int layerHeight;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bcp$shrinkDefaultLayerHeight(CallbackInfo ci) {
        this.layerHeight = 10;
    }

    @Redirect(
        method = "subMouseScrolled",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/utils/MathUtils;clamp(III)I")
    )
    private int bcp$shrinkLayerHeightRange(int value, int min, int max) {
        return MathUtils.clamp(value, 6, 24);
    }
}
