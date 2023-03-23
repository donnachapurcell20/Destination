package com.example.destination.routing;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;


//import com.example.destination.utils.BonusPackHelper;

import java.util.ArrayList;


//Road leg is the portion of the road between 2 points
public class RoadLeg implements Parcelable {
	//In KM and in seconds
	public double mLength; 

	public double mDuration; 

	//Node thats at the start of the leg which is the index in the nodes array
	public int mStartNodeIndex;
	//End node

	public int mEndNodeIndex; 
	
	public RoadLeg(){
		mLength = mDuration = 0.0;
		mStartNodeIndex = mEndNodeIndex = 0;
	}
	
	public RoadLeg(int startNodeIndex, int endNodeIndex,
                   ArrayList<RoadNode> nodes){
		mStartNodeIndex = startNodeIndex;
		mEndNodeIndex = endNodeIndex;
		mLength = mDuration = 0.0;
		for (int i=startNodeIndex; i<=endNodeIndex; i++){ //TODO: <= or < ??? To check. 
			RoadNode node = nodes.get(i);
			mLength += node.mLength;
			mDuration += node.mDuration;
		}
		Log.d(BonusPackHelper.LOG_TAG, "Leg: " + startNodeIndex + "-" + endNodeIndex
				+ ", length=" + mLength + "km, duration="+mDuration+"s");
	}

	//--- Parcelable implementation
	
	@Override public int describeContents() {
		return 0;
	}

	@Override public void writeToParcel(Parcel out, int flags) {
		out.writeDouble(mLength);
		out.writeDouble(mDuration);
		out.writeInt(mStartNodeIndex);
		out.writeInt(mEndNodeIndex);
	}
	
	public static final Creator<RoadLeg> CREATOR = new Creator<RoadLeg>() {
		@Override public RoadLeg createFromParcel(Parcel source) {
			return new RoadLeg(source);
		}
		@Override public RoadLeg[] newArray(int size) {
			return new RoadLeg[size];
		}
	};

	//Private constructor which takes in
	private RoadLeg(Parcel in){
		mLength = in.readDouble();
		mDuration = in.readDouble();
		mStartNodeIndex = in.readInt();
		mEndNodeIndex = in.readInt();
	}
}
