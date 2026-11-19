"""ASGI upload contract tests with synthetic pixels; no private fixture or models."""
import io,json,unittest
from unittest.mock import patch
import numpy as np
from PIL import Image
from edge.app import app


class MacUploadTests(unittest.IsolatedAsyncioTestCase):
    async def request(self,body,origin='http://127.0.0.1:8000'):
        scope=dict(type='http',asgi={'version':'3.0'},http_version='1.1',method='POST',scheme='http',path='/scan',raw_path=b'/scan',query_string=b'',root_path='',headers=[(b'host',b'127.0.0.1:8000'),(b'origin',origin.encode())],server=('127.0.0.1',8000),client=('127.0.0.1',1234))
        messages=[]
        async def receive():return {'type':'http.request','body':body,'more_body':False}
        async def send(message):messages.append(message)
        await app(scope,receive,send)
        return messages

    async def test_exif_orientation_applied_before_scanner(self):
        im=Image.new('RGB',(12,24),(210,60,30));exif=im.getexif();exif[274]=6
        b=io.BytesIO();im.save(b,format='JPEG',exif=exif)
        with patch('edge.app.run',return_value={'status':'no_mrz'}) as run:
            messages=await self.request(b.getvalue())
            actual=run.call_args.args[0]
            self.assertEqual((12,24,3),actual.shape)
            # Scanner input is BGR, not RGB.
            self.assertGreater(int(actual[0,0,2]),int(actual[0,0,0]))
            self.assertEqual(200,messages[0]['status'])
            self.assertIn((b'cache-control',b'no-store'),messages[0]['headers'])

    async def test_invalid_image_never_reaches_scanner(self):
        with patch('edge.app.run') as run:
            messages=await self.request(b'not an image')
            self.assertEqual(400,messages[0]['status']);run.assert_not_called()

    async def test_foreign_origin_rejected(self):
        with patch('edge.app.run') as run:
            messages=await self.request(b'anything','https://example.invalid')
            self.assertEqual(403,messages[0]['status']);run.assert_not_called()

if __name__=='__main__':unittest.main()
