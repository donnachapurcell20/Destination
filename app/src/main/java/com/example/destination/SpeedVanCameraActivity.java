package com.example.destination;

import static android.content.ContentValues.TAG;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;


public class SpeedVanCameraActivity extends AppCompatActivity {

    private EditText latitudeTextView;
    private EditText longitudeTextView;
    private Spinner spinnercategories;

    // Declare a reference to your Firebase database
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_form_for_me);

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Get the TextViews from the layout
        longitudeTextView = findViewById(R.id.longitudeme);
        latitudeTextView = findViewById(R.id.latitudeme);
        spinnercategories = findViewById(R.id.spinner_categories);


        // Create a new user with a first and last name
        Map<String, Object> speedVanCamera = new HashMap<>();
        speedVanCamera.put("first", "Ada");
        speedVanCamera.put("longitude", "Lovelace");
        speedVanCamera.put("latitude", 1815);

        // Add a new document with a generated ID
        db.collection("users")
                .add(speedVanCamera)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.d(TAG, "DocumentSnapshot added with ID: " + documentReference.getId());
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error adding document", e);
                    }
                });

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


