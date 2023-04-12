package com.example.destination.module;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

//public class named Route
public class Route
{
    //defining different classes all public fields.
    public Distance distance;
    public Duration duration;
    public String endAddress;
    public LatLng endLocation;
    public String startAddress;
    public LatLng startLocation;

    public List<LatLng> points;
}
