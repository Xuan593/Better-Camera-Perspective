package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.utils.clips.Clip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import perspective.better_camera_perspective.client.BcpPovTrackTranslator;

/**
 * V1.7.6-Alpha：hook UIClips.getClipDisplayName(Clip) 方法。
 *
 * 右键菜单里的 action 类型名、以及时间轴上已存在的 clip 名，
 * 最终都会走这个方法。在返回字符串时做翻译替换。
 */
@Mixin(UIClips.class)
public abstract class UIClipsPovDisplayNameMixin {

    @Inject(method = "getClipDisplayName", at = @At("RETURN"), cancellable = true)
    private void bcp$onGetClipDisplayName(Clip clip, CallbackInfoReturnable<String> cir) {
        if (!BcpPovTrackTranslator.isCubedChineseNamesEnabled()) {
            return;
        }
        String original = cir.getReturnValue();
        if (original == null || original.isEmpty()) {
            return;
        }
        // 尝试翻译
        String translated = BcpPovTrackTranslator.translate(original);
        if (translated != null && !translated.equals(original)) {
            cir.setReturnValue(translated);
        }
    }
}
