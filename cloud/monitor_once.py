from __future__ import annotations

import json
import os
import re
import sys
from datetime import datetime, timezone
from io import BytesIO
from pathlib import Path
from typing import Any

import requests
from docx import Document

ROOT = Path(__file__).resolve().parents[1]
STATE_PATH = ROOT / "state" / "cloud-state.json"
DATA_PATH = ROOT / "site" / "data.json"
YANDEX_API = "https://cloud-api.yandex.net/v1/disk/public/resources"
TELEGRAM_API = "https://api.telegram.org"
GROUP = os.getenv("TARGET_GROUP", "23СК")
PUBLIC_URL = os.getenv("YANDEX_PUBLIC_URL", "https://disk.yandex.ru/d/F_GFm6_Qi9GYAQ").strip()
TOKEN = os.getenv("TELEGRAM_BOT_TOKEN", "").strip()
RECIPIENTS = [x.strip() for x in re.split(r"[,;\s]+", os.getenv("TELEGRAM_CHAT_IDS", "")) if x.strip()]


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def read_json(path: Path, default: dict[str, Any]) -> dict[str, Any]:
    try:
        with path.open("r", encoding="utf-8") as handle:
            value = json.load(handle)
        return value if isinstance(value, dict) else default.copy()
    except FileNotFoundError:
        return default.copy()


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + ".tmp")
    with temp.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    temp.replace(path)


def telegram(method: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
    if not TOKEN:
        raise RuntimeError("Не задан GitHub Secret TELEGRAM_BOT_TOKEN")
    response = requests.post(
        f"{TELEGRAM_API}/bot{TOKEN}/{method}",
        json=payload or {},
        timeout=30,
    )
    response.raise_for_status()
    body = response.json()
    if not body.get("ok"):
        raise RuntimeError(f"Telegram API {method}: {body}")
    return body


def send_message(chat_id: str | int, text: str) -> None:
    # Telegram limits text messages to 4096 characters.
    telegram("sendMessage", {"chat_id": chat_id, "text": text[:3900], "disable_web_page_preview": True})


def docx_text(data: bytes) -> str:
    document = Document(BytesIO(data))
    parts: list[str] = []
    for paragraph in document.paragraphs:
        value = paragraph.text.strip()
        if value:
            parts.append(value)
    for table in document.tables:
        for row in table.rows:
            cells = [cell.text.strip().replace("\n", " / ") for cell in row.cells]
            if any(cells):
                parts.append(" | ".join(cells))
    return "\n".join(parts)


def group_lines(text: str, group: str = GROUP) -> list[str]:
    target = re.sub(r"[^0-9A-ZА-ЯЁ]+", "", group.upper()).replace("C", "С").replace("K", "К")
    if not target:
        return []
    flexible = r"[\s.\-_/]*".join(re.escape(char) for char in target)
    pattern = re.compile(rf"(?<![0-9A-ZА-ЯЁ]){flexible}(?![0-9A-ZА-ЯЁ])", re.IGNORECASE)
    return [line.strip() for line in text.splitlines() if line.strip() and pattern.search(line.upper().replace("C", "С").replace("K", "К"))]


def list_docx() -> list[dict[str, Any]]:
    response = requests.get(
        YANDEX_API,
        params={"public_key": PUBLIC_URL, "limit": 100, "sort": "-modified"},
        timeout=30,
    )
    response.raise_for_status()
    payload = response.json()
    items = payload.get("_embedded", {}).get("items", [])
    return [item for item in items if item.get("type") == "file" and str(item.get("name", "")).lower().endswith(".docx") and item.get("path")]


def download_docx(path: str) -> bytes:
    response = requests.get(
        f"{YANDEX_API}/download",
        params={"public_key": PUBLIC_URL, "path": path},
        timeout=30,
    )
    response.raise_for_status()
    href = response.json().get("href")
    if not href:
        raise RuntimeError(f"Яндекс Диск не вернул ссылку скачивания для {path}")
    file_response = requests.get(href, timeout=90)
    file_response.raise_for_status()
    return file_response.content


def date_label(file_name: str) -> str:
    stem = Path(file_name).stem.strip()
    return stem or "дата не указана"


def make_public_data(file_name: str, modified: str, lines: list[str], full_text: str) -> dict[str, Any]:
    status = "has_changes" if lines else "no_changes"
    return {
        "version": 1,
        "group": GROUP,
        "status": status,
        "date_label": date_label(file_name),
        "file_name": file_name,
        "file_modified": modified or "",
        "published_at": utc_now(),
        "count": len(lines),
        "lines": lines[:100],
        # Never publish non-23SK rows from the shared college document.
        "document_preview": "",
    }


def render_notification(data: dict[str, Any]) -> str:
    if data["status"] == "no_changes":
        return f"✅ На {data['date_label']} замен для группы {GROUP} нет.\n📄 Проверен документ: {data['file_name']}"
    excerpts = "\n".join(f"• {line}" for line in data["lines"][:30])
    suffix = f"\n…и ещё {data['count'] - 30} строк(и)." if data["count"] > 30 else ""
    return f"🔔 Обновление замен для {GROUP}\n📅 Дата: {data['date_label']}\n📄 Файл: {data['file_name']}\n\n{excerpts}{suffix}"[:3900]


def render_data_reply(data: dict[str, Any] | None) -> str:
    if not data or data.get("status") == "waiting":
        return "Пока расписание не опубликовано. Проверь позже — облачный монитор ещё не обработал документ."
    title = f"Последний документ: {data.get('file_name', 'неизвестно')}\nДата: {data.get('date_label', 'не указана')}"
    if data.get("status") == "no_changes":
        return f"✅ На {data.get('date_label', 'эту дату')} замен для группы {GROUP} нет.\n📄 {data.get('file_name', '')}"
    lines = data.get("lines", [])
    content = "\n".join(f"• {line}" for line in lines[:30])
    return f"{title}\n\n{content}"[:3900]


def process_telegram_updates(state: dict[str, Any], latest_data: dict[str, Any] | None) -> None:
    if not TOKEN:
        print("TELEGRAM_BOT_TOKEN не задан: проверка Яндекс Диска продолжится, Telegram пропущен.")
        return
    offset = int(state.get("telegram_update_offset", 0))
    try:
        response = telegram("getUpdates", {"offset": offset, "timeout": 0, "limit": 100, "allowed_updates": ["message"]})
        updates = response.get("result", [])
    except Exception as exc:
        print(f"Не удалось получить команды Telegram: {exc}")
        return

    next_offset = offset
    for update in updates:
        update_id = int(update.get("update_id", 0))
        next_offset = max(next_offset, update_id + 1)
        message = update.get("message") or {}
        chat_id = (message.get("chat") or {}).get("id")
        text = (message.get("text") or "").strip()
        if not chat_id or not text.startswith("/"):
            continue
        command = text.split()[0].split("@")[0].lower()
        try:
            if command == "/id":
                reply = f"Твой Telegram chat_id: {chat_id}\n\nЧтобы получать уведомления о заменах, владелец проекта должен добавить этот ID в GitHub Secret TELEGRAM_CHAT_IDS."
                send_message(chat_id, reply)
            elif command in ("/start", "/help"):
                reply = (
                    "Привет! Я облачный бот замен для группы 23СК. ☁️\n\n"
                    "/today — последнее опубликованное расписание\n"
                    "/tomorrow — последнее опубликованное расписание\n"
                    "/week — последнее опубликованное расписание\n"
                    "/id — показать ID этого чата\n\n"
                    "Уведомления отправляются в чаты, перечисленные владельцем в настройках проекта."
                )
                send_message(chat_id, reply)
            elif command in ("/today", "/tomorrow", "/week"):
                send_message(chat_id, render_data_reply(latest_data))
        except Exception as exc:
            print(f"Не удалось ответить на команду {command} в чате {chat_id}: {exc}")
    if next_offset != offset:
        state["telegram_update_offset"] = next_offset


def main() -> int:
    if not PUBLIC_URL:
        raise RuntimeError("Не задан YANDEX_PUBLIC_URL")
    state = read_json(STATE_PATH, {"initialized": False, "telegram_update_offset": 0})
    data = read_json(DATA_PATH, {"version": 1, "group": GROUP, "status": "waiting", "lines": [], "count": 0})

    if not TOKEN:
        raise RuntimeError("Не задан TELEGRAM_BOT_TOKEN в GitHub Secrets")

    result_code = 0
    try:
        files = list_docx()
        if not files:
            print("В публичной папке Яндекс Диска не найдено DOCX-файлов. Последние опубликованные данные сохранены.")
        else:
            latest = max(files, key=lambda item: str(item.get("modified", "")))
            file_id = str(latest.get("resource_id") or latest.get("path") or latest.get("name"))
            modified = str(latest.get("modified", ""))
            fingerprint_changed = (
                state.get("latest_file_id") != file_id
                or state.get("latest_file_modified") != modified
            )
            if fingerprint_changed:
                raw = download_docx(str(latest["path"]))
                text = docx_text(raw)
                if not text.strip():
                    raise RuntimeError(f"Документ {latest.get('name')} пустой или не содержит читаемого текста")
                lines = group_lines(text)
                data = make_public_data(str(latest.get("name", "Расписание.docx")), modified, lines, text)
                write_json(DATA_PATH, data)
                print(f"Обновлён сайт: {data['file_name']} — {data['count']} строк для {GROUP}.")

                # The first successful run establishes a baseline and publishes it without spamming old files.
                if state.get("initialized") and RECIPIENTS:
                    notification = render_notification(data)
                    for recipient in RECIPIENTS:
                        try:
                            send_message(recipient, notification)
                        except Exception as exc:
                            print(f"Не удалось отправить уведомление в чат {recipient}: {exc}")
                elif not state.get("initialized"):
                    print("Первый запуск: сохранён последний документ без массовой рассылки старых замен.")
                elif not RECIPIENTS:
                    print("Новый документ найден, но TELEGRAM_CHAT_IDS не настроен — уведомления не отправлены.")

                state["initialized"] = True
                state["latest_file_id"] = file_id
                state["latest_file_modified"] = modified
            else:
                print("Проверка завершена: новых изменений в последнем документе нет.")
    except Exception as exc:
        # Do not turn source/API errors into a false "no replacements" state.
        print(f"Ошибка проверки Яндекс Диска: {type(exc).__name__}: {exc}")
        # Keep the previous good site data intact; fail the job so GitHub shows the error.
        result_code = 1

    # Process commands after refreshing data so /today can return the newest document.
    process_telegram_updates(state, data)
    write_json(STATE_PATH, state)
    return result_code


if __name__ == "__main__":
    raise SystemExit(main())
