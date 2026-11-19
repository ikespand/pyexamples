import unittest
import cv2
import numpy as np
from edge.document_edges import rectify_document

class DocumentEdgeTests(unittest.TestCase):
 def test_oblique_page_returns_valid_warp_and_transform(self):
  image=np.zeros((500,700,3),np.uint8)
  corners=np.float32([[150,80],[580,110],[540,400],[100,380]])
  cropped,transform=rectify_document(image,corners)
  self.assertGreater(cropped.shape[0],250);self.assertGreater(cropped.shape[1],380)
  projected=cv2.perspectiveTransform(corners.reshape(1,4,2),transform)[0]
  self.assertLess(np.linalg.norm(projected[0]),2)
 def test_degenerate_quad_is_rejected(self):
  self.assertEqual(rectify_document(np.zeros((5,5,3),np.uint8),np.zeros((4,2),np.float32)),(None,None))
