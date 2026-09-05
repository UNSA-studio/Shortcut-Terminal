package unsa.st.com.api;

import java.util.Set;

/**
 * 内部保留名单：附属 API 不得注册的名称，防止覆盖内置命令/模块。
 * 包级私有，仅供 API 内部使用。
 */
final class ReservedNames {
    private ReservedNames() {}

    /** 不可被附属命令覆盖的内置命令名。 */
    static final Set<String> COMMANDS = Set.of(
            "help", "ls", "mkdir", "touch", "rm", "cat", "echo", "cd", "pwd",
            "cp", "mv", "head", "tail", "wc", "grep", "sort", "uniq",
            "whoami", "uname", "df", "free", "ps", "du",
            "ping", "curl", "wget", "clear", "date", "which", "chmod",
            "sh", "refresh", "pkg", "macro", "run", "user", "stop", "winget",
            "uptime", "who", "env", "hostname", "lscpu", "top", "addons",
            "init", "kill", "sleep"
    );

    /** 不可被附属 run 模块覆盖的内置模块名。 */
    static final Set<String> MODULES = Set.of(
            "spoof", "screenshot", "id"
    );
}