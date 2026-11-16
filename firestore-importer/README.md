# FurBetterChoice — One-Click Firestore Product Importer

This folder imports `Master Product List - FurBetterChoice.xlsx` into your Cloud Firestore `products` collection.
It uses **100% pure Python** (`openpyxl` + built-in Google OAuth2/Firestore REST API v1) with **no native C/DLL extensions (`grpcio`/`cygrpc`)**, so Windows Application Control / Smart App Control will not block it.

## Quick Start (3 Steps)

1. **Add your Firebase Service Account Key**
   - In Firebase Console: **Project Settings → Service accounts → Generate new private key**.
   - Drop the downloaded `.json` file into this `firestore-importer` folder (either keep its default `*firebase-adminsdk*.json` filename or rename it to `serviceAccountKey.json`).
2. **Optional — Preview first (no database writes)**
   - Double-click **`Preview_Dry_Run.bat`**.
   - It parses the Excel workbook, shows any data warnings, and saves `dry_run_preview.json` without needing `serviceAccountKey.json` or touching Firestore.
3. **Import to Firestore**
   - Double-click **`Run_Importer.bat`**.
   - Uploads all valid products to `products/{productId}` (preserving unrelated fields like `brandHistory` and `reviews`).
   - To use a different Excel file later, either place it in this folder or drag-and-drop the `.xlsx` file onto `Run_Importer.bat`.

## What It Handles Automatically

- **Nutrient Key & Name Normalization**
  - Maps label variations (`Crude Protein`/`Protein`, `Crude Fat`/`Crude fat`/`Fat`, `Crude Fiber`/`Crude Fibre`/`Crude Fibres`/`Fiber`, `Crude Ash`/`Ash`, `Phosphorus`/`Total Phosphorus`, `Salt`, `Taurine`, `Omega-3`, `Omega-6`, `Omega-6 Fatty Acids`) to the canonical keys used by the Android app and Admin Dashboard.
- **Energy & Unit Handling (`kcal/kg`, `kcal/100g`, `kcal/bag`, `kcal/pouch`)**
  - Recognizes all energy label variants (`Metabolizable Energy`, `Metabolized Energy`, `Calorie Content`, `Energy`, `Energy Consumption`).
  - Converts weight-based units (`kcal/100g` → `× 10 kcal/kg`, `kJ/kg`, `MJ/kg`) into normalized root `metabolizableEnergy` (`kcal/kg`) while keeping the original unit/value in `nutrition.metabolizableEnergy`.
  - Resolves duplicate/conflicting energy rows in the spreadsheet (e.g., `P0043` / `P0044` where `kcal/kg` and `kcal/100g` have the same raw number, and `P0049` preferring `80 kcal/100g` over `96 kcal/pouch (120)` while preserving `energyPerServing`).
  - Preserves non-convertible serving units (e.g., `P0041`: `204.6 kcal/bag`) without corrupting them into `kcal/kg`.
- **Clean Data Hygiene**
  - Trims whitespace and embedded newlines (`\n`), formats numeric barcodes as clean strings, skips empty placeholder rows (`P0081`–`P0090`), and preserves unrelated fields on existing documents (e.g., `brandHistory`, `reviews` subcollection).
