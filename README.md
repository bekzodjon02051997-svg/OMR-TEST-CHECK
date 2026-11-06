# OMR Live Scanner — DTM Multilevel answer sheet prototype

Android Studio project: Kotlin, Jetpack Compose, CameraX, Google ML Kit Text Recognition.

## User-provided template analysis
The supplied blank PDF is a two-column DTM Multilevel Master Books form:
- Listening: questions 1–8 (A–C), 15–18 (A–F), 19–23 (A–H), 24–29 (A–C), plus written-letter fields 9–14 and 30–35.
- Reading: written-letter fields 1–6 and 30–33; bubbles 7–14 (A–J), 15–20 (A–H), 21–24 and 25–29 (A–D/A–C), and bubbles 34–35 (A–D).
- Candidate identity and special-code fields are separate from scored answer areas.

## What this project currently does
- CameraX live camera preview.
- ML Kit text recognition on live frames; extracted text is shown to the operator.
- Answer-key TXT/CSV import and manual key entry UI.
- Result UI scaffold.

## Important accuracy limitation
This ZIP is an updated **development prototype**, not a certified/finished automatic grader for the uploaded DTM template. The OCR text is surfaced as raw recognition output and is not yet mapped into each numbered answer cell. Exact scoring needs a perspective-corrected page image, registration to the supplied blank template, calibrated coordinates for every bubble and letter cell, and per-cell OCR/mark confidence thresholds. The first sample is filled and the second is blank; both were used to identify the layout. Do not use this build for official scoring until cell mapping, multi-mark/blank detection, and real-device testing have been completed.

Handwritten uppercase text is especially variable. Lighting, focus, paper rotation, pen/pencil darkness, and camera distance affect recognition. A human review/confirm step is necessary.

## Run
1. Open this folder in Android Studio.
2. Sync Gradle (internet is needed to download dependencies).
3. Run on Android 8.0+ and grant camera permission.
4. Load a TXT/CSV answer key containing comma-separated answers or one answer per line.

## Next engineering milestones
1. Detect the page boundary and rectify perspective.
2. Register the rectified page to the supplied DTM PDF template.
3. Map each bubble center and each letter cell by question number and section.
4. Detect filled bubbles and multiple marks.
5. OCR each letter cell separately, normalize A-Z, and flag low-confidence characters.
6. Compare only supported question types against the answer key; display uncertain items for human confirmation.
7. Export verified results to CSV/PDF.
