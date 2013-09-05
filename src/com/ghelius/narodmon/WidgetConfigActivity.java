package com.ghelius.narodmon;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RemoteViews;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.commonsware.cwac.wakeful.WakefulIntentService;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;

public class WidgetConfigActivity extends SherlockFragmentActivity {
	private final static String TAG = "narodmon-widgetConfig";
	private int mAppWidgetId;
	private SensorItemAdapter adapter;
	private DatabaseHandler dbh;
	public void onCreate(Bundle savedInstanceState) {
		setTheme(com.actionbarsherlock.R.style.Theme_Sherlock);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.widget_config_activity);
		setTitle(getString(R.string.select_sensor_text));
		setResult(RESULT_CANCELED);
		Intent intent = getIntent();
		ListView list = (ListView) findViewById(R.id.listView);
		adapter = new SensorItemAdapter(getApplicationContext(), getSavedList());
		adapter.hideValue(true);
		adapter.update();
		dbh = new DatabaseHandler(getApplicationContext());

		list.setAdapter(adapter);
		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getApplicationContext());
				RemoteViews views = new RemoteViews(getApplicationContext().getPackageName(),R.layout.widget_layout);
				String name = ((EditText)findViewById(R.id.editName)).getText().toString();
				if (name.isEmpty()) {
						name = adapter.getItem(position).name;
				}
				// set up widget icon and name
				views.setTextViewText(R.id.name, name);
				views.setImageViewBitmap(R.id.imageView,((BitmapDrawable)SensorTypeProvider.getInstance(getApplicationContext()).getIcon(adapter.getItem(position).type)).getBitmap());
				views.setTextViewText(R.id.unit, SensorTypeProvider.getInstance(getApplicationContext()).getUnitForType(adapter.getItem(position).type));

				/* did it in watchService in updating */
//				// When we click the widget, we want to open our main activity.
//				Intent launchActivity = new Intent(getApplicationContext(), SensorInfo.class);
//				launchActivity.putExtra("sensorId", adapter.getItem(position).id);
//				PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, launchActivity, 0);
//				views.setOnClickPendingIntent(R.id.widget_body, pendingIntent);
//				appWidgetManager.updateAppWidget(mAppWidgetId, views);

				// save to db
				dbh.addWidget(new Widget(mAppWidgetId, adapter.getItem(position).id, name, adapter.getItem(position).type));
				dbh.close();

				// start watch service for update data
				WakefulIntentService.sendWakefulWork(getApplicationContext(), WatchService.class);

				// config done
				Intent resultValue = new Intent();
				resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
				setResult(RESULT_OK, resultValue);
				finish();
			}
		});

		Bundle extras = intent.getExtras();
		if (extras != null) {
			mAppWidgetId = extras.getInt(
					AppWidgetManager.EXTRA_APPWIDGET_ID,
					AppWidgetManager.INVALID_APPWIDGET_ID);
		}
	}

	ArrayList<Sensor> getSavedList () {
		final String fileName = "sensorList.obj";
			ArrayList<Sensor> sensorList = new ArrayList<Sensor>();
			Log.d(TAG, "------restore list start-------");
			FileInputStream fis;
			try {
				fis = getApplicationContext().openFileInput(fileName);
				ObjectInputStream is = new ObjectInputStream(fis);
				sensorList.addAll((ArrayList<Sensor>) is.readObject());
				is.close();
				fis.close();
				for (Sensor aSensorList : sensorList) aSensorList.value = "--";
				Log.d(TAG,"------restored list end------- " + sensorList.size());
			} catch (Exception e) {
				Log.e(TAG,"Can't read sensorList: " + e.getMessage());
			}
		return sensorList;
	}
}
