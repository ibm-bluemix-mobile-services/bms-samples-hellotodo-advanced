package com.ibm.hellotodoadvanced;


/**
 * Copyright 2016 IBM Corp. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.ibm.mobilefirstplatform.clientsdk.android.core.api.Request;
import com.ibm.mobilefirstplatform.clientsdk.android.core.api.BMSClient;
import com.ibm.mobilefirstplatform.clientsdk.android.core.api.Response;
import com.ibm.mobilefirstplatform.clientsdk.android.core.api.ResponseListener;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPush;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushException;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushNotificationListener;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushResponseListener;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPSimplePushNotification;
import com.ibm.mobilefirstplatform.clientsdk.android.security.api.AuthorizationManager;
import com.ibm.mobilefirstplatform.clientsdk.android.security.facebookauthentication.FacebookAuthenticationManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The {@code MainActivity} is the primary visual activity shown when the app is being interacted with.
 * The ResponseListener interface is implemented to handle Mobile Client Access authentication and related responses.
 */
public class MainActivity extends Activity implements ResponseListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSION_REQUEST_GET_ACCOUNTS = 0;

    private ListView mListView; // Main ListView
    private List<TodoItem> mTodoItemList; // The list of TodoItems
    private TodoItemAdapter mTodoItemAdapter; // Adapter for bridging the list of TodoItems with the ListView

    private SwipeRefreshLayout mSwipeLayout; // Swipe down refresh to update local app if backend has changed

    private BMSClient bmsClient; // Bluemix Mobile Services Client SDK

    private MFPPush push; // Push Client
    private MFPPushNotificationListener notificationListener; // Notification listener to handle push notifications sent to the application

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bmsClient = BMSClient.getInstance();
        try {
            //initialize SDK with IBM Bluemix application ID and route
            //You can find your backendRoute and backendGUID in the Mobile Options section on top of your Bluemix application dashboard
            //TODO: Please replace <APPLICATION_ROUTE> with a valid ApplicationRoute and <APPLICATION_ID> with a valid ApplicationId
            bmsClient.initialize(this, "<APPLICATION_ROUTE>", "<APPLICATION_ID>");
        } catch (MalformedURLException exception) {
            throw new RuntimeException(exception);
        }

        // Runtime Permission handling required for SDK 23+
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.GET_ACCOUNTS}, PERMISSION_REQUEST_GET_ACCOUNTS);
        }

        //initialize UI
        initListView();
        initSwipeRefresh();

        // initialize IBM Push Notifications SDK
        initPush();
    }

    /**
     * Initializes the main list view and sets long click listener for delete.
     * Note: the Node delete endpoint is protected by Mobile Client Access and can only be accessed with an authorization header from the Bluemix Mobile Services Client SDK.
     */
    private void initListView() {
        // Get MainActivity's ListView
        mListView = (ListView) findViewById(R.id.listView);

        // Init array to hold TodoItems
        mTodoItemList = new ArrayList<>();

        // Create and set ListView adapter for displaying TodoItems
        mTodoItemAdapter = new TodoItemAdapter(getBaseContext(), mTodoItemList);
        mListView.setAdapter(mTodoItemAdapter);

        // Set long click listener for deleting TodoItems
        mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {

                // Grab TodoItem to delete from current showing list
                TodoItem todoItem = mTodoItemList.get(position);

                // Grab TodoItem id number and append to the DELETE rest request using the Bluemix Mobile Services Client SDK
                String todoId = Integer.toString(todoItem.idNumber);
                Request request = new Request(bmsClient.getBluemixAppRoute() + "/api/Items/" + todoId, Request.DELETE);

                // Send the request and use the response listener to react
                request.send(getApplicationContext(), new ResponseListener() {
                    // Update the list if successful
                    @Override
                    public void onSuccess(Response response) {
                        Log.i(TAG, "Item  deleted successfully");

                        loadList();
                    }

                    // If the request fails, log errors
                    @Override
                    public void onFailure(Response response, Throwable throwable, JSONObject extendedInfo) {

                        String errorMessage = "";

                        // different responses can cause different parameters to be null, be sure to check for those cases
                        if (response != null) {
                            errorMessage += response.toString() + "\n";
                        }

                        if (throwable != null) {
                            StringWriter sw = new StringWriter();
                            PrintWriter pw = new PrintWriter(sw);
                            throwable.printStackTrace(pw);
                            errorMessage += "THROWN" + sw.toString() + "\n";
                        }

                        if (extendedInfo != null){
                            errorMessage += "EXTENDED_INFO" + extendedInfo.toString() + "\n";
                        }

                        if (errorMessage.isEmpty())
                            errorMessage = "Request Failed With Unknown Error.";

                        Log.e(TAG, "deleteItem failed with error: " + errorMessage);


                    }
                });

                return true;
            }
        });
    }

    /**
     * Enables swipe down refresh for the list
     */
    private void initSwipeRefresh() {

        mSwipeLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);

        // Set swipe refresh listener to update the local list on pull down
        mSwipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadList();
            }
        });
    }

    /**
     * Initializes the IBM Push Notifications SDK and creates notification listener to handle incoming push notifications.
     */
    private void initPush(){
        // Initialize Push client using this activity as the context
        push = MFPPush.getInstance();
        push.initialize(this);

        // Create notification listener and enable pop up alert notification when a message is received
        // Note: You may see some errors in the logs on notification receipt indicating missing values. These are non-fatal and can be ignored.
        notificationListener = new MFPPushNotificationListener() {
            @Override
            public void onReceive(final MFPSimplePushNotification message) {
                // The entire message is printed in the log for your understanding
                Log.i(TAG, "Received a Push Notification: " + message.toString());
                runOnUiThread(new Runnable() {
                    public void run() {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Received a Push Notification")
                                .setMessage(message.getAlert())
                                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        // Make sure most up to date cloud data is displayed when notification is dismissed.
                                        loadList();
                                    }
                                })
                                .show();
                    }
                });
            }
        };
    }

    // Necessary override for Runtime Permission Handling required for SDK 23+
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_GET_ACCOUNTS: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Permisssions for Android 23+ fully enabled");

                } else {
                    Log.e(TAG, "Unable to authorize without full permissions. \nPlease retry with permissions enabled.");
                }
                return;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Calling the auth initialization code in onResume ensures that authentication is required whenever the app enters the foreground
        initAuth();
    }

    /**
     * Handles configuring and starting authentication.
     * If Facebook auth is configured properly, a public FB login will be required before the authorization header can be obtained
     */
    private void initAuth(){
        // Register this activity to handle Facebook auth response using the ResponseListener interface
        FacebookAuthenticationManager.getInstance().register(this);

        // Obtaining an authorization header kicks off the Facebook login process. If successful, the onSuccess() function is called and the authorization header is cached to be used on outbound requests.
        // Note: if no auth is configured in the Bluemix MCA instance, this auth will succeed automatically since it only checks that the request is coming from a Bluemix Mobile Services core SDK.
        AuthorizationManager.getInstance().obtainAuthorizationHeader(this, this);
    }

    /**
     * Handles successful authentication against MCA. If facebook auth is required, this will be called upon successful login.
     * @param response HTTP response object from MCA.
     */
    @Override
    public void onSuccess(Response response) {
        Log.i(TAG, "Successfully authenticated against MCA: " + response.getResponseText());

        // Register for push notifications and show data now that the user is authenticated
        registerForPush();
        loadList();
    }

    /**
     * Registers device for push notifications and, if successful, the IBM Push Notifications SDK begins listening to the notification listener (created in initPush) to handle incoming push notifications.
     */
    private void registerForPush(){
        Log.i(TAG, "Registering for push notifications");

        // Creates response listener to handle the response when a device is registered.
        MFPPushResponseListener registrationResponselistener = new MFPPushResponseListener<String>() {
            @Override
            public void onSuccess(String response) {
                Log.i(TAG, "Successfully registered for push notifications, String: " + response);
                // Begin listening
                push.listen(notificationListener);
            }

            @Override
            public void onFailure(MFPPushException exception) {
                Log.e(TAG,"Error registering for push notifications: " + exception.getErrorMessage());
                // Set null on failure so the sdk does not need to hold notifications
                push = null;
            }
        };

        // Attempt to register device using response listener created above
        push.register(registrationResponselistener);
    }

    /**
     * Uses Bluemix Mobile Services SDK to GET the TodoItems from Bluemix and updates the local list.
     */
    private void loadList() {

        // Send GET Request to Bluemix backend to retreive item list with response listener
        Request request = new Request(bmsClient.getBluemixAppRoute() + "/api/Items", Request.GET);
        request.send(getApplicationContext(), new ResponseListener() {
            // Loop through JSON response and create local TodoItems if successful
            @Override
            public void onSuccess(Response response) {
                if (response.getStatus() != 200) {
                    Log.e(TAG, "Error pulling items from Bluemix: " + response.toString());
                } else {

                    try {

                        mTodoItemList.clear();

                        JSONArray jsonArray = new JSONArray(response.getResponseText());

                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject tempTodoJSON = jsonArray.getJSONObject(i);
                            TodoItem tempTodo = new TodoItem();

                            tempTodo.idNumber = tempTodoJSON.getInt("id");
                            tempTodo.text = tempTodoJSON.getString("text");
                            tempTodo.isDone = tempTodoJSON.getBoolean("isDone");

                            mTodoItemList.add(tempTodo);
                        }

                        // Need to update adapter on main thread in order for list changes to update visually
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mTodoItemAdapter.notifyDataSetChanged();

                                Log.i(TAG, "List updated successfully");

                                if (mSwipeLayout.isRefreshing()) {
                                    mSwipeLayout.setRefreshing(false);
                                }
                            }
                        });

                    } catch (Exception exception) {
                        Log.e(TAG, "Error reading response JSON: " + exception.getLocalizedMessage());
                    }
                }
            }

            // Log Errors on failure
            @Override
            public void onFailure(Response response, Throwable throwable, JSONObject extendedInfo) {
                String errorMessage = "";

                if (response != null) {
                    errorMessage += response.toString() + "\n";
                }

                if (throwable != null) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    throwable.printStackTrace(pw);
                    errorMessage += "THROWN" + sw.toString() + "\n";
                }

                if (extendedInfo != null){
                    errorMessage += "EXTENDED_INFO" + extendedInfo.toString() + "\n";
                }

                if (errorMessage.isEmpty())
                    errorMessage = "Request Failed With Unknown Error.";

                Log.e(TAG, "loadList failed with error: " + errorMessage);
            }
        });

    }

    /**
     * Handles response from Bluemix MCA, kicks off the Facebook login intent, and routes appropriately, this should always be the same depending on the form of auth (Facebook auth manager vs Google auth manager etc...).
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        FacebookAuthenticationManager.getInstance().onActivityResultCalled(requestCode, resultCode, data);
    }

    /**
     * Clears list data, if any, when authentication against MCA fails and logs errors/response.
     * @param response HTTP response object from MCA
     */
    @Override
    public void onFailure(Response response, Throwable throwable, JSONObject extendedInfo) {

        if(!mTodoItemAdapter.isEmpty()){
            Log.i(TAG, "clearing list data since authentication failed");
            mTodoItemList.clear();
            mTodoItemAdapter.notifyDataSetChanged();
        }

        String errorMessage = "";

        // Check for 404s and unknown host exception since this is the first request made by the app
        if (response != null) {
            if (response.getStatus() == 404) {
                errorMessage += "Application Route not found at:\n" + BMSClient.getInstance().getBluemixAppRoute() +
                        "\nPlease verify your Application Route and rebuild the app.";
            } else {
                errorMessage += response.toString() + "\n";
            }
        }

        // Be sure to check for null pointers, any of the above parameters may be null depending on the failure.
        if (throwable != null) {
            if (throwable.getClass().equals(UnknownHostException.class)) {
                errorMessage = "Unable to access Bluemix host!\nPlease verify internet connectivity and try again.";
            } else {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                throwable.printStackTrace(pw);
                errorMessage += "THROWN" + sw.toString() + "\n";
            }
        }

        if (extendedInfo != null){
            errorMessage += "EXTENDED_INFO" + extendedInfo.toString() + "\n";
        }

        if (errorMessage.isEmpty())
            errorMessage = "Request Failed With Unknown Error.";

        Log.e(TAG, "Failed to authenticate against MCA: " + errorMessage);

    }

    /**
     * Launches a dialog for adding a new TodoItem. Called when plus button is tapped.
     *
     * @param view The plus button that is tapped.
     */
    public void addTodo(View view) {

        final Dialog addDialog = new Dialog(this);

        // UI settings for dialog pop-up
        addDialog.setContentView(R.layout.add_edit_dialog);
        addDialog.setTitle("Add Todo");
        TextView textView = (TextView) addDialog.findViewById(android.R.id.title);
        if (textView != null) {
            textView.setGravity(Gravity.CENTER);
        }
        addDialog.setCancelable(true);
        Button add = (Button) addDialog.findViewById(R.id.Add);
        addDialog.show();

        // When done is pressed, send POST request to create TodoItem on Bluemix
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditText itemToAdd = (EditText) addDialog.findViewById(R.id.todo);
                final String name = itemToAdd.getText().toString();
                // If text was added, continue with normal operations
                if (!name.isEmpty()) {

                    // Create JSON for new TodoItem, id should be 0 for new items
                    String json = "{\"text\":\"" + name + "\",\"isDone\":false,\"id\":0}";

                    // Create POST request with the Bluemix Mobile Services SDK and set HTTP headers so Bluemix knows what to expect in the request
                    Request request = new Request(bmsClient.getBluemixAppRoute() + "/api/Items", Request.POST);

                    HashMap headers = new HashMap();
                    List<String> contentType = new ArrayList<>();
                    contentType.add("application/json");
                    List<String> accept = new ArrayList<>();
                    accept.add("Application/json");

                    headers.put("Content-Type", contentType);
                    headers.put("Accept", accept);

                    request.setHeaders(headers);

                    request.send(getApplicationContext(), json, new ResponseListener() {
                        // On success, update local list with new TodoItem
                        @Override
                        public void onSuccess(Response response) {
                            Log.i(TAG, "Item " + name + " created successfully");

                            loadList();
                        }

                        // On failure, log errors
                        @Override
                        public void onFailure(Response response, Throwable throwable, JSONObject extendedInfo) {
                            String errorMessage = "";

                            if (response != null) {
                                errorMessage += response.toString() + "\n";
                            }

                            if (throwable != null) {
                                StringWriter sw = new StringWriter();
                                PrintWriter pw = new PrintWriter(sw);
                                throwable.printStackTrace(pw);
                                errorMessage += "THROWN" + sw.toString() + "\n";
                            }

                            if (extendedInfo != null){
                                errorMessage += "EXTENDED_INFO" + extendedInfo.toString() + "\n";
                            }

                            if (errorMessage.isEmpty())
                                errorMessage = "Request Failed With Unknown Error.";

                            Log.e(TAG, "addTodo failed with error: " + errorMessage);
                        }
                    });
                }

                // Close dialog when finished, or if no text was added
                addDialog.dismiss();
            }
        });
    }

    /**
     * Launches a dialog for updating the TodoItem name. Called when the list item is tapped.
     *
     * @param view The TodoItem that is tapped.
     */
    public void editTodoName(View view) {
        // Gets position in list view of tapped item
        final Integer position = mListView.getPositionForView(view);
        final Dialog editDialog = new Dialog(this);

        // UI settings for dialog pop-up
        editDialog.setContentView(R.layout.add_edit_dialog);
        editDialog.setTitle("Edit Todo");
        TextView textView = (TextView) editDialog.findViewById(android.R.id.title);
        if (textView != null) {
            textView.setGravity(Gravity.CENTER);
        }
        editDialog.setCancelable(true);
        EditText et = (EditText) editDialog.findViewById(R.id.todo);

        // Get selected TodoItem values
        final String name = mTodoItemList.get(position).text;
        final boolean isDone = mTodoItemList.get(position).isDone;
        final int id = mTodoItemList.get(position).idNumber;
        et.setText(name);

        Button editDone = (Button) editDialog.findViewById(R.id.Add);
        editDialog.show();

        // When done is pressed, send PUT request to update TodoItem on Bluemix
        editDone.setOnClickListener(new View.OnClickListener() {
            // Save text inputted when done is tapped
            @Override
            public void onClick(View view) {
                EditText editedText = (EditText) editDialog.findViewById(R.id.todo);

                final String updatedName = editedText.getText().toString();

                // If new text is not empty, create JSON with updated info and send PUT request
                if (!updatedName.isEmpty()) {
                    String json = "{\"text\":\"" + updatedName + "\",\"isDone\":" + isDone + ",\"id\":" + id + "}";

                    // Create PUT REST request using Bluemix Mobile Services SDK and set HTTP headers so Bluemix knows what to expect in the request
                    Request request = new Request(bmsClient.getBluemixAppRoute() + "/api/Items", Request.PUT);

                    HashMap headers = new HashMap();
                    List<String> contentType = new ArrayList<>();
                    contentType.add("application/json");
                    List<String> accept = new ArrayList<>();
                    accept.add("Application/json");

                    headers.put("Content-Type", contentType);
                    headers.put("Accept", accept);

                    request.setHeaders(headers);

                    request.send(getApplicationContext(), json, new ResponseListener() {
                        // On success, update local list with updated TodoItem
                        @Override
                        public void onSuccess(Response response) {
                            Log.i(TAG, "Item " + updatedName + " updated successfully");

                            loadList();
                        }

                        // On failure, log errors
                        @Override
                        public void onFailure(Response response, Throwable throwable, JSONObject extendedInfo) {
                            String errorMessage = "";

                            if (response != null) {
                                errorMessage += response.toString() + "\n";
                            }

                            if (throwable != null) {
                                StringWriter sw = new StringWriter();
                                PrintWriter pw = new PrintWriter(sw);
                                throwable.printStackTrace(pw);
                                errorMessage += "THROWN" + sw.toString() + "\n";
                            }

                            if (extendedInfo != null){
                                errorMessage += "EXTENDED_INFO" + extendedInfo.toString() + "\n";
                            }

                            if (errorMessage.isEmpty())
                                errorMessage = "Request Failed With Unknown Error.";

                            Log.e(TAG, "editTodoName failed with error: " + errorMessage);
                        }
                    });

                }
                editDialog.dismiss();
            }
        });
    }

    /**
     * When TodoItem image is tapped, switch boolean isDone value to indicate current completion status.
     * The TodoItem is updated on Bluemix and then the list is refreshed to reflect new status.
     * Calls notifyAllDevices if an item is successfully completed. Uses same REST request as editTodoName.
     *
     * @param view The TodoItem that has been tapped.
     */
    public void isDoneToggle(View view) {
        Integer position = mListView.getPositionForView(view);
        final TodoItem todoItem = mTodoItemList.get(position);

        final boolean isDone = !todoItem.isDone;

        String json = "{\"text\":\"" + todoItem.text + "\",\"isDone\":" + isDone + ",\"id\":" + todoItem.idNumber + "}";

        // Create PUT REST request using the Bluemix Mobile Services SDK and set HTTP headers so Bluemix knows what to expect in the request
        Request request = new Request(bmsClient.getBluemixAppRoute() + "/api/Items", Request.PUT);

        HashMap headers = new HashMap();

        List<String> contentType = new ArrayList<>();
        contentType.add("application/json");
        List<String> accept = new ArrayList<>();
        accept.add("Application/json");

        headers.put("Content-Type", contentType);
        headers.put("Accept", accept);

        request.setHeaders(headers);

        request.send(getApplicationContext(), json, new ResponseListener() {
            // On success, update local list with updated TodoItem, and call notifyAllDevices of marked complete.
            @Override
            public void onSuccess(Response response) {
                Log.i(TAG, todoItem.text + " completeness updated successfully");

                loadList();

                if (isDone) {
                    notifyAllDevices(todoItem.text);
                }
            }

            // On failure, log errors
            @Override
            public void onFailure(Response response, Throwable throwable, JSONObject extendedInfo) {
                String errorMessage = "";

                if (response != null) {
                    errorMessage += response.toString() + "\n";
                }

                if (throwable != null) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    throwable.printStackTrace(pw);
                    errorMessage += "THROWN" + sw.toString() + "\n";
                }

                if (extendedInfo != null){
                    errorMessage += "EXTENDED_INFO" + extendedInfo.toString() + "\n";
                }

                if (errorMessage.isEmpty())
                    errorMessage = "Request Failed With Unknown Error.";

                Log.e(TAG, "isDoneToggle failed with error: " + errorMessage);
            }
        });

    }

    /**
     * Formulates and sends REST request to the custom Node.js endpoint "<your_bluemix_route>/notifyAllDevices" deployed on Bluemix.
     * If configured correctly, expect an incoming push notification with the description of the completed item.
     * @param completedItem the task completed.
     */
    private void notifyAllDevices(String completedItem) {

        Request request = new Request(bmsClient.getBluemixAppRoute() + "/notifyAllDevices", Request.POST);

        String json = "{\"text\":\"" + completedItem + "\"}";

        HashMap headers = new HashMap();

        List<String> contentType = new ArrayList<>();
        contentType.add("application/json");
        List<String> accept = new ArrayList<>();
        accept.add("Application/json");

        headers.put("Content-Type", contentType);
        headers.put("Accept", accept);

        request.setHeaders(headers);

        request.send(getApplicationContext(), json, new ResponseListener() {
            @Override
            public void onSuccess(Response response) {
                Log.i(TAG, "All registered devices notified successfully: " + response.getResponseText());
            }

            // On failure, log errors
            @Override
            public void onFailure(Response response, Throwable throwable, JSONObject extendedInfo) {

                String errorMessage = "";

                if (response != null) {
                    errorMessage += response.toString() + "\n";
                }

                if (throwable != null) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    throwable.printStackTrace(pw);
                    errorMessage += "THROWN" + sw.toString() + "\n";
                }

                if (extendedInfo != null){
                    errorMessage += "EXTENDED_INFO" + extendedInfo.toString() + "\n";
                }

                if (errorMessage.isEmpty())
                    errorMessage = "Request Failed With Unknown Error.";

                Log.e(TAG, "notifyAllDevices failed with error: " + errorMessage);

            }
        });
    }

    /**
     * If the device has been registered successfully, hold push notifications when the app is paused.
     * Also, clear list data when the app leaves the foreground. This forces successful authentication to see cloud data when the app re-enters the foreground.
     * Note: As soon as push.listen(notificationListener) is called again, the notifications will be released for consumption.
     */
    @Override
    protected void onPause() {
        super.onPause();

        Log.i(TAG, "Clearing list data since the app is leaving the foreground");

        if(!mTodoItemAdapter.isEmpty()){
            mTodoItemList.clear();
            mTodoItemAdapter.notifyDataSetChanged();
        }

        // Holds notifications.
        if (push != null) {
            push.hold();
        }
    }
}


