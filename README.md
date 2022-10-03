# pyexamples
Some use-case examples to serve as a documentation. 

## Index
1. **RestAPI**: This example illustrate how one can build a simple REST interface to exachnge data. A full documentation can be found [here](https://ikespand.github.io/posts/RestAPI_with_Python/ "RestAPI") about this example.
2. **NavigationApp_Rasta_Flask**: A custom navigation app in REST API fashion. It usage OpenTripPlanner's API to fetch the data with the help of rasta. You can also use map view. 
3. **kivy-cv2**: [Kivy](https://kivy.org/ "Kivy") is a great framework to build cross-platform GUI (including android, iOS, Windows). To be able to run ML model, we often use open-cv in python, therefore, this example shows how one can use cv2 library inside kivy and convert the image into kivy's original texture. It has a minimal working example with one main (where user need to write "yes" and click on submit to see camera) and one camera screen. Additionally, it has a button which a user can click to capture image from camera. This is not very clean but shows the usage :)
4. **valhalla-python**: Valhalla is a great opensource & multimodal routing engine. It offers several API ranging from navigation to map-matching. One can find about it from its [documentation](https://valhalla.readthedocs.io/en/latest/ "documentation").
	a. **map_matching_meli_valhalla.ipynb** is a notebook to shows the map matching. Map matching is a process by which we match raw GPS points to actual map. One of the library in Valhalla, called Meili does this part. To use this notebook, one needs to have running server for Valhalla. A complete documentation about setting up Valhalla and Meili can be found [on my blog page](map_matching_meli_valhalla.ipynb "here").
	b. Make a request, if a user, you need something.
5. **data-science**: This example is for a sample data analytics cum machine learning task for a time-series data related to bike rental.
6. **youtube**: Using YouTube API to scrap some analytics and thumbnails. Not the best implementation as not directly using YouTube's official client.
7. **ds-stockmarket**: A notebook where we predict the opening price of a stock using its past values. Here, LSTM and ESN are compared. This correspond to [this blog](https://ikespand.github.io/posts/ml-for-stock-market-1/). 