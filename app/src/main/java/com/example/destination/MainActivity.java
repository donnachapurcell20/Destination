package com.example.destination;



import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;


public class MainActivity extends AppCompatActivity
{


    @Override public void onCreate(Bundle savedInstanceState)
    {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);


        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));

        setContentView(R.layout.activity_main);
        MapView map = (MapView) findViewById(R.id.mapView);
        map.setMultiTouchControls(true);

        GeoPoint startPoint = new GeoPoint(48.13, -1.63);
        IMapController mapController = map.getController();
        mapController.setZoom(9);
        mapController.setCenter(startPoint);

        //Marker Overlay
        Marker startMarker = new Marker(map);
        startMarker.setPosition(startPoint);
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        startMarker.setTitle("Start point");

//        map.invalidate();

        //Road Manager
        RoadManager roadManager = new OSRMRoadManager(this, "Donnacha/1.0");

        //Setting up start and end point
        ArrayList<GeoPoint> waypoints = new ArrayList<GeoPoint>();
        waypoints.add(startPoint);
        GeoPoint endPoint = new GeoPoint(48.4, -1.9);
        waypoints.add(endPoint);

        //Retrieve the road between these points
        Road road = roadManager.getRoad(waypoints);
        if (road.mStatus != Road.STATUS_OK)
            Toast.makeText(this, "Error when loading the road - status=" + road.mStatus, Toast.LENGTH_SHORT).show();


        //Build a Polyline with the route shape
        Polyline roadOverlay = RoadManager.buildRoadOverlay(road);

        //Add this Polyline to the overlays of the map
        map.getOverlays().add(roadOverlay);

        //Showing the Route steps on the map
        Drawable nodeIcon = getResources().getDrawable(R.drawable.img);
        for (int i=0; i<road.mNodes.size(); i++){
            RoadNode node = road.mNodes.get(i);
            Marker nodeMarker = new Marker(map);
            nodeMarker.setPosition(node.mLocation);
            nodeMarker.setIcon(nodeIcon);
            nodeMarker.setTitle("Step "+i);
            map.getOverlays().add(nodeMarker);

            //Filling the bubbles
            nodeMarker.setSnippet(node.mInstructions);

            nodeMarker.setSubDescription(Road.getLengthDurationText(this, node.mLength, node.mDuration));

            Drawable icon = getResources().getDrawable(R.drawable.ic_continue);
            nodeMarker.setImage(icon);
        }
        //Refresh the map
        map.invalidate();
        }
}