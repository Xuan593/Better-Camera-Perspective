package perspective.better_camera_perspective.mixin.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.ReplayContextAction;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v1.0.1：接管 UIAnchorKeyframeFactory.displayActors（锚点「选择角色」列表）。
 *
 * BBS 原逻辑的问题：
 *  1. 用 entity != null 过滤 —— 新建的演员（Replay）若尚未实例化实体，
 *     列表里就看不到，导致「新建的伪装无法被识别」；
 *  2. 列表平铺且无分组，演员一多无法按组/类别浏览。
 *
 * 替换后的行为：
 *  - 显示影片中的全部 Replay（不再按实体实例化状态过滤）；
 *  - 按 Replay.category（组/类别）分组做二级选择：一级 = 类别（含「未分类」），
 *    二级 = 该类别下的演员；只有一个类别时直接平铺；
 *  - 二级菜单带「返回」；菜单本身可滚动（UISimpleContextMenu 内置，条目 ≥12 时
 *    自动出现过滤搜索框），不再受屏幕高度限制只能看到十余个。
 */
@Environment(EnvType.CLIENT)
@Mixin(UIAnchorKeyframeFactory.class)
public class UIAnchorKeyframeFactoryMixin {
    @Inject(method = "displayActors", at = @At("HEAD"), cancellable = true)
    private static void bcp$displayActors(UIContext context, Map<String, IEntity> entities, String value, Consumer<String> callback, CallbackInfo ci) {
        bcp$showActorList(context, value, callback);
        ci.cancel();
    }

    /** 一级列表：按类别分组（"" 归入「未分类」）；单组时直接平铺。 */
    private static void bcp$showActorList(UIContext context, String value, Consumer<String> callback) {
        List<UIFilmPanel> children = context.menu.main.getChildren(UIFilmPanel.class);
        UIFilmPanel panel = children.isEmpty() ? null : children.get(0);
        List<Replay> replays = panel != null ? panel.getData().replays.getList() : List.of();

        Map<String, List<Replay>> groups = new LinkedHashMap<>();
        for (Replay replay : replays) {
            String category = Replay.normalizeCategory(replay.category.get());
            groups.computeIfAbsent(category, k -> new ArrayList<>()).add(replay);
        }

        if (groups.size() <= 1) {
            List<Replay> list = groups.isEmpty() ? List.of() : groups.values().iterator().next();
            bcp$showActors(context, value, callback, list);
            return;
        }

        UISimpleContextMenu replayMenu = new UISimpleContextMenu();
        replayMenu.actions.scroll.scrollItemSize = 30;
        context.replaceContextMenu(menu -> {
            menu.custom(replayMenu);
            menu.autoKeys();
            menu.action(Icons.CLOSE, UIKeys.GENERAL_NONE, 16728153, () -> callback.accept(""));
            for (Map.Entry<String, List<Replay>> entry : groups.entrySet()) {
                IKey title = entry.getKey().isEmpty() ? L10n.lang("bbs.ui.camera.panels.anchor_uncategorized") : IKey.raw(entry.getKey());
                List<Replay> list = entry.getValue();
                menu.action(Icons.FOLDER, title, () -> bcp$showGroup(context, value, callback, list));
            }
        });
    }

    /** 二级：组内演员列表（带「返回」）。 */
    private static void bcp$showGroup(UIContext context, String value, Consumer<String> callback, List<Replay> list) {
        UISimpleContextMenu replayMenu = new UISimpleContextMenu();
        replayMenu.actions.scroll.scrollItemSize = 30;
        context.replaceContextMenu(menu -> {
            menu.custom(replayMenu);
            menu.autoKeys();
            menu.action(Icons.ARROW_LEFT, L10n.lang("bbs.ui.camera.panels.back"), () -> bcp$showActorList(context, value, callback));
            for (Replay replay : list) {
                bcp$addActor(menu, replay, value, callback);
            }
        });
    }

    /** 平铺：全部演员。 */
    private static void bcp$showActors(UIContext context, String value, Consumer<String> callback, List<Replay> list) {
        UISimpleContextMenu replayMenu = new UISimpleContextMenu();
        replayMenu.actions.scroll.scrollItemSize = 30;
        context.replaceContextMenu(menu -> {
            menu.custom(replayMenu);
            menu.autoKeys();
            menu.action(Icons.CLOSE, UIKeys.GENERAL_NONE, 16728153, () -> callback.accept(""));
            for (Replay replay : list) {
                bcp$addActor(menu, replay, value, callback);
            }
        });
    }

    /** 单个演员条目（复用 BBS 的 ReplayContextAction，显示缩略图 + 名称）。 */
    private static void bcp$addActor(ContextMenuManager menu, Replay replay, String value, Consumer<String> callback) {
        int color = replay.getId().equals(value) ? BBSSettings.primaryColor(0) : 0;
        menu.action(new ReplayContextAction(replay, IKey.raw(replay.getName()), () -> callback.accept(replay.getId()), color));
    }
}
