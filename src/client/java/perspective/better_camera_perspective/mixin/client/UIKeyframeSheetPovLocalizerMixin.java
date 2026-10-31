package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perspective.better_camera_perspective.client.BcpPovTrackTranslator;

/**
 * POV 轨道名中文翻译补丁（UIKeyframeSheet hook）。
 */
@Environment(EnvType.CLIENT)
@Mixin(UIKeyframeSheet.class)
public abstract class UIKeyframeSheetPovLocalizerMixin {

    private static final Logger BCP_LOG = LoggerFactory.getLogger("BCP-KeyframeSheet");
    private static boolean firstHitLogged = false;

    @Inject(method = "applyStyle", at = @At("TAIL"))
    private void bcp$localizePovTrackNames(CallbackInfo ci) {
        UIKeyframeSheet self = (UIKeyframeSheet) (Object) this;
        if (!firstHitLogged) {
            BCP_LOG.debug("[BCP-KeyframeSheet] applyStyle hook 首次触发，id={}", self.id);
            firstHitLogged = true;
        }
        String translated = BcpPovTrackTranslator.translate(self.id);
        if (translated != null && !translated.equals(self.id)) {
            self.title = IKey.constant(translated);
        }
    }
}
