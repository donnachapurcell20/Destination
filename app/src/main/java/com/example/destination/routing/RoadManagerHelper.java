package com.example.destination.routing;

import android.location.Location;

public class RoadManagerHelper {
    public static int getIndexNearestTo(Road road, Location location) {
        double distance = Double.MAX_VALUE;
        int index = -1;
        for (int i = 0; i < road.mNodes.size(); i++) {
            RoadNode node = road.mNodes.get(i);
            Location nodeLocation = new Location("");
            nodeLocation.setLatitude(node.mLocation.getLatitude());
            nodeLocation.setLongitude(node.mLocation.getLongitude());
            double d = location.distanceTo(nodeLocation);
            if (d < distance) {
                distance = d;
                index = i;
            }
        }
        return index;
    }

}

