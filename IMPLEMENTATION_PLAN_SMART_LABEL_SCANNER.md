# FurBetterChoice — Smart Camera Label Scanner Implementation Plan

An end-to-end plan to turn the barcode scanner into an interactive, AI-assisted pet food label scanner with a swipeable **Product Scanned** bottom sheet, pencil-icon editing, strict type & bad-word validation, and direct community saving to Firestore (with no admin approval flow).

---

## 1. User Flow Overview

1. **Step 1 — Scan Barcode & Fetch Product**
   * User scans a barcode in [`ProductScanner.kt`](app/src/main/java/com/example/furbetterchoice/ui/ProductScanner.kt).
   * App queries [`ProductRepository.getProduct(barcode)`](app/src/main/java/com/example/furbetterchoice/api/ProductRepository.kt).
   * **If found:** Loads existing metadata (name, brand, species, life stage, image, packaging, reviews).
   * **If not found:** Starts a new draft product with that barcode and offers an optional **1-tap "Scan Front of Bag"** (or quick Dog/Cat + Life Stage pills) so AI can auto-fill Product Name, Brand, Species, and Life Stage before moving to Nutrients.

2. **Step 2 — Guided Hover: Nutrients First (Guaranteed Analysis)**
   * Camera stays open and switches to live text recognition.
   * Prompts the user to hover over **Nutrients** (`Crude Protein`, `Crude Fat`, `Crude Fiber`, `Moisture`, `Ash`, `Calcium`, `Phosphorus`, `Taurine`, `Metabolizable Energy`).
   * Displays a **live nutrient checklist** (`Protein ✓`, `Fat ✓`, `Moisture ?`, `Calcium ?`, etc.) so the user knows immediately if they should tilt the bag to catch Moisture or Minerals before moving on.
   * Multi-frame stabilization + English-first filtering + AI autocorrect extract clean numeric values.

3. **Step 3 — Guided Hover: Ingredients Next**
   * Prompts: *"Now hover over the Ingredients list"*.
   * English-first language filter excludes foreign-language blocks on bilingual bags.
   * AI autocorrects OCR typos into a clean comma-separated `String`.

4. **Step 4 — Swipeable Pop-Up Bottom Sheet (Almost Full Screen)**
   * **Peek / Half state:** Shows live scan progress while keeping the camera visible above.
   * **Swiped up (~92–95% screen height):** Displays the exact **Product Scanned** layout ([`ProductAnalysisScreen.kt`](app/src/main/java/com/example/furbetterchoice/ui/ProductAnalysisScreen.kt)) — Hero Header, Tabs (`Description`, `Ingredients`, `Alternatives`, `Reviews`), filtered Pet Selector, **FEDIAF & AAFCO assessments**, and Ingredient allergy/safety checks.
   * **Swiped down:** Returns to the camera to rescan nutrients or ingredients anytime.

5. **Step 5 — Free Editing via Small Pencil Icons + Strict Validation**
   * Small pencil icons let the user edit **Product Info**, **Nutrients**, and **Ingredients** inside the sheet.
   * **Nutrients** strictly accept and store **numbers (`Double`)** only.
   * **Ingredients** strictly accept and store a **text string (`String`)** only.
   * Real-time **bad-word detection** highlights inappropriate words in **red** and disables submission until removed (reused across **Reviews** too).

6. **Step 6 — Permanent "Save to Database" With Accuracy Reminder & Safe Merge**
   * Tapping **Save Product to Database** opens a confirmation dialog with an **accuracy reminder**, a **Before vs. After** change summary, and any low-confidence highlights.
   * Saves safely to Firestore (`products/{productId}`) with field-by-field merging, an automatic `edit_history` backup snapshot, and offline retry protection.

---

## 2. Dependencies & AI Key Security

**Files to update:**
* `gradle/libs.versions.toml`
* `app/build.gradle.kts`

**Libraries to add:**
1. **ML Kit Text Recognition (100% Free, On-Device / Offline):**
   * `com.google.mlkit:text-recognition:16.0.1`
2. **ML Kit Language Identification (100% Free, On-Device / Offline):**
   * `com.google.mlkit:language-id:17.0.6`
3. **AI Autocorrect & Structured Parsing (Free Tier + Offline Fallback + Key Protection):**
   * **Primary:** **Firebase AI Logic** using the **Gemini Developer API** free tier (`gemini-2.5-flash` / `gemini-2.0-flash`) — *do not* use the Vertex AI backend (which requires paid Blaze billing).
   * **API Key Protection:** Enable **Firebase App Check** (Play Integrity / debug provider) with Firebase AI Logic—or route requests through a lightweight Firebase Cloud Function proxy—so no raw Gemini API key is ever hardcoded or extractable from the APK.
   * **Offline / Quota Backup:** Local dictionary + fuzzy/regex pet-food parser so autocorrect still works without internet or if free-tier limits are reached.

---

## 3. New Domain & AI Services (`api/`)

### A. `api/LabelTextLanguageFilter.kt`
* **Region-of-interest filtering:** Only keeps ML Kit text blocks whose bounding box overlaps the scanner's center guide box (prevents reading unrelated marketing text on the bag).
* **English-first priority:** Runs `LanguageIdentification` on each block:
  1. Keeps blocks identified as English (`"en"`, confidence `>= 0.5`) or containing core English label terms (`"crude"`, `"protein"`, `"fat"`, `"fiber"`, `"fibre"`, `"moisture"`, `"ingredients"`, `"calcium"`).
  2. Keeps adjacent numeric/unit tokens (e.g., `"26.0%"`, `"1.2%"`) near English headers.
  3. Drops non-English blocks (French, Spanish, Japanese, etc.) whenever English text is present.
  4. **Fallback:** If zero English blocks exist on a foreign-only bag, passes the highest-confidence text to AI to translate/normalize into English.

### B. `api/LabelAiCorrectionService.kt`
1. **`parseAndCorrectFrontOfBag(rawEnglishText: String): ScannedFrontInfoResult`** *(optional quick-fill for brand-new barcodes)*
   * Extracts `productName`, `brand`, `species` (`Dog` / `Cat`), and `lifeStage` (`Adult`, `Puppy`, `Kitten`, `Senior`, `All Life Stages`).
2. **`parseAndCorrectNutrients(rawEnglishText: String): ScannedNutrientsResult`**
   * Fixes OCR misspellings (`"Crvde Pr0tein 26.O%"` → `Crude Protein: 26.0%`, `"M01sture"` → `Moisture`).
   * Maps strictly to numeric `Double` values + units (`crudeProtein`, `crudeFat`, `crudeFiber`, `moisture`, `ash`, `calcium`, `phosphorus`, `taurine`, `metabolizableEnergy`).
   * **Sanity checks:**
     * `%` nutrients must fall within `0.0 < value <= 100.0`.
     * Detects missing decimal points (e.g., Calcium `"125%"` → flags/suggests `"1.25%"`; Moisture `"120%"` → `"12.0%"`).
     * Recognizes `Min` / `Max` columns and ignores feeding-table numbers (`"cups/day"`).
     * Flags low-confidence / adjusted values so the UI highlights them gently in amber for quick review.
3. **`autocorrectIngredients(rawEnglishText: String): ScannedIngredientsResult`**
   * Fixes OCR typos (`"Chlcken MeaI, Br0wn Rlce, Taur1ne"` → `"Chicken Meal, Brown Rice, Taurine"`).
   * Strips stray OCR noise and returns a clean comma-separated `String` (with offline pet-food dictionary backup if offline).

### C. `api/ContentModerationFilter.kt`
* Shared profanity/bad-word detector used across **all editable product text fields** AND **Product Reviews** (`ReviewsTabContent` / `ReviewsFormSection`).
* Normalizes obfuscated spellings (`@` → `a`, `0` → `o`, `1`/`!` → `i`, `3` → `e`, `$` → `s`, stripped separators) while safe-listing legitimate pet-food terms (`ash`, `bass`, `cassava`, etc.).
* Returns:
  * `containsBadWords: Boolean`
  * `matchedWords: List<String>`
  * `highlightRanges: List<IntRange>` (exact start..end indices in the original text for real-time red highlighting).

---

## 4. Camera Accuracy & Guided Scanner UI (`ui/ProductScanner.kt`)

* **Scan Steps:**
  * `BARCODE_SCANNING` → `FETCHING_PRODUCT` → *(optional `SCANNING_FRONT_OF_BAG` if barcode not in DB)* → `SCANNING_NUTRIENTS` → `SCANNING_INGREDIENTS` → `REVIEWING_RESULT`.
* **Live Nutrient Checklist Overlay (during `SCANNING_NUTRIENTS`):**
  * Compact pill row showing key FEDIAF/AAFCO fields in real time: `Protein ✓`, `Fat ✓`, `Fiber ✓`, `Moisture ?`, `Calcium ?`, `Phosphorus ?` so the user knows before leaving the bag if Moisture or Minerals are still missing.
* **CameraX Accuracy Enhancements:**
  * **Crop to guide frame:** Ignore blocks outside the targeting box.
  * **Multi-frame stabilization ("Best-of-5"):** Collect 3–5 consecutive frames (~0.8–1.2s) and take consensus values before invoking AI.
  * **High-res still capture:** Capture a sharp still frame on auto-lock / "Capture" tap so tiny decimals (`0.8%`) and `%` signs are crisp.
  * **Tap-to-focus & Flashlight toggle:** Essential for glossy foil bags and dim store aisles.
  * **Skip / Rescan controls:** Users can skip any step if DB already has data, or swipe the sheet down to rescan Nutrients or Ingredients.

---

## 5. ViewModel, Safe Merging & Offline Draft Persistence (`ui/AppViewModel.kt`)

* **Scan Session State (`ScanSessionState`):**
  * Tracks `scannedBarcode`, `baseDatabaseProduct` (original DB snapshot), `workingProduct` (merged + user-edited product), `lowConfidenceKeys`, `scanStep`, `isAiProcessing`, `isSaving`, `isSavedToDatabase`, and `saveError`.
* **Field-by-Field Safe Merge:**
  * Never blindly wipes existing DB fields that weren't scanned (e.g., keeps existing `imageUrl`, `brandHistory`, `packaging*`, and unedited nutrients unless updated by scan/edit).
  * Immediately triggers [`NutritionAssessmentEngine`](app/src/main/java/com/example/furbetterchoice/api/NutritionAssessmentEngine.kt), [`AafcoAssessmentEngine`](app/src/main/java/com/example/furbetterchoice/api/AafcoAssessmentEngine.kt), [`IngredientAnalyzer`](app/src/main/java/com/example/furbetterchoice/api/IngredientAnalyzer.kt), and [`ScoringSystem`](app/src/main/java/com/example/furbetterchoice/api/ScoringSystem.kt) on every scan or pencil edit.
* **Offline Draft & Duplicate-Save Protection:**
  * Persists `workingProduct` draft locally (e.g., `SharedPreferences` JSON cache keyed by barcode) if the network fails during save, showing a **"Retry Save"** banner instead of losing the user's scan.
  * Prevents double-submission while `isSaving == true`.

---

## 6. Swipeable Bottom Sheet & Reused Product Scanned Layout

**Files to update:**
* `app/src/main/java/com/example/furbetterchoice/ui/ProductAnalysisScreen.kt`
* `app/src/main/java/com/example/furbetterchoice/ui/ProductScanner.kt`
* `app/src/main/java/com/example/furbetterchoice/MainActivity.kt`

* **Extract Reusable Layout:** Move the body of [`ProductAnalysisScreen`](app/src/main/java/com/example/furbetterchoice/ui/ProductAnalysisScreen.kt) (hero header, tabs, FEDIAF/AAFCO assessments + filtered pet selector, ingredients, alternatives, reviews) into `ProductAnalysisContentBody(...)` with optional `isEditModeEnabled` and pencil-edit callbacks.
* **Swipeable Almost-Full-Screen Sheet in `ProductScanner.kt`:**
  * Use Material 3 `BottomSheetScaffold` (or `anchoredDraggable` sheet styled with `28.dp` rounded top corners & `subtleCardShadow`):
    * **Peek state (~140–170dp):** Drag handle, current scan step, nutrient/ingredient progress, and *"Swipe up for full assessment"* button.
    * **Expanded state (~92–95% screen height):** Renders `ProductAnalysisContentBody` with full FEDIAF, AAFCO, pet selector, and ingredient analysis.
* **Navigation (`MainActivity.kt`):** Keep `"scanner"` open after barcode scan instead of immediately popping it so the user stays in the interactive camera + swipeable sheet experience until they tap Close/Back.

---

## 7. Pencil Icon Editing, Strict Data Types & Bad-Word Protection

**Files to update:**
* `app/src/main/java/com/example/furbetterchoice/ui/ProductAnalysisScreen.kt`
* `app/src/main/java/com/example/furbetterchoice/ui/Components.kt`

### A. Small Pencil Icons (`isEditModeEnabled = true`)
1. **Product Header Pencil Icon:** Edits `Product Name` (`String`), `Brand` (`String`), `Pet Type` (`Dog` / `Cat` pills), `Life Stage` (`Adult`, `Puppy`, `Early Growth (<14 wks)`, `Late Growth (≥14 wks)`, `Kitten`, `Senior`, `All Life Stages` pills), and `Food Type` (`Dry Food`, `Wet Food`, `Treat`).
2. **Nutrients Pencil Icons:** Located in the FEDIAF/AAFCO card header and on individual nutrient rows.
   * **Strict Number Enforcement:** Uses `KeyboardType.Decimal`, blocks all letters/spaces/symbols except `0–9` and one `.`, validates ranges (`0.0 < v <= 100.0` for `%`; `> 0.0` for `kcal/kg`), and stores strictly as `Double` in `NutrientValue`.
3. **Ingredients Pencil Icon:** Located in the Ingredients section header; edits and stores strictly as a sanitized `String`.

### B. Real-Time Bad-Word Highlighting & Submit Blocking
* **Shared `ModerationVisualTransformation`:** Applied to Product Name, Brand, Food Type, Ingredients, **and Product Reviews (`ReviewsFormSection`)**.
  * Highlights flagged character ranges with a red background (`Color(0xFFE53935).copy(alpha = 0.20f)`) and bold red text (`Color(0xFFD32F2F)`).
  * Shows red field border + inline error message listing the flagged word(s).
* **Submit Blocking:**
  * Disables **"Apply Changes"** inside edit dialogs while bad words exist.
  * Shows a red warning badge on affected sections in the scanned sheet and **disables "Save Product to Database"** (and **"Submit Review"**) until all bad words are removed.

---

## 8. Permanent Save to Database, Accuracy Reminder & History Backup

**Files to update:**
* `app/src/main/java/com/example/furbetterchoice/api/FirestoreRepository.kt`
* `app/src/main/java/com/example/furbetterchoice/ui/ProductScanner.kt` / `ProductAnalysisScreen.kt`

1. **"Save Product to Database" Button:**
   * Shown in a sticky footer bar inside the expanded bottom sheet once scanning is complete.
   * Triggers `LoginRequiredDialog` if not signed in; disabled if required fields (`productName`, `pet`) are missing or if `ContentModerationFilter` detects any bad words.
2. **Confirmation Dialog With Accuracy Reminder & Before-vs-After Diff:**
   * **Highlighted Accuracy Reminder Banner:** *"Accuracy Reminder: Please recheck all scanned numbers, product details, and ingredients to make sure they match the physical package before saving permanently."*
   * **Before vs. After Change Summary:**
     * For existing DB products, explicitly highlights changed or newly added fields (e.g., `Crude Protein: 26.0% → 28.0%`, `Moisture: Not listed → 10.0% (newly scanned)`).
     * Highlights any low-confidence OCR values in amber.
   * **Buttons:** `"Recheck / Edit"` (closes dialog so user can use pencil icons or rescan) and `"Confirm & Save"`.
3. **Safe Save + Snapshot History (`FirestoreRepository`):**
   * Before updating an existing document in `products/{productId}`, writes a lightweight backup snapshot (previous fields + `savedAt` timestamp + `savedByUid`) to `products/{productId}/edit_history`.
   * Performs a safe field-by-field merge on `products/{productId}` (preserving unedited fields) and updates `AppViewModel._productDetailsCache`.
4. **Firestore Security Rules (`firestore.rules`):**
   * Allow any authenticated user to read/create/update `products/{productId}` and append to `edit_history`, while validating at the rule level that `productName` and `ingredients` are non-empty strings and nutrient values are valid numbers (`>= 0` and `<= 100` for `%` entries).

---

## 9. Testing & Verification Plan

1. **Unit Tests:**
   * `LabelTextLanguageFilterTest`: English blocks prioritized over non-English on bilingual packaging; non-English fallback works.
   * `LabelAiCorrectionServiceTest`: OCR typo autocorrect, decimal sanity checks, strict numeric-only nutrient parsing, and offline fallback.
   * `ContentModerationFilterTest`: detects profanity and leet-speak obfuscations, returns exact highlight ranges, avoids false positives on pet-food words (`ash`, `bass`, etc.).
   * `ScanMergeAndHistoryTest`: verifies safe field-by-field merge preserves untouched DB metadata and recalculates FEDIAF/AAFCO assessments live.
2. **Device / Emulator Verification:**
   * Scan barcode → (Front-of-Bag quick-fill if new) → Nutrients hover with live checklist → Ingredients hover.
   * Swipe bottom sheet between peek and ~95% full screen and verify full Product Scanned layout + FEDIAF/AAFCO + pet selector.
   * Edit fields via small pencil icons (verify nutrients reject letters and store numbers only; ingredients store string only).
   * Enter a bad word in product edits and in Reviews → confirm red highlighting and disabled submit button → remove bad word → confirm submit unlocks.
   * Tap **Save Product to Database** → inspect Accuracy Reminder + Before/After diff → confirm save → test offline retry banner + verify Firestore `products/{id}` and `edit_history` persistence.
