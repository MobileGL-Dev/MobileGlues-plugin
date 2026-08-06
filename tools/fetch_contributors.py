#!/usr/bin/env python3
"""Regenerate the contributor list and avatars bundled with the app.

This app declares no INTERNET permission -- that is a promise the privacy page
makes and invites the user to verify -- so the credits cannot be fetched at
runtime. They are baked in instead, and this script is how they get refreshed:
run it by hand when the list has moved on, never as part of the build. A build
that reaches the network is a build that fails offline and cannot be reproduced.

    python3 tools/fetch_contributors.py

Writes:
    app/src/main/res/drawable-nodpi/avatar_*.webp
    app/src/main/java/com/fcl/plugin/mobileglues/ui/Contributors.kt
"""

from __future__ import annotations

import io
import json
import pathlib
import re
import urllib.request

from PIL import Image

REPOS = [
    ("MobileGlues", "R.string.third_party_renderer"),
    ("MobileGlues-plugin", "R.string.third_party_plugin"),
    ("MobileGlues-release", "R.string.repo_release"),
]

ROOT = pathlib.Path(__file__).resolve().parent.parent
AVATAR_DIR = ROOT / "app/src/main/res/drawable-nodpi"
KOTLIN_FILE = (
    ROOT / "app/src/main/java/com/fcl/plugin/mobileglues/ui/Contributors.kt"
)

AVATAR_PX = 96


def get(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "MobileGlues-plugin"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def resource_name(login: str) -> str:
    """Android resource names are [a-z0-9_] and may not start with a digit."""
    cleaned = re.sub(r"[^a-z0-9_]", "_", login.lower())
    return f"avatar_{cleaned}"


def main() -> None:
    AVATAR_DIR.mkdir(parents=True, exist_ok=True)
    groups = []
    seen_avatars: dict[str, str] = {}

    for repo, title_res in REPOS:
        url = f"https://api.github.com/repos/MobileGL-Dev/{repo}/contributors?per_page=100"
        people = json.loads(get(url))
        entries = []
        for person in people:
            # Bots commit too, but a credit list is for people.
            if person.get("type") == "Bot" or person["login"].endswith("[bot]"):
                continue
            login = person["login"]
            name = resource_name(login)
            if name not in seen_avatars:
                raw = get(f"{person['avatar_url']}&s={AVATAR_PX}")
                image = Image.open(io.BytesIO(raw)).convert("RGB")
                image = image.resize((AVATAR_PX, AVATAR_PX), Image.LANCZOS)
                image.save(AVATAR_DIR / f"{name}.webp", "WEBP", quality=80, method=6)
                seen_avatars[name] = login
            entries.append((login, name, person["contributions"]))
        groups.append((repo, title_res, entries))

    lines = [
        "package com.fcl.plugin.mobileglues.ui",
        "",
        "import androidx.annotation.DrawableRes",
        "import androidx.annotation.StringRes",
        "import com.fcl.plugin.mobileglues.R",
        "",
        "/**",
        " * 一位贡献者。头像随包内置，不是运行时下载的——本应用没有网络权限。",
        " */",
        "data class Contributor(",
        "    val login: String,",
        "    @param:DrawableRes val avatar: Int,",
        ")",
        "",
        "/** 一个仓库的贡献者名单。 */",
        "data class ContributorGroup(",
        "    @param:StringRes val title: Int,",
        "    val contributors: List<Contributor>,",
        ")",
        "",
        "/**",
        " * 三个仓库的贡献者，按提交数从多到少。",
        " *",
        " * 这份名单和头像由 tools/fetch_contributors.py 生成，不要手改：改了下次刷新就没了。",
        " * 也正因为是生成的，它停在生成的那一刻——把它做成实时的需要 INTERNET 权限，",
        " * 而隐私政策里承诺了没有这个权限，一份致谢名单不值得拿那条承诺去换。",
        " */",
        "val ContributorGroups = listOf(",
    ]
    for repo, title_res, entries in groups:
        lines.append(f"    // {repo}")
        lines.append("    ContributorGroup(")
        lines.append(f"        {title_res},")
        lines.append("        listOf(")
        for login, name, contributions in entries:
            lines.append(
                f'            Contributor("{login}", R.drawable.{name}), '
                f"// {contributions}"
            )
        lines.append("        ),")
        lines.append("    ),")
    lines.append(")")
    lines.append("")

    KOTLIN_FILE.write_text("\n".join(lines), encoding="utf-8")

    total = sum(len(entries) for _, _, entries in groups)
    size = sum(path.stat().st_size for path in AVATAR_DIR.glob("avatar_*.webp"))
    print(f"{total} entries, {len(seen_avatars)} avatars, {size / 1024:.0f} KiB")


if __name__ == "__main__":
    main()
