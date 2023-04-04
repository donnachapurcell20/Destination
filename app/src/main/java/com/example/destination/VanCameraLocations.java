package com.example.destination;

public class VanCameraLocations
{
    private String category;
    private double latitude;
    private double longitude;


    private String[] options = {"Speed Van", "Speed Camera", "Construction", "Accident", "Traffic"};

    public String[] getOptions() {
        return options;
    }

    public void setOptions(String[] options) {
        this.options = options;
    }



    public VanCameraLocations(String category, double latitude, double longitude)
    {

    }

    public VanCameraLocations(double latitude, double longitude, String category)
    {
        this.latitude = latitude;
        this.longitude = longitude;
        this.category = category;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String name) {
        this.category = name;
    }
}
