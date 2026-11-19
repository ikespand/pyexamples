# Pageport

## Product decision

On 2026-11-18, the user accepted the current page-first passport scan MVP and selected model. Android debug and release now launch this model by default. Focus follow-up work on maintaining and polishing this MVP; do not restart model search or pursue the former 90% goal unless the user reopens it. The 90% exact real-passport-session target and one-second Android p95 target have **not** been formally measured or passed. Never claim otherwise.

## Current Android scan path

`android/app/src/main/java/com/example/mrz/CandidateScanner.java` runs the pinned local cascade:

1. DocAligner FastViT-T8 predicts page corners.
2. OpenCV perspective-corrects the full page.
3. MRZScanner MobileNetV4 spotting reads a two-line TD3 MRZ.
4. If the first result is invalid or not two 44-character lines, the MRZScanner specialist detector and recognizer run as fallback.
5. A fixed development confidence gate either shows a valid two-line TD3 result or asks for a retake. No characters are guessed or changed to satisfy checksums.

The selected capture flow waits for a stable page, takes a CameraX quality-priority still, redetects corners at still resolution, displays the rectified page, then scans. The image picker uses the same page-crop flow. Inference is on-device; the Android manifest requests no Internet permission. Unsaved scans and page images remain in memory. The picker honors EXIF orientation and caps the longest side at 4096 pixels. NFC chip reading is a separate, unauthenticated flow.

The app targets TD3 passports. ID cards and other MRZ types are not validated. A checksum pass does not prove every OCR character is exact; tell users to compare displayed fields with the passport. Avoid claims of identity verification or chip authenticity.

## Using Pageport

Open **Scan passport** and hold the photo page landscape, with all four edges visible on a plain, contrasting surface. Keep the two printed lines clear. On Android, **Save scan** encrypts the extracted fields and scan metadata with Android Keystore in app-private storage excluded from backup. **Saved scans** opens or deletes records. Page images and raw MRZ lines are excluded from saved records. Sharing passes the displayed text to the Android share sheet only on request.

Birth and expiry dates use inferred centuries because MRZ years contain two digits. Repeat scans match on issuer, passport number and raw birth date and show the most recent matching capture time. Entries created before capture timestamps were added show their save time. The per-scan timing starts at first page detection (camera) or photo selection (picker) and ends at the OCR result. It is a single observation.

**Read passport chip** is separate. Review the MRZ-derived access details before reading. Certificate trust and passive/active chip authentication are unimplemented. Physical chip compatibility has limited testing.

## Pinned model files

All four models are stored locally in ignored `.tools/docsaid-models/`; `scripts/download_models.py` verifies their hashes against `models/specialist_models.json`. Gradle verifies the hashes for **debug and release**, and both variants bundle the model files and Apache license notice. Total model bytes: 117,560,836 (about 112.1 MiB).

| File | Role | SHA-256 |
| --- | --- | --- |
| `align.onnx` | DocAligner FastViT-T8 page corners | `950070fd4a46e25d46db7c35d0ed77612d9063f70d4543c7c504f0da6a943a89` |
| `spot.onnx` | MRZScanner MobileNetV4, release 20240919 | `d58b6af5501295fb1c9d93a529b6725d98d4983711f2d2b3323855d155ec2105` |
| `det.onnx` | MRZScanner fallback detector, release 20250222 | `0786844728d828dc68c19b844aa87e2b29fdba3a587468f2863824d116ffc269` |
| `rec.onnx` | MRZScanner fallback recognizer, release 20250221 | `07cef987fc1852aac12f18e1269fbaf1e2644579ca5743061d6f9cec7bef77c0` |

Android and web both require a valid two-line TD3 result and either spotter mean token score ≥ `0.994124` or selected first-line name-field minimum score ≥ `0.839322`. These thresholds were chosen on development data. Android ONNX Runtime uses the CPU with two intra-op threads.

The chosen weights were not fine-tuned by this project. DOCSAID documents synthetic pretraining followed by MIDV-2020 fine-tuning. The project separately tried PP-OCRv5 CTC fine-tuning on public/mock images; the best three-reader configuration reached 69/79 exact, zero wrong accepted and 10 abstentions, with 2.56 s Mac p95. It was not bundled or selected. It is not real-passport or target-phone evidence. See the [MRZScanner training notes](https://docsaid.org/en/docs/mrzscanner/dataset/). Public benchmark overlap with upstream training is unknown.

## Attribution and licenses

The adapters run the official DOCSAID Community weights directly with OpenCV and ONNX Runtime. Preprocessing and decoding draw on these upstream revisions:

- [DocAligner](https://github.com/DocsaidLab/DocAligner), `3275b0f07f8e99d8c01cb0774dea2549be1416b6`
- [MRZScanner](https://github.com/DocsaidLab/MRZScanner), `7f98880115441c8e42424eda1dc8f1abd4e39377`
- [Capybara](https://github.com/DocsaidLab/Capybara), `85a0d2638f895b39f82965b137320c8aa92c517b`

As checked on 2026-11-18, DOCSAID identifies its Community code and official weights as Apache-2.0, including commercial use ([license](https://github.com/DocsaidLab/MRZScanner/blob/main/LICENSE), [DocAligner model page](https://docsaid.org/en/models/docaligner)). Dataset terms remain separate. Keep the packaged Apache license and third-party notice in `android/app/src/main/assets/licenses/`. Android dependency licenses also apply. Recheck upstream notices before external distribution.

## Measurement and limits

- The ungated cascade scored 44/70 exact on a previously used MIDV-500 public/mock subset, with zero wrong among 41 accepted. Historical old-fixture regressions exist. The single-run Mac pipeline measured 924/980 ms p50/p95, with 1,155 ms initialization measured separately. It excluded camera, UI, and phone runtime.
- The current Python confidence experiment retained 68/79 exact MIDV-2020 mock validation reads, 24/70 on the reused MIDV-500 subset, and 8/20 correlated tilt variants; remaining cases were abstentions. It rejected four negative smoke images. The confidence gate was selected using development data.
- These numbers do not establish Android numerical parity, independently labelled real-passport session accuracy, or target-phone p50/p95 latency. The owner has smoke-tested and accepted the installed scan experience, but that is not a quantified accuracy result.
- On 2026-11-18, `assembleDebug` and `assembleRelease` both succeeded; the release APK was inspected to confirm all four embedded model hashes and the license notice. The rebuilt debug APK installed successfully on the connected Samsung SM-A546B. No live camera/accuracy benchmark or unit-test suite was run in that proofcheck.
- Keep the reserved identity-disjoint MIDV-2020 holdout sealed until OCR-blind image-quality review is complete. Never exclude blurry sessions silently or tune on sealed data.

## Build and install Android

From the repository root:

```sh
.venv/bin/python scripts/download_models.py
source .tools/android-env.sh
gradle -p android :app:assembleDebug
gradle -p android :app:assembleRelease
adb devices -l
adb -s PHONE_SERIAL install -r android/app/build/outputs/apk/debug/app-debug.apk
```

`.tools/android-env.sh` is the local toolchain helper. For a fresh checkout with JDK 17 and Android SDK 34 configured, use the Gradle wrapper at `android/gradlew` (for example, `android/gradlew -p android :app:assembleDebug`). Android targets API 34, requires API 26 or later, and packages arm64-v8a. The release APK is unsigned until a distribution signing key is configured.

## Run the local web app

Create a virtual environment in `.venv` if needed, then run:

```sh
.venv/bin/python -m pip install -r requirements-edge.txt
.venv/bin/python scripts/download_models.py
.venv/bin/python -m uvicorn edge.app:app --host 127.0.0.1 --port 8000 --no-access-log
```

Open [http://localhost:8000](http://localhost:8000) and choose **Start passport scan**. Browser images go only to the loopback Python service. Do not expose it to a network.

## Local Android image benchmark

```sh
source .tools/android-env.sh
python3 android/benchmark.py --serial PHONE_SERIAL --manifest .local/evaluation/private/mobile_baseline.json --repeat 1 --output .local/evaluation/results/android-candidate.json
```

Use the debug APK and an ignored private manifest with image paths relative to that manifest. Include ground-truth MRZ lines only when independently verified. This measures the image pipeline, excluding CameraX capture and UI. Record device power and thermal conditions. The runner reports per-case and aggregate metrics and clears its temporary app-private files. Keep the reserved holdout sealed pending quality review. Repeated frames or transformations of one document are correlated.

## Workspace boundaries

Keep passport pixels, MRZ text, fields and chip data local. Do not commit them or send them to cloud services or LLMs. Preserve ignored private images, evaluation data, environments and model caches, including `sample_img/`, `.tools/`, `.venv/` and `.local/evaluation/`. The supported code is in `android/` and `edge/`. Keep the model downloader, pinned manifest, automated checks and debug benchmark that support those apps.
