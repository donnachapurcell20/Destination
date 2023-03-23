package com.example.destination.routing;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.Locale;

//This is a generic class to get a route between the starting point and end point and goes through a list of waypoints.
public abstract class RoadManager {

  protected String mOptions;

	//This for finding the road, if there is an error the road is set to an error, and shape just has straight lines
	//between the waypoints.
	public abstract Road getRoad(ArrayList<GeoPoint> waypoints);

	//An array that will contain more entries for alternate route.
	//If there is an error it will return only 1 road.
	public abstract Road[] getRoads(ArrayList<GeoPoint> waypoints);

	public RoadManager() {
		mOptions = "";
	}

	//Adds an option that will be used in the route request part mainly used for routetypes
	public void addRequestOption(String requestOption){
		mOptions += "&" + requestOption;
	}

	//This method returns the GeoPoint as a string and then formats the longitude and latitude
	protected String geoPointAsString(GeoPoint p){
		Locale l = null;
		return String.format(l, "%.10f,%.10f", p.getLatitude(), p.getLongitude());
	}

	//This method returns the GeoPoint as a string and then formats the longitude and latitude
	protected String geoPointAsLonLatString(GeoPoint p){
		Locale l = null;
		return String.format(l, "%.10f,%.10f", p.getLongitude(), p.getLatitude());
	}
	//This build and returns a polyline also give the roadoverlay colour and width the width is in pixels
	public static Polyline buildRoadOverlay(Road road, int color, float width){
		Polyline roadOverlay = new Polyline();
		roadOverlay.setColor(color);
		roadOverlay.setWidth(width);
		if (road != null) {
			ArrayList<GeoPoint> polyline = road.mRouteHigh;
			roadOverlay.setPoints(polyline);
		}
		return roadOverlay;
	}

	//This builds an overlay for th road shape with a default style
	public static Polyline buildRoadOverlay(Road road){
		//Returns the route shape overlay
		return buildRoadOverlay(road, 0x800000FF, 5.0f);
	}

}
