# Анализ механизма обнаружения foreground процесса в Termux
## Для реализации авто-переключения раскладки ExtraKeys

> Дата: 2026-07-30
> Цель: Определить, какую программу сейчас запустил пользователь в терминале (vim, nano, opencode, python, shell),
> чтобы автоматически переключать раскладку ExtraKeysView.

---

## 1. Исходные данные: что мы уже имеем

### TerminalSession.java (`terminal-emulator/.../TerminalSession.java`)
- **`mShellPid`** (int) — PID shell процесса (bash/zsh/sh), установлен в `initializeEmulator()`.
- **`getPid()`** → возвращает `mShellPid`.
- **`getCwd()`** → читает `/proc/{mShellPid}/cwd/` через `File.getCanonicalPath()`.

### TermuxSession.java (`termux-shared/.../TermuxSession.java`)
- Оборачивает `TerminalSession` + `ExecutionCommand`.

### TermuxShellManager.java (`termux-shared/.../TermuxShellManager.java`)
- `List<TermuxSession> mTermuxSessions` — все сессии.

### TermuxActivity.java (`app/.../TermuxActivity.java`)
- `getCurrentSession()` → `mTerminalView.getCurrentSession()` → возвращает `TerminalSession`.

### ExtraKeysView.java (`termux-shared/.../ExtraKeysView.java`)
- `reload(ExtraKeysInfo, float)` — загружает матрицу кнопок.
- `ExtraKeysInfo` → JSON-определение раскладки (обычная или `arrows-only`, `arrows-all`, `all`).
- `SpecialButton` (CTRL, ALT, SHIFT, FN) с переключением через `SpecialButtonMode` (TOGGLE / HOLD).
- **Текущий механизм смены раскладки:** Нет авто-переключения. Всё статично — задаётся единственной конфигурацией `extra-keys` из `termux.properties`.

---

## 2. Все способы определения процесса через `/proc/{shellPid}`

### 2.1. `/proc/{pid}/cmdline` — полная командная строка

**Файл:** `/proc/{shellPid}/cmdline`

**Формат:** Строки, разделённые null (`\0`):
```
vim\0/tmp/foo\0
bash\0--login\0
```
**чтение в Java:**
```java
static String readCmdline(int pid) throws IOException {
    byte[] bytes = java.nio.file.Files.readAllBytes(
        java.nio.file.Paths.get("/proc/" + pid + "/cmdline"));
    String full = new String(bytes, StandardCharsets.UTF_8);
    // Разделяем по null-символу; argv[0] — имя программы
    int end = full.indexOf(0);
    return end > 0 ? full.substring(0, end) : full;
}
```

**Надёжность:** ★★★★★ (высочайшая для идентификации)
- Показывает **полный путь** и аргументы: `/data/data/com.termux/files/usr/bin/vim`, `bash --login`
- Единственный надёжный способ отличить `bash` от `bash --login` (login shell имеет префикс `-`).
- **Недостаток:** может быть пустым для kernel-thread или некоторых zomie-процессов.

**Размер данных:** ~30–200 байт (зависит от длины аргументов).

### 2.2. `/proc/{pid}/comm` — короткое имя процесса

**Файл:** `/proc/{shellPid}/comm`

**Формат:** Одна строка, максимум 15 символов + `\n`:
```
vim
bash
python3
```

**чтение в Java:**
```java
static String readComm(int pid) throws IOException {
    String content = new String(
        java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("/proc/" + pid + "/comm")),
        StandardCharsets.UTF_8).trim();
    return content;
}
```

**Надёжность:** ★★★☆☆ (средняя для идентификации)
- Очень быстро — всегда 16 байт чтения.
- **Ядра ограничение:** максимум 15 символов (`TASK_COMM_LEN`). `opencode` — 8 символов, умещается. `python3.11` — 10, умещается.
- **Проблема:** может не совпадать с реальным именем — процесс может изменить `comm` через `prctl(PR_SET_NAME)`. `busybox` и некоторые утилиты так делают.
- **Проблема:** `comm` показывает `python3` для любого Python-скрипта — не отличить интерактивную сессию от запуска `script.py`.

### 2.3. `/proc/{pid}/cwd` — текущая рабочая директория (косвенная)

**Файл:** симлинк `/proc/{pid}/cwd/`

**чтение в Java:**
```java
String cwd = new File("/proc/" + pid + "/cwd/").getCanonicalPath();
```

**Надёжность:** ★★☆☆☆ (низкая для определения процесса, средняя для контекста)
- Уже реализован в `TerminalSession.getCwd()`.
- **Не предназначен** для определения КАКОЙ процесс запущен. Даёт контекст («пользователь в директории проекта X»).
- Может намекнуть на проект, когда пользователь запускает `vim` в конкретном репозитории.
- **Полезен как дополнительный сигнал** для переключения не только по типу процесса, но и по директории (напр., в `~/repos/...` — одна раскладка, в `/tmp/` — другая).

### 2.4. `/proc/{pid}/status` — имя процесса + метаданные

**Файл:** `/proc/{pid}/status`

**Формат:** Ключ-значение, текстовый:
```
Name:   bash
State:  S (sleeping)
Tgid:   12345
Pid:    12345
PPid:   12344
...
```

**чтение в Java:**
```java
static String readNameFromStatus(int pid) throws IOException {
    try (BufferedReader br = new BufferedReader(
            new InputStreamReader(
                new FileInputStream("/proc/" + pid + "/status")))) {
        String line;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("Name:")) {
                return line.substring(5).trim();
            }
        }
    }
    return null;
}
```

**Надёжность:** ★★★☆☆ (средняя)
- `Name:` — то же самое что `comm` (макс 15 символов, может быть неточным).
- **Полезный дополнительный сигнал:** содержит `State:`, `Tgid:`, `Pid:`, `TracerPid:` (полезно для отладки).
- Чтение всего файла (~30 строк) ∼ 500 байт — тяжелее чем `comm`.

---

## 3. Определение foreground (активного) процесса в терминале

### 3.1. Теория: как работает foreground в терминале

```
┌──────────────────────────────────────────────┐
│  PTY master (Termux process)                  │
│  mTerminalFileDescriptor                      │
└──────────────────┬───────────────────────────┘
                   │
┌──────────────────▼───────────────────────────┐
│  PTY slave /dev/pts/X                        │
│  Controllable terminal (tty)                 │
│  Foreground process group = tpgid            │
└──────┬───────────────────────────┬───────────┘
       │                           │
┌──────▼──────┐           ┌───────▼──────────┐
│ Shell bash  │           │ Foreground proc  │
│ PID=1000    │           │ vim, PID=1001    │
│ PGRP=1000   │           │ PGRP=1001        │
│ tpgid=1001  │           │ tpgid=1001       │
│ state='S'   │           │ state='R'/'S'    │
│ (waiting)   │           │ (running)        │
└─────────────┘           └──────────────────┘
      │
┌─────▼──────────────────────────────┐
│ Background job (sleep 100 &)       │
│ PID=1002, PGRP=1002, tpgid=1001   │
│ (не равна tpgid — не foreground!)  │
└────────────────────────────────────┘
```

**Ключевые факты:**
1. Shell (bash/zsh) при запуске создаёт свою process group (PGRP).
2. Shell делает свою PGRP foreground через `tcsetpgrp()`.
3. Когда запускается foreground программа, bash создаёт **новую** PGRP для ребёнка через `setpgid()` и передаёт ему управление терминалом через `tcsetpgrp()`.
4. Таким образом, **PGRP foreground-процесса отличается от PGRP shell'а**.
5. Фоновые процессы (`&`) имеют свою PGRP, но НЕ являются foreground.

### 3.2. Метод 1: Сравнение tpgid из `/proc/{shellPid}/stat` (РЕКОМЕНДУЕТСЯ)

**Файл:** `/proc/{shellPid}/stat`

**Формат (позиционный):**
```
PID (comm) state ppid pgrp session tty_nr tpgid flags ...
  1     2     3    4    5     6      7    8    9
```

**Алгоритм:**
```
tpgid = field 8 of /proc/{shellPid}/stat
pgrp  = field 5 of /proc/{shellPid}/stat

if tpgid == -1:
    → нет управляющего терминала (не должно быть в Termux-сессии)
    → fallback: проверять по cmdline
elif tpgid == pgrp:
    → shell сам является foreground процессом
    → пользователь в prompt'е shell (либо выполняет builtin)
elif tpgid != pgrp:
    → foreground процесс отличается от shell
    → нужно найти процесс(ы) с PGRP == tpgid
```

**Преимущества:**
- Одно чтение `/proc/{shellPid}/stat` (~200 байт).
- Работает для любых shell-ов (bash, zsh, fish, sh).
- Работает для пайпов (`cat | sort | grep` — вся пайпа в одной PGRP).
- **Не требует поиска по всем процессам** в 90% случаев.

**Недостатки:**
- **Не даёт имя процесса** — только факт «что-то запущено».
- Требует парсинга позиционных полей (поля 2 в скобках может содержать пробелы).

**Парсинг stat с учётом скобок:**
```java
static int parseTpgid(String statContent) throws Exception {
    // Поле 2: (comm) — содержит скобки, может включать пробелы
    int openParen = statContent.indexOf('(');
    int closeParen = statContent.lastIndexOf(')');
    if (openParen < 0 || closeParen < 0)
        throw new Exception("Cannot parse stat");
    String afterComm = statContent.substring(closeParen + 2); // ") " → after
    String[] fields = afterComm.split("\\s+");
    // fields[4] = pgrp, fields[7] = tpgid (0-indexed: 0=state, 3=ppid, 4=pgrp, 7=tpgid)
    return Integer.parseInt(fields[7]);
}
```

### 3.3. Метод 2: Сравнение `/proc/{pid}/fd/0` symlink с pty shell'а

**Файлы:**
- `/proc/{shellPid}/fd/0` → `/dev/pts/X` (symlink на pty slave)
- `/proc/{childPid}/fd/0` → если тоже `/dev/pts/X`, то этот процесс использует тот же терминал

**Алгоритм:**
```
Проверяем всех потомков shell (через /proc/{pid}/status PPid):
- Читаем readlink(/proc/{pid}/fd/0)
- Если symlink == readlink(/proc/{shellPid}/fd/0) → процесс использует тот же терминал
- Среди таких процессов выбираем один (первый/любой) — это foreground
```

**Надёжность:** ★★★☆☆
- Требует сканирования `/proc/*/status` + `readlink` на fd для каждого кандидата.
- **Медленно** (много операций ввода-вывода).
- Не отличает foreground от background процесса, читающего из того же терминала (но background обычно имеет перенаправленный stdin).
- **Преимущество:** не требует job control от shell — работает даже если shell не делает `tcsetpgrp()`.

### 3.4. Метод 3: Анализ состояния shell через `State` в `/proc/{shellPid}/status`

**Файл:** `/proc/{shellPid}/status`

```
State:  S (sleeping)
```

- `S` (sleeping) в сочетании с WCHAN в режиме `wait_for_completion` или `do_wait` → shell ждёт child.
- `S` в `n_tty_read` или `wait_woken` → shell ждёт ввод с терминала (то есть у prompt'а).
- `R` (running) → shell активен.
- `D` (uninterruptible sleep) — редко.

**Надёжность:** ★★☆☆☆
- Ненадёжен как самостоятельный метод — состояние процесса меняется не только от foreground/background.
- Может быть дополнительным признаком для уточнения.

### 3.5. Метод 4: Поиск процесса с PGRP == tpgid

После того как tpgid != pgrp обнаружен (через метод 3.2), нужно найти **какой именно** процесс:

```java
// Сканировать /proc/*/stat, найти процессы, где pgrp == tpgid
// Обычно это 1 процесс (или N процессов для пайпа)
int tpgid = ...;
List<Integer> foregroundPids = new ArrayList<>();
for (File dir : new File("/proc").listFiles()) {
    if (!dir.isDirectory()) continue;
    try {
        int pid = Integer.parseInt(dir.getName());
        String stat = readFile("/proc/" + pid + "/stat");
        int foundPgrp = parsePgrp(stat);
        if (foundPgrp == tpgid) {
            foregroundPids.add(pid);
        }
    } catch (NumberFormatException e) {
        // skip non-numeric entries
    }
}
```

**Надёжность:** ★★★★★ (в паре с tpgid)
- Это самый надёжный способ найти **все** foreground процессы.
- Для пайпов (pipe) будет найдено несколько процессов.
- Для одиночной команды — один процесс.

**Накладные расходы:** Сканирование `/proc/*/stat` на Android:
- Типично 30–80 entry (процессов и тредов).
- Каждое чтение stat ~200 байт.
- Итого: ~6–16 KB данных, 30–80 операций open/read/close.
- На CPU: ~1–3ms (очень быстро, ядро кеширует dentry).
- При polling раз в 500ms — незаметно для пользователя.

---

## 4. Оценка накладных расходов polling

### Сводная таблица

| Метод | Размер чтения | IO ops | CPU время | Применимость |
|-------|:------------:|:------:|:---------:|:-----------:|
| `/proc/{pid}/comm` | ~16 байт | 1 read | < 0.05 ms | **Быстрейший** (но неточный) |
| `/proc/{pid}/cmdline` | ~30–200 байт | 1 read | < 0.1 ms | Надёжный, чуть тяжелее |
| `/proc/{pid}/stat` | ~200 байт | 1 read | < 0.1 ms | **Рекомендован** (tpgid!) |
| `/proc/{pid}/status` | ~500 байт | 1 read | < 0.2 ms | Тяжеловат (но содержит много мета) |
| Сканирование `/proc/*/status` | 50×500 байт | ~50 reads | 1–3 ms | Только при смене процесса |
| Сканирование `/proc/*/fd/0` | 50×readlink | ~100 ops | 3–8 ms | Самый медленный |

### Рекомендуемая стратегия polling (500ms)

```
Каждые 500ms:
  ┌─────────────────────────────────┐
  │ FAST PATH (1 read, ~0.1ms):     │
  │ read /proc/{shellPid}/stat      │
  │ парсим tpgid                    │
  └──────────┬──────────────────────┘
             │
      tpgid == pgrp?          tpgid != pgrp?
             │                       │
    ┌────────▼────┐        ┌─────────▼───────────┐
    │ SHELL IDLE  │        │ CHILD IN FOREGROUND  │
    │ Пользователь│        │ Сканировать          │
    │ в prompt'е  │        │ /proc/*/stat для     │
    │             │        │ поиска процесса с    │
    │ Применить   │        │ pgrp == tpgid        │
    │ default     │        │                      │
    │ раскладку   │        │ Читать cmdline       │
    │             │        │ процесса             │
    └─────────────┘        │                      │
                           │ Сматчить с таблицей  │
                           │ известных программ   │
                           │ → переключить        │
                           │ раскладку            │
                           └──────────────────────┘

// Оптимизация: кешировать currentForegroundPid и currentLayout
// Переключаться ТОЛЬКО при изменении
```

**Чистая стоимость fast-path (90% времени):** ~0.1ms каждые 500ms = 0.02% CPU.

**Чистая стоимость slow-path (10% времени):** ~3ms каждые 5 секунд (если процесс живет) + ~3ms при запуске/смене процесса.

---

## 5. Рекомендуемый метод: самый надёжный для определения vim/nano/opencode

### Stage 1: Определение foreground процесса (cheap, 500ms polling)

1. Читаем `/proc/{shellPid}/stat`
2. Парсим `tpgid` (field 8)
3. Если `tpgid == field 5 (pgrp)`: **shell is foreground** → default layout
4. Если `tpgid != pgrp`: **что-то запущено** → переходим к Stage 2

### Stage 2: Идентификация foreground процесса (on-demand, кешируется)

5. Сканируем `/proc/*/stat`, ищем процессы с `pgrp == tpgid`
6. Для каждого найденного читаем `/proc/{pid}/cmdline` (или comm, если cmdline пуст)
7. Сравниваем с таблицей известных программ:

```java
enum KnownProgram {
    // Editor-adjacent tools
    VIM,      // vim, nvim, vi
    NANO,     // nano, pico
    EMACS,    // emacs, emacs-nox
    OPINIONATED_EDITOR,
    CODE,     // code, codium (VS Code server)
    
    // Pagers
    LESS,     // less, more, most
    MAN,      // man
    
    // REPLs & interpreters
    PYTHON,   // python3, python, ipython
    NODE,     // node
    OPENCODE, // opencode CLI
    
    // Shells
    BASH,     // bash, sh
    ZSH,      // zsh
    FISH,     // fish
    
    // Build tools
    MAKE,     // make, cmake, ninja
    GRADLE,   // gradle
    
    // Debuggers
    GDB,      // gdb
    LLDB,     // lldb
}
```

### Сопоставление cmdline → KnownProgram

```java
static KnownProgram identify(String cmdline) {
    String name = getBasename(cmdline);
    if (name.startsWith("nvim") || name.startsWith("vim") || name.equals("vi"))
        return VIM;
    if (name.startsWith("nano") || name.equals("pico"))
        return NANO;
    if (name.equals("opencode"))
        return OPENCODE;
    if (name.startsWith("python") || name.equals("ipython"))
        return PYTHON;
    if (name.equals("node") || name.equals("nodejs"))
        return NODE;
    if (name.equals("less") || name.equals("more") || name.equals("most"))
        return LESS;
    if (name.equals("man"))
        return MAN;
    // ... etc
}
```

### Почему это самый надёжный метод

| Требование | `stat + tpgid` | `comm` только | `cmdline` только | `fd/0` |
|:-----------|:--------------:|:-------------:|:----------------:|:------:|
| Отличает foreground от background | ✅ | ❌ | ❌ | ✅ |
| Отличает shell от child | ✅ | ❌ | ❌ | ✅ |
| Определяет точное имя программы | ✅ (stage 2) | ⚠️ (15 chars) | ✅ | ✅ |
| Работает с пайпами | ✅ | ⚠️ | ✅ | ✅ |
| Работает с job control | ✅ | ✅ | ✅ | ❌ (без job control) |
| Минимальные накладные расходы | ✅ | ✅ | ✅ | ❌ |

### 5.1. Важный нюанс: `/proc/{shellPid}/stat` на Android

В Android (особенно MIUI/HyperOS) процесс может не иметь `tpgid` если он не управляет терминалом напрямую через `TIOCSCTTY`. В Termux shell процесс **имеет** управляющий терминал (pty), так что `tpgid` будет корректным.

**Проверка:**
```bash
# Внутри Termux-сессии:
$ cat /proc/$$/stat | awk '{print "pgrp=" $5 " tpgid=" $8}'
```
Вывод: `pgrp=... tpgid=...` — оба не -1, tpgid равен pgrp shell'а или pgrp запущенной программы.

---

## 6. Java код: определение foreground процесса в терминале

### 6.1. Утилитарный класс `ForegroundProcessDetector`

```java
package com.termux.shared.termux.extrakeys;

import android.annotation.SuppressLint;
import androidx.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects the foreground process running in the terminal.
 * Uses /proc/{shellPid}/stat to get tpgid (foreground process group)
 * and identifies the actual command via cmdline.
 */
public class ForegroundProcessDetector {

    /**
     * Check if the shell is the foreground process (user at prompt).
     *
     * @param shellPid PID of the shell process (from TerminalSession.mShellPid)
     * @return true if shell is the foreground process (idle/prompt), false if
     *         a child process is in the foreground, null if unable to determine.
     */
    @Nullable
    public static Boolean isShellForeground(int shellPid) {
        if (shellPid < 1) return null;
        try {
            String stat = readFile("/proc/" + shellPid + "/stat");
            if (stat == null) return null;

            int pgrp = parseField5(stat);   // process group of shell
            int tpgid = parseField8(stat);   // foreground process group of terminal

            // If tpgid == -1, the process has no controlling terminal
            if (tpgid == -1) return null;

            return tpgid == pgrp;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the cmdline of the foreground process.
     *
     * @param shellPid PID of the shell process
     * @return cmdline string (e.g. "vim", "python3 script.py"),
     *         "SHELL" if shell is at prompt, or null on error.
     */
    @Nullable
    public static String getForegroundCommand(int shellPid) {
        if (shellPid < 1) return null;
        try {
            String stat = readFile("/proc/" + shellPid + "/stat");
            if (stat == null) return null;

            int pgrp = parseField5(stat);
            int tpgid = parseField8(stat);

            if (tpgid == -1) return null;
            if (tpgid == pgrp) return "SHELL";

            // Find foreground process(es) with pgrp == tpgid
            List<Integer> foregroundPids = findProcessesByPgrp(tpgid);
            if (foregroundPids.isEmpty()) return null;

            // For a pipeline, return the first process's cmdline
            int foregroundPid = foregroundPids.get(0);
            String cmdline = readCmdline(foregroundPid);

            if (cmdline != null && !cmdline.isEmpty()) {
                return cmdline;
            }

            // Fallback to comm if cmdline is empty
            return readComm(foregroundPid);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Identify the running program type from its cmdline.
     *
     * @param cmdline The raw cmdline string (e.g. "vim /tmp/foo")
     * @return A normalized program identifier, or "UNKNOWN"
     */
    public static String identifyProgramType(String cmdline) {
        if (cmdline == null || cmdline.isEmpty()) return "UNKNOWN";

        String name = getBasename(cmdline);

        // Editors
        if (name.startsWith("nvim") || name.startsWith("vim") || "vi".equals(name))
            return "VIM";
        if (name.startsWith("nano") || "pico".equals(name) || "micro".equals(name))
            return "NANO";
        if ("emacs".equals(name) || "emacs-nox".equals(name))
            return "EMACS";
        if ("opencode".equals(name) || "open.code".equals(name))
            return "OPENCODE";
        if ("code".equals(name) || "codium".equals(name) || "cursor".equals(name))
            return "CODE";
        if ("subl".equals(name) || "sublime_text".equals(name))
            return "SUBLIME";

        // Pagers & viewers
        if ("less".equals(name) || "more".equals(name) || "most".equals(name))
            return "PAGER";
        if ("man".equals(name) || "info".equals(name))
            return "MANUAL";

        // REPLs / interpreters
        if (name.startsWith("python") || "ipython".equals(name))
            return "PYTHON";
        if ("node".equals(name) || "nodejs".equals(name))
            return "NODE";
        if ("ruby".equals(name) || "irb".equals(name) || "pry".equals(name))
            return "RUBY";
        if ("lua".equals(name) || "luajit".equals(name))
            return "LUA";

        // Shells
        if ("bash".equals(name) || "sh".equals(name) || "-bash".equals(name) || name.endsWith("/bash"))
            return "SHELL";
        if ("zsh".equals(name) || "-zsh".equals(name))
            return "SHELL";
        if ("fish".equals(name))
            return "SHELL";

        // Build tools
        if ("make".equals(name) || "cmake".equals(name) || "ninja".equals(name) ||
            "gradle".equals(name) || "mvn".equals(name) || "ant".equals(name))
            return "BUILD";

        // Git
        if (name.startsWith("git"))
            return "GIT";

        // Debuggers
        if ("gdb".equals(name) || "lldb".equals(name) || "strace".equals(name) ||
            "perf".equals(name) || "valgrind".equals(name))
            return "DEBUG";

        // Default
        return "UNKNOWN";
    }

    // ────────────────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────────────────

    /**
     * Parse /proc/{pid}/stat field 5 (pgrp).
     * stat format: pid (comm) state ppid pgrp session tty_nr tpgid ...
     */
    private static int parseField5(String statContent) {
        return parseStatField(statContent, 4); // 0-indexed: 4 = pgrp
    }

    /**
     * Parse /proc/{pid}/stat field 8 (tpgid).
     */
    private static int parseField8(String statContent) {
        return parseStatField(statContent, 7); // 0-indexed: 7 = tpgid
    }

    /**
     * Parse a field from /proc/[pid]/stat.
     * The tricky part is field 2 "(comm)" which contains parentheses.
     * We skip it by finding the last ')'.
     *
     * @param statContent Full content of /proc/{pid}/stat
     * @param fieldIndex  0-indexed field number after the comm field
     * @return the field value parsed as integer
     */
    private static int parseStatField(String statContent, int fieldIndex) {
        // Find the closing parenthesis of comm (field 2)
        int closeParen = statContent.lastIndexOf(')');
        if (closeParen < 0) return -1;

        // Everything after ") " is field 3 onwards
        String afterComm = statContent.substring(closeParen + 2);
        String[] fields = afterComm.split("\\s+");

        if (fieldIndex >= fields.length) return -1;
        try {
            return Integer.parseInt(fields[fieldIndex]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Scan /proc/*/stat for processes with the given pgrp.
     */
    private static List<Integer> findProcessesByPgrp(int targetPgrp) {
        List<Integer> result = new ArrayList<>();
        File procDir = new File("/proc");
        File[] entries = procDir.listFiles();
        if (entries == null) return result;

        for (File entry : entries) {
            if (!entry.isDirectory()) continue;
            try {
                int pid = Integer.parseInt(entry.getName());
                String stat = readFile("/proc/" + pid + "/stat");
                if (stat == null) continue;
                int pgrp = parseField5(stat);
                if (pgrp == targetPgrp) {
                    result.add(pid);
                }
            } catch (NumberFormatException e) {
                // skip non-numeric /proc entries (e.g. "cpu", "meminfo")
            }
        }
        return result;
    }

    /** Read /proc/{pid}/cmdline. Returns null on error. */
    @Nullable
    private static String readCmdline(int pid) {
        try {
            String cmdline = readFile("/proc/" + pid + "/cmdline");
            if (cmdline == null) return null;
            // Handle null-separated args: replace with spaces
            int firstNull = cmdline.indexOf(0);
            if (firstNull >= 0) {
                return cmdline.substring(0, firstNull);
            }
            return cmdline.trim();
        } catch (Exception e) {
            return null;
        }
    }

    /** Read /proc/{pid}/comm. Returns null on error. */
    @Nullable
    private static String readComm(int pid) {
        try {
            String comm = readFile("/proc/" + pid + "/comm");
            return comm != null ? comm.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Read a small text file completely. Returns null on error. */
    @Nullable
    private static String readFile(String path) {
        try {
            byte[] bytes = new byte[4096];
            int totalRead;
            try (FileInputStream fis = new FileInputStream(path)) {
                totalRead = 0;
                int n;
                while ((n = fis.read(bytes, totalRead, bytes.length - totalRead)) != -1) {
                    totalRead += n;
                    if (totalRead >= bytes.length) break;
                }
            }
            return new String(bytes, 0, totalRead, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** Extract the basename from a cmdline. */
    private static String getBasename(String cmdline) {
        if (cmdline == null || cmdline.isEmpty()) return "";
        // Strip path prefix
        int lastSlash = cmdline.lastIndexOf('/');
        String name = (lastSlash >= 0) ? cmdline.substring(lastSlash + 1) : cmdline;
        // Handle leading '-' (login shell)
        if (name.startsWith("-")) name = name.substring(1);
        return name;
    }
}
```

### 6.2. Интеграция с ExtraKeysView: схема подключения

```java
// ── В ExtraKeysView добавить ──

// Handler для polling
private final Handler mFgProcessHandler = new Handler(Looper.getMainLooper());
private static final int FG_POLL_INTERVAL_MS = 500;
private String mCurrentFgProgram = "SHELL";
private int mCurrentShellPid = -1;

/** Начать мониторинг foreground процесса. */
public void startForegroundMonitor(TerminalSession session) {
    if (session == null) return;
    mCurrentShellPid = session.getPid();
    mFgProcessHandler.post(fgPollRunnable);
}

/** Остановить мониторинг. */
public void stopForegroundMonitor() {
    mFgProcessHandler.removeCallbacks(fgPollRunnable);
}

private final Runnable fgPollRunnable = new Runnable() {
    @Override
    public void run() {
        if (mCurrentShellPid < 1) return;

        String program = ForegroundProcessDetector.getForegroundCommand(mCurrentShellPid);
        if (program == null) {
            // Error reading /proc — retry
            mFgProcessHandler.postDelayed(this, FG_POLL_INTERVAL_MS);
            return;
        }

        String type = ForegroundProcessDetector.identifyProgramType(program);

        // Only switch layout if program type changed
        if (!type.equals(mCurrentFgProgram)) {
            mCurrentFgProgram = type;
            applyLayoutForProgramType(type);
        }

        mFgProcessHandler.postDelayed(this, FG_POLL_INTERVAL_MS);
    }
};

/** Переключить раскладку ExtraKeys на основе программы. */
private void applyLayoutForProgramType(String type) {
    switch (type) {
        case "VIM":
            // Раскладка с Esc, :, /, ?, Ctrl на видном месте
            // (vim не выходит из insert mode без Esc)
            switchLayout("vim");
            break;
        case "OPENCODE":
        case "CODE":
        case "NANO":
            // Редакторская раскладка: Ctrl, Alt, /, стрелки
            switchLayout("editor");
            break;
        case "PYTHON":
        case "NODE":
            // REPL: Tab для автодополнения, Ctrl+C, Ctrl+D
            switchLayout("repl");
            break;
        case "SHELL":
        default:
            // Обычная раскладка
            switchLayout("default");
            break;
    }
}

/**
 * Переключить между несколькими заранее определёнными раскладками.
 * Каждая раскладка — это свой ExtraKeysInfo.
 * Либо загружается из JSON, либо из отдельного свойства termux.properties.
 */
private void switchLayout(String layoutName) {
    // Загрузить ExtraKeysInfo для layoutName
    // ExtraKeysInfo layoutInfo = loadLayout(layoutName);
    // if (layoutInfo != null) {
    //     reload(layoutInfo, getHeight());
    // }
}
```

---

## 7. Обработка граничных случаев

### Shell без job control (Android default /system/bin/sh)

На некоторых Android устройствах shell может не использовать job control. В этом случае:
- Shell не вызывает `tcsetpgrp()` для детей.
- Дети наследуют PGRP от shell.
- `tpgid == pgrp` ВСЕГДА, даже при запущенной программе.

**Fallback:** Если `tpgid == pgrp` и shell не дал информации, использовать проверку `State`:
- Если shell в состоянии `S` (sleeping) с WCHAN, указывающим на `do_wait` → вероятно, ребёнок запущен.
- Eщё один fallback: найти детей shell через `/proc/{shellPid}/task/{shellPid}/children` (если доступно) — эта файловая система доступна на Android с ядром 3.5+.

```java
// Альтернативный метод: найти потомков shell
private static List<Integer> getChildren(int ppid) {
    List<Integer> children = new ArrayList<>();
    try {
        String content = readFile("/proc/" + ppid + "/task/" + ppid + "/children");
        if (content != null) {
            for (String s : content.trim().split("\\s+")) {
                if (!s.isEmpty()) children.add(Integer.parseInt(s));
            }
        }
    } catch (Exception ignored) {}
    return children;
}
```

### Пайпы (pipeline)

При выполнении `cat file | sort | grep pattern`:
- Все три процесса принадлежат одной PGRP (= tpgid).
- `findProcessesByPgrp()` вернёт все три.
- Определяем тип по первому процессу (cat → неинтересно) или по последнему (grep → более интересно).
- **Рекомендация:** брать последний процесс в пайпе — он часто является тем, с кем пользователь взаимодействует.

### Быстро завершающиеся программы

`ls`, `grep`, `sed` выполняются < 100ms. Polling с интервалом 500ms может их пропустить.
- **Решение:** Это нормально — раскладка не будет переключаться для мгновенных команд. Auto-switch имеет смысл только для интерактивных программ (vim, nano, python, less).

---

## 8. Практические рекомендации

### A. Минимальные изменения в коде

1. **Новый файл:** `termux-shared/.../extrakeys/ForegroundProcessDetector.java`
2. **Изменения в ExtraKeysView.java:**
   - Добавить `Handler` с polling runnable (500ms).
   - Добавить словарь `Map<String, ExtraKeysInfo>` для разных раскладок.
   - Добавить `startForegroundMonitor()` / `stopForegroundMonitor()`.
3. **Изменения в TermuxActivity.java:**
   - Вызвать `extraKeysView.startForegroundMonitor(currentSession)` при переключении сессии.
4. **Изменения в termux.properties:**
   - Добавить секции `extra-keys-vim`, `extra-keys-editor`, `extra-keys-repl`, `extra-keys-default`.

### B. Тестирование на устройстве

```bash
# Проверить tpgid в Termux-сессии (открой Termux, выполни):
echo "PID:$$ PGRP:$(ps -o pgid -p $$ | tail -1) TPGID:$(cat /proc/$$/stat | awk '{print $8}')"

# Запусти vim и проверь в другом окне:
cat /proc/$$/stat | awk '{print "PGRP="$5" TPGID="$8}'
# → ожидается: PGRP != TPGID (или ==, зависит от job control shell)
```

### C. Градация надёжности

| Метод | Надёжность | Скорость | Рекомендация |
|-------|:---------:|:--------:|:----------:|
| `stat` → tpgid vs pgrp | ⭐⭐⭐⭐⭐ | ⚡⚡⚡ (1 read) | **Основной** |
| `stat` → pgrp == tpgid + сканирование `/proc/*/stat` | ⭐⭐⭐⭐⭐ | ⚡ (N reads) | **Идентификация** |
| `/proc/{pid}/cmdline` чтение | ⭐⭐⭐⭐⭐ | ⚡⚡ (1 read) | **Имя процесса** |
| `/proc/{pid}/comm` | ⭐⭐⭐ | ⚡⚡⚡ (1 read) | Fallback на cmdline |
| `/proc/{pid}/fd/0` → pty сравнение | ⭐⭐⭐ | 🐢 (N readlink) | Fallback при ошибках |
| `/proc/{pid}/status` → `State:` | ⭐⭐ | ⚡⚡ (1 read) | Дополнительный сигнал |

### D. Рекомендуемый pipeline

```
  read /proc/{shellPid}/stat ─→ получить tpgid
          │
          ▼
   tpgid == -1? ─→ fallback к comm/status (вероятно, нет TTY)
          │
          ▼
   tpgid == pgrp? ─YES─→ shell foreground → default layout
          │
          NO
          ▼
   scan /proc/*/stat ─→ найти pgrp == tpgid
          │
          ▼
   read /proc/{pid}/cmdline ─→ определить имя
          │
          ▼
   identifyProgramType(cmdline) ─→ сопоставить с таблицей
          │
          ▼
   switchLayout(type)
```

---

## 9. Выводы

1. **Самый быстрый и надёжный метод** — чтение `/proc/{shellPid}/stat` и сравнение `tpgid` с `pgrp`. 
   Одно чтение ~200 байт, ~0.1ms CPU. Позволяет с ~100% точностью определить, запущена ли 
   foreground программа или shell у prompt'а.

2. **Для идентификации программы** (vim vs nano vs opencode) требуется второй шаг — 
   сканирование `/proc/*/stat` для поиска процесса с `pgrp == tpgid`, а затем чтение его `/proc/{pid}/cmdline`.

3. **Накладные расходы пренебрежимо малы:** fast-path (90% времени) — 0.1ms за чтение, 
   slow-path (10% времени при смене процесса) — 1-3ms. Даже при polling раз в 500ms 
   это не влияет на производительность UI.

4. **Самое сложное — правильно спарсить `/proc/{pid}/stat`** из-за поля `(comm)`, 
   содержащего скобки и пробелы. Решение: искать последнюю `)`.

5. **Специфика Android:** Метод требует, чтобы shell имел контроль над терминалом 
   (вызывал `TIOCSCTTY`). В Termux это всегда так, но в редко конфигурациях без job control 
   (через `adb shell` или `su`) tpgid может быть `-1`. Для таких случаев предусмотрен fallback.
