package com.example.taller3

import android.graphics.drawable.BitmapDrawable
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private lateinit var database: FirebaseDatabase
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etFirstName: EditText
    private lateinit var etIdentificationNumber: EditText
    private lateinit var ivProfileImage: ImageView

    private val REQUEST_IMAGE_CAPTURE = 1
    private lateinit var currentPhotoPath: String


    val PATH_USERS = "users/"
    private lateinit var myRef : DatabaseReference
    private lateinit var uid : String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()
        database = FirebaseDatabase.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etFirstName = findViewById(R.id.etFirstName)
        etIdentificationNumber = findViewById(R.id.etIdentificationNumber)
        ivProfileImage = findViewById(R.id.ivProfileImage)

        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnTakePhoto = findViewById<Button>(R.id.btnTakePhoto)

        btnRegister.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()
            val firstName = etFirstName.text.toString()
            val identificationNumber = etIdentificationNumber.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty() && firstName.isNotEmpty() && identificationNumber.isNotEmpty()) {
                requestLocationPermission()
            } else {
                Toast.makeText(this, "Complete todos los campos", Toast.LENGTH_SHORT).show()
            }
        }

        btnTakePhoto.setOnClickListener {
            askCameraPermission()
        }
    }

    private fun askCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
                //Toast.makeText(this, "Gracias", Toast.LENGTH_SHORT).show()
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.CAMERA
            ) -> {
                Toast.makeText(
                    this,
                    "Se necesita permiso para acceder a la cámara",
                    Toast.LENGTH_LONG
                ).show()
                requestCameraPermission()
            }

            else -> {
                requestCameraPermission()
            }
        }
    }
    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            MY_PERMISSION_REQUEST_CAMERA
        )
    }
    private fun openCamera() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        }

    }
    private fun requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                getLastLocation()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }





    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    val imageBitmap = data?.extras?.get("data") as Bitmap
                    ivProfileImage.setImageBitmap(imageBitmap)
                    createImageFile()

                }

            }
        }
    }
    private fun getLastLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "No se han concedido los permisos necesarios", Toast.LENGTH_SHORT).show()
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    // Ubicación obtenida con éxito
                    val latitude = location.latitude
                    val longitude = location.longitude

                    // Continuar con el registro del usuario
                    registerUser(
                        etEmail.text.toString(),
                        etPassword.text.toString(),
                        etFirstName.text.toString(),
                        etIdentificationNumber.text.toString(),
                        latitude,
                        longitude
                    )
                } else {
                    // No se pudo obtener la ubicación
                    Toast.makeText(this, "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                // Manejar el error al obtener la ubicación
                Toast.makeText(this, "Error al obtener la ubicación: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createImageFile() {
        try {
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir: File? = getExternalFilesDir("Pictures")
            val imageFile = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
            currentPhotoPath = imageFile.absolutePath
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun registerUser(email: String, password: String, firstName: String, identificationNumber: String, latitude: Double, longitude: Double) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        saveUserDataToDatabase(userId, firstName, identificationNumber, latitude, longitude)
                        Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()



                        uploadProfileImageToStorage(userId)
                    }
                    startActivity(Intent(this, HomeActivity::class.java))
                   // finish()
                } else {
                    Toast.makeText(this, "Registro fallido", Toast.LENGTH_SHORT).show()
                }
            }
    }



private fun saveUserDataToDatabase(userId: String, firstName: String, identificationNumber: String, latitude: Double, longitude: Double) {
    val user1 = User()
    user1.firstName = firstName
    user1.identificationNumber = identificationNumber
    user1.latitude = latitude
    user1.longitude = longitude
    myRef = database.getReference(PATH_USERS+userId)

    myRef.setValue(user1)
    Toast.makeText(this,"Usuario guardado",Toast.LENGTH_SHORT).show()
}


    private fun uploadProfileImageToStorage(userId: String) {
        val storageReference = storage.reference.child("profile_images").child(userId)
        val imageBitmap = (ivProfileImage.drawable as BitmapDrawable).bitmap

        val baos = ByteArrayOutputStream()
        imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val data = baos.toByteArray()

        storageReference.putBytes(data)
            .addOnSuccessListener {
                // Subida exitosa
                Toast.makeText(this, "Imagen subida exitosamente", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al subir la imagen", Toast.LENGTH_LONG).show()
                // Manejar el error en caso de falla
            }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1002
        val MY_PERMISSION_REQUEST_CAMERA = 103

    }
}
