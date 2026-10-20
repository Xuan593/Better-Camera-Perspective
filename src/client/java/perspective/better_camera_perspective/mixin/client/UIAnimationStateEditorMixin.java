package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.ui.forms.editors.states.keyframes.UIAnimationStateEditor;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perspective.better_camera_perspective.client.BcpKeyframeProcessing;

import java.util.List;

/**
 * V1.6.2：「关键帧展开模式」注入（动画状态编辑器）。
 *
 * 背景：BBS 2.7 更新后，动画编辑器（Form 动画状态编辑器）在打开 / 切换部件
 * （UIAnimationStateEditor.setState）时会对 expandedTabs 执行 collapseAll()，
 * 所有部件关键帧轨道默认折叠，需手动逐个点出展开，非常麻烦。
 *
 * 本注入：当设置「关键帧展开模式 = 原始」时，在 setState 完成后把
 * expandedTabs（已通过 UIKeyframeDopeSheet.setExpanded 与 dope sheet 的 folds 引用同一对象）
 * 中所有 sheet / section 设为展开 —— 一次性展开全部部件关键帧轨道；
 * 被可视度筛选排除的轨道不会进入 dope sheet 的 sheets 列表，天然不会被展开。
 * 「默认」模式不注入任何行为，保持 BBS 2.7 原逻辑。
 */
@Environment(EnvType.CLIENT)
@Mixin(UIAnimationStateEditor.class)
public class UIAnimationStateEditorMixin {
    @Shadow
    @Final
    private FoldState<String> expandedTabs;

    @Inject(method = "setState", at = @At("RETURN"))
    private void bcp$setState(UIAnimationStateEditor state, CallbackInfo ci) {
        if (BcpKeyframeProcessing.getExpandMode() != BcpKeyframeProcessing.EXPAND_ORIGINAL_IDX) {
            return;
        }
        UIKeyframeDopeSheet dope = this.bcp$dopeSheet();
        if (dope == null) {
            return;
        }
        List<UIKeyframeSheet> sheets = dope.getSheets();
        for (UIKeyframeSheet sheet : sheets) {
            if (sheet.section != null) {
                this.expandedTabs.set(sheet.section.id(), true);
            }
            this.expandedTabs.set(sheet.id, true);
        }
    }

    /** 当前动画状态编辑器的 dope sheet（无编辑器 / 无视图时返回 null）。 */
    private UIKeyframeDopeSheet bcp$dopeSheet() {
        UIAnimationStateEditor self = (UIAnimationStateEditor) (Object) this;
        if (self.keyframeEditor == null || self.keyframeEditor.view == null) {
            return null;
        }
        return self.keyframeEditor.view.getDopeSheet();
    }
}
