import os
import subprocess
import asyncio

from typing import Final, List
from pathlib import Path
from telegram import Bot, InputMediaDocument


TOKEN: Final[str] = os.getenv("TELEGRAM_BOT_TOKEN")
BASE_URL: Final[str] = f"https://api.telegram.org/bot{TOKEN}"
TARGET_CHAT: Final[str] = "@on_Hit"
TARGET_TOPIC_ID: Final[int] = 4 # CI Build Topic ID
USE_REMOTE_MESSAGE: Final[bool] = True
CAPTION_TEMPLATE: Final[str] = """
onHit CI Build `{}`
```
{}
```
"""
BUILD_TYPES: Final[list[str]] = ["debug", "release"]

def get_apk_path(build_type: str, module: str = "app") -> Path:
    apk_dir = Path(f"{module}/build/outputs/apk/{build_type}")
    apks = list(apk_dir.glob("*.apk"))
    if not apks:
        raise FileNotFoundError(f"No APK found in {apk_dir}")
    return apks[0]

def get_latest_commit(branch: str = "main") -> tuple[str, str]:
    full_hash = subprocess.check_output(
        ["git", "rev-parse", "--short", branch], text=True
    ).strip()
    commit_msg = subprocess.check_output(
        ["git", "log", branch, "-1", "--pretty=format:%B"], text=True
    ).strip()

    return full_hash, commit_msg

async def send_files(
        bot: Bot,
        chat_id: str,
        topic_id: int,
        files: List[Path],
        caption: str) -> None:
    media_group: List[InputMediaDocument] = []
    for num, file in enumerate(files):
        media_group.append(InputMediaDocument(
            media=open(file, 'rb'),
            caption=caption if num == len(files) - 1 else "",
            parse_mode="MarkdownV2"
        ))
    await bot.send_media_group(
        chat_id=chat_id,
        media=media_group,
        parse_mode="MarkdownV2",
        disable_notification=True,
        allow_sending_without_reply=True,
        message_thread_id=topic_id
    )


async def main() -> None:
    try:
        bot: Bot = Bot(token = TOKEN)
        latest_hash, latest_message = get_latest_commit()
        caption: str = CAPTION_TEMPLATE.format(latest_hash, latest_message)
        apk_path: List[Path] = []
        for build_type in BUILD_TYPES:
            apk_path.append(get_apk_path(build_type))
        await send_files(bot, TARGET_CHAT, TARGET_TOPIC_ID, apk_path, caption)
    except Exception as e:
        print(e)

if __name__ == "__main__":
    asyncio.run(main())