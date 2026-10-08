package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import perspective.better_camera_perspective.client.BcpClipProcessing;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * v1.3.2：「整理剪辑动画」。
 *
 * 原理：renderCameraWork 每帧快照各剪辑所在层（clip → layer），
 * 检测到剪辑层位变化（即触发了「整理剪辑」sortLayers）时记录旧层位并启动过渡动画，
 * 动画期间在 getClipArea 中按插值偏移剪辑条纵向位置，实现平滑移动到新层位。
 * 仅渲染层偏移，不修改任何数据；开关关闭时不做快照与插值。
 */
@Environment(EnvType.CLIENT)
@Mixin(UIClips.class)
public abstract class UIClipsMixin {
    /** 整理剪辑动画时长（毫秒） */
    private static final long ANIM_MS = 200;

    @Shadow
    private Clips clips;

    @Shadow
    private boolean grabbing;

    /** 上一帧快照：clip → layer */
    private Map<Clip, Integer> bcp$prevLayers = null;
    /** 动画中的剪辑：clip → 旧 layer */
    private final Map<Clip, Integer> bcp$moving = new IdentityHashMap<>();
    /** 动画起始时间戳 */
    private long bcp$animStart = 0L;

    @Inject(method = "renderCameraWork", at = @At("HEAD"))
    private void bcp$detectLayerChange(UIContext context, CallbackInfo ci) {
        if (!BcpClipProcessing.isEnabled(BcpClipProcessing.KEY_REORGANIZE_ANIMATION)) {
            this.bcp$moving.clear();
            this.bcp$prevLayers = null;
            return;
        }

        Map<Clip, Integer> now = new IdentityHashMap<>();
        for (Clip clip : this.clips.get()) {
            now.put(clip, clip.layer.get());
        }

        if (this.bcp$prevLayers == null) {
            this.bcp$prevLayers = now;
            return;
        }

        boolean changed = false;
        for (Map.Entry<Clip, Integer> entry : now.entrySet()) {
            Integer prev = this.bcp$prevLayers.get(entry.getKey());
            if (prev != null && !prev.equals(entry.getValue())) {
                if (!this.bcp$moving.containsKey(entry.getKey())) {
                    this.bcp$moving.put(entry.getKey(), prev);
                }
                changed = true;
            }
        }

        if (changed && !this.grabbing) {
            this.bcp$animStart = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - this.bcp$animStart > ANIM_MS) {
            this.bcp$moving.clear();
        }
        this.bcp$prevLayers = now;
    }

    @Inject(method = "getClipArea", at = @At("RETURN"))
    private void bcp$animateClipArea(Clip clip, Area area, int h, CallbackInfoReturnable<Area> cir) {
        if (this.bcp$moving.isEmpty()) {
            return;
        }
        Integer oldLayer = this.bcp$moving.get(clip);
        if (oldLayer == null) {
            return;
        }

        long elapsed = System.currentTimeMillis() - this.bcp$animStart;
        float t = Math.min(1.0F, (float) elapsed / (float) ANIM_MS);
        t = 1.0F - (1.0F - t) * (1.0F - t);

        Area out = cir.getReturnValue();
        int newY = out.y;
        int oldY = ((UIClips) (Object) this).toLayerY(oldLayer);
        out.y = Math.round(oldY + (newY - oldY) * t);

        if (t >= 1.0F) {
            this.bcp$moving.remove(clip);
        }
    }
}
