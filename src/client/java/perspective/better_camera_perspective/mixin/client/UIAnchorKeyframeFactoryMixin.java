package perspective.better_camera_perspective.mixin.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.StableIds;
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
 * 相比 BBS 原逻辑：
 *  1. 不再按实体实例化状态过滤 —— 影片全部演员（含新建、未实例化的）都能选中；
 *  2. 按 Replay.category（组/类别）分组做二级选择（一级 = 类别，二级 = 演员，带返回）；
 *  3. 菜单可滚动（条目 ≥12 自动出现搜索过滤框）。
 *
 * v1.0.2：修复「显示名回到猜模型」。
 *  FormDisplayNameMixin 的反查此前用「formId（类型）相等」匹配，会误伤资产库伪装
 *  （资产库伪装与影片演员共用同一模板类型时，名称被集体覆盖）—— 该宽匹配已移除；
 *  但移除后，影片锚点列表里没有自定义名的演员（label/name 为空）退化成显示类型
 *  （bbs:model 等），多个同类型演员无法区分。
 *  本处修复：在列表内部做「影片内借用」—— 同一影片里同类型演员若有可辨识名
 *  （label / form.name），则借用它显示（不影响 FormDisplayNameMixin，资产库不受影响）；
 *  都没有时兜底显示「类型·短ID」，保证每个条目唯一可区分。
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
                bcp$addActor(menu, replay, value, callback, list);
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
                bcp$addActor(menu, replay, value, callback, list);
            }
        });
    }

    /** 单个演员条目（复用 BBS 的 ReplayContextAction，显示缩略图 + 可辨识名）。 */
    private static void bcp$addActor(ContextMenuManager menu, Replay replay, String value, Consumer<String> callback, List<Replay> all) {
        int color = replay.getId().equals(value) ? BBSSettings.primaryColor(0) : 0;
        menu.action(new ReplayContextAction(replay, IKey.raw(bcp$displayName(replay, all)), () -> callback.accept(replay.getId()), color));
    }

    /** 构建可辨识显示名：label → form.name → 影片内同类型借用 → 类型·短ID。 */
    private static String bcp$displayName(Replay replay, List<Replay> all) {
        String label = replay.label.get();
        if (!label.isEmpty() && !bcp$isCode(label)) {
            return label;
        }

        Form form = replay.form.get();
        if (form != null) {
            String name = form.name.get();
            if (!name.isEmpty() && !bcp$isCode(name)) {
                return name;
            }
        }

        // 影片内借用：同类型演员的可辨识名（仅在本影片 Replay 范围内，资产库不受影响）
        if (form != null) {
            String formId = form.getFormId();
            for (Replay other : all) {
                if (other == replay) {
                    continue;
                }
                Form otherForm = other.form.get();
                if (otherForm != null && otherForm.getFormId().equals(formId)) {
                    String otherLabel = other.label.get();
                    if (!otherLabel.isEmpty() && !bcp$isCode(otherLabel)) {
                        return otherLabel;
                    }
                    String otherName = otherForm.name.get();
                    if (!otherName.isEmpty() && !bcp$isCode(otherName)) {
                        return otherName;
                    }
                }
            }
        }

        // 兜底：类型·短ID，保证每个条目唯一可区分
        String id = replay.getId();
        String shortId = id.length() > 4 ? id.substring(0, 4) : id;
        return (form == null ? "?" : form.getFormId()) + "·" + shortId;
    }

    /** 判断字符串是否是「数据码」样式（纯 8 位稳定 ID，或 "8位hex - xxx"）。 */
    private static boolean bcp$isCode(String s) {
        if (StableIds.isStableId(s)) {
            return true;
        }
        return s.matches("(?i)[0-9a-f]{8}\\s*-\\s*.*");
    }
}
