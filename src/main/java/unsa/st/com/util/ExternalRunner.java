package unsa.st.com.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 执行已安装的外部程序（pkg 安装的 Linux 二进制等）。
 * 在支持执行的宿主平台（Linux / Windows / macOS 桌面）上真实运行并捕获输出；
 * 安卓沙箱下通常无执行权限，会在错误信息中体现。
 */
public final class ExternalRunner {
    private ExternalRunner() {}

    /** 运行外部程序；timeoutSec<=0 表示不限时；返回 "[exit code: N]\n输出..."。 */
    public static String run(Path program, List<String> args, int timeoutSec) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(program.toString());
            cmd.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try { proc.getOutputStream().close(); } catch (IOException ignored) {}

            final List<String> lines = new ArrayList<>();
            Thread reader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), Charset.defaultCharset()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        synchronized (lines) {
                            if (lines.size() < 500) lines.add(line);
                        }
                    }
                } catch (IOException ignored) {}
            }, "ShortcutTerminal-ExternalReader");
            reader.setDaemon(true);
            reader.start();

            boolean finished;
            if (timeoutSec <= 0) {
                proc.waitFor();
                finished = true;
            } else {
                finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS);
            }
            StringBuilder sb = new StringBuilder();
            if (!finished) {
                proc.destroyForcibly();
                proc.waitFor(5, TimeUnit.SECONDS);
                sb.append("Error: program timed out after ").append(timeoutSec).append("s and was killed.\n");
            }
            reader.join(2000);
            int code = -1;
            try { code = proc.exitValue(); } catch (IllegalThreadStateException ignored) {}
            sb.append("[exit code: ").append(code).append(']');
            synchronized (lines) {
                for (String l : lines) sb.append('\n').append(l);
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error: cannot execute '" + program.getFileName() + "': " + e.getMessage();
        }
    }
}