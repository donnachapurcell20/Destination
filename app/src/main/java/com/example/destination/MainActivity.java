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