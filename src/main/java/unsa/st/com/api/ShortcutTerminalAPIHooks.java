package unsa.st.com.api;

import unsa.st.com.ShortcutTerminal;

/**
 * API 内部桥接：与模组主类日志的隔离层，避免 API 包反向依赖执行器。
 * 包级私有。
 */
final class ShortcutTerminalAPIHooks {
    private ShortcutTerminalAPIHooks() {}

    /** 记录一次被拒绝的注册尝试。 */
    static void logReject(String kind, String name) {
        ShortcutTerminal.LOGGER.warn(
                "[ShortcutTerminal API] Rejected {} registration '{}': name collides with built-in", kind, name);
    }

    /** 查询名称是否为内置命令（用于 hasCommand）。 */
    static boolean isBuiltInCommand(String name) {
        return ReservedNames.COMMANDS.contains(name);
    }

    /** 查询名称是否为内置 run 模块（用于 hasModule）。 */
    static boolean isBuiltInModule(String name) {
        return ReservedNames.MODULES.contains(name);
    }
}