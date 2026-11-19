"""Benchmark the debug APK using app-private files; output excludes image and MRZ text."""
import argparse
import json
import subprocess
import time
import statistics
import re
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--manifest', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--repeat', type=int, default=3)
args = parser.parse_args()
if args.repeat < 1:
    parser.error('--repeat must be at least 1')
adb = ['adb', '-s', args.serial]
def call(*arguments, data=None):
    return subprocess.run(adb + list(arguments), input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True, timeout=30).stdout
call('shell','run-as com.example.mrz mkdir -p files/benchmark')
records=[]
def save_report():
    args.output.parent.mkdir(parents=True,exist_ok=True)
    times=sorted(r['timing']['total'] for r in records if r.get('timing'))
    summary=dict(runs=len(records), exact=sum(r.get('exactNormalized') is True for r in records),
                 evaluated=sum(r.get('exactNormalized') is not None for r in records),
                 falseValid=sum(r.get('falseValid') is True for r in records),
                 accepted=sum(r.get('accepted') is True for r in records),
                 exactAccepted=sum(r.get('exactAccepted') is True for r in records),
                 wrongAccepted=sum(r.get('wrongAccepted') is True for r in records),
                 review=sum(r['status']=='review' for r in records),
                 timeouts=sum(r['status']=='timeout' for r in records),
                 falseValidEvaluated=sum(r.get('falseValid') is not None for r in records),
                 awakeAtStart=sum(r.get('wakefulness')=='Awake' for r in records),
                 pipelineMedianMs=statistics.median(times) if times else None,
                 pipelineP95Ms=times[max(0, (95*len(times)+99)//100-1)] if times else None)
    args.output.write_text(json.dumps(dict(model='page-first-cascade',device=args.serial,mode='full-page',summary=summary,results=records),indent=2))

try:
    for index, item in enumerate(json.loads(args.manifest.read_text())):
        image=(args.manifest.parent / item['image']).read_bytes()
        for attempt in range(args.repeat):
            # Isolate benchmark runs from an active camera activity and OCR worker.
            call('shell','am','force-stop','com.example.mrz')
            call('shell','run-as com.example.mrz sh -c "cat > files/benchmark/input.jpg"',data=image)
            call('shell','run-as com.example.mrz sh -c ": > files/benchmark/result.json"')
            power=call('shell','dumpsys','power').decode('utf-8','replace')
            match=re.search(r'mWakefulness=(\w+)',power)
            wakefulness=match.group(1) if match else 'unknown'
            start=time.monotonic()
            call('shell','am','start','-W','-n','com.example.mrz/.MainActivity','--ez','benchmark','true')
            result=None
            deadline=time.monotonic()+60
            while time.monotonic()<deadline:
                text=call('exec-out','run-as','com.example.mrz','cat','files/benchmark/result.json')
                if text:
                    try: result=json.loads(text);break
                    except json.JSONDecodeError: pass
                time.sleep(.25)
            if result is None:
                records.append(dict(case=index,attempt=attempt,status='timeout',wakefulness=wakefulness,exactNormalized=False if item.get('lines') else None))
                raise RuntimeError('Benchmark timeout; partial metrics retained')
            if result.get('error'): raise RuntimeError('Device benchmark reported an error; no private output printed')
            end_power=call('shell','dumpsys','power').decode('utf-8','replace')
            end_match=re.search(r'mWakefulness=(\w+)',end_power)
            expected=item.get('lines');lines=result.get('lines',[])
            raw='\n'.join(''.join(line.split()).upper() for line in result.get('rawText','').splitlines() if line.strip())
            accepted=result.get('status')=='valid'
            exact=(lines==expected) if expected else None
            rows=dict(case=index,attempt=attempt,wakefulness=wakefulness,wakefulnessAtEnd=end_match.group(1) if end_match else 'unknown',status=result.get('status'),accepted=accepted,pageFound=result.get('pageFound'),candidateCount=result.get('candidateCount'),
                      timing=result.get('timingsMs'),deskewDegrees=result.get('deskewDegrees'),initializationMs=result.get('initializationMs'),imageLoadMs=result.get('imageLoadMs'),recognitionAttempts=result.get('recognitionAttempts'),imageWidth=result.get('imageWidth'),imageHeight=result.get('imageHeight'),wallMs=round((time.monotonic()-start)*1000),
                      exactNormalized=exact,exactAccepted=(accepted and exact) if expected else None,
                      exactRaw=(raw=='\n'.join(expected)) if expected else None,
                      falseValid=(accepted and lines!=expected) if expected else (accepted if item.get('negative') else None),
                      wrongAccepted=(accepted and lines!=expected) if expected else (accepted if item.get('negative') else None))
            records.append(rows)
            save_report()
            print('case',index,'run',attempt,rows['status'],'exact',rows['exactNormalized'],'ms',rows['timing']['total'],flush=True)
finally:
    save_report()
    # Stop workers before clearing, so a timed-out worker cannot rewrite output.
    call('shell','am','force-stop','com.example.mrz')
    # Clear input and OCR output, including after an error.
    call('shell','run-as com.example.mrz sh -c ": > files/benchmark/input.jpg; : > files/benchmark/result.json"')
