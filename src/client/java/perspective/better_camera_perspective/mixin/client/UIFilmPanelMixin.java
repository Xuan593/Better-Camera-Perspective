package perspective.better_camera_perspective.mixin.client;

import mchorse.bbs_mod.ui.film.UIFilmPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perspective.better_camera_perspective.client.FilmDisplayNames;

/**
 * UIFilmPanel 构造完成后，把面板登记进 {@link FilmDisplayNames}，
 * 供 Form 显示名反查使用。
 */
@Mixin(UIFilmPanel.class)
public abstract class UIFilmPanelMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bcp$registerPanel(CallbackInfo ci) {
        FilmDisplayNames.register((UIFilmPanel) (Object) this);
    }
}
