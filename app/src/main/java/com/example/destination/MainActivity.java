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

//        // Add the first location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.519345);
//        locationData.put("longitude", -6.610428);
//        databaseReference.child("location1").setValue(locationData);
//
//        // Add the second location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.81358);
//        locationData.put("longitude", -6.146841);
//        databaseReference.child("location2").setValue(locationData);
//
//        // Add the third location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.161323);
//        locationData.put("longitude", -6.725301);
//        databaseReference.child("location3").setValue(locationData);
//
//        // Add the fourth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.168662);
//        locationData.put("longitude", -7.227399);
//        databaseReference.child("location4").setValue(locationData);
//
//        // Add the fifth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.95379);
//        locationData.put("longitude", -6.171606);
//        databaseReference.child("location5").setValue(locationData);
//
//        // Add the sixth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 51.895514);
//        locationData.put("longitude", -8.505027);
//        databaseReference.child("location6").setValue(locationData);
//
//        // Add the seventh location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.604296);
//        locationData.put("longitude", -6.456414);
//        databaseReference.child("location7").setValue(locationData);
//
//        // Add the eighth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.571761);
//        locationData.put("longitude", -9.285755);
//        databaseReference.child("location8").setValue(locationData);
//
//        // Add the ninth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.287517);
//        locationData.put("longitude", -6.772033);
//        databaseReference.child("location9").setValue(locationData);
//
//        // Add the tenth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.426512);
//        locationData.put("longitude", -6.950856);
//        databaseReference.child("location10").setValue(locationData);
//
//
//        // Add the eleventh location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.576074);
//        locationData.put("longitude", -6.210629);
//        databaseReference.child("location11").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.632897);
//        locationData.put("longitude", -6.372609);
//        databaseReference.child("location12").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.358812);
//        locationData.put("longitude", -7.502059);
//        databaseReference.child("location13").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.39934);
//        locationData.put("longitude", -7.829015);
//        databaseReference.child("location14").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.835898);
//        locationData.put("longitude", -7.501285);
//        databaseReference.child("location15").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.343441);
//        locationData.put("longitude", -6.515194);
//        databaseReference.child("location16").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.610494);
//        locationData.put("longitude", -6.439756);
//        databaseReference.child("location17").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 54.359784);
//        locationData.put("longitude", -8.522433);
//        databaseReference.child("location18").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 54.490398);
//        locationData.put("longitude", -8.203269);
//        databaseReference.child("location19").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 54.678167);
//        locationData.put("longitude", -8.042266);
//        databaseReference.child("location20").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.471404);
//        locationData.put("longitude", -9.020695);
//        databaseReference.child("location21").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.551305);
//        locationData.put("longitude", -8.812588);
//        databaseReference.child("location22").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.365333);
//        locationData.put("longitude", -7.624309);
//        databaseReference.child("location23").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.361152);
//        locationData.put("longitude", -7.499734);
//        databaseReference.child("location24").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 51.946199);
//        locationData.put("longitude", -7.886671);
//        databaseReference.child("location25").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.346);
//        locationData.put("longitude", -7.044845);
//        databaseReference.child("location26").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.986428);
//        locationData.put("longitude", -7.31751);
//        databaseReference.child("location27").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 54.039974);
//        locationData.put("longitude", -7.378137);
//        databaseReference.child("location28").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.943622);
//        locationData.put("longitude", -8.070974);
//        databaseReference.child("location29").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 54.653223);
//        locationData.put("longitude", -8.243801);
//        databaseReference.child("location30").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.714694);
//        locationData.put("longitude", -9.548478);
//        databaseReference.child("location31").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.900554);
//        locationData.put("longitude", -7.797818);
//        databaseReference.child("location32").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.348583);
//        locationData.put("longitude", -7.845681);
//        databaseReference.child("location33").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.435649);
//        locationData.put("longitude", -6.292715);
//        databaseReference.child("location34").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.354198);
//        locationData.put("longitude", -6.266939);
//        databaseReference.child("location35").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.721128);
//        locationData.put("longitude", -6.968328);
//        databaseReference.child("location36").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.972008);
//        locationData.put("longitude", -6.726329);
//        databaseReference.child("location37").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.501052);
//        locationData.put("longitude", -8.118253);
//        databaseReference.child("location38").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.569847);
//        locationData.put("longitude", -8.568213);
//        databaseReference.child("location39").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.294037);
//        locationData.put("longitude", -7.147243);
//        databaseReference.child("location40").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.364055);
//        locationData.put("longitude", -6.563115);
//        databaseReference.child("location41").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.221068);
//        locationData.put("longitude", -6.696108);
//        databaseReference.child("location42").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.86854);
//        locationData.put("longitude", -6.808969);
//        databaseReference.child("location43").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.849183);
//        locationData.put("longitude", -8.039664);
//        databaseReference.child("location44").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.32887);
//        locationData.put("longitude", -8.203709);
//        databaseReference.child("location45").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.117018);
//        locationData.put("longitude", -6.764241);
//        databaseReference.child("location46").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 51.876117);
//        locationData.put("longitude", -8.431664);
//        databaseReference.child("location47").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 51.852672);
//        locationData.put("longitude", -8.164088);
//        databaseReference.child("location48").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.310881);
//        locationData.put("longitude", -7.310008);
//        databaseReference.child("location49").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.529854);
//        locationData.put("longitude", -7.14209);
//        databaseReference.child("location50").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.628218);
//        locationData.put("longitude", -7.029336);
//        databaseReference.child("location51").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.821628);
//        locationData.put("longitude", -6.798759);
//        databaseReference.child("location52").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.365143);
//        locationData.put("longitude", -6.564756);
//        databaseReference.child("location53").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.313642);
//        locationData.put("longitude", -6.771274);
//        databaseReference.child("location54").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.388466);
//        locationData.put("longitude", -6.124319);
//        databaseReference.child("location55").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.233781);
//        locationData.put("longitude", -6.887879);
//        databaseReference.child("location56").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.328639);
//        locationData.put("longitude", -6.246986);
//        databaseReference.child("location57").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.061525);
//        locationData.put("longitude", -9.61638);
//        databaseReference.child("location58").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.629496);
//        locationData.put("longitude", -6.281102);
//        databaseReference.child("location59").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.510945);
//        locationData.put("longitude", -6.42014);
//        databaseReference.child("location60").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 52.319556);
//        locationData.put("longitude", -9.736346);
//        databaseReference.child("location61").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2651);
        locationData.put("longitude", -9.7112);
        databaseReference.child("location62").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2800);
        locationData.put("longitude", -9.7675);
        databaseReference.child("location63").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1407);
        locationData.put("longitude", -10.1748);
        databaseReference.child("location64").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0724);
        locationData.put("longitude", -9.5753);
        databaseReference.child("location65").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0724);
        locationData.put("longitude", -9.5753);
        databaseReference.child("location66").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1021);
        locationData.put("longitude", -9.6276);
        databaseReference.child("location67").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0812);
        locationData.put("longitude", -9.2470);
        databaseReference.child("location68").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1134);
        locationData.put("longitude", -9.5169);
        databaseReference.child("location69").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4096);
        locationData.put("longitude", -9.5167);
        databaseReference.child("location70").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4428);
        locationData.put("longitude", -9.4105);
        databaseReference.child("location71").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4582);
        locationData.put("longitude", -9.6616);
        databaseReference.child("location72").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1420);
        locationData.put("longitude", -9.7316);
        databaseReference.child("location73").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3917);
        locationData.put("longitude", -9.1873);
        databaseReference.child("location74").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3773);
        locationData.put("longitude", -9.3068);
        databaseReference.child("location75").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4027);
        locationData.put("longitude", -9.1699);
        databaseReference.child("location76").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5941);
        locationData.put("longitude", -9.0701);
        databaseReference.child("location77").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6067);
        locationData.put("longitude", -8.9183);
        databaseReference.child("location78").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6231);
        locationData.put("longitude", -8.8214);
        databaseReference.child("location79").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6414);
        locationData.put("longitude", -8.6810);
        databaseReference.child("location80").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4947);
        locationData.put("longitude", -9.0640);
        databaseReference.child("location81").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4739);
        locationData.put("longitude", -9.0183);
        databaseReference.child("location82").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5296);
        locationData.put("longitude", -8.9337);
        databaseReference.child("location83").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5387);
        locationData.put("longitude", -8.5560);
        databaseReference.child("location84").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6126);
        locationData.put("longitude", -8.6262);
        databaseReference.child("location85").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4118);
        locationData.put("longitude", -8.6853);
        databaseReference.child("location86").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3948);
        locationData.put("longitude", -8.5638);
        databaseReference.child("location87").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4206);
        locationData.put("longitude", -8.4697);
        databaseReference.child("location88").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4134);
        locationData.put("longitude", -8.3794);
        databaseReference.child("location89").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4489);
        locationData.put("longitude", -8.4197);
        databaseReference.child("location90").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5709);
        locationData.put("longitude", -8.3431);
        databaseReference.child("location91").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5787);
        locationData.put("longitude", -8.5455);
        databaseReference.child("location92").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6503);
        locationData.put("longitude", -8.5875);
        databaseReference.child("location93").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6671);
        locationData.put("longitude", -8.6375);
        databaseReference.child("location94").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6658);
        locationData.put("longitude", -8.6711);
        databaseReference.child("location95").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7013);
        locationData.put("longitude", -8.4585);
        databaseReference.child("location96").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7326);
        locationData.put("longitude", -8.4683);
        databaseReference.child("location97").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7700);
        locationData.put("longitude", -8.4301);
        databaseReference.child("location98").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7956);
        locationData.put("longitude", -8.3539);
        databaseReference.child("location99").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8328);
        locationData.put("longitude", -8.2807);
        databaseReference.child("location100").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8612);
        locationData.put("longitude", -8.2157);
        databaseReference.child("location101").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8563);
        locationData.put("longitude", -8.0737);
        databaseReference.child("location102").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8691);
        locationData.put("longitude", -7.9972);
        databaseReference.child("location103").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9574);
        locationData.put("longitude", -7.7762);
        databaseReference.child("location104").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6535);
        locationData.put("longitude", -7.7985);
        databaseReference.child("location105").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4890);
        locationData.put("longitude", -8.1937);
        databaseReference.child("location106").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4520);
        locationData.put("longitude", -8.0943);
        databaseReference.child("location107").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4412);
        locationData.put("longitude", -8.0546);
        databaseReference.child("location108").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4078);
        locationData.put("longitude", -7.8958);
        databaseReference.child("location109").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3577);
        locationData.put("longitude", -7.9895);
        databaseReference.child("location110").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3615);
        locationData.put("longitude", -7.7550);
        databaseReference.child("location111").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3740);
        locationData.put("longitude", -7.6989);
        databaseReference.child("location112").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3588);
        locationData.put("longitude", -7.4898);
        databaseReference.child("location113").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3075);
        locationData.put("longitude", -7.8963);
        databaseReference.child("location114").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3324);
        locationData.put("longitude", -7.8163);
        databaseReference.child("location115").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9614);
        locationData.put("longitude", -8.8696);
        databaseReference.child("location116").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6545);
        locationData.put("longitude", -9.5229);
        databaseReference.child("location117").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6728);
        locationData.put("longitude", -9.3919);
        databaseReference.child("location118").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7060);
        locationData.put("longitude", -9.2847);
        databaseReference.child("location119").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7383);
        locationData.put("longitude", -9.1747);
        databaseReference.child("location120").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8211);
        locationData.put("longitude", -9.0139);
        databaseReference.child("location121").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8391);
        locationData.put("longitude", -8.9707);
        databaseReference.child("location122").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7892);
        locationData.put("longitude", -8.7743);
        databaseReference.child("location123").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6840);
        locationData.put("longitude", -8.7210);
        databaseReference.child("location124").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6732);
        locationData.put("longitude", -8.6161);
        databaseReference.child("location125").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7317);
        locationData.put("longitude", -8.5502);
        databaseReference.child("location126").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7975);
        locationData.put("longitude", -8.4499);
        databaseReference.child("location127").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0901);
        locationData.put("longitude", -8.1906);
        databaseReference.child("location128").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1109);
        locationData.put("longitude", -8.8005);
        databaseReference.child("location129").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0644);
        locationData.put("longitude", -8.8136);
        databaseReference.child("location130").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1776);
        locationData.put("longitude", -8.8393);
        databaseReference.child("location131").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2135);
        locationData.put("longitude", -8.6866);
        databaseReference.child("location132").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4006);
        locationData.put("longitude", -8.3347);
        databaseReference.child("location133").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4006);
        locationData.put("longitude", -8.3347);
        databaseReference.child("location133").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4830);
        locationData.put("longitude", -8.5420);
        databaseReference.child("location134").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3541);
        locationData.put("longitude", -8.9338);
        databaseReference.child("location135").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5435);
        locationData.put("longitude", -8.8444);
        databaseReference.child("location136").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3054);
        locationData.put("longitude", -8.9248);
        databaseReference.child("location137").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3001);
        locationData.put("longitude", -8.9618);
        databaseReference.child("location138").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2719);
        locationData.put("longitude", -8.9171);
        databaseReference.child("location139").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2888);
        locationData.put("longitude", -9.0229);
        databaseReference.child("location140").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2452);
        locationData.put("longitude", -9.3576);
        databaseReference.child("location141").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2401);
        locationData.put("longitude", -9.4669);
        databaseReference.child("location142").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3797);
        locationData.put("longitude", -9.2504);
        databaseReference.child("location143").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0578);
        locationData.put("longitude", -7.7585);
        databaseReference.child("location144").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9588);
        locationData.put("longitude", -8.0800);
        databaseReference.child("location145").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9716);
        locationData.put("longitude", -8.0643);
        databaseReference.child("location146").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.2042);
        locationData.put("longitude", -7.1598);
        databaseReference.child("location147").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9607);
        locationData.put("longitude", -6.7515);
        databaseReference.child("location148").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9833);
        locationData.put("longitude", -6.7170);
        databaseReference.child("location149").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0585);
        locationData.put("longitude", -6.6968);
        databaseReference.child("location150").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0585);
        locationData.put("longitude", -6.6968);
        databaseReference.child("location151").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.2662);
        locationData.put("longitude", -6.8926);
        databaseReference.child("location152").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.3267);
        locationData.put("longitude", -6.9616);
        databaseReference.child("location153").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.4002);
        locationData.put("longitude", -6.9756);
        databaseReference.child("location154").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9682);
        locationData.put("longitude", -7.8199);
        databaseReference.child("location155").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1587);
        locationData.put("longitude", -7.9451);
        databaseReference.child("location156").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3020);
        locationData.put("longitude", -7.8189);
        databaseReference.child("location157").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3124);
        locationData.put("longitude", -7.7117);
        databaseReference.child("location158").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2804);
        locationData.put("longitude", -7.5008);
        databaseReference.child("location159").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2717);
        locationData.put("longitude", -7.4952);
        databaseReference.child("location160").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2500);
        locationData.put("longitude", -7.3992);
        databaseReference.child("location161").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2664);
        locationData.put("longitude", -7.3349);
        databaseReference.child("location162").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1698);
        locationData.put("longitude", -7.2303);
        databaseReference.child("location163").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3339);
        locationData.put("longitude", -7.0665);
        databaseReference.child("location164").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3938);
        locationData.put("longitude", -7.5848);
        databaseReference.child("location165").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.2405);
        locationData.put("longitude", -7.2992);
        databaseReference.child("location166").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.2232);
        locationData.put("longitude", -7.2670);
        databaseReference.child("location167").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.1993);
        locationData.put("longitude", -7.3966);
        databaseReference.child("location168").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.1962);
        locationData.put("longitude", -7.0533);
        databaseReference.child("location169").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.2034);
        locationData.put("longitude", -6.9819);
        databaseReference.child("location170").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.1513);
        locationData.put("longitude", -7.1529);
        databaseReference.child("location171").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.1370);
        locationData.put("longitude", -7.2242);
        databaseReference.child("location172").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.1125);
        locationData.put("longitude", -7.2097);
        databaseReference.child("location173").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.0890);
        locationData.put("longitude", -7.2416);
        databaseReference.child("location174").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.0653);
        locationData.put("longitude", -7.3043);
        databaseReference.child("location175").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.0935);
        locationData.put("longitude", -7.4777);
        databaseReference.child("location176").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.1491);
        locationData.put("longitude", -7.4450);
        databaseReference.child("location177").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.1488);
        locationData.put("longitude", -7.4456);
        databaseReference.child("location178").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.1168);
        locationData.put("longitude", -7.4592);
        databaseReference.child("location179").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.1530);
        locationData.put("longitude", -7.9271);
        databaseReference.child("location180").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.1174);
        locationData.put("longitude", -8.1383);
        databaseReference.child("location181").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.0798);
        locationData.put("longitude", -8.2901);
        databaseReference.child("location182").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.9850);
        locationData.put("longitude", -8.3195);
        databaseReference.child("location183").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.9935);
        locationData.put("longitude", -8.3082);
        databaseReference.child("location184").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.0470);
        locationData.put("longitude", -8.2410);
        databaseReference.child("location185").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.9783);
        locationData.put("longitude", -7.7265);
        databaseReference.child("location186").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.9411);
        locationData.put("longitude", -7.6955);
        databaseReference.child("location187").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.9480);
        locationData.put("longitude", -7.7139);
        databaseReference.child("location188").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.9539);
        locationData.put("longitude", -7.7224);
        databaseReference.child("location189").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.9820);
        locationData.put("longitude", -7.5607);
        databaseReference.child("location190").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.9402);
        locationData.put("longitude", -7.6191);
        databaseReference.child("location191").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.8877);
        locationData.put("longitude", -7.5567);
        databaseReference.child("location192").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.8695);
        locationData.put("longitude", -7.6103);
        databaseReference.child("location193").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.8487);
        locationData.put("longitude", -7.5001);
        databaseReference.child("location194").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.8195);
        locationData.put("longitude", -7.5048);
        databaseReference.child("location195").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.7977);
        locationData.put("longitude", -7.5653);
        databaseReference.child("location196").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.8043);
        locationData.put("longitude", -7.7444);
        databaseReference.child("location197").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.8359);
        locationData.put("longitude", -7.7407);
        databaseReference.child("location198").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.8046);
        locationData.put("longitude", -7.7680);
        databaseReference.child("location199").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.7107);
        locationData.put("longitude", -7.9694);
        databaseReference.child("location200").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.6595);
        locationData.put("longitude", -8.0849);
        databaseReference.child("location201").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.6495);
        locationData.put("longitude", -8.1571);
        databaseReference.child("location202").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.6477);
        locationData.put("longitude", -8.1957);
        databaseReference.child("location203").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.4931);
        locationData.put("longitude", -8.1645);
        databaseReference.child("location204").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.4749);
        locationData.put("longitude", -8.3112);
        databaseReference.child("location205").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.5236);
        locationData.put("longitude", -8.1534);
        databaseReference.child("location206").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.3884);
        locationData.put("longitude", -8.5299);
        databaseReference.child("location207").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1046);
        locationData.put("longitude", -8.4103);
        databaseReference.child("location208").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1158);
        locationData.put("longitude", -8.6094);
        databaseReference.child("location209").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9590);
        locationData.put("longitude", -8.5424);
        databaseReference.child("location210").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.3154);
        locationData.put("longitude", -8.4809);
        databaseReference.child("location211").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1493);
        locationData.put("longitude", -9.0905);
        databaseReference.child("location212").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3932);
        locationData.put("longitude", -7.8239);
        databaseReference.child("location213").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4150);
        locationData.put("longitude", -7.8910);
        databaseReference.child("location214").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5210);
        locationData.put("longitude", -7.8173);
        databaseReference.child("location215").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5294);
        locationData.put("longitude", -7.5576);
        databaseReference.child("location216").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4891);
        locationData.put("longitude", -7.5076);
        databaseReference.child("location217").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5073);
        locationData.put("longitude", -7.1806);
        databaseReference.child("location218").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5163);
        locationData.put("longitude", -7.3749);
        databaseReference.child("location219").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5259);
        locationData.put("longitude", -7.3409);
        databaseReference.child("location220").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5375);
        locationData.put("longitude", -7.3061);
        databaseReference.child("location221").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6210);
        locationData.put("longitude", -7.4582);
        databaseReference.child("location222").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6486);
        locationData.put("longitude", -7.5092);
        databaseReference.child("location223").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8038);
        locationData.put("longitude", -6.7415);
        databaseReference.child("location224").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8221);
        locationData.put("longitude", -6.7264);
        databaseReference.child("location225").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8436);
        locationData.put("longitude", -6.7767);
        databaseReference.child("location226").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8334);
        locationData.put("longitude", -6.9328);
        databaseReference.child("location227").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7963);
        locationData.put("longitude", -6.9485);
        databaseReference.child("location228").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7672);
        locationData.put("longitude", -6.9372);
        databaseReference.child("location229").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7486);
        locationData.put("longitude", -6.9665);
        databaseReference.child("location230").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8347);
        locationData.put("longitude", -7.0770);
        databaseReference.child("location231").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8399);
        locationData.put("longitude", -7.1045);
        databaseReference.child("location232").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8357);
        locationData.put("longitude", -7.0847);
        databaseReference.child("location233").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8558);
        locationData.put("longitude", -7.1544);
        databaseReference.child("location234").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8414);
        locationData.put("longitude", -7.4466);
        databaseReference.child("location235").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9272);
        locationData.put("longitude", -7.0065);
        databaseReference.child("location236").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9194);
        locationData.put("longitude", -6.9793);
        databaseReference.child("location237").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0708);
        locationData.put("longitude", -7.0870);
        databaseReference.child("location238").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1085);
        locationData.put("longitude", -7.3588);
        databaseReference.child("location239").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0849);
        locationData.put("longitude", -7.4251);
        databaseReference.child("location240").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0049);
        locationData.put("longitude", -7.3791);
        databaseReference.child("location241").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9828);
        locationData.put("longitude", -7.3569);
        databaseReference.child("location242").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9571);
        locationData.put("longitude", -7.3679);
        databaseReference.child("location243").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9393);
        locationData.put("longitude", -7.2228);
        databaseReference.child("location244").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9127);
        locationData.put("longitude", -6.8058);
        databaseReference.child("location245").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.5510);
        locationData.put("longitude", -9.2992);
        databaseReference.child("location246").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.5457);
        locationData.put("longitude", -9.2651);
        databaseReference.child("location247").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.6478);
        locationData.put("longitude", -9.4099);
        databaseReference.child("location248").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.7535);
        locationData.put("longitude", -8.7160);
        databaseReference.child("location249").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.7856);
        locationData.put("longitude", -8.6305);
        databaseReference.child("location250").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8333);
        locationData.put("longitude", -8.3829);
        databaseReference.child("location251").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8330);
        locationData.put("longitude", -8.3931);
        databaseReference.child("location252").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8776);
        locationData.put("longitude", -8.3741);
        databaseReference.child("location253").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8973);
        locationData.put("longitude", -8.2812);
        databaseReference.child("location254").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8866);
        locationData.put("longitude", -8.1657);
        databaseReference.child("location255").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9082);
        locationData.put("longitude", -8.2554);
        databaseReference.child("location256").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9155);
        locationData.put("longitude", -8.0941);
        databaseReference.child("location257").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1377);
        locationData.put("longitude", -8.2787);
        databaseReference.child("location258").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1415);
        locationData.put("longitude", -8.3099);
        databaseReference.child("location259").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2820);
        locationData.put("longitude", -8.2403);
        databaseReference.child("location260").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2147);
        locationData.put("longitude", -8.5863);
        databaseReference.child("location261").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2381);
        locationData.put("longitude", -8.6698);
        databaseReference.child("location262").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1556);
        locationData.put("longitude", -8.5036);
        databaseReference.child("location263").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1484);
        locationData.put("longitude", -8.6175);
        databaseReference.child("location264").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1319);
        locationData.put("longitude", -8.6792);
        databaseReference.child("location265").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1363);
        locationData.put("longitude", -8.9407);
        databaseReference.child("location266").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3467);
        locationData.put("longitude", -8.6760);
        databaseReference.child("location267").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3613);
        locationData.put("longitude", -8.6644);
        databaseReference.child("location268").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8675);
        locationData.put("longitude", -8.7520);
        databaseReference.child("location269").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8653);
        locationData.put("longitude", -8.7703);
        databaseReference.child("location270").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8523);
        locationData.put("longitude", -8.8683);
        databaseReference.child("location271").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9022);
        locationData.put("longitude", -8.9434);
        databaseReference.child("location272").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9127);
        locationData.put("longitude", -9.0516);
        databaseReference.child("location273").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9475);
        locationData.put("longitude", -8.5674);
        databaseReference.child("location274").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9811);
        locationData.put("longitude", -8.5854);
        databaseReference.child("location275").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9551);
        locationData.put("longitude", -8.4232);
        databaseReference.child("location276").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9495);
        locationData.put("longitude", -8.3922);
        databaseReference.child("location277").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.7236);
        locationData.put("longitude", -9.0803);
        databaseReference.child("location278").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6128);
        locationData.put("longitude", -6.1886);
        databaseReference.child("location279").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5971);
        locationData.put("longitude", -6.1617);
        databaseReference.child("location280").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5789);
        locationData.put("longitude", -6.1110);
        databaseReference.child("location281").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4160);
        locationData.put("longitude", -6.1780);
        databaseReference.child("location282").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4277);
        locationData.put("longitude", -6.2296);
        databaseReference.child("location283").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4310);
        locationData.put("longitude", -6.4615);
        databaseReference.child("location284").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4012);
        locationData.put("longitude", -6.4003);
        databaseReference.child("location285").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3981);
        locationData.put("longitude", -6.3329);
        databaseReference.child("location286").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4034);
        locationData.put("longitude", -6.3108);
        databaseReference.child("location287").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3338);
        locationData.put("longitude", -6.2450);
        databaseReference.child("location288").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2946);
        locationData.put("longitude", -6.1690);
        databaseReference.child("location289").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3197);
        locationData.put("longitude", -6.2893);
        databaseReference.child("location290").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2749);
        locationData.put("longitude", -6.4016);
        databaseReference.child("location291").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2856);
        locationData.put("longitude", -6.3659);
        databaseReference.child("location292").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3256);
        locationData.put("longitude", -6.3420);
        databaseReference.child("location293").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4026);
        locationData.put("longitude", -6.3972);
        databaseReference.child("location294").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3501);
        locationData.put("longitude", -6.2340);
        databaseReference.child("location295").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4146);
        locationData.put("longitude", -6.8325);
        databaseReference.child("location296").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3670);
        locationData.put("longitude", -6.5664);
        databaseReference.child("location297").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3034);
        locationData.put("longitude", -6.5594);
        databaseReference.child("location298").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3221);
        locationData.put("longitude", -6.6153);
        databaseReference.child("location299").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2935);
        locationData.put("longitude", -6.6874);
        databaseReference.child("location300").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2892);
        locationData.put("longitude", -6.7262);
        databaseReference.child("location301").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2859);
        locationData.put("longitude", -6.8112);
        databaseReference.child("location302").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2598);
        locationData.put("longitude", -6.8486);
        databaseReference.child("location303").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2365);
        locationData.put("longitude", -6.8486);
        databaseReference.child("location304").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2525);
        locationData.put("longitude", -6.5758);
        databaseReference.child("location305").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2132);
        locationData.put("longitude", -6.6917);
        databaseReference.child("location306").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1807);
        locationData.put("longitude", -6.7992);
        databaseReference.child("location307").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1628);
        locationData.put("longitude", -6.8247);
        databaseReference.child("location308").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1564);
        locationData.put("longitude", -6.8711);
        databaseReference.child("location309").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1157);
        locationData.put("longitude", -7.0316);
        databaseReference.child("location310").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1448);
        locationData.put("longitude", -6.7764);
        databaseReference.child("location311").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0184);
        locationData.put("longitude", -6.9253);
        databaseReference.child("location312").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8706);
        locationData.put("longitude", -6.8756);
        databaseReference.child("location313").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0596);
        locationData.put("longitude", -6.7727);
        databaseReference.child("location314").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2376);
        locationData.put("longitude", -6.6230);
        databaseReference.child("location315").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6826);
        locationData.put("longitude", -7.0210);
        databaseReference.child("location316").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3937);
        locationData.put("longitude", -7.1864);
        databaseReference.child("location317").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4764);
        locationData.put("longitude", -7.2075);
        databaseReference.child("location318").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2906);
        locationData.put("longitude", -7.1472);
        databaseReference.child("location319").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2913);
        locationData.put("longitude", -7.0574);
        databaseReference.child("location320").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6116);
        locationData.put("longitude", -7.2902);
        databaseReference.child("location321").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6549);
        locationData.put("longitude", -7.2465);
        databaseReference.child("location322").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7315);
        locationData.put("longitude", -7.5700);
        databaseReference.child("location323").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8458);
        locationData.put("longitude", -7.1275);
        databaseReference.child("location324").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9447);
        locationData.put("longitude", -7.6623);
        databaseReference.child("location325").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9447);
        locationData.put("longitude", -7.6623);
        databaseReference.child("location326").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0105);
        locationData.put("longitude", -7.4246);
        databaseReference.child("location327").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0057);
        locationData.put("longitude", -7.3049);
        databaseReference.child("location328").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9918);
        locationData.put("longitude", -7.3112);
        databaseReference.child("location329").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9170);
        locationData.put("longitude", -7.3468);
        databaseReference.child("location330").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9168);
        locationData.put("longitude", -7.3471);
        databaseReference.child("location331").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0892);
        locationData.put("longitude", -7.3349);
        databaseReference.child("location332").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1230);
        locationData.put("longitude", -7.3304);
        databaseReference.child("location333").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1292);
        locationData.put("longitude", -7.2116);
        databaseReference.child("location334").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1500);
        locationData.put("longitude", -7.1314);
        databaseReference.child("location335").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8189);
        locationData.put("longitude", -6.9514);
        databaseReference.child("location336").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6448);
        locationData.put("longitude", -7.8224);
        databaseReference.child("location337").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6904);
        locationData.put("longitude", -7.8503);
        databaseReference.child("location338").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7254);
        locationData.put("longitude", -7.8543);
        databaseReference.child("location339").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7475);
        locationData.put("longitude", -7.8014);
        databaseReference.child("location340").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6991);
        locationData.put("longitude", -7.5999);
        databaseReference.child("location341").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5505);
        locationData.put("longitude", -7.7175);
        databaseReference.child("location342").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8263);
        locationData.put("longitude", -7.7579);
        databaseReference.child("location343").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7973);
        locationData.put("longitude", -6.4889);
        databaseReference.child("location344").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8416);
        locationData.put("longitude", -6.5407);
        databaseReference.child("location345").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8926);
        locationData.put("longitude", -6.5516);
        databaseReference.child("location346").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8990);
        locationData.put("longitude", -6.3942);
        databaseReference.child("location347").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9363);
        locationData.put("longitude", -6.3817);
        databaseReference.child("location348").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9586);
        locationData.put("longitude", -6.4466);
        databaseReference.child("location349").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9656);
        locationData.put("longitude", -6.5006);
        databaseReference.child("location350").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9738);
        locationData.put("longitude", -6.5285);
        databaseReference.child("location351").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0261);
        locationData.put("longitude", -6.3844);
        databaseReference.child("location352").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0250);
        locationData.put("longitude", -6.4094);
        databaseReference.child("location353").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0213);
        locationData.put("longitude", -6.4605);
        databaseReference.child("location354").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0327);
        locationData.put("longitude", -6.5334);
        databaseReference.child("location355").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0620);
        locationData.put("longitude", -6.2084);
        databaseReference.child("location356").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8101);
        locationData.put("longitude", -6.4328);
        databaseReference.child("location357").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7163);
        locationData.put("longitude", -6.3576);
        databaseReference.child("location358").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7184);
        locationData.put("longitude", -6.4592);
        databaseReference.child("location359").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8429);
        locationData.put("longitude", -9.0110);
        databaseReference.child("location360").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5361);
        locationData.put("longitude", -9.2125);
        databaseReference.child("location361").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7770);
        locationData.put("longitude", -9.3077);
        databaseReference.child("location362").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8483);
        locationData.put("longitude", -9.3100);
        databaseReference.child("location363").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8095);
        locationData.put("longitude", -9.4272);
        databaseReference.child("location364").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.2082);
        locationData.put("longitude", -9.9359);
        databaseReference.child("location365").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.2072);
        locationData.put("longitude", -9.9258);
        databaseReference.child("location366").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1157);
        locationData.put("longitude", -9.1471);
        databaseReference.child("location367").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1198);
        locationData.put("longitude", -9.1603);
        databaseReference.child("location368").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7697);
        locationData.put("longitude", -7.1626);
        databaseReference.child("location369").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6382);
        locationData.put("longitude", -6.8938);
        databaseReference.child("location370").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5841);
        locationData.put("longitude", -6.7160);
        databaseReference.child("location371").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5271);
        locationData.put("longitude", -6.6772);
        databaseReference.child("location372").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5513);
        locationData.put("longitude", -6.7846);
        databaseReference.child("location373").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5188);
        locationData.put("longitude", -6.5472);
        databaseReference.child("location374").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5214);
        locationData.put("longitude", -6.5231);
        databaseReference.child("location375").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4681);
        locationData.put("longitude", -6.4710);
        databaseReference.child("location376").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4679);
        locationData.put("longitude", -6.3591);
        databaseReference.child("location377").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4963);
        locationData.put("longitude", -6.3803);
        databaseReference.child("location378").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5506);
        locationData.put("longitude", -6.4347);
        databaseReference.child("location379").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6264);
        locationData.put("longitude", -6.6896);
        databaseReference.child("location380").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6646);
        locationData.put("longitude", -6.6802);
        databaseReference.child("location381").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6557);
        locationData.put("longitude", -6.6885);
        databaseReference.child("location382").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7281);
        locationData.put("longitude", -6.8768);
        databaseReference.child("location383").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7510);
        locationData.put("longitude", -6.8603);
        databaseReference.child("location384").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7295);
        locationData.put("longitude", -6.8819);
        databaseReference.child("location385").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7783);
        locationData.put("longitude", -6.7886);
        databaseReference.child("location386").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7363);
        locationData.put("longitude", -6.7127);
        databaseReference.child("location387").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6634);
        locationData.put("longitude", -6.4018);
        databaseReference.child("location388").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7067);
        locationData.put("longitude", -6.3254);
        databaseReference.child("location389").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6348);
        locationData.put("longitude", -6.2198);
        databaseReference.child("location390").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4243);
        locationData.put("longitude", -6.8306);
        databaseReference.child("location391").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9474);
        locationData.put("longitude", -8.3352);
        databaseReference.child("location392").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9037);
        locationData.put("longitude", -8.5838);
        databaseReference.child("location393").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7693);
        locationData.put("longitude", -8.2208);
        databaseReference.child("location394").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7696);
        locationData.put("longitude", -8.1077);
        databaseReference.child("location395").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6930);
        locationData.put("longitude", -8.4338);
        databaseReference.child("location396").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6870);
        locationData.put("longitude", -8.3761);
        databaseReference.child("location397").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6436);
        locationData.put("longitude", -8.2153);
        databaseReference.child("location398").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6575);
        locationData.put("longitude", -8.0918);
        databaseReference.child("location399").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6108);
        locationData.put("longitude", -8.2017);
        databaseReference.child("location400").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3809);
        locationData.put("longitude", -7.9954);
        databaseReference.child("location401").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3340);
        locationData.put("longitude", -8.2038);
        databaseReference.child("location402").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1122);
        locationData.put("longitude", -8.0010);
        databaseReference.child("location403").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9734);
        locationData.put("longitude", -7.8646);
        databaseReference.child("location404").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1278);
        locationData.put("longitude", -7.7907);
        databaseReference.child("location405").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0954);
        locationData.put("longitude", -7.6453);
        databaseReference.child("location406").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0982);
        locationData.put("longitude", -7.5928);
        databaseReference.child("location407").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1328);
        locationData.put("longitude", -7.5633);
        databaseReference.child("location408").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2830);
        locationData.put("longitude", -7.2966);
        databaseReference.child("location409").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1561);
        locationData.put("longitude", -7.1626);
        databaseReference.child("location410").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1743);
        locationData.put("longitude", -7.1589);
        databaseReference.child("location411").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1765);
        locationData.put("longitude", -7.1375);
        databaseReference.child("location412").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1766);
        locationData.put("longitude", -7.0342);
        databaseReference.child("location413").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2366);
        locationData.put("longitude", -7.0603);
        databaseReference.child("location414").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2302);
        locationData.put("longitude", -7.1143);
        databaseReference.child("location415").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2599);
        locationData.put("longitude", -7.1060);
        databaseReference.child("location416").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2585);
        locationData.put("longitude", -7.1294);
        databaseReference.child("location417").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2650);
        locationData.put("longitude", -7.1048);
        databaseReference.child("location418").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2474);
        locationData.put("longitude", -7.1109);
        databaseReference.child("location419").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8748);
        locationData.put("longitude", -8.6043);
        databaseReference.child("location420").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7685);
        locationData.put("longitude", -6.1900);
        databaseReference.child("location421").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6852);
        locationData.put("longitude", -6.2796);
        databaseReference.child("location422").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6449);
        locationData.put("longitude", -6.2289);
        databaseReference.child("location423").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6426);
        locationData.put("longitude", -6.2326);
        databaseReference.child("location424").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5970);
        locationData.put("longitude", -6.5839);
        databaseReference.child("location425").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4593);
        locationData.put("longitude", -6.4005);
        databaseReference.child("location426").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5146);
        locationData.put("longitude", -6.3678);
        databaseReference.child("location427").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5030);
        locationData.put("longitude", -6.5725);
        databaseReference.child("location428").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4328);
        locationData.put("longitude", -6.7069);
        databaseReference.child("location429").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3772);
        locationData.put("longitude", -6.5689);
        databaseReference.child("location430").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3034);
        locationData.put("longitude", -6.4582);
        databaseReference.child("location431").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4412);
        locationData.put("longitude", -6.3567);
        databaseReference.child("location432").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3505);
        locationData.put("longitude", -6.4938);
        databaseReference.child("location433").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3269);
        locationData.put("longitude", -6.4846);
        databaseReference.child("location434").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3241);
        locationData.put("longitude", -6.4927);
        databaseReference.child("location435").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3182);
        locationData.put("longitude", -6.5183);
        databaseReference.child("location436").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1425);
        locationData.put("longitude", -6.5598);
        databaseReference.child("location437").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1045);
        locationData.put("longitude", -6.6053);
        databaseReference.child("location438").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0269);
        locationData.put("longitude", -6.6404);
        databaseReference.child("location439").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9593);
        locationData.put("longitude", -6.6997);
        databaseReference.child("location440").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9469);
        locationData.put("longitude", -6.7121);
        databaseReference.child("location441").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7875);
        locationData.put("longitude", -6.1677);
        databaseReference.child("location442").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7874);
        locationData.put("longitude", -6.1678);
        databaseReference.child("location443").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8659);
        locationData.put("longitude", -6.1039);
        databaseReference.child("location444").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9032);
        locationData.put("longitude", -6.1019);
        databaseReference.child("location445").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9481);
        locationData.put("longitude", -6.1012);
        databaseReference.child("location446").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0849);
        locationData.put("longitude", -6.0676);
        databaseReference.child("location447").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2020);
        locationData.put("longitude", -6.5010);
        databaseReference.child("location448").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1853);
        locationData.put("longitude", -6.5205);
        databaseReference.child("location449").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3393);
        locationData.put("longitude", -8.6767);
        databaseReference.child("location450").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0965);
        locationData.put("longitude", -7.9107);
        databaseReference.child("location451").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7694);
        locationData.put("longitude", -6.9549);
        databaseReference.child("location452").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3304);
        locationData.put("longitude", -7.0552);
        databaseReference.child("location453").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1037);
        locationData.put("longitude", -7.6589);
        databaseReference.child("location454").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3852);
        locationData.put("longitude", -6.9015);
        databaseReference.child("location455").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3928);
        locationData.put("longitude", -6.9338);
        databaseReference.child("location456").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3608);
        locationData.put("longitude", -6.7983);
        databaseReference.child("location457").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4336);
        locationData.put("longitude", -6.7741);
        databaseReference.child("location458").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4402);
        locationData.put("longitude", -6.7344);
        databaseReference.child("location459").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4943);
        locationData.put("longitude", -6.5909);
        databaseReference.child("location460").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4400);
        locationData.put("longitude", -6.5389);
        databaseReference.child("location461").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5735);
        locationData.put("longitude", -6.5181);
        databaseReference.child("location462").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5896);
        locationData.put("longitude", -6.4943);
        databaseReference.child("location463").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6282);
        locationData.put("longitude", -6.6238);
        databaseReference.child("location464").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6484);
        locationData.put("longitude", -6.6464);
        databaseReference.child("location465").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2605);
        locationData.put("longitude", -6.4593);
        databaseReference.child("location466").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2445);
        locationData.put("longitude", -6.3669);
        databaseReference.child("location467").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3414);
        locationData.put("longitude", -6.5729);
        databaseReference.child("location468").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3288);
        locationData.put("longitude", -6.5068);
        databaseReference.child("location469").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0121);
        locationData.put("longitude", -6.4031);
        databaseReference.child("location470").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0008);
        locationData.put("longitude", -6.4057);
        databaseReference.child("location471").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2389);
        locationData.put("longitude", -7.1770);
        databaseReference.child("location472").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3518);
        locationData.put("longitude", -7.3868);
        databaseReference.child("location473").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.9651);
        locationData.put("longitude", -7.7623);
        databaseReference.child("location474").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.2623);
        locationData.put("longitude", -7.4093);
        databaseReference.child("location475").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.7952);
        locationData.put("longitude", -7.7937);
        databaseReference.child("location476").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.8023);
        locationData.put("longitude", -7.8051);
        databaseReference.child("location477").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9911);
        locationData.put("longitude", -6.3788);
        databaseReference.child("location478").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9974);
        locationData.put("longitude", -6.8981);
        databaseReference.child("location479").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7963);
        locationData.put("longitude", -6.4873);
        databaseReference.child("location480").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7736);
        locationData.put("longitude", -7.4938);
        databaseReference.child("location481").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2753);
        locationData.put("longitude", -8.9849);
        databaseReference.child("location482").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2601);
        locationData.put("longitude", -9.1258);
        databaseReference.child("location483").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6532);
        locationData.put("longitude", -6.6825);
        databaseReference.child("location484").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5079);
        locationData.put("longitude", -6.1844);
        databaseReference.child("location485").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5220);
        locationData.put("longitude", -6.1479);
        databaseReference.child("location486").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4853);
        locationData.put("longitude", -6.1507);
        databaseReference.child("location487").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3896);
        locationData.put("longitude", -6.1979);
        databaseReference.child("location488").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3590);
        locationData.put("longitude", -6.1962);
        databaseReference.child("location489").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3908);
        locationData.put("longitude", -6.3197);
        databaseReference.child("location490").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3643);
        locationData.put("longitude", -6.2293);
        databaseReference.child("location491").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3698);
        locationData.put("longitude", -6.3249);
        databaseReference.child("location492").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3613);
        locationData.put("longitude", -6.2910);
        databaseReference.child("location493").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3545);
        locationData.put("longitude", -6.2733);
        databaseReference.child("location494").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3499);
        locationData.put("longitude", -6.2680);
        databaseReference.child("location495").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3528);
        locationData.put("longitude", -6.2489);
        databaseReference.child("location496").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3475);
        locationData.put("longitude", -6.2907);
        databaseReference.child("location497").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3452);
        locationData.put("longitude", -6.2693);
        databaseReference.child("location498").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3240);
        locationData.put("longitude", -6.3282);
        databaseReference.child("location499").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3215);
        locationData.put("longitude", -6.2794);
        databaseReference.child("location500").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3170);
        locationData.put("longitude", -6.3262);
        databaseReference.child("location501").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2916);
        locationData.put("longitude", -6.3574);
        databaseReference.child("location502").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2894);
        locationData.put("longitude", -6.4015);
        databaseReference.child("location503").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2843);
        locationData.put("longitude", -6.3783);
        databaseReference.child("location504").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1481);
        locationData.put("longitude", -6.0790);
        databaseReference.child("location505").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9761);
        locationData.put("longitude", -9.2975);
        databaseReference.child("location506").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8566);
        locationData.put("longitude", -9.0145);
        databaseReference.child("location507").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6732);
        locationData.put("longitude", -8.6403);
        databaseReference.child("location508").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6732);
        locationData.put("longitude", -8.6403);
        databaseReference.child("location509").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6713);
        locationData.put("longitude", -8.5215);
        databaseReference.child("location510").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6348);
        locationData.put("longitude", -7.2517);
        databaseReference.child("location511").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9885);
        locationData.put("longitude", -8.7881);
        databaseReference.child("location512").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9185);
        locationData.put("longitude", -8.6191);
        databaseReference.child("location513").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8828);
        locationData.put("longitude", -8.6703);
        databaseReference.child("location514").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8861);
        locationData.put("longitude", -8.6095);
        databaseReference.child("location515").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9030);
        locationData.put("longitude", -8.4259);
        databaseReference.child("location516").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8784);
        locationData.put("longitude", -8.5267);
        databaseReference.child("location517").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8646);
        locationData.put("longitude", -8.4529);
        databaseReference.child("location518").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8812);
        locationData.put("longitude", -8.4429);
        databaseReference.child("location519").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9196);
        locationData.put("longitude", -8.4732);
        databaseReference.child("location520").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8648);
        locationData.put("longitude", -8.4397);
        databaseReference.child("location521").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9069);
        locationData.put("longitude", -8.4963);
        databaseReference.child("location522").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9055);
        locationData.put("longitude", -9.7501);
        databaseReference.child("location523").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8926);
        locationData.put("longitude", -9.1357);
        databaseReference.child("location524").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9240);
        locationData.put("longitude", -8.9772);
        databaseReference.child("location525").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9594);
        locationData.put("longitude", -8.7603);
        databaseReference.child("location526").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8388);
        locationData.put("longitude", -8.8588);
        databaseReference.child("location527").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7313);
        locationData.put("longitude", -8.8642);
        databaseReference.child("location528").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7235);
        locationData.put("longitude", -8.9741);
        databaseReference.child("location529").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7768);
        locationData.put("longitude", -9.0712);
        databaseReference.child("location530").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8181);
        locationData.put("longitude", -9.1645);
        databaseReference.child("location531").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8181);
        locationData.put("longitude", -9.1645);
        databaseReference.child("location532").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8228);
        locationData.put("longitude", -9.5375);
        databaseReference.child("location533").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7841);
        locationData.put("longitude", -9.4727);
        databaseReference.child("location534").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8136);
        locationData.put("longitude", -9.4076);
        databaseReference.child("location535").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5337);
        locationData.put("longitude", -9.0933);
        databaseReference.child("location536").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0275);
        locationData.put("longitude", -8.7561);
        databaseReference.child("location537").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1072);
        locationData.put("longitude", -8.5334);
        databaseReference.child("location538").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.2763);
        locationData.put("longitude", -8.4588);
        databaseReference.child("location539").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.3114);
        locationData.put("longitude", -8.2365);
        databaseReference.child("location540").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9215);
        locationData.put("longitude", -7.9128);
        databaseReference.child("location541").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8416);
        locationData.put("longitude", -7.9181);
        databaseReference.child("location542").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.3001);
        locationData.put("longitude", -8.1936);
        databaseReference.child("location543").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1829);
        locationData.put("longitude", -7.7068);
        databaseReference.child("location544").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1143);
        locationData.put("longitude", -7.6326);
        databaseReference.child("location545").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0921);
        locationData.put("longitude", -7.5421);
        databaseReference.child("location546").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0835);
        locationData.put("longitude", -7.4195);
        databaseReference.child("location547").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0355);
        locationData.put("longitude", -7.2686);
        databaseReference.child("location548").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0399);
        locationData.put("longitude", -7.3780);
        databaseReference.child("location549").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9622);
        locationData.put("longitude", -7.4275);
        databaseReference.child("location550").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8569);
        locationData.put("longitude", -7.2023);
        databaseReference.child("location551").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9849);
        locationData.put("longitude", -7.3255);
        databaseReference.child("location552").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.2511);
        locationData.put("longitude", -6.9981);
        databaseReference.child("location553").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.2427);
        locationData.put("longitude", -6.9518);
        databaseReference.child("location554").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.2288);
        locationData.put("longitude", -6.9700);
        databaseReference.child("location555").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.2304);
        locationData.put("longitude", -7.0795);
        databaseReference.child("location556").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9039);
        locationData.put("longitude", -6.7918);
        databaseReference.child("location557").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9103);
        locationData.put("longitude", -7.1967);
        databaseReference.child("location558").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8198);
        locationData.put("longitude", -7.0541);
        databaseReference.child("location559").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4665);
        locationData.put("longitude", -9.8227);
        databaseReference.child("location560").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2511);
        locationData.put("longitude", -9.6182);
        databaseReference.child("location561").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2878);
        locationData.put("longitude", -9.5631);
        databaseReference.child("location562").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4286);
        locationData.put("longitude", -9.3188);
        databaseReference.child("location563").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3725);
        locationData.put("longitude", -9.2387);
        databaseReference.child("location564").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2439);
        locationData.put("longitude", -9.3340);
        databaseReference.child("location565").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3164);
        locationData.put("longitude", -9.1526);
        databaseReference.child("location566").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3164);
        locationData.put("longitude", -9.0761);
        databaseReference.child("location567").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2887);
        locationData.put("longitude", -9.0104);
        databaseReference.child("location568").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3015);
        locationData.put("longitude", -9.0348);
        databaseReference.child("location569").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2740);
        locationData.put("longitude", -8.9795);
        databaseReference.child("location570").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2777);
        locationData.put("longitude", -8.9266);
        databaseReference.child("location571").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2986);
        locationData.put("longitude", -9.0104);
        databaseReference.child("location572").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6492);
        locationData.put("longitude", -8.9477);
        databaseReference.child("location573").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5878);
        locationData.put("longitude", -8.8649);
        databaseReference.child("location574").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4750);
        locationData.put("longitude", -8.9095);
        databaseReference.child("location575").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3886);
        locationData.put("longitude", -8.9419);
        databaseReference.child("location576").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5179);
        locationData.put("longitude", -8.3409);
        databaseReference.child("location577").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2599);
        locationData.put("longitude", -8.7393);
        databaseReference.child("location578").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3128);
        locationData.put("longitude", -8.2621);
        databaseReference.child("location579").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3366);
        locationData.put("longitude", -8.1311);
        databaseReference.child("location580").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2250);
        locationData.put("longitude", -8.1947);
        databaseReference.child("location581").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0026);
        locationData.put("longitude", -8.8244);
        databaseReference.child("location582").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2065);
        locationData.put("longitude", -8.5255);
        databaseReference.child("location583").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0930);
        locationData.put("longitude", -6.3592);
        databaseReference.child("location584").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9122);
        locationData.put("longitude", -6.3967);
        databaseReference.child("location585").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7145);
        locationData.put("longitude", -6.3890);
        databaseReference.child("location586").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.0506);
        locationData.put("longitude", -7.8186);
        databaseReference.child("location587").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.8587);
        locationData.put("longitude", -7.7508);
        databaseReference.child("location588").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.7717);
        locationData.put("longitude", -7.8572);
        databaseReference.child("location589").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.0315);
        locationData.put("longitude", -8.1448);
        databaseReference.child("location590").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5925);
        locationData.put("longitude", -8.1235);
        databaseReference.child("location591").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6093);
        locationData.put("longitude", -7.7091);
        databaseReference.child("location592").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5642);
        locationData.put("longitude", -7.7648);
        databaseReference.child("location593").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5199);
        locationData.put("longitude", -8.0589);
        databaseReference.child("location594").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4387);
        locationData.put("longitude", -7.8990);
        databaseReference.child("location595").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6268);
        locationData.put("longitude", -7.0586);
        databaseReference.child("location596").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7238);
        locationData.put("longitude", -6.8610);
        databaseReference.child("location597").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6457);
        locationData.put("longitude", -6.5072);
        databaseReference.child("location598").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5568);
        locationData.put("longitude", -6.5765);
        databaseReference.child("location599").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6213);
        locationData.put("longitude", -6.4245);
        databaseReference.child("location600").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5166);
        locationData.put("longitude", -6.4065);
        databaseReference.child("location601").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5388);
        locationData.put("longitude", -6.0923);
        databaseReference.child("location602").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4112);
        locationData.put("longitude", -6.1794);
        databaseReference.child("location603").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3830);
        locationData.put("longitude", -6.2052);
        databaseReference.child("location604").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4032);
        locationData.put("longitude", -6.2820);
        databaseReference.child("location605").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3973);
        locationData.put("longitude", -6.2450);
        databaseReference.child("location606").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5745);
        locationData.put("longitude", -6.2051);
        databaseReference.child("location607").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3637);
        locationData.put("longitude", -6.2249);
        databaseReference.child("location608").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4389);
        locationData.put("longitude", -6.3533);
        databaseReference.child("location609").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3816);
        locationData.put("longitude", -6.3166);
        databaseReference.child("location610").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3795);
        locationData.put("longitude", -6.3106);
        databaseReference.child("location611").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3832);
        locationData.put("longitude", -6.3934);
        databaseReference.child("location612").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3589);
        locationData.put("longitude", -6.4228);
        databaseReference.child("location613").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3423);
        locationData.put("longitude", -6.3495);
        databaseReference.child("location614").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2772);
        locationData.put("longitude", -6.3986);
        databaseReference.child("location615").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3226);
        locationData.put("longitude", -6.2793);
        databaseReference.child("location616").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3172);
        locationData.put("longitude", -6.2705);
        databaseReference.child("location617").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2919);
        locationData.put("longitude", -6.1994);
        databaseReference.child("location618").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1535);
        locationData.put("longitude", -6.7132);
        databaseReference.child("location619").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0983);
        locationData.put("longitude", -6.9215);
        databaseReference.child("location620").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9374);
        locationData.put("longitude", -6.8360);
        databaseReference.child("location621").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0899);
        locationData.put("longitude", -6.8100);
        databaseReference.child("location622").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2541);
        locationData.put("longitude", -7.0128);
        databaseReference.child("location623").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8759);
        locationData.put("longitude", -6.9319);
        databaseReference.child("location624").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3107);
        locationData.put("longitude", -7.5013);
        databaseReference.child("location625").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2182);
        locationData.put("longitude", -7.4705);
        databaseReference.child("location626").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0347);
        locationData.put("longitude", -7.3031);
        databaseReference.child("location627").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8706);
        locationData.put("longitude", -7.3638);
        databaseReference.child("location628").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0200);
        locationData.put("longitude", -7.1610);
        databaseReference.child("location629").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8643);
        locationData.put("longitude", -6.9909);
        databaseReference.child("location630").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8532);
        locationData.put("longitude", -7.0159);
        databaseReference.child("location631").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8562);
        locationData.put("longitude", -7.5681);
        databaseReference.child("location632").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9895);
        locationData.put("longitude", -6.6557);
        databaseReference.child("location633").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1485);
        locationData.put("longitude", -6.5568);
        databaseReference.child("location634").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1796);
        locationData.put("longitude", -6.1366);
        databaseReference.child("location635").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9296);
        locationData.put("longitude", -6.0412);
        databaseReference.child("location636").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9296);
        locationData.put("longitude", -6.0412);
        databaseReference.child("location637").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9473);
        locationData.put("longitude", -6.1786);
        databaseReference.child("location638").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1224);
        locationData.put("longitude", -6.1053);
        databaseReference.child("location639").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2182);
        locationData.put("longitude", -9.0216);
        databaseReference.child("location640").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4930);
        locationData.put("longitude", -9.6491);
        databaseReference.child("location641").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4012);
        locationData.put("longitude", -9.8206);
        databaseReference.child("location642").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0286);
        locationData.put("longitude", -9.3935);
        databaseReference.child("location643").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3128);
        locationData.put("longitude", -9.7687);
        databaseReference.child("location644").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4209);
        locationData.put("longitude", -9.3951);
        databaseReference.child("location645").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0913);
        locationData.put("longitude", -9.8471);
        databaseReference.child("location646").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2592);
        locationData.put("longitude", -9.6494);
        databaseReference.child("location647").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8744);
        locationData.put("longitude", -9.6441);
        databaseReference.child("location648").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1790);
        locationData.put("longitude", -9.5550);
        databaseReference.child("location649").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2390);
        locationData.put("longitude", -9.8318);
        databaseReference.child("location650").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3849);
        locationData.put("longitude", -9.7473);
        databaseReference.child("location651").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.6728);
        locationData.put("longitude", -9.2071);
        databaseReference.child("location652").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.6317);
        locationData.put("longitude", -8.9471);
        databaseReference.child("location653").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.7379);
        locationData.put("longitude", -8.9322);
        databaseReference.child("location654").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8241);
        locationData.put("longitude", -8.8716);
        databaseReference.child("location655").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9039);
        locationData.put("longitude", -8.7868);
        databaseReference.child("location656").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0685);
        locationData.put("longitude", -9.1041);
        databaseReference.child("location657").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1778);
        locationData.put("longitude", -8.2608);
        databaseReference.child("location658").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9198);
        locationData.put("longitude", -8.0320);
        databaseReference.child("location659").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9534);
        locationData.put("longitude", -8.1055);
        databaseReference.child("location660").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0610);
        locationData.put("longitude", -8.2891);
        databaseReference.child("location661").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0934);
        locationData.put("longitude", -8.1030);
        databaseReference.child("location662").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9586);
        locationData.put("longitude", -9.1944);
        databaseReference.child("location663").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0559);
        locationData.put("longitude", -8.6050);
        databaseReference.child("location664").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8763);
        locationData.put("longitude", -8.6165);
        databaseReference.child("location665").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9012);
        locationData.put("longitude", -8.4934);
        databaseReference.child("location666").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8384);
        locationData.put("longitude", -8.4818);
        databaseReference.child("location667").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9152);
        locationData.put("longitude", -8.4735);
        databaseReference.child("location668").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9057);
        locationData.put("longitude", -8.5755);
        databaseReference.child("location669").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8855);
        locationData.put("longitude", -8.4864);
        databaseReference.child("location670").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8592);
        locationData.put("longitude", -8.5281);
        databaseReference.child("location671").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8983);
        locationData.put("longitude", -8.4617);
        databaseReference.child("location672").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8827);
        locationData.put("longitude", -8.5071);
        databaseReference.child("location673").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4397);
        locationData.put("longitude", -9.0864);
        databaseReference.child("location674").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4423);
        locationData.put("longitude", -9.0422);
        databaseReference.child("location675").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3990);
        locationData.put("longitude", -8.2985);
        databaseReference.child("location676").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6503);
        locationData.put("longitude", -8.4126);
        databaseReference.child("location677").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6313);
        locationData.put("longitude", -8.5143);
        databaseReference.child("location678").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4555);
        locationData.put("longitude", -8.5576);
        databaseReference.child("location679").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7303);
        locationData.put("longitude", -8.4714);
        databaseReference.child("location680").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6328);
        locationData.put("longitude", -8.7284);
        databaseReference.child("location681").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4929);
        locationData.put("longitude", -9.2909);
        databaseReference.child("location682").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3417);
        locationData.put("longitude", -8.5242);
        databaseReference.child("location683").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6612);
        locationData.put("longitude", -8.6337);
        databaseReference.child("location684").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3761);
        locationData.put("longitude", -8.3520);
        databaseReference.child("location685").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5569);
        locationData.put("longitude", -8.7257);
        databaseReference.child("location686").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3716);
        locationData.put("longitude", -8.5198);
        databaseReference.child("location687").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5611);
        locationData.put("longitude", -8.7941);
        databaseReference.child("location688").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2569);
        locationData.put("longitude", -9.5994);
        databaseReference.child("location689").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7114);
        locationData.put("longitude", -7.6012);
        databaseReference.child("location690").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7386);
        locationData.put("longitude", -7.6729);
        databaseReference.child("location691").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8383);
        locationData.put("longitude", -7.8202);
        databaseReference.child("location692").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8907);
        locationData.put("longitude", -7.8021);
        databaseReference.child("location693").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9477);
        locationData.put("longitude", -7.8106);
        databaseReference.child("location694").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9295);
        locationData.put("longitude", -7.8478);
        databaseReference.child("location695").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9295);
        locationData.put("longitude", -7.8478);
        databaseReference.child("location696").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7121);
        locationData.put("longitude", -8.4111);
        databaseReference.child("location697").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5507);
        locationData.put("longitude", -7.5390);
        databaseReference.child("location698").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3798);
        locationData.put("longitude", -7.8792);
        databaseReference.child("location699").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9286);
        locationData.put("longitude", -8.1658);
        databaseReference.child("location700").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5722);
        locationData.put("longitude", -7.8379);
        databaseReference.child("location701").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6493);
        locationData.put("longitude", -7.7198);
        databaseReference.child("location702").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6931);
        locationData.put("longitude", -7.8121);
        databaseReference.child("location703").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7567);
        locationData.put("longitude", -7.8449);
        databaseReference.child("location704").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8259);
        locationData.put("longitude", -8.1984);
        databaseReference.child("location705").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9792);
        locationData.put("longitude", -7.7574);
        databaseReference.child("location706").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1206);
        locationData.put("longitude", -7.5637);
        databaseReference.child("location707").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2388);
        locationData.put("longitude", -7.2596);
        databaseReference.child("location708").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2174);
        locationData.put("longitude", -7.3824);
        databaseReference.child("location709").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0616);
        locationData.put("longitude", -7.6497);
        databaseReference.child("location710").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2263);
        locationData.put("longitude", -7.0578);
        databaseReference.child("location711").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3195);
        locationData.put("longitude", -7.2980);
        databaseReference.child("location712").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4886);
        locationData.put("longitude", -7.2209);
        databaseReference.child("location713").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7467);
        locationData.put("longitude", -6.7746);
        databaseReference.child("location714").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8324);
        locationData.put("longitude", -6.9098);
        databaseReference.child("location715").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7432);
        locationData.put("longitude", -6.9760);
        databaseReference.child("location716").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5870);
        locationData.put("longitude", -7.1798);
        databaseReference.child("location717").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6102);
        locationData.put("longitude", -6.9549);
        databaseReference.child("location718").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6373);
        locationData.put("longitude", -6.9418);
        databaseReference.child("location719").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7326);
        locationData.put("longitude", -7.3948);
        databaseReference.child("location720").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8457);
        locationData.put("longitude", -6.7910);
        databaseReference.child("location721").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2419);
        locationData.put("longitude", -6.9449);
        databaseReference.child("location722").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3820);
        locationData.put("longitude", -6.9534);
        databaseReference.child("location723").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3228);
        locationData.put("longitude", -6.5010);
        databaseReference.child("location724").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2849);
        locationData.put("longitude", -6.4902);
        databaseReference.child("location725").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3016);
        locationData.put("longitude", -6.5284);
        databaseReference.child("location726").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2311);
        locationData.put("longitude", -6.4985);
        databaseReference.child("location727").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2796);
        locationData.put("longitude", -6.7214);
        databaseReference.child("location728").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2915);
        locationData.put("longitude", -6.7545);
        databaseReference.child("location729").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2632);
        locationData.put("longitude", -6.7897);
        databaseReference.child("location730").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2919);
        locationData.put("longitude", -6.8688);
        databaseReference.child("location731").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3614);
        locationData.put("longitude", -6.7841);
        databaseReference.child("location732").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3628);
        locationData.put("longitude", -6.8292);
        databaseReference.child("location733").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6016);
        locationData.put("longitude", -6.4639);
        databaseReference.child("location734").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2842);
        locationData.put("longitude", -6.6375);
        databaseReference.child("location735").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5569);
        locationData.put("longitude", -6.5343);
        databaseReference.child("location736").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6733);
        locationData.put("longitude", -6.3027);
        databaseReference.child("location737").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0759);
        locationData.put("longitude", -6.0957);
        databaseReference.child("location738").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8465);
        locationData.put("longitude", -6.2245);
        databaseReference.child("location739").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1446);
        locationData.put("longitude", -7.0222);
        databaseReference.child("location740").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3484);
        locationData.put("longitude", -6.4562);
        databaseReference.child("location741").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8737);
        locationData.put("longitude", -8.4693);
        databaseReference.child("location742").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.7465);
        locationData.put("longitude", -8.8429);
        databaseReference.child("location743").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6577);
        locationData.put("longitude", -7.2075);
        databaseReference.child("location744").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6059);
        locationData.put("longitude", -7.2990);
        databaseReference.child("location745").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3631);
        locationData.put("longitude", -7.3253);
        databaseReference.child("location746").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5713);
        locationData.put("longitude", -7.2336);
        databaseReference.child("location747").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6319);
        locationData.put("longitude", -7.2520);
        databaseReference.child("location748").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7014);
        locationData.put("longitude", -6.9477);
        databaseReference.child("location749").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5922);
        locationData.put("longitude", -7.1864);
        databaseReference.child("location750").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6569);
        locationData.put("longitude", -7.2607);
        databaseReference.child("location751").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7466);
        locationData.put("longitude", -7.2370);
        databaseReference.child("location752").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3649);
        locationData.put("longitude", -7.0126);
        databaseReference.child("location753").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8778);
        locationData.put("longitude", -8.2024);
        databaseReference.child("location754").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0183);
        locationData.put("longitude", -8.1254);
        databaseReference.child("location755").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8605);
        locationData.put("longitude", -8.1962);
        databaseReference.child("location756").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8314);
        locationData.put("longitude", -8.0977);
        databaseReference.child("location757").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7575);
        locationData.put("longitude", -7.9888);
        databaseReference.child("location758").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6785);
        locationData.put("longitude", -7.8167);
        databaseReference.child("location759").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6375);
        locationData.put("longitude", -7.8823);
        databaseReference.child("location760").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3176);
        locationData.put("longitude", -8.1170);
        databaseReference.child("location761").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4089);
        locationData.put("longitude", -7.9687);
        databaseReference.child("location762").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4888);
        locationData.put("longitude", -8.0586);
        databaseReference.child("location763").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4368);
        locationData.put("longitude", -7.7917);
        databaseReference.child("location764").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3607);
        locationData.put("longitude", -7.6367);
        databaseReference.child("location765").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3851);
        locationData.put("longitude", -6.9010);
        databaseReference.child("location766").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4181);
        locationData.put("longitude", -6.8963);
        databaseReference.child("location767").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5777);
        locationData.put("longitude", -6.7230);
        databaseReference.child("location768").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3246);
        locationData.put("longitude", -6.6451);
        databaseReference.child("location769").setValue(locationData);

        //From here
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3408);
        locationData.put("longitude", -6.5035);
        databaseReference.child("location770").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6122);
        locationData.put("longitude", -6.3059);
        databaseReference.child("location771").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6876);
        locationData.put("longitude", -6.3965);
        databaseReference.child("location772").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1472);
        locationData.put("longitude", -8.0409);
        databaseReference.child("location773").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1215);
        locationData.put("longitude", -7.6903);
        databaseReference.child("location774").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1167);
        locationData.put("longitude", -7.5072);
        databaseReference.child("location775").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2349);
        locationData.put("longitude", -7.0412);
        databaseReference.child("location776").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1755);
        locationData.put("longitude", -7.0337);
        databaseReference.child("location777").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2640);
        locationData.put("longitude", -7.1188);
        databaseReference.child("location778").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0358);
        locationData.put("longitude", -10.0355);
        databaseReference.child("location779").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9588);
        locationData.put("longitude", -9.3720);
        databaseReference.child("location780").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0511);
        locationData.put("longitude", -9.3968);
        databaseReference.child("location781").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0580);
        locationData.put("longitude", -9.4723);
        databaseReference.child("location782").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0374);
        locationData.put("longitude", -9.4935);
        databaseReference.child("location783").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0719);
        locationData.put("longitude", -9.5971);
        databaseReference.child("location784").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.0693);
        locationData.put("longitude", -9.5166);
        databaseReference.child("location785").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1703);
        locationData.put("longitude", -9.5523);
        databaseReference.child("location786").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2395);
        locationData.put("longitude", -9.9969);
        databaseReference.child("location787").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2772);
        locationData.put("longitude", -9.8271);
        databaseReference.child("location788").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3272);
        locationData.put("longitude", -9.7382);
        databaseReference.child("location789").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2713);
        locationData.put("longitude", -9.6999);
        databaseReference.child("location790").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3700);
        locationData.put("longitude", -9.6340);
        databaseReference.child("location791").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3275);
        locationData.put("longitude", -9.6898);
        databaseReference.child("location792").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4610);
        locationData.put("longitude", -9.6760);
        databaseReference.child("location793").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3225);
        locationData.put("longitude", -9.6490);
        databaseReference.child("location794").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.1762);
        locationData.put("longitude", -9.5476);
        databaseReference.child("location795").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4550);
        locationData.put("longitude", -9.4378);
        databaseReference.child("location796").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5461);
        locationData.put("longitude", -9.4750);
        databaseReference.child("location797").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.2692);
        locationData.put("longitude", -9.6887);
        databaseReference.child("location798").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.6968);
        locationData.put("longitude", -9.7199);
        databaseReference.child("location799").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.7502);
        locationData.put("longitude", -9.5494);
        databaseReference.child("location800").setValue(locationData);

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

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.2782);
        locationData.put("longitude", -9.0213);
        databaseReference.child("location901").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0821);
        locationData.put("longitude", -8.5634);
        databaseReference.child("location902").setValue(locationData);


        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1638);
        locationData.put("longitude", -8.5199);
        databaseReference.child("location903").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1213);
        locationData.put("longitude", -8.4335);
        databaseReference.child("location904").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1860);
        locationData.put("longitude", -8.4869);
        databaseReference.child("location905").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.2716);
        locationData.put("longitude", -8.4813);
        databaseReference.child("location906").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.4342);
        locationData.put("longitude", -8.4449);
        databaseReference.child("location907").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.4653);
        locationData.put("longitude", -8.2813);
        databaseReference.child("location908").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9303);
        locationData.put("longitude", -8.5229);
        databaseReference.child("location909").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0467);
        locationData.put("longitude", -8.0401);
        databaseReference.child("location910").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.5282);
        locationData.put("longitude", -8.1435);
        databaseReference.child("location911").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.6585);
        locationData.put("longitude", -8.0858);
        databaseReference.child("location912").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.6340);
        locationData.put("longitude", -8.1135);
        databaseReference.child("location913").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.6353);
        locationData.put("longitude", -8.3376);
        databaseReference.child("location914").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.9304);
        locationData.put("longitude", -7.9098);
        databaseReference.child("location915").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.0865);
        locationData.put("longitude", -8.2894);
        databaseReference.child("location916").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.6809);
        locationData.put("longitude", -8.0358);
        databaseReference.child("location917").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.7555);
        locationData.put("longitude", -7.9011);
        databaseReference.child("location918").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.1584);
        locationData.put("longitude", -7.8041);
        databaseReference.child("location919").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.0430);
        locationData.put("longitude", -7.3820);
        databaseReference.child("location920").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.0393);
        locationData.put("longitude", -7.4341);
        databaseReference.child("location921").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1916);
        locationData.put("longitude", -7.9907);
        databaseReference.child("location922").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1208);
        locationData.put("longitude", -7.5240);
        databaseReference.child("location923").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9374);
        locationData.put("longitude", -7.5272);
        databaseReference.child("location924").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9032);
        locationData.put("longitude", -7.4431);
        databaseReference.child("location925").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8309);
        locationData.put("longitude", -7.4375);
        databaseReference.child("location926").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7976);
        locationData.put("longitude", -7.2822);
        databaseReference.child("location927").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8648);
        locationData.put("longitude", -7.3242);
        databaseReference.child("location928").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1776);
        locationData.put("longitude", -7.2290);
        databaseReference.child("location929").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.2086);
        locationData.put("longitude", -7.1485);
        databaseReference.child("location930").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1117);
        locationData.put("longitude", -6.7327);
        databaseReference.child("location931").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1283);
        locationData.put("longitude", -6.7532);
        databaseReference.child("location932").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0498);
        locationData.put("longitude", -6.6977);
        databaseReference.child("location933").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9885);
        locationData.put("longitude", -6.7160);
        databaseReference.child("location934").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0400);
        locationData.put("longitude", -6.1823);
        databaseReference.child("location935").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9999);
        locationData.put("longitude", -6.1918);
        databaseReference.child("location936").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0351);
        locationData.put("longitude", -6.3837);
        databaseReference.child("location937").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9797);
        locationData.put("longitude", -6.3932);
        databaseReference.child("location938").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0188);
        locationData.put("longitude", -6.4447);
        databaseReference.child("location939").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9741);
        locationData.put("longitude", -6.4244);
        databaseReference.child("location940").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8397);
        locationData.put("longitude", -6.4147);
        databaseReference.child("location941").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9199);
        locationData.put("longitude", -6.6306);
        databaseReference.child("location942").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8877);
        locationData.put("longitude", -6.6023);
        databaseReference.child("location943").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8664);
        locationData.put("longitude", -6.5389);
        databaseReference.child("location944").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7186);
        locationData.put("longitude", -6.3481);
        databaseReference.child("location945").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7131);
        locationData.put("longitude", -6.3447);
        databaseReference.child("location946").setValue(locationData);


        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7697);
        locationData.put("longitude", -6.4005);
        databaseReference.child("location947").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8321);
        locationData.put("longitude", -6.3950);
        databaseReference.child("location948").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7218);
        locationData.put("longitude", -7.1988);
        databaseReference.child("location949").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7692);
        locationData.put("longitude", -7.1621);
        databaseReference.child("location950").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7242);
        locationData.put("longitude", -6.9940);
        databaseReference.child("location951").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6682);
        locationData.put("longitude", -6.9777);
        databaseReference.child("location952").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8490);
        locationData.put("longitude", -6.8205);
        databaseReference.child("location953").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7074);
        locationData.put("longitude", -6.6800);
        databaseReference.child("location954").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0821);
        locationData.put("longitude", -8.5634);
        databaseReference.child("location955").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6529);
        locationData.put("longitude", -6.6750);
        databaseReference.child("location956").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6021);
        locationData.put("longitude", -6.6377);
        databaseReference.child("location957").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6229);
        locationData.put("longitude", -6.4928);
        databaseReference.child("location958").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5172);
        locationData.put("longitude", -6.5451);
        databaseReference.child("location959").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4312);
        locationData.put("longitude", -6.5898);
        databaseReference.child("location960").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5331);
        locationData.put("longitude", -6.9700);
        databaseReference.child("location961").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6358);
        locationData.put("longitude", -6.8937);
        databaseReference.child("location962").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5545);
        locationData.put("longitude", -7.2389);
        databaseReference.child("location963").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6109);
        locationData.put("longitude", -7.0358);
        databaseReference.child("location964").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5284);
        locationData.put("longitude", -7.3072);
        databaseReference.child("location965").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4210);
        locationData.put("longitude", -7.2903);
        databaseReference.child("location966").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3898);
        locationData.put("longitude", -7.7643);
        databaseReference.child("location967").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3746);
        locationData.put("longitude", -7.4239);
        databaseReference.child("location968").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5861);
        locationData.put("longitude", -7.3718);
        databaseReference.child("location969").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5314);
        locationData.put("longitude", -7.3563);
        databaseReference.child("location970").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4270);
        locationData.put("longitude", -8.0185);
        databaseReference.child("location971").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4203);
        locationData.put("longitude", -7.9766);
        databaseReference.child("location972").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4107);
        locationData.put("longitude", -8.0159);
        databaseReference.child("location973").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0821);
        locationData.put("longitude", -8.5634);
        databaseReference.child("location974").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2598);
        locationData.put("longitude", -7.9675);
        databaseReference.child("location975").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1899);
        locationData.put("longitude", -7.9866);
        databaseReference.child("location976").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1742);
        locationData.put("longitude", -7.9446);
        databaseReference.child("location977").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1490);
        locationData.put("longitude", -7.9349);
        databaseReference.child("location978").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0949);
        locationData.put("longitude", -7.9086);
        databaseReference.child("location979").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2114);
        locationData.put("longitude", -7.8859);
        databaseReference.child("location980").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1924);
        locationData.put("longitude", -7.4952);
        databaseReference.child("location981").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1352);
        locationData.put("longitude", -7.4051);
        databaseReference.child("location982").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1100);
        locationData.put("longitude", -7.2529);
        databaseReference.child("location983").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9401);
        locationData.put("longitude", -7.6168);
        databaseReference.child("location984").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9177);
        locationData.put("longitude", -7.5465);
        databaseReference.child("location985").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0135);
        locationData.put("longitude", -7.2952);
        databaseReference.child("location986").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8902);
        locationData.put("longitude", -7.0276);
        databaseReference.child("location987").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0584);
        locationData.put("longitude", -7.2295);
        databaseReference.child("location988").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4449);
        locationData.put("longitude", -7.0605);
        databaseReference.child("location989").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3634);
        locationData.put("longitude", -7.0431);
        databaseReference.child("location990").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3525);
        locationData.put("longitude", -6.9621);
        databaseReference.child("location991").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9377);
        locationData.put("longitude", -6.8563);
        databaseReference.child("location992").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1321);
        locationData.put("longitude", -6.7416);
        databaseReference.child("location993").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1670);
        locationData.put("longitude", -6.7661);
        databaseReference.child("location994").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1573);
        locationData.put("longitude", -6.6321);
        databaseReference.child("location995").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2559);
        locationData.put("longitude", -6.5611);
        databaseReference.child("location996").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2138);
        locationData.put("longitude", -6.6650);
        databaseReference.child("location997").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2543);
        locationData.put("longitude", -6.6673);
        databaseReference.child("location998").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3247);
        locationData.put("longitude", -6.5997);
        databaseReference.child("location999").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3430);
        locationData.put("longitude", -6.5404);
        databaseReference.child("location1000").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3820);
        locationData.put("longitude", -6.5824);
        databaseReference.child("location1001").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4208);
        locationData.put("longitude", -6.7658);
        databaseReference.child("location1002").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0821);
        locationData.put("longitude", -8.5634);
        databaseReference.child("location1003").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0605);
        locationData.put("longitude", -6.6242);
        databaseReference.child("location1004").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2057);
        locationData.put("longitude", -6.4950);
        databaseReference.child("location1005").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1578);
        locationData.put("longitude", -6.3545);
        databaseReference.child("location1006").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2001);
        locationData.put("longitude", -6.1106);
        databaseReference.child("location1007").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1039);
        locationData.put("longitude", -6.1074);
        databaseReference.child("location1008").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1687);
        locationData.put("longitude", -6.1480);
        databaseReference.child("location1009").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0821);
        locationData.put("longitude", -8.5634);
        databaseReference.child("location1010").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0643);
        locationData.put("longitude", -6.2247);
        databaseReference.child("location1011").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8547);
        locationData.put("longitude", -6.3095);
        databaseReference.child("location1012").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8275);
        locationData.put("longitude", -6.3633);
        databaseReference.child("location1013").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8266);
        locationData.put("longitude", -6.5209);
        databaseReference.child("location1014").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5679);
        locationData.put("longitude", -6.3795);
        databaseReference.child("location1015").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5320);
        locationData.put("longitude", -6.2073);
        databaseReference.child("location1016").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4726);
        locationData.put("longitude", -6.2539);
        databaseReference.child("location1017").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4110);
        locationData.put("longitude", -6.1635);
        databaseReference.child("location1018").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4184);
        locationData.put("longitude", -6.1419);
        databaseReference.child("location1019").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3825);
        locationData.put("longitude", -6.1489);
        databaseReference.child("location1020").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4027);
        locationData.put("longitude", -6.2338);
        databaseReference.child("location1021").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4072);
        locationData.put("longitude", -6.2999);
        databaseReference.child("location1022").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4020);
        locationData.put("longitude", -6.2051);
        databaseReference.child("location1023").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3927);
        locationData.put("longitude", -6.3787);
        databaseReference.child("location1024").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3909);
        locationData.put("longitude", -6.4312);
        databaseReference.child("location1025").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3169);
        locationData.put("longitude", -6.3740);
        databaseReference.child("location1026").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3228);
        locationData.put("longitude", -6.4062);
        databaseReference.child("location1027").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3096);
        locationData.put("longitude", -6.4690);
        databaseReference.child("location1028").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2761);
        locationData.put("longitude", -6.5034);
        databaseReference.child("location1029").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2350);
        locationData.put("longitude", -6.4551);
        databaseReference.child("location1030").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2655);
        locationData.put("longitude", -6.2001);
        databaseReference.child("location1031").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2751);
        locationData.put("longitude", -6.1710);
        databaseReference.child("location1032").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3121);
        locationData.put("longitude", -6.2012);
        databaseReference.child("location1033").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3094);
        locationData.put("longitude", -6.3601);
        databaseReference.child("location1034").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3261);
        locationData.put("longitude", -6.2425);
        databaseReference.child("location1035").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3195);
        locationData.put("longitude", -6.3645);
        databaseReference.child("location1036").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3310);
        locationData.put("longitude", -6.2875);
        databaseReference.child("location1037").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3407);
        locationData.put("longitude", -6.3067);
        databaseReference.child("location1038").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3647);
        locationData.put("longitude", -6.2871);
        databaseReference.child("location1039").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3775);
        locationData.put("longitude", -6.2590);
        databaseReference.child("location1040").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8758);
        locationData.put("longitude", -8.5665);
        databaseReference.child("location1041").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.8758);
        locationData.put("longitude", -8.5054);
        databaseReference.child("location1042").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.9711);
        locationData.put("longitude", -7.8752);
        databaseReference.child("location1043").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1111);
        locationData.put("longitude", -8.9198);
        databaseReference.child("location1044").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9865);
        locationData.put("longitude", -8.8405);
        databaseReference.child("location1045").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2669);
        locationData.put("longitude", -8.4142);
        databaseReference.child("location1046").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6134);
        locationData.put("longitude", -8.7525);
        databaseReference.child("location1047").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6190);
        locationData.put("longitude", -8.7429);
        databaseReference.child("location1048").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1123);
        locationData.put("longitude", -10.0910);
        databaseReference.child("location1049").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1088);
        locationData.put("longitude", -9.5103);
        databaseReference.child("location1050").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9807);
        locationData.put("longitude", -9.1127);
        databaseReference.child("location1051").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6758);
        locationData.put("longitude", -7.9971);
        databaseReference.child("location1052").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.1041);
        locationData.put("longitude", -8.1488);
        databaseReference.child("location1053").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.1021);
        locationData.put("longitude", -7.7137);
        databaseReference.child("location1054").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.0725);
        locationData.put("longitude", -7.7121);
        databaseReference.child("location1055").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1218);
        locationData.put("longitude", -7.4816);
        databaseReference.child("location1056").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7786);
        locationData.put("longitude", -7.4758);
        databaseReference.child("location1057").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1266);
        locationData.put("longitude", -6.8562);
        databaseReference.child("location1058").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0786);
        locationData.put("longitude", -7.0894);
        databaseReference.child("location1059").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.1125);
        locationData.put("longitude", -7.0431);
        databaseReference.child("location1060").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9796);
        locationData.put("longitude", -7.2356);
        databaseReference.child("location1061").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.9634);
        locationData.put("longitude", -6.5504);
        databaseReference.child("location1062").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8723);
        locationData.put("longitude", -6.6077);
        databaseReference.child("location1063").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8184);
        locationData.put("longitude", -6.3574);
        databaseReference.child("location1064").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.8724);
        locationData.put("longitude", -6.7376);
        databaseReference.child("location1065").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5658);
        locationData.put("longitude", -6.4452);
        databaseReference.child("location1066").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4874);
        locationData.put("longitude", -6.3843);
        databaseReference.child("location1067").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5018);
        locationData.put("longitude", -6.8547);
        databaseReference.child("location1068").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.6298);
        locationData.put("longitude", -7.0551);
        databaseReference.child("location1069").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4713);
        locationData.put("longitude", -7.3575);
        databaseReference.child("location1070").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.5816);
        locationData.put("longitude", -7.5328);
        databaseReference.child("location1071").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.4300);
        locationData.put("longitude", -6.9684);
        databaseReference.child("location1072").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.3087);
        locationData.put("longitude", -6.9224);
        databaseReference.child("location1073").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2383);
        locationData.put("longitude", -6.8570);
        databaseReference.child("location1074").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1985);
        locationData.put("longitude", -6.9799);
        databaseReference.child("location1075").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1909);
        locationData.put("longitude", -6.9065);
        databaseReference.child("location1076").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2925);
        locationData.put("longitude", -6.6869);
        databaseReference.child("location1077").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.1602);
        locationData.put("longitude", -7.8162);
        databaseReference.child("location1078").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.2171);
        locationData.put("longitude", -7.4638);
        databaseReference.child("location1079").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0189);
        locationData.put("longitude", -7.4652);
        databaseReference.child("location1080").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0133);
        locationData.put("longitude", -6.1601);
        databaseReference.child("location1081").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3195);
        locationData.put("longitude", -7.1511);
        databaseReference.child("location1082").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5220);
        locationData.put("longitude", -7.1242);
        databaseReference.child("location1083").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6909);
        locationData.put("longitude", -6.9970);
        databaseReference.child("location1084").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7844);
        locationData.put("longitude", -6.8621);
        databaseReference.child("location1085").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8239);
        locationData.put("longitude", -6.8901);
        databaseReference.child("location1086").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9429);
        locationData.put("longitude", -8.0395);
        databaseReference.child("location1087").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4605);
        locationData.put("longitude", -8.0037);
        databaseReference.child("location1088").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5196);
        locationData.put("longitude", -7.8865);
        databaseReference.child("location1089").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5459);
        locationData.put("longitude", -8.0629);
        databaseReference.child("location1090").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.4691);
        locationData.put("longitude", -7.6914);
        databaseReference.child("location1091").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6412);
        locationData.put("longitude", -7.6096);
        databaseReference.child("location1092").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5674);
        locationData.put("longitude", -6.8752);
        databaseReference.child("location1093").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3458);
        locationData.put("longitude", -6.5149);
        databaseReference.child("location1094").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7209);
        locationData.put("longitude", -6.2461);
        databaseReference.child("location1095").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.6410);
        locationData.put("longitude", -6.3500);
        databaseReference.child("location1096").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.9861);
        locationData.put("longitude", -6.9378);
        databaseReference.child("location1097").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.5250);
        locationData.put("longitude", -6.7414);
        databaseReference.child("location1098").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.3273);
        locationData.put("longitude", -9.7382);
        databaseReference.child("location1099").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.0423);
        locationData.put("longitude", -6.3863);
        databaseReference.child("location1100").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0821);
        locationData.put("longitude", -8.5634);
        databaseReference.child("location1101").setValue(locationData);

        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.07241);
        locationData.put("longitude", -9.575299);
        databaseReference.child("location1102").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.081185);
        locationData.put("longitude", -9.247033);
        databaseReference.child("location1103").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.143582);
        locationData.put("longitude", -9.553727);
        databaseReference.child("location1104").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.442841);
        locationData.put("longitude", -9.410501);
        databaseReference.child("location1105").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.141959);
        locationData.put("longitude", -9.731576);
        databaseReference.child("location1106").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.377351);
        locationData.put("longitude", -9.306778);
        databaseReference.child("location1107").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.606726);
        locationData.put("longitude", -8.918287);
        databaseReference.child("location1108").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.641366);
        locationData.put("longitude", -8.681017);
        databaseReference.child("location1109").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.473904);
        locationData.put("longitude", -9.018279);
        databaseReference.child("location1110").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.545741);
        locationData.put("longitude", -8.839494);
        databaseReference.child("location1111").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.47042);
        locationData.put("longitude", -8.55051);
        databaseReference.child("location1112").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.411809);
        locationData.put("longitude", -8.685294);
        databaseReference.child("location1113").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.432932);
        locationData.put("longitude", -8.43204);
        databaseReference.child("location1114").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.486369);
        locationData.put("longitude", -8.443212);
        databaseReference.child("location1115").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.650341);
        locationData.put("longitude", -8.58751);
        databaseReference.child("location1116").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.661713);
        locationData.put("longitude", -8.622051);
        databaseReference.child("location1117").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.451994);
        locationData.put("longitude", -8.094339);
        databaseReference.child("location1118").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.357334);
        locationData.put("longitude", -7.742635);
        databaseReference.child("location1119").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.455514);
        locationData.put("longitude", -7.70622);
        databaseReference.child("location1120").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.351404);
        locationData.put("longitude", -7.38923);
        databaseReference.child("location1121").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.686348);
        locationData.put("longitude", -9.359332);
        databaseReference.child("location1122").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.738346);
        locationData.put("longitude", -9.174654);
        databaseReference.child("location1123").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.673223);
        locationData.put("longitude", -8.616052);
        databaseReference.child("location1124").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0821);
        locationData.put("longitude", -8.5634);
        databaseReference.child("location1125").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.731719);
        locationData.put("longitude", -8.550201);
        databaseReference.child("location1126").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.809103);
        locationData.put("longitude", -8.45262);
        databaseReference.child("location1127").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.110942);
        locationData.put("longitude", -8.800505);
        databaseReference.child("location1128").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.06436);
        locationData.put("longitude", -8.813556);
        databaseReference.child("location1129").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.213507);
        locationData.put("longitude", -8.686588);
        databaseReference.child("location1130").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.400632);
        locationData.put("longitude", -8.334653);
        databaseReference.child("location1131").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.494337);
        locationData.put("longitude", -8.473988);
        databaseReference.child("location1132").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.305396);
        locationData.put("longitude", -8.924806);
        databaseReference.child("location1133").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.271935);
        locationData.put("longitude", -8.917043);
        databaseReference.child("location1134").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.379722);
        locationData.put("longitude", -9.250379);
        databaseReference.child("location1135").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.958819);
        locationData.put("longitude", -8.079937);
        databaseReference.child("location1136").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.971586);
        locationData.put("longitude", -8.064324);
        databaseReference.child("location1137").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.012501);
        locationData.put("longitude", -6.711587);
        databaseReference.child("location1138").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.158738);
        locationData.put("longitude", -7.945096);
        databaseReference.child("location1139").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.326103);
        locationData.put("longitude", -7.821838);
        databaseReference.child("location1140").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.275302);
        locationData.put("longitude", -7.492992);
        databaseReference.child("location1141").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.32738);
        locationData.put("longitude", -7.081639);
        databaseReference.child("location1142").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.393817);
        locationData.put("longitude", -7.584746);
        databaseReference.child("location1143").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.196198);
        locationData.put("longitude", -7.053217);
        databaseReference.child("location1144").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.14779);
        locationData.put("longitude", -7.160774);
        databaseReference.child("location1145").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.097428);
        locationData.put("longitude", -7.229104);
        databaseReference.child("location1146").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.067374);
        locationData.put("longitude", -7.290371);
        databaseReference.child("location1147").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.119346);
        locationData.put("longitude", -7.457833);
        databaseReference.child("location1148").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 55.191872);
        locationData.put("longitude", -7.400996);
        databaseReference.child("location1149").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.978315);
        locationData.put("longitude", -7.726467);
        databaseReference.child("location1150").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.942058);
        locationData.put("longitude", -7.695959);
        databaseReference.child("location1151").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.951848);
        locationData.put("longitude", -7.711745);
        databaseReference.child("location1152").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.942891);
        locationData.put("longitude", -7.633093);
        databaseReference.child("location1153").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.887734);
        locationData.put("longitude", -7.556641);
        databaseReference.child("location1154").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.812374);
        locationData.put("longitude", -7.520506);
        databaseReference.child("location1155").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.474956);
        locationData.put("longitude", -8.311189);
        databaseReference.child("location1156").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.104573);
        locationData.put("longitude", -8.410283);
        databaseReference.child("location1157").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.31546);
        locationData.put("longitude", -8.480917);
        databaseReference.child("location1158").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.52448);
        locationData.put("longitude", -7.798424);
        databaseReference.child("location1159").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.525922);
        locationData.put("longitude", -7.340867);
        databaseReference.child("location1160").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.648618);
        locationData.put("longitude", -7.509206);
        databaseReference.child("location1161").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.839448);
        locationData.put("longitude", -6.809868);
        databaseReference.child("location1162").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.8334);
        locationData.put("longitude", -6.932744);
        databaseReference.child("location1163").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.792099);
        locationData.put("longitude", -6.946697);
        databaseReference.child("location1164").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.839887);
        locationData.put("longitude", -7.104505);
        databaseReference.child("location1165").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.836479);
        locationData.put("longitude", -7.086294);
        databaseReference.child("location1166").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.845762);
        locationData.put("longitude", -7.260542);
        databaseReference.child("location1167").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.836433);
        locationData.put("longitude", -7.349548);
        databaseReference.child("location1168").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.084921);
        locationData.put("longitude", -7.425062);
        databaseReference.child("location1169").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.95709);
        locationData.put("longitude", -7.367878);
        databaseReference.child("location1170").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.933608);
        locationData.put("longitude", -7.217771);
        databaseReference.child("location1171").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.940994);
        locationData.put("longitude", -6.831514);
        databaseReference.child("location1172").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.54288);
        locationData.put("longitude", -9.344223);
        databaseReference.child("location1173").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.545687);
        locationData.put("longitude", -9.265131);
        databaseReference.child("location1174").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.832867);
        locationData.put("longitude", -8.346192);
        databaseReference.child("location1175").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.83296);
        locationData.put("longitude", -8.393064);
        databaseReference.child("location1176").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.877321);
        locationData.put("longitude", -8.378789);
        databaseReference.child("location1177").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.897322);
        locationData.put("longitude", -8.281163);
        databaseReference.child("location1178").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.888699);
        locationData.put("longitude", -8.16568);
        databaseReference.child("location1179").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.906283);
        locationData.put("longitude", -8.270664);
        databaseReference.child("location1180").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.906584);
        locationData.put("longitude", -8.166553);
        databaseReference.child("location1181").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.142);
        locationData.put("longitude", -8.276801);
        databaseReference.child("location1182").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.182735);
        locationData.put("longitude", -8.670825);
        databaseReference.child("location1183").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.148378);
        locationData.put("longitude", -8.617459);
        databaseReference.child("location1184").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.129767);
        locationData.put("longitude", -8.764977);
        databaseReference.child("location1185").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.136267);
        locationData.put("longitude", -8.940663);
        databaseReference.child("location1186").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.362977);
        locationData.put("longitude", -8.658304);
        databaseReference.child("location1187").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.880506);
        locationData.put("longitude", -8.688656);
        databaseReference.child("location1188").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.947483);
        locationData.put("longitude", -8.567356);
        databaseReference.child("location1189").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.942025);
        locationData.put("longitude", -8.397515);
        databaseReference.child("location1190").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.723597);
        locationData.put("longitude", -9.080298);
        databaseReference.child("location1191").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.61281);
        locationData.put("longitude", -6.188607);
        databaseReference.child("location1192").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.597129);
        locationData.put("longitude", -6.16165);
        databaseReference.child("location1193").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.416024);
        locationData.put("longitude", -6.178003);
        databaseReference.child("location1194").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.427707);
        locationData.put("longitude", -6.229529);
        databaseReference.child("location1195").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.401188);
        locationData.put("longitude", -6.400307);
        databaseReference.child("location1196").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.398081);
        locationData.put("longitude", -6.332871);
        databaseReference.child("location1197").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.367585);
        locationData.put("longitude", -6.272903);
        databaseReference.child("location1198").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.333802);
        locationData.put("longitude", -6.244927);
        databaseReference.child("location1199").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.326326);
        locationData.put("longitude", -6.278169);
        databaseReference.child("location1200").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.285648);
        locationData.put("longitude", -6.365907);
        databaseReference.child("location1201").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.338384);
        locationData.put("longitude", -6.34787);
        databaseReference.child("location1202").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.402639);
        locationData.put("longitude", -6.39721);
        databaseReference.child("location1203").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.350146);
        locationData.put("longitude", -6.233991);
        databaseReference.child("location1204").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.414568);
        locationData.put("longitude", -6.832474);
        databaseReference.child("location1205").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.367041);
        locationData.put("longitude", -6.566376);
        databaseReference.child("location1206").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.303392);
        locationData.put("longitude", -6.559402);
        databaseReference.child("location1207").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.322771);
        locationData.put("longitude", -6.612458);
        databaseReference.child("location1208").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.293456);
        locationData.put("longitude", -6.687324);
        databaseReference.child("location1209").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.285907);
        locationData.put("longitude", -6.811205);
        databaseReference.child("location1210").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.259783);
        locationData.put("longitude", -6.848525);
        databaseReference.child("location1211").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.21582);
        locationData.put("longitude", -6.665682);
        databaseReference.child("location1212").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.162834);
        locationData.put("longitude", -6.824623);
        databaseReference.child("location1213").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.157122);
        locationData.put("longitude", -6.864624);
        databaseReference.child("location1214").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.018353);
        locationData.put("longitude", -6.925257);
        databaseReference.child("location1215").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.87622);
        locationData.put("longitude", -6.875637);
        databaseReference.child("location1216").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.011994);
        locationData.put("longitude", -6.80241);
        databaseReference.child("location1217").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.291254);
        locationData.put("longitude", -7.057455);
        databaseReference.child("location1218").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.611567);
        locationData.put("longitude", -7.29019);
        databaseReference.child("location1219").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.654911);
        locationData.put("longitude", -7.246443);
        databaseReference.child("location1220").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.005745);
        locationData.put("longitude", -7.304866);
        databaseReference.child("location1221").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.916982);
        locationData.put("longitude", -7.346759);
        databaseReference.child("location1222").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.122958);
        locationData.put("longitude", -7.330403);
        databaseReference.child("location1223").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.644792);
        locationData.put("longitude", -7.822328);
        databaseReference.child("location1224").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.550481);
        locationData.put("longitude", -7.717456);
        databaseReference.child("location1225").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.799751);
        locationData.put("longitude", -7.789701);
        databaseReference.child("location1226").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.7685);
        locationData.put("longitude", -6.491838);
        databaseReference.child("location1227").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.836661);
        locationData.put("longitude", -6.537868);
        databaseReference.child("location1228").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.949217);
        locationData.put("longitude", -6.459843);
        databaseReference.child("location1229").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.973971);
        locationData.put("longitude", -6.6302);
        databaseReference.child("location1230").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.021273);
        locationData.put("longitude", -6.460457);
        databaseReference.child("location1231").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.031765);
        locationData.put("longitude", -6.526208);
        databaseReference.child("location1232").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.052977);
        locationData.put("longitude", -6.194967);
        databaseReference.child("location1233").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.715856);
        locationData.put("longitude", -6.465648);
        databaseReference.child("location1234").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.536095);
        locationData.put("longitude", -9.212457);
        databaseReference.child("location1235").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.77698);
        locationData.put("longitude", -9.307728);
        databaseReference.child("location1236").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.848311);
        locationData.put("longitude", -9.309969);
        databaseReference.child("location1237").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.809557);
        locationData.put("longitude", -9.427227);
        databaseReference.child("location1238").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.202023);
        locationData.put("longitude", -9.91744);
        databaseReference.child("location1239").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.119801);
        locationData.put("longitude", -9.160278);
        databaseReference.child("location1240").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.769693);
        locationData.put("longitude", -7.162569);
        databaseReference.child("location1241").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.53004);
        locationData.put("longitude", -6.688896);
        databaseReference.child("location1242").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.455111);
        locationData.put("longitude", -6.347932);
        databaseReference.child("location1243").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.602821);
        locationData.put("longitude", -6.696698);
        databaseReference.child("location1244").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.662137);
        locationData.put("longitude", -6.679914);
        databaseReference.child("location1245").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.658654);
        locationData.put("longitude", -6.679661);
        databaseReference.child("location1246").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.728058);
        locationData.put("longitude", -6.876734);
        databaseReference.child("location1247").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.774721);
        locationData.put("longitude", -6.79933);
        databaseReference.child("location1248").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.803275);
        locationData.put("longitude", -6.736924);
        databaseReference.child("location1249").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.863573);
        locationData.put("longitude", -6.79154);
        databaseReference.child("location1250").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.706804);
        locationData.put("longitude", -6.325112);
        databaseReference.child("location1251").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.634758);
        locationData.put("longitude", -6.219769);
        databaseReference.child("location1252").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.766158);
        locationData.put("longitude", -8.057926);
        databaseReference.child("location1253").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.657541);
        locationData.put("longitude", -8.091825);
        databaseReference.child("location1254").setValue(locationData);
        //Here
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.360493);
        locationData.put("longitude", -8.069541);
        databaseReference.child("location1255").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.121996);
        locationData.put("longitude", -7.564045);
        databaseReference.child("location1256").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.176612);
        locationData.put("longitude", -7.03421);
        databaseReference.child("location1257").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.236606);
        locationData.put("longitude", -7.060264);
        databaseReference.child("location1258").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.259931);
        locationData.put("longitude", -7.106033);
        databaseReference.child("location1259").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.258473);
        locationData.put("longitude", -7.1294);
        databaseReference.child("location1260").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.266643);
        locationData.put("longitude", -7.101945);
        databaseReference.child("location1261").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.235289);
        locationData.put("longitude", -7.104808);
        databaseReference.child("location1262").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.70802);
        locationData.put("longitude", -6.262564);
        databaseReference.child("location1263").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.64259);
        locationData.put("longitude", -6.23254);
        databaseReference.child("location1264").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.597023);
        locationData.put("longitude", -6.583901);
        databaseReference.child("location1265").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.459323);
        locationData.put("longitude", -6.400516);
        databaseReference.child("location1266").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.51461);
        locationData.put("longitude", -6.367746);
        databaseReference.child("location1267").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0821);
        locationData.put("longitude", -8.5634);
        databaseReference.child("location1268").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.502993);
        locationData.put("longitude", -6.572478);
        databaseReference.child("location1269").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.343963);
        locationData.put("longitude", -6.467352);
        databaseReference.child("location1270").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.32694);
        locationData.put("longitude", -6.484538);
        databaseReference.child("location1271").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.324107);
        locationData.put("longitude", -6.492645);
        databaseReference.child("location1272").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.295834);
        locationData.put("longitude", -6.571467);
        databaseReference.child("location1273").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.142473);
        locationData.put("longitude", -6.559817);
        databaseReference.child("location1274").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.7875);
        locationData.put("longitude", -6.167709);
        databaseReference.child("location1275").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.787356);
        locationData.put("longitude", -6.167776);
        databaseReference.child("location1276").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.903176);
        locationData.put("longitude", -6.101866);
        databaseReference.child("location1277").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.084851);
        locationData.put("longitude", -6.067542);
        databaseReference.child("location1278").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.185273);
        locationData.put("longitude", -6.520423);
        databaseReference.child("location1279").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.089947);
        locationData.put("longitude", -7.907537);
        databaseReference.child("location1280").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.38515);
        locationData.put("longitude", -6.901434);
        databaseReference.child("location1281").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.433554);
        locationData.put("longitude", -6.774047);
        databaseReference.child("location1282").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.439968);
        locationData.put("longitude", -6.5389);
        databaseReference.child("location1283").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.573529);
        locationData.put("longitude", -6.518054);
        databaseReference.child("location1284").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.628224);
        locationData.put("longitude", -6.623768);
        databaseReference.child("location1285").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.345138);
        locationData.put("longitude", -6.593835);
        databaseReference.child("location1286").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.012076);
        locationData.put("longitude", -6.403056);
        databaseReference.child("location1287").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.238884);
        locationData.put("longitude", -7.176938);
        databaseReference.child("location1288").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.351793);
        locationData.put("longitude", -7.386829);
        databaseReference.child("location1289").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.804335);
        locationData.put("longitude", -7.753416);
        databaseReference.child("location1290").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.802348);
        locationData.put("longitude", -7.80508);
        databaseReference.child("location1291").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.997402);
        locationData.put("longitude", -6.898094);
        databaseReference.child("location1292").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.275351);
        locationData.put("longitude", -8.984867);
        databaseReference.child("location1293").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.655472);
        locationData.put("longitude", -6.687201);
        databaseReference.child("location1294").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.521953);
        locationData.put("longitude", -6.147831);
        databaseReference.child("location1295").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.485324);
        locationData.put("longitude", -6.150638);
        databaseReference.child("location1296").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.389558);
        locationData.put("longitude", -6.197874);
        databaseReference.child("location1297").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.359736);
        locationData.put("longitude", -6.200128);
        databaseReference.child("location1298").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.390784);
        locationData.put("longitude", -6.319665);
        databaseReference.child("location1299").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.364481);
        locationData.put("longitude", -6.229493);
        databaseReference.child("location1300").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.371854);
        locationData.put("longitude", -6.331091);
        databaseReference.child("location1301").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.360746);
        locationData.put("longitude", -6.272876);
        databaseReference.child("location1302").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.354469);
        locationData.put("longitude", -6.273307);
        databaseReference.child("location1303").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.349935);
        locationData.put("longitude", -6.267989);
        databaseReference.child("location1304").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.348989);
        locationData.put("longitude", -6.251547);
        databaseReference.child("location1305").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.348965);
        locationData.put("longitude", -6.251631);
        databaseReference.child("location1306").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.346875);
        locationData.put("longitude", -6.290776);
        databaseReference.child("location1307").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.323302);
        locationData.put("longitude", -6.352401);
        databaseReference.child("location1308").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.322829);
        locationData.put("longitude", -6.279177);
        databaseReference.child("location1309").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.316961);
        locationData.put("longitude", -6.326202);
        databaseReference.child("location1310").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.29159);
        locationData.put("longitude", -6.357379);
        databaseReference.child("location1311").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.291007);
        locationData.put("longitude", -6.404592);
        databaseReference.child("location1312").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.284252);
        locationData.put("longitude", -6.378243);
        databaseReference.child("location1313").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.856558);
        locationData.put("longitude", -9.014445);
        databaseReference.child("location1314").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.673236);
        locationData.put("longitude", -8.640304);
        databaseReference.child("location1315").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.673133);
        locationData.put("longitude", -8.517);
        databaseReference.child("location1316").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 52.63478);
        locationData.put("longitude", -7.251674);
        databaseReference.child("location1317").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.88613);
        locationData.put("longitude", -8.609543);
        databaseReference.child("location1318").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.903266);
        locationData.put("longitude", -8.425794);
        databaseReference.child("location1319").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.899639);
        locationData.put("longitude", -8.465884);
        databaseReference.child("location1320").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.864755);
        locationData.put("longitude", -8.439695);
        databaseReference.child("location1321").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 54.0821);
        locationData.put("longitude", -8.5634);
        databaseReference.child("location1322").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 51.906869);
        locationData.put("longitude", -8.496283);
        databaseReference.child("location1323").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.905504);
        locationData.put("longitude", -9.750139);
        databaseReference.child("location1324").setValue(locationData);
        // Add the twelfth location
        locationData.put("Category", "Speed Van");
        locationData.put("latitude", 53.896012);
        locationData.put("longitude", -9.154622);
        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);
//        // Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);// Add the twelfth location
//        locationData.put("Category", "Speed Van");
//        locationData.put("latitude", 53.896012);
//        locationData.put("longitude", -9.154622);
//        databaseReference.child("location1325").setValue(locationData);



















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