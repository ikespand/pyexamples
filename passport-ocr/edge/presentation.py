"""Human-readable MRZ presentation; raw decoder fields remain lossless.

Germany's ICAO code D is padded to D<< (ICAO Doc 9303 parts 3/13).
Country lookup is local. Date centuries are display estimates, never OCR edits.
"""
from datetime import date
import re
import pycountry

# ICAO exceptions to ISO alpha-3. Preserve unknown codes rather than guessing.
SPECIAL_COUNTRIES = {
    'D<<': 'Germany', 'D': 'Germany',
    'GBD': 'British overseas territories citizen',
    'GBN': 'British national (overseas)',
    'GBO': 'British overseas citizen',
    'GBP': 'British protected person',
    'GBS': 'British subject',
    'XXA': 'Stateless', 'XXB': 'Refugee', 'XXC': 'Refugee',
    'XXX': 'Unspecified nationality', 'UTO': 'Utopia (specimen)',
    'UNK': 'Kosovo (UN-issued travel document)',
    'UNO': 'United Nations', 'UNA': 'United Nations specialized agency',
}
MONTHS = ('Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec')


def country_name(raw):
    if raw in SPECIAL_COUNTRIES:
        return SPECIAL_COUNTRIES[raw]
    if raw == '<<<' or not raw:
        return 'Not specified'
    # Do not strip arbitrary fillers: a malformed or unknown code stays visible.
    item = pycountry.countries.get(alpha_3=raw) if re.fullmatch('[A-Z]{3}',raw) else None
    if item is None:
        return 'Unknown code (%s)' % raw
    return getattr(item,'common_name',item.name)


def readable_date(raw, kind, today=None):
    """Latest nonfuture DOB, closest-century expiry; both explicitly estimated.

Never force an expired document into the future. Full birth century cannot be
proved from MRZ alone (e.g. a centenarian); the UI must retain the note.
"""
    today = today or date.today()
    if not re.fullmatch(r'[0-9]{6}',raw or ''):
        return raw or 'Not specified', 'Incomplete MRZ date; no year inferred.'
    yy,month,day = int(raw[:2]),int(raw[2:4]),int(raw[4:])
    century = today.year // 100 * 100
    candidates = []
    for base in (century-100,century,century+100):
        try:
            candidate = date(base+yy,month,day)
        except ValueError:
            continue
        if kind != 'birth' or candidate <= today:
            candidates.append(candidate)
    if not candidates:
        return raw, 'Invalid or unresolved MRZ date.'
    chosen = max(candidates) if kind == 'birth' else min(candidates,key=lambda d:abs((d-today).days))
    return '%02d %s %04d' % (chosen.day,MONTHS[chosen.month-1],chosen.year), 'Century inferred from the two-digit MRZ year; check against the document.'


def display_fields(fields, today=None):
    def row(label,value,note=None):
        result={'label':label,'value':value or 'Not specified'}
        if note: result['note']=note
        return result
    birth,birth_note=readable_date(fields.get('birth_date_raw',''),'birth',today)
    expiry,expiry_note=readable_date(fields.get('expiry_date_raw',''),'expiry',today)
    kind=fields.get('document_type','')
    return [
        row('Document type',{'P':'Passport','I':'Identity card','A':'Identity document','C':'Identity document'}.get(kind,kind)),
        row('Document number',fields.get('document_number','')),
        row('Surname',fields.get('surname','')),
        row('Given names',fields.get('given_names','')),
        row('Nationality',country_name(fields.get('nationality',''))),
        row('Issuing country',country_name(fields.get('issuer',''))),
        row('Date of birth',birth,birth_note),
        row('Expiry date',expiry,expiry_note),
        row('Sex',{'M':'Male','F':'Female','<':'Not specified'}.get(fields.get('sex'),fields.get('sex',''))),
    ]
