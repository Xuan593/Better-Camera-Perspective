package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.context.ColorfulContextAction;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
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
import perspective.better_camera_perspective.client.BcpConflictManager;
import perspective.better_camera_perspective.client.BcpVisibility;
import perspective.better_camera_perspective.client.ui.BcpAdaptiveClipMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * v1.2.4-Alpha：摄像机编辑器右键添加剪辑 —— 自适应多列菜单。
 *
 * 原逻辑在 {@code UIClips.showAddClips} 中直接以 {@code this.factory.getKeys()}
 * 构建一长竖串的剪辑选择菜单；本 mixin 在方法开头接管：
 *  - 兜底触发一次剪辑可见性功能的来源检测（冲突弹窗，仅多来源冲突时弹出）；
 *  - 菜单最前恒定插入「可视性设置」项（列1 第一格，1 号位）；
 *  - 其余剪辑按 {@link BcpVisibility} 过滤；
 *  - 改用自适应多列右键菜单：每列最多 8 个，多出的自动排下一列，各列平均分配。
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
        BcpConflictManager.checkAndShow(context);

        if (preview == null) {
            this.addPreview = null;
            context.notifyError(UIKeys.CAMERA_TIMELINE_CANT_FIT_NOTIFICATION);
        } else {
            List<ContextAction> actions = new ArrayList<>();

            // 「可视性设置」伪条目：恒定排在菜单最前（列1 第一格 = 1 号位），与其他剪辑视觉同格式
            actions.add(new ContextAction(
                Icons.SETTINGS,
                L10n.lang("bbs.ui.camera.visibility_settings"),
                () -> BcpVisibility.openVisibilityPanel(context)
            ));

            for (Link key : this.factory.getKeys()) {
                if (!BcpVisibility.isClipVisible(key)) {
                    continue;
                }
                ClipFactoryData data = this.factory.getData(key);
                Runnable run = () -> this.addClip(key, preview.x, preview.y, preview.z);
                ContextAction action = data.color != 0
                    ? new ColorfulContextAction(data.icon, UIKeys.C_CLIP.get(key), run, data.color)
                    : new ContextAction(data.icon, UIKeys.C_CLIP.get(key), run);
                actions.add(action);
            }

            if (!actions.isEmpty()) {
                context.replaceContextMenu(new BcpAdaptiveClipMenu(actions));
            }
            this.addPreview = preview;
        }

        ci.cancel();
    }
}
