# Process-Aware ExtraKeys Context Layout — Design Document

## 1. Обзор

Текущая система ExtraKeys использует одно статическое свойство `extra-keys` (JSON массив массивов)
для всей раскладки. Это неудобно, когда пользователь запускает разные программы в терминале:
для vim нужны одни кнопки (`:`, `/`, `n`, `N`), для Python — другие (`(`, `)`, `→`), для opencode — третьи.

Предлагается новое свойство `extra-keys-context`, которое хранит **множество раскладок** и автоматически
переключает их в зависимости от текущего активного процесса в терминале.

## 2. Формат свойства `extra-keys-context`

### 2.1. JSON Schema

```json
{
  "$schema": "https://json-schema.org/draft-07/schema#",
  "title": "ExtraKeys Context Layouts",
  "description": "Process-aware extra-keys layouts for Termux. Defines multiple keyboard layouts keyed by process name patterns.",
  "type": "object",
  "properties": {
    "default": {
      "description": "The fallback layout used when no other context matches. REQUIRED. Same format as the existing extra-keys property.",
      "$ref": "#/definitions/layout"
    },
    "contexts": {
      "description": "Array of context-specific layouts, evaluated in order. First match wins.",
      "type": "array",
      "items": { "$ref": "#/definitions/context" }
    }
  },
  "required": ["default"],
  "additionalProperties": false,
  "definitions": {
    "context": {
      "type": "object",
      "properties": {
        "name": {
          "description": "Human-readable context name (used for logging, debugging, and UI hints).",
          "type": "string",
          "minLength": 1
        },
        "patterns": {
          "description": "Array of glob patterns to match against the current foreground process name (comm/argv[0]). Patterns support * and ? wildcards.",
          "type": "array",
          "items": { "type": "string", "minLength": 1 },
          "minItems": 1
        },
        "matchStrategy": {
          "description": "What to match patterns against.",
          "type": "string",
          "enum": ["comm", "cmdline", "both"],
          "default": "both"
        },
        "layout": {
          "description": "The ExtraKeys layout to use when this context is active. Same format as the existing extra-keys property.",
          "$ref": "#/definitions/layout"
        }
      },
      "required": ["name", "patterns", "layout"],
      "additionalProperties": false
    },
    "layout": {
      "description": "An array of rows, each row being an array of key definitions. Each key is either a string (key name) or a JSON object (advanced config with key/macro, popup, display, swipe directions).",
      "type": "array",
      "items": {
        "type": "array",
        "items": {
          "oneOf": [
            { "type": "string" },
            { "$ref": "#/definitions/advancedKey" }
          ]
        }
      }
    },
    "advancedKey": {
      "type": "object",
      "properties": {
        "key":    { "type": "string", "description": "Key name to send" },
        "macro":  { "type": "string", "description": "Space-separated key combination" },
        "display": { "type": "string", "description": "Custom display text on the button" },
        "popup":  { "description": "Popup key on swipe up (deprecated, use swipeUp)" },
        "swipeUp":   { "$ref": "#/definitions/swipeKey" },
        "swipeDown": { "$ref": "#/definitions/swipeKey" },
        "swipeLeft": { "$ref": "#/definitions/swipeKey" },
        "swipeRight":{ "$ref": "#/definitions/swipeKey" }
      },
      "oneOf": [
        { "required": ["key"] },
        { "required": ["macro"] }
      ],
      "additionalProperties": false
    },
    "swipeKey": {
      "oneOf": [
        { "type": "string" },
        {
          "type": "object",
          "properties": {
            "key":    { "type": "string" },
            "macro":  { "type": "string" },
            "display": { "type": "string" }
          },
          "oneOf": [
            { "required": ["key"] },
            { "required": ["macro"] }
          ]
        }
      ]
    }
  }
}
```

### 2.2. Пример конфигурации

```json
{
  "default": [
    ["ESC", "TAB", "CTRL", "ALT", "LEFT", "DOWN", "UP", "RIGHT"],
    ["-", "/", "HOME", "PGUP", "PGDN", "END", "BKSP"]
  ],
  "contexts": [
    {
      "name": "vim",
      "patterns": ["*vim*", "nvim*", "*vi*"],
      "matchStrategy": "comm",
      "layout": [
        ["ESC", ":", "/", "?", "n", "N"],
        ["u", "CTRL+r", "dd", "yy", "p", "P"],
        ["SHIFT", "%", "$", "^", "w", "b"]
      ]
    },
    {
      "name": "opencode",
      "patterns": ["opencode*", "codex*", "claude*"],
      "matchStrategy": "comm",
      "layout": [
        ["CTRL", "c", "v", "z", "y", "s"],
        ["/", "ENTER", "TAB", "ESC"],
        ["UP", "DOWN", "LEFT", "RIGHT"]
      ]
    },
    {
      "name": "python",
      "patterns": ["python*", "ipython*", "pdb*", "ptpython*"],
      "matchStrategy": "comm",
      "layout": [
        ["(", ")", "{", "}", "[", "]"],
        ["TAB", "CTRL", "c", "v", "d"],
        ["def", "class", "return", "print", "import"]
      ]
    },
    {
      "name": "shell",
      "patterns": ["*sh*", "bash*", "zsh*", "fish*"],
      "matchStrategy": "comm",
      "layout": [
        ["ESC", "cd", "ls", "ll", "cat", "grep"],
        ["TAB", "CTRL", "c", "d", "↑", "↓"],
        ["|", ">", ">>", "<", "&", ";"]
      ]
    }
  ]
}
```

### 2.2b. Минимальный пример (простая форма)

Поскольку поле `default` всегда обязательно, минимальная конфигурация для пользователя,
которому не нужны контексты, выглядит так — и в этом случае `extra-keys-context` просто
не указывается, система использует старый `extra-keys`:

```json
{
  "default": [
    ["ESC", "TAB", "CTRL", "ALT", "LEFT", "DOWN", "UP", "RIGHT"],
    ["-", "/", "HOME", "PGUP", "PGDN", "END", "BKSP"]
  ]
}
```

### 2.3. Детектирование активного процесса

Для определения контекста используется **PID процесса оболочки** (`TerminalSession.mShellPid`):

1. **comm** (`/proc/<pid>/comm`): короткое имя процесса (например, `vim`, `python3`, `bash`)
2. **cmdline** (`/proc/<pid>/cmdline`): полная командная строка (например, `/data/data/com.termux/files/usr/bin/vim main.c`)

Базовая стратегия:
- Открыть `/proc/<shell_pid>/cmdline`, прочитать argv[0]
- Если argv[0] содержит имя интерпретатора (например, `python3`), заглянуть в дочерние
  процессы через `/proc/<shell_pid>/task/*/children` и найти листовой (самый глубокий) процесс
- Для этого процесса прочитать `comm` и `cmdline`
- Сопоставить с паттернами из `contexts`

Для эффективности можно использовать `pgrep -P <shell_pid>` или чтение `/proc/<shell_pid>/task/<tid>/children`.

**Алгоритм поиска:**
```
1. Прочитать /proc/<shell_pid>/cmdline → получаем процесс оболочки
2. Прочитать /proc/<shell_pid>/task/<tid>/children для каждого thread
3. Найти листовой child (наибольшая глубина)
4. Для него прочитать comm и cmdline
5. Сопоставить с паттернами
```

### 2.4. Периодичность проверки

Проверка процесса должна происходить:
- **При переключении сессии** (`onSessionPageSelected`) — обязательный релоад
- **При нажатии клавиши** (после отправки ввода) — опционально, если процесс мог измениться
- **По таймеру** (каждые 1-2 секунды) — lazy проверка через Handler, не UI-блокирующая
- **Лучший вариант: пост-обработка вывода** — сигнал: когда после отправки команды
  терминал отдаёт новый промпт (можно проверять через `onTitleChanged` или
  кастомный парсинг вывода, но это сложнее)

Практически рекомендуется **смешанный подход**:
- При `onSessionPageSelected()` — всегда перепроверять
- Запускать `Handler.postDelayed` проверку каждые 1.5с, которая читает `/proc` без I/O на UI-потоке
  (через `AsyncTask` / корутину), и если процесс изменился — релоадит раскладку

## 3. Wildcard / Pattern Matching

### 3.1. Формат паттернов

Используются **стандартные glob-паттерны** (совместимость с `java.nio.file.FileSystem.getPathMatcher("glob:...")`
или ручная реализация):

| Паттерн | Значение | Примеры совпадений |
|---------|----------|--------------------|
| `*vim*` | Содержит "vim" в любом месте | `vim`, `nvim`, `vimdiff`, `lvim` |
| `vim*` | Начинается с "vim" | `vim`, `vimdiff` |
| `*vim` | Заканчивается на "vim" | `nvim` (если comm = nvim), не `nvim.app` |
| `nvim` | Точное совпадение | только `nvim` |
| `python?` | Один любой символ после `python` | `python3`, `python2`, не `python3.10` |
| `[kv]im*` | Класс символов | `kim` или `vim` |
| `*sh` | Заканчивается на sh | `bash`, `zsh`, `sh`, `ksh` |
| `node*` | Начинается с node | `node`, `nodejs` |

### 3.2. Стратегия сопоставления

1. **`matchStrategy`** (опционально, `"both"` по умолчанию):
   - `"comm"` — сопоставлять только с коротким именем процесса (`/proc/pid/comm`)
   - `"cmdline"` — сопоставлять с argv[0] из `/proc/pid/cmdline`
   - `"both"` — сопоставлять с обоими (OR логика — совпадение с любым достаточно)

2. **Порядок проверки:** контексты проверяются в порядке их объявления в массиве `contexts`.
   Первый совпавший контекст активируется. Если ни один не совпал — используется `default`.

3. **Регистронезависимое сравнение** (на Android процесс comm может быть в lowercase).

## 4. Хранение: SharedPreferences vs Отдельный файл

### 4.1. SharedPreferences (как сейчас хранится `extra-keys`)

| Плюсы | Минусы |
|-------|--------|
| Единый механизм хранения всех Termux-свойств | Ограничение на размер значения: SharedPreferences не предназначены для хранения больших JSON-блоков (файлы preferences XML растут, итерируются при каждом save) |
| Уже интегрировано в `TermuxAppSharedPreferences.getInternalPropertyValue()` | Строковый тип — при больших конфигурациях (5+ контекстов x 3+ строк) JSON может стать >10KB, что неудобно для SharedPreferences |
| Не нужно менять систему загрузки — наследуется вся инфраструктура парсинга | Миграция старого `extra-keys` вместе с новым `extra-keys-context` — оба в одном shared_prefs XML могут пересекаться по размеру |
| Простая обратная совместимость — свойство просто отсутствует | Сложность редактирования пользователем вне приложения (SharedPreferences — бинарный XML, сложно редактировать вручную) |
| Уже есть `setExtraKeys()` — аналогичный сеттер | |

### 4.2. Отдельный файл (JSON в `TERMUX_HOME/.termux/extra-keys-context.json`)

| Плюсы | Минусы |
|-------|--------|
| Пользователь может редактировать вручную (на Android через редактор) | Нужна новая система загрузки/парсинга — нельзя использовать `getInternalPropertyValue()` напрямую |
| Структура не ограничена размером | Нужен механизм наблюдения за изменениями файла (FileObserver) |
| Чистое разделение: статическая раскладка (`extra-keys`) и контекстные (`extra-keys-context`) — независимые файлы | Усложнение для пользователя: два места для правок |
| Легко версионировать, бекапить | Расхождение между файловым и preferences-состоянием (если редактировать и так, и так) |
| Помогает визуальным редакторам (можно редактировать отдельно от preferences) | |

### 4.3. Рекомендация: SharedPreferences + файловый fallback

Использовать **SharedPreferences как primary storage** (единообразие с существующей системой)
с опцией загрузки из **JSON-файла** через `extra-keys-context-file` (опциональное свойство-путь):

```
extra-keys-concrete=@/data/data/com.termux/files/home/.termux/extra-keys-context.json
```

Где синтаксис `@<path>` загружает значение из файла по абсолютному пути. Это уже
частично реализовано в `SharedProperties.getPropertiesFromFile()` — можно переиспользовать
механизм.

**Итоговый приоритет:**
1. Если есть `extra-keys-context-file` → загрузить JSON из файла
2. Иначе прочитать `extra-keys-context` из SharedPreferences
3. Если и там пусто → использовать legacy `extra-keys`

## 5. Fallback-механизм

```
1. process_matched = null
2. Для каждого context из extra-keys-context.contexts (по порядку):
     Для каждого pattern из context.patterns:
       Если glob совпал с comm или cmdline активного процесса → process_matched = context
3. Если process_matched != null → используем process_matched.layout
4. Если process_matched == null → используем extra-keys-context.default
5. Если extra-keys-context вообще не задан → используем legacy extra-keys (обратная совместимость)
6. Если и extra-keys пуст → используем DEFAULT_IVALUE_EXTRA_KEYS
```

## 6. Изменения в коде

### 6.1. `TermuxPropertyConstants` — новый ключ

```java
/** Defines the key for process-aware extra keys context layouts */
public static final String KEY_EXTRA_KEYS_CONTEXT = "extra-keys-context";
public static final String KEY_EXTRA_KEYS_CONTEXT_FILE = "extra-keys-context-file";

/** Defines the key for the interval (ms) between process checks */
public static final String KEY_EXTRA_KEYS_CONTEXT_POLL_INTERVAL = "extra-keys-context-poll-interval";
public static final String DEFAULT_IVALUE_EXTRA_KEYS_CONTEXT_POLL_INTERVAL = "1500";
```

Добавить в `TERMUX_APP_PROPERTIES_LIST`:

```java
KEY_EXTRA_KEYS_CONTEXT,
KEY_EXTRA_KEYS_CONTEXT_FILE,
KEY_EXTRA_KEYS_CONTEXT_POLL_INTERVAL,
```

### 6.2. `ExtraKeysContextInfo` — новый класс

Новый класс, аналогичный `ExtraKeysInfo`, который:
- Парсит JSON-объект `extra-keys-context`
- Хранит `ExtraKeyButton[][]` для каждого контекста + `default`
- Предоставляет `getLayoutForProcess(String comm, String cmdline)`
- Использует `java.nio.file.FileSystem.getPathMatcher("glob:" + pattern)` для матчинга
- Кэширует скомпилированные паттерны (`Pattern.compile()`)

```java
public class ExtraKeysContextInfo {
    private final ExtraKeyButton[][] defaultLayout;
    private final List<ContextLayout> contexts;

    public ExtraKeysContextInfo(String json, String style,
                                ExtraKeyDisplayMap aliasMap) throws JSONException { ... }

    @Nullable
    public ExtraKeyButton[][] getLayoutForProcess(String comm, String cmdline) { ... }

    private static class ContextLayout {
        final String name;
        final List<Pattern> compiledPatterns; // glob→regex
        final String matchStrategy;
        final ExtraKeyButton[][] layout;
    }
}
```

### 6.3. `TermuxTerminalExtraKeys.setExtraKeys()` — модификация

```java
private void setExtraKeys() {
    mExtraKeysInfo = null;
    
    try {
        // 1. Читаем extra-keys-context
        String contextJson = (String) mActivity.getProperties()
            .getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_CONTEXT, true);
        
        if (contextJson != null && !contextJson.isEmpty()) {
            // 2. Парсим контекстную конфигурацию
            ExtraKeysContextInfo contextInfo = new ExtraKeysContextInfo(
                contextJson, extraKeysStyle, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            
            // 3. Определяем активный процесс
            ProcessInfo processInfo = getCurrentProcessInfo();
            
            // 4. Получаем раскладку для процесса
            ExtraKeyButton[][] buttons = contextInfo.getLayoutForProcess(
                processInfo.comm, processInfo.cmdline);
            if (buttons == null) {
                // fallback: контексты есть, но ни один не совпал — берём default из контекста
                buttons = contextInfo.getDefaultLayout();
            }
            
            // 5. Создаём ExtraKeysInfo из выбранных кнопок
            mExtraKeysInfo = new ExtraKeysInfo(buttons, extraKeysStyle,
                ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        } else {
            // 6. Обратная совместимость — legacy extra-keys
            String extrakeys = (String) mActivity.getProperties()
                .getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS, true);
            mExtraKeysInfo = new ExtraKeysInfo(extrakeys, extraKeysStyle,
                ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        }
    } catch (JSONException e) {
        // ошибка парсинга — fallback к legacy extra-keys, затем к default
        ...
    }
}
```

### 6.4. Детектирование процесса — `ProcessMonitor`

```java
public class ProcessMonitor {
    public static ProcessInfo getCurrentProcessInfo(TerminalSession session) {
        int shellPid = session.mShellPid;
        if (shellPid <= 0) return null;
        
        // 1. Читаем cmdline оболочки
        String shellComm = readProcFile(shellPid, "comm");
        String shellCmdline = readProcCmdline(shellPid);
        
        // 2. Ищем дочерние процессы
        int leafPid = findDeepestChild(shellPid);
        
        if (leafPid > 0 && leafPid != shellPid) {
            return new ProcessInfo(
                readProcFile(leafPid, "comm"),
                readProcCmdline(leafPid)
            );
        }
        
        // Если дочерних нет — возвращаем процесс оболочки
        return new ProcessInfo(shellComm, shellCmdline);
    }
    
    private static int findDeepestChild(int pid) {
        // Через /proc/pid/task/*/children
        // BFS/DFS до листового процесса
    }
}
```

### 6.5. `ExtraKeysView` — контекстный мониторинг

```java
// В ExtraKeysView (или TermuxTerminalExtraKeys):
private Handler mContextCheckHandler;
private Runnable mContextCheckRunnable;
private ProcessInfo mLastProcessInfo;

private void startContextMonitoring() {
    stopContextMonitoring();
    mContextCheckRunnable = new Runnable() {
        @Override
        public void run() {
            ProcessInfo current = getCurrentProcessInfo();
            if (!current.equals(mLastProcessInfo)) {
                mLastProcessInfo = current;
                reloadContextAwareLayout();
            }
            mContextCheckHandler.postDelayed(this, pollIntervalMs);
        }
    };
    mContextCheckHandler.postDelayed(mContextCheckRunnable, pollIntervalMs);
}

private void stopContextMonitoring() {
    if (mContextCheckHandler != null) {
        mContextCheckHandler.removeCallbacks(mContextCheckRunnable);
    }
}
```

## 7. Обратная совместимость

1. **Если `extra-keys-context` не задан:** система работает ровно как сейчас:
   читает `extra-keys`, парсит, отображает. Никаких изменений в поведении.

2. **Если `extra-keys-context` задан, но не содержит ключа `default`:**
   возвращать ошибку парсинга → fallback к `extra-keys`.

3. **Если `extra-keys-context` задан, но ни один контекст не совпал:**
   используется `extra-keys-context.default`. Если его нет — fallback к `extra-keys`.

4. **Если заданы ОБА `extra-keys` и `extra-keys-context`:**
   `extra-keys-context` имеет более высокий приоритет. `extra-keys` — fallback.

5. **Если `extra-keys-context` JSON некорректен:**
   показать toast об ошибке, использовать `extra-keys` (или default из PropertyConstants).

## 8. Итоговый JSON Schema — компактная версия

Для документации пользователя:

```json
{
  "//": "Обязательное поле: раскладка по умолчанию",
  "default": [
    ["ESC", "TAB", "CTRL", "ALT", "LEFT", "UP", "DOWN", "RIGHT"],
    ["HOME", "PGUP", "PGDN", "END", "BKSP"]
  ],

  "//": "Опционально: массив контекстных раскладок",
  "contexts": [
    {
      "//": "Название контекста (для логов)",
      "name": "vim",
      "//": "Glob-паттерны для сопоставления с именем процесса",
      "patterns": ["*vim*", "nvim*"],
      "//": "Опционально: что матчить — 'comm' | 'cmdline' | 'both' (по умолч.)",
      "matchStrategy": "comm",
      "//": "Раскладка для этого контекста",
      "layout": [["ESC", "/", "?", "n", "N"], [":", "dd", "yy", "u"]]
    }
  ]
}
```

## 9. План реализации (краткий)

1. **Добавить `KEY_EXTRA_KEYS_CONTEXT`** в `TermuxPropertyConstants.java`
   и в `TERMUX_APP_PROPERTIES_LIST`

2. **Создать `ExtraKeysContextInfo.java`** в пакете `termux/extrakeys/`:
   - Парсинг JSON (object с `default` и `contexts`)
   - Компиляция glob-паттернов в regex
   - Метод `getLayoutForProcess(comm, cmdline)` возвращает `ExtraKeyButton[][]`

3. **Создать `ProcessMonitor.java`** для чтения `/proc/<pid>/comm` и `/proc/<pid>/cmdline`

4. **Модифицировать `TermuxTerminalExtraKeys.setExtraKeys()`**:
   - Читать `extra-keys-context` первым
   - Если есть — определять процесс и выбирать раскладку
   - Если нет — читать `extra-keys` (обратная совместимость)

5. **Добавить механизм poll-проверки** в `ExtraKeysView` или `TermuxTerminalExtraKeys`:
   - Handler.postDelayed с интервалом из `extra-keys-context-poll-interval`
   - При изменении процесса — вызывать `reload()`

6. **Добавить поддержку в ExtraKeysEditorFragment** (опционально) — редактор контекстов
