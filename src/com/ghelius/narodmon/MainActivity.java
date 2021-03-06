package com.ghelius.narodmon;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends ActionBarActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener,
        SensorInfoFragment.SensorConfigChangeListener,
        FilterFragment.OnFilterChangeListener,
        SensorListFragment.OnSensorListClickListener,
        FragmentManager.OnBackStackChangedListener,
        SlidingMenuFragment.MenuClickListener{


    enum LoginStatus {LOGIN, LOGOUT, ERROR}

    private static final int MAX_DEVICES_LIMIT = 20;
    private SensorInfoFragment sensorInfoFragment;
    private FilterFragment filterFragment;
    private SensorListFragment sensorListFragment;
    private Menu mOptionsMenu;
    private final static int gpsUpdateIntervalMs = 20 * 60 * 1000; // time interval for updateFilter coordinates and sensor list
    private NarodmonApi.onResultReceiveListener apiListener;
    private boolean showRefreshProgress;
    private int oldRadius = 0;
    private boolean clearOptionsMenu = false;
    private MyLocation.LocationResult myUpdateLocationListener;
    private int deviceRequestLimit = MAX_DEVICES_LIMIT;
    private boolean allMenuSelected;
    private ArrayList<Integer> oldHidenTypes = new ArrayList<Integer>();
    private boolean dontUpdateMore = false;
    private ArrayList<Integer> additionalSensors = new ArrayList<Integer>();
    private static final String api_key = "36nzbVLboSwPM"; // prev value "85UneTlo8XBlA"
    private final String TAG = "narodmon-main";
    private ArrayList<Sensor> sensorList;
    private SensorItemAdapter listAdapter;
    private Timer updateTimer = null;
    private Timer gpsUpdateTimer = null;
    private LoginDialog loginDialog;
    private UiFlags uiFlags;
    private NarodmonApi mNarodmonApi;
    private String apiHeader;
    private LoginStatus loginStatus = LoginStatus.LOGOUT;
    private String uid;
    private DrawerLayout mDrawerLayout = null;
    private View mDrawerMenu = null;
    private ActionBarDrawerToggle mDrawerToggle = null;
    private CharSequence mTitle;
    private SlidingMenuFragment slidingMenu;
    //	private final static int gpsUpdateIntervalMs = 1*60*1000; // time interval for updateFilter coordinates and sensor list




    // ********************************************
    // SensorListFragment.OnSensorListClickListener
    // ********************************************
    @Override
    public void onItemClick(ListView l, View v, int position, long id) {
        Log.d(TAG, "sensor clicked: " + position);
        sensorItemClick(position);
    }

    @Override
    public void scrollOverDown() {
        Log.d(TAG, "more: !showRefreshProgress=" + !showRefreshProgress +
                ", allMenuSelected=" + allMenuSelected +
                ", !dontUpdateMore=" + !dontUpdateMore);
        if (!showRefreshProgress && allMenuSelected && !dontUpdateMore) {
            deviceRequestLimit += 10;
            Log.d(TAG, "more get list");
            getSensorsList(deviceRequestLimit);
        }
    }

    @Override
    public void moreButtonPressed() {
        deviceRequestLimit += 10;
        Log.d(TAG, "more get list");
        getSensorsList(deviceRequestLimit);
    }
    // ********************************************




    // for catch sensorId from widget click
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onBackStackChanged() {
        supportInvalidateOptionsMenu();
        //Enable Up button only  if there are entries in the back stack
        Log.d(TAG, "rotate: onBackStackListener");
        boolean canBack = getSupportFragmentManager().getBackStackEntryCount() > 0;
        if (canBack) {
        } else {
            clearOptionsMenu = false;
            View v = findViewById(R.id.content_frame1);
            if (v != null)
                v.setVisibility(View.GONE);
        }
        if (mDrawerToggle != null) {
            mDrawerToggle.setDrawerIndicatorEnabled(!canBack);
        } else {
            getSupportActionBar().setDisplayHomeAsUpEnabled(canBack);
        }
        updateFragmentHolderVisibility();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, ">>>>>>>> onCreate, rotate " + !(savedInstanceState == null));
        Log.d(TAG,"rotate: last configuration is " + (getSupportFragmentManager().getBackStackEntryCount() > 0 ? getSupportFragmentManager().getBackStackEntryAt(getSupportFragmentManager().getBackStackEntryCount()-1).getName() : "none"));
        myUpdateLocationListener = new UpdateLocationListener();
        uiFlags = UiFlags.load(this);
        oldRadius = uiFlags.radiusKm;
        Log.d(TAG, "radius: " + uiFlags.radiusKm);
        setContentView(R.layout.activity_main);
        apiListener = new ApiListener();
        getSupportFragmentManager().addOnBackStackChangedListener(this);
        sensorList = ((MyApplication)this.getApplication()).getSensorList();


        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (mDrawerLayout != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            mDrawerMenu = findViewById(R.id.left_menu_view);
            // set a custom shadow that overlays the main content when the drawer opens
            mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
            // enable ActionBar app icon to behave as action to toggle nav drawer

            // ActionBarDrawerToggle ties together the the proper interactions
            // between the sliding drawer and the action bar app icon
            mDrawerToggle = new ActionBarDrawerToggle(
                    this,                  /* host Activity */
                    mDrawerLayout,         /* DrawerLayout object */
                    R.drawable.ic_drawer,  /* nav drawer image to replace 'Up' caret */
                    R.string.drawer_open,  /* "open drawer" description for accessibility */
                    R.string.drawer_close  /* "close drawer" description for accessibility */
            ) {
                public void onDrawerClosed(View view) {
                    getSupportActionBar().setTitle(mTitle);
//				invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
                }

                public void onDrawerOpened(View drawerView) {
                    getSupportActionBar().setTitle(mTitle);
//				invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
                }
            };
            mDrawerLayout.setDrawerListener(mDrawerToggle);
        }

        if (savedInstanceState != null) {
            Log.d(TAG, "rotate backstack count: " + getSupportFragmentManager().getBackStackEntryCount());
//            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                sensorInfoFragment = (SensorInfoFragment)getSupportFragmentManager().findFragmentByTag("SENSOR_INFO_FRAGMENT");
                Log.d(TAG, "rotate, sensorInfoFragment exist:" + (sensorInfoFragment != null));
                sensorListFragment = (SensorListFragment)getSupportFragmentManager().findFragmentByTag("MAIN_LIST_FRAGMENT");
                Log.d(TAG, "rotate, sensorListFragment exist:" + (sensorListFragment != null));
                filterFragment = (FilterFragment)getSupportFragmentManager().findFragmentByTag("FILTER_FRAGMENT");
                Log.d(TAG, "rotate, filterFragment exist:" + (filterFragment != null));
                slidingMenu = (SlidingMenuFragment)getSupportFragmentManager().findFragmentByTag("SLIDING_MENU");
                Log.d(TAG, "rotate, menuFragment exist:" + (slidingMenu != null));
//            }
        }

        if (sensorListFragment == null) {
            Log.d(TAG,"rotate: create new sensorListFragment");
            sensorListFragment = new SensorListFragment();
        } else {
            Log.d(TAG, "rotate, dont create new sensorListFragment");
        }

        if (slidingMenu == null) {
            slidingMenu = new SlidingMenuFragment();
        }

        if (savedInstanceState == null) {
            FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
            trans.replace(R.id.content_frame, sensorListFragment, "MAIN_LIST_FRAGMENT");
            trans.replace(R.id.left_menu_view, slidingMenu, "SLIDING_MENU");
            trans.commit();
        }

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        // get android UUID
        uid = NarodmonApi.md5(Settings.Secure.getString(getBaseContext().getContentResolver(), Settings.Secure.ANDROID_ID));
        Log.d(TAG, "android ID: " + uid);
        apiHeader = "{\"uuid\":\"" + uid +
                "\",\"api_key\":\"" + api_key + "\",";
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString("apiHeader",apiHeader).commit();


        listAdapter = new SensorItemAdapter(getApplicationContext(), sensorList);
        listAdapter.setUiFlags(uiFlags);
        sensorListFragment.setListAdapter(listAdapter);

        loginDialog = new LoginDialog();
        loginDialog.setOnChangeListener(new LoginDialog.LoginEventListener() {
            @Override
            public void login() {
                doLogin();
            }

            @Override
            public void logout() {
                mNarodmonApi.doLogout();
            }

            @Override
            public LoginStatus loginStatus() {
                return loginStatus;
            }
        });

        mNarodmonApi = new NarodmonApi(getApplicationContext().getString(R.string.api_url), apiHeader);
        mNarodmonApi.setOnResultReceiveListener(apiListener);

        if (((MyApplication)this.getApplication()).isListOld()) {
            mNarodmonApi.restoreSensorList(getApplicationContext(), sensorList);
            listAdapter.updateFilter();
            updateMenuSensorCounts();
            Log.d(TAG, "load.. load new list");
            getSensorsList(deviceRequestLimit);
        } else {
            Log.d(TAG, "load.. use existing list");
            updateMenuSensorCounts();
        }

        Intent intent = new Intent(this, OnBootReceiver.class);
        sendBroadcast(intent);
        scheduleAlarmWatcher();

        setTitle(mTitle);

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.pref_key_autologin), false)) {
            doLogin();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, ">>>>>>>> onResume: ");

        Intent startIntent = getIntent();
        final int sensorId = startIntent.getIntExtra("sensorId", -1);
        if (sensorId != -1) {
            Log.d(TAG,"we launch from widget: " + sensorId);
            new Handler().postDelayed(new TimerTask() {
                @Override
                public void run() {
                    showSensorInfo(sensorId);
                }
            }, 0);
        } else {
            Log.d(TAG,"regular launch");
        }

        updateSensorsValue();
        initLocationUpdater();

        startUpdateTimer();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (!pref.getBoolean(getString(R.string.pref_key_use_geocode), false)) {
            startGpsTimer();
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                sendVersion();
                mNarodmonApi.getTypeDictionary();
            }
        }, 5000);
        if (mDrawerToggle!= null) {
            mDrawerToggle.setDrawerIndicatorEnabled(!(getSupportFragmentManager().getBackStackEntryCount() > 0));
            mDrawerToggle.syncState();
        }

        onBackStackChanged();
    }

    void updateFragmentHolderVisibility() {
        int backStackCount = getSupportFragmentManager().getBackStackEntryCount();
        View v = findViewById(R.id.content_frame1);
        if (backStackCount == 0 && v != null) {
            v.setVisibility(View.GONE);
            return;
        }
        String backStackTag = "";
        if (backStackCount > 0)
            backStackTag = getSupportFragmentManager().getBackStackEntryAt(backStackCount-1).getName();

        if ( v != null) { // tablet
            if (backStackTag.equals("SENSOR_INFO")) {
                v.setVisibility(View.VISIBLE);
            } else if (backStackTag.equals("FILTER")) {
                v.setVisibility(View.VISIBLE);
            } else {
               v.setVisibility(View.GONE);
            }
        } else {                                         // phone
            // nothing todo
        }
    }

    @Override
    public void onPause() {
        Log.i(TAG, ">>>>>>>>> onPause");
        super.onPause();
        stopUpdateTimer();
        stopGpsTimer();
    }

    @Override
    public void onDestroy() {
        mNarodmonApi.setOnResultReceiveListener(null);
        Log.i(TAG, ">>>>>>>>>> onDestroy");
        uiFlags.save(this);
        stopUpdateTimer();
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
        stopGpsTimer();
        myUpdateLocationListener = null;
        super.onDestroy();
    }

    @Override
    public void favoritesChanged() {
        int cnt = DatabaseManager.getInstance().getFavoritesId().size();
        slidingMenu.setMenuWatchCount(cnt);
        listAdapter.updateFavorites();
        listAdapter.updateFilter();
    }

    @Override
    public void alarmChanged() {
        int cnt = DatabaseManager.getInstance().getAlarmTasks().size();
        slidingMenu.setMenuAlarmCount(cnt);
        listAdapter.updateAlarms();
        listAdapter.updateFilter();
    }

    @Override
    public void filterChange() {
        Log.d(TAG,"more: filterChange " + uiFlags.hidenTypes + " vs " + oldHidenTypes);
        if (oldHidenTypes.size() != uiFlags.hidenTypes.size()) {
            Log.d(TAG, "more: hiddenTypes changed");
            deviceRequestLimit = MAX_DEVICES_LIMIT;
            oldHidenTypes.clear();
            oldHidenTypes.addAll(uiFlags.hidenTypes);
            dontUpdateMore = false;
            getSensorsList(deviceRequestLimit);
        } else {
            Log.d(TAG,"more: hidden the same");
        }
        listAdapter.updateFilter();
    }

    @Override
    public UiFlags returnUiFlags() {
        Log.d(TAG,"returnUiFlags radius: " + uiFlags.radiusKm);
        return uiFlags;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG, "onSharedPreferenceChanged " + key);
        if (key.equals(getString(R.string.pref_key_interval)) || key.equals(getString(R.string.pref_key_digits_amount))) { // updateFilter interval changed
            scheduleAlarmWatcher();
            startUpdateTimer();
        } else if (key.equals(getString(R.string.pref_key_geoloc)) || key.equals(getString(R.string.pref_key_use_geocode))) {
            initLocationUpdater();
            getSensorsList(deviceRequestLimit);
        }
    }

    void initLocationUpdater() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean useGps = !prefs.getBoolean(getString(R.string.pref_key_use_geocode), false);
        Float lat = prefs.getFloat("lat", 0.0f);
        Float lng = prefs.getFloat("lng", 0.0f);
        // it's first start, gps data may be not ready or gps not used, so we use prev coordinates always first time
        if (lat != 0.0f && lng != 0.0f)
            mNarodmonApi.setLocation(lat, lng);
        if (useGps) { // if use gps, just updateFilter location periodically, set to api, don't use saved value
            Log.d(TAG, "init location updater: we use gps");
            startGpsTimer();
            updateLocation();
        } else { // if use address, use this value and set to api, this value updateFilter and save in location result callback
            Log.d(TAG, "init location updater: we use address");
            mNarodmonApi.sendLocation(prefs.getString(getString(R.string.pref_key_geoloc), ""));
        }
    }


    private void updateMenuSensorCounts() {
        Log.d(TAG,"updateSensorCount: " + listAdapter.getAllCount());
        slidingMenu.setMenuAllCount(listAdapter.getAllCount());
        slidingMenu.setMenuMyCount(listAdapter.getMyCount());
        favoritesChanged();
        alarmChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        setRefreshProgress(showRefreshProgress);
        if (clearOptionsMenu) {
            mOptionsMenu.clear();
        }
        return super.onCreateOptionsMenu(menu);
    }

    /* Called whenever we call invalidateOptionsMenu() */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.d(TAG,"onPrepareOptionMenu");
        // If the nav drawer is open, hide action items related to the content view
//        boolean drawerOpen = mDrawerLayout.isDrawerOpen(mDrawerList);
        MenuItem i = menu.findItem(R.id.menu_filter);
        if (i != null)
            i.setVisible(getSupportFragmentManager().getBackStackEntryCount() == 0);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // The action bar home/up action should open or close the drawer.
        // ActionBarDrawerToggle will take care of this.


        if (item.getItemId() == android.R.id.home && getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            return true;
        }

        // Handle action buttons
        switch (item.getItemId()) {
            case R.id.menu_settings:
                Log.d(TAG, "start settings activity");
                startActivity(new Intent(MainActivity.this, PreferActivity.class));
                break;
            case R.id.menu_refresh:
                Log.d(TAG, "refresh sensor list, load..");
                getSensorsList(deviceRequestLimit);
                break;
            case R.id.menu_login:
                Log.d(TAG, "show login dialog");
                loginDialog.show(getSupportFragmentManager(), "dlg2");
                break;
            case R.id.menu_help:
                String url = "http://helius.github.com/narmon-client/";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
                break;
            default:

        }

        if (mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        getSupportActionBar().setTitle(mTitle);
    }

    /**
     * When using the ActionBarDrawerToggle, you must call it during
     * onPostCreate() and onConfigurationChanged()...
     */

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (mDrawerToggle != null)
            mDrawerToggle.syncState();
    }

    private void menuFilterClicked() {
        showFilter();
    }


    public void getSensorsList(int number) {

        Log.d(TAG, "start full list load...");
        setRefreshProgress(true);
        ArrayList<Integer> types = new ArrayList<Integer>();

        if (uiFlags.hidenTypes.size()!=0) {
            ArrayList<SensorType> sensorTypes = SensorTypeProvider.getInstance(getApplicationContext()).getTypesList();

            for (SensorType sType : sensorTypes) {
                if (!uiFlags.hidenTypes.contains(sType.code)) {
                    types.add(sType.code);
                }
            }
        }
        mNarodmonApi.getSensorList(sensorList, number, types);
        ((MyApplication)getApplication()).setUpdateTimeStamp(System.currentTimeMillis());
        updateSavedSensors();
    }

    public void updateSavedSensors() {
        additionalSensors.clear();
        for (AlarmSensorTask a : (DatabaseManager.getInstance().getAlarmTasks())) {
            Log.d(TAG, "devices alarm: ["+ a.id +"]["+a.deviceId+"]");
            if (!additionalSensors.contains(a.deviceId)) {
                additionalSensors.add(a.deviceId);
            }
        }
        for (Pair<Integer,Integer> i : DatabaseManager.getInstance().getFavorites()) {
            Log.d(TAG, "devices favorites: ["+ i.first +"]["+i.second+"]");
            if (!additionalSensors.contains(i.second)) {
                additionalSensors.add(i.second);
            }
        }
        Log.d(TAG, "devices for request: " + additionalSensors);
        if (!additionalSensors.isEmpty()) {
            Integer s = additionalSensors.get(0);
            mNarodmonApi.getSensorsByDevice(s);
            additionalSensors.remove(s);
            Log.d(TAG, "devices sensor obtain: list not empty, get " + s);
        }
    }

    public void updateSensorsValue() {

        Log.d(TAG, "------------ updateFilter sensor value ---------------");
        setRefreshProgress(true);
        mNarodmonApi.updateSensorsValue(sensorList);
    }


    private void sendVersion() {
        mNarodmonApi.sendVersion(getString(R.string.app_version_name));
    }

    private void doLogin() {
        Log.d(TAG, "doLogin");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        String login = prefs.getString(String.valueOf(getText(R.string.pref_key_login)), "");
        String passwd = prefs.getString(String.valueOf(getText(R.string.pref_key_passwd)), "");
        if (!login.equals("")) {// don't try if login is empty
            mNarodmonApi.doAuthorisation(login, passwd, uid);
        } else {
            Log.w(TAG, "login is empty, do not authorisation");
        }
    }

    void updateLocation() {
        // try to avoid multiply listener calls, but it doesn't work...
        MyLocation myLocation = ((MyApplication)getApplication()).getMyLocation();
        if (myLocation == null) {
            myLocation = new MyLocation();
            ((MyApplication)getApplication()).setMyLocation(myLocation);
        }
        myLocation.getLocation(getApplicationContext(), myUpdateLocationListener);//new MyLocation.LocationResult() {
    }

    private void showSensorInfo (int sensorId) {
        sensorInfoFragment = (SensorInfoFragment)getSupportFragmentManager().findFragmentByTag("SENSOR_INFO_FRAGMENT");
        if (sensorInfoFragment == null) { // lazy
            sensorInfoFragment = new SensorInfoFragment();
            sensorInfoFragment.setConfigChangeListener(this);
        }

        Sensor tmpS = null;
        for (Sensor s : sensorList) {
            if (s.id == sensorId) {
                tmpS = s;
                break;
            }
        }
        if (tmpS == null) {
            sensorInfoFragment.setId(sensorId);
        } else {
            sensorInfoFragment.setSensor(tmpS);
        }
        if (findViewById(R.id.content_frame1) != null) {
            Log.d(TAG,"frame1 exist");
            if (getSupportFragmentManager().findFragmentById(R.id.content_frame1) == null) {
                Log.d(TAG,"rotate: add sensorInfoFragment to frame1");
                FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
                trans.hide(getSupportFragmentManager().findFragmentById(R.id.left_menu_view));
                trans.add(R.id.content_frame1, sensorInfoFragment, "SENSOR_INFO_FRAGMENT");
                trans.addToBackStack("SENSOR_INFO");
                trans.commit();
            } else {
                Log.d(TAG,"frame1 already contain fragment");
            }
            sensorInfoFragment.loadInfo();
        } else {
            if (getSupportFragmentManager().findFragmentByTag("SENSOR_INFO_FRAGMENT") == null) {
                Log.d(TAG, "frame1 doesn't exist");
                FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
                trans.replace(R.id.content_frame, sensorInfoFragment, "SENSOR_INFO_FRAGMENT");
                trans.addToBackStack("SENSOR_INFO");
                trans.commit();
            }
        }
    }

    private void showFilter () {
        if (filterFragment == null) { // lazy
            filterFragment = new FilterFragment();
        }
        if (findViewById(R.id.content_frame1) != null) {
            Log.d(TAG, "frame1 exist");
            if (getSupportFragmentManager().findFragmentById(R.id.content_frame1) == null) {
                Log.d(TAG,"frame1 not contain fragment");
//                findViewById(R.id.content_frame1).setVisibility(View.VISIBLE);
                FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
                trans.hide(getSupportFragmentManager().findFragmentById(R.id.left_menu_view));
                trans.add(R.id.content_frame1, filterFragment, "FILTER_FRAGMENT");
                trans.addToBackStack("FILTER");
                trans.commit();
            } else {
                Log.d(TAG,"frame1 already contain fragment");
            }
        } else {
            Log.d(TAG,"frame1 doesn't exist");
            FragmentTransaction trans = getSupportFragmentManager().beginTransaction();
            trans.replace(R.id.content_frame, filterFragment);
            trans.addToBackStack("FILTER");
            trans.commit();
        }
    }

    private void sensorItemClick(int position) {
        showSensorInfo(listAdapter.getItem(position).id);
    }

    // called by action (define via xml onClick)
    public void showFilterDialog(MenuItem item) {
        menuFilterClicked();
    }

    // called by pressing refresh button (define via xml onClick)
    public void onUpdateBtnPress(MenuItem item) {
        updateSensorsValue();
    }

    private void setRefreshProgress(boolean refreshing) {
        showRefreshProgress = refreshing;
        if (mOptionsMenu != null) {
            final MenuItem refreshItem = mOptionsMenu.findItem(R.id.menu_refresh);
            if (refreshItem != null) {
                if (refreshing) {
                    MenuItemCompat.setActionView(refreshItem, R.layout.actionbar_indeterminate_progress);
                } else {
                    MenuItemCompat.setActionView(refreshItem, null);
                }
            }
        }
    }

    final Handler updateTimerHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            Log.d(TAG, "updateTimer fired");
            updateSensorsValue();
            return false;
        }
    });

    final Handler gpsTimerHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            Log.d(TAG, "GPS updateTimer fired");
            updateLocation();
            return false;
        }
    });

    void stopGpsTimer() {
        if (gpsUpdateTimer != null) {
            gpsUpdateTimer.cancel();
            gpsUpdateTimer.purge();
            gpsUpdateTimer = null;
        }
    }

    void startGpsTimer() {
        stopGpsTimer();
        gpsUpdateTimer = new Timer("gpsUpdateTimer", true);
        gpsUpdateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                gpsTimerHandler.sendEmptyMessage(0);
            }
        }, gpsUpdateIntervalMs, gpsUpdateIntervalMs); // updateFilter gps data timeout 10 min
    }

    void startUpdateTimer() {
        stopUpdateTimer();
        updateTimer = new Timer("updateTimer", true);
        updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateTimerHandler.sendEmptyMessage(0);
            }
        }, 60000, 60000 * Integer.valueOf(PreferenceManager.
                getDefaultSharedPreferences(this).
                getString(getString(R.string.pref_key_interval), "5")));

    }

    void stopUpdateTimer() {
        if (updateTimer != null) {
            updateTimer.cancel();
            updateTimer.purge();
            updateTimer = null;
        }
    }


    void scheduleAlarmWatcher() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(this, OnAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, i, 0);
        try {
            am.cancel(pi);
        } catch (Exception e) {
            Log.e(TAG, "cancel pending intent of AlarmManager failed");
            e.getMessage();
        }

        Log.d(TAG, "Alarm watcher new updateInterval " + Integer.valueOf(PreferenceManager.
                getDefaultSharedPreferences(this).
                getString(getString(R.string.pref_key_interval), "5")));

        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + (3 * 1000), // 3 sec
                (60000 * Integer.valueOf(PreferenceManager.
                        getDefaultSharedPreferences(this).
                        getString(getString(R.string.pref_key_interval), "5"))),
                pi
        );
    }


    private class ApiListener implements NarodmonApi.onResultReceiveListener {
        /**
         * <p>Calls for result on sending address string (Russia, Moscow, Lenina 1) to server</p>
         *
         * @param ok
         * @param addr    - contain address (if lat/lng was sended), empty if address was sended
         * @param lat,lng - contain coordinates, if address string was sended
         */
        @Override
        public void onLocationResult(boolean ok, String addr, Float lat, Float lng) {
//		Log.d(TAG, "on location Result (server answer): " + addr);
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            // if use gps save address
            if (ok && !pref.getBoolean(getString(R.string.pref_key_use_geocode), false)) {
                Log.d(TAG, "on location result: we use gps, so sawe address string to shared pref: " + addr);
                pref.edit().putString(getString(R.string.pref_key_geoloc), addr).commit();
//			Toast.makeText(getApplicationContext(), addr, Toast.LENGTH_SHORT);
            } else if (ok) { // if use address, save coordinates and set it to api
                Log.d(TAG, "on location result: we use addres, so save coordinates to shared pref: " + lat + ", " + lng);
                pref.edit().putFloat("lat", lat).putFloat("lng", lng).commit();
                mNarodmonApi.setLocation(lat, lng);
            }
            Log.d(TAG,"onLocationResult new list load..");
            getSensorsList(deviceRequestLimit);
        }

        @Override
        public void onAuthorisationResult(boolean ok, String res) {
            if (ok) {
                if (res.equals("")) {
                    loginStatus = LoginStatus.LOGOUT;
                    loginDialog.updateLoginStatus();
                } else {
                    Log.d(TAG, "authorisation: ok, result:" + res);
                    loginStatus = LoginStatus.LOGIN;
                    loginDialog.updateLoginStatus();
                }
            } else {
                Log.e(TAG, "authorisation: fail, result: " + res);
                loginStatus = LoginStatus.ERROR;
                loginDialog.updateLoginStatus();
            }
            Log.d(TAG,"onAuthorisationResult new list load..");
            getSensorsList(deviceRequestLimit);
        }

        @Override
        public void onSendVersionResult(boolean ok, String res) {
        }

        @Override
        public void onSensorListResult(boolean ok, String res) {
            Log.d(TAG, "more ---------------- List updated --------------:" + sensorList.size() +", in adapter: "+ listAdapter.getAllCount());
            setRefreshProgress(false);
            if (!ok) {
                Toast.makeText(getApplicationContext(),"result: " + res,Toast.LENGTH_SHORT).show();
                //dontUpdateMore = true;
            }
            listAdapter.updateFilter();
            updateMenuSensorCounts();
        }

        @Override
        public void onSensorTypeResult(boolean ok, String res) {
            //parse res to container
            Log.d(TAG, "---------------- TypeDict updated --------------");
            if (!ok) return;
            SensorTypeProvider.getInstance(getApplicationContext()).setTypesFromString(res);
            listAdapter.notifyDataSetChanged();
        }

        @Override
        public void onInitResult(boolean ok, String res) {

        }

        @Override
        public void onDeviceSensorList(boolean ok, ArrayList<Sensor> list) {
            if (list != null) {
                Log.d(TAG, "receive devices list: " + list);
                for (Sensor newSensor : list) {
                    boolean notExist = true;
                    for (Sensor s : sensorList) {
                        if (s.id == newSensor.id) {
                            notExist = false;
                            s.distance = newSensor.distance;
                            s.location = newSensor.location;
                            s.name = newSensor.name;
//                            s.my = newSensor.my;
                            s.type = newSensor.type;
                            s.time = newSensor.time;
                            s.value = newSensor.value;
                            break;
                        }
                    }
                    if (notExist)
                        sensorList.add(newSensor);
                }
            } else {
                Log.e(TAG,"devices list return empty list!");
            }
            Log.d(TAG, "devices for request: " + additionalSensors);
            if (!additionalSensors.isEmpty()) {
                Integer s = additionalSensors.get(0);
                mNarodmonApi.getSensorsByDevice(s);
                additionalSensors.remove(s);
                Log.d(TAG, "devices sensor obtain: list not empty, get " + s);
            } else {
                mNarodmonApi.saveList(sensorList, getApplicationContext());
            }
        }
    }

    private class UpdateLocationListener extends MyLocation.LocationResult {
        @Override
        public void gotLocation(Location location) {
            if (location == null) return;
            Log.d(TAG, "got location: " + location.getLatitude() + ", " + location.getLongitude());
            final double lat = location.getLatitude();
            final double lon = location.getLongitude();
            // use API to send location
            Log.d(TAG, "location was updated and set into api : " + lat + " " + lon);
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            pref.edit().putFloat("lat", (float) lat).putFloat("lng", (float) lon).commit();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "location: update sensor list, load..!");
                    mNarodmonApi.setLocation((float) lat, (float) lon);
                    getSensorsList(deviceRequestLimit);
                }
            });
        }
    }
    @Override
    public void menuAllClicked() {
        listAdapter.setGroups(SensorItemAdapter.SensorGroups.All);
        setTitle(getString(R.string.menu_all_text));
        if (mDrawerLayout != null)
            mDrawerLayout.closeDrawer(mDrawerMenu);
        allMenuSelected = true;
        sensorListFragment.setEmptyMessage(getString(R.string.empty_sensor_list));
        sensorListFragment.showMoreButton(true);
    }

    @Override
    public void menuWatchedClicked() {
        listAdapter.setGroups(SensorItemAdapter.SensorGroups.Watched);
        setTitle(getString(R.string.menu_watched_text));
        sensorListFragment.setEmptyMessage(getString(R.string.empty_watched_msg));
        if (mDrawerLayout != null)
            mDrawerLayout.closeDrawer(mDrawerMenu);
        allMenuSelected = false;
        sensorListFragment.showMoreButton(false);
    }

    @Override
    public void menuMyClicked() {
        listAdapter.setGroups(SensorItemAdapter.SensorGroups.My);
        sensorListFragment.setEmptyMessage(getString(R.string.empty_my_list));
        setTitle(getString(R.string.menu_my_text));
        if (mDrawerLayout != null)
            mDrawerLayout.closeDrawer(mDrawerMenu);
        allMenuSelected = false;
        sensorListFragment.showMoreButton(false);
    }

    @Override
    public void menuAlarmClicked() {
        if (mDrawerLayout != null)
            mDrawerLayout.closeDrawer(mDrawerMenu);
        listAdapter.setGroups(SensorItemAdapter.SensorGroups.Alarmed);
        sensorListFragment.setEmptyMessage(getString(R.string.empty_alarm_list));
        setTitle(getString(R.string.menu_alarm_text));
        allMenuSelected = false;
        sensorListFragment.showMoreButton(false);
    }
}

