package perspective.better_camera_perspective.data;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

/**
 * {@link PhaseFrameData} 的 BBS 关键帧数据工厂。
 *
 * 插值：{@code smooth} 走 IInterp 基础插值（0~1 之间随曲线过渡）；
 * {@code enabled} 为离散开关，段内按 t 取 before / after 端（开关帧是事件，不做渐变）。
 *
 * <p><b>必须在启动时注册进 BBS 全局表。</b>BBS 的 {@code KeyframeChannel} 在
 * 序列化时用 {@code CollectionUtils.getKey(KeyframeFactories.FACTORIES, factory)}
 * 反查本工厂的类型名写盘，读盘时又用该类型名 {@code KeyframeFactories.FACTORIES.get(type)}
 * 取回工厂——若未注册，存档写出的 type 为 null，读档时 keyframes 会被
 * {@code "has unknown value type"} 整段丢弃。注册入口：
 * {@code RegisterKeyframeFactoriesEvent}（BBSMod 初始化时 post）。</p>
 */
public class PhaseFrameDataKeyframeFactory implements IKeyframeFactory<PhaseFrameData> {
    public static final PhaseFrameDataKeyframeFactory INSTANCE = new PhaseFrameDataKeyframeFactory();

    /** 全局序列化类型名（写入影片存档；务必保持稳定，改名会导致旧存档读不回）。 */
    public static final String TYPE = "bcp_phase_frame";

    private PhaseFrameDataKeyframeFactory() {
    }

    @Override
    public BaseType toData(PhaseFrameData value) {
        return value.toData();
    }

    @Override
    public PhaseFrameData fromData(BaseType type) {
        PhaseFrameData data = new PhaseFrameData();
        if (type.isMap()) {
            data.fromData(type.asMap());
        }
        return data;
    }

    @Override
    public PhaseFrameData createEmpty() {
        return new PhaseFrameData();
    }

    @Override
    public PhaseFrameData copy(PhaseFrameData value) {
        return value.copy();
    }

    @Override
    public PhaseFrameData interpolate(
        Keyframe<PhaseFrameData> prev, Keyframe<PhaseFrameData> before, Keyframe<PhaseFrameData> after,
        Keyframe<PhaseFrameData> next, IInterp interp, float t
    ) {
        return this.interpolate(prev.getValue(), before.getValue(), after.getValue(), next.getValue(), interp, t);
    }

    @Override
    public PhaseFrameData interpolate(
        PhaseFrameData prev, PhaseFrameData before, PhaseFrameData after, PhaseFrameData next, IInterp interp, float t
    ) {
        // smooth 走曲线插值；enabled 离散：段内按 t 取端值
        double smooth = interp.interpolate(
            IInterp.context.set(prev.smooth, before.smooth, after.smooth, next.smooth, t)
        );
        boolean enabled = t < 0.5F ? before.enabled : after.enabled;
        return new PhaseFrameData(enabled, smooth);
    }
}
