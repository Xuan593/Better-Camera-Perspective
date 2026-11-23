package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perspective.better_camera_perspective.client.ui.UIXyzTripleKeyframeGraph;
import perspective.better_camera_perspective.data.XyzFrameDataKeyframeFactory;

/**
 * V1.7.22-Alpha：仅当进入 XYZ 轨道（{@code bcp_xyz_frame}）的曲线编辑时，
 * 把 {@code UIKeyframes.currentGraph} 换成三子窗图。
 *
 * <p>对应入口是 BBS 轨道右键「编辑轨道」/ 轨道编辑按钮触发的
 * {@link UIKeyframes#editSheet(UIKeyframeSheet)}；摄像机编辑器整块界面不受影响，
 * 其余轨道（位置 / 旋转 / 锚点等）继续使用 BBS 原生单轴图。</p>
 */
@Mixin(UIKeyframes.class)
public class UIKeyframesXyzGraphMixin {
    @Shadow
    private IUIKeyframeGraph currentGraph;

    @Inject(method = "editSheet", at = @At("TAIL"))
    private void bcp$useXyzTripleGraph(UIKeyframeSheet sheet, CallbackInfo info) {
        if (sheet == null || sheet.channel.getFactory() != XyzFrameDataKeyframeFactory.INSTANCE) {
            return;
        }

        UIXyzTripleKeyframeGraph graph = new UIXyzTripleKeyframeGraph((UIKeyframes) (Object) this, sheet);

        this.currentGraph = graph;
        graph.resize();
    }
}
