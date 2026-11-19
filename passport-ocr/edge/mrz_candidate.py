"""Load the pinned ONNX models used by the page-first web scanner."""
import hashlib,json,time
from pathlib import Path
import onnxruntime as ort
from .mrz_specialist import SpecialistScanner, MODELS


def verified_model(name):
    manifest=Path(__file__).resolve().parent.parent/'models/specialist_models.json'
    expected=json.loads(manifest.read_text())[name]['sha256'];path=MODELS/(name+'.onnx')
    if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest()!=expected:
        raise RuntimeError('Pinned candidate model missing or changed: '+name)
    return str(path)

class DocumentAligner:
    def __init__(self, num_threads=2):
        options=ort.SessionOptions();options.intra_op_num_threads=num_threads
        self.model=ort.InferenceSession(verified_model('align'),options,providers=['CPUExecutionProvider'])

class SpottingRecognizer:
    chars=['<PAD>','<EOS>']+list('ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<&')
    def __init__(self, num_threads=2):
        options=ort.SessionOptions();options.intra_op_num_threads=num_threads
        self.model=ort.InferenceSession(verified_model('spot'),options,providers=['CPUExecutionProvider'])


class CandidateScanner:
    def __init__(self):
        start=time.perf_counter()
        self.aligner=DocumentAligner();self.spotter=SpottingRecognizer();self.specialist=SpecialistScanner()
        self.initialization_ms=(time.perf_counter()-start)*1000
