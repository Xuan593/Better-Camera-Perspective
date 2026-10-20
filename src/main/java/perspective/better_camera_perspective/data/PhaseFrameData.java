package perspective.better_camera_perspective.data;

import mchorse.bbs_mod.data.types.MapType;

/**
 * 相位追踪（Phase Tracker）帧值（V1.6.1-Alpha）。
 *
 * 「相位追踪」是多样追踪的新关键帧轴：每帧一个独立开关（默认关）。
 * 开启时，播放端读取当前关键帧驱动的摄像机位置（游动 / 追踪世界坐标），
 * 以及被追踪演员（伪装）的实时位置，自动把摄像机朝向平滑转向演员——
 * 摄像机位置不被修改（位置仍由关键帧曲线驱动），实现「不完全锁定的自动跟踪」：
 *
 * <ul>
 *   <li>演员移动而摄像机不动（或摄像机关键帧照常移动）：摄像机时刻转头盯住演员，
 *       完成跟踪录制（可能性一 / 可能性二均覆盖）；</li>
 *   <li>{@link #smooth}（0~1）控制朝向跟随强度：1 = 完全盯住演员；
 *       0 = 保持关键帧朝向（等同关闭）；默认 0.9，丝滑且不失跟随。</li>
 * </ul>
 *
 * 序列化：{@code on}（0 关 / 1 开）+ {@code smooth}（跟随强度）。
 *
 * <p>注意：本类与 {@link PhaseFrameDataKeyframeFactory} 随 {@code phase} 关键帧通道
 * 一起被 BBS 的 {@code KeyframeChannel} 序列化，工厂必须在启动时注册进全局
 * {@code KeyframeFactories.FACTORIES}（见 {@code RegisterKeyframeFactoriesEvent}），
 * 否则通道存档时写不出类型、读档时 keyframes 会被整段丢弃。</p>
 */
public class PhaseFrameData {
    /** 相位追踪开关：false = 关（默认）/ true = 开。 */
    public boolean enabled;
    /** 朝向跟随强度（0~1）：1 完全盯住演员；0 保持关键帧朝向。默认 0.9。 */
    public double smooth;

    public PhaseFrameData() {
        this(false, 0.9D);
    }

    public PhaseFrameData(boolean enabled, double smooth) {
        this.enabled = enabled;
        this.smooth = smooth;
    }

    /** 序列化为 MapType。 */
    public MapType toData() {
        MapType map = new MapType();
        map.putInt("on", this.enabled ? 1 : 0);
        map.putDouble("smooth", this.smooth);
        return map;
    }

    /** 从 MapType 反序列化（缺字段给默认值，兼容老数据）。 */
    public void fromData(MapType map) {
        this.enabled = map.getInt("on", 0) == 1;
        this.smooth = map.getDouble("smooth", 0.9D);
    }

    /** 深拷贝。 */
    public PhaseFrameData copy() {
        return new PhaseFrameData(this.enabled, this.smooth);
    }
}
