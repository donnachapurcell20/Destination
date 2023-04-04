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