package unsa.st.com.compute;

import java.util.Locale;
import java.util.Random;

/**
 * 光刻良品率表：目标等级 → 满级率。失败降级（降1级×70% / 降2级×30%，保底 L1）。
 * 一片逻辑晶圆在光刻机中切出一批 10 颗 CPU，逐颗独立掷骰。
 * 品质越高不良品越多——L9 目标下仅约 22% 满级。
 */
public final class YieldTable {
    private YieldTable() {}

    /** 一片晶圆产出的 CPU 数量。 */
    public static final int BATCH_SIZE = 10;

    /** 每个目标等级的满级率（百分比 0-100）。索引 = 等级。 */
    private static final int[] FULL_YIELD_PCT = {0, 100, 92, 86, 80, 72, 62, 50, 36, 22};

    /** 目标等级的满级率。 */
    public static int fullYieldPercent(int targetLevel) {
        if (targetLevel < 1 || targetLevel > 9) return 0;
        return FULL_YIELD_PCT[targetLevel];
    }

    /** 单颗 CPU 质检：返回实际等级（≤ 目标）。 */
    public static int rollDie(int targetLevel, Random random) {
        int level = targetLevel;
        while (level > 1) {
            if (random.nextInt(100) < fullYieldPercent(level)) return level;
            // 不合格：70% 降 1 级，30% 降 2 级
            level -= random.nextInt(10) < 7 ? 1 : 2;
        }
        return Math.max(1, level);
    }

    /** 一批的质检结果（长度 BATCH_SIZE 的等级数组）。 */
    public static int[] rollBatch(int targetLevel, Random random) {
        int[] result = new int[BATCH_SIZE];
        for (int i = 0; i < BATCH_SIZE; i++) result[i] = rollDie(targetLevel, random);
        return result;
    }

    /** 批次结果的摘要（供 GUI/终端显示）。 */
    public static String batchSummary(int targetLevel, int[] batch) {
        int[] counts = new int[10];
        for (int l : batch) counts[l]++;
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                "Lithography run (target L%d):", targetLevel));
        boolean first = true;
        for (int l = 9; l >= 1; l--) {
            if (counts[l] > 0) {
                sb.append(first ? "" : ",").append(" L").append(l).append("x").append(counts[l]);
                first = false;
            }
        }
        return sb.toString();
    }
}