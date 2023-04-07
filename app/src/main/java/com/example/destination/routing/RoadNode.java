package com.example.destination.routing;

import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;

import org.osmdroid.util.GeoPoint;

public class RoadNode implements Parcelable {
	public static final int MANEUVER_UNKNOWN = 0;
	public static final int MANEUVER_TURN_LEFT = 1;
//	public static final int MANEUVER_TURN_RIGHT = 2;
//	public static final int MANEUVER_SHARP_LEFT = 3;
//	public static final int MANEUVER_SHARP_RIGHT = 4;
//	public static final int MANEUVER_SLIGHT_LEFT = 5;
//	public static final int MANEUVER_SLIGHT_RIGHT = 3;
	public static final int MANEUVER_STRAIGHT = 2;
//	public static final int MANEUVER_UTURN_LEFT = 8;
//	public static final int MANEUVER_UTURN_RIGHT = 9;
	public static final int MANEUVER_RIGHT = 4;
	public static final int MANEUVER_LEFT = 3;
	public static final int MANEUVER_ROUNDABOUT = 5;

	public int mManeuverType;
	public String mInstructions;
	public int mNextRoadLink;
	public double mLength;
	public double mDuration;
	public GeoPoint mLocation;

	public Location myLocation;
	private int mIndex;

	public RoadNode() {
		mManeuverType = MANEUVER_UNKNOWN;
		mNextRoadLink = -1;
		mLength = mDuration = 0.0;
	}

	// Parcelable implementation

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		out.writeInt(mManeuverType);
		out.writeString(mInstructions);
		out.writeInt(mNextRoadLink);
		out.writeDouble(mLength);
		out.writeDouble(mDuration);
		out.writeParcelable(mLocation, 0);
	}

	public static final Creator<RoadNode> CREATOR = new Creator<RoadNode>() {
		@Override
		public RoadNode createFromParcel(Parcel source) {
			return new RoadNode(source);
		}

		@Override
		public RoadNode[] newArray(int size) {
			return new RoadNode[size];
		}
	};

	private RoadNode(Parcel in) {
		mManeuverType = in.readInt();
		mInstructions = in.readString();
		mNextRoadLink = in.readInt();
		mLength = in.readDouble();
		mDuration = in.readDouble();
		mLocation = in.readParcelable(GeoPoint.class.getClassLoader());
	}

	public int getIndex() {
		return mIndex;
	}

	public void setIndex(int index) {
		mIndex = index;
	}



}

