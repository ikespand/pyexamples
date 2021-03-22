# -*- coding: utf-8 -*-
"""
This is a simple example to demonstrate the usage of cv2 inside the kivy for 
its texture. This allow us to use any image manuplation and running inference.
Here, I use face detector on the cv2 image.

Usage: `python main.py` type "yes" and then click "submit"    
"""
import numpy as np
import time
import cv2
from kivy.app import App 
from kivy.uix.button import Button 
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.image import Image
from kivy.clock import Clock
from kivy.graphics.texture import Texture
from kivy.uix.screenmanager import ScreenManager, Screen
from kivy.lang import Builder

# %%

# Load the face classifier 
face_cascade = cv2.CascadeClassifier('haarcascade_frontalface_default.xml')

def face_detector(frame):
    """Detects the face from a video using a rather
    crude cv2 cascade methods.
    """
    gray_img = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

    faces = face_cascade.detectMultiScale(gray_img, 1.3, 5)

    # Draw a rectangle around the faces
    for (x, y, w, h) in faces:
        cv2.rectangle(frame, (x, y), (x+w, y+h), (0, 255, 0), 2)
    
    return frame

def adjust_cv_img(img, brightness = 100, contrast = 1):
    """Enhances the brightness and contrast for a given image
    """
    img = np.int16(img)
    img = img * (contrast/127+1) - contrast + brightness
    img = np.clip(img, 0, 255)
    img = np.uint8(img)
    return img

class CustomCvCamera(Image):
    """Extending the Image class of kivy for our cv2
    """
    def __init__(self, capture=None, fps=45.0, **kwargs):
        super(CustomCvCamera, self).__init__(**kwargs)
        # self.capture = cv2.VideoCapture("/sdcard2/python-apk/2.mp4")
        self.capture = cv2.VideoCapture(0)
        Clock.schedule_interval(self.update, 1.0 / fps)

    def update(self, dt):
        ret, frame = self.capture.read()
        if ret:
            #frame = adjust_cv_img(frame)
            frame = face_detector(frame)
            # convert it to texture
            buf1 = cv2.flip(frame, 0)
            buf = buf1.tobytes()
            image_texture = Texture.create(size=(frame.shape[1], frame.shape[0]), colorfmt='bgr')
            image_texture.blit_buffer(buf, colorfmt='bgr', bufferfmt='ubyte')
            # display image from the texture
            self.texture = image_texture

class CustomGrid(FloatLayout):
    """Extending FloadLayout for camera.
    """
    def __init__(self, **kwargs):
        super(CustomGrid, self).__init__(**kwargs)
        # self.cols = 1
        # 1st row 
        self.my_camera = CustomCvCamera()
        self.add_widget(self.my_camera) 
        # 2nd row 
        # Create a button for taking photograph
        self.camaraClick = Button(text="Take Photo")
        self.camaraClick.size_hint=(.5, .2)
        self.camaraClick.pos_hint={'x': .25, 'y':.75}
        # bind the button's on_press to on_camera_click
        self.camaraClick.bind(on_press=self.on_camera_click)
        self.add_widget(self.camaraClick)

    def on_camera_click(self, *args):
        """Capture the image when someone clicks on click button
        """
        self.camaraClick.opacity=0.5
        timestr = time.strftime("%Y%m%d_%H%M%S")
        self.my_camera.export_to_png("IMG_{}.png".format(timestr))


# We have 2 different screen, so for the same:
class MainWindow(Screen):
    pass


class SecondWindow(Screen):
    pass


class WindowManager(ScreenManager):
    pass


kv = Builder.load_file("my.kv")


# This is out main app
class FaceApp(App): 
    def build(self): 
        return kv


if __name__ == "__main__":
    myapp = FaceApp() 
    myapp.run() 
