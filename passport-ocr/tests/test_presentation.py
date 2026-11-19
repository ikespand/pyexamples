import unittest
from datetime import date
from edge.decoder import decode
from edge.presentation import country_name,readable_date,display_fields

class PresentationTests(unittest.TestCase):
    def test_german_padding_is_country_not_corrupt_ocr(self):
        # Synthetic specimen with Germany's ICAO code; no real identity fixture.
        mrz='P<D<<ERIKSSON<<ANNA<MARIA'.ljust(44,'<')+'\nL898902C36D<<7408122F1204159ZE184226B<<<<<10'
        result=decode(mrz)
        self.assertEqual('valid',result['status'])
        before=dict(result['fields'])
        shown={r['label']:r for r in display_fields(result['fields'],date(2026,11,14))}
        self.assertEqual('Germany',shown['Nationality']['value'])
        self.assertEqual('Germany',shown['Issuing country']['value'])
        self.assertEqual('ANNA MARIA',shown['Given names']['value'])
        self.assertEqual('12 Aug 1974',shown['Date of birth']['value'])
        self.assertEqual('15 Apr 2012',shown['Expiry date']['value'])
        self.assertIn('inferred',shown['Date of birth']['note'])
        self.assertEqual(before,result['fields'])
        self.assertEqual('D<<',result['fields']['issuer'])
        self.assertEqual(mrz.splitlines(),result['normalizedLines'])

    def test_regular_and_unknown_codes(self):
        self.assertEqual('India',country_name('IND'))
        self.assertEqual('Germany',country_name('DEU'))
        self.assertEqual('United States',country_name('USA'))
        self.assertEqual('Unknown code (QZZ)',country_name('QZZ'))
        self.assertEqual('Unknown code (I<N)',country_name('I<N'))
        self.assertEqual('Not specified',country_name('<<<'))

    def test_birth_and_expiry_use_different_century_rules(self):
        today=date(2026,11,14)
        self.assertEqual('01 Jan 1930',readable_date('300101','birth',today)[0])
        self.assertEqual('01 Jan 2030',readable_date('300101','expiry',today)[0])
        self.assertEqual('01 Jan 2004',readable_date('040101','birth',today)[0])
        self.assertIn('inferred',readable_date('040101','birth',today)[1])

    def test_calendar_validation_and_unknown_digits(self):
        self.assertEqual('29 Feb 2000',readable_date('000229','birth',date(2026,11,14))[0])
        self.assertEqual('990229',readable_date('990229','birth',date(2026,11,14))[0])
        self.assertEqual('74<<<<',readable_date('74<<<<','birth')[0])

    def test_future_century_boundary(self):
        self.assertEqual('01 Jan 2101',readable_date('010101','expiry',date(2099,1,1))[0])
        self.assertEqual('01 Jan 2001',readable_date('010101','birth',date(2099,1,1))[0])

if __name__=='__main__':unittest.main()
