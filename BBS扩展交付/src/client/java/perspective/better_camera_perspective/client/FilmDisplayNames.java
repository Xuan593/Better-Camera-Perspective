package perspective.better_camera_perspective.client;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.film.UIFilmPanel;

/**
 * 记录当前打开过的影片编辑面板（弱引用，防止泄漏），
 * 用于把「匿名表单」反查回对应的演员显示名。
 *
 * bug 根因：Anchor 的 actor 属性存储的是 Replay 的稳定 ID（8 位十六进制，
 * 如 071cb8c1）；当绑定表单未设置自定义名称时，Form.getFormIdOrName() /
 * getDisplayName() 只能返回 form 类型 Link（如 bbs:model），而不会回查
 * 影片中哪个 Replay 使用了这张表单。
 *
 * 本类维护「已打开的影片面板」列表，按 Form 匹配 Replay，返回
 * replay.label（演员的自定义名称），供 mixin 替换显示。
 *
 * 匹配规则（2026-10-02 v2 加固）：
 *  1. 引用相等：form 与 replay.form.get() 是同一实例（最常见）；
 *  2. formId 相等：影片重新加载后表单可能是不同实例，但类型 Link 相同——
 *     此时仍能反查到使用该类型表单的演员（优先取有自定义名/表单名的那个）。
 */
public final class FilmDisplayNames {
    private static final List<WeakReference<UIFilmPanel>> PANELS = new ArrayList<>();

    private FilmDisplayNames() {
    }

    /** 面板构造完成后调用（见 UIFilmPanelMixin）。 */
    public static void register(UIFilmPanel panel) {
        if (panel != null) {
            PANELS.add(new WeakReference<>(panel));
        }
    }

    /**
     * 若 form 是某个已打开影片中演员的表单，返回该演员的显示名：
     * 优先 replay.label（自定义名），其次 form.name（表单名）；
     * 都没有则返回 null（由调用方回退到原逻辑，即显示 form 类型）。
     */
    public static String lookupActorName(Form form) {
        if (form == null) {
            return null;
        }

        String formId = form.getFormId();
        Iterator<WeakReference<UIFilmPanel>> it = PANELS.iterator();
        while (it.hasNext()) {
            UIFilmPanel panel = it.next().get();
            if (panel == null) {
                it.remove();
                continue;
            }

            Film film = panel.getData();
            if (film == null) {
                continue;
            }

            for (Replay replay : film.replays.getList()) {
                Form other = replay.form.get();
                if (other == null) {
                    continue;
                }

                // 引用相等（同实例），或类型相等（跨加载实例兜底）
                if (other == form || other.getFormId().equals(formId)) {
                    String label = replay.label.get();
                    if (!label.isEmpty()) {
                        return label;
                    }
                    String name = other.name.get();
                    if (!name.isEmpty()) {
                        return name;
                    }
                }
            }
        }

        return null;
    }
}
