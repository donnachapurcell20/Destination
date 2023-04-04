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