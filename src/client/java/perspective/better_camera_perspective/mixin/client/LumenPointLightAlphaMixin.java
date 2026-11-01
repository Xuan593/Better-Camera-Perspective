package perspective.better_camera_perspective.mixin.client;

import mc.lumencore.client.integration.bbs.render.AbstractLumenLightFormRenderer;
import mc.lumencore.client.light.LumenLight;
import mchorse.bbs_mod.utils.colors.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * V1.7.14-Alpha：灯光实验补丁。
 *
 * 需求：实现"黑光"效果——光源发出的光，照到哪里哪里就变黑。
 *
 * 实现思路：
 * 1. 检测 V=0（RGB 都是 0）的灯光
 * 2. 把灯光强度设成负值，尝试模拟"吸收光"的效果
 * 3. 这只是实验性补丁，效果可能不理想
 */
@Mixin(AbstractLumenLightFormRenderer.class)
public abstract class LumenPointLightAlphaMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");

    @Inject(method = "copyColor", at = @At("RETURN"))
    private static void bcp$blackLightExperiment(LumenLight light, Color color, CallbackInfo ci) {
        // 当 RGB 都是 0 时（V=0），尝试模拟黑光效果
        if (light.red <= 0.001f && light.green <= 0.001f && light.blue <= 0.001f) {
            // 先把 RGB 设成极小值，避免灯光被跳过
            light.red = 0.001f;
            light.green = 0.001f;
            light.blue = 0.001f;
            // 把强度设成负值，尝试模拟"吸收光"的效果
            light.intensity = -Math.abs(light.intensity);
            LOGGER.info("[BCP-Lumen] Black light mode, set intensity={}", light.intensity);
        }
    }
}
