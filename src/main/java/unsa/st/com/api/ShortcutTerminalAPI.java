package unsa.st.com.api;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公共 API：允许其他模组开发者创建 Shortcut Terminal 的附属。
 * 附属可通过本 API 注册自定义命令，这些命令在客户端 GUI 终端与
 * 服务端 /ST run 中均可执行。
 *
 * <p>基本用法（附属模组 FMLCommonSetupEvent 中）：
 * <pre>{@code
 * ShortcutTerminalAPI.registerCommand("hello", (cmd, args) -> "Hello from addon! " + String.join(" ", args), "Says hello");
 * ShortcutTerminalAPI.registerModule("weather", (module, args) -> "Weather: " + String.join(" ", args));
 * }</pre></p>
 *
 * <p>所有注册方法均为线程安全，可在任意线程调用。
 * 命令名与模块名统一转小写存储，重名注册将覆盖旧处理器并记录日志。</p>
 */
public final class ShortcutTerminalAPI {
    private ShortcutTerminalAPI() {}

    /** 命令处理器：接收命令名与参数，返回输出文本；返回 null 表示该处理器放弃处理。 */
    @FunctionalInterface
    public interface TerminalCommandHandler {
        String execute(String command, String[] args);
    }

    /** 模块处理器：接收模块名与参数，返回输出文本；返回 null 表示该处理器放弃处理。 */
    @FunctionalInterface
    public interface TerminalModuleHandler {
        String execute(String module, String[] args);
    }

    private static final Map<String, TerminalCommandHandler> COMMANDS = new ConcurrentHashMap<>();
    private static final Map<String, TerminalModuleHandler> MODULES = new ConcurrentHashMap<>();
    private static final Map<String, String> COMMAND_INFO = new ConcurrentHashMap<>();
    private static final Map<String, String> MODULE_INFO = new ConcurrentHashMap<>();

    /**
     * 注册一个终端命令。
     * @param name 命令名（将被转为小写；禁止与内置命令重名，重名将被拒绝）
     * @param handler 命令处理器
     * @param info 命令简介（显示在 help 中，可为 null）
     * @return true=注册成功；false=被拒绝（名称非法/与内置冲突）
     */
    public static boolean registerCommand(String name, TerminalCommandHandler handler, String info) {
        if (name == null || name.isBlank() || handler == null) return false;
        String key = name.toLowerCase(Locale.ROOT);
        if (isReserved(key)) {
            ShortcutTerminalAPIHooks.logReject("command", name);
            return false;
        }
        TerminalCommandHandler old = COMMANDS.put(key, handler);
        if (info != null) COMMAND_INFO.put(key, info);
        if (old != null) {
            ShortcutTerminal.LOGGER.info("[ShortcutTerminal API] Command '{}' overridden by a later registration", key);
        }
        return true;
    }

    /** 便捷重载：无简介注册命令。 */
    public static boolean registerCommand(String name, TerminalCommandHandler handler) {
        return registerCommand(name, handler, null);
    }

    /**
     * 注册一个 run 模块（供 {@code run <module> [args...]} 调用）。
     * @return true=注册成功；false=被拒绝（名称非法/与内置冲突）
     */
    public static boolean registerModule(String name, TerminalModuleHandler handler, String info) {
        if (name == null || name.isBlank() || handler == null) return false;
        String key = name.toLowerCase(Locale.ROOT);
        if (isReservedModule(key)) {
            ShortcutTerminalAPIHooks.logReject("module", name);
            return false;
        }
        TerminalModuleHandler old = MODULES.put(key, handler);
        if (info != null) MODULE_INFO.put(key, info);
        if (old != null) {
            ShortcutTerminal.LOGGER.info("[ShortcutTerminal API] Module '{}' overridden by a later registration", key);
        }
        return true;
    }

    /** 便捷重载：无简介注册模块。 */
    public static boolean registerModule(String name, TerminalModuleHandler handler) {
        return registerModule(name, handler, null);
    }

    /**
     * 注销命令/模块（附属卸载或重载时调用）。
     * @return true=存在并已移除
     */
    public static boolean unregisterCommand(String name) {
        if (name == null) return false;
        String key = name.toLowerCase(Locale.ROOT);
        COMMAND_INFO.remove(key);
        return COMMANDS.remove(key) != null;
    }

    /** 注销 run 模块。 */
    public static boolean unregisterModule(String name) {
        if (name == null) return false;
        String key = name.toLowerCase(Locale.ROOT);
        MODULE_INFO.remove(key);
        return MODULES.remove(key) != null;
    }

    /** 查询命令是否已注册（含内置命令）。 */
    public static boolean hasCommand(String name) {
        if (name == null) return false;
        String key = name.toLowerCase(Locale.ROOT);
        return COMMANDS.containsKey(key) || ShortcutTerminalAPIHooks.isBuiltInCommand(key);
    }

    /** 查询 run 模块是否已注册（含内置模块）。 */
    public static boolean hasModule(String name) {
        if (name == null) return false;
        String key = name.toLowerCase(Locale.ROOT);
        return MODULES.containsKey(key) || ShortcutTerminalAPIHooks.isBuiltInModule(key);
    }

    /** 已注册的附属命令列表（仅附属命令，不含内置）。 */
    public static List<String> getRegisteredCommands() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(COMMANDS.keySet()));
    }

    /** 已注册的附属模块列表（仅附属模块，不含内置）。 */
    public static List<String> getRegisteredModules() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(MODULES.keySet()));
    }

    /** 取命令简介；不存在返回 null。 */
    public static String getCommandInfo(String name) {
        return name == null ? null : COMMAND_INFO.get(name.toLowerCase(Locale.ROOT));
    }

    /** 取模块简介；不存在返回 null。 */
    public static String getModuleInfo(String name) {
        return name == null ? null : MODULE_INFO.get(name.toLowerCase(Locale.ROOT));
    }

    /** 内部使用：调度附属命令，供执行器调用。执行器位于其他包，故 public。 */
    public static String dispatchCommand(String name, String[] args) {
        TerminalCommandHandler h = COMMANDS.get(name.toLowerCase(Locale.ROOT));
        return h == null ? null : h.execute(name, args);
    }

    /** 内部使用：调度附属 run 模块。执行器位于其他包，故 public。 */
    public static String dispatchModule(String name, String[] args) {
        TerminalModuleHandler h = MODULES.get(name.toLowerCase(Locale.ROOT));
        return h == null ? null : h.execute(name, args);
    }

    /** 内部使用：附属命令简介快照（供 help 展示）。执行器位于其他包，故 public。 */
    public static Map<String, String> commandInfoSnapshot() {
        return Collections.unmodifiableMap(new java.util.HashMap<>(COMMAND_INFO));
    }

    /** 内部使用：附属模块简介快照（供 help 展示）。执行器位于其他包，故 public。 */
    public static Map<String, String> moduleInfoSnapshot() {
        return Collections.unmodifiableMap(new java.util.HashMap<>(MODULE_INFO));
    }

    private static boolean isReserved(String cmd) {
        return ReservedNames.COMMANDS.contains(cmd);
    }

    private static boolean isReservedModule(String module) {
        return ReservedNames.MODULES.contains(module);
    }
}