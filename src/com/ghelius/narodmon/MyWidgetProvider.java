package com.ghelius.narodmon;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MyWidgetProvider extends AppWidgetProvider {

	private static final String LOG = "narodmon-widgetProvider";

	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
	                     int[] appWidgetIds) {
		DatabaseHandler dbh = new DatabaseHandler(context);
		Log.w(LOG, "onUpdate WidgetProvider's method called with " + appWidgetIds.length + "widgets");
		// Build the intent to call the service
		Intent intent = new Intent(context, UpdateWidgetService.class);
		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new Integer[0]);
		// Update the widgets via the service
		context.startService(intent);
		dbh.close();
	}
	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		super.onDeleted(context, appWidgetIds);
		DatabaseHandler dbh = new DatabaseHandler(context);
		dbh.deleteWidgetByWidgetId(appWidgetIds[0]);
		dbh.close();
	}
//	@Override
//	public void onDisabled(Context context)	{}
}
