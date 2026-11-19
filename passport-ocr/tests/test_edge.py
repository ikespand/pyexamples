import unittest
from edge.decoder import decode

TD3='P<UTOERIKSSON<<ANNA<MARIA'.ljust(44,'<')+'\nL898902C36UTO7408122F1204159ZE184226B<<<<<10'
TD1='I<UTOD231458907<<<<<<<<<<<<<<<\n7408122F1204159UTO<<<<<<<<<<<6\nERIKSSON<<ANNA<MARIA<<<<<<<<<<'

class PipelineTests(unittest.TestCase):
    def test_td3(self):
        r=decode(TD3)
        self.assertEqual(r['status'],'valid')
        self.assertEqual(r['fields']['document_number'],'L898902C3')
    def test_td1(self):
        r = decode(TD1)
        self.assertEqual(r['status'], 'valid')
        self.assertEqual(r['fields']['document_number'], 'D23145890')
        self.assertEqual(r['fields']['surname'], 'ERIKSSON')
        self.assertEqual(r['fields']['given_names'], 'ANNA MARIA')
        self.assertEqual(r['fields']['birth_date_raw'], '740812')
        self.assertEqual(r['fields']['expiry_date_raw'], '120415')
        self.assertEqual(r['fields']['nationality'], 'UTO')
    def test_td3_all_fields(self):
        r = decode(TD3)
        self.assertEqual(r['fields'], {'document_type': 'P', 'document_number': 'L898902C3', 'surname': 'ERIKSSON', 'given_names': 'ANNA MARIA', 'nationality': 'UTO', 'issuer': 'UTO', 'birth_date_raw': '740812', 'expiry_date_raw': '120415', 'sex': 'F'})
        self.assertTrue(all(r['checks'].values()))
    def test_name_digits_rejected(self):
        self.assertEqual(decode(TD3.replace('ANNA', 'ANN4'))['status'], 'invalid')
    def test_invalid_date_rejected_even_with_correct_checks(self):
        # Construct a checksum-consistent but impossible date.
        from edge.decoder import check
        first, line = TD3.splitlines()
        line = line[:13] + '991332' + line[19:]
        digit = next(str(n) for n in range(10) if check('991332', str(n)))
        line = line[:19] + digit + line[20:]
        composite = line[:10] + line[13:20] + line[21:43]
        digit = next(str(n) for n in range(10) if check(composite, str(n)))
        line = line[:43] + digit
        self.assertEqual(decode(first + '\n' + line)['status'], 'invalid')
    def test_corruption(self):
        self.assertEqual(decode(TD3.replace('L898','L899'))['status'],'invalid')
    def test_country_digit_rejected(self):
        self.assertEqual(decode(TD3.replace('6UTO','6UT0'))['status'],'invalid')
    def test_no_padding(self):
        self.assertEqual(decode(TD3[:-1])['status'],'invalid')
    def test_name_not_protected(self):
        self.assertEqual(decode(TD3.replace('ANNA','ANNE'))['status'],'valid')
if __name__=='__main__': unittest.main()
