package com.stepahnieqianwang.upalarm;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;

//for HTTP request
import java.io.IOException;
import java.util.List;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import android.os.AsyncTask;

//get Date and time
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * We use
 * {@link com.google.android.gms.location.ActivityRecognitionApi} to recognize a user's current
 * activity, such as walking, driving, or standing still. It uses an
 * {@link android.app.IntentService} to broadcast detected activities through a
 * {@link BroadcastReceiver}. See the {@link DetectedActivity} class for a list of DetectedActivity
 * types.
 * <p/>
 * Note that this activity implements
 * {@link ResultCallback<R extends com.google.android.gms.common.api.Result>}.
 * Requesting activity detection updates using
 * {@link com.google.android.gms.location.ActivityRecognitionApi#requestActivityUpdates}
 * and stopping updates using
 * {@link com.google.android.gms.location.ActivityRecognitionApi#removeActivityUpdates}
 * returns a {@link com.google.android.gms.common.api.PendingResult}, whose result
 * object is processed by the {@code onResult} callback.
 */
public class MainActivity extends ActionBarActivity implements
        ConnectionCallbacks, OnConnectionFailedListener, ResultCallback<Status> {

    protected static final String TAG = "UpAlarm";

    /**
     * A receiver for DetectedActivity objects broadcast by the
     * {@code ActivityDetectionIntentService}.
     */
    protected ActivityDetectionBroadcastReceiver mBroadcastReceiver;

    /**
     * Provides the entry point to Google Play services.
     */
    protected GoogleApiClient mGoogleApiClient;

    /**
     * Used when requesting or removing activity detection updates.
     */
    private PendingIntent mActivityDetectionPendingIntent;

    // UI elements.
    private Button mRequestActivityUpdatesButton;
    private Button mRemoveActivityUpdatesButton;
    private ListView mDetectedActivitiesListView;

    /**
     * Adapter backed by a list of DetectedActivity objects.
     */
    private DetectedActivitiesAdapter mAdapter;

    /**
     * The DetectedActivities that we track in this sample. We use this for initializing the
     * {@code DetectedActivitiesAdapter}. We also use this for persisting state in
     * {@code onSaveInstanceState()} and restoring it in {@code onCreate()}. This ensures that each
     * activity is displayed with the correct confidence level upon orientation changes.
     */
    private ArrayList<DetectedActivity> mDetectedActivities;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get the UI widgets.
        mRequestActivityUpdatesButton = (Button) findViewById(R.id.request_activity_updates_button);
        mRemoveActivityUpdatesButton = (Button) findViewById(R.id.remove_activity_updates_button);
        mDetectedActivitiesListView = (ListView) findViewById(R.id.detected_activities_listview);

        // Get a receiver for broadcasts from ActivityDetectionIntentService.
        mBroadcastReceiver = new ActivityDetectionBroadcastReceiver();

        // get androidID
        //TextView textview_android_id = (TextView) findViewById(R.id.android_id);
        Constants.androidID = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
        //textview_android_id.setText(androidID);

        new RetrieveAllAsyncTask().execute();
        new RetrieveIndAsyncTask().execute();

        Button button_history;
        Button button_actkarma;
        Button button_community;

        //go to history page
        button_history = (Button)findViewById(R.id.button_history);

        button_history.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, History.class));
            }
        });

        //go to actkarma page
        button_actkarma = (Button)findViewById(R.id.button_actkarma);

        button_actkarma.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, Actkarma.class));
            }
        });

        //go to community page
        button_community = (Button)findViewById(R.id.button_community);

        button_community.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, Community.class));
            }
        });

        // Enable either the Request Updates button or the Remove Updates button depending on
        // whether activity updates have been requested.
        setButtonsEnabledState();

        // Reuse the value of mDetectedActivities from the bundle if possible. This maintains state
        // across device orientation changes. If mDetectedActivities is not stored in the bundle,
        // populate it with DetectedActivity objects whose confidence is set to 0. Doing this
        // ensures that the bar graphs for only the most recently detected activities are
        // filled in.
        if (savedInstanceState != null && savedInstanceState.containsKey(
                Constants.DETECTED_ACTIVITIES)) {
            mDetectedActivities = (ArrayList<DetectedActivity>) savedInstanceState.getSerializable(
                    Constants.DETECTED_ACTIVITIES);
        } else {
            mDetectedActivities = new ArrayList<DetectedActivity>();

            // Set the confidence level of each monitored activity to zero.
            for (int i = 0; i < Constants.MONITORED_ACTIVITIES.length; i++) {
                mDetectedActivities.add(new DetectedActivity(Constants.MONITORED_ACTIVITIES[i], 0));
            }
        }

        // Bind the adapter to the ListView responsible for display data for detected activities.
        mAdapter = new DetectedActivitiesAdapter(this, mDetectedActivities);
        mDetectedActivitiesListView.setAdapter(mAdapter);

        // Kick off the request to build GoogleApiClient.
        buildGoogleApiClient();
    }

    //HTTP request code start

    //end HTTP request code

    /**
     * Builds a GoogleApiClient. Uses the {@code #addApi} method to request the
     * ActivityRecognition API.
     */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(ActivityRecognition.API)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();

    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register the broadcast receiver that informs this activity of the DetectedActivity
        // object broadcast sent by the intent service.
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver,
                new IntentFilter(Constants.BROADCAST_ACTION));
    }

    @Override
    protected void onPause() {
        // Unregister the broadcast receiver that was registered during onResume().
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        super.onPause();
    }

    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "Connected to GoogleApiClient");
        Constants.lastLoc = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    /**
     * Registers for activity recognition updates using
     * {@link com.google.android.gms.location.ActivityRecognitionApi#requestActivityUpdates} which
     * returns a {@link com.google.android.gms.common.api.PendingResult}. Since this activity
     * implements the PendingResult interface, the activity itself receives the callback, and the
     * code within {@code onResult} executes. Note: once {@code requestActivityUpdates()} completes
     * successfully, the {@code DetectedActivitiesIntentService} starts receiving callbacks when
     * activities are detected.
     */
    public void requestActivityUpdatesButtonHandler(View view) {
        if (!mGoogleApiClient.isConnected()) {
            Toast.makeText(this, getString(R.string.not_connected),
                    Toast.LENGTH_SHORT).show();
            return;
        }
       ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(
                mGoogleApiClient,
                Constants.DETECTION_INTERVAL_IN_MILLISECONDS,
                getActivityDetectionPendingIntent()
       ).setResultCallback(this);


    }

    /**
     * Removes activity recognition updates using
     * {@link com.google.android.gms.location.ActivityRecognitionApi#removeActivityUpdates} which
     * returns a {@link com.google.android.gms.common.api.PendingResult}. Since this activity
     * implements the PendingResult interface, the activity itself receives the callback, and the
     * code within {@code onResult} executes. Note: once {@code removeActivityUpdates()} completes
     * successfully, the {@code DetectedActivitiesIntentService} stops receiving callbacks about
     * detected activities.
     */
    public void removeActivityUpdatesButtonHandler(View view) {
        if (!mGoogleApiClient.isConnected()) {
            Toast.makeText(this, getString(R.string.not_connected), Toast.LENGTH_SHORT).show();
            return;
        }
        // Remove all activity updates for the PendingIntent that was used to request activity
        // updates.
        ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(
                mGoogleApiClient,
                getActivityDetectionPendingIntent()
        ).setResultCallback(this);
    }

    /**
     * Runs when the result of calling requestActivityUpdates() and removeActivityUpdates() becomes
     * available. Either method can complete successfully or with an error.
     *
     * @param status The Status returned through a PendingIntent when requestActivityUpdates()
     *               or removeActivityUpdates() are called.
     */
    public void onResult(Status status) {
        if (status.isSuccess()) {
            // Toggle the status of activity updates requested, and save in shared preferences.
            boolean requestingUpdates = !getUpdatesRequestedState();
            setUpdatesRequestedState(requestingUpdates);

            // Update the UI. Requesting activity updates enables the Remove Activity Updates
            // button, and removing activity updates enables the Add Activity Updates button.
            setButtonsEnabledState();

            Toast.makeText(
                    this,
                    getString(requestingUpdates ? R.string.activity_updates_added :
                            R.string.activity_updates_removed),
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            Log.e(TAG, "Error adding or removing activity detection: " + status.getStatusMessage());
        }
    }

    /**
     * Gets a PendingIntent to be sent for each activity detection.
     */
    private PendingIntent getActivityDetectionPendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (mActivityDetectionPendingIntent != null) {
            return mActivityDetectionPendingIntent;
        }
        Intent intent = new Intent(this, DetectedActivitiesIntentService.class);

        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // requestActivityUpdates() and removeActivityUpdates().
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Ensures that only one button is enabled at any time. The Request Activity Updates button is
     * enabled if the user hasn't yet requested activity updates. The Remove Activity Updates button
     * is enabled if the user has requested activity updates.
     */
    private void setButtonsEnabledState() {
        if (getUpdatesRequestedState()) {
            mRequestActivityUpdatesButton.setEnabled(false);
            mRemoveActivityUpdatesButton.setEnabled(true);
        } else {
            mRequestActivityUpdatesButton.setEnabled(true);
            mRemoveActivityUpdatesButton.setEnabled(false);
        }
    }

    /**
     * Retrieves a SharedPreference object used to store or read values in this app. If a
     * preferences file passed as the first argument to {@link #getSharedPreferences}
     * does not exist, it is created when {@link SharedPreferences.Editor} is used to commit
     * data.
     */
    private SharedPreferences getSharedPreferencesInstance() {
        return getSharedPreferences(Constants.SHARED_PREFERENCES_NAME, MODE_PRIVATE);
    }

    /**
     * Retrieves the boolean from SharedPreferences that tracks whether we are requesting activity
     * updates.
     */
    private boolean getUpdatesRequestedState() {
        return getSharedPreferencesInstance()
                .getBoolean(Constants.ACTIVITY_UPDATES_REQUESTED_KEY, false);
    }

    /**
     * Sets the boolean in SharedPreferences that tracks whether we are requesting activity
     * updates.
     */
    private void setUpdatesRequestedState(boolean requestingUpdates) {
        getSharedPreferencesInstance()
                .edit()
                .putBoolean(Constants.ACTIVITY_UPDATES_REQUESTED_KEY, requestingUpdates)
                .commit();
    }

    /**
     * Stores the list of detected activities in the Bundle.
     */
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putSerializable(Constants.DETECTED_ACTIVITIES, mDetectedActivities);
        super.onSaveInstanceState(savedInstanceState);
    }

    /**
     * Processes the list of freshly detected activities. Asks the adapter to update its list of
     * DetectedActivities with new {@code DetectedActivity} objects reflecting the latest detected
     * activities.
     */
    protected void updateDetectedActivitiesList(ArrayList<DetectedActivity> detectedActivities) {
        mAdapter.updateActivities(detectedActivities);
    }

    /**
     * Receiver for intents sent by DetectedActivitiesIntentService via a sendBroadcast().
     * Receives a list of one or more DetectedActivity objects associated with the current state of
     * the device.
     */
    public class ActivityDetectionBroadcastReceiver extends BroadcastReceiver {
        protected static final String TAG = "activity-detection-response-receiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            ArrayList<DetectedActivity> updatedActivities =
                    intent.getParcelableArrayListExtra(Constants.ACTIVITY_EXTRA);
            updateDetectedActivitiesList(updatedActivities);
        }
    }

    private void runOnPostExecute(String string) {
        Toast.makeText(this, string, Toast.LENGTH_SHORT).show();
    }

    private class MyTask extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... strings){
            try {
                retrieveData();
            } catch (NullPointerException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        private String retrieveData(){
            String id = Constants.androidID;

            try{
                // url where the data will be posted
                String postReceiverUrl = "http://52.11.244.12/lastMove.php";
                //Log.v(TAG, "postURL: " + postReceiverUrl);
                //Log.v(TAG, "ID is: " + Constants.androidID);

                // HttpClient
                HttpClient httpClient = new DefaultHttpClient();

                // post header
                HttpPost httpPost = new HttpPost(postReceiverUrl);

                // add your data
                List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
                nameValuePairs.add(new BasicNameValuePair("userID", Constants.androidID));
                httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

                // execute HTTP post request
                HttpResponse response = httpClient.execute(httpPost);
                HttpEntity resEntity = response.getEntity();

                if (resEntity != null) {
                    String responseStr = EntityUtils.toString(resEntity).trim();
                    Log.v(TAG, "Response: " +  responseStr);
                    return responseStr;
                    // you can add an if statement here and do other actions based on the response
                }
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return "0000-00-00 00:00:00";
        }

        protected void onPostExecute(String result){
            runOnPostExecute(result);
        }

    }

}