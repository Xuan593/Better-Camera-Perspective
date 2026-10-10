package perspective.better_camera_perspective.client.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * v1.5-Alpha：预新建剪辑分类（覆盖 / 修改器 / 杂项），参考 FS2.8 的三列分类布局。
 *
 * 按剪辑注册 key（如 bbs:translate）映射分类；未识别的剪辑统一归「杂项」兜底。
 */
@Environment(EnvType.CLIENT)
public final class ClipCategory {
    public static final int COVER = 0;
    public static final int MODIFIER = 1;
    public static final int MISC = 2;

    private ClipCategory() {
    }

    public static int of(String key) {
        String t = key;
        int i = t.indexOf(':');
        if (i >= 0) {
            t = t.substring(i + 1);
        }
        switch (t) {
            case "static":
            case "path":
            case "keyframe":
            case "tracker":
            case "spline":
            case "multi_tracker":
            case "follow":
            case "idle":
                return COVER;
            case "translate":
            case "angle":
            case "drag":
            case "shake":
            case "math":
            case "remapper":
            case "look":
            case "orbit":
            case "dolly_zoom":
            case "cinematic":
                return MODIFIER;
            default:
                return MISC;
        }
    }

    /** 分类配色（V1.5.2-Alpha 起，用于「混合」模式按分类着色，与「原始」形成视觉区别） */
    public static int colorOf(int category) {
        switch (category) {
            case COVER:
                return 0xFF4A90D9; // 覆盖：蓝
            case MODIFIER:
                return 0xFF5CB85C; // 修改器：绿
            default:
                return 0xFF8A8A8A; // 杂项：灰
        }
    }
}
