import unittest
import numpy as np
from edge.mrz_specialist import decode_tokens,warp,order

class SpecialistTests(unittest.TestCase):
    def test_sequence_preserves_repeated_fillers_and_stops_at_eos(self):
        logits=np.zeros((9,40));logits[np.arange(9),[3,39,39,2,4,0,1,5,5]]=1
        self.assertEqual(decode_tokens(logits),['A<<','B'])
    def test_rejects_wrong_class_count(self):
        with self.assertRaises(ValueError):decode_tokens(np.zeros((3,39)))
    def test_degenerate_crop(self):
        self.assertIsNone(warp(np.zeros((10,10,3),np.uint8),np.zeros((4,2),np.float32)))
    def test_corner_order(self):
        q=np.float32([[9,8],[1,2],[1,8],[9,2]])
        np.testing.assert_array_equal(order(q),[[1,2],[9,2],[9,8],[1,8]])
