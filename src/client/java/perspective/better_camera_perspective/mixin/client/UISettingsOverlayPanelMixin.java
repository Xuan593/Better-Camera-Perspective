package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.settings.ui.UIValueFactory;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

/**
 * V1.5.0：设置页「摄像机编辑设置」相关适配。
 *
 *  - {@link #bcp$pinPastLogs}：将「往期日志」子设置页按钮锁定在子设置页区域 1 号位
 *    （BBS 每次切换模块都会调用 {@code buildSections()} 重建子设置页按钮列表，
 *    在其 RETURN 后把本插件「past_logs」分类按钮移至列表首位，其他子设置页不动）；
 *  - {@link #bcp$adaptLogsHeight}：每次渲染前将「往期日志」的 UITextarea 高度
 *    自适应为 options 滚动区视口高度，内容只在本插件文本框内部滚动，
 *    不再出现外层 options 垂直滚动条（消除两层滚动条影响体验的问题）。
 */
@Mixin(UISettingsOverlayPanel.class)
public class UISettingsOverlayPanelMixin {
    @Shadow
    public UIScrollView sections;

    @Shadow
    public UIScrollView options;

    @Inject(method = "buildSections", at = @At("RETURN"))
    private void bcp$pinPastLogs(CallbackInfo ci) {
        try {
            List<IUIElement> children = this.sections.getChildren();
            for (IUIElement child : children) {
                if (child.getClass().getSimpleName().equals("UISectionButton")) {
                    Field f = child.getClass().getDeclaredField("category");
                    f.setAccessible(true);
                    Object group = f.get(child);
                    if (group instanceof BaseValue) {
                        String key = UIValueFactory.getTitleKey((BaseValue) group);
                        if (key != null && key.contains("config.past_logs")) {
                            this.sections.remove(child);
                            this.sections.prepend(child);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 锁定失败不阻塞设置页，不影响其他正常功能
        }
    }

    @Inject(method = "renderBackground", at = @At("RETURN"))
    private void bcp$adaptLogsHeight(CallbackInfo ci) {
        try {
            if (this.options == null) {
                return;
            }
            int viewportH = this.options.area.h;
            for (IUIElement el : this.options.getChildren()) {
                if (el instanceof UITextarea) {
                    ((UITextarea) el).h(Math.max(64, viewportH - 8)).resize();
                }
            }
        } catch (Exception e) {
            // 自适应失败不阻塞设置页，不影响其他正常功能
        }
    }
}
