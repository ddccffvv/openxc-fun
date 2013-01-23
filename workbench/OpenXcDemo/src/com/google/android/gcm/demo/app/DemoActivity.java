/*
 * Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gcm.demo.app;

import static com.google.android.gcm.demo.app.CommonUtilities.DISPLAY_MESSAGE_ACTION;
import static com.google.android.gcm.demo.app.CommonUtilities.EXTRA_MESSAGE;
import static com.google.android.gcm.demo.app.CommonUtilities.SENDER_ID;
import static com.google.android.gcm.demo.app.CommonUtilities.SERVER_URL;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.android.gcm.GCMRegistrar;
import com.openxc.VehicleManager;
import com.openxc.measurements.HeadlampStatus;
import com.openxc.measurements.Latitude;
import com.openxc.measurements.Longitude;
import com.openxc.measurements.Measurement;
import com.openxc.measurements.UnrecognizedMeasurementTypeException;
import com.openxc.measurements.WindshieldWiperStatus;
import com.openxc.remote.VehicleServiceException;

/**
 * Main UI for the demo app.
 */
public class DemoActivity extends Activity {
	
	private WindshieldWiperStatus last_windshield_value = null;
	private HeadlampStatus last_headlamp_status = null;
	private LimitedQueue<Latitude> latitudes = new LimitedQueue<Latitude>();
	private LimitedQueue<Longitude> longitudes = new LimitedQueue<Longitude>();
	private boolean disasterSent = false;
	
	private boolean checkAndClearDisaster(){
		if(last_windshield_value==null)
			return false;
		if(last_headlamp_status==null)
			return false;
		boolean wipers = last_windshield_value.getValue().booleanValue();
		boolean lamp = last_headlamp_status.getValue().booleanValue();
		if(wipers && lamp && !disasterSent){
			disasterSent = true;
			return true;
		}else if(wipers && lamp){
			//disaster still ongoing, but already sent
			return false;
		}else{
			//one of them apparently is not enabled, no disaster anymore...
			disasterSent = false;
			return false;
		}
	}
	
    WindshieldWiperStatus.Listener mWiperListener =
            new WindshieldWiperStatus.Listener() {
        public void receive(Measurement measurement) {
            final WindshieldWiperStatus wiperStatus =
                (WindshieldWiperStatus) measurement;
            Log.w("bla", "measurement received");
            if (last_windshield_value==null || last_windshield_value!=wiperStatus){
            	// we send the new value to the server and update the last value
            	last_windshield_value = wiperStatus;
            	mHandler.post(new Runnable() {
            		public void run() {
            			Log.w("bla", "new value for windshield wipers...");
            			if(checkAndClearDisaster()){
            				sendTask = new AsyncTask<WindshieldWiperStatus, Void, Void>() {
            					@Override
            					protected Void doInBackground(WindshieldWiperStatus ... params){
            						ServerUtilities.sendMessage("id:1;disaster dude!");
            						return null;
            					}
            				};
            				sendTask.execute(wiperStatus, null, null);
            			}
            		}
            	});
            }
        }
    };
    
    HeadlampStatus.Listener mHeadlampStatus = new HeadlampStatus.Listener() {
        public void receive(Measurement measurement) {
        	final HeadlampStatus status = (HeadlampStatus) measurement;
        	if (last_headlamp_status==null || last_headlamp_status!=status){
        		last_headlamp_status = status;
        		mHandler.post(new Runnable() {
        			public void run() {
        				if(last_headlamp_status==null || last_headlamp_status!=status){
        					Log.w("bla", "new value for headlight");
        					//TODO
        					if(checkAndClearDisaster()){
        						mRegisterTask = new AsyncTask<Void, Void, Void>() {
        							@Override
        							protected Void doInBackground(Void ...params){
        								ServerUtilities.sendMessage("id:1;disaster dude!!");
        								return null;
        							}
        						};
        						mRegisterTask.execute(null,null,null);
        					}
        				}
        			}
        		});
        	}
        }
    };
    
    Latitude.Listener mLatitude =
            new Latitude.Listener() {
        public void receive(Measurement measurement) {
            final Latitude lat = (Latitude) measurement;
            mHandler.post(new Runnable() {
                public void run() {
                	Log.w("bla", "new value for latitude");
                    latitudes.add(lat);
                }
            });
        }
    };

    Longitude.Listener mLongitude =
            new Longitude.Listener() {
        public void receive(Measurement measurement) {
            final Longitude lng = (Longitude) measurement;
            mHandler.post(new Runnable() {
                public void run() {
                	Log.w("bla", "new value for longitude");
                    longitudes.add(lng);
                }
            });
        }
    };

    
    private final Handler mHandler = new Handler();

    private static String TAG = "VehicleDashboard";

    private VehicleManager mVehicleManager;
    private boolean mIsBound;
    
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            Log.i(TAG, "Bound to VehicleManager");
            mVehicleManager = ((VehicleManager.VehicleBinder)service
                    ).getService();

            try {
                
                mVehicleManager.addListener(WindshieldWiperStatus.class,
                        mWiperListener);
                mVehicleManager.addListener(HeadlampStatus.class,
                        mHeadlampStatus);
                mVehicleManager.addListener(Latitude.class,
                        mLatitude);
                mVehicleManager.addListener(Longitude.class,
                        mLongitude);
                
            } catch(VehicleServiceException e) {
                Log.w(TAG, "Couldn't add listeners for measurements", e);
            } catch(UnrecognizedMeasurementTypeException e) {
                Log.w(TAG, "Couldn't add listeners for measurements", e);
            }
            mIsBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.w(TAG, "VehicleService disconnected unexpectedly");
            mVehicleManager = null;
            mIsBound = false;
        }
    };

    TextView mDisplay;
    AsyncTask<Void, Void, Void> mRegisterTask;
    AsyncTask<WindshieldWiperStatus, Void, Void> sendTask;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkNotNull(SERVER_URL, "SERVER_URL");
        checkNotNull(SENDER_ID, "SENDER_ID");
        // Make sure the device has the proper dependencies.
        GCMRegistrar.checkDevice(this);
        // Make sure the manifest was properly set - comment out this line
        // while developing the app, then uncomment it when it's ready.
        GCMRegistrar.checkManifest(this);
        setContentView(R.layout.main);
        mDisplay = (TextView) findViewById(R.id.display);
        registerReceiver(mHandleMessageReceiver,
                new IntentFilter(DISPLAY_MESSAGE_ACTION));
        final String regId = GCMRegistrar.getRegistrationId(this);
        if (regId.equals("")) {
            // Automatically registers application on startup.
            GCMRegistrar.register(this, SENDER_ID);
            mRegisterTask = new AsyncTask<Void, Void, Void>() {
            	@Override
            	protected Void doInBackground(Void ... params){
            		ServerUtilities.sendMessage("registered");
            		return null;
            	}
            };
            mRegisterTask.execute(null, null, null);
            
        } else {
            // Device is already registered on GCM, check server.
            if (GCMRegistrar.isRegisteredOnServer(this)) {
                // Skips registration.
                mDisplay.append(getString(R.string.already_registered) + "\n");
                mRegisterTask = new AsyncTask<Void, Void, Void>() {
                	@Override
                	protected Void doInBackground(Void ... params){
                		ServerUtilities.sendMessage("skip registered");
                		return null;
                	}
                };
                mRegisterTask.execute(null, null, null);
            } else {
                // Try to register again, but not in the UI thread.
                // It's also necessary to cancel the thread onDestroy(),
                // hence the use of AsyncTask instead of a raw thread.
                final Context context = this;
                mRegisterTask = new AsyncTask<Void, Void, Void>() {

                    @Override
                    protected Void doInBackground(Void... params) {
                        boolean registered =
                                ServerUtilities.register(context, regId);
                        // At this point all attempts to register with the app
                        // server failed, so we need to unregister the device
                        // from GCM - the app will try to register again when
                        // it is restarted. Note that GCM will send an
                        // unregistered callback upon completion, but
                        // GCMIntentService.onUnregistered() will ignore it.
                        if (!registered) {
                            GCMRegistrar.unregister(context);
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void result) {
                        mRegisterTask = null;
                    }

                };
                mRegisterTask.execute(null, null, null);
            }
        }
    }
    @Override
    public void onResume() {
        super.onResume();
        bindService(new Intent(this, VehicleManager.class),
                mConnection, Context.BIND_AUTO_CREATE);

//        LocationManager locationManager = (LocationManager)
//            getSystemService(Context.LOCATION_SERVICE);
//        try {
//            locationManager.requestLocationUpdates(
//                    VehicleManager.VEHICLE_LOCATION_PROVIDER, 0, 0,
//                    mAndroidLocationListener);
//        } catch(IllegalArgumentException e) {
//            Log.w(TAG, "Vehicle location provider is unavailable");
//        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if(mIsBound) {
            Log.i(TAG, "Unbinding from vehicle service");
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            /*
             * Typically, an application registers automatically, so options
             * below are disabled. Uncomment them if you want to manually
             * register or unregister the device (you will also need to
             * uncomment the equivalent options on options_menu.xml).
             */
            /*
            case R.id.options_register:
                GCMRegistrar.register(this, SENDER_ID);
                return true;
            case R.id.options_unregister:
                GCMRegistrar.unregister(this);
                return true;
             */
            case R.id.options_clear:
                mDisplay.setText(null);
                return true;
            case R.id.options_exit:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onDestroy() {
        if (mRegisterTask != null) {
            mRegisterTask.cancel(true);
        }
        unregisterReceiver(mHandleMessageReceiver);
        GCMRegistrar.onDestroy(this);
        super.onDestroy();
    }

    private void checkNotNull(Object reference, String name) {
        if (reference == null) {
            throw new NullPointerException(
                    getString(R.string.error_config, name));
        }
    }

    private final BroadcastReceiver mHandleMessageReceiver =
            new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String newMessage = intent.getExtras().getString(EXTRA_MESSAGE);
            mDisplay.append(newMessage + "\n");
        }
    };

}
