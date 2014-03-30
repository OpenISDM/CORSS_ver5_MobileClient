/**
 ** Copyright (c) 2010 Ushahidi Inc
 ** All rights reserved
 ** Contact: team@ushahidi.com
 ** Website: http://www.ushahidi.com
 **
 ** GNU Lesser General Public License Usage
 ** This file may be used under the terms of the GNU Lesser
 ** General Public License version 3 as published by the Free Software
 ** Foundation and appearing in the file LICENSE.LGPL included in the
 ** packaging of this file. Please review the following information to
 ** ensure the GNU Lesser General Public License version 3 requirements
 ** will be met: http://www.gnu.org/licenses/lgpl.html.
 **
 **
 ** If you have questions regarding the use of this file, please contact
 ** Ushahidi developers at team@ushahidi.com.
 **
 **/

package com.ushahidi.android.app.ui.tablet;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.view.MenuItem;
import com.google.analytics.tracking.android.Log;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.ushahidi.android.app.Preferences;
import com.ushahidi.android.app.R;
import com.ushahidi.android.app.adapters.CategorySpinnerAdater;
import com.ushahidi.android.app.adapters.ListFetchedReportAdapter;
import com.ushahidi.android.app.adapters.PopupAdapter;
import com.ushahidi.android.app.api.CategoriesApi;
import com.ushahidi.android.app.api.ReportsApi;
import com.ushahidi.android.app.entities.PhotoEntity;
import com.ushahidi.android.app.entities.ReportEntity;
import com.ushahidi.android.app.fragments.BaseMapFragment;
import com.ushahidi.android.app.models.ListPhotoModel;
import com.ushahidi.android.app.models.ListReportModel;
import com.ushahidi.android.app.tasks.ProgressTask;
import com.ushahidi.android.app.ui.phone.AddReportActivity;
import com.ushahidi.android.app.ui.phone.ViewReportSlideActivity;
import com.ushahidi.android.app.util.ImageManager;
import com.ushahidi.android.app.util.Util;

public class MapFragment extends BaseMapFragment implements
		OnInfoWindowClickListener, ConnectionCallbacks, OnConnectionFailedListener, LocationListener{

	private ListReportModel mListReportModel;

	private List<ReportEntity> mReportModel;

	private Handler mHandler;

	private int filterCategory = 0;

	private MenuItem refresh;

	private CategorySpinnerAdater spinnerArrayAdapter;

	private boolean refreshState = false;

	private UpdatableMarker mMarker = createUpdatableMarker();

	public MapFragment() {
		super(R.menu.map_report);
	}
	
	
	/*add*/
	private Button volunteer;
	
	private LocationClient mLocationClient;
	
	private List<LatLng> _points = new ArrayList<LatLng>();
	
	private LatLng center = new LatLng(24.730870310199286, 121.76321268081665);
	
	private final LocationRequest REQUEST = LocationRequest.create()
		      .setInterval(5000)         // 5 seconds
		      .setFastestInterval(16)    // 16ms = 60fps
		      .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mListReportModel = new ListReportModel();
		mListReportModel.load();
		mReportModel = mListReportModel.getReports();
		showCategories();
		mHandler = new Handler();

		if (checkForGMap()) {
			map = ((SupportMapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();;
			Preferences.loadSettings(getActivity());

			initMap();
			map.setInfoWindowAdapter(new PopupAdapter(
					getLayoutInflater(savedInstanceState)));
			map.setOnInfoWindowClickListener(this);

		}
		setViewById();
		setListener();
		
	}
	
	private void initMap() {
		// set up the map tile use
		Util.setMapTile(getActivity(), map);
		map.getUiSettings().setMyLocationButtonEnabled(true);
		map.setMyLocationEnabled(true);
		map.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 13.0f));
		if (mReportModel.size() > 0) {
			setupMapCenter();
			mHandler.post(mMarkersOnMap);

		} else {
			toastLong(R.string.no_reports);
		}
	}

	@Override
	public void onInfoWindowClick(Marker marker) {
		if (marker.getTitle().equals("���a����")){
			return ;
		}
		if (mReportModel != null) {

			List<String> markers = mMarker.markersHolder;
			// FIX ME: Using the title to find which latlng have been tapped.
			// This ugly hack has to do with the limitation in Google maps api
			// for android. There is a
			// posibility of having the wront position returned in case there
			// are two or more of the same title.
			// SEE:https://code.google.com/p/gmaps-api-issues/issues/detail?id=4650
			final int position = markers.indexOf(marker.getTitle());
			if (markers != null && markers.size() > 0) {
				Toast.makeText(getActivity(), Integer.toString(position), 5).show();
				launchViewReport(position, "");
			}
		}

		if (marker.isInfoWindowShown())
			marker.hideInfoWindow();
	}

	private void launchViewReport(int position, final String filterCategory) {
		Intent i = new Intent(getActivity(), ViewReportSlideActivity.class);
		i.putExtra("id", position);
		if (filterCategory != null
				&& !filterCategory.equalsIgnoreCase(getActivity().getString(
						R.string.all_categories))) {
			i.putExtra("category", filterCategory);
		} else {
			i.putExtra("category", "");
		}
		getActivity().startActivityForResult(i, 0);
		getActivity().overridePendingTransition(R.anim.home_enter,
				R.anim.home_exit);

	}

	protected void setupMapCenter() {
		if (map != null) {
			final View mapView = getView();
			if (mapView != null) {
				if (mapView.getViewTreeObserver().isAlive()) {
					mapView.getViewTreeObserver().addOnGlobalLayoutListener(
							new OnGlobalLayoutListener() {
								@SuppressWarnings("deprecation")
								// We use the new method when supported
								@SuppressLint("NewApi")
								// We check which build version we are using.
								@Override
								public void onGlobalLayout() {

									LatLng latLng = getReportLatLng();

									if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
										mapView.getViewTreeObserver()
												.removeGlobalOnLayoutListener(
														this);
									} else {
										mapView.getViewTreeObserver()
												.removeOnGlobalLayoutListener(
														this);
									}
									if (latLng != null)
										map.moveCamera(CameraUpdateFactory
												.newLatLng(latLng));

								}
							});
				}
			}
		}
	}

	private LatLng getReportLatLng() {
		if (mReportModel != null) {
			LatLngBounds.Builder builder = new LatLngBounds.Builder();
			for (ReportEntity reportEntity : mReportModel) {
				double latitude = 0.0;
				double longitude = 0.0;
				try {
					latitude = Double.valueOf(reportEntity.getIncident()
							.getLatitude());
				} catch (NumberFormatException e) {
					latitude = 0.0;
				}

				try {
					longitude = Double.valueOf(reportEntity.getIncident()
							.getLongitude());
				} catch (NumberFormatException e) {
					longitude = 0.0;
				}

				builder.include(new LatLng(latitude, longitude));
			}
			return Util.getCenter(builder.build());
		}
		return null;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		if (item.getItemId() == R.id.menu_refresh) {
			refresh = item;
			new RefreshReports(getActivity()).execute((String) null);
			return true;
		} else if (item.getItemId() == R.id.menu_add) {
			launchAddReport();
			return true;
		} else if (item.getItemId() == R.id.menu_normal) {
			if (Preferences.mapTiles.equals("google")) {
				map.setMapType(GoogleMap.MAP_TYPE_NONE);
				map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
			}

			return true;
		} else if (item.getItemId() == R.id.menu_satellite) {
			if (Preferences.mapTiles.equals("google")) {
				map.setMapType(GoogleMap.MAP_TYPE_NONE);
				map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
			}

			return true;

		} else if (item.getItemId() == R.id.filter_by) {

			showDropDownNav();

			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	protected View headerView() {
		LayoutInflater inflater = getActivity().getLayoutInflater();
		ViewGroup viewGroup = (ViewGroup) inflater.inflate(
				R.layout.map_view_header, null, false);
		TextView textView = (TextView) viewGroup.findViewById(R.id.map_header);
		textView.setText(R.string.all_categories);
		return viewGroup;
	}

	// FIXME:: look into how to put this in it own class
	private void showDropDownNav() {
		showCategories();
		new AlertDialog.Builder(getActivity())
				.setTitle(getString(R.string.prompt_mesg))
				.setAdapter(spinnerArrayAdapter,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {

								filterCategory = spinnerArrayAdapter.getTag(
										which).getCategoryId();
								
								final String all = spinnerArrayAdapter.getTag(
										which).getCategoryTitle();
								if ((all != null)
										&& (!TextUtils.isEmpty(all))
										&& (all != getString(R.string.all_categories))) {
								    
								    mHandler.post(fetchReportListByCategory);

								} else {
								 
								    mHandler.post(fetchReportList);
									
								}

								dialog.dismiss();
							}
						}).create().show();
	}

	public void showCategories() {
		spinnerArrayAdapter = new CategorySpinnerAdater(getActivity());
		spinnerArrayAdapter.refresh();
	}

	/**
	 * refresh by category id
	 */
	final Runnable fetchReportListByCategory = new Runnable() {
		public void run() {
			try {
				final boolean loaded = mListReportModel
						.loadReportByCategory(filterCategory);
				if (loaded) {
					mReportModel = mListReportModel.getReports();
					log("Filter reports by category: "+mReportModel.size());
					populateMap();
				}
			} catch (Exception e) {
				return;
			}
		}
	};

	/**
	 * Refresh the list view with new items
	 */
	final Runnable fetchReportList = new Runnable() {
		public void run() {
			try {
				mListReportModel.load();
				mReportModel = mListReportModel.getReports();
				populateMap();
				showCategories();
			} catch (Exception e) {
				return;
			}
		}
	};

	private void updateRefreshStatus() {
		if (refresh != null) {
			if (refreshState)
				refresh.setActionView(R.layout.indeterminate_progress_action);
			else
				refresh.setActionView(null);
		}

	}

	private void setViewById(){
		volunteer = (Button) view.findViewById(R.id.volunteer);
	}
	
	private void setListener(){
		volunteer.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				new asyncTaskProgress().execute("23.7079219", "120.5521570");
			}
			
		});
	}
	
	
	
	public class asyncTaskProgress extends AsyncTask<String, Void, Void>{
	    	
	    String input[] = new String[2];
	    String message;
	   	ProgressDialog PDialog;
	    	
		@Override
		protected void onPostExecute(Void result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);
			
			GetDirection(new LatLng(Double.valueOf(input[0]), Double.valueOf(input[1])));
			message = "���|�W������w����";
			PDialog.dismiss();
			show_Dialog(message);
		}
		@Override
		protected void onPreExecute() {
			// TODO Auto-generated method stub
			super.onPreExecute();
			PDialog = PDialog.show(getActivity(), null, "�W�����|��...");
		}
	
		@Override
		protected Void doInBackground(String... params) {
			// TODO Auto-generated method stub
			try {
				for(int i = 0; i < params.length; i++)
					input[i] = params[i];
					Thread.sleep(3000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}
	}
	
	
	public class httpProgress extends AsyncTask<String, Void, Void>{
		
		StringBuilder builder;
		String result = null;
		
		@Override
		protected Void doInBackground(String... params) {
			// TODO Auto-generated method stub
			try{
				HttpClient httpClient = new DefaultHttpClient();
		        HttpPost httpPost = new HttpPost(params[0]);
		        ArrayList<NameValuePair> param = new ArrayList<NameValuePair>();
		        httpPost.setEntity(new UrlEncodedFormEntity(param, HTTP.UTF_8));
		        HttpResponse httpResponse = httpClient.execute(httpPost);
		        HttpEntity httpEntity = httpResponse.getEntity();
		        InputStream inputStream = httpEntity.getContent(); 
		        BufferedReader bufReader = new BufferedReader(new InputStreamReader(inputStream, "utf-8"), 8);
		        builder = new StringBuilder();
		        String line = null;
		        while((line = bufReader.readLine()) != null) {
		        	builder.append(line + "\n");
		        }
		
		        inputStream.close();
		        result = builder.toString();
		        JSONObject jsonObject = new JSONObject(result);
		        JSONArray routeObject = jsonObject.getJSONArray("routes");
		        String polyline = routeObject.getJSONObject(0).getJSONObject("overview_polyline").getString("points");
		
		        if (polyline.length() > 0){
		            decodePolylines(polyline);
		            publishProgress();
		        }
		        
			}catch(Exception e){
				Log.i(e.toString());
			}
			return null;
		}
		
		
		
		@Override
		protected void onProgressUpdate(Void... values) {
			// TODO Auto-generated method stub
			super.onProgressUpdate(values);
			Route();
		}

		/*
		 * ��u�ѽX�t��k�A�ѪRJSON����points
		 */
		private void decodePolylines(String poly){
		    int len = poly.length();
		    int index = 0;
		    double lat = 0;
		    double lng = 0;
		
		    while (index < len){
		        int b, shift = 0, result = 0;
		        do{
		            b = poly.charAt(index++) - 63;
		            result |= (b & 0x1f) << shift;
		            shift += 5;
		        } while (b >= 0x20);
		        int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
		        lat += dlat;
		
		        shift = 0;
		        result = 0;
		        do
		        {
		            b = poly.charAt(index++) - 63;
		            result |= (b & 0x1f) << shift;
		            shift += 5;
		        } while (b >= 0x20);
		        int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
		        lng += dlng;
		
		        LatLng p = new LatLng(lat / 1E5, lng / 1E5);
		        _points.add(p);
		    }
		}

		//��PolyLine�e�a�ϡA��ܸ��|
		public void Route(){
			Polyline line;
			for(int i = 1; i < _points.size(); i++){
				line = map.addPolyline(new PolylineOptions()
		        .add(_points.get(i-1), _points.get(i))
		        .width(8)
		        .color(Color.RED));
			}
		}
	}
	
	//�W�����|�A�N�I��iList��
	public List<LatLng> GetDirection(LatLng position){
	
		try {
			LatLng now = new LatLng(mLocationClient.getLastLocation().getLatitude(), mLocationClient.getLastLocation().getLongitude());
	        String route= "http://map.google.com/maps/api/directions/json?origin=" +
	           		now.latitude + "," + now.longitude +"&destination=" + position.latitude + "," + position.longitude + "&language=en&sensor=true";
	        
	        new httpProgress().execute(route);
	        map.addMarker(new MarkerOptions()
	           .position(position)
	           .snippet("Help Me~")
	            .icon(BitmapDescriptorFactory.fromResource(R.drawable.trapped))
	            .title("���a����"));
		} catch(Exception e) {  
	        Toast.makeText(getActivity(), "�W�����u���ѡA�Э��s����!", Toast.LENGTH_SHORT).show();
	    }
		
		return _points;
	}

	private void show_Dialog(String message){
		Builder dialog = new AlertDialog.Builder(getActivity());
	    
		dialog.setTitle("�W��")
	    .setMessage(message)
	    .setPositiveButton("�T�w", new DialogInterface.OnClickListener() {
	    	@Override
	    	public void onClick(DialogInterface dialog, int which) {
	    		
	    	}
	    });
		
		dialog.show();
	}
	
	/**
	 * Restart the receiving, when we are back on line.
	 */

	@Override
	public void onResume() {
		super.onResume();
		initMap();
		setUpLocationClientIfNeeded();
		mLocationClient.connect();
	}

	public void onDestroy() {
		super.onDestroy();
		if (new RefreshReports(getActivity()).cancel(true)) {
			refreshState = false;
			updateRefreshStatus();
		}
	}
	
	@Override
	public void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		if (mLocationClient != null) {
	    	mLocationClient.disconnect();
	    }
	}
	
	private void setUpLocationClientIfNeeded() {
        if (mLocationClient == null) {
          mLocationClient = new LocationClient(
              getActivity(),
              this,  // ConnectionCallbacks
              this); // OnConnectionFailedListener
        }
    }
	

	// put this stuff in a seperate thread
	final Runnable mMarkersOnMap = new Runnable() {
		public void run() {
			populateMap();
		}
	};

	/**
	 * add marker to the map
	 */
	public void populateMap() {

		if (mReportModel != null) {
			mMarker.clearMapMarkers();
			
			for (ReportEntity reportEntity : mReportModel) {
				double latitude = 0.0;
				double longitude = 0.0;
				try {
					latitude = Double.valueOf(reportEntity.getIncident()
							.getLatitude());
				} catch (NumberFormatException e) {
					latitude = 0.0;
				}

				try {
					longitude = Double.valueOf(reportEntity.getIncident()
							.getLongitude());
				} catch (NumberFormatException e) {
					longitude = 0.0;
				}
				final String description = Util.limitString(reportEntity
						.getIncident().getDescription(), 30);

				mMarker.addMarkerWithIcon(map, latitude, longitude,
						reportEntity.getIncident().getTitle(), description,
						reportEntity.getThumbnail());

			}
		}
	}

	public void launchAddReport() {
		Intent i = new Intent(getActivity(), AddReportActivity.class);
		i.putExtra("id", 0);
		startActivityForResult(i, 2);
		getActivity().overridePendingTransition(R.anim.home_enter,
				R.anim.home_exit);
	}

	private void deleteFetchedReport() {
		final List<ReportEntity> items = new ListFetchedReportAdapter(
				getActivity()).fetchedReports();
		for (ReportEntity report : items) {
			if (new ListReportModel().deleteAllFetchedReport(report
					.getIncident().getId())) {
				final List<PhotoEntity> photos = new ListPhotoModel()
						.getPhotosByReportId(report.getIncident().getId());

				for (PhotoEntity photo : photos) {
					ImageManager.deletePendingPhoto(getActivity(),
							"/" + photo.getPhoto());
				}
			}

		}

	}

	/**
	 * Refresh for new reports
	 */
	class RefreshReports extends ProgressTask {

		protected Integer status = 4; // there is no internet

		public RefreshReports(Activity activity) {
			super(activity, R.string.loading_);
			// pass custom loading message to super call
			refreshState = true;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			dialog.cancel();
			refreshState = true;
			updateRefreshStatus();
		}

		@Override
		protected Boolean doInBackground(String... strings) {
			try {
				// check if there is internet
				if (Util.isConnected(getActivity())) {
					// delete everything before updating with a new one
					deleteFetchedReport();

					// fetch categories -- assuming everything will go just
					// right!
					new CategoriesApi().getCategoriesList();

					status = new ReportsApi().saveReports(getActivity()) ? 0
							: 99;
				}

				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
				return false;
			}
			return true;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (result) {
				log("fetching ");
				if (status == 4) {
					toastLong(R.string.internet_connection);
				} else if (status == 110) {
					toastLong(R.string.connection_timeout);
				} else if (status == 100) {
					toastLong(R.string.could_not_fetch_reports);
				} else if (status == 0) {
					log("successfully fetched");
					mReportModel = mListReportModel.getReports();
					populateMap();
					showCategories();

				}
			}
			refreshState = false;
			updateRefreshStatus();
		}
	}


	@Override
	public void onLocationChanged(Location arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onConnectionFailed(ConnectionResult arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onConnected(Bundle arg0) {
		// TODO Auto-generated method stub
		mLocationClient.requestLocationUpdates(
		        REQUEST,
		        this);  // LocationListener
	}

	@Override
	public void onDisconnected() {
		// TODO Auto-generated method stub
		
	}

}