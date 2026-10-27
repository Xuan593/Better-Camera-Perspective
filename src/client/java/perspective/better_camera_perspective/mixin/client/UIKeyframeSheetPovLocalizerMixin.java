package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perspective.better_camera_perspective.client.BcpPovTrackTranslator;

import java.util.HashMap;
import java.util.Map;

/**
 * V1.7.3-Alpha：POV 轨道名中文翻译补丁。
 *
 * 当 Cubed 的「中文关键帧轨道名称」开关打开时，自动将 POV 时间轴子页
 * （hotbar、Hand、POV Action）的轨道名翻译为中文。
 *
 * 机制：hook UIKeyframeSheet.applyStyle()，在 Cubed 自己的翻译之后，
 * 检查当前 sheet.id 是否在 POV 翻译表里，如果是就替换 title。
 */
@Environment(EnvType.CLIENT)
@Mixin(UIKeyframeSheet.class)
public abstract class UIKeyframeSheetPovLocalizerMixin {

    @Inject(method = "applyStyle", at = @At("TAIL"))
    private void bcp$localizePovTrackNames(CallbackInfo ci) {
        UIKeyframeSheet self = (UIKeyframeSheet) (Object) this;
        if (!BcpPovTrackTranslator.isCubedChineseNamesEnabled()) {
            return;
        }
        String translated = BcpPovTrackTranslator.translate(self.id);
        if (translated != null && !translated.equals(self.id)) {
            self.title = IKey.constant(translated);
        }
    }
}
