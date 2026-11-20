"""Read pending Telegram updates and print chat IDs without displaying the bot token."""
import getpass
import json
import urllib.parse
import urllib.request


def main() -> None:
    token = getpass.getpass("Вставь токен бота (ввод скрыт): ").strip()
    if not token:
        print("Токен не введён.")
        return
    url = f"https://api.telegram.org/bot{token}/getUpdates?" + urllib.parse.urlencode({"limit": 100, "timeout": 0})
    try:
        with urllib.request.urlopen(url, timeout=25) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except Exception as exc:
        print(f"Не удалось обратиться к Telegram: {exc}")
        return
    if not payload.get("ok"):
        print("Telegram API вернул ошибку:", payload)
        return
    seen = {}
    for item in payload.get("result", []):
        message = item.get("message") or item.get("edited_message") or {}
        chat = message.get("chat") or {}
        chat_id = chat.get("id")
        if chat_id is not None:
            seen[str(chat_id)] = {
                "chat_id": str(chat_id),
                "type": chat.get("type", "unknown"),
                "title": chat.get("title") or " ".join(x for x in [chat.get("first_name"), chat.get("last_name")] if x) or chat.get("username") or "Чат",
                "last_message": (message.get("text") or "")[:80],
            }
    if not seen:
        print("Новых ожидающих сообщений нет. Открой Telegram, отправь своему боту /id, затем запусти этот файл ещё раз.")
        return
    print("Найденные чаты (выбери свой личный чат):")
    for info in seen.values():
        print(f"chat_id: {info['chat_id']} | type: {info['type']} | {info['title']} | сообщение: {info['last_message']}")
    print("\nДобавь нужные ID в GitHub Secret TELEGRAM_CHAT_IDS, разделяя запятой.")
    print("Токен не был выведен на экран и никуда не отправлялся.")


if __name__ == "__main__":
    main()
