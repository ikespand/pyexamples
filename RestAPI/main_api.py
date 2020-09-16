"""
ikespand@GitHub

Implemention of a REST API for our function which itself fetch data from the
OSM and do some calculation based on Flask.
"""
import flask
from flask import request, render_template
from request_data_osm import FindToilets

# http://127.0.0.1:5000/api/v1/toilets?src=12.973132,77.601479&radius=500
# results = near_toilets("12.973132,77.601479",None)
# %%
app = flask.Flask(__name__)
app.config["DEBUG"] = True


def near_toilets(coords_string, radius):
    """Returns nearby toiletse"""
    lat = float(coords_string.split(',')[0])
    lon = float(coords_string.split(',')[1])
    results = FindToilets(lat=lat, lon=lon,
                          radius=radius).provide_dataframe()
    return results


@app.route('/')
def my_form():
    """Home page of our api"""
    return render_template('my-form.html')


@app.route('/', methods=['POST'])
def my_form_post():
    """Take the data from our form and provide the results."""
    src = request.form['src']
    if not len(src) > 0:
        return "Error: No or bad source field!"
    elif 'radius' in request.form['radius']:
        radius = request.form['radius']
        results = near_toilets(src, radius)
        return results.__geo_interface__
    else:
        results = near_toilets(src, None)
        return results.__geo_interface__


@app.route('/api/v1/toilets', methods=['GET'])
def api_id():
    """Returns the data with web request."""
    if 'src' in request.args:
        src = request.args['src']
    else:
        return "Error: No src field provided."

    if 'radius' in request.args:
        radius = request.args['radius']
    else:
        return "Error: No radius field provided."

    results = near_toilets(src, radius)

    return results.__geo_interface__


# %%
if __name__ == "__main__":
    app.run()
