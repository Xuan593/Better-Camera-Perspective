package perspective.better_camera_perspective.client;

import mchorse.bbs_mod.ui.film.FrameOverlays;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.colors.Colors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.math.MatrixStack;
import perspective.better_camera_perspective.clips.FXData;

import java.util.List;

/**
 * 特效移植（FX Port）——画面叠加渲染器（v1.2-Alpha 实验性更新）。
 *
 * 注册进 BBS 的 {@link FrameOverlays}（公开注册点，无需 mixin）：
 *  - 模糊转场：先对当前帧主 framebuffer 执行 GL 模糊后期（FXBlurPass），
 *    使预览与导出画面均呈现模糊效果；
 *  - 色度转场：再在画面之上绘制全屏色度（单色度 / 渐变双色度）。
 *
 * 多个特效剪辑同时活动时取层级（layer）最高者。
 */
@Environment(EnvType.CLIENT)
public class FXOverlays {
    /** 渐变双色度分段数（水平插值），越高越平滑。 */
    private static final int GRADIENT_SEGMENTS = 24;

    /** 注册进 BBS 画面叠加渲染列表（FrameOverlays.setup 为追加式，顺序安全）。 */
    public static void register() {
        FrameOverlays.register(FXOverlays::render);
    }

    private static void render(MatrixStack stack, Batcher2D batcher, ClipContext context) {
        List<FXData> fxs = FXData.get(context);
        FXData fx = pickHighest(fxs);
        if (fx == null) {
            return;
        }

        if (fx.mode == 1 && fx.blur > 0.0D) {
            // 模糊转场：先模糊画面（作用于当前主 framebuffer，预览与导出均生效）
            FXBlurPass.apply(fx);
        } else if (fx.mode == 0 && fx.amount > 0.0D) {
            // 色度转场：画面之上叠加色度层
            renderChroma(stack, batcher, fx);
        }
    }

    private static FXData pickHighest(List<FXData> fxs) {
        FXData best = null;
        for (FXData fx : fxs) {
            if (best == null || fx.layer > best.layer) {
                best = fx;
            }
        }
        return best;
    }

    private static void renderChroma(MatrixStack stack, Batcher2D batcher, FXData fx) {
        Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
        float width = (float) fb.textureWidth * 1080.0F / (float) fb.textureHeight;
        float height = 1080.0F;

        int color1 = fx.color1 == null ? 0xFF000000 : fx.color1.getARGBColor();
        int color2 = fx.color2 == null ? color1 : fx.color2.getARGBColor();
        float alpha = (float) Math.min(1.0D, Math.max(0.0D, fx.amount));

        if (color1 == color2) {
            // 单色度（color2 为空或与 color1 同色 → 同单色度逻辑）
            batcher.box(0.0F, 0.0F, width, height, Colors.setA(color1, alpha));
        } else {
            // 渐变双色度：水平分段插值（color1 → color2）
            float segWidth = width / GRADIENT_SEGMENTS;
            for (int i = 0; i < GRADIENT_SEGMENTS; i++) {
                int c = Colors.lerp(color1, color2, (float) i / (GRADIENT_SEGMENTS - 1));
                batcher.box(i * segWidth, 0.0F, segWidth + 1.0F, height, Colors.setA(c, alpha));
            }
        }
    }
}
