package perspective.better_camera_perspective.client.ui;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.utils.clips.Clips;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import perspective.better_camera_perspective.clips.FXClip;

/**
 * 特效移植（FX Port）——剪辑 UI 面板（v1.2-Alpha 实验性更新）。
 *
 * 面板仅两个控件：
 *  - 特效类型切换（色度转场 / 模糊转场）；
 *  - 编辑按钮：打开关键帧编辑器，color1 / color2 / amount / blur / blurX /
 *    blurY / spreadMode / spreadType / expr 全部为关键帧轴，逐轴打点即可
 *    为画面叠加转场特效。
 */
@Environment(EnvType.CLIENT)
public class UIFXClip extends UIClip<FXClip> {
    public UIButton edit;
    public UIKeyframeEditor keyframes;
    public UIButton modeBtn;

    public UIFXClip(FXClip clip, IUIClipsDelegate editor) {
        super(clip, editor);
    }

    @Override
    protected void registerUI() {
        super.registerUI();

        // —— 关键帧编辑器（全部特效轴）——
        this.keyframes = new UIKeyframeEditor(consumer -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes
            .view
            .rulerRenderer(
                context -> UIReplaysEditor.renderRuler(context, this.keyframes.view, (UIClipsPanel) this.editor, (Clips) this.clip.getParent(), this.clip.tick.get())
            );
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("fx_clip_keyframes");
        this.edit = new UIButton(UIKeys.GENERAL_EDIT, b -> {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            this.keyframes.view.getGraph().clearSelection();
        });
        this.edit.keys().register(Keys.FORMS_EDIT, () -> this.edit.clickItself());

        // —— 特效类型切换 ——
        this.modeBtn = new UIButton(L10n.lang("bbs.ui.camera.panels.fx_mode_chroma"), b -> {
            this.clip.mode.set(this.clip.mode.get() == 0 ? 1 : 0);
            this.modeBtn.label = L10n.lang(this.clip.mode.get() == 0 ? "bbs.ui.camera.panels.fx_mode_chroma" : "bbs.ui.camera.panels.fx_mode_blur");
        });
    }

    @Override
    protected void registerPanels() {
        super.registerPanels();
        this.panels.add(this.section(L10n.lang("bbs.ui.camera.panels.fx_mode"), new UIElement[]{this.modeBtn}));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_KEYFRAMES, new UIElement[]{this.edit}));
    }
}
