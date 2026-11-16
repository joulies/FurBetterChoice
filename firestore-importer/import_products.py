#!/usr/bin/env python3
"""
FurBetterChoice — One-Click Excel to Firestore Product Importer
Reads Products, Nutrition, and Ingredients sheets from:
  Master Product List - FurBetterChoice.xlsx
Normalizes field names, nutrient keys, and energy units (kcal/kg, kcal/100g,
kcal/bag, etc.), resolves duplicate/anomalous rows, and uploads to Firestore
under `products/{productId}` using pure-Python OAuth2 + Firestore REST API v1
(no grpc/native DLLs required, immune to Windows Application Control).
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import openpyxl

BASE_DIR = Path(__file__).resolve().parent
DEFAULT_EXCEL_PATH = Path(r"C:\Users\damlo\Downloads\Master Product List - FurBetterChoice.xlsx")
DEFAULT_COLLECTION = "products"
BATCH_SIZE = 200

# Canonical nutrient key mapping matching FirestoreRepository.kt & AdminDashboardScreen.kt
NUTRIENT_MAP: dict[str, tuple[str, str]] = {
    "crude protein": ("crudeProtein", "Crude Protein"),
    "protein": ("crudeProtein", "Crude Protein"),
    "proteins": ("crudeProtein", "Crude Protein"),
    "crude fat": ("crudeFat", "Crude Fat"),
    "fat": ("crudeFat", "Crude Fat"),
    "fats": ("crudeFat", "Crude Fat"),
    "crude fiber": ("crudeFiber", "Crude Fiber"),
    "crude fibre": ("crudeFiber", "Crude Fiber"),
    "crude fibres": ("crudeFiber", "Crude Fiber"),
    "fiber": ("crudeFiber", "Crude Fiber"),
    "fibre": ("crudeFiber", "Crude Fiber"),
    "crude ash": ("ash", "Crude Ash"),
    "ash": ("ash", "Crude Ash"),
    "moisture": ("moisture", "Moisture"),
    "calcium": ("calcium", "Calcium"),
    "phosphorus": ("phosphorus", "Phosphorus"),
    "total phosphorus": ("phosphorus", "Phosphorus"),
    "taurine": ("taurine", "Taurine"),
    "salt": ("salt", "Salt"),
    "omega-3": ("omega3", "Omega-3"),
    "omega 3": ("omega3", "Omega-3"),
    "omega3": ("omega3", "Omega-3"),
    "omega-6": ("omega6", "Omega-6"),
    "omega 6": ("omega6", "Omega-6"),
    "omega6": ("omega6", "Omega-6"),
    "omega-6 fatty acids": ("omega6FattyAcids", "Omega-6 Fatty Acids"),
    "omega 6 fatty acids": ("omega6FattyAcids", "Omega-6 Fatty Acids"),
    "carbohydrates": ("carbohydrates", "Carbohydrates"),
    "nfe": ("carbohydrates", "Carbohydrates"),
}

ENERGY_KEYWORDS = (
    "metabolizable",
    "metabolized",
    "metabolisable",
    "metabolised",
    "calorie",
    "energy",
)

NON_CONVERTIBLE_UNIT_TOKENS = (
    "cup",
    "can",
    "pouch",
    "treat",
    "piece",
    "biscuit",
    "chew",
    "stick",
    "serving",
    "tbsp",
    "tsp",
    "bag",
    "packet",
    "tin",
    "tub",
)


def clean_str(val: Any) -> str:
    if val is None:
        return ""
    return re.sub(r"\s+", " ", str(val)).strip()


def clean_barcode(val: Any) -> str:
    if val is None:
        return ""
    if isinstance(val, bool):
        return ""
    if isinstance(val, int):
        return str(val)
    if isinstance(val, float):
        if val.is_integer():
            return str(int(val))
        return clean_str(val)
    s = clean_str(val)
    if re.fullmatch(r"\d+\.0+", s):
        s = s.split(".", 1)[0]
    return s


def clean_breed(val: Any) -> str:
    s = clean_str(val)
    if s.lower() == "all breed":
        return "All Breed"
    return s


def to_number(val: Any) -> float | None:
    if val is None or isinstance(val, bool):
        return None
    if isinstance(val, (int, float)):
        f = float(val)
        return f if f == f else None
    s = clean_str(val).replace(",", "")
    if not s:
        return None
    m = re.search(r"[-+]?\d+(?:\.\d+)?", s)
    if not m:
        return None
    try:
        return float(m.group(0))
    except ValueError:
        return None


def is_energy_nutrient(nutrient_name: str) -> bool:
    nl = nutrient_name.lower().strip()
    return nl == "me" or any(kw in nl for kw in ENERGY_KEYWORDS)


def convert_energy_to_kcal_per_kg(value: float, unit: str) -> tuple[bool, float | None]:
    """
    Returns (is_weight_convertible, kcal_per_kg_value).
    Matches NutritionEnergyHelper.kt conversion factors.
    """
    u_lower = unit.lower().strip()
    if any(tok in u_lower for tok in NON_CONVERTIBLE_UNIT_TOKENS):
        return False, None

    u_clean = (
        re.sub(r"\s+", "", u_lower)
        .replace("(me)", "")
        .replace("(dm)", "")
        .replace("drymatter", "")
        .replace("as-fed", "")
        .replace("asfed", "")
        .replace("calculated", "")
    )
    if u_clean in ("kcal/kg", "kcalkg", "kcalperkg", ""):
        return True, value
    if u_clean in ("kcal/100g", "kcal100g", "kcalper100g"):
        return True, value * 10.0
    if u_clean in ("kcal/g", "kcalg", "kcalperg"):
        return True, value * 1000.0
    if u_clean in ("kj/kg", "kjkg", "kjperkg"):
        return True, value / 4.184
    if u_clean in ("kj/100g", "kj100g", "kjper100g"):
        return True, (value * 10.0) / 4.184
    if u_clean in ("mj/kg", "mjkg", "mjperkg"):
        return True, (value * 1000.0) / 4.184
    if u_clean in ("kcal/lb", "kcallb", "kcalperlb"):
        return True, value * 2.20462262
    if u_clean in ("kcal/oz", "kcaloz", "kcalperoz"):
        return True, value * 35.27396195
    return False, None


def resolve_energy_entries(
    product_id: str,
    entries: list[dict[str, Any]],
    warnings: list[str],
) -> tuple[dict[str, Any] | None, float | None, str | None, dict[str, Any] | None]:
    """
    Resolves one or more energy rows for a product.
    Returns:
      - nutrition_entry: dict for nutrition["metabolizableEnergy"]
      - root_me_value: float for root metabolizableEnergy (normalized kcal/kg when convertible)
      - root_me_unit: str for root metabolizableEnergyUnit
      - extra_serving_entry: optional serving-unit energy when both convertible & serving exist
    """
    if not entries:
        return None, None, None, None

    enriched = []
    for idx, e in enumerate(entries):
        convertible, kcal_kg = convert_energy_to_kcal_per_kg(e["value"], e["unit"])
        is_realistic = convertible and kcal_kg is not None and (200.0 <= kcal_kg <= 8000.0)
        is_kcal_kg = re.sub(r"\s+", "", e["unit"].lower()) in ("kcal/kg", "kcalkg", "kcalperkg")
        enriched.append({
            **e,
            "convertible": convertible,
            "kcal_kg": kcal_kg,
            "realistic": is_realistic,
            "is_kcal_kg": is_kcal_kg,
            "idx": idx,
        })

    # Rank: realistic convertible (prefer kcal/kg if tied) > any convertible > serving-unit
    ranked = sorted(
        enriched,
        key=lambda x: (
            1 if x["realistic"] else 0,
            1 if x["convertible"] else 0,
            1 if x["is_kcal_kg"] else 0,
            -x["idx"],
        ),
        reverse=True,
    )
    chosen = ranked[0]

    if len(enriched) > 1:
        others = [
            f"{o['value']} {o['unit']} (row {o['row']})"
            for o in enriched
            if o["idx"] != chosen["idx"]
        ]
        warnings.append(
            f"{product_id}: Multiple energy rows found -> kept {chosen['value']} {chosen['unit']} "
            f"(row {chosen['row']}); skipped/secondary: {', '.join(others)}"
        )

    if chosen["convertible"] and chosen["kcal_kg"] is not None:
        kcal_kg_val = round(chosen["kcal_kg"], 2)
        if kcal_kg_val < 200.0:
            warnings.append(
                f"{product_id}: '{chosen['raw_name']}' is {chosen['value']} {chosen['unit']} "
                f"({kcal_kg_val} kcal/kg), which is unusually low — verify whether Excel unit should be kcal/100g."
            )
        elif kcal_kg_val > 8000.0:
            warnings.append(
                f"{product_id}: '{chosen['raw_name']}' converts to {kcal_kg_val} kcal/kg, which is unusually high — verify Excel unit."
            )
        root_me_val = kcal_kg_val
        root_me_unit = "kcal/kg"
    else:
        root_me_val = chosen["value"]
        root_me_unit = chosen["unit"] or "serving"
        warnings.append(
            f"{product_id}: Energy '{chosen['value']} {chosen['unit']}' is a serving-only unit "
            f"(kept as '{root_me_unit}', not converted to kcal/kg)."
        )

    nutrition_entry = {
        "name": "Metabolizable Energy",
        "value": chosen["value"],
        "unit": chosen["unit"] or "kcal/kg",
    }

    # Preserve secondary serving-unit value (e.g. P0049: 96.0 kcal/pouch (120)) on root field
    extra_serving = None
    if chosen["convertible"]:
        serving_item = next((o for o in enriched if not o["convertible"]), None)
        if serving_item is not None:
            extra_serving = {
                "name": serving_item["raw_name"],
                "value": serving_item["value"],
                "unit": serving_item["unit"],
            }

    return nutrition_entry, root_me_val, root_me_unit, extra_serving


def slugify_nutrient_key(name: str) -> str:
    parts = re.findall(r"[A-Za-z0-9]+", name)
    if not parts:
        return "customNutrient"
    first = parts[0].lower()
    rest = [p.capitalize() for p in parts[1:]]
    return first + "".join(rest)


def load_workbook_products(excel_path: Path) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    wb = openpyxl.load_workbook(excel_path, data_only=True)
    required_sheets = {"Products", "Nutrition", "Ingredients"}
    missing = required_sheets - set(wb.sheetnames)
    if missing:
        raise ValueError(f"Missing required sheet(s) in workbook: {', '.join(sorted(missing))}")

    ws_products = wb["Products"]
    ws_nutrition = wb["Nutrition"]
    ws_ingredients = wb["Ingredients"]

    warnings: list[str] = []

    # 1. Read Ingredients map
    ingredients_by_pid: dict[str, str] = {}
    for r in range(2, ws_ingredients.max_row + 1):
        pid = clean_str(ws_ingredients.cell(r, 1).value)
        ing = clean_str(ws_ingredients.cell(r, 2).value)
        if pid and ing:
            ingredients_by_pid[pid] = ing

    # 2. Read Nutrition rows grouped by productId
    nutrients_by_pid: dict[str, dict[str, dict[str, Any]]] = defaultdict(dict)
    energy_by_pid: dict[str, list[dict[str, Any]]] = defaultdict(list)

    for r in range(2, ws_nutrition.max_row + 1):
        pid = clean_str(ws_nutrition.cell(r, 1).value)
        raw_name = clean_str(ws_nutrition.cell(r, 2).value)
        raw_val = ws_nutrition.cell(r, 3).value
        raw_unit = clean_str(ws_nutrition.cell(r, 4).value)

        if not pid and not raw_name and raw_val is None:
            continue
        if not pid or not raw_name:
            warnings.append(f"Nutrition row {r}: missing productId or nutrient name — skipped.")
            continue

        num_val = to_number(raw_val)
        if num_val is None or num_val <= 0.0:
            warnings.append(f"Nutrition row {r} ({pid}, {raw_name}): non-positive or invalid value '{raw_val}' — skipped.")
            continue

        if is_energy_nutrient(raw_name):
            energy_by_pid[pid].append({
                "row": r,
                "raw_name": raw_name,
                "value": num_val,
                "unit": raw_unit or "kcal/kg",
            })
            continue

        lookup = raw_name.lower()
        if lookup in NUTRIENT_MAP:
            canon_key, canon_name = NUTRIENT_MAP[lookup]
        else:
            canon_key = slugify_nutrient_key(raw_name)
            canon_name = raw_name

        unit = raw_unit or "%"
        if canon_key in nutrients_by_pid[pid]:
            prev = nutrients_by_pid[pid][canon_key]
            warnings.append(
                f"{pid}: Duplicate nutrient '{canon_key}' ({prev['value']} {prev['unit']} vs {num_val} {unit} at row {r}) — keeping {num_val} {unit}."
            )
        nutrients_by_pid[pid][canon_key] = {
            "name": canon_name,
            "value": num_val,
            "unit": unit,
        }

    # 3. Read Products sheet
    headers = [clean_str(ws_products.cell(1, c).value) for c in range(1, ws_products.max_column + 1)]
    col_idx = {h: i + 1 for i, h in enumerate(headers) if h}

    def get_col(row_num: int, name: str) -> Any:
        idx = col_idx.get(name)
        return ws_products.cell(row_num, idx).value if idx else None

    products_out: list[dict[str, Any]] = []
    skipped_empty = 0
    missing_barcode_count = 0
    energy_convertible_count = 0
    energy_serving_only_count = 0

    for r in range(2, ws_products.max_row + 1):
        pid = clean_str(get_col(r, "productId"))
        barcode = clean_barcode(get_col(r, "barcode"))
        brand = clean_str(get_col(r, "brand"))
        product_name = clean_str(get_col(r, "productName"))

        # Skip blank template rows (e.g. P0081-P0090 where only productId is populated)
        if not pid and not barcode and not product_name:
            continue
        if not barcode and not brand and not product_name:
            skipped_empty += 1
            continue

        if not pid:
            pid = barcode or f"PROD_{r}"
        if not barcode:
            missing_barcode_count += 1
            warnings.append(f"{pid} ({product_name}): missing barcode.")

        nutrition_dict = dict(nutrients_by_pid.get(pid, {}))
        energy_entry, root_me_val, root_me_unit, extra_serving = resolve_energy_entries(
            pid, energy_by_pid.get(pid, []), warnings
        )
        if energy_entry is not None:
            nutrition_dict["metabolizableEnergy"] = energy_entry
            if root_me_unit == "kcal/kg":
                energy_convertible_count += 1
            else:
                energy_serving_only_count += 1

        doc: dict[str, Any] = {
            "productId": pid,
            "barcode": barcode or pid,
            "brand": brand,
            "productName": product_name,
            "imageUrl": clean_str(get_col(r, "imageUrl")),
            "pet": clean_str(get_col(r, "pet")),
            "breed": clean_breed(get_col(r, "breed")),
            "lifeStage": clean_str(get_col(r, "lifeStage")),
            "foodType": clean_str(get_col(r, "foodType")),
            "packagingMaterial": clean_str(get_col(r, "packagingMaterial")),
            "packagingType": clean_str(get_col(r, "packagingType")),
            "packagingRecyclability": clean_str(get_col(r, "packagingRecyclability")),
            "packagingBiodegradability": clean_str(get_col(r, "packagingBiodegradability")),
            "ingredients": ingredients_by_pid.get(pid, ""),
            "nutrition": nutrition_dict,
        }

        if root_me_val is not None and root_me_unit is not None:
            doc["metabolizableEnergy"] = root_me_val
            doc["metabolizableEnergyUnit"] = root_me_unit
        if extra_serving is not None:
            doc["energyPerServing"] = extra_serving

        products_out.append(doc)

    stats = {
        "productsParsed": len(products_out),
        "skippedEmptyRows": skipped_empty,
        "missingBarcodeCount": missing_barcode_count,
        "productsWithNutrition": sum(1 for p in products_out if p["nutrition"]),
        "productsWithIngredients": sum(1 for p in products_out if p["ingredients"]),
        "productsWithConvertibleEnergy": energy_convertible_count,
        "productsWithServingOnlyEnergy": energy_serving_only_count,
        "warnings": warnings,
    }
    return products_out, stats


# ==============================================================================
# Pure-Python Service Account OAuth2 + Firestore REST v1 Client (zero native DLLs)
# ==============================================================================

def _read_der_tlv(data: bytes, offset: int) -> tuple[int, bytes, int]:
    tag = data[offset]
    offset += 1
    length = data[offset]
    offset += 1
    if length & 0x80:
        num_bytes = length & 0x7F
        length = int.from_bytes(data[offset : offset + num_bytes], "big")
        offset += num_bytes
    value = data[offset : offset + length]
    return tag, value, offset + length


def _parse_der_sequence(data: bytes) -> list[tuple[int, bytes]]:
    tag, inner, _ = _read_der_tlv(data, 0)
    if tag != 0x30:
        raise ValueError("Invalid ASN.1 DER sequence in service account private key")
    items: list[tuple[int, bytes]] = []
    pos = 0
    while pos < len(inner):
        t, v, pos = _read_der_tlv(inner, pos)
        items.append((t, v))
    return items


def _extract_rsa_private_key(pem_text: str) -> tuple[int, int, int]:
    lines = [
        line.strip()
        for line in pem_text.strip().splitlines()
        if line.strip() and not line.strip().startswith("-----")
    ]
    der = base64.b64decode("".join(lines))
    if "BEGIN RSA PRIVATE KEY" in pem_text:
        rsa_der = der
    else:
        # PKCS#8 PrivateKeyInfo: SEQUENCE { version, algId, OCTET STRING(RSAPrivateKey) }
        top = _parse_der_sequence(der)
        if len(top) < 3 or top[2][0] != 0x04:
            raise ValueError("Unsupported PKCS#8 private key structure")
        rsa_der = top[2][1]

    seq = _parse_der_sequence(rsa_der)
    if len(seq) < 4:
        raise ValueError("Invalid RSAPrivateKey ASN.1 structure")
    n = int.from_bytes(seq[1][1], "big")
    e = int.from_bytes(seq[2][1], "big")
    d = int.from_bytes(seq[3][1], "big")
    return n, e, d


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def _sign_rs256(message: bytes, pem_text: str) -> bytes:
    n, e, d = _extract_rsa_private_key(pem_text)
    # DER DigestInfo prefix for SHA-256 + 32-byte digest
    digest_info = (
        bytes.fromhex("3031300d060960864801650304020105000420")
        + hashlib.sha256(message).digest()
    )
    k = (n.bit_length() + 7) // 8
    ps_len = k - len(digest_info) - 3
    if ps_len < 8:
        raise ValueError("RSA key size too small for SHA-256 PKCS#1 v1.5 signing")
    em = b"\x00\x01" + (b"\xff" * ps_len) + b"\x00" + digest_info
    sig_int = pow(int.from_bytes(em, "big"), d, n)
    if pow(sig_int, e, n).to_bytes(k, "big") != em:
        raise ValueError("RSA signature self-verification failed")
    return sig_int.to_bytes(k, "big")


def get_access_token(sa_info: dict[str, Any]) -> str:
    token_uri = sa_info.get("token_uri") or "https://oauth2.googleapis.com/token"
    client_email = sa_info["client_email"]
    private_key = sa_info["private_key"]
    now = int(time.time())

    header = {"alg": "RS256", "typ": "JWT"}
    if sa_info.get("private_key_id"):
        header["kid"] = sa_info["private_key_id"]

    claims = {
        "iss": client_email,
        "scope": "https://www.googleapis.com/auth/datastore",
        "aud": token_uri,
        "iat": now,
        "exp": now + 3600,
    }
    signing_input = (
        f"{_b64url(json.dumps(header, separators=(',', ':')).encode('utf-8'))}."
        f"{_b64url(json.dumps(claims, separators=(',', ':')).encode('utf-8'))}"
    ).encode("ascii")

    signature = _sign_rs256(signing_input, private_key)
    assertion = f"{signing_input.decode('ascii')}.{_b64url(signature)}"

    body = urllib.parse.urlencode({
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": assertion,
    }).encode("utf-8")

    req = urllib.request.Request(
        token_uri,
        data=body,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as err:
        detail = err.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"OAuth2 token request failed ({err.code}): {detail}") from err

    token = payload.get("access_token")
    if not token:
        raise RuntimeError(f"OAuth2 token response missing access_token: {payload}")
    return token


def to_firestore_value(val: Any) -> dict[str, Any]:
    if val is None:
        return {"nullValue": None}
    if isinstance(val, bool):
        return {"booleanValue": val}
    if isinstance(val, int):
        return {"integerValue": str(val)}
    if isinstance(val, float):
        return {"doubleValue": val}
    if isinstance(val, str):
        return {"stringValue": val}
    if isinstance(val, list):
        return {"arrayValue": {"values": [to_firestore_value(x) for x in val]}}
    if isinstance(val, dict):
        return {"mapValue": {"fields": {str(k): to_firestore_value(v) for k, v in val.items()}}}
    return {"stringValue": str(val)}


def upload_to_firestore(
    products: list[dict[str, Any]],
    key_path: Path,
    collection_name: str,
) -> int:
    sa_info = json.loads(key_path.read_text(encoding="utf-8"))
    project_id = sa_info.get("project_id")
    if not project_id:
        raise ValueError(f"Service account key {key_path.name} is missing 'project_id'.")

    print(f"Authenticating with Firebase project '{project_id}'...")
    access_token = get_access_token(sa_info)

    commit_url = (
        f"https://firestore.googleapis.com/v1/projects/{urllib.parse.quote(project_id)}"
        f"/databases/(default)/documents:commit"
    )
    doc_prefix = (
        f"projects/{project_id}/databases/(default)/documents/{collection_name}"
    )

    total_written = 0
    for i in range(0, len(products), BATCH_SIZE):
        chunk = products[i : i + BATCH_SIZE]
        writes = []
        for doc in chunk:
            doc_name = f"{doc_prefix}/{doc['productId']}"
            fields = {k: to_firestore_value(v) for k, v in doc.items()}
            writes.append({
                "update": {
                    "name": doc_name,
                    "fields": fields,
                },
                "updateMask": {
                    "fieldPaths": list(doc.keys()),
                },
                "updateTransforms": [
                    {
                        "fieldPath": "updatedAt",
                        "setToServerValue": "REQUEST_TIME",
                    }
                ],
            })

        req = urllib.request.Request(
            commit_url,
            data=json.dumps({"writes": writes}).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {access_token}",
                "Content-Type": "application/json",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                resp.read()
        except urllib.error.HTTPError as err:
            detail = err.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"Firestore commit failed ({err.code}): {detail}") from err

        total_written += len(chunk)
        print(f"  Committed batch: {total_written}/{len(products)} products...")

    return total_written


def find_service_account_key(explicit_key: str | None) -> Path | None:
    if explicit_key:
        p = Path(explicit_key).expanduser().resolve()
        return p if p.is_file() else None

    preferred = BASE_DIR / "serviceAccountKey.json"
    if preferred.is_file():
        return preferred

    candidates = sorted(BASE_DIR.glob("*firebase-adminsdk*.json"))
    if candidates:
        return candidates[0]

    json_candidates = [
        p for p in sorted(BASE_DIR.glob("*.json"))
        if p.name.lower() != "dry_run_preview.json"
    ]
    for p in json_candidates:
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
            if isinstance(data, dict) and data.get("type") == "service_account" and "private_key" in data:
                return p
        except Exception:
            continue
    return None


def find_excel_file(explicit_excel: str | None) -> Path | None:
    if explicit_excel:
        p = Path(explicit_excel).expanduser().resolve()
        return p if p.is_file() else None

    local_excels = [
        p for p in sorted(BASE_DIR.glob("*.xlsx"))
        if not p.name.startswith("~$")
    ]
    if local_excels:
        return local_excels[0]

    parent_excel = BASE_DIR.parent / "Master Product List - FurBetterChoice.xlsx"
    if parent_excel.is_file():
        return parent_excel

    if DEFAULT_EXCEL_PATH.is_file():
        return DEFAULT_EXCEL_PATH
    return None


def print_summary(excel_path: Path, stats: dict[str, Any], warnings: list[str]) -> None:
    print()
    print("=" * 68)
    print("FurBetterChoice Product Import Summary")
    print("=" * 68)
    print(f"Excel file                : {excel_path}")
    print(f"Products parsed           : {stats['productsParsed']}")
    print(f"Empty template rows skipped: {stats['skippedEmptyRows']}")
    print(f"With nutrition data       : {stats['productsWithNutrition']}")
    print(f"With ingredients          : {stats['productsWithIngredients']}")
    print(f"Energy (kcal/kg ready)    : {stats['productsWithConvertibleEnergy']}")
    print(f"Energy (serving-unit only): {stats['productsWithServingOnlyEnergy']}")
    if warnings:
        print()
        print(f"Warnings ({len(warnings)}):")
        for w in warnings:
            print(f"  [WARN] {w}")
    print("=" * 68)


def main() -> int:
    parser = argparse.ArgumentParser(description="Import FurBetterChoice Master Product List Excel to Firestore.")
    parser.add_argument("--excel", help="Path to Master Product List .xlsx file")
    parser.add_argument("--key", help="Path to Firebase serviceAccountKey.json")
    parser.add_argument("--collection", default=DEFAULT_COLLECTION, help="Firestore collection name (default: products)")
    parser.add_argument("--dry-run", action="store_true", help="Parse and validate without writing to Firestore")
    parser.add_argument("--export-json", help="Optional path to save parsed JSON preview")
    args = parser.parse_args()

    excel_path = find_excel_file(args.excel)
    if excel_path is None:
        print("[ERROR] Could not find the Excel file.", file=sys.stderr)
        print(f"  Looked in: {BASE_DIR}", file=sys.stderr)
        print(f"  And at   : {DEFAULT_EXCEL_PATH}", file=sys.stderr)
        print("  Tip: Drag-and-drop your .xlsx onto Run_Importer.bat or place it in this folder.", file=sys.stderr)
        return 1

    print(f"Reading Excel workbook: {excel_path} ...")
    products, stats = load_workbook_products(excel_path)
    print_summary(excel_path, stats, stats["warnings"])

    preview_path = Path(args.export_json).resolve() if args.export_json else (BASE_DIR / "dry_run_preview.json" if args.dry_run else None)
    if preview_path is not None:
        preview_payload = {
            "generatedAt": datetime.now(timezone.utc).isoformat(),
            "excelSource": str(excel_path),
            "stats": stats,
            "products": products,
        }
        preview_path.write_text(json.dumps(preview_payload, indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"[OK] Wrote JSON preview to: {preview_path}")

    if args.dry_run:
        print("\n[DRY RUN] No changes were written to Firestore.")
        return 0

    key_path = find_service_account_key(args.key)
    if key_path is None:
        print("\n[ERROR] Firebase serviceAccountKey.json not found!", file=sys.stderr)
        print(f"  Please place your serviceAccountKey.json inside:\n    {BASE_DIR}", file=sys.stderr)
        print("  (Firebase Console -> Project Settings -> Service accounts -> Generate new private key)", file=sys.stderr)
        return 2

    print(f"\nUsing service account key: {key_path.name}")
    print(f"Uploading {len(products)} products to Firestore collection '{args.collection}'...")
    written = upload_to_firestore(products, key_path, args.collection)
    print(f"\n[SUCCESS] Imported/updated {written} products in Firestore '{args.collection}'.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
