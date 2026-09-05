# Shortcut Terminal Addon API

Version: 1.0 (NeoForge 1.21.1) · Package: `unsa.st.com.api`

为 Shortcut Terminal 开发附属模组，让你的模组获得终端命令能力。

---

## Quick Start

**1. 添加依赖**（`build.gradle`）:

```gradle
repositories {
    maven { url = "https://jitpack.io" }
}

dependencies {
    // 方式 A: JitPack
    implementation "com.github.UNSA-studio:Shortcut-Terminal:1.0.1"
    // 方式 B: 本地 jar（开发期推荐）
    implementation files("libs/shortcutterminal-1.0.1.jar")
}
```

**2. 声明依赖**（`src/main/resources/META-INF/neoforge.mods.toml`）:

```toml
[[dependencies.yourmodid]]
modId = "shortcutterminal"
type = "required"
versionRange = "[1.0.1,)"
ordering = "AFTER"
```

**3. 注册命令**（主类构造函数或 FMLCommonSetupEvent）:

```java
@Mod("yourmod")
public class YourMod {
    public YourMod(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // 注册终端命令（第三个参数是 help/addons 里显示的简介）
        ShortcutTerminalAPI.registerCommand("hello", (cmd, args) -> {
            if (args.length == 0) return "Hello, terminal!";
            return "Hello, " + String.join(" ", args) + "!";
        }, "Greets the caller");

        // 注册 run 模块（用法: run <module> [args...]）
        ShortcutTerminalAPI.registerModule("tpall", (module, args) -> {
            // 你的逻辑...
            return "Teleported everyone.";
        }, "Teleports all players");

        // 查询
        for (String cmd : ShortcutTerminalAPI.getRegisteredCommands()) {
            LOGGER.info("[YourMod] Addon command: {}", cmd);
        }
    }
}
```

**4. 测试**: 启动游戏，终端里执行 `hello` 和 `addons` 验证。

---

## API Reference

### 注册

| 方法 | 说明 |
|------|------|
| `registerCommand(name, handler, info)` | 注册终端命令。返回 false = 被拒绝（保留名/非法名/冲突） |
| `registerCommand(name, handler)` | 无简介版本 |
| `registerModule(name, handler, info)` | 注册 run 模块（`run <module> [args...]`） |
| `unregisterCommand(name)` / `unregisterModule(name)` | 注销（模组重载时用） |
| `hasCommand(name)` / `hasModule(name)` | 查询是否存在（含内置） |
| `getRegisteredCommands()` / `getRegisteredModules()` | 列出已注册（仅附属） |
| `getCommandInfo(name)` / `getModuleInfo(name)` | 取简介 |

### 处理器签名

```java
@FunctionalInterface
public interface TerminalCommandHandler {
    String execute(String command, String[] args);  // 返回 null = 放弃处理
}

@FunctionalInterface
public interface TerminalModuleHandler {
    String execute(String module, String[] args);
}
```

处理器的返回值会直接显示在终端。返回 `null` 表示该处理器放弃处理，
执行器会继续走 pkg 外部程序 → 未知命令的兜底链。

### 保留名

以下名称被内置命令占用，附属不能注册（会返回 false 并写日志）：

```
help ls mkdir touch rm cat echo cd pwd cp mv head tail wc grep sort uniq
whoami uname uptime who env hostname lscpu top df free ps du
ping curl wget clear date which chmod sh refresh pkg macro run user stop winget addons
```

run 模块保留名: `spoof` `screenshot` `id`

---

## 执行链

```
用户输入 → 内置命令？ ─ 是 → 执行
              │否
              ├→ 附属命令 (ShortcutTerminalAPI.dispatchCommand)
              │     处理器抛异常 → 打日志 + 返回错误信息（不崩终端）
              ├→ pkg 安装的外部程序 (PATH)
              └→ "Unknown command"
```

同一优先级下，后注册的附属覆盖先注册的（日志会提示 overridden）。

---

## 数据口径与限制

- `uptime`/`who`/`hostname`/`ps` 读取服务端真实状态
- `lscpu`/`top` 是 JVM 层面近似值（模组沙箱拿不到宿主机 CPU 负载）
- 处理器在调用线程执行：**服务端命令（/ST run）在服务器线程，GUI 终端命令在客户端线程**
  —— 需要操作世界的逻辑请自行 `server.execute(...)` 调度

---

## 给附属开发者的建议

1. **命令名加前缀**: 如 `yourmod_xxx`，避免与其他附属撞名
2. **info 写清楚用法**: 会显示在 `help` 和 `addons` 里
3. **不要抛异常**: 执行器会兜住，但日志会很吵
4. **兼容性**: 依赖 `versionRange = "[1.0.1,)"` 时注意 API 只增不改的原则
