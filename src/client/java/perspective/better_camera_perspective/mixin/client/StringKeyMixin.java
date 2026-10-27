package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.l10n.keys.StringKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import perspective.better_camera_perspective.client.BcpPovTrackTranslator;

@Mixin(StringKey.class)
public abstract class StringKeyMixin {
    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private void bcp$translateStringKey(CallbackInfoReturnable<String> cir) {
        if (!BcpPovTrackTranslator.isCubedChineseNamesEnabled()) {
            return;
        }
        String original = cir.getReturnValue();
        if (original == null || original.isEmpty()) {
            return;
        }
        // 只翻译翻译表里有的字符串
        String translated = BcpPovTrackTranslator.translate(original);
        if (!original.equals(translated)) {
            System.out.println("[BCP-PovDisplay] StringKey.get: " + original + " -> " + translated);
            cir.setReturnValue(translated);
        }
    }
}
