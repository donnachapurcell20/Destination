package com.example.destination;



import static android.content.ContentValues.TAG;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.destination.routing.OSRMRoadManager;
import com.example.destination.routing.Road;
import com.example.destination.routing.RoadManager;
import com.example.destination.routing.RoadNode;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.mapsforge.BuildConfig;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import android.Manifest;




public class MainActivity extends AppCompatActivity
{
    FirebaseDatabase firebaseDatabase;
    FirebaseFirestore firebaseFirestore;
    private Spinner categorySpinner;
    private EditText longitudeme, latitudeme;

    private DatabaseReference mDatabase;
    private MapView mapView;
    private EditText startEditText;
    private EditText endEditText;
    private Drawable nodeIcon;
    private Button submitButton;
    private Marker marker;
    private AlertDialog alertDialog;
    private EditText categoryEditText;
    private EditText latitudeEditText;
    private EditText longitudeEditText;
    private LinearLayout searchPanel;
    private ImageButton imageButton;
    private Button launchFormButton;
    private LocationManager locationManager;
    private LocationListener locationListener;

    private boolean isGPSEnabled;
    private boolean isNetworkEnabled;
    private static final int PERMISSION_REQUEST_LOCATION = 101;
    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 100;







    private class GeocoderTask extends AsyncTask<String, Void, Road> {

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

                    // Set the icon according to the maneuver
                    switch (node.mManeuverType) {
                        case RoadNode.MANEUVER_TURN_LEFT:
                            nodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_turn_left));
                            break;
                        case RoadNode.MANEUVER_STRAIGHT:
                            nodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_continue));
                            break;
                        case RoadNode.MANEUVER_RIGHT:
                            nodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_turn_left));
                            break;
                        case RoadNode.MANEUVER_LEFT:
                            nodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_turn_right));
                            break;
                        case RoadNode.MANEUVER_ROUNDABOUT:
                            nodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_roundabout));
                            break;
                        default:
                            nodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_continue));
                            break;
                    }

                    nodeMarker.setTitle("Step " + i);
                    nodeMarker.setSnippet(node.mInstructions);
                    nodeMarker.setSubDescription(Road.getLengthDurationText(MainActivity.this, node.mLength, node.mDuration));
                    mapView.getOverlays().add(nodeMarker);
                }

                // Zoom to the bounds of the route
                BoundingBox routeBounds = roadOverlay.getBounds();
                mapView.zoomToBoundingBox(routeBounds, true);


                // Center the map on the starting point of the route
                GeoPoint startPoint = road.mNodes.get(0).mLocation;
                mapView.getController().setCenter(startPoint);
            } else {
                // Display an error message to the user
                Toast.makeText(MainActivity.this, "Unable to calculate route.", Toast.LENGTH_SHORT).show();
            }
        }
    }





        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

//        FirebaseDatabase database = FirebaseDatabase.getInstance();
//        DatabaseReference myRef = database.getReference("locations");
//
//        // Create a Firebase database reference
//        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("locations");

            // Get the system's LocationManager service
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

// Check if the app has permission to access the user's location
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted, you can use the method that requires the permission here
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

                //Once you have permission, following code gets the user's current location
                Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                //Update the user's location on the map
                GeoPoint userLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                mapView.getController().animateTo(userLocation);

                //The context variable needs to be initialized with a valid Context object
                Context context = MainActivity.this;
                // Create a new location overlay
                MyLocationNewOverlay myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(context), mapView);

                // Enable the overlay to show the user's location
                myLocationOverlay.enableMyLocation();

                // Add the overlay to the map view
                mapView.getOverlays().add(myLocationOverlay);
            } else {
                // Permission is not granted, request the permission
                ActivityCompat.requestPermissions(MainActivity.this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, MY_PERMISSIONS_REQUEST_LOCATION);
            }


            // Initialize views
            mapView = findViewById(R.id.map);
            startEditText = findViewById(R.id.start_point);
            endEditText = findViewById(R.id.end_point);
            mapView.setTileSource(TileSourceFactory.MAPNIK);
//        mapView.getController().setZoom(10);
            mapView.setBuiltInZoomControls(true);
            mapView.setMultiTouchControls(true);
            Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);



            // Get the user's current location
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            GeoPoint userLocation = new GeoPoint(location.getLatitude(), location.getLongitude());

            // Add a marker to the user's location
            Marker userMarker = new Marker(mapView);
            userMarker.setPosition(userLocation);
            mapView.getOverlays().add(userMarker);















            // Set the default location to London
            IMapController mapController = mapView.getController();
//        mapController.setZoom(12.0);
            mapView.getController().setZoom(12.0);
            GeoPoint startPoint = new GeoPoint(53.0996218803593, -7.911131504579704);
            mapController.setCenter(startPoint);



            //Creating a new marker that is to be used by the user
            marker = new Marker(mapView);
            marker.setIcon(getResources().getDrawable(R.drawable.ic_marker));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            // Find the expand button in your layout
            ImageButton expandButton = findViewById(R.id.image_button);
            searchPanel = findViewById(R.id.search_panel);

            // Retrieve data from Firebase Realtime Database
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("locations");
            ref.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    List<VanCameraLocations> locationList = new ArrayList<>();
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        String category = snapshot.child("category").getValue(String.class);
                        double latitude = snapshot.child("latitude").getValue(Double.class);
                        double longitude = snapshot.child("longitude").getValue(Double.class);
                        VanCameraLocations location = new VanCameraLocations(category, latitude, longitude);
                        locationList.add(location);
                    }
                    // Display markers on mapview
                    Drawable markerIcon = getResources().getDrawable(R.drawable.img);
                    for (VanCameraLocations location : locationList) {
                        Marker marker = new Marker(mapView);
                        marker.setPosition(new GeoPoint(location.getLatitude(), location.getLongitude()));
                        marker.setTitle(location.getCategory());
                        marker.setIcon(markerIcon);
                        mapView.getOverlayManager().add(marker);
                    }
                }


                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.e(TAG, "onCancelled", databaseError.toException());
                }
            });


            // Check if the image button is null
            if (expandButton != null) {
                // Set the click listener for the expand button
                expandButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Toggle the visibility of the search panel
                        if (searchPanel.getVisibility() == View.VISIBLE) {
                            searchPanel.setVisibility(View.GONE);
                            expandButton.setImageResource(R.drawable.ic_baseline_expand_more_24);
                        } else {
                            searchPanel.setVisibility(View.VISIBLE);
                            expandButton.setImageResource(R.drawable.ic_baseline_expand_less_24);
                        }
                        // Adjust the layout params of the map view to fill the remaining space
                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mapView.getLayoutParams();
                        if (searchPanel.getVisibility() == View.VISIBLE) {
                            params.addRule(RelativeLayout.BELOW, R.id.search_panel);
                        } else {
                            params.addRule(RelativeLayout.BELOW, 0);
                        }
                        mapView.setLayoutParams(params);
                    }
                });
            }

//        mapView.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                if (event.getAction() == MotionEvent.ACTION_UP) {
//                    // Get the location of the touch
//                    GeoPoint touchedPoint = (GeoPoint) mapView.getProjection().fromPixels((int)event.getX(), (int)event.getY());
//
//                    // Create the marker
//                    Marker marker = new Marker(mapView);
//                    marker.setPosition(touchedPoint);
//
//                    // Show the confirmation dialog
//                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
//                    builder.setMessage("Add a marker here?");
//                    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
//                        public void onClick(DialogInterface dialog, int id) {
//                            // Launch your form activity and pass the latitude and longitude of the clicked position
//                            Intent intent = new Intent(MainActivity.this, MarkerOnMapActivity.class);
//                            intent.putExtra("latitude", marker.getPosition().getLatitude());
//                            intent.putExtra("longitude", marker.getPosition().getLongitude());
//                            startActivity(intent);
//
//                        }
//
//                    });
//                    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
//                        public void onClick(DialogInterface dialog, int id) {
//                            mapView.getOverlays().remove(marker);
//                        }
//                    });
//                    AlertDialog alertDialog = builder.create();
//                    alertDialog.show();
//
//                    // Add the marker to the map
//                    mapView.getOverlays().add(marker);
//                    mapView.invalidate();
//                }
//                return true;
//            }
//        });


            Button searchButton = findViewById(R.id.search_button);
            searchButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    // Hide the keyboard
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

                    // Start the search task
                    String startLocation = startEditText.getText().toString();
                    String endLocation = endEditText.getText().toString();
                    //new SearchTask().execute(startLocation, endLocation);
                    new GeocoderTask().execute(startLocation, endLocation);


                }
            });
            startEditText.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        startEditText.requestFocus();
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.showSoftInput(startEditText, InputMethodManager.SHOW_IMPLICIT);
                        return true;
                    }
                    return false;
                }
            });

            endEditText.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        endEditText.requestFocus();
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.showSoftInput(endEditText, InputMethodManager.SHOW_IMPLICIT);
                        return true;
                    }
                    return false;
                }
            });




        }


    private void toggleSearchPanel()
    {
        if (searchPanel.getVisibility() == View.VISIBLE) {
            // Hide search panel
            searchPanel.setVisibility(View.GONE);

            // Change image button icon
            imageButton.setImageResource(R.drawable.ic_baseline_expand_more_24);

            // Update map view layout parameters
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            );
            mapView.setLayoutParams(params);
        } else {
            // Show search panel
            searchPanel.setVisibility(View.VISIBLE);

            // Change image button icon
            imageButton.setImageResource(R.drawable.ic_baseline_expand_less_24);

            // Update map view layout parameters
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1
            );
            mapView.setLayoutParams(params);
        }
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