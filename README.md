# FACEIT CS2 Telegram Tracker 🎯

Бот для отслеживания матчей, изменения ELO и детальной статистики игроков FACEIT CS2 в Telegram.

## Стек технологий
* **Язык**: Kotlin 2.3 (JVM 21)
* **Telegram API**: `dev.inmo:tgbotapi` (Long Polling)
* **HTTP клиент**: Ktor Client (CIO) + Content Negotiation (KotlinX Serialization)
* **База данных**: Exposed ORM + SQLite (WAL mode)
* **CI/CD & Деплой**: GitHub Actions + GitHub Packages (`ghcr.io`) + Docker Compose

---

## Быстрый старт на сервере

### 1. Первоначальная настройка
На сервере перейдите в папку с сервисом (например, `~/services/tracker`):
```bash
mkdir -p ~/services/tracker && cd ~/services/tracker
```

Создайте файл `.env`:
```ini
TELEGRAM_BOT_TOKEN=ваш_токен_бота
FACEIT_API_KEY=ваш_api_ключ_faceit
TELEGRAM_CHAT_ID=
POLL_INTERVAL_SECONDS=45
SQLITE_DB_PATH=/app/data/tracker.db
```

Создайте `docker-compose.yml`:
```yaml
version: "3.8"

services:
  faceit-tracker:
    image: ghcr.io/incw/tracker:latest
    container_name: faceit-tracker
    restart: unless-stopped
    env_file:
      - .env
    environment:
      - SQLITE_DB_PATH=/app/data/tracker.db
    volumes:
      - ./data:/app/data
    deploy:
      resources:
        limits:
          cpus: "0.50"
          memory: 384M
        reservations:
          memory: 128M
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
    networks:
      - tracker-network

networks:
  tracker-network:
    driver: bridge
```

Подготовьте директорию для SQLite:
```bash
mkdir -p data
sudo chown -R 10001:10001 data
```

Запустите контейнер:
```bash
docker compose pull
docker compose up -d
```

---

## Как обновлять бота после релиза

Весь процесс полностью автоматизирован через GitHub Actions:

### 1. На вашем компьютере (отправка изменений)
Внесите изменения в код, протестируйте и отправьте коммит в репозиторий:
```bash
git add .
git commit -m "feat: описание ваших изменений"
git push origin main
```
> GitHub Actions автоматически соберет новый Docker-образ и опубликует его в `ghcr.io` с тегом `latest`.

### 2. На сервере (применение обновления)
Перейдите в папку с ботом на сервере и выполните:
```bash
docker compose pull && docker compose up -d
```
Docker скачает обновлённый образ и перезапустит контейнер с сохранением базы данных и всех настроек.

---

## Работа с базой данных (SQLite)

База данных и сопутствующие WAL-файлы хранятся в папке `./data/` на хосте:
* `data/tracker.db`
* `data/tracker.db-wal`
* `data/tracker.db-shm`

### Сброс / Дроп базы данных при тестировании
Если вам нужно полностью очистить базу и начать с чистого листа:

```bash
# 1. Останавливаем бота
docker compose down

# 2. Удаляем файлы базы данных
rm -f data/tracker.db*

# 3. Запускаем бота заново (база и все таблицы создадутся с нуля)
docker compose up -d
```

### Резервное копирование (бэкап) базы
Для создания мгновенной резервной копии базы:
```bash
cp data/tracker.db data/tracker_backup_$(date +%Y%m%d_%H%M%S).db
```

### Просмотр содержимого базы прямо на сервере
Если на сервере установлена утилита `sqlite3` (`apt install -y sqlite3`):
```bash
# Посмотреть отслеживаемых игроков
sqlite3 data/tracker.db "SELECT id, nickname, current_elo FROM tracked_players;"

# Посмотреть активные подписки чатов
sqlite3 data/tracker.db "SELECT * FROM chat_subscriptions;"
```

---

## Полезные команды управления

| Задача | Команда |
|---|---|
| **Просмотр логов в реальном времени** | `docker compose logs -f faceit-tracker` |
| **Последние 100 строк логов** | `docker compose logs --tail=100 faceit-tracker` |
| **Проверить статус контейнера** | `docker compose ps` |
| **Перезапустить бота** | `docker compose restart faceit-tracker` |
| **Остановить бота** | `docker compose down` |
| **Посмотреть потребление RAM и CPU** | `docker stats faceit-tracker` |
| **Очистить старые неиспользуемые Docker-образы** | `docker image prune -f` |
