package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.utils.clips.Clip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger BCP_LOG = LoggerFactory.getLogger("BCP-PovDisplay");

    @Inject(method = "getClipDisplayName", at = @At("RETURN"), cancellable = true)
    private void bcp$onGetClipDisplayName(Clip clip, CallbackInfoReturnable<String> cir) {
        String original = cir.getReturnValue();
        BCP_LOG.info("[BCP-PovDisplay] getClipDisplayName called, clip={}, original={}",
                clip != null ? clip.getClass().getSimpleName() : "null", original);
        if (!BcpPovTrackTranslator.isCubedChineseNamesEnabled()) {
            BCP_LOG.info("[BCP-PovDisplay] Cubed ChineseNames disabled, skip");
            return;
        }
        if (original == null || original.isEmpty()) {
            return;
        }
        // 尝试翻译
        String translated = BcpPovTrackTranslator.translate(original);
        BCP_LOG.info("[BCP-PovDisplay] translate result: {} -> {}", original, translated);
        if (translated != null && !translated.equals(original)) {
            cir.setReturnValue(translated);
        }
    }
}
