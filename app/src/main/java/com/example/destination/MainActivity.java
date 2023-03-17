package com.example.destination;


import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.RetryPolicy;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.googlemaps.module.DirectionFinder;
import com.example.googlemaps.module.DirectionFinderListener;
import com.example.googlemaps.module.Route;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends FragmentActivity implements OnMapReadyCallback, DirectionFinderListener, TextView.OnEditorActionListener {
    private GoogleMap googleMap;
    private EditText etOrigin;
    private EditText etDestination;
    private List<Marker> originMarkers = new ArrayList<>();
    private List<Marker> destinationMarkers = new ArrayList<>();
    private List<Polyline> polylinePaths = new ArrayList<>();
    private ProgressDialog progressDialog;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.fragmentMap);

        // Show soft keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);


        // Hide soft keyboard
//        imm.hideSoftInputFromWindow(etOrigin.getWindowToken(), 0);

//        etOrigin.setOnFocusChangeListener((v, hasFocus) -> {
//            // handle focus change event
//        });

//        etDestination.setOnFocusChangeListener((v, hasFocus) -> {
//            // handle focus change event
//        });


        // Check if input connection is active before performing operations on it
//        if (etOrigin.hasFocus() && etOrigin.isEnabled() && etOrigin.isFocused())
//        {
//            InputConnection inputConnection = etOrigin.onCreateInputConnection(new EditorInfo());
//            if (inputConnection != null) {
//                // perform operations on input connection
//                inputConnection.commitText("This is for the starting location", 1);
//            }
//        }

        // Check if input connection is active before performing operations on it
//        if (etDestination.hasFocus() && etDestination.isEnabled() && etDestination.isFocused())
//        {
//            InputConnection inputConnection = etDestination.onCreateInputConnection(new EditorInfo());
//            if (inputConnection != null) {
//                // perform operations on input connection
//                inputConnection.commitText("This is for the ending location", 1);
//            }
//        }






//        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.fragmentMap);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        Button btnFindPath = (Button) findViewById(R.id.btnFindPath);
        etOrigin = (EditText) findViewById(R.id.etOrigin);
        etOrigin.setOnEditorActionListener(this);

        etDestination = (EditText) findViewById(R.id.etDestination);
        etDestination.setOnEditorActionListener(this);

        // Add a TextWatcher to listen for changes to the text in the EditText
        etOrigin.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Do nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Call finishComposingText() if the InputConnection is active
                if (etOrigin.hasWindowFocus()) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        InputConnection ic = etOrigin.onCreateInputConnection(new EditorInfo());
                        if (ic != null) {
                            ic.finishComposingText();
                        }
                    }
                }
            }
        });

        // Add a TextWatcher to listen for changes to the text in the EditText
        etDestination.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Do nothing
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Call finishComposingText() if the InputConnection is active
                if (etDestination.hasWindowFocus()) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        InputConnection ic = etDestination.onCreateInputConnection(new EditorInfo());
                        if (ic != null) {
                            ic.finishComposingText();
                        }
                    }
                }
            }
        });



        btnFindPath.setOnClickListener(v -> sendRequest());
    }

    private void sendRequest() {
        String origin = etOrigin.getText().toString();
        String destination = etDestination.getText().toString();
        if (origin.isEmpty()) {
            Toast.makeText(this, "Please enter origin address!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (destination.isEmpty()) {
            Toast.makeText(this, "Please enter destination address!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            new DirectionFinder(this, origin, destination).execute();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onMapReady(GoogleMap googleMap)
    {
        this.googleMap = googleMap;
        directions();


        LatLng birr = new LatLng(53.09849397637937, -7.909688234800787);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(birr, 10));
        originMarkers.add(googleMap.addMarker(new MarkerOptions().title("Birr").position(birr)));
        //googleMap.addMarker(new MarkerOptions().position(birr).title("Marker in Ireland").icon(BitmapDescriptorFactory.fromResource(R.drawable.default_location)));

        LatLng dhag = new LatLng(53.424160555295806, -7.940775315285082);
        googleMap.addPolyline(new PolylineOptions().add(birr, new LatLng(53.424160555295806, -7.940775315285082), new LatLng(53.424160555295806, -7.940775315285082) ,dhag).width(10).color(Color.RED));



        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        googleMap.setMyLocationEnabled(true);
    }




    private void directions()
    {
        EditText startEditText = findViewById(R.id.etOrigin);
        EditText endEditText = findViewById(R.id.etDestination);
        String startLocation = startEditText.getText().toString();
        String endLocation = endEditText.getText().toString();


        RequestQueue requestQueue = Volley.newRequestQueue(this);

        String apiKey = "AIzaSyAsmV0r2sWySq1jtO_gzAOVebfy79zIFHA";

        String url = "http://maps.googleapis.com/maps/api/directions/json"+
                "origin=" + startLocation +
                "&destination=" + endLocation +
                "&key=" + apiKey;
                //.buildUpon().appendQueryParameter("destination", "53.42354680691251, -7.9406036539090845").appendQueryParameter("origin", "53.098081684128324, -7.909816980832775").appendQueryParameter("mode", "driving").appendQueryParameter("key", "AIzaSyAsmV0r2sWySq1jtO_gzAOVebfy79zIFHA").toString();
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null, response -> {
            try
            {
                String status = response.getString("status");
                if (status.equals("OK"))
                {
                    JSONArray routes = response.getJSONArray("routes");

                    ArrayList<LatLng> points;
                    PolylineOptions polylineOptions = null;

                    for (int i = 0; i < routes.length(); i++)
                    {
                        points = new ArrayList<>();
                        polylineOptions = new PolylineOptions();
                        JSONArray legs = routes.getJSONObject(i).getJSONArray("legs");

                        for (int j = 0; j < legs.length(); j++)
                        {
                            JSONArray steps = legs.getJSONObject(j).getJSONArray("steps");


                            for (int k = 0; k < steps.length(); k++)
                            {
                                String polyline = steps.getJSONObject(k).getJSONObject("polyline").getString("points");
                                List<LatLng> list = decodedPolyline(polyline);

                                for (int l = 0; l < list.size(); l++)
                                {
                                    LatLng position = new LatLng((list.get(l)).latitude, (list.get(l)).longitude);
                                    points.add(position);
                                }
                            }
                        }
                        polylineOptions.addAll(points);
                        polylineOptions.width(10);
                        polylineOptions.color(ContextCompat.getColor(MainActivity.this, R.color.purple_500));
                        polylineOptions.geodesic(true);
                    }

                    if (polylineOptions != null) {
                        googleMap.addPolyline(polylineOptions);
                    }
                    googleMap.addMarker(new MarkerOptions().position(new LatLng(53.098081684128324, -7.909816980832775)).title("Marker 1"));
                    googleMap.addMarker(new MarkerOptions().position(new LatLng(53.42354680691251, -7.9406036539090845)).title("Marker 2"));


                    LatLngBounds bounds = new LatLngBounds.Builder().include(new LatLng(53.098081684128324, -7.909816980832775)).include(new LatLng(53.42354680691251, -7.9406036539090845)).build();
                    Point point = new Point();
                    getWindowManager().getDefaultDisplay().getSize(point);
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, point.x, 150, 30));



                }
            }catch (JSONException e)
            {
                e.printStackTrace();
            }

        }, error -> {

        });
        RetryPolicy retryPolicy = new DefaultRetryPolicy(30000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
        jsonObjectRequest.setRetryPolicy(retryPolicy);
        requestQueue.add(jsonObjectRequest);


    }
    private List<LatLng> decodedPolyline(String encoded)
    {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;
        while (index < len)
        {
            int b, shift = 0, result = 0;

            do
            {
                b = encoded.charAt(index ++) - 63;
                result |= (b & 0x1f) << shift;
                shift+= 5;
            }while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index ++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            }while (b > 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >>1) : (result >> 1));
            lng += dlng;
            LatLng p = new LatLng((( (double) lat / 1E5)), (((double) lng / 1E5)));

            poly.add(p);

        }
        return poly;
    }


    @Override
    public void onDirectionFinderStart() {

        progressDialog = ProgressDialog.show(this, "Please wait.",
                "Finding direction..!", true);

        clearMap();
        if (originMarkers != null) {
            for (Marker marker : originMarkers) {
                marker.remove();
            }
        }

        if (destinationMarkers != null) {
            for (Marker marker : destinationMarkers) {
                marker.remove();
            }
        }

        if (polylinePaths != null) {
            for (Polyline polyline:polylinePaths ) {
                polyline.remove();
            }
        }
    }

    @Override
    public void onDirectionFinderSuccess(List<Route> routes)
    {
        progressDialog.dismiss();
        polylinePaths = new ArrayList<>();
        originMarkers = new ArrayList<>();
        destinationMarkers = new ArrayList<>();

        for (Route route : routes) {
//            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(route.startLocation, 16));
//            ((TextView) findViewById(R.id.tvDuration)).setText(route.duration.text);
//            ((TextView) findViewById(R.id.tvDistance)).setText(route.distance.text);

            // Add markers for the origin and destination
            originMarkers.add(googleMap.addMarker(new MarkerOptions()
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.starting_pin))
                    .title(route.startAddress)
                    .position(route.startLocation)));
            destinationMarkers.add(googleMap.addMarker(new MarkerOptions()
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ending_pin))
                    .title(route.endAddress)
                    .position(route.endLocation)));


            // Add the polyline for the route
            PolylineOptions polylineOptions = new PolylineOptions().
                    geodesic(true).
                    color(Color.BLUE).
                    width(10);

            for (int i = 0; i < route.points.size(); i++)
                polylineOptions.add(route.points.get(i));

            polylinePaths.add(googleMap.addPolyline(polylineOptions));
        }
    }

    private void clearMap() {
        for (Marker marker : originMarkers) {
            marker.remove();
        }
        originMarkers.clear();

        for (Marker marker : destinationMarkers) {
            marker.remove();
        }
        destinationMarkers.clear();

        for (Polyline polyline : polylinePaths) {
            polyline.remove();
        }
        polylinePaths.clear();
    }




    @Override
    public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent)
    {
        if (i == EditorInfo.IME_ACTION_DONE || i == KeyEvent.KEYCODE_ENTER)
        {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
            directions();
            return true;
        }
        return false;
    }
}