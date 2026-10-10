package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.settings.ui.UIValueFactory;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

/**
 * V1.5.0：将「往期日志」子设置页按钮锁定在设置页子页区域 1 号位。
 *
 * BBS 每次切换模块都会调用 {@code buildSections()} 重建子设置页按钮列表（UISectionButton），
 * 我们在其 RETURN 后找到本插件「past_logs」分类对应的按钮并移至列表首位：
 * 无论 settings.categories 的迭代顺序如何，「往期日志」恒定位于子设置页区域最上方；
 * 其他子设置页（可视化设置 / 关键帧处理设置）的内容与顺序完全不动。
 */
@Mixin(UISettingsOverlayPanel.class)
public class UISettingsOverlayPanelMixin {
    @Shadow
    public UIScrollView sections;

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
}
