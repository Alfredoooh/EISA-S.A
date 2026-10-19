#!/usr/bin/env python3
"""Download the exact Material Symbols Rounded SVGs used by IconLoader and generate Android vectors.

Source: Google's official material-design-icons repository (pinned commit).
SVGs go into app/src/main/assets/icons/material-symbols-rounded/.
VectorDrawable equivalents go into drawable and drawable-nodpi so Kotlin and XML layouts use the same icon set.
The script never clears drawable-nodpi; custom/site/brand assets there are retained.
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
ICON_LOADER = ROOT / "app/src/main/java/com/appao/ui/IconLoader.kt"
OUTPUT = ROOT / "app/src/main/assets/icons/material-symbols-rounded"
RES_DRAWABLE = ROOT / "app/src/main/res/drawable"
RES_NODPI = ROOT / "app/src/main/res/drawable-nodpi"
# Additional generic resources that exist in this project but are not covered by IconLoader aliases.
# The avatar keeps its original 64dp intrinsic geometry; the green success check adapts via values-night.
EXTRA_RESOURCE_SYMBOLS = {
    "ic_avatar_default": "account_circle",
    "ic_checkmark_circle_green": "check_circle",
}
EXTRA_RESOURCE_SIZES = {"ic_avatar_default": 64}
EXTRA_RESOURCE_COLORS = {"ic_checkmark_circle_green": "@color/dotGreen"}
UPSTREAM_COMMIT = "49d4db35df873165d6bd6ba09b063c7dafbac2f4"
BASE_URL = (
    f"https://raw.githubusercontent.com/google/material-design-icons/{UPSTREAM_COMMIT}/"
    "symbols/web/{name}/materialsymbolsrounded/{name}_24px.svg"
)


def normalized_alias(alias: str) -> str:
    return alias.lower().strip().replace("-", "_")


def alias_maps_from_source() -> tuple[dict[str, str], dict[str, str]]:
    text = ICON_LOADER.read_text(encoding="utf-8")
    legacy_match = re.search(
        r"private val legacyAliases:\s*Map<String, String>\s*=\s*mapOf\((.*?)\n\s*\)",
        text,
        re.S,
    )
    material_match = re.search(
        r"private val materialAliases:\s*Map<String, String>\s*=\s*mapOf\((.*?)\n\s*\)",
        text,
        re.S,
    )
    if not legacy_match or not material_match:
        raise SystemExit("Could not parse legacyAliases/materialAliases in IconLoader.kt")

    legacy_pairs = re.findall(
        r'"([a-z0-9_-]+)"\s+to\s+"([a-z0-9_-]+)"', legacy_match.group(1)
    )
    material_pairs = re.findall(
        r'"([a-z0-9_]+)"\s+to\s+"([a-z0-9_]+)"', material_match.group(1)
    )
    legacy = {normalized_alias(alias): value for alias, value in legacy_pairs}
    material = {normalized_alias(alias): value for alias, value in material_pairs}
    if len(material) < 20:
        raise SystemExit(f"Material aliases unexpectedly contain only {len(material)} entries")
    return legacy, material


def targets_from_source() -> list[str]:
    _, material = alias_maps_from_source()
    return sorted(set(material.values()) | set(EXTRA_RESOURCE_SYMBOLS.values()))


def download(name: str) -> tuple[str, int]:
    url = BASE_URL.format(name=name)
    error: Exception | None = None
    for attempt in range(1, 4):
        try:
            request = urllib.request.Request(
                url, headers={"User-Agent": "AppAo-MaterialSymbols-Downloader/1.0"}
            )
            with urllib.request.urlopen(request, timeout=25) as response:
                data = response.read()
            root = ET.fromstring(data)
            if not root.tag.endswith("svg") or not any(
                element.tag.rsplit("}", 1)[-1] == "path" for element in root.iter()
            ):
                raise ValueError("response is not a Material symbol SVG with a path")
            OUTPUT.mkdir(parents=True, exist_ok=True)
            (OUTPUT / f"{name}.svg").write_bytes(data)
            return name, len(data)
        except Exception as exc:
            error = exc
            if attempt < 3:
                time.sleep(attempt)
    raise RuntimeError(f"Failed downloading Material Symbols Rounded icon '{name}' from {url}: {error}")


def build_vector_drawable(
    symbol_name: str,
    svg_path: Path,
    size_dp: int = 24,
    color_ref: str = "@color/iconTint",
) -> str:
    root = ET.fromstring(svg_path.read_bytes())
    raw_view_box = root.attrib.get("viewBox", "0 0 24 24").replace(",", " ").split()
    if len(raw_view_box) != 4:
        raise ValueError(f"Unexpected SVG viewBox for {symbol_name}: {raw_view_box}")
    min_x, min_y, width, height = (float(value) for value in raw_view_box)
    if width <= 0 or height <= 0:
        raise ValueError(f"Invalid SVG viewport for {symbol_name}: {raw_view_box}")

    paths = [element for element in root.iter() if element.tag.rsplit("}", 1)[-1] == "path"]
    if not paths:
        raise ValueError(f"No path found in Material SVG {symbol_name}")
    vector_paths: list[str] = []
    for element in paths:
        if element.attrib.get("transform"):
            raise ValueError(f"SVG path transform is not supported for {symbol_name}")
        path_data = element.attrib.get("d", "").strip()
        if not path_data:
            continue
        vector_paths.append(
            f'        <path android:fillColor="{color_ref}" android:pathData="{path_data}" />'
        )
    if not vector_paths:
        raise ValueError(f"No path data found in Material SVG {symbol_name}")

    # Google's 24px Material Symbols SVGs use viewBox 0 -960 960 960. The +960 Y
    # translation moves the original viewBox into VectorDrawable's 0..960 viewport.
    translate_x, translate_y = -min_x, -min_y
    if translate_x or translate_y:
        content = (
            f'    <group android:translateX="{translate_x:g}" android:translateY="{translate_y:g}">\n'
            + "\n".join("    " + item for item in vector_paths)
            + "\n    </group>"
        )
    else:
        content = "\n".join(vector_paths)

    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{size_dp}dp"\n'
        f'    android:height="{size_dp}dp"\n'
        f'    android:viewportWidth="{width:g}"\n'
        f'    android:viewportHeight="{height:g}">\n'
        f'{content}\n'
        '</vector>\n'
    )


def write_vector_alias(folder: Path, resource_name: str, vector_xml: str) -> None:
    """Overwrite only the mapped icon name; never remove any unrelated custom assets."""
    folder.mkdir(parents=True, exist_ok=True)
    destination = folder / f"{resource_name}.xml"
    # If the restored nodpi pack contains a PNG/WebP with this same generic name, remove it
    # so AAPT sees one unambiguous resource and the rounded vector becomes the selected asset.
    for extension in (".png", ".webp", ".jpg", ".jpeg", ".gif"):
        old_bitmap = folder / f"{resource_name}{extension}"
        if old_bitmap.exists():
            old_bitmap.unlink()
    destination.write_text(vector_xml, encoding="utf-8")


def generate_resource_vectors() -> int:
    legacy, material = alias_maps_from_source()
    # These resource names are purposely colored/brand artwork, not general monochrome UI icons.
    preserve = {"heart", "logo-google", "ad-block"}
    generated_candidates: dict[str, set[str]] = {}
    for alias, legacy_name in legacy.items():
        symbol = material.get(alias)
        if not symbol:
            continue
        if legacy_name in preserve or legacy_name.startswith("brand-") or legacy_name.startswith("search-"):
            continue
        resource_name = "ic_" + legacy_name.replace("-", "_")
        generated_candidates.setdefault(resource_name, set()).add(symbol)

    # Extra generic icon resources that are referenced independently of IconLoader.
    for resource_name, symbol in EXTRA_RESOURCE_SYMBOLS.items():
        generated_candidates.setdefault(resource_name, set()).add(symbol)

    # Some historical Ionicons aliases share a drawable name despite meaning different things.
    # Keep one sensible canonical Material symbol for XML resource callers; IconLoader's SVG path
    # still uses the exact individual alias for each call.
    resource_priority = {
        "ic_arrow_up_outline": "arrow_upward",
        "ic_create_outline": "edit",
        "ic_document_text_outline": "description",
        "ic_language_outline": "language",
        "ic_location_outline": "location_on",
        "ic_reader_outline": "article",
    }
    generated_symbols: dict[str, str] = {}
    for resource_name, choices in generated_candidates.items():
        if len(choices) == 1:
            generated_symbols[resource_name] = next(iter(choices))
        elif resource_priority.get(resource_name) in choices:
            generated_symbols[resource_name] = resource_priority[resource_name]
        else:
            raise ValueError(f"Icon resource {resource_name} has ambiguous Material symbols: {sorted(choices)}")

    for resource_name, symbol in sorted(generated_symbols.items()):
        svg_path = OUTPUT / f"{symbol}.svg"
        if not svg_path.is_file():
            raise FileNotFoundError(f"Downloaded SVG missing for {resource_name}: {svg_path}")
        vector_xml = build_vector_drawable(
            symbol,
            svg_path,
            size_dp=EXTRA_RESOURCE_SIZES.get(resource_name, 24),
            color_ref=EXTRA_RESOURCE_COLORS.get(resource_name, "@color/iconTint"),
        )
        # Existing layout/R.drawable references use these names. Both variants are emitted so the
        # generated, density-neutral vector also overrides the user's restored drawable-nodpi icon pack.
        write_vector_alias(RES_DRAWABLE, resource_name, vector_xml)
        write_vector_alias(RES_NODPI, resource_name, vector_xml)

    print(
        f"Generated/updated {len(generated_symbols)} Material Rounded VectorDrawables in drawable/ and drawable-nodpi/; "
        "unmapped brand/site/custom assets are retained."
    )
    return len(generated_symbols)


def main() -> int:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    names = targets_from_source()
    failures: list[str] = []
    downloaded: list[tuple[str, int]] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        futures = {pool.submit(download, name): name for name in names}
        for future in concurrent.futures.as_completed(futures):
            try:
                downloaded.append(future.result())
            except Exception as exc:
                failures.append(str(exc))
    if failures:
        for failure in sorted(failures):
            print(f"ERROR: {failure}", file=sys.stderr)
        return 1

    license_text = (
        "Material Symbols Rounded\n"
        "Copyright Google LLC\n"
        "Source: https://github.com/google/material-design-icons\n"
        f"Pinned source commit: {UPSTREAM_COMMIT}\n"
        "License: Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)\n"
    )
    (OUTPUT / "LICENSE.txt").write_text(license_text, encoding="utf-8")
    try:
        resource_count = generate_resource_vectors()
    except Exception as exc:
        print(f"ERROR generating VectorDrawable resources: {exc}", file=sys.stderr)
        return 1

    total_bytes = sum(size for _, size in downloaded)
    print(
        f"Downloaded {len(downloaded)} official Material Symbols Rounded SVGs ({total_bytes:,} bytes) "
        f"into {OUTPUT.relative_to(ROOT)}"
    )
    print(f"Generated {resource_count} rounded vector aliases; pinned upstream commit: {UPSTREAM_COMMIT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
