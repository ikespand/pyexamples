"""Portable deterministic MRZ decoding; no image or OCR dependencies."""
import re

def check(value, digit):
    if not digit.isdigit():
        return False
    values = [0 if c == '<' else int(c) if c.isdigit() else ord(c)-55 for c in value]
    return str(sum(v * (7, 3, 1)[i % 3] for i, v in enumerate(values)) % 10) == digit


def decode(text):
    lines = [''.join(s.split()).upper() for s in text.strip().splitlines() if s.strip()]
    out = dict(status='invalid', normalizedLines=lines, fields={}, checks={}, warnings=[])
    shape = [len(s) for s in lines]
    if shape not in ([44, 44], [30, 30, 30]):
        out['warnings'] = ['Expected TD3 (2 × 44) or TD1 (3 × 30) complete lines']
        return out
    if any(re.fullmatch('[A-Z0-9<]+', s) is None for s in lines):
        return out
    a, b = lines[:2]
    if shape == [44, 44]:
        out['format'] = 'TD3'
        number, birth, expiry, sex, nationality = b[:9], b[13:19], b[21:27], b[20], b[10:13]
        names = a[5:]
        pairs = {'number': (number,b[9]), 'birth':(birth,b[19]), 'expiry':(expiry,b[27]),
                 'optional':(b[28:42],b[42]), 'composite':(b[:10]+b[13:20]+b[21:43],b[43])}
        if b[28:43] == '<'*15:
            pairs.pop('optional')
        type_ok = a[0] == 'P'
    else:
        out['format'] = 'TD1'
        number, birth, expiry, sex, nationality = a[5:14], b[:6], b[8:14], b[7], b[15:18]
        names = lines[2]
        pairs = {'number':(number,a[14]), 'birth':(birth,b[6]), 'expiry':(expiry,b[14]),
                 'composite':(a[5:30]+b[:7]+b[8:15]+b[18:29],b[29])}
        type_ok = a[0] in 'IAC'
    surname, _, given = names.partition('<<')
    out['fields'] = dict(document_type=a[:2].replace('<', ''), document_number=number.rstrip('<'), surname=surname.replace('<',' ').strip(),
        given_names=given.replace('<',' ').strip(), nationality=nationality, issuer=a[2:5],
        birth_date_raw=birth, expiry_date_raw=expiry, sex=sex)
    out['checks'] = {k: check(*v) for k,v in pairs.items()}
    # Preserve YYMMDD; validate calendar structure without asserting century.
    import datetime
    dates_ok = True
    for value in (birth, expiry):
        try:
            if not re.fullmatch(r'[0-9]{6}', value):
                raise ValueError('Date must be six digits')
            datetime.datetime.strptime('20'+value, '%Y%m%d')
        except ValueError:
            dates_ok = False
    out['checks']['structure'] = bool(type_ok and sex in ('M', 'F', '<') and dates_ok and
        re.fullmatch('[A-Z<]+', names) and re.fullmatch('[A-Z0-9<]{9}', number) and number.strip('<') and
        re.fullmatch('[A-Z<]{3}', nationality) and re.fullmatch('[A-Z<]{3}',a[2:5]))
    if all(out['checks'].values()):
        out['status'] = 'valid'
    out['warnings'] = ['Dates retain YYMMDD; century is not inferred. Names and country codes are not protected by check digits. Checksums do not authenticate identity.']
    if not names.endswith('<'):
        out['warnings'].append('The name may be truncated in the MRZ.')
    if pairs['number'][1] == '<':
        out['status'] = 'unsupported'
        out['warnings'].append('Extended document numbers are not supported in this prototype.')
    return out
