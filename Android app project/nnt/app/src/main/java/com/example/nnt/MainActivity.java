package com.example.nnt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.nnt.directionhelpers.FetchURL;
import com.example.nnt.directionhelpers.TaskLoadedCallback;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.SphericalUtil;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, TaskLoadedCallback, SensorEventListener  {
    Button send;
    TextView msg_box,msg_box2, status;

    private SensorManager mSensorManager;

    ArrayList<LatLng> coordList;

    ToneGenerator toneGen1 ;

    Activity myActivity;

    LocationManager locationManager;
    BluetoothAdapter bluetoothAdapter;
    int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final String APP_NAME = "MipLAb";
    private static final UUID MY_UUID = UUID.fromString("8ce255c0-223a-11e0-ac64-0803450c9a66");
//--------------------------------------------------------//

    private TextToSpeech mTTS;
    double heading;

    public  double degree=0;
    GoogleMap map;
    Button btnGetDerection;

    MarkerOptions place1, place2;
    Polyline currentPolyline;
    //---
    public BluetoothSocket btSocket;
    static final UUID mUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    long finish;
    //----
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        //----------
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        myActivity = this;
        toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        //--------------

       // scenario coordinates
        coordList = new ArrayList<LatLng>();
        coordList.add(new LatLng(36.624241813, 127.458093074));
        coordList.add(new LatLng(36.62439918386, 127.457807958));
        coordList.add(new LatLng(36.6245541205, 127.457776151));
                //.add(new LatLng(36.6245638074, 127.457781517))
        coordList.add(new LatLng(36.6250513784, 127.458230787));
        coordList.add(new LatLng(36.6249932575, 127.458351486));
        coordList.add(new LatLng(36.6248426655, 127.458515772));
        coordList.add(new LatLng(36.624627113, 127.458869153));
        coordList.add(new LatLng(36.624109612, 127.458399736));
        coordList.add(new LatLng(36.6241332912, 127.458241486));
        //--------------
        checkMyPermission();
        // initialize your android device sensor capabilities
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        LatLng point2 = new LatLng(36.624264, 127.457617);
        LatLng point1 = new LatLng(36.62469, 127.45799);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        }
        //----------------
        findViewByIdes();
        implementListeners();
        //--------

        //-------- Text To speech

        mTTS = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {

                if (status == TextToSpeech.SUCCESS) {
                    int result = mTTS.setLanguage(Locale.US);

                    if (result == TextToSpeech.LANG_MISSING_DATA
                            || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Toast.makeText(MainActivity.this, "Language is not supported", Toast.LENGTH_SHORT).show();

                    } else {
                        Toast.makeText(MainActivity.this, "Language Supported", Toast.LENGTH_SHORT).show();
                    }

                } else {
                    Log.e("TTS", "initialisation failed");
                }
            }
        });
//---------------------
        btnGetDerection = findViewById(R.id.btnGetDirection);
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.mapNearBy);
        mapFragment.getMapAsync(this);


        place1 = new MarkerOptions().position(new LatLng(36.624241813, 127.458093074)).title("Start");
        place2 = new MarkerOptions().position(new LatLng(36.6241332912, 127.458241486)).title("Finish ");

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(myActivity,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(myActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
           ActivityCompat.requestPermissions(myActivity,new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},1);
        }

        msg_box2.setText("Cheongju eagles team");
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 0, new LocationListener() {
            @Override
            public void onLocationChanged( Location location) {

                LatLng point1 = new LatLng(location.getLatitude(),location.getLongitude());
                LatLng point2 = coordList.get(0);//

                heading = SphericalUtil.computeHeading(point1, point2);
                 double distance = SphericalUtil.computeDistanceBetween(point1, point2);

                 String msg ="";

                double heading3=heading;
                if (distance < 4) {
                    msg_box2.setText("wasalt ya bacha inzal");
                    coordList.remove(0);
                }

                if (heading < 0)
                heading += 360 ;
                 double  result= degree - heading;
                 if (result > 180)
                     result= 360 - result;
                 if( result < -180)
                     result = 360 + result;

                finish = System.currentTimeMillis();
                speak(inputDirecton(result, distance));
                msg_box2.setText("result: "+String.valueOf(result)+" | heading: "+String.valueOf( heading)+"|degree :"+String.valueOf( degree));
            }
        });
        //*********
        btnGetDerection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //implementListeners();
                LatLng latLng = new LatLng(36.624476, 127.457859);
                toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP,150);
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18), 5000, null);
                PolylineOptions polylineOptions = new PolylineOptions()

                        // scenario visualisation on the map
                .add(new LatLng(36.624241813, 127.458093074))
                .add(new LatLng(36.62439918386, 127.457807958))
                .add(new LatLng(36.6245541205, 127.457776151))
                .add(new LatLng(36.6250513784, 127.458230787))
                .add(new LatLng(36.6249932575, 127.458351486))
                .add(new LatLng(36.6248426655, 127.458515772))
                .add(new LatLng(36.624627113, 127.458869153))
                .add(new LatLng(36.624109612, 127.458399736))
                .add(new LatLng(36.6241332912, 127.458241486));

                Polyline polyline = map.addPolyline(polylineOptions.color(Color.RED));

                System.out.println("********************************************");
                LatLng point1 = new LatLng(36.62461, 127.45792);
                LatLng point2 = new LatLng(36.62427, 127.46434);

                double heading = SphericalUtil.computeHeading(point1, point2);

                System.out.println(heading);
                System.out.println("********************************************");



            }

        });

        String url = getUrl(place1.getPosition(),place2.getPosition(),"walking");
        new FetchURL(MainActivity.this).execute(getUrl(place1.getPosition(), place2.getPosition(), "walking"), "walking");

    }

    private void findViewByIdes() {
        msg_box =(TextView) findViewById(R.id.textView);
        msg_box2 =(TextView) findViewById(R.id.textView2);
        status=(TextView) findViewById(R.id.textView);
    }

    private void implementListeners() {

        BluetoothThread s=new BluetoothThread();
        s.start();

    }

    private void speak(String msg) {
        String text = msg_box.getText().toString();
            mTTS.setSpeechRate(0.70f);
            mTTS.speak(msg, TextToSpeech.QUEUE_FLUSH, null);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // for the system's orientation sensor registered listeners
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // to stop the listener and save battery
        mSensorManager.unregisterListener(this);
    }
    @Override
    public void onSensorChanged(SensorEvent event) {

        // get the angle around the z-axis rotated
         degree = Math.round(event.values[0]);

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    class BluetoothThread extends  Thread {

        //Thread for run the bluetooth

        public void run() {

            final TextView txtv = (TextView) findViewById(R.id.textView);
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            System.out.println("-----------------------");
            System.out.println(btAdapter.getBondedDevices());
            System.out.println("-----------------------");

            //Use raspberry pi Mac Address for pairing
            BluetoothDevice hc05 = btAdapter.getRemoteDevice("E4:5F:01:1B:9A:8B");
            System.out.println(hc05.getName());

            btSocket = null;


            // textTospeash

            //connection
            int counter = 0;
            do {

                try {
                    btSocket = hc05.createRfcommSocketToServiceRecord(mUUID);
                    System.out.println(btSocket);
                    btSocket.connect();
                    System.out.println(btSocket.isConnected());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                counter++;
            } while (!btSocket.isConnected() && counter < 3);


            try {
                OutputStream outputStream = btSocket.getOutputStream();
                outputStream.write(48);
            } catch (IOException e) {
                e.printStackTrace();
            }

            while (true) {
                //input
                InputStream inputStream = null;
                try {
                    inputStream = btSocket.getInputStream();
                    inputStream.skip(inputStream.available());

                    byte[] buffer = new byte[1024];
                    int bytes; // bytes returned from read()

                    bytes = inputStream.read(buffer);
                    final String incomingMessage = new String(buffer, 0, bytes);
                    System.out.println("InputStream: " + incomingMessage);


                    myActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            txtv.setText(String.valueOf("OAK Command : "+incomingMessage.toString()));
                            //speak(incomingMessage);
                            speak2(incomingMessage);
                        }
                    });

                    if (incomingMessage == "z") {System.out.println(btSocket.isConnected());}

                } catch (IOException e) {

                    e.printStackTrace();
                }
            }
        }

        private void speak2(String msg) {

            // Speaking function for received text via bluetooth
            String text = msg_box.getText().toString();

            mTTS.setPitch(0.5f);
            mTTS.speak(msg, TextToSpeech.QUEUE_ADD, null);
        }


    }
    @Override
    protected void onDestroy() {
        if(mTTS != null){
            mTTS.stop();
            mTTS.shutdown();
        }
        super.onDestroy();
    }

    private void checkMyPermission() {
        Dexter.withContext(this).withPermission(Manifest.permission.ACCESS_FINE_LOCATION).withListener(new PermissionListener() {
            @Override
            public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                Toast.makeText(MainActivity.this,"Permission Garant",Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("pakage",getPackageName(),"");
                intent.setData(uri);
                startActivity(intent);
            }

            @Override
            public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {
                    permissionToken.continuePermissionRequest();
            }
        }).check();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setMyLocationEnabled(true);
        map.addMarker(place1);
        map.addMarker(place2);
    }

    private String getUrl(LatLng origin, LatLng dest, String directionMode) {
        System.out.println("***************getUrl******************** Start");
        // Origin of route
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        System.out.println( str_origin);
        // Destination of route
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;
        System.out.println(str_dest);
        // Mode
        String mode = "mode=" + directionMode;
        System.out.println(mode);
        // Building the parameters to the web service
        String parameters = str_origin + "&" + str_dest + "&" + mode;
        System.out.println(parameters);
        // Output format
        String output = "json";
        System.out.println(output);
        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters + "&key=" + getString(R.string.google_maps_key);
        System.out.println("*****************getUrl******************End");
        return url;
    }


    @Override
    public void onTaskDone(Object... values) {
        if(currentPolyline!= null)
            currentPolyline.remove();
        currentPolyline = map.addPolyline((PolylineOptions) values[0]);

    }

    public static double computeHeading(LatLng from, LatLng to) {
        double fromLat = Math.toRadians(from.latitude);
        double fromLng = Math.toRadians(from.longitude);
        double toLat = Math.toRadians(to.latitude);
        double toLng = Math.toRadians(to.longitude);
        double dLng = toLng - fromLng;
        double heading = Math.atan2(
                Math.sin(dLng) * Math.cos(toLat),
                Math.cos(fromLat) * Math.sin(toLat) - Math.sin(fromLat) * Math.cos(toLat) * Math.cos(dLng));
        return Math.toDegrees(heading);
    }

   /* public static String direction(LatLng latlng1, LatLng latlng2) {
        double delta = 22.5;
        String direction = "UNKNOWN";
        double heading = SphericalUtil.computeHeading(latlng1, latlng2);

        if ((heading >= 0 && heading < delta) || (heading < 0 && heading >= -delta)) {
            direction = "NORTH";
        } else if (heading >= delta && heading < 90 - delta) {
            direction = "NORTH_EAST";
        } else if (heading >= 90 - delta && heading < 90 + delta) {
            direction = "EAST";
        } else if (heading >= 90 + delta && heading < 180 - delta) {
            direction = "SOUTH_EAST";
        } else if (heading >= 180 - delta || heading <= -180 + delta) {
            direction = "SOUTH";
        } else if (heading >= -180 + delta && heading < -90 - delta) {
            direction = "SOUTH_WEST";
        } else if (heading >= -90 - delta && heading < -90 + delta) {
            direction = "WEST";
        } else if (heading >= -90 + delta && heading < -delta) {
            direction = "NORTH_WEST";
        }

        return direction;
    }*/

   /* Double ComputeAngle(LatLng latlng1, LatLng latlng2) {

        double  dy =Math.toRadians(latlng2.latitude)-Math.toRadians(latlng1.latitude);
        double dx = Math.cos(Math.PI/180*latlng1.latitude)*(Math.toRadians(latlng2.longitude)-Math.toRadians(latlng1.longitude));

        return Math.toDegrees(Math.atan2(dy,dx) );
    }

    Double ComputeAngle2(LatLng latlng1, LatLng latlng2) {
        LatLng latlng_north = new LatLng(81.3000, -110.8000);
        double heading1 = SphericalUtil.computeHeading(latlng1, latlng_north);
        double heading2 = SphericalUtil.computeHeading(latlng2, latlng_north);
        double heading = heading1 - heading2;
        return heading ;
    }*/

    String inputDirecton(double r, double d){

        //walking navigation angle decisions
        if (d > 4) {

            if (r < -30 && r >= -50)
                return "Turn slightly right";
            if (r < -50 && r >= -120)
                return "Turn  right";
           /*if (r < -120)
                return "You are out of course turn back please";*/
            if (r > 30 && r <= 50)
                return "Turn slightly left";
            if (r > 50 && r <= 120)
                return "Turn  left ";
            if (r > 120 || r<-120)
                return "You are out of course turn back please";


        }
        else{
            toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP,150);
        }

        return ".............";
    }

}


