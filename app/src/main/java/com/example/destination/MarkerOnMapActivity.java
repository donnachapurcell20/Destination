package com.example.destination;

import android.os.Bundle;
import android.view.View;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class MarkerOnMapActivity extends AppCompatActivity {
    private TextView latitudeTextView;
    private TextView longitudeTextView;
    private Spinner spinnercategories;

    // Declare a reference to your Firebase database
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speed_camera_van_locations);

        // Get the TextViews from the layout
        longitudeTextView = findViewById(R.id.longitude);
        latitudeTextView = findViewById(R.id.latitude);
        spinnercategories = findViewById(R.id.spinner_categories);

        // Get the longitude and latitude from the intent extras
        double longitude = getIntent().getDoubleExtra("longitude", 0);
        double latitude = getIntent().getDoubleExtra("latitude", 0);

        // Set the text of the TextViews
        longitudeTextView.setText(String.valueOf(longitude));
        latitudeTextView.setText(String.valueOf(latitude));

        // Initialize your Firebase database reference
        mDatabase = FirebaseDatabase.getInstance().getReference();
    }

    public void onSubmitButtonClick(View view) {
        // Get the selected category from the spinner
        String category = spinnercategories.getSelectedItem().toString();

        // Get the longitude and latitude from the TextViews
        double longitude = Double.parseDouble(longitudeTextView.getText().toString());
        double latitude = Double.parseDouble(latitudeTextView.getText().toString());

        // Create a new Firebase database reference for the data you want to store
        DatabaseReference markerRef = mDatabase.child("your_node_name").push();

        // Set the values for the new marker
        markerRef.child("category").setValue(category);
        markerRef.child("longitude").setValue(longitude);
        markerRef.child("latitude").setValue(latitude);
    }
}
