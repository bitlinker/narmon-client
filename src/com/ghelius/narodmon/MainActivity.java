package com.ghelius.narodmon;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class MainActivity extends Activity implements ServerDataGetter.OnResultListener {

    private final String TAG = "narodmon";
    private ArrayList<Sensor> sensorList = null;
    private  SensorItemAdapter adapter = null;
    private ImageButton btFavour = null;
    private ImageButton btList = null;
    private ListView listView = null;
    private String uid;


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        listView = (ListView)findViewById(R.id.listView);
        sensorList = new ArrayList<Sensor>();

		// get android UUID
        uid = Settings.Secure.getString(getBaseContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        Log.d(TAG,"my id is: " + uid);

		//get location
		LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
  		Criteria criteria = new Criteria();
  		criteria.setAccuracy(Criteria.ACCURACY_FINE);
  		String provider = lm.getBestProvider(criteria, true);
  		Location mostRecentLocation = lm.getLastKnownLocation(provider);
  		if(mostRecentLocation != null){
  			double latid=mostRecentLocation.getLatitude();
  			double longid=mostRecentLocation.getLongitude();
			// use API to send location
            Log.d(TAG,"my location: " + latid +" "+longid);
  		}

        adapter = new SensorItemAdapter(getApplicationContext(), sensorList);
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                sensorItemClick (position);
            }
        });

        btFavour = (ImageButton) findViewById(R.id.imageButton2);
        btFavour.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchFavourites();
            }
        });
        btList = (ImageButton) findViewById(R.id.imageButton1);
        btList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchList();
            }
        });



        ImageButton btRefresh = (ImageButton) findViewById(R.id.imageButton);
        btRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // update full sensor list from server
                ServerDataGetter updater = new ServerDataGetter();
                updater.setOnListChangeListener(MainActivity.this);
                updater.execute("http://narodmon.ru/client.php?json={\"cmd\":\"sensorList\",\"uuid\":\"" + uid + "\"}");
            }
        });

        // get full sensor list from server
        ServerDataGetter updater = new ServerDataGetter();
        updater.setOnListChangeListener(this);
        updater.execute("http://narodmon.ru/client.php?json={\"cmd\":\"sensorList\",\"uuid\":\"" + uid + "\"}");
    }

    class CustomComparator implements Comparator<Sensor> {
        @Override
        public int compare(Sensor o1, Sensor o2) {
            return o1.getDistance().compareTo(o2.getDistance());
        }
    }
    void makeSensorListFromJson (String result) {
        if (result != null) {
            sensorList.clear();
            try {
                JSONObject jObject = new JSONObject(result);
                JSONArray devicesArray = jObject.getJSONArray("devices");
                for (int i = 0; i < devicesArray.length(); i++) {
                    String location = devicesArray.getJSONObject(i).getString("location");
                    float distance = Float.parseFloat(devicesArray.getJSONObject(i).getString("distance"));
                    boolean my      = (devicesArray.getJSONObject(i).getInt("my") != 0);
                    //Log.d(TAG, + i + ": " + location);
                    JSONArray sensorsArray = devicesArray.getJSONObject(i).getJSONArray("sensors");
                    for (int j = 0; j < sensorsArray.length(); j++) {
                        String values = sensorsArray.getJSONObject(j).getString("value");
                        String name   = sensorsArray.getJSONObject(j).getString("name");
                        int type      = sensorsArray.getJSONObject(j).getInt("type");
                        int id        = sensorsArray.getJSONObject(j).getInt("id");
                        boolean pub   = (sensorsArray.getJSONObject(j).getInt("pub") != 0);
                        long times    = sensorsArray.getJSONObject(j).getLong("time");
                        sensorList.add(new Sensor(id, type, location, name, values, distance, my, pub, times));
                    }
                }
                // sort by distance
                Collections.sort(sensorList, new CustomComparator());

            } catch (JSONException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }


    @Override
    public void onResultReceived(String result) {
        makeSensorListFromJson(result);
        adapter.addAll(sensorList);
        adapter.notifyDataSetChanged();
        Toast toast = Toast.makeText(getApplicationContext(), sensorList.size() + " sensors online", Toast.LENGTH_SHORT);
        toast.show();
        //todo switchList of showWatched depend of last user choise, if we are started from notification - show watched
        switchList();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //MenuInflater inflater = getMenuInflater();
        //inflater.inflate(R.menu.icon_menu, menu);
        MenuItem mi = menu.add(0, 1, 0, "Preferences");
        mi.setIntent(new Intent(this, PreferActivity.class));
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.preference:
                
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void switchFavourites()
    {
        Log.d(TAG, "switch to watched");
        adapter.getFilter().filter("watch");
        btFavour.setImageResource(R.drawable.yey_blue);
        btList.setImageResource(R.drawable.list_gray);
        setTitle(listView.getCount() + " watched sensors");
    }

    private void switchList()
    {
        Log.d(TAG,"switch to list " + sensorList.size());
        adapter.getFilter().filter("");
        adapter.notifyDataSetChanged();
        btFavour.setImageResource(R.drawable.yey_gray);
        btList.setImageResource(R.drawable.list_blue);
        setTitle(sensorList.size() + " sensors online");
    }

    private String md5(String s) {
        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuffer hexString = new StringBuffer();
            for (int i=0; i<messageDigest.length; i++)
                hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }






    private void sensorItemClick (int position)
    {
        Intent i = new Intent (this, SensorInfo.class);
        i.putExtra("Sensor", sensorList.get(position));
        startActivity(i);
    }

}

// get update for sensors value (may be needed for threshold-alarms)
// request
// http://narodmon.ru/client.php?json={"cmd":"sensorinfo","uuid":12345,"sensor":[115,125]}
// answer
// {"sensors":[{"id":115,"value":-7.75,"time":"1356060145"},{"id":125,"value":-16.75,"time":"1356059853"}]}

// login
//{"cmd":"login","uuid":"eb9bcf95cdfc87b52352a7fc4ebd4e2e","login":"ghelius@gmail.com","hash":"f1cc51b1b741b771eaf77723d0303640"}


