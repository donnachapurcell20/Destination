package com.example.destination;



import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private MapView mapView;
    private EditText startEditText;
    private EditText endEditText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        mapView = findViewById(R.id.mapView);
        startEditText = findViewById(R.id.start_point);
        endEditText = findViewById(R.id.end_point);

        // Set up map
        mapView.setMultiTouchControls(true);
        IMapController mapController = mapView.getController();
        mapController.setZoom(9);


        // Set up search button
        Button searchButton = findViewById(R.id.search_button);
        searchButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                String startLocation = startEditText.getText().toString();
                String endLocation = endEditText.getText().toString();
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
                        if (road.mStatus != Road.STATUS_OK) {
                            Toast.makeText(MainActivity.this, "Error when loading the road - status=" + road.mStatus, Toast.LENGTH_SHORT).show();
                        } else {
                            // Build a Polyline with the route shape
                            Polyline roadOverlay = RoadManager.buildRoadOverlay(road);

                            // Add this Polyline to the overlays of your map
                            mapView.getOverlays().add(roadOverlay);

                            // Show the route steps on the map
                            Drawable nodeIcon = getResources().getDrawable(R.drawable.img);
                            for (int i = 0; i < road.mNodes.size(); i++) {
                                RoadNode node = road.mNodes.get(i);
                                Marker nodeMarker = new Marker(mapView);
                                nodeMarker.setPosition(node.mLocation);
                                nodeMarker.setIcon(nodeIcon);
                                nodeMarker.setTitle("Step " + i);
                                mapView.getOverlays().add(nodeMarker);

                                // Fill the bubbles
                                nodeMarker.setSnippet(node.mInstructions);
                                nodeMarker.setSubDescription(Road.getLengthDurationText(MainActivity.this, node.mLength, node.mDuration));
                                Drawable icon = getResources().getDrawable(R.drawable.ic_continue);
                                nodeMarker.setImage(icon);
                            }

                            // Refresh the map
                            mapView.invalidate();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Could not find start or end location", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }
}