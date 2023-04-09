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
//import android.location.LocationRequest;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
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
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.annotations.Nullable;
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

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.android.gms.location.LocationRequest;


import org.osmdroid.views.overlay.Marker;

import android.Manifest;


public class MainActivity extends AppCompatActivity implements LocationListener {
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
    MotionEvent downEvent = null;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private List<GeoPoint> waypoints;
    private LocationManager locationManager;
    private Location lastLocation;
    private Polyline roadOverlay;
    private int[] distances = {500, 400, 300, 200, 100, 50};
    private int currentDistanceIndex = 0;
    private boolean isDirectionNotified = false;
    // Define a global variable for the user's location
    private Location userLocation;

    // Declare the location provider and overlay
    MyLocationNewOverlay locationOverlay;
    GpsMyLocationProvider provider;
    // declare nextPoint as a member variable
    private GeoPoint nextPoint;
    private TextView countdownTextBox;





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
        Location nextPointLocation = new Location("nextPoint");

        TextView countdownTextBox = findViewById(R.id.countdownTextBox);





        // set the user agent for OsmDroid
        Configuration.getInstance().setUserAgentValue(BuildConfig.LIBRARY_PACKAGE_NAME);

//        FirebaseDatabase database = FirebaseDatabase.getInstance();
//        DatabaseReference myRef = database.getReference("locations");
//
//        // Create a Firebase database reference
//        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("locations");


        // Initialize views
        mapView = findViewById(R.id.map);
        startEditText = findViewById(R.id.start_point);
        endEditText = findViewById(R.id.end_point);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.getController().setZoom(10);
//            mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);


        // Set the default location to London
        IMapController mapController = mapView.getController();
        mapController.setZoom(12.0);
//            mapView.getController().setZoom(12.0);
        GeoPoint startPoint = new GeoPoint(53.0996218803593, -7.911131504579704);
        mapController.setCenter(startPoint);

        // Initialize the location provider and overlay
        provider = new GpsMyLocationProvider(this);
        provider.addLocationSource(LocationManager.NETWORK_PROVIDER);
        locationOverlay = new MyLocationNewOverlay(provider, mapView);
        locationOverlay.enableFollowLocation();
        mapView.getOverlays().add(locationOverlay);


        //Creating a new marker that is to be used by the user
        marker = new Marker(mapView);
        marker.setIcon(getResources().getDrawable(R.drawable.ic_marker));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        // Find the expand button in your layout
        ImageButton expandButton = findViewById(R.id.image_button);
        searchPanel = findViewById(R.id.search_panel);

        // Retrieve data from Firebase Realtime Database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference ref = database.getReference("locations");
        DatabaseReference locationsRef = FirebaseDatabase.getInstance().getReference("locations");
        locationsRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String name = snapshot.child("Category").getValue(String.class);
                Double latitudeDouble = snapshot.child("latitude").getValue(Double.class);
                double latitude = 0.0; // default value
                if (latitudeDouble != null) {
                    latitude = latitudeDouble.doubleValue();
                }

                Double longitudeDouble = snapshot.child("longitude").getValue(Double.class);
                double longitude = 0.0; // default value
                if (latitudeDouble != null) {
                    longitude = longitudeDouble.doubleValue();
                }

                GeoPoint location = new GeoPoint(latitude, longitude);
                Marker marker = new Marker(mapView);
                marker.setPosition(location);
                mapView.getOverlays().add(marker);
                mapView.invalidate();
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // Handle child updates here
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                // Handle child deletions here
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                // Handle child movements here
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle errors here
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

        final Handler handler = new Handler();
        final long LONG_PRESS_TIME = 2000; // in milliseconds
        final long MARKER_LIFETIME = 30 * 60 * 1000; // in milliseconds (30 minutes)
        final Marker[] marker = {null};
        final MotionEvent[] downEvent = {null};

        final Runnable mLongPressed = new Runnable() {
            public void run() {
                // Get the location of the touch
                GeoPoint touchedPoint = (GeoPoint) mapView.getProjection().fromPixels((int) downEvent[0].getX(), (int) downEvent[0].getY());

                // Create the marker
                marker[0] = new Marker(mapView);
                marker[0].setPosition(touchedPoint);

                // Show the confirmation dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setMessage("Add a marker here?");
                builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Launch your form activity and pass the latitude and longitude of the clicked position
                        GeoPoint markerPosition = marker[0].getPosition();
                        if (markerPosition != null) {
                            Intent intent = new Intent(MainActivity.this, MarkerOnMapActivity.class);
                            intent.putExtra("latitude", markerPosition.getLatitude());
                            intent.putExtra("longitude", markerPosition.getLongitude());
                            startActivity(intent);
                        }
                    }

                });
                builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mapView.getOverlays().remove(marker[0]);
                    }
                });
                AlertDialog alertDialog = builder.create();
                alertDialog.show();

                // Add the marker to the map
                mapView.getOverlays().add(marker[0]);
                mapView.invalidate();

                // Schedule the marker to be removed after the specified duration
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (marker[0] != null) {
                            mapView.getOverlays().remove(marker[0]);
                            mapView.invalidate();
                        }
                    }
                }, MARKER_LIFETIME);
            }
        };

        mapView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        handler.removeCallbacks(mLongPressed);
                        downEvent[0] = MotionEvent.obtain(event);
                        handler.postDelayed(mLongPressed, LONG_PRESS_TIME);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getX() - downEvent[0].getX()) > 10 || Math.abs(event.getY() - downEvent[0].getY()) > 10) {
                            handler.removeCallbacks(mLongPressed);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(mLongPressed);
                        break;
                }
                return false;
            }
        });


    }


    @Override
    public void onLocationChanged(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        // Check user's distance from speed van locations at specified distances
        DatabaseReference locationsRef = FirebaseDatabase.getInstance().getReference("locations");
        locationsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Location vanLocation = new Location("");
                    vanLocation.setLatitude((Double) snapshot.child("latitude").getValue());
                    vanLocation.setLongitude((Double) snapshot.child("longitude").getValue());

                    float distance = location.distanceTo(vanLocation);

                    if (distance <= 450 && distance > 400) {
                        showDialog("You are 450 meters from a speed van location");
                    } else if (distance <= 350 && distance > 300) {
                        showDialog("You are 350 meters from a speed van location");
                    } else if (distance <= 250 && distance > 200) {
                        showDialog("You are 250 meters from a speed van location");
                    } else if (distance <= 150 && distance > 100) {
                        showDialog("You are 150 meters from a speed van location");
                    } else if (distance <= 25) {
                        showDialog("You are 25 meters from a speed van location");
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error
            }
        });

        // Update countdown timer
        countdownTimer.updateCountdownText(countdownTextBox);
    }

    private void showDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }


    private void updateCountdownText(String countdownText) { // corrected method signature
        // Set countdown text in the textbox
        countdownTextBox.setText(countdownText);
    }

    private Handler handler = new Handler();
    private Runnable updateTextRunnable = new Runnable() {
        @Override
        public void run() {
            // Get user's current location from lastLocation
            GeoPoint userLocation = new GeoPoint(lastLocation.getLatitude(), lastLocation.getLongitude());

            // Calculate distance to next point
            double distance = userLocation.distanceToAsDouble(nextPoint);

            // Convert distance to text
            String distanceText = getDistanceText(distance);

            // Calculate direction to next point
            double bearing = userLocation.bearingTo(nextPoint);

            // Convert bearing to text
            String directionText = getDirectionText(bearing);

            // Update text view with distance and direction
            String message = "Turn " + directionText + " in " + distanceText;
            countdownTextBox.setText(message);



            // Schedule next update in 1 second
            if (distance > 50) {
                handler.postDelayed(this, 1000);
            } else {
                countdownTextBox.setText("Turn " + directionText + " now!");
            }
        }
    };

    private String getCountdownText(double distance) {
        if (distance >= 500) {
            return "Turn left in 500 meters";
        } else if (distance >= 400) {
            return "Turn left in 400 meters";
        } else if (distance >= 300) {
            return "Turn left in 300 meters";
        } else if (distance >= 200) {
            return "Turn left in 200 meters";
        } else if (distance >= 100) {
            return "Turn left in 100 meters";
        } else if (distance >= 50) {
            return "Turn left in 50 meters";
        } else {
            return "Turn left now";
        }
    }

    private String getDistanceText(double distance) {
        if (distance >= 1000) {
            double km = distance / 1000;
            return String.format("%.2f km", km);
        } else {
            return String.format("%.0f m", distance);
        }
    }

    public static String getDirectionText(double bearing) {
        if (bearing < 0 || bearing >= 360) {
            return "Invalid bearing";
        }

        if (bearing < 22.5 || bearing >= 337.5) {
            return "North";
        } else if (bearing < 67.5) {
            return "Northeast";
        } else if (bearing < 112.5) {
            return "East";
        } else if (bearing < 157.5) { // Added missing decimal point
            return "Southeast";
        } else if (bearing < 202.5) {
            return "South";
        } else if (bearing < 247.5) {
            return "Southwest";
        } else if (bearing < 292.5) {
            return "West";
        } else {
            return "Northwest";
        }
    }



    private void notifyUser(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }


    public double getDistance(GeoPoint start, GeoPoint end) {
        double lat1 = start.getLatitude();
        double lon1 = start.getLongitude();
        double lat2 = end.getLatitude();
        double lon2 = end.getLongitude();
        int R = 6371; // Radius of the earth in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c * 1000; // convert to meters
        return distance;
    }









    @Override
    protected void onResume() {
        super.onResume();
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        locationOverlay.enableMyLocation();  // enable location updates
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
        locationOverlay.disableMyLocation();  // disable location updates

        super.onPause();
    }

}