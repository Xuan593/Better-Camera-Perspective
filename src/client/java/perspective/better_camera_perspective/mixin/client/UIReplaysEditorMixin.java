package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perspective.better_camera_perspective.client.BcpKeyframeProcessing;

import java.util.List;

/**
 * V1.6.2（三修）：「关键帧展开模式」注入（回放编辑器）。
 *
 * 背景修正：用户实际打开的是影片面板（UIFilmPanel）中的「回放编辑器」
 * （UIReplaysEditor，编辑 Replay 关键帧轨道），而非动画面板中的
 * UIAnimationStateEditor。BBS 2.7 起，打开回放时 `getExpandedTracks()`
 * 会为该回放创建默认只展开 `replay_section/position_rotation` 的折叠状态
 * （FoldState 无参构造 = 默认全折叠），其余部件关键帧轨道全部折叠，
 * 需要手动逐个点出，非常麻烦。
 *
 * 本注入：当设置「关键帧展开模式 = 原始」时，在每次轨道列表重建完成后
 * （updateChannelsList RETURN，覆盖打开回放 / 切换分类 / 切换部件 / 全部轨道
 * 等所有路径），把当前回放的展开状态（getExpandedTracks() 返回的 FoldState，
 * 与 dope sheet 的 folds 引用同一对象）中全部 sheet / section 设为展开——
 * 一次性展开所有部件关键帧轨道；被可视度筛选（disabledSheets）排除的轨道
 * 不会进入 sheets 列表，天然不会被展开。
 *
 * 该展开状态写入 expandedTracksByReplay（按回放记忆），切换回放再回来仍保持
 * 展开；用户仍可手动折叠单条轨道。「默认」模式不注入任何行为，保持 BBS 2.7
 * 原逻辑。
 */
@Environment(EnvType.CLIENT)
@Mixin(UIReplaysEditor.class)
public class UIReplaysEditorMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("better_camera_perspective_client");

    @Inject(method = "updateChannelsList", at = @At("RETURN"))
    private void bcp$updateChannelsList(CallbackInfo ci) {
        int mode = BcpKeyframeProcessing.getExpandMode();
        if (mode != BcpKeyframeProcessing.EXPAND_ORIGINAL_IDX) {
            return;
        }
        UIReplaysEditor self = (UIReplaysEditor) (Object) this;
        if (self.keyframeEditor == null || self.keyframeEditor.view == null) {
            return;
        }
        UIKeyframeDopeSheet dope = self.keyframeEditor.view.getDopeSheet();
        if (dope == null) {
            return;
        }
        List<UIKeyframeSheet> sheets = dope.getSheets();
        FoldState<String> tracks = self.getExpandedTracks();
        for (UIKeyframeSheet sheet : sheets) {
            if (sheet.section != null) {
                tracks.set(sheet.section.id(), true);
            }
            tracks.set(sheet.id, true);
        }
        // V1.7.0：强制重新布局，避免展开后渲染缓存导致轨道堆叠
        dope.resize();
        LOGGER.info("[BCP] expand replays: mode={} sheets={}", mode, sheets.size());
    }
}
