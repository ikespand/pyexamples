"""Download and SHA-256 verify the pinned local inference models."""

import hashlib
import json
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
MODEL_DIR = ROOT / ".tools" / "docsaid-models"
MANIFEST = ROOT / "models" / "specialist_models.json"


def main():
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    for name, item in json.loads(MANIFEST.read_text()).items():
        target = MODEL_DIR / f"{name}.onnx"
        if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() == item["sha256"]:
            print(name, "verified")
            continue

        temporary = target.with_suffix(".download")
        urllib.request.urlretrieve(item["url"], temporary)
        if (
            temporary.stat().st_size != item["bytes"]
            or hashlib.sha256(temporary.read_bytes()).hexdigest() != item["sha256"]
        ):
            temporary.unlink(missing_ok=True)
            raise RuntimeError(f"Model integrity check failed: {name}")
        temporary.replace(target)
        print(name, "downloaded and verified")


if __name__ == "__main__":
    main()
