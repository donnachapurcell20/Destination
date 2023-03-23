package com.example.destination;

public class VanCameraLocations
{
    private double latitude;
    private double longitude;
    private String name;

    private String[] options = {"Speed Van", "Speed Camera", "Construction", "Accident", "Traffic"};

    public String[] getOptions() {
        return options;
    }

    public void setOptions(String[] options) {
        this.options = options;
    }



    public VanCameraLocations()
    {

    }

    public VanCameraLocations(double latitude, double longitude, String name)
    {
        this.latitude = latitude;
        this.longitude = longitude;
        this.name = name;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
