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

/**
 * V1.7.4-Alpha：POV 轨道名中文翻译补丁。
 *
 * 当 Cubed 的「中文关键帧轨道名称」开关打开时，自动将 POV 时间轴子页
 * （hotbar、Hand、POV Action）的轨道名翻译为中文。
 */
@Environment(EnvType.CLIENT)
@Mixin(UIKeyframeSheet.class)
public abstract class UIKeyframeSheetPovLocalizerMixin {

    @Inject(method = "applyStyle", at = @At("TAIL"))
    private void bcp$localizePovTrackNames(CallbackInfo ci) {
        UIKeyframeSheet self = (UIKeyframeSheet) (Object) this;
        // 由 BcpConflictDetection 设置接管优先级控制，不再依赖 Cubed 开关
        String translated = BcpPovTrackTranslator.translate(self.id);
        if (translated != null && !translated.equals(self.id)) {
            self.title = IKey.constant(translated);
        }
    }
}
