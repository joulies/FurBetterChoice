# Implementation Plan — AI-Powered Can vs. Pouch & Eco-Friendliness Packaging Detection

This plan details the implementation of AI-powered detection for packaging types (**Can** vs. **Pouch**, **Bag**, **Tub**, **Tetra Pak**) and associated eco-friendliness / environmental attributes (packaging material, recyclability, biodegradability) when scanning or adding a new product.

## User Review Required

> [!IMPORTANT]
> - **AI Vision Enhancement:** Gemini (`gemini-2.5-flash`) will be instructed to visually analyze the front-label photo/OCR text to classify the container as a **Can**, **Pouch**, **Bag**, **Tub**, or **Tetra Pak**.
> - **Smart Eco-Inference:** Based on the detected packaging type (e.g., metal can vs. flexible pouch), the system will automatically suggest standard environmental attributes (Material, Recyclability, Biodegradability), which the user can still review or edit before saving.

## Open Questions
- None. The UI already has dedicated sections for "Brand Eco-Friendliness History" and "Packaging and Environmental Information" (`BrandHistoryCard` in `ProductAnalysisScreen.kt`).

## Proposed Changes

### 1. AI Correction & Vision Service
#### [MODIFY] [LabelAiCorrectionService.kt](file:///C:/Users/DELL/AndroidStudioProjects/FurBetterChoice/app/src/main/java/com/example/furbetterchoice/api/LabelAiCorrectionService.kt)
- Expand `ScannedFrontInfoResult` data class to include:
  - `packagingType` (String)
  - `packagingMaterial` (String)
  - `packagingRecyclability` (String)
  - `packagingBiodegradability` (String)
- Update `frontSchema` to include properties for `packagingType`, `packagingMaterial`, `packagingRecyclability`, and `packagingBiodegradability`.
- Update `parseAndCorrectFrontOfBag` prompt to instruct Gemini to visually identify whether the product is in a **Can**, **Pouch**, **Bag**, **Tub**, or **Tetra Pak**, and infer default eco-friendliness properties (e.g., Cans -> Aluminum/Tin, Recyclable; Pouches -> Flexible Plastic/Foil, Difficult to Recycle).

### 2. Data Models & Repository
#### [MODIFY] [Models.kt](file:///C:/Users/DELL/AndroidStudioProjects/FurBetterChoice/app/src/main/java/com/example/furbetterchoice/api/Models.kt)
- Ensure `Product` data class handles packaging fields robustly with fallback defaults.

#### [MODIFY] [FirestoreRepository.kt](file:///C:/Users/DELL/AndroidStudioProjects/FurBetterChoice/app/src/main/java/com/example/furbetterchoice/api/FirestoreRepository.kt)
- Ensure `saveScannedProduct` and merge logic correctly read, write, and persist `packagingType`, `packagingMaterial`, `packagingRecyclability`, and `packagingBiodegradability`.

### 3. ViewModel & Session State
#### [MODIFY] [AppViewModel.kt](file:///C:/Users/DELL/AndroidStudioProjects/FurBetterChoice/app/src/main/java/com/example/furbetterchoice/ui/AppViewModel.kt)
- Update scan session merging logic (`buildUpdatedProduct`) so that `packagingType`, `packagingMaterial`, `packagingRecyclability`, and `packagingBiodegradability` from `ScannedFrontInfoResult` are correctly mapped to the working product draft.

### 4. UI & Edit Dialogs
#### [MODIFY] [ScannedProductEditDialogs.kt](file:///C:/Users/DELL/AndroidStudioProjects/FurBetterChoice/app/src/main/java/com/example/furbetterchoice/ui/ScannedProductEditDialogs.kt)
- Add input fields/pills in the product header edit dialog for **Packaging Type** (Can, Pouch, Bag, Tub, etc.), **Packaging Material**, **Recyclability**, and **Biodegradability** so users can review or override AI-detected environmental information.

## Verification Plan

### Automated Tests
- Run unit tests (`gradle_build("app:testDebugUnitTest")`) to verify parsing and model mapping.

### Manual Verification
- Deploy to emulator/device, scan a new product barcode, take a front photo of a canned or pouched pet food product, and verify that Gemini correctly identifies the packaging type (Can vs Pouch) and populates the Eco-Friendliness / Packaging & Environmental Information card.
