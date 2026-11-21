package perspective.better_camera_perspective.client.clips;

import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * anchor 轴统一采样器（V1.7.19-Alpha）。
 *
 * <p>背景：BBS 2.7 的 {@code KeyframeChannel#findRawSegment} 有一条特殊规则——
 * 当 tick 落在整数关键帧刻上时把段「前移」一格（取 [K_i, K_{i+1}] 而不是 [K_{i-1}, K_i]）；
 * 而 {@code AnchorKeyframeFactory#interpolate} 对「相邻两帧目标不同」是**离散硬切换**：
 * 整段直接返回右侧帧的值，把左侧帧塞进 {@code previous}。
 * 两者叠加后，{@code anchor.interpolate(tick)} 在关键帧刻会突然读到「下一帧」的目标状态，
 * 与 XYZ 帧值（本帧）错配，导致播放到每个关键帧刻都回跳一次。</p>
 *
 * <p>本采样器给出唯一口径，播放端与 UI 端共用：</p>
 * <ul>
 *   <li>{@link #cur}：本帧有效关键帧 = 最后一个 tick ≤ 当前 tick 的关键帧
 *       （tick 早于首帧时取首帧，等价 BBS 的退化段行为）；</li>
 *   <li>{@link #next}：下一个关键帧 = 第一个 tick &gt; 当前 tick 的关键帧；</li>
 *   <li>{@link #t}：段内进度 0~1（仅在 cur / next 都存在时有意义）。</li>
 * </ul>
 *
 * <p>路线判定一律以 {@code cur} 为准：cur 绑定目标 → 追踪；cur 为空值 → 游动；
 * 通道完全无关键帧时才回退剪辑属性的「目标」绑定。相邻帧目标不同时，过渡由 {@link #t} 表达。</p>
 */
@Environment(EnvType.CLIENT)
public final class AnchorSampler {
    /** 本帧有效关键帧值（可为 null = 通道为空）。 */
    public final Anchor cur;
    /** 下一个关键帧值（可为 null = 已是最后一帧）。 */
    public final Anchor next;
    /** 段内进度：0 = 恰在 cur 帧，1 = 恰在 next 帧。 */
    public final float t;

    private AnchorSampler(Anchor cur, Anchor next, float t) {
        this.cur = cur;
        this.next = next;
        this.t = t;
    }

    private static final AnchorSampler EMPTY = new AnchorSampler(null, null, 0.0F);

    /** 采样：返回该 tick 处的「本帧 / 下一帧 / 段内进度」。 */
    public static AnchorSampler sample(KeyframeChannel<Anchor> channel, float tick) {
        if (channel == null || channel.isEmpty()) {
            return EMPTY;
        }

        Anchor cur = null;
        Anchor next = null;
        float curTick = 0.0F;
        float nextTick = 0.0F;

        for (Keyframe keyframe : channel.getKeyframes()) {
            float kt = keyframe.getTick();
            Anchor value = (Anchor) keyframe.getValue();

            if (kt <= tick) {
                if (cur == null || kt >= curTick) {
                    cur = value;
                    curTick = kt;
                }
            } else if (next == null || kt < nextTick) {
                next = value;
                nextTick = kt;
            }
        }

        if (cur == null) {
            // tick 早于首帧：按首帧状态处理（无 next，进度 0）
            return new AnchorSampler(next, null, 0.0F);
        }
        if (next == null) {
            return new AnchorSampler(cur, null, 0.0F);
        }

        float span = nextTick - curTick;
        float progress = span <= 0.0F ? 0.0F : (tick - curTick) / span;
        if (progress < 0.0F) {
            progress = 0.0F;
        } else if (progress > 1.0F) {
            progress = 1.0F;
        }

        return new AnchorSampler(cur, next, progress);
    }

    /** 该值是否绑定了目标（非空值且 replay 非空）。 */
    public static boolean hasTarget(Anchor anchor) {
        return anchor != null && anchor.hasTarget();
    }

    /** 本帧是否处于追踪路线（cur 绑定目标）。 */
    public boolean routeIsTrack() {
        return hasTarget(this.cur);
    }

    /** 本帧到下一帧是否存在「目标不同」的过渡（含 游动⇄追踪、追踪→追踪不同目标）。 */
    public boolean isCrossing() {
        return this.cur != null && this.next != null && !this.cur.hasSameTarget(this.next);
    }

    /** 过渡是否为「两帧都绑定目标但目标不同」（追踪→追踪）。 */
    public boolean isCrossTarget() {
        return this.isCrossing() && hasTarget(this.cur) && hasTarget(this.next);
    }

    /** 过渡是否为「游动⇄追踪」（一端为空值）。 */
    public boolean isCrossRoute() {
        return this.isCrossing() && hasTarget(this.cur) != hasTarget(this.next);
    }
}
