#!/usr/bin/env python3
"""Builds app/src/main/assets/foods/basic_foods.tsv from the USDA FoodData Central SR Legacy CSVs.

Stdlib only. See design/usda/README.md for where the CSVs come from.
Usage: python3 scripts/build-foods.py [--src DIR] [--out FILE] [--names FILE]
"""
import argparse
import csv
import re
import sys
from collections import Counter
from pathlib import Path

KEEP_CATEGORIES = [
    "Dairy and Egg Products", "Fats and Oils", "Poultry Products", "Fruits and Fruit Juices",
    "Pork Products", "Vegetables and Vegetable Products", "Nut and Seed Products", "Beef Products",
    "Beverages", "Finfish and Shellfish Products", "Legumes and Legume Products",
    "Lamb, Veal, and Game Products", "Cereal Grains and Pasta", "Breakfast Cereals",
    "Baked Products", "Sweets", "Snacks", "Sausages and Luncheon Meats",
]
# Dropped case-insensitively; all-caps brand names (ALL_CAPS) are dropped case-sensitively.
ALL_CAPS = re.compile(r"[A-Z]{3,}")
USDA_NOTE = re.compile(r"\s*\(Includes foods for USDA's Food Distribution Program\)")
DROP_ANY_CASE = re.compile(
    r"babyfood|infant|fast food|restaurant|industrial|commercial(?!ly prepared)|USDA commodity|"
    r"school lunch|formulated bar|\bNFS\b|\bUPC\b|\bNLEA\b|\bunprepared\b|\bUSDA\b|mission|"
    r"Beerwurst|Bockwurst|Braunschweiger|Thuringer|Knackwurst|Mortadella|Headcheese|Blood sausage|Whale|Seal,|Walrus|"
    r"Caribou|Moose|Ostrich|Emu\b|Squirrel|Beaver|Muskrat|Opossum|Raccoon|Oopah|Archway|Vitasoy|Oscar Mayer|Pepperidge|Pillsbury|George Weston|Martha White|Kraft|Interstate Brands|"
    r"Mary's Gone|Sage Valley|alcoholic|Navajo|Alaska Native|Hopi|Apache|Shoshone|Makah|Latino|Hawaii|"
    r"carcass|composite|with added solution|as purchased|\bjunior\b|\bstrained\b", re.IGNORECASE)
REMOVE_FRAGMENTS = [
    ", broilers or fryers", ", broiler or fryers", ", skinless, boneless", ", meat only", ", separable lean only", ", separable lean and fat",
    ", all commercial varieties", ", mature seeds", ", without salt", ", regular", ", enriched",
    ", with added vitamin D", ", with added vitamin A and vitamin D", ", fluid", ", ready-to-eat",
    ", with salt", ", without added vitamin A and vitamin D", ", without added vitamin D", ", without added ascorbic acid", ", without added salt", ", includes foods for USDA's Food Distribution Program", ", trimmed to 0\" fat",
    ", trimmed to 1/8\" fat", ", trimmed to 1/4\" fat", ", drained", ", prepared", ", year round average",
    ", salt added in processing", ", not fortified", ", commercially prepared", ", unenriched",
    ", all varieties", ", fresh", ", boneless", ", bone-in", ", separable lean and fat",
    ", retail cuts", ", composite of trimmed retail cuts", ", all classes", ", from whole bird", ", cooked as purchased", ", all types", ", plain", ", NFS",
]
# Variants that bloat the list without helping a search; a food listed in names.tsv is never dropped.
NOISE_VARIANT = re.compile(
    r"refrigerated dough|from mix|dry mix|dehydrated|freeze-dried|heavy syrup|light syrup|extra light syrup|"
    r"juice pack|water pack|with added salt|low sodium|sprouted|toasted|rehydrated|from recipe|made with|"
    r"prepared with|not reconstituted|undiluted|reduced sodium|dietetic|sugar free|fat-free|fat free|"
    r"unsweetened, frozen|solids and liquids|home-prepared|home prepared|ready-to-serve|ready-to-drink|"
    r"cooked as purchased|unheated|no-bake|leavening|enriched|unenriched|fortified|\bwith added\b|"
    r"frozen concentrate|diluted|reduced calorie|reduced-calorie|low fat|lowfat|low-fat|unsalted|sulfured|homemade|"
    r"\bcanned\b.*\bdrained\b.*\brinsed\b|\bflavored\b|\bflavor\b", re.IGNORECASE)
MAX_PARTS = 4
PIECE_PRIORITY = ["medium", "large", "whole", "piece", "fruit", "egg", "small", "slice"]
PREFERRED_UNITS = ["cup", "tbsp", "tsp", "fl oz", "serving", "slice", "can", "bar", "scoop"]
LABEL_ALIASES = {"tablespoon": "tbsp", "teaspoon": "tsp", "pieces": "piece", "slices": "slice"}
MAX_OTHER_PORTIONS = 4
ML_PER_CUP = 236.588
ML_PER_FL_OZ = 29.5735
ENERGY_KJ_PER_KCAL = 4.184
NUTRIENT_NUMBERS = {"203": "protein", "204": "fat", "205": "carbs", "291": "fiber",
                    "208": "kcal", "958": "kcal958", "957": "kcal957", "268": "kj"}
COLUMNS = ["usdaId", "name", "aliases", "protein", "carbs", "fat", "fiber", "kcal", "mlDensity",
           "pieceGrams", "portions"]


def load_csv(path):
    with open(path, newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def categories(src):
    """Returns {category id: description} for the categories we keep."""
    return {row["id"]: row["description"] for row in load_csv(src / "food_category.csv")
            if row["description"] in KEEP_CATEGORIES}


def description_dropped(description):
    return bool(DROP_ANY_CASE.search(description)) or bool(ALL_CAPS.search(description))


def nutrients(src, food_ids):
    """Returns {fdc_id: {protein, carbs, fat, fiber, kcal}} per 100 g; foods lacking P/C/F are absent."""
    number_by_id = {row["id"]: row["nutrient_nbr"] for row in load_csv(src / "nutrient.csv")}
    raw = {}
    for row in load_csv(src / "food_nutrient.csv"):
        fdc_id = row["fdc_id"]
        if fdc_id not in food_ids:
            continue
        key = NUTRIENT_NUMBERS.get(number_by_id.get(row["nutrient_id"], ""))
        if key is None or row["amount"] == "":
            continue
        raw.setdefault(fdc_id, {})[key] = max(0.0, float(row["amount"]))
    result = {}
    for fdc_id, values in raw.items():
        if not all(key in values for key in ("protein", "fat", "carbs")):
            continue
        kcal = values.get("kcal", values.get("kcal958", values.get("kcal957")))
        if kcal is None and "kj" in values:
            kcal = values["kj"] / ENERGY_KJ_PER_KCAL
        result[fdc_id] = {"protein": values["protein"], "carbs": values["carbs"], "fat": values["fat"],
                          "fiber": values.get("fiber", 0.0), "kcal": kcal}
    return result


def simplify_label(text):
    text = re.sub(r"\(.*?\)", "", text).split(",")[0].strip().lower()
    text = re.sub(r"\s+", " ", text)
    return LABEL_ALIASES.get(text, text)


def portions(src, food_ids):
    """Returns {fdc_id: [(label, grams per 1 unit), ...]} in USDA order."""
    units = {row["id"]: row["name"] for row in load_csv(src / "measure_unit.csv")}
    result = {}
    for row in load_csv(src / "food_portion.csv"):
        fdc_id = row["fdc_id"]
        if fdc_id not in food_ids:
            continue
        try:
            amount, grams = float(row["amount"]), float(row["gram_weight"])
        except ValueError:
            continue
        if amount <= 0 or grams <= 0:
            continue
        unit = units.get(row["measure_unit_id"], "undetermined")
        label = simplify_label(row["modifier"] if unit == "undetermined" else unit)
        if not label or not re.fullmatch(r"[a-z][a-z \-]{0,19}", label):
            continue
        result.setdefault(fdc_id, []).append((label, grams / amount))
    return result


def pick_piece(description, food_portions):
    priority = list(PIECE_PRIORITY)
    if description.startswith("Egg"):
        priority.remove("large")
        priority.insert(0, "large")  # a "large" egg is the everyday egg
    for wanted in priority:
        if wanted == "slice" and any(label in PIECE_PRIORITY[:-1] for label, _ in food_portions):
            continue
        for label, grams in food_portions:
            if label == wanted:
                return grams
    return None


def pick_density(food_portions):
    for label, grams in food_portions:
        if label == "cup":
            return grams / ML_PER_CUP
        if label == "fl oz":
            return grams / ML_PER_FL_OZ
    return None


def pick_others(food_portions):
    seen, others = set(), []
    for label, grams in food_portions:
        if label in seen or label in ("oz", "lb", "g", "ml"):
            continue
        seen.add(label)
        others.append((label, grams))
    others.sort(key=lambda item: PREFERRED_UNITS.index(item[0]) if item[0] in PREFERRED_UNITS else len(PREFERRED_UNITS))
    return others[:MAX_OTHER_PORTIONS]


def short_name(description):
    name = description
    for fragment in REMOVE_FRAGMENTS:
        name = name.replace(fragment, "")
    name = re.sub(r", trimmed to [^,]*? fat|, all grades|, (choice|select|prime)\b", "", name)
    name = re.sub(r"^(Beverages|Snacks), ", "", name)
    name = re.sub(r"\s+", " ", name)
    name = re.sub(r",\s*,", ",", name).strip(" ,")
    name = name.replace("\t", " ").replace("|", " ")
    return name[:1].upper() + name[1:]


def load_names(path):
    names = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        cells = line.split("\t")
        if len(cells) < 2:
            continue
        aliases = [alias.strip() for alias in cells[2].split("|")] if len(cells) > 2 else []
        names[cells[0].strip()] = (cells[1].strip(), [alias for alias in aliases if alias])
    return names


def number(value):
    text = f"{value:.2f}".rstrip("0").rstrip(".")
    return text if text else "0"


def main():
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--src", type=Path, default=root / "design/usda/sr_legacy")
    parser.add_argument("--out", type=Path, default=root / "app/src/main/assets/foods/basic_foods.tsv")
    parser.add_argument("--names", type=Path, default=root / "design/usda/names.tsv")
    args = parser.parse_args()

    kept = categories(args.src)
    names = load_names(args.names)
    foods = {}
    for row in load_csv(args.src / "food.csv"):
        if row["data_type"] != "sr_legacy_food" or row["food_category_id"] not in kept:
            continue
        description = USDA_NOTE.sub("", row["description"])
        row["description"] = description
        if description not in names and description_dropped(description):
            continue
        foods[row["fdc_id"]] = row
    facts = nutrients(args.src, set(foods))
    food_portions = portions(args.src, set(facts))

    matched = set()
    candidates = {}  # name -> (rank key, row dict)
    for fdc_id, row in foods.items():
        if fdc_id not in facts:
            continue
        description = row["description"]
        listed = description in names
        if listed:
            matched.add(description)
            name, aliases = names[description]
        else:
            name, aliases = short_name(description), []
            if NOISE_VARIANT.search(description):
                continue
            if name.count(",") + 1 > MAX_PARTS:
                continue
        name = name.replace("\t", " ").replace("|", " ")
        own = food_portions.get(fdc_id, [])
        liquid = kept[row["food_category_id"]] == "Beverages" or re.search(r"milk|juice|\boil\b", name, re.IGNORECASE)
        density = pick_density(own) if liquid else None
        piece = pick_piece(description, own)
        others = pick_others(own)
        entry = {"id": int(fdc_id), "name": name, "aliases": aliases, "facts": facts[fdc_id],
                 "density": density, "piece": piece, "others": others, "category": kept[row["food_category_id"]]}
        rank = (not listed, -len(own), int(fdc_id))
        current = candidates.get(name.lower())
        if current is None or rank < current[0]:
            candidates[name.lower()] = (rank, entry)

    entries = sorted((entry for _, entry in candidates.values()), key=lambda entry: entry["name"].lower())
    args.out.parent.mkdir(parents=True, exist_ok=True)
    lines = ["#ironquest-basic-foods\t1", "\t".join(COLUMNS)]
    for entry in entries:
        facts_ = entry["facts"]
        portion_text = "|".join(f"{label}={number(grams)}" for label, grams in entry["others"])
        lines.append("\t".join([
            str(entry["id"]), entry["name"], "|".join(entry["aliases"]),
            number(facts_["protein"]), number(facts_["carbs"]), number(facts_["fat"]), number(facts_["fiber"]),
            "" if facts_["kcal"] is None else number(facts_["kcal"]),
            "" if entry["density"] is None else number(entry["density"]),
            "" if entry["piece"] is None else number(entry["piece"]),
            portion_text]))
    args.out.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"Wrote {len(entries)} foods to {args.out} ({args.out.stat().st_size / 1024:.1f} KB)")
    for category, count in sorted(Counter(entry["category"] for entry in entries).items()):
        print(f"  {category}: {count}")
    for description in names:
        if description not in matched:
            print(f"WARNING: names.tsv matched nothing: {description}", file=sys.stderr)


if __name__ == "__main__":
    main()
