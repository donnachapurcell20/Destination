package com.example.destination.module;

import java.util.List;


//Interface that defines two methods onDrectionFinderStart and onDirectionFinderSuccess
public interface DirectionFinderListener {
    // This method is called when the direction finding process starts.
    void onDirectionFinderStart();
    //Method called when the direction finding process is successfully completed.
    //Takes a list of route objects which represents the direction between two point
    void onDirectionFinderSuccess(List<Route> route);
}
