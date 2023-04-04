package com.example.destination;



import static android.content.ContentValues.TAG;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
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

import com.example.destination.routing.OSRMRoadManager;
import com.example.destination.routing.Road;
import com.example.destination.routing.RoadManager;
import com.example.destination.routing.RoadNode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

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

                    // Set the icon according to the maneuver
                    switch (node.mManeuverType) {
                        case RoadNode.MANEUVER_TURN_LEFT:
                            nodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_turn_left));
                            break;
//                        case RoadNode.MANEUVER_TURN_RIGHT:
//                            nodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_turn_right));
//                            break;
                        case RoadNode.MANEUVER_STRAIGHT:
                            nodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_continue));
                            break;
                        case RoadNode.MANEUVER_RIGHT:
                            nodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_turn_left));
                            break;
                        case RoadNode.MANEUVER_LEFT:
                            nodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_turn_right));
                            break;
//                        case RoadNode.MANEUVER_ROUNDABOUT:
//                            nodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_roundabout));
//                            break;
                        default:
                            nodeMarker.setIcon(getResources().getDrawable(R.drawable.ic_continue));
                            break;
                    }


                    nodeMarker.setTitle("Step " + i);
                    nodeMarker.setSnippet(node.mInstructions);
                    nodeMarker.setSubDescription(Road.getLengthDurationText(MainActivity.this, node.mLength, node.mDuration));
                    mapView.getOverlays().add(nodeMarker);
                }

            }
        }
    }





    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myRef = database.getReference("locations");

        // Create a Firebase database reference
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("locations");

        // Create a HashMap to store the location data
        HashMap<String, Object> locationData = new HashMap<>();


// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5722);
        locationData.put("longitude", -7.8379);
        databaseReference.child("location701").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.6936);
        locationData.put("longitude", -9.1490);
        databaseReference.child("location801").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.5853);
        locationData.put("longitude", -9.0453);
        databaseReference.child("location802").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.7007);
        locationData.put("longitude", -8.8500);
        databaseReference.child("location803").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.7334);
        locationData.put("longitude", -8.7576);
        databaseReference.child("location804").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.7980);
        locationData.put("longitude", -8.7229);
        databaseReference.child("location805").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.7764);
        locationData.put("longitude", -8.5685);
        databaseReference.child("location806").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.7064);
        locationData.put("longitude", -8.5233);
        databaseReference.child("location807").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.7252);
        locationData.put("longitude", -8.4912);
        databaseReference.child("location808").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8976);
        locationData.put("longitude", -8.9396);
        databaseReference.child("location809").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0854);
        locationData.put("longitude", -9.2353);
        databaseReference.child("location810").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1306);
        locationData.put("longitude", -8.1906);
        databaseReference.child("location811").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1529);
        locationData.put("longitude", -8.2788);
        databaseReference.child("location812").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9038);
        locationData.put("longitude", -8.6772);
        databaseReference.child("location813").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0197);
        locationData.put("longitude", -8.6511);
        databaseReference.child("location814").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0217);
        locationData.put("longitude", -8.5930);
        databaseReference.child("location815").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9148);
        locationData.put("longitude", -8.3810);
        databaseReference.child("location816").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0055);
        locationData.put("longitude", -8.3432);
        databaseReference.child("location817").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9050);
        locationData.put("longitude", -8.4113);
        databaseReference.child("location818").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3246);
        locationData.put("longitude", -6.6451);
        databaseReference.child("location819").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3809);
        locationData.put("longitude", -9.2151);
        databaseReference.child("location820").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3875);
        locationData.put("longitude", -8.9763);
        databaseReference.child("location821").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5272);
        locationData.put("longitude", -8.9456);
        databaseReference.child("location822").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5240);
        locationData.put("longitude", -8.4760);
        databaseReference.child("location823").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3127);
        locationData.put("longitude", -8.4108);
        databaseReference.child("location824").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6104);
        locationData.put("longitude", -8.4523);
        databaseReference.child("location825").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6124);
        locationData.put("longitude", -8.3104);
        databaseReference.child("location826").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6648);
        locationData.put("longitude", -8.6690);
        databaseReference.child("location827").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6489);
        locationData.put("longitude", -8.6374);
        databaseReference.child("location828").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6495);
        locationData.put("longitude", -8.6151);
        databaseReference.child("location829").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6232);
        locationData.put("longitude", -8.6709);
        databaseReference.child("location830").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9529);
        locationData.put("longitude", -8.3920);
        databaseReference.child("location831").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8602);
        locationData.put("longitude", -8.7541);
        databaseReference.child("location832").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8664);
        locationData.put("longitude", -8.8768);
        databaseReference.child("location833").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8322);
        locationData.put("longitude", -8.7582);
        databaseReference.child("location834").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7198);
        locationData.put("longitude", -8.8150);
        databaseReference.child("location835").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7007);
        locationData.put("longitude", -8.8319);
        databaseReference.child("location836").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7798);
        locationData.put("longitude", -8.9151);
        databaseReference.child("location837").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8815);
        locationData.put("longitude", -8.9449);
        databaseReference.child("location838").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9120);
        locationData.put("longitude", -8.9216);
        databaseReference.child("location839").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8505);
        locationData.put("longitude", -8.9813);
        databaseReference.child("location840").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8579);
        locationData.put("longitude", -9.4029);
        databaseReference.child("location841").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2380);
        locationData.put("longitude", -9.4817);
        databaseReference.child("location842").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2678);
        locationData.put("longitude", -9.5553);
        databaseReference.child("location843").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2879);
        locationData.put("longitude", -9.6562);
        databaseReference.child("location844").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3695);
        locationData.put("longitude", -9.6857);
        databaseReference.child("location845").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5987);
        locationData.put("longitude", -9.7508);
        databaseReference.child("location846").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4715);
        locationData.put("longitude", -9.0972);
        databaseReference.child("location847").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4914);
        locationData.put("longitude", -8.9156);
        databaseReference.child("location848").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5144);
        locationData.put("longitude", -8.8511);
        databaseReference.child("location849").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5347);
        locationData.put("longitude", -8.8687);
        databaseReference.child("location850").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6231);
        locationData.put("longitude", -8.7427);
        databaseReference.child("location851").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6790);
        locationData.put("longitude", -8.6544);
        databaseReference.child("location852").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5108);
        locationData.put("longitude", -8.4178);
        databaseReference.child("location853").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1646);
        locationData.put("longitude", -8.2859);
        databaseReference.child("location854").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1307);
        locationData.put("longitude", -8.4548);
        databaseReference.child("location855").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1869);
        locationData.put("longitude", -8.6136);
        databaseReference.child("location856").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1187);
        locationData.put("longitude", -8.7963);
        databaseReference.child("location857").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1409);
        locationData.put("longitude", -9.0244);
        databaseReference.child("location858").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2830);
        locationData.put("longitude", -9.0473);
        databaseReference.child("location859").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2722);
        locationData.put("longitude", -9.0861);
        databaseReference.child("location860").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2714);
        locationData.put("longitude", -8.9180);
        databaseReference.child("location861").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2987);
        locationData.put("longitude", -8.8332);
        databaseReference.child("location862").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4960);
        locationData.put("longitude", -9.1402);
        databaseReference.child("location863").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5718);
        locationData.put("longitude", -9.2303);
        databaseReference.child("location864").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7719);
        locationData.put("longitude", -9.7577);
        databaseReference.child("location865").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8843);
        locationData.put("longitude", -9.5496);
        databaseReference.child("location866").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9630);
        locationData.put("longitude", -9.9762);
        databaseReference.child("location867").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.2107);
        locationData.put("longitude", -10.0342);
        databaseReference.child("location868").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.2745);
        locationData.put("longitude", -9.3489);
        databaseReference.child("location869").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1516);
        locationData.put("longitude", -9.1761);
        databaseReference.child("location870").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1152);
        locationData.put("longitude", -9.2350);
        databaseReference.child("location871").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1134);
        locationData.put("longitude", -9.1551);
        databaseReference.child("location872").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1357);
        locationData.put("longitude", -9.1362);
        databaseReference.child("location873").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1093);
        locationData.put("longitude", -9.1603);
        databaseReference.child("location874").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0346);
        locationData.put("longitude", -9.1407);
        databaseReference.child("location875").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9082);
        locationData.put("longitude", -9.0335);
        databaseReference.child("location876").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7708);
        locationData.put("longitude", -8.7617);
        databaseReference.child("location877").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7266);
        locationData.put("longitude", -8.9819);
        databaseReference.child("location878").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7279);
        locationData.put("longitude", -9.0051);
        databaseReference.child("location879").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8585);
        locationData.put("longitude", -9.2985);
        databaseReference.child("location880").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8284);
        locationData.put("longitude", -9.3047);
        databaseReference.child("location881").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9009);
        locationData.put("longitude", -9.1418);
        databaseReference.child("location882").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9816);
        locationData.put("longitude", -9.1257);
        databaseReference.child("location883").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4341);
        locationData.put("longitude", -7.9677);
        databaseReference.child("location884").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6261);
        locationData.put("longitude", -8.2376);
        databaseReference.child("location885").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6793);
        locationData.put("longitude", -8.7476);
        databaseReference.child("location886").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7657);
        locationData.put("longitude", -8.5269);
        databaseReference.child("location887").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8824);
        locationData.put("longitude", -8.4527);
        databaseReference.child("location888").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8098);
        locationData.put("longitude", -8.3122);
        databaseReference.child("location889").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7669);
        locationData.put("longitude", -8.2043);
        databaseReference.child("location890").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8422);
        locationData.put("longitude", -8.1922);
        databaseReference.child("location891").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8771);
        locationData.put("longitude", -8.3984);
        databaseReference.child("location892").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9725);
        locationData.put("longitude", -8.2964);
        databaseReference.child("location893").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9688);
        locationData.put("longitude", -8.2253);
        databaseReference.child("location894").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7509);
        locationData.put("longitude", -7.9551);
        databaseReference.child("location895").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8130);
        locationData.put("longitude", -7.8937);
        databaseReference.child("location896").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6872);
        locationData.put("longitude", -7.5931);
        databaseReference.child("location897").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6508);
        locationData.put("longitude", -7.6489);
        databaseReference.child("location898").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7122);
        locationData.put("longitude", -7.7102);
        databaseReference.child("location899").setValue(locationData);

// Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.2857);
        locationData.put("longitude", -8.9549);
        databaseReference.child("location900").setValue(locationData);













        // Initialize views
        mapView = findViewById(R.id.map);
        startEditText = findViewById(R.id.start_point);
        endEditText = findViewById(R.id.end_point);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.getController().setZoom(10);
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

        // Set the default location to London
        IMapController mapController = mapView.getController();
        mapController.setZoom(12.0);
        GeoPoint startPoint = new GeoPoint(53.0996218803593, -7.911131504579704);
        mapController.setCenter(startPoint);

        //Creating a new marker that is to be used by the user
        marker = new Marker(mapView);
        marker.setIcon(getResources().getDrawable(R.drawable.ic_marker));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        // Find the expand button in your layout
        ImageButton expandButton = findViewById(R.id.image_button);
        searchPanel = findViewById(R.id.search_panel);

//        // Retrieve data from Firebase Realtime Database
//        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("locations");
//        ref.addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(DataSnapshot dataSnapshot) {
//                List<VanCameraLocations> locationList = new ArrayList<>();
//                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
//                    String category = snapshot.child("category").getValue(String.class);
//                    double latitude = snapshot.child("latitude").getValue(Double.class);
//                    double longitude = snapshot.child("longitude").getValue(Double.class);
//                    VanCameraLocations location = new VanCameraLocations(category, latitude, longitude);
//                    locationList.add(location);
//                }
//                // Display markers on mapview
//                for (VanCameraLocations location : locationList) {
//                    Marker marker = new Marker(mapView);
//                    marker.setPosition(new GeoPoint(location.getLatitude(), location.getLongitude()));
//                    marker.setTitle(location.getCategory());
//                    mapView.getOverlays().add(marker);
//                }
//            }
//            @Override
//            public void onCancelled(DatabaseError databaseError) {
//                Log.e(TAG, "onCancelled", databaseError.toException());
//            }
//        });






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

        mapView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    // Get the location of the touch
                    GeoPoint touchedPoint = (GeoPoint) mapView.getProjection().fromPixels((int)event.getX(), (int)event.getY());

                    // Create the marker
                    Marker marker = new Marker(mapView);
                    marker.setPosition(touchedPoint);

                    // Show the confirmation dialog
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setMessage("Add a marker here?");
                    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // Launch your form activity and pass the latitude and longitude of the clicked position
                            Intent intent = new Intent(MainActivity.this, MarkerOnMapActivity.class);
                            intent.putExtra("latitude", marker.getPosition().getLatitude());
                            intent.putExtra("longitude", marker.getPosition().getLongitude());
                            startActivity(intent);

                        }

                    });
                    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            mapView.getOverlays().remove(marker);
                        }
                    });
                    AlertDialog alertDialog = builder.create();
                    alertDialog.show();

                    // Add the marker to the map
                    mapView.getOverlays().add(marker);
                    mapView.invalidate();
                }
                return true;
            }
        });


        Button searchButton = findViewById(R.id.search_button);
        searchButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                // Hide the keyboard
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

                // Start the search task
                String startLocation = startEditText.getText().toString();
                String endLocation = endEditText.getText().toString();
                new SearchTask().execute(startLocation, endLocation);

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