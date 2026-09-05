package unsa.st.com.kernel;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;

/**
 * ps 顶层模拟：内核线程表 + 玩家进程化呈现。
 * 玩家被视为"前台进程"（PID = UUID hash），附 ping/坐标/血量/游戏模式。
 */
public final class ProcessTable {
    private ProcessTable() {}

    public static final class Row {
        public final String user;
        public final int pid;
        public final double cpu;
        public final String state;
        public final String command;
        Row(String user, int pid, double cpu, String state, String command) {
            this.user = user; this.pid = pid; this.cpu = cpu; this.state = state; this.command = command;
        }
    }

    /** 生成进程表快照。 */
    public static List<Row> snapshot() {
        List<Row> rows = new ArrayList<>();
        // 内核线程（真实 JVM 数据，按 CPU 时间排序，取前 12 个）
        List<TerminalKernel.ThreadRow> threads = TerminalKernel.threadTable();
        int shown = 0;
        for (TerminalKernel.ThreadRow t : threads) {
            if (shown >= 12) break;
            String shortName = t.name.length() > 24 ? t.name.substring(0, 24) : t.name;
            double cpu = t.cpuMs < 0 ? 0 : t.cpuMs / 1000.0;
            rows.add(new Row("root", (int) (t.id % 65536), cpu, t.state, "[" + shortName + "]"));
            shown++;
        }
        // 玩家进程（真实玩家数据）
        var server = TerminalKernel.server();
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                String mode = p.gameMode.getGameModeForPlayer() == GameType.CREATIVE ? "creative"
                        : p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR ? "spectator" : "survival";
                rows.add(new Row(
                        p.getGameProfile().getName(),
                        Math.abs(p.getUUID().hashCode() % 65536),
                        p.connection.latency() / 1000.0,
                        "S",
                        "mc-player " + mode + " @" + (int) p.getX() + "," + (int) p.getY() + "," + (int) p.getZ()
                ));
            }
        }
        return rows;
    }
}