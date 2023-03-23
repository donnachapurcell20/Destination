package com.example.destination;



import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.destination.routing.OSRMRoadManager;
import com.example.destination.routing.Road;
import com.example.destination.routing.RoadManager;
import com.example.destination.routing.RoadNode;

import org.osmdroid.api.IMapController;
import org.osmdroid.mapsforge.BuildConfig;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private MapView mapView;
    private EditText startEditText;
    private EditText endEditText;
    private Drawable nodeIcon;


    private class SearchTask extends AsyncTask<String, Void, Road> {

        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = ProgressDialog.show(MainActivity.this, "", "Searching route...");
        }

        @Override
        protected Road doInBackground(String... locations) {
            String startLocation = locations[0];
            String endLocation = locations[1];
            if (!startLocation.isEmpty() && !endLocation.isEmpty()) {
                // Get start and end points from geocoding
                Geocoder geocoder = new Geocoder(MainActivity.this);
                List<Address> startAddresses = null;
                try {
                    startAddresses = geocoder.getFromLocationName(startLocation, 1);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                List<Address> endAddresses = null;
                try {
                    endAddresses = geocoder.getFromLocationName(endLocation, 1);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (!startAddresses.isEmpty() && !endAddresses.isEmpty()) {
                    GeoPoint start = new GeoPoint(startAddresses.get(0).getLatitude(), startAddresses.get(0).getLongitude());
                    GeoPoint end = new GeoPoint(endAddresses.get(0).getLatitude(), endAddresses.get(0).getLongitude());

                    // Add start and end points to waypoints
                    ArrayList<GeoPoint> waypoints = new ArrayList<>();
                    waypoints.add(start);
                    waypoints.add(end);

                    // Get road between start and end points
                    RoadManager roadManager = new OSRMRoadManager(MainActivity.this, "Donnacha/1.0");
                    Road road = roadManager.getRoad(waypoints);

                    return road;
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Road road) {
            super.onPostExecute(road);
            progressDialog.dismiss();
            if (road != null && road.mStatus == Road.STATUS_OK) {
                // Build a Polyline with the route shape
                Polyline roadOverlay = RoadManager.buildRoadOverlay(road);

                // Add this Polyline to the overlays of your map
                mapView.getOverlays().add(roadOverlay);

                // Add markers for each step of the route
                for (int i = 0; i < road.mNodes.size(); i++) {
                    RoadNode node = road.mNodes.get(i);
                    Marker nodeMarker = new Marker(mapView);
                    nodeMarker.setPosition(node.mLocation);

                    // Determine the direction of the nodeIcon
                    int direction = 0; // 0 for straight, 1 for left, 2 for right
                    if (i < road.mNodes.size() - 1) {
                        RoadNode nextNode = road.mNodes.get(i + 1);
                        float bearing1 = (float) node.mLocation.bearingTo(nextNode.mLocation);
                        float bearing2 = mapView.getMapOrientation() + 360f;
                        float angle = bearing1 - bearing2;
                        if (angle < 0) {
                            angle += 360;
                        }
                        if (angle > 180) {
                            direction = 2;
                        } else if (angle > 0) {
                            direction = 1;
                        }
                    }

                    // Set the nodeIcon based on the direction
                    switch (direction) {
                        case "left":
                            nodeIcon = getResources().getDrawable(R.drawable.ic_left);
                            break;
                        case "right":
                            nodeIcon = getResources().getDrawable(R.drawable.ic_right);
                            break;
                        case "straight":
                            nodeIcon = getResources().getDrawable(R.drawable.ic_continue);
                            break;
                        default:
                            nodeIcon = getResources().getDrawable(R.drawable.ic_continue);
                    }

// Add markers for each step of the route
                    for (int i = 0; i < road.mNodes.size(); i++) {
                        RoadNode node = road.mNodes.get(i);
                        Marker nodeMarker = new Marker(mapView);
                        nodeMarker.setPosition(node.mLocation);
                        nodeMarker.setTitle("Step " + i);
                        nodeMarker.setSnippet(node.mInstructions);
                        nodeMarker.setSubDescription(Road.getLengthDurationText(MainActivity.this, node.mLength, node.mDuration));
                        // Set the appropriate icon for the node based on the direction
                        String direction = node.mManeuverType;
                        switch (direction) {
                            case "left":
                                nodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_left));
                                break;
                            case "right":
                                nodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_right));
                                break;
                            case "straight":
                                nodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_continue));
                                break;
                            default:
                                nodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_continue));
                        }
                        mapView.getOverlays().add(nodeMarker);
                    }
                }
            }
        }
    }

}







                    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // Initialize views
        mapView = findViewById(R.id.map);
        startEditText = findViewById(R.id.start_point);
        endEditText = findViewById(R.id.end_point);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.getController().setZoom(10);
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        nodeIcon = getResources().getDrawable(R.drawable.ic_continue);
        // Set the default location to London
        IMapController mapController = mapView.getController();
        mapController.setZoom(12.0);
        GeoPoint startPoint = new GeoPoint(53.0996218803593, -7.911131504579704);
        mapController.setCenter(startPoint);

        Button searchButton = findViewById(R.id.search_button);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String startLocation = startEditText.getText().toString();
                String endLocation = endEditText.getText().toString();
                new SearchTask().execute(startLocation, endLocation);
            }
        });
    }
    @Override
    protected void onPause() {

        // hide the keyboard in order to avoid getTextBeforeCursor on inactive InputConnection
        InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);

        inputMethodManager.hideSoftInputFromWindow(startEditText.getWindowToken(), 0);
        inputMethodManager.hideSoftInputFromWindow(endEditText.getWindowToken(), 0);

        super.onPause();
    }
}