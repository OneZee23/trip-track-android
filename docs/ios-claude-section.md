## Каждая правка доезжает до Android

Android живёт в `~/OneZeeProjects/trip-track-android`, рабочие копии задач в
`~/OneZeeProjects/trip-track-android-wt/<task>`. iPhone остаётся эталоном поведения,
чисел, wire-контракта и строк. Исправил iOS, передай оркестратору Android-пару с тестом;
не оставляй вторую платформу на старом поведении. В чужом checkout только читай.

Из корня Android-репозитория: `python3 tools/parity/parity.py changed <base>..HEAD`.
Диапазон относится к iOS; `--ios <path>` выбирает его checkout. Команда покажет
изменённые файлы и Android-пары по комментариям `// iOS: File.swift`.
`no counterpart yet` означает, что связь ещё не найдена. Если первым исправлен Android,
`changed-android <base>..HEAD` покажет Swift-файлы для обратного переноса.

Строки изменились, запускай в Android `python3 tools/strings/generate.py`.
Изменился провод, обновляй `docs/api-contract.md` и тесты обеих платформ.
Платформенные отличия объясняй в `docs/divergences.md`, общий инвентарь обновляй
командой `python3 tools/parity/parity.py report`. Порядок работы и выпуска:
Android `docs/how-to-port-a-change.md` и `docs/release.md`. В передаче называй commit
обеих платформ и что осталось перенести; одна ссылка на файл парность не доказывает.
