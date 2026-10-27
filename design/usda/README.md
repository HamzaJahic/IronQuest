# Built-in food data

`app/src/main/assets/foods/basic_foods.tsv` is generated from USDA FoodData Central, SR Legacy.

1. Download "SR Legacy" (CSV) from https://fdc.nal.usda.gov/download-datasets (`FoodData_Central_sr_legacy_food_csv_2018-04.zip`, ~6 MB).
2. Unzip into `design/usda/sr_legacy/` (needs `food.csv`, `food_nutrient.csv`, `nutrient.csv`, `food_portion.csv`, `measure_unit.csv`, `food_category.csv`).
3. Run `python3 scripts/build-foods.py` (stdlib only). It prints the food count, per-category counts, the output size and a warning for every `names.tsv` line that matched nothing.

`names.tsv` maps an exact USDA description to a short name and `|`-separated search aliases. Foods listed there are always kept and win name clashes. Descriptions are matched after the "(Includes foods for USDA's Food Distribution Program)" note is removed.

The unzipped CSVs are the downloaded originals (~40 MB). They live outside `app/`, so they never reach the APK, and can be deleted once the TSV is generated.

Public domain (CC0 1.0), U.S. Department of Agriculture, Agricultural Research Service. FoodData Central, SR Legacy (2018).
