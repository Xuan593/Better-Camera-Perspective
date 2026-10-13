package perspective.better_camera_perspective.client.data;

import mchorse.bbs_mod.data.types.MapType;

/**
 * XYZ 轴「帧级小类存储」值（V1.5.4-Alpha 十版重构）。
 *
 * 每个 XYZ 关键帧独立携带以下数据，游动路线与追踪路线各存各的，
 * 不再共用一个 double 池、也不再读剪辑级共享模式，杜绝跨路线相互污染：
 *
 * <ul>
 *   <li>{@link #roamValue}：游动路线（anchor 关键帧值为 <无>）的世界坐标；
 *       游动的「相对 / 绝对」两值无锚点可换算，二者同值。</li>
 *   <li>{@link #trackRelative}：追踪路线（anchor 绑定模型）的相对锚点偏移。</li>
 *   <li>{@link #trackAbsolute}：追踪路线的绝对世界坐标。
 *       与 trackRelative 按编辑时刻锚点互转（相对 + 锚点 = 绝对）。</li>
 *   <li>{@link #roamMode} / {@link #trackMode}：该帧各自的小模式——
 *       0 = 相对（游动再叠加 offset 调试值；追踪按「锚点 + 偏移」合成）；
 *       1 = 绝对（游动 / 追踪均直接使用世界坐标，不叠加）。</li>
 * </ul>
 *
 * 序列化：5 个字段写 MapType（与 BBS Anchor 值同风格）。
 */
public class XyzFrameData {
    /** 游动路线世界坐标（游动相对 / 绝对同值）。 */
    public double roamValue;
    /** 追踪路线相对锚点偏移。 */
    public double trackRelative;
    /** 追踪路线绝对世界坐标。 */
    public double trackAbsolute;
    /** 该帧游动小模式：0 相对 / 1 绝对。 */
    public int roamMode;
    /** 该帧追踪小模式：0 相对 / 1 绝对。 */
    public int trackMode;

    public XyzFrameData() {
    }

    public XyzFrameData(double roamValue, double trackRelative, double trackAbsolute, int roamMode, int trackMode) {
        this.roamValue = roamValue;
        this.trackRelative = trackRelative;
        this.trackAbsolute = trackAbsolute;
        this.roamMode = roamMode;
        this.trackMode = trackMode;
    }

    /** 序列化为 MapType。 */
    public MapType toData() {
        MapType map = new MapType();
        map.putDouble("roam", this.roamValue);
        map.putDouble("track_rel", this.trackRelative);
        map.putDouble("track_abs", this.trackAbsolute);
        map.putInt("roam_mode", this.roamMode);
        map.putInt("track_mode", this.trackMode);
        return map;
    }

    /** 从 MapType 反序列化（缺字段给默认 0，兼容老数据）。 */
    public void fromData(MapType map) {
        this.roamValue = map.getDouble("roam", 0.0D);
        this.trackRelative = map.getDouble("track_rel", 0.0D);
        this.trackAbsolute = map.getDouble("track_abs", 0.0D);
        this.roamMode = map.getInt("roam_mode", 0);
        this.trackMode = map.getInt("track_mode", 0);
    }

    /** 深拷贝。 */
    public XyzFrameData copy() {
        return new XyzFrameData(this.roamValue, this.trackRelative, this.trackAbsolute, this.roamMode, this.trackMode);
    }
}
