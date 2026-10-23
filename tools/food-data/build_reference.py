"""
Builds supabase/functions/analyze-meal/reference_foods.json from USDA FoodData Central
"SR Legacy" (April 2018, public domain): lab-analysed values per 100 g for ~7,800 foods.

Download (once) into tools/food-data/.cache and unzip:
  https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip
Then:  python tools/food-data/build_reference.py

Output rows: [fdc_id, description, kcal, protein, carbs, fat, fibre, sugar] (per 100 g).
"""
import csv
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, ".cache", "FoodData_Central_sr_legacy_food_csv_2018-04")
OUT = os.path.join(HERE, "..", "..", "supabase", "functions", "analyze-meal", "reference_foods.json")

# Nutrient ids in FoodData Central.
ENERGY, PROTEIN, FAT, CARBS, FIBRE, SUGAR = "1008", "1003", "1004", "1005", "1079", "2000"
WANTED = {ENERGY, PROTEIN, FAT, CARBS, FIBRE, SUGAR}

# Categories that are not meals people photograph.
SKIP_CATEGORIES = {"Baby Foods", "American Indian/Alaska Native Foods"}


def main():
    categories = {}
    with open(os.path.join(SRC, "food_category.csv"), encoding="utf-8") as f:
        for row in csv.DictReader(f):
            categories[row["id"]] = row["description"]

    foods = {}
    with open(os.path.join(SRC, "food.csv"), encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if categories.get(row["food_category_id"]) in SKIP_CATEGORIES:
                continue
            foods[row["fdc_id"]] = {"desc": row["description"].strip(), "n": {}}

    with open(os.path.join(SRC, "food_nutrient.csv"), encoding="utf-8") as f:
        for row in csv.DictReader(f):
            food = foods.get(row["fdc_id"])
            if food is not None and row["nutrient_id"] in WANTED and row["amount"]:
                food["n"][row["nutrient_id"]] = float(row["amount"])

    rows = []
    for fdc_id, food in foods.items():
        n = food["n"]
        if ENERGY not in n or PROTEIN not in n or FAT not in n or CARBS not in n:
            continue  # incomplete entries are not used for grounding
        r1 = lambda v: round(v, 1)
        rows.append([
            int(fdc_id), food["desc"], round(n[ENERGY]), r1(n[PROTEIN]), r1(n[CARBS]), r1(n[FAT]),
            r1(n.get(FIBRE, 0.0)), r1(n.get(SUGAR, 0.0)),
        ])
    rows.sort(key=lambda r: r[0])
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({
            "source": "USDA FoodData Central, SR Legacy (2018-04), public domain",
            "columns": ["fdc_id", "description", "kcal", "protein_g", "carbs_g", "fat_g", "fiber_g", "sugar_g"],
            "per": "100 g",
            "foods": rows,
        }, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(rows)} foods -> {os.path.relpath(OUT)} ({os.path.getsize(OUT) // 1024} KB)")


if __name__ == "__main__":
    main()
