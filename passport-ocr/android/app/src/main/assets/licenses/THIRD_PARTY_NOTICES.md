# Third-party notices

## DOCSAID model assets and adapted inference code

This application bundles official DOCSAID Community ONNX weights for DocAligner and MRZScanner. The Android and Python adapters use OpenCV and ONNX Runtime to run the pinned models locally. The model filenames, hashes, and upstream source links are documented in `AGENTS.md` and `models/specialist_models.json`.

- DocAligner FastViT-T8 document-corner model.
- MRZScanner MobileNetV4 spotting model, release `20240919`.
- MRZScanner specialist detection model, release `20250222`.
- MRZScanner specialist recognition model, release `20250221`.

The model and adapted inference code are distributed under the Apache License, Version 2.0. A copy is included as `Apache-2.0.txt`. Copyright and patent notices required by that license remain applicable. The original training datasets retain their own terms; those datasets are not bundled here.

Upstream projects:

- https://github.com/DocsaidLab/DocAligner
- https://github.com/DocsaidLab/MRZScanner
- https://github.com/DocsaidLab/Capybara
