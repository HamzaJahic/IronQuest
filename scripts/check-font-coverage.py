"""Fails when a character used in the app's code or strings was in an original font but not in its subset."""
import pathlib
import sys
import unicodedata

from fontTools.ttLib import TTFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
SKIPPED_SCRIPTS = ("CYRILLIC", "GREEK", "ARABIC", "HEBREW", "CJK", "HANGUL", "HIRAGANA", "KATAKANA", "THAI", "DEVANAGARI")


def used_characters() -> set[int]:
    sources = list((ROOT / "app/src/main/java").rglob("*.kt"))
    sources += list((ROOT / "app/src/main/res").glob("values*/*.xml"))
    used = set()
    for path in sources:
        for char in path.read_text(encoding="utf-8"):
            code = ord(char)
            if code <= 0x7E:
                continue
            name = unicodedata.name(char, "")
            if name.startswith(SKIPPED_SCRIPTS):
                continue  # deliberately left to the system font
            used.add(code)
    return used


def main() -> int:
    used = used_characters()
    failures = []
    for original_path in sorted((ROOT / "design/fonts").glob("*.ttf")):
        original = TTFont(original_path).getBestCmap()
        subset = TTFont(ROOT / "app/src/main/res/font" / original_path.name).getBestCmap()
        missing = sorted(code for code in used if code in original and code not in subset)
        if missing:
            failures.append(f"{original_path.name}: " + " ".join(f"U+{code:04X} {chr(code)}" for code in missing))
        if "tnum" not in feature_tags(ROOT / "app/src/main/res/font" / original_path.name) and "tnum" in feature_tags(original_path):
            failures.append(f"{original_path.name}: lost the tnum (tabular figures) feature")
    print(f"{len(used)} non-ASCII characters in use; " + ("OK" if not failures else "MISSING:"))
    for line in failures:
        print("  " + line)
    return 1 if failures else 0


def feature_tags(path: pathlib.Path) -> set[str]:
    font = TTFont(path)
    if "GSUB" not in font and "GPOS" not in font:
        return set()
    tags = set()
    for table_tag in ("GSUB", "GPOS"):
        if table_tag in font and font[table_tag].table.FeatureList:
            tags |= {record.FeatureTag for record in font[table_tag].table.FeatureList.FeatureRecord}
    return tags


if __name__ == "__main__":
    sys.exit(main())
