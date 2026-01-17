import os
import sys
import subprocess
import asyncio

from typing import Final, List
from pathlib import Path
from telegram import Bot, InputMediaDocument, Message

ARGS: Final[List[str]] = sys.argv[1:]
TOKEN: Final[str] = os.getenv("TELEGRAM_BOT_TOKEN")
TARGET_CHAT: Final[str] = "@on_Hit"
TARGET_TOPIC_ID: Final[int] = 271 if ARGS else 4
CAPTION_TEMPLATE: Final[str] = """
onHit v`{}` Release Build `{}`
{}
{}
""" if ARGS else """
onHit CI Build `{}`
```
{}
```
{}
"""
BUILD_TYPES: Final[list[str]] = ["debug", "release"]


def escape_markdown_v2(text: str) -> str:
    special_chars = r"_*[]()~`>#+-=|{}.!"""
    return ''.join(f'\\{c}' if c in special_chars else c for c in text)


ON_HIT_URL_CAPTION: Final[str] = (f"[onHit]({escape_markdown_v2('https://github.com/0penPublic/onHit')}) "
                                  f"\\| [Official Telegram Group]({escape_markdown_v2('https://t.me/on_hit')})")
RELEASE_URL_TEMPLATE: Final[str] = "https://github.com/0penPublic/onHit/releases/v{}"


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
        caption: str) -> tuple[Message, ...]:
    media_group: List[InputMediaDocument] = []
    for num, file in enumerate(files):
        media_group.append(InputMediaDocument(
            media=open(file, 'rb'),
            caption=caption if num == len(files) - 1 else "",
            parse_mode="MarkdownV2"
        ))
    print(caption)
    return await bot.send_media_group(
        chat_id=chat_id,
        media=media_group,
        parse_mode="MarkdownV2",
        disable_notification=True,
        allow_sending_without_reply=True,
        message_thread_id=topic_id
    )


async def main(argv: List[str]) -> None:
    try:
        bot: Bot = Bot(token=TOKEN)
        latest_hash, latest_message = get_latest_commit()
        apk_path: List[Path] = []
        for build_type in BUILD_TYPES:
            apk_path.append(get_apk_path(build_type))
        caption: str
        if argv:
            version_code: str = escape_markdown_v2(argv[0])
            release_url: str = RELEASE_URL_TEMPLATE.format(argv[0])
            release_markdown: str = f"[Release URL]({escape_markdown_v2(release_url)})"
            caption = CAPTION_TEMPLATE.format(version_code, latest_hash,
                                              release_markdown, ON_HIT_URL_CAPTION)
        else:
            caption = CAPTION_TEMPLATE.format(latest_hash, latest_message, ON_HIT_URL_CAPTION)
        messages = await send_files(bot, TARGET_CHAT, TARGET_TOPIC_ID, apk_path, caption)
        if argv:
            if await messages[-1].pin():
                print("Pinning the last message succeeded.")
            else:
                print("Pinning the last message failed.")
    except Exception as e:
        raise e


if __name__ == "__main__":
    asyncio.run(main(ARGS))
