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

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.auth.User;

import java.util.HashMap;
import java.util.Map;


public class SpeedVanCameraActivity extends AppCompatActivity
{


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_form_for_me);


        // Initialize Firebase in your app
        FirebaseApp.initializeApp(this);

        // Create a reference to your Firebase Realtime Database instance using the URL
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://destination-e6a01-default-rtdb.europe-west1.firebasedatabase.app");
        Log.d("TAG", "Database URL: " + database.getReference().toString());


        DatabaseReference myRef = database.getReference("destination-e6a01-default-rtdb");

        Spinner categorySpinner = findViewById(R.id.spinner_categories);
        String category = categorySpinner.getSelectedItem().toString();

        double latitude = 52.519345;
        double longitude = -6.610428;

        VanCameraLocations speedvancameralocations = new VanCameraLocations(latitude, longitude, category);

        myRef.push().setValue(speedvancameralocations);




    }
}


