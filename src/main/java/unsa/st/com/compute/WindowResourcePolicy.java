package unsa.st.com.compute;

/** Pure STOS accounting, not a measurement of host JVM memory. One buffer unit reserves 1 KiB. */
public final class WindowResourcePolicy {
    private WindowResourcePolicy() {}
    public static final int WINDOW_OVERHEAD_UNITS = 10;
    public static final String CPU_ERROR = "STOS:There are no available CPU resources to calculate a new page";
    public static final String RAM_ERROR = "STOS:Insufficient RAM capacity to create a new window";
    public static boolean underPressure(long totalUnits, long capacity) {
        return capacity > 0 && totalUnits * 100L > capacity * 80L;
    }
    public static String creationError(int count, int cpuThreads, long totalUnits, long capacity) {
        if (count >= Math.max(1, cpuThreads)) return CPU_ERROR;
        if (underPressure(totalUnits, capacity) || totalUnits + WINDOW_OVERHEAD_UNITS > capacity) return RAM_ERROR;
        return null;
    }
    public static int usagePercent(long units, long capacity) {
        return capacity > 0 ? (int) Math.min(Integer.MAX_VALUE, units * 100L / capacity) : 0;
    }
    public static int visibleTabCount(int width) { return Math.max(1, (width - 64) / 64); }
    public static int firstVisibleTab(int current, int first, int visible, int count) {
        int result = Math.max(0, Math.min(first, Math.max(0, count - visible)));
        if (current < result) result = current;
        if (current >= result + visible) result = current - visible + 1;
        return result;
    }
}