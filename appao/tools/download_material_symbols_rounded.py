#!/usr/bin/env python3
"""Download Google's official Material Symbols Rounded SVGs and regenerate generic Android vectors.

Pinned to a specific upstream commit for reproducible CI builds. Only generic monochrome icon
resource aliases are regenerated. Brand logos, site favicons, splash artwork and unrelated assets
are preserved. The script never clears drawable-nodpi/; that folder may be restored separately.
"""
from __future__ import annotations

import concurrent.futures
import re
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA_ICON_LOADER = ROOT / "app/src/main/java/com/appao/ui/IconLoader.kt"
ASSET_DIR = ROOT / "app/src/main/assets/icons/material-symbols-rounded"
DRAWABLE_DIR = ROOT / "app/src/main/res/drawable"
NODPI_DIR = ROOT / "app/src/main/res/drawable-nodpi"
UPSTREAM_COMMIT = "49d4db35df873165d6bd6ba09b063c7dafbac2f4"
BASE_URL = (
    f"https://raw.githubusercontent.com/google/material-design-icons/{UPSTREAM_COMMIT}/"
    "symbols/web/{symbol}/materialsymbolsrounded/{filename}"
)

# Existing app call sites often use old Ionicons-style names. Map those resource IDs to the
# official Rounded symbols so XML layouts and Kotlin-generated views remain visually consistent.
RESOURCE_ALIASES: dict[str, str] = {
    "ic_add_circle_outline": "add_circle",
    "ic_add_outline": "add",
    "ic_apps": "apps",
    "ic_apps_outline": "apps",
    "ic_archive_outline": "archive",
    "ic_arrow_back_outline": "arrow_back",
    "ic_arrow_down_outline": "arrow_downward",
    "ic_arrow_forward_outline": "arrow_forward",
    "ic_arrow_redo_outline": "redo",
    "ic_arrow_undo_outline": "undo",
    "ic_arrow_up_outline": "arrow_upward",
    "ic_attach_outline": "attach_file",
    "ic_bar_chart_outline": "bar_chart",
    "ic_bluetooth_outline": "bluetooth",
    "ic_bookmark_outline": "bookmark",
    "ic_calendar_outline": "calendar_month",
    "ic_camera_outline": "photo_camera",
    "ic_chatbubble_outline": "chat_bubble",
    "ic_chatbubbles_outline": "forum",
    "ic_checkmark_circle_outline": "check_circle",
    "ic_checkmark_outline": "check",
    "ic_chevron_down_outline": "keyboard_arrow_down",
    "ic_close_circle_outline": "cancel",
    "ic_close_outline": "close",
    "ic_compass": "explore",
    "ic_compass_outline": "explore",
    "ic_contract_outline": "zoom_out",
    "ic_copy_outline": "content_copy",
    "ic_contrast_outline": "contrast",
    "ic_create_outline": "edit",
    "ic_desktop_outline": "desktop_windows",
    "ic_document_text_outline": "description",
    "ic_download_outline": "download",
    "ic_ellipsis_horizontal_outline": "more_horiz",
    "ic_ellipsis_vertical_outline": "more_vert",
    "ic_expand_outline": "zoom_in",
    "ic_eye_off_outline": "visibility_off",
    "ic_eye_outline": "visibility",
    "ic_file_tray_full_outline": "archive",
    "ic_flame_outline": "local_fire_department",
    "ic_folder_outline": "folder",
    "ic_funnel_outline": "filter_list",
    "ic_globe_outline": "public",
    "ic_heart": "favorite",
    "ic_heart_outline": "favorite",
    "ic_home": "home",
    "ic_home_outline": "home",
    "ic_image_outline": "image",
    "ic_key_outline": "key",
    "ic_language_outline": "language",
    "ic_link_outline": "link",
    "ic_location_outline": "location_on",
    "ic_lock_closed_outline": "lock",
    "ic_lock_open_outline": "lock_open",
    "ic_mail_outline": "mail",
    "ic_menu_outline": "menu",
    "ic_moon_outline": "dark_mode",
    "ic_newspaper_outline": "newspaper",
    "ic_notifications_outline": "notifications",
    "ic_open_outline": "open_in_new",
    "ic_options_outline": "tune",
    "ic_paper_plane_outline": "send",
    "ic_person_circle_outline": "account_circle",
    "ic_person_outline": "person",
    "ic_pie_chart_outline": "pie_chart",
    "ic_reader_outline": "article",
    "ic_refresh_outline": "refresh",
    "ic_search_outline": "search",
    "ic_settings_outline": "settings",
    "ic_share_social_outline": "share",
    "ic_shield_checkmark_outline": "verified_user",
    "ic_sparkles_outline": "auto_awesome",
    "ic_speedometer_outline": "speed",
    "ic_sunny_outline": "light_mode",
    "ic_swap_vertical_outline": "swap_vert",
    "ic_time_outline": "history",
    "ic_trash_outline": "delete",
    "ic_wifi_outline": "wifi",
    "ic_avatar_default": "account_circle",
    "ic_checkmark_circle_green": "check_circle",
}
RESOURCE_SIZES = {"ic_avatar_default": 64}
RESOURCE_COLORS = {"ic_checkmark_circle_green": "@color/dotGreen"}
FILLED_SYMBOLS = {"home", "apps", "explore", "person", "bookmark", "favorite", "account_circle"}


def symbol_names_from_loader() -> set[str]:
    source = JAVA_ICON_LOADER.read_text(encoding="utf-8")
    names = set(re.findall(r'^\s*(?:add|filled)\("([a-z0-9_]+)"', source, re.M))
    # The loader may be extended later. Fail closed if the expected map is not recognizable.
    if len(names) < 60:
        raise SystemExit(f"IconLoader symbol map looks incomplete: found {len(names)} official symbol names")
    names.update(RESOURCE_ALIASES.values())
    return names


def request_bytes(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "AppAo-Material-Symbols-Rounded/1.0"})
    with urllib.request.urlopen(req, timeout=35) as response:
        return response.read()


def download_variant(symbol: str, filled: bool = False) -> tuple[str, int]:
    filename = f"{symbol}{'_fill1' if filled else ''}_24px.svg"
    url = BASE_URL.format(symbol=symbol, filename=filename)
    last_error: Exception | None = None
    for attempt in range(1, 4):
        try:
            data = request_bytes(url)
            root = ET.fromstring(data)
            if not root.tag.endswith("svg") or not any(e.tag.rsplit("}", 1)[-1] == "path" for e in root.iter()):
                raise ValueError("response was not a valid SVG with path data")
            viewbox = root.attrib.get("viewBox", "")
            if not viewbox:
                raise ValueError("SVG has no viewBox")
            ASSET_DIR.mkdir(parents=True, exist_ok=True)
            (ASSET_DIR / filename).write_bytes(data)
            return filename, len(data)
        except Exception as exc:
            last_error = exc
            if attempt < 3:
                time.sleep(attempt)
    raise RuntimeError(f"Cannot download Material Symbol '{filename}' ({url}): {last_error}")


def vector_xml(symbol: str, size_dp: int = 24, color_ref: str = "@color/iconTint") -> str:
    asset = ASSET_DIR / f"{symbol}_24px.svg"
    root = ET.fromstring(asset.read_bytes())
    parts = root.attrib.get("viewBox", "0 0 24 24").replace(",", " ").split()
    if len(parts) != 4:
        raise ValueError(f"Unsupported viewBox for {symbol}: {parts}")
    min_x, min_y, width, height = map(float, parts)
    paths: list[str] = []
    for node in root.iter():
        if node.tag.rsplit("}", 1)[-1] != "path":
            continue
        d = node.attrib.get("d", "").strip()
        if not d:
            continue
        if node.attrib.get("transform"):
            raise ValueError(f"Unsupported transform attribute on {symbol} path")
        paths.append(f'        <path android:fillColor="{color_ref}" android:pathData="{d}" />')
    if not paths:
        raise ValueError(f"No paths found in Material Symbol {symbol}")
    tx, ty = -min_x, -min_y
    if tx or ty:
        body = f'    <group android:translateX="{tx:g}" android:translateY="{ty:g}">\n' + "\n".join(paths) + "\n    </group>"
    else:
        body = "\n".join(paths)
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{size_dp}dp"\n'
        f'    android:height="{size_dp}dp"\n'
        f'    android:viewportWidth="{width:g}"\n'
        f'    android:viewportHeight="{height:g}">\n'
        f'{body}\n'
        '</vector>\n'
    )


def write_alias(folder: Path, resource_name: str, content: str) -> None:
    folder.mkdir(parents=True, exist_ok=True)
    target = folder / f"{resource_name}.xml"
    # Avoid Android resource-merger conflicts with a restored nodpi bitmap of the same generic name.
    # Only the single generic resource being regenerated is removed; no directory-wide cleanup occurs.
    for ext in (".png", ".webp", ".jpg", ".jpeg", ".gif"):
        bitmap = folder / f"{resource_name}{ext}"
        if bitmap.exists():
            bitmap.unlink()
    target.write_text(content, encoding="utf-8")


def generate_vectors() -> int:
    for resource, symbol in sorted(RESOURCE_ALIASES.items()):
        color = RESOURCE_COLORS.get(resource, "@color/iconTint")
        content = vector_xml(symbol, RESOURCE_SIZES.get(resource, 24), color)
        write_alias(DRAWABLE_DIR, resource, content)
        write_alias(NODPI_DIR, resource, content)
    return len(RESOURCE_ALIASES)


def main() -> int:
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    symbols = symbol_names_from_loader()
    variants = [(symbol, False) for symbol in sorted(symbols)]
    variants += [(symbol, True) for symbol in sorted(FILLED_SYMBOLS & symbols)]
    errors: list[str] = []
    completed: list[tuple[str, int]] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        future_to_name = {executor.submit(download_variant, symbol, filled): (symbol, filled) for symbol, filled in variants}
        for future in concurrent.futures.as_completed(future_to_name):
            try:
                completed.append(future.result())
            except Exception as exc:
                errors.append(str(exc))
    if errors:
        print("\n".join(sorted(errors)), file=sys.stderr)
        return 1
    license_text = (
        "Material Symbols Rounded\nCopyright Google LLC\n"
        "Source: https://github.com/google/material-design-icons\n"
        f"Pinned upstream commit: {UPSTREAM_COMMIT}\n"
        "License: Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)\n"
    )
    (ASSET_DIR / "LICENSE.txt").write_text(license_text, encoding="utf-8")
    try:
        vector_count = generate_vectors()
    except Exception as exc:
        print(f"ERROR generating Android VectorDrawable aliases: {exc}", file=sys.stderr)
        return 1
    total_bytes = sum(size for _, size in completed)
    print(f"Downloaded {len(completed)} official Material Symbols Rounded SVG files ({total_bytes:,} bytes).")
    print(f"Generated {vector_count} generic VectorDrawable aliases in drawable/ and drawable-nodpi/ without clearing either folder.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
