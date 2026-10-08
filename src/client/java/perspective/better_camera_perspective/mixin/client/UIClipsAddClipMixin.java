package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.context.UIChoiceMenu;
import mchorse.bbs_mod.ui.utils.icons.Icons;
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

import java.util.ArrayList;
import java.util.List;

/**
 * v1.2.1：摄像机编辑器右键添加剪辑菜单（「可视性设置」+ 可见性筛选 + 冲突预防）。
 *
 * 原逻辑在 {@code UIClips.showAddClips} 中直接以 {@code this.factory.getKeys()}
 * 构建剪辑选择菜单；本 mixin 在方法开头接管：
 *  - 兜底触发一次剪辑可见性功能的来源检测（冲突弹窗，会话内幂等）；
 *  - 功能未封锁时，在菜单最顶部（「静止」上方）插入「可视性设置」项，
 *    点击打开可视性筛选面板；
 *  - 其余剪辑按 {@link BcpVisibility} 过滤后重建菜单（逻辑与原实现一致，仅过滤一步）。
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
            context.replaceContextMenu(
                add -> {
                    // 「可视性设置」伪条目：排在菜单最前（「静止」上方），与其他剪辑视觉同格式
                    if (!BcpVisibility.isBlocked()) {
                        add.action(
                            Icons.SETTINGS,
                            L10n.lang("bbs.ui.camera.visibility_settings"),
                            () -> BcpVisibility.openVisibilityPanel(context)
                        );
                    }

                    List<Link> visible = new ArrayList<>();
                    for (Link key : this.factory.getKeys()) {
                        if (BcpVisibility.isClipVisible(key)) {
                            visible.add(key);
                        }
                    }

                    UIChoiceMenu.of(visible)
                        .icon(type -> this.factory.getData(type).icon)
                        .label(type -> UIKeys.C_CLIP.get(type))
                        .color(type -> this.factory.getData(type).color)
                        .build(add, UIKeys.CAMERA_TIMELINE_KEYS_CLIPS, type -> this.addClip(type, preview.x, preview.y, preview.z));
                    add.onClose(m -> this.addPreview = null);
                }
            );
            this.addPreview = preview;
        }

        ci.cancel();
    }
}
