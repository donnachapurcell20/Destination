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