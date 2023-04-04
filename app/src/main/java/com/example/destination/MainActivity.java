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