# Телефония и whitelist

## Назначение

Приложение предоставляет ограниченный детский телефонный интерфейс: звонок можно начать только выбранному контакту из `AllowedContacts`, а входящие номера вне whitelist отклоняются.

## Исходящий звонок

Экран контактов доступен из неактивного сканера. `CallUiController` владеет выбранным контактом и видимостью picker, а перед `TelecomManager.placeCall` проверяет whitelist, роль default dialer и runtime-разрешение `CALL_PHONE`. Если роль не назначена, он запрашивает у view-layer стандартный Android request role dialog.

## Входящий и активный звонок

`WhitelistCallScreeningService` отклоняет неизвестный номер и убирает его из журнала/уведомлений. `FieldScannerInCallService` публикует состояние звонка и аудиомаршрута в `CallCoordinator`; `CallScreenPresenter` отображает состояние только через `DisplayZonesView`. Он обрабатывает руны `EMBER`/`FLUX`: принять или отклонить входящий вызов, завершить активный/исходящий. Отдельного XML overlay и call-кнопок (answer/reject/end/mute/speaker/Bluetooth) нет.

## Ограничения и обязательная проверка

- Полная работа требует назначения FieldScanner приложением телефона по умолчанию на реальном устройстве.
- Фактическое поведение screening и in-call UI зависит от версии Android и оператора; оно должно проверяться ручными звонками разрешённого и неразрешённого номеров.
- Не добавляйте номера в документацию, логи или тестовые snapshots. Редактирование списка контактов и нормализации номера требует обновления unit-тестов `WhitelistPolicyTest`.
- Отказ `TelecomManager.placeCall` по `SecurityException` или `IllegalStateException` дополнительно попадает в owner-only `DiagnosticsLog`; номер контакта в запись не добавляется.
