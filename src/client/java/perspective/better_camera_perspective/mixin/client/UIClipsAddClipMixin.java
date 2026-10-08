package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.IFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Vector3i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perspective.better_camera_perspective.client.BcpVisibility;
import perspective.better_camera_perspective.client.ui.BcpClipGridPanel;

/**
 * v1.2.2-Alpha：摄像机编辑器右键添加剪辑 —— 多列网格选择面板。
 *
 * 原逻辑在 {@code UIClips.showAddClips} 中直接以 {@code this.factory.getKeys()}
 * 构建一长竖串的剪辑选择菜单；本 mixin 在方法开头接管：
 *  - 兜底触发一次剪辑可见性功能的来源检测（冲突弹窗，仅多来源冲突时弹出）；
 *  - 改为弹出多列网格选择面板（仿「可视性设置」，默认 2 列、可切 1/2/3 列），
 *    面板顶栏提供「可视性设置」入口，剪辑按 {@link BcpVisibility} 过滤；
 *  - 点击某项以右键位置添加对应剪辑。
 */
@Environment(EnvType.CLIENT)
@Mixin(UIClips.class)
public class UIClipsAddClipMixin {
    @Shadow
    @Final
    private IFactory<Clip, ClipFactoryData> factory;

    @Shadow
    private Vector3i addPreview;

    @Shadow
    public void addClip(Link type, int tick, int layer, int duration) {
        throw new AssertionError();
    }

    @Inject(method = "showAddClips", at = @At("HEAD"), cancellable = true)
    private void bcp$showAddClips(UIContext context, Vector3i preview, CallbackInfo ci) {
        BcpVisibility.checkConflict(context);

        if (preview == null) {
            this.addPreview = null;
            context.notifyError(UIKeys.CAMERA_TIMELINE_CANT_FIT_NOTIFICATION);
        } else {
            UIOverlay.addOverlay(
                context,
                new BcpClipGridPanel(context, type -> this.addClip(type, preview.x, preview.y, preview.z)),
                460, 460
            );
            this.addPreview = preview;
        }

        ci.cancel();
    }
}
