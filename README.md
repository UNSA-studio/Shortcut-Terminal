# Shortcut Terminal

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.219-blue)](https://neoforged.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)](https://www.minecraft.net/)

**Bring a touch of Linux to your Minecraft world.**

Shortcut Terminal is a NeoForge mod that adds a set of Linux-like commands directly into the Minecraft chat. Execute familiar terminal operations without leaving the game, all within a secure, player-specific sandboxed filesystem.

Developed by **AI and UNSA-STUDIO**

---

## ✨ Features

- **Familiar Linux Commands**: Use commands like "ls", `mkdir,touch,rm,cat,echo,cd,pwd,clear`,and `whoami`.
- **Built-in Help**: Type `/ST Help` to see all available commands.
- **Per-Player Sandbox**: Each player gets a private folder named after their UUID inside `Terminal File/`. File operations are strictly contained to this folder.
- **No File Hopping**: `cd` commands that attempt to leave a player's sandbox or access another player's directory will be blocked with a clear "permission denied" message.
- **Admin User Command**: Administrators can bypass vanilla command restrictions using `/ST User <player> <action>`. For example:
  - `/ST User Steve switchingmode create` — Switches Steve to Creative mode.
  - `/ST User Steve switchingmode 1` — Also switches Steve to Creative mode (numeric aliases: `1`=Creative, `0`=Survival, `2`=Adventure, `3`=Spectator).
- **Immersive Terminal Mode**: Enter a dedicated terminal interface with `/ST open terminal page`. In this mode, you can type commands directly (no `/ST` prefix needed) and press Enter to execute. Type `exit` to return to normal chat.
- **Lightweight & Server-Side Friendly**: Files are stored in the Minecraft server/game directory, ensuring compatibility across clients.

---

## 📥 Installation

1. **Download** the latest `.jar` file from the [Releases](https://github.com/UNSA-STUDIO/ShortcutTerminal/releases) page.
2. **Place** the `.jar` file into your Minecraft instance's `mods` folder.
3. **Ensure** you have **NeoForge 21.1.219** (or compatible) installed for Minecraft 1.21.1.
4. **Launch** the game.

---

## 🛠️ Usage

### Basic Command Syntax

All terminal commands are executed through the chat window using the `/ST` prefix, unless you are in **Terminal Mode**.

| Command | Description |
| :--- | :--- |
| `/ST Help` | Displays the list of available commands. |
| `/ST ls` | Lists files and folders in the current directory. |
| `/ST mkdir <name>` | Creates a new directory. |
| `/ST touch <name>` | Creates a new empty file. |
| `/ST rm <name>` | Deletes a file or empty directory. |
| `/ST cat <name>` | Displays the contents of a file. |
| `/ST echo <text>` | Prints text to the chat (redirection `>` not fully implemented). |
| `/ST cd <path>` | Changes the current working directory. |
| `/ST pwd` | Prints the absolute path of the current directory. |
| `/ST clear` | Clears the chat history. |
| `/ST whoami` | Displays your current Minecraft username. |

### Terminal Mode

To avoid typing `/ST` for every command, enter the terminal interface:

```

/ST open terminal page

```

Once inside, you can type commands like `ls` or `mkdir docs` directly. The prompt will indicate you are in terminal mode. Type `exit` to return to standard chat.

### Admin Commands (Requires OP)

```

/ST User <player_name> switchingmode <mode>

```

**Examples:**
- `/ST User Notch switchingmode creative`
- `/ST User Notch switchingmode 0` (Survival)

---

## 📂 File Storage

All user data is stored locally in the game directory under:

```

./Terminal File/<player_uuid>/

```

Each player is restricted to their own UUID-named folder. Attempting to access a different UUID via `cd ../<other_uuid>` will result in:

> `You do not have permission to access this user's folder`

---

## 🔨 Building from Source

This project uses **Gradle** and targets **NeoForge 21.1.219**.

1. **Clone** the repository:
   ```bash
   git clone https://github.com/UNSA-STUDIO/ShortcutTerminal.git
   cd ShortcutTerminal
```

1. Build the mod using the Gradle wrapper:
   ```bash
   ./gradlew build
   ```
2. Find the compiled .jar file in:
   ```
   build/libs/st-<version>.jar
   ```

Automated Builds: This repository includes a GitHub Actions workflow (.github/workflows/build.yml). Every push to the main branch will automatically compile the mod and upload the artifact.

---

📄 License

This project is open-source and licensed under the MIT License. See the LICENSE file for details.

---

🤝 Contributing

Issues and pull requests are welcome! Feel free to fork the repository and submit improvements. Please ensure your code adheres to the existing style and includes appropriate documentation.

Contact: UNSA-studio@outlook.com

---

Feel like Minecraft is missing a command line? Try this out!
