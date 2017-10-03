package nz.org.cacophony.cacophonometerlite;

import android.content.Context;
import android.os.Build;
import android.util.JsonReader;
import android.util.Log;
import android.widget.Toast;
//import android.util.Log;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.SyncHttpClient;
import org.apache.commons.lang3.RandomStringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import cz.msebera.android.httpclient.Header;
import info.guardianproject.netcipher.NetCipher;

import static android.R.attr.data;
import static android.R.attr.password;
import static android.R.id.message;
import static nz.org.cacophony.cacophonometerlite.R.id.messageText;

/**
 * This class deals with connecting to the server (test connection, Login, Register, upload recording).
 */

class Server {
    private static final String TAG = Server.class.getName();

    private static final String UPLOAD_AUDIO_API_URL = "/api/v1/audiorecordings";
    private static final String PING_URL = "/ping";
    private static final String LOGIN_URL = "/authenticate_device";
    private static final String REGISTER_URL = "/api/v1/devices";

    static boolean serverConnection = false;
    static boolean loggedIn = false;
    private static String token = null;
    private static String errorMessage = null;
    private static boolean uploading = false;
    private static boolean uploadSuccess = false;
//    private static Logger logger = null;
//    static
//    {
//        if (logger == null){
//            logger =  LoggerFactory.getLogger(LOG_TAG); // couldn't call getAndConfigureLogger as no context is currently available.  But this should work as Util.configureLogbackDirectly(context) should have been called by now.
//        }
//    }

    /**
     * Will ping server and try to login.
     *
     * @param context app context
     */
    static void updateServerConnectionStatus(Context context) {
//        logger.info("updateServerConnectionStatus method");
        try {
            Util.disableFlightMode(context);
            // Now wait for network connection as setFlightMode takes a while
            if (!Util.waitForNetworkConnection(context, true)) {

//        logger.error("Failed to disable airplane mode");
                Log.e(TAG, "Failed to disable airplane mode");
                return;
            }


// removed ping check as this feature has been removed from server
//    if (!ping(context)) {
////        logger.error("Could not connect to server");
//        Log.e(TAG,"Could not connect to server" );
//        Util.getToast(context, "Could not connect to server", true);
//    } else {
//        login(context);
//    }

            login(context);

//    Util.enableFlightMode(context);
        } catch (Exception ex) {
//    logger.error(ex.getLocalizedMessage());
            Log.e(TAG, ex.getLocalizedMessage());

        } finally {
            Util.broadcastAMessage(context, "enable_vitals_button");
            Util.broadcastAMessage(context, "enable_test_recording_button");
            Util.broadcastAMessage(context, "enable_setup_button");
        }
    }

    /**
     * Pings server. Can't be run on main thread as it does a synchronous http request.
     * Use runPing instead if on main thread or want it to be asynchronous.
     *
     * @return if got a response from server.
     */
//    private static boolean ping(Context context) {
//
//        if (!Util.isNetworkConnected(context)){
//            Util.disableFlightMode(context);
//        }
//
//        // Now wait for network connection as setFlightMode takes a while
//        if (!Util.waitForNetworkConnection(context, true)){
//            Log.e(TAG, "Failed to disable airplane mode");
////            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "Failed to disable airplane mode");
////            logger.error("Failed to disable airplane mode");
//            return false;
//        }
//
//        SyncHttpClient client = new SyncHttpClient();
//
//        Prefs prefs = new Prefs(context);
////        boolean useTestServer = prefs.getUseTestServer();
////        client.get(prefs.getServerUrl(useTestServer) + PING_URL, null, new AsyncHttpResponseHandler() {
//             client.get(prefs.getServerUrl() + PING_URL, null, new AsyncHttpResponseHandler() {
//
//            @Override
//            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
//                String responseString = new String(response);
//                serverConnection = (responseString.equals("pong..."));
//            }
//
//            @Override
//            public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
//                serverConnection = false;
//            }
//        });
//        return serverConnection;
//    }

    /**
     * Will login and save JSON Web Token. Can't be run on main/UI thread as it does a synchronous http request.
     *
     * @param context app context
     * @return if login was successful
     */
//    private static boolean login(Context context) {
    static boolean login(Context context) {

        Util.disableFlightMode(context);

        // Now wait for network connection as setFlightMode takes a while
        if (!Util.waitForNetworkConnection(context, true)) {
            Log.e(TAG, "Failed to disable airplane mode");
//            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "Failed to disable airplane mode");

//            logger.error("Failed to disable airplane mode");
            return false;
        }

        // Get credentials from shared preferences.
        Prefs prefs = new Prefs(context);
        String devicename = prefs.getDeviceName();
        String password = prefs.getPassword();
        String group = prefs.getGroupName();
        if (devicename == null || password == null || group == null) {

            // One or more credentials are null, so can not attempt to login.
            Log.e(TAG, "No credentials to login with.");
//            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "No credentials to login with.");
//            logger.error("No credentials to login with.");
            loggedIn = false;
            return false;
        }

        String loginUrl = prefs.getServerUrl(true) + LOGIN_URL;
        URL cacophonyRegisterEndpoint = null;
        try {
            cacophonyRegisterEndpoint = new URL(loginUrl);
            // Create connection
            HttpsURLConnection myConnection = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                //  HttpsURLConnection myConnection = (HttpsURLConnection) cacophonyRegisterEndpoint.openConnection();
                //https://stackoverflow.com/questions/26633349/disable-ssl-as-a-protocol-in-httpsurlconnection
                myConnection = NetCipher.getHttpsURLConnection(cacophonyRegisterEndpoint);
            } else {
                myConnection = (HttpsURLConnection) cacophonyRegisterEndpoint.openConnection();
            }

            myConnection.setRequestMethod("POST");
            myConnection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            myConnection.setRequestProperty("Accept", "application/json");
            myConnection.setDoOutput(true);
            myConnection.setDoInput(true);

            JSONObject jsonParam = new JSONObject();
            jsonParam.put("devicename", devicename);
            jsonParam.put("password", password);

            DataOutputStream os = new DataOutputStream(myConnection.getOutputStream());
            os.writeBytes(jsonParam.toString());
            os.flush();

            //  Here you read any answer from server.
            BufferedReader serverAnswer = new BufferedReader(new InputStreamReader(myConnection.getInputStream()));
            String responseLine;

            responseLine = serverAnswer.readLine();
            os.close();
            serverAnswer.close();

            Log.i("STATUS", String.valueOf(myConnection.getResponseCode()));
            String status = String.valueOf(myConnection.getResponseCode());
            Log.i("MSG", myConnection.getResponseMessage());
            String responseCode = String.valueOf(myConnection.getResponseCode());

            myConnection.disconnect();

            String responseString = new String(responseLine);

            JSONObject joRes = new JSONObject(responseString);
            if (joRes.getBoolean("success")) {
                Log.i(TAG, "Successful login.");
//                        logger.info("Login", "Successful login.");
                loggedIn = true;
                setToken(joRes.getString("token"));  // Save JWT (JSON Web Token)

            } else {
                // Failed register.
                loggedIn = false;
                setToken(null);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error with parsing register response into a JSON.");
        }


        return loggedIn;
    }

    /**
     * Will login and save JSON Web Token. Can't be run on main/UI thread as it does a synchronous http request.
     *
     * @param context app context
     * @return if login was successful
     */
//    private static boolean login(Context context) {
    static boolean loginOld(Context context) {

        Util.disableFlightMode(context);

        // Now wait for network connection as setFlightMode takes a while
        if (!Util.waitForNetworkConnection(context, true)) {
            Log.e(TAG, "Failed to disable airplane mode");
//            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "Failed to disable airplane mode");

//            logger.error("Failed to disable airplane mode");
            return false;
        }

        // Get credentials from shared preferences.
        Prefs prefs = new Prefs(context);
        String devicename = prefs.getDeviceName();
        String password = prefs.getPassword();
        String group = prefs.getGroupName();
        if (devicename == null || password == null || group == null) {

            // One or more credentials are null, so can not attempt to login.
            Log.e(TAG, "No credentials to login with.");
//            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "No credentials to login with.");
//            logger.error("No credentials to login with.");
            loggedIn = false;
            return false;
        }




        SyncHttpClient client = new SyncHttpClient();
        RequestParams params = new RequestParams();

        params.put("devicename", devicename);
        params.put("password", password);

//        boolean useTestServer = prefs.getUseTestServer();
//
//        client.post(prefs.getServerUrl(useTestServer) + LOGIN_URL, params, new AsyncHttpResponseHandler() {
        String url = prefs.getServerUrl(true) + LOGIN_URL;
//            client.post(prefs.getServerUrl() + LOGIN_URL, params, new AsyncHttpResponseHandler() {
        client.post(url, params, new AsyncHttpResponseHandler() {

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                String responseString = new String(response);
                try {
                    JSONObject joRes = new JSONObject(responseString);
                    if (joRes.getBoolean("success")) {
                        Log.i(TAG, "Successful login.");
//                        logger.info("Login", "Successful login.");
                        loggedIn = true;
                        setToken(joRes.getString("token"));  // Save JWT (JSON Web Token)

                    } else {
                        loggedIn = false;
                        setToken(null);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error with parsing register response into a JSON.");
//                    logger.error("Error with parsing register response into a JSON.");

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                if (headers != null) {
                    for (Header header : headers) {
                        Log.e(TAG, header.toString());
                    }
                } else {
                    Log.e(TAG, "headers is null");
                }
                if (errorResponse != null) {
                    String s = new String(errorResponse);
                    Log.e(TAG, s);
                } else {
                    Log.e(TAG, "errorResponse is null");
                }


                Log.e(TAG, e.getLocalizedMessage());

                if (statusCode == 401) {
                    loggedIn = false;
                    Log.e(TAG, "Invalid devicename or password for login.");
//                    logger.error("Invalid devicename or password for login.");
                } else {
                    loggedIn = false;
                }
                setToken(null);
            }
        });
        return loggedIn;
    }



    /**
     * Does a synchronous http request to register the device. Can't be run on main/UI thread.
     *
     * @param group   Name of group to register under.
     * @param context App context.
     * @return If the device successfully registered.
     */
    static boolean register(final String group, final Context context) {

        // Check that the group name is valid, at least 4 characters.
        if (group == null || group.length() < 4) {
            Log.i(TAG, "Invalid group name: " + group);
//            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "Invalid group name: " + group);
//            logger.info("Register", "Invalid group name: " + group);
            return false;
        }

        boolean registered = false;

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
//            loggedIn = registerForAPI16AndAbove( group,  context);
//        }else{
//            loggedIn = registerForAPI15AndBelow( group,  context);
//        }


        //     loggedIn = registerForAPI16AndAbove( group,  context);
        final Prefs prefs = new Prefs(context);
        //https://code.tutsplus.com/tutorials/android-from-scratch-using-rest-apis--cms-27117
        // https://stackoverflow.com/questions/42767249/android-post-request-with-json
        String registerUrl = prefs.getServerUrl(true) + REGISTER_URL;
        URL cacophonyRegisterEndpoint = null;
        try {
            cacophonyRegisterEndpoint = new URL(registerUrl);
            // Create connection
            HttpsURLConnection myConnection = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                //  HttpsURLConnection myConnection = (HttpsURLConnection) cacophonyRegisterEndpoint.openConnection();
                //https://stackoverflow.com/questions/26633349/disable-ssl-as-a-protocol-in-httpsurlconnection
                myConnection = NetCipher.getHttpsURLConnection(cacophonyRegisterEndpoint);
            } else {
                myConnection = (HttpsURLConnection) cacophonyRegisterEndpoint.openConnection();
            }

            myConnection.setRequestMethod("POST");
            myConnection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            myConnection.setRequestProperty("Accept", "application/json");
            myConnection.setDoOutput(true);
            myConnection.setDoInput(true);

            final String devicename = RandomStringUtils.random(20, true, true);
            final String password = RandomStringUtils.random(20, true, true);


            JSONObject jsonParam = new JSONObject();
            jsonParam.put("devicename", devicename);
            jsonParam.put("password", password);
            jsonParam.put("group", group);
            DataOutputStream os = new DataOutputStream(myConnection.getOutputStream());
            os.writeBytes(jsonParam.toString());
            os.flush();

            //  Here you read any answer from server.
            BufferedReader serverAnswer = new BufferedReader(new InputStreamReader(myConnection.getInputStream()));
            String responseLine;

            responseLine = serverAnswer.readLine();
            os.close();
            serverAnswer.close();

            Log.i("STATUS", String.valueOf(myConnection.getResponseCode()));
            String status = String.valueOf(myConnection.getResponseCode());
            Log.i("MSG", myConnection.getResponseMessage());
            String responseCode = String.valueOf(myConnection.getResponseCode());

            myConnection.disconnect();

            String responseString = new String(responseLine);

            JSONObject joRes = new JSONObject(responseString);
            if (joRes.getBoolean("success")) {
                registered = true;
                setToken(joRes.getString("token"));

                // look at web token
                String deviceID = Util.getDeviceID(context, getToken());
                prefs.setDeviceId(deviceID);

                prefs.setDeviceName(devicename);
                prefs.setGroupName(group);
                prefs.setPassword(password);

                prefs.setDeviceId(deviceID);
            } else {
                // Failed register.
                registered = false;
            }


        } catch (Exception e) {
            e.printStackTrace();
        }


        return registered;


    }

    /**
     * Does a synchronous http request to upload the file and JSON Object to the server as an audio
     * recording. Can't be run on main/UI thread.
     *
     * @param audioFile recording.
     * @param data      metadata.
     * @return If upload was successful
     */
    static boolean uploadAudioRecording(File audioFile, JSONObject data, Context context) {
        Prefs prefs = new Prefs(context);

        if (audioFile == null || data == null) {
            Log.e(TAG, "uploadAudioRecording: Invalid audioFile or JSONObject. Aborting upload");
//            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "uploadAudioRecording: Invalid audioFile or JSONObject. Aborting upload");
//            logger.error("uploadAudioRecording: Invalid audioFile or JSONObject. Aborting upload");
            return false;
        }

        // Check that there is a JWT (JSON Web Token)
        if (getToken() == null) {
            if (!login(context)) {
                Log.e(TAG, "sendFile: no JWT. Aborting upload");
//                Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "sendFile: no JWT. Aborting upload");
//                logger.error("sendFile: no JWT. Aborting upload");

                return false; // Can't upload without JWT, login/register device to get JWT.
            }
        }

        // Building POST request
        SyncHttpClient client = new SyncHttpClient();
        client.addHeader("Authorization", getToken());
        RequestParams params = new RequestParams();
        params.put("data", data.toString());
        try {
            params.put("file", audioFile);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found, can't upload...");
//            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "File not found, can't upload...");
//            logger.error("File not found, can't upload...");
            return false;
        }

        if (uploading) {
            Log.i(TAG, "Already uploading. Wait until last upload is finished.");
//            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "Already uploading. Wait until last upload is finished.");
//            logger.info("Already uploading. Wait until last upload is finished.");
            return false;
        }
        uploading = true;


        // Send request
        // boolean useTestServer = prefs.getUseTestServer();
//        client.post(prefs.getServerUrl(useTestServer) + UPLOAD_AUDIO_API_URL, params, new AsyncHttpResponseHandler() {
        client.post(prefs.getServerUrl(true) + UPLOAD_AUDIO_API_URL, params, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                // called when response HTTP status is "200 OK"
                Log.i(TAG, "sendFile: onSuccess: Successful upload.");
//                logger.info("Successful upload");
                uploadSuccess = true;
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                // called when response HTTP status is "4XX" (eg. 401, 403, 404)
                Log.w(TAG, "sendFile: onSuccess: Failed upload.");
//                logger.error("Failed upload.");

                uploadSuccess = false;
            }
        });
//        Log.d(LOG_TAG, "uploadAudioRecording: finished.");
        //    logger.info("uploadAudioRecording: finished.");
        uploading = false;

        return uploadSuccess;
    }


    static boolean uploadAudioRecording5(File audioFile, JSONObject data, Context context) {
        // http://www.codejava.net/java-se/networking/upload-files-by-sending-multipart-request-programmatically
        if (uploading) {
            Log.i(TAG, "Already uploading. Wait until last upload is finished.");
//            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "Already uploading. Wait until last upload is finished.");
//            logger.info("Already uploading. Wait until last upload is finished.");
            return false;
        }
        uploading = true;

    String charset = "UTF-8";

        Prefs prefs = new Prefs(context);
        String uploadUrl = prefs.getServerUrl(true) + UPLOAD_AUDIO_API_URL;
        try {
        MultipartUtility multipart = new MultipartUtility(uploadUrl, charset);


        multipart.addFormField("data", data.toString());
        multipart.addFilePart("file", audioFile);


        List<String> responseString = multipart.finish();


            Log.i(TAG, "SERVER REPLIED:");
            try {
                uploadSuccess = false;
                for (String line : responseString) {
                    JSONObject joRes = new JSONObject(line);

                    if (joRes.getBoolean("success")) {
                        uploadSuccess = true;
                        break;
                    }

                }

            }catch (JSONException e) {
                e.printStackTrace();
            }

    } catch(IOException ex) {
      Log.e(TAG, ex.getLocalizedMessage());
    }
        uploading = false;
return uploadSuccess;
}

    static boolean uploadAudioRecording4(File audioFile, JSONObject data, Context context) {

        Prefs prefs = new Prefs(context);

        if (audioFile == null || data == null) {
            Log.e(TAG, "uploadAudioRecording: Invalid audioFile or JSONObject. Aborting upload");
//            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "uploadAudioRecording: Invalid audioFile or JSONObject. Aborting upload");
//            logger.error("uploadAudioRecording: Invalid audioFile or JSONObject. Aborting upload");
            return false;
        }

        // Check that there is a JWT (JSON Web Token)
        if (getToken() == null) {
            if (!login(context)) {
                Log.e(TAG, "sendFile: no JWT. Aborting upload");
//                Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "sendFile: no JWT. Aborting upload");
//                logger.error("sendFile: no JWT. Aborting upload");

                return false; // Can't upload without JWT, login/register device to get JWT.
            }
        }

        String uploadUrl = prefs.getServerUrl(true) + UPLOAD_AUDIO_API_URL;
        URL cacophonyUploadEndpoint = null;
        try {
            cacophonyUploadEndpoint = new URL(uploadUrl);
            HttpsURLConnection myConnection = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                //  HttpsURLConnection myConnection = (HttpsURLConnection) cacophonyRegisterEndpoint.openConnection();
                //https://stackoverflow.com/questions/26633349/disable-ssl-as-a-protocol-in-httpsurlconnection
                myConnection = NetCipher.getHttpsURLConnection(cacophonyUploadEndpoint);
            } else {
                myConnection = (HttpsURLConnection) cacophonyUploadEndpoint.openConnection();
            }

            myConnection.setRequestProperty("Authorization", getToken());

            String charset = "UTF-8";

          //  File textFile = new File("/path/to/file.txt");
          //  File binaryFile = new File("/path/to/file.bin");
            String boundary = Long.toHexString(System.currentTimeMillis()); // Just generate some unique random value.
            String CRLF = "\r\n"; // Line separator required by multipart/form-data.
          //  URLConnection connection = new URL(url).openConnection();
            myConnection.setDoOutput(true);
            myConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);


                    OutputStream output = myConnection.getOutputStream();
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);

                // Send normal param.
                writer.append("--" + boundary).append(CRLF);
                writer.append("Content-Disposition: form-data; name=\"data\"").append(CRLF);
                writer.append("Content-Type: text/plain; charset=" + charset).append(CRLF);
                writer.append(CRLF).append(data.toString()).append(CRLF).flush();

                // Send binary file.
                writer.append("--" + boundary).append(CRLF);
                writer.append("Content-Disposition: form-data; name=\"binaryFile\"; filename=\"" + audioFile.getName() + "\"").append(CRLF);
                writer.append("Content-Type: " + URLConnection.guessContentTypeFromName(audioFile.getName())).append(CRLF);
                writer.append("Content-Transfer-Encoding: binary").append(CRLF);
                writer.append(CRLF).flush();

              //  Files.copy(binaryFile.toPath(), output);
            InputStream is = null;
        //    OutputStream os = null;
            try {
                is = new FileInputStream(audioFile);
              //  os = new FileOutputStream(dest);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    output.write(buffer, 0, length);
                }
            } finally {
             //   is.close();
              //  os.close();
            }

                output.flush(); // Important before continuing with writer!
                writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.

                // End of multipart/form-data.
                writer.append("--" + boundary + "--").append(CRLF).flush();

            //  Here you read any answer from server.
            BufferedReader serverAnswer = new BufferedReader(new InputStreamReader(myConnection.getInputStream()));
            String responseLine;

            responseLine = serverAnswer.readLine();
            output.close();
            serverAnswer.close();

            Log.i("STATUS", String.valueOf(myConnection.getResponseCode()));
            String status = String.valueOf(myConnection.getResponseCode());
            Log.i("MSG" , myConnection.getResponseMessage());
            String responseCode = String.valueOf(myConnection.getResponseCode());

            myConnection.disconnect();

        }catch (Exception ex){
            Log.e(TAG, ex.getLocalizedMessage());
        }

return true;
    }

//    static boolean uploadAudioRecording3(File audioFile, JSONObject data, Context context) {
//
//         int serverResponseCode = 0;
//        Prefs prefs = new Prefs(context);
//
//        if (audioFile == null || data == null) {
//            Log.e(TAG, "uploadAudioRecording: Invalid audioFile or JSONObject. Aborting upload");
////            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "uploadAudioRecording: Invalid audioFile or JSONObject. Aborting upload");
////            logger.error("uploadAudioRecording: Invalid audioFile or JSONObject. Aborting upload");
//            return false;
//        }
//
//        // Check that there is a JWT (JSON Web Token)
//        if (getToken() == null) {
//            if (!login(context)) {
//                Log.e(TAG, "sendFile: no JWT. Aborting upload");
////                Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "sendFile: no JWT. Aborting upload");
////                logger.error("sendFile: no JWT. Aborting upload");
//
//                return false; // Can't upload without JWT, login/register device to get JWT.
//            }
//        }
//
//        String uploadUrl = prefs.getServerUrl(true) + UPLOAD_AUDIO_API_URL;
//        URL cacophonyUploadEndpoint = null;
//        try {
//            cacophonyUploadEndpoint = new URL(uploadUrl);
//            HttpsURLConnection myConnection = null;
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
//                //  HttpsURLConnection myConnection = (HttpsURLConnection) cacophonyRegisterEndpoint.openConnection();
//                //https://stackoverflow.com/questions/26633349/disable-ssl-as-a-protocol-in-httpsurlconnection
//                myConnection = NetCipher.getHttpsURLConnection(cacophonyUploadEndpoint);
//            } else {
//                myConnection = (HttpsURLConnection) cacophonyUploadEndpoint.openConnection();
//            }
//
//            String param = "11";
//
//          //  File binaryFile = new File("/path/to/file.bin");
//            String boundary = Long.toHexString(System.currentTimeMillis()); // Just generate some unique random value.
//            String CRLF = "\r\n"; // Line separator required by multipart/form-data.
//
//
//            myConnection.setDoOutput(true);
//            myConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
//            String charset = "UTF-8";
//            try {
//                    OutputStream output = myConnection.getOutputStream();
//                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);
//
//                // Send normal param.
//                writer.append("--" + boundary).append(CRLF);
//                writer.append("Content-Disposition: form-data; name=\"duration\"").append(CRLF);
//                writer.append("Content-Type: text/plain; charset=" + charset).append(CRLF);
//                writer.append(CRLF).append(param).append(CRLF).flush();
//
//
//
//                // Send binary file.
//                writer.append("--" + boundary).append(CRLF);
//                writer.append("Content-Disposition: form-data; name=\"binaryFile\"; filename=\"" + audioFile.getName() + "\"").append(CRLF);
//                writer.append("Content-Type: " + URLConnection.guessContentTypeFromName(audioFile.getName())).append(CRLF);
//                writer.append("Content-Transfer-Encoding: binary").append(CRLF);
//                writer.append(CRLF).flush();
//
//                InputStream is = null;
//                OutputStream os = null;
//                try {
//                    is = new FileInputStream(audioFile);
//                   // os = new FileOutputStream(dest);
//                    byte[] buffer = new byte[1024];
//                    int length;
//                    while ((length = is.read(buffer)) > 0) {
//                        output.write(buffer, 0, length);
//                    }
//                } finally {
//                    is.close();
//                    os.close();
//                }
//
//               // Files.copy(binaryFile.toPath(), output);
//                output.flush(); // Important before continuing with writer!
//                writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.
//
//                // End of multipart/form-data.
//                writer.append("--" + boundary + "--").append(CRLF).flush();
//
//            myConnection.setRequestMethod("POST");
//           // con.setRequestProperty("User-Agent", USER_AGENT);
//
//            // For POST only - START
//            myConnection.setDoOutput(true);
//            DataOutputStream dos = new DataOutputStream(myConnection.getOutputStream());
//            dos.writeBytes(data.toString());
//            dos.flush();
//
//
//            dos.close();
//            // For POST only - END
//
//            int responseCode = myConnection.getResponseCode();
//            System.out.println("POST Response Code :: " + responseCode);
//
//            if (responseCode == HttpURLConnection.HTTP_OK) { //success
//                BufferedReader in = new BufferedReader(new InputStreamReader(
//                        myConnection.getInputStream()));
//                String inputLine;
//                StringBuffer response = new StringBuffer();
//
//                while ((inputLine = in.readLine()) != null) {
//                    response.append(inputLine);
//                }
//                in.close();
//
//                // print result
//                System.out.println(response.toString());
//            } else {
//                System.out.println("POST request not worked");
//            }
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//            myConnection.setRequestMethod("POST");
//            myConnection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
//            myConnection.setRequestProperty("Accept","application/json");
//            myConnection.setDoOutput(true);
//            myConnection.setDoInput(true);
//            myConnection.setRequestProperty("Authorization", getToken());
//
//            DataOutputStream dos = new DataOutputStream(myConnection.getOutputStream());
//            dos.writeBytes(data.toString());
//            dos.flush();
//
//
//
//          //  DataOutputStream dos = null;
//            String lineEnd = "\r\n";
//            String twoHyphens = "--";
//            String boundary = "*****";
//            int bytesRead, bytesAvailable, bufferSize;
//            byte[] buffer;
//            int maxBufferSize = 1 * 1024 * 1024;
//
//
//            FileInputStream fileInputStream = new FileInputStream(audioFile);
//
//       //     dos = new DataOutputStream(myConnection.getOutputStream());
//
//            dos.writeBytes(twoHyphens + boundary + lineEnd);
//            //  dos.writeBytes("Content-Disposition: form-data; name="uploaded_file";filename="" + fileName + """ + lineEnd);
//            dos.writeBytes("Content-Disposition: form-data; name=\"document[file]\"; filename=\"" + audioFile.getName() +"\"" + lineEnd);
//
//
//            dos.writeBytes(lineEnd);
//
//            // create a buffer of  maximum size
//            bytesAvailable = fileInputStream.available();
//
//            bufferSize = Math.min(bytesAvailable, maxBufferSize);
//            buffer = new byte[bufferSize];
//
//            // read file and write it into form...
//            bytesRead = fileInputStream.read(buffer, 0, bufferSize);
//
//            while (bytesRead > 0) {
//
//                dos.write(buffer, 0, bufferSize);
//                bytesAvailable = fileInputStream.available();
//                bufferSize = Math.min(bytesAvailable, maxBufferSize);
//                bytesRead = fileInputStream.read(buffer, 0, bufferSize);
//
//            }
//
//            // send multipart form data necesssary after file data...
//            dos.writeBytes(lineEnd);
//            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
//
////            // Responses from the server (code and message)
////            serverResponseCode = myConnection.getResponseCode();
////            String serverResponseMessage = myConnection.getResponseMessage();
//
//
//            // Open a HTTP  connection to  the URL
//
////            myConnection.setDoInput(true); // Allow Inputs
////            myConnection.setDoOutput(true); // Allow Outputs
////            myConnection.setUseCaches(false); // Don't use a Cached Copy
////            myConnection.setRequestMethod("POST");
////            myConnection.setRequestProperty("Connection", "Keep-Alive");
////            myConnection.setRequestProperty("ENCTYPE", "multipart/form-data");
////            myConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
////            myConnection.setRequestProperty("uploaded_file", audioFile.getName());
////            myConnection.setRequestProperty("Authorization", getToken());
//            //myConnection.setRequestProperty("data", data.toString());
//
//            // Responses from the server (code and message)
//            serverResponseCode = myConnection.getResponseCode();
//            String serverResponseMessage = myConnection.getResponseMessage();
//
//            Log.i("uploadFile", "HTTP Response is : "
//                    + serverResponseMessage + ": " + serverResponseCode);
//            dos = new DataOutputStream(myConnection.getOutputStream());
//
//            dos.writeBytes(twoHyphens + boundary + lineEnd);
//          //  dos.writeBytes("Content-Disposition: form-data; name="uploaded_file";filename="" + fileName + """ + lineEnd);
//            dos.writeBytes("Content-Disposition: form-data; name=\"document[file]\"; filename=\"" + audioFile.getName() +"\"" + lineEnd);
//
//
//                    dos.writeBytes(lineEnd);
//
//            // create a buffer of  maximum size
//            bytesAvailable = fileInputStream.available();
//
//            bufferSize = Math.min(bytesAvailable, maxBufferSize);
//            buffer = new byte[bufferSize];
//
//            // read file and write it into form...
//            bytesRead = fileInputStream.read(buffer, 0, bufferSize);
//
//            while (bytesRead > 0) {
//
//                dos.write(buffer, 0, bufferSize);
//                bytesAvailable = fileInputStream.available();
//                bufferSize = Math.min(bytesAvailable, maxBufferSize);
//                bytesRead = fileInputStream.read(buffer, 0, bufferSize);
//
//            }
//
//            // send multipart form data necesssary after file data...
//            dos.writeBytes(lineEnd);
//            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
//
////            // Responses from the server (code and message)
////            serverResponseCode = myConnection.getResponseCode();
////            String serverResponseMessage = myConnection.getResponseMessage();
//
//            Log.i("uploadFile", "HTTP Response is : "
//                    + serverResponseMessage + ": " + serverResponseCode);
//
//            if(serverResponseCode == 200){
//                Log.i(TAG, "upload successful");
//
////                runOnUiThread(new Runnable() {
////                    public void run() {
////
////                        String msg = "File Upload Completed.\n\n See uploaded file here : \n\n"
////                                +" http://www.androidexample.com/media/uploads/"
////                                +uploadFileName;
////
////                        messageText.setText(msg);
////                        Toast.makeText(UploadToServer.this, "File Upload Complete.",
////                                Toast.LENGTH_SHORT).show();
////                    }
////                });
//            }
//
//            //close the streams //
//            fileInputStream.close();
//            dos.flush();
//            dos.close();
//
//
//        }catch (Exception ex){
//            ex.printStackTrace();
//            uploading = false;
//        }
//
//return true;
//    }

    static boolean uploadAudioRecording2(File audioFile, JSONObject data, Context context) {
        Prefs prefs = new Prefs(context);

        if (audioFile == null || data == null) {
            Log.e(TAG, "uploadAudioRecording: Invalid audioFile or JSONObject. Aborting upload");
//            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "uploadAudioRecording: Invalid audioFile or JSONObject. Aborting upload");
//            logger.error("uploadAudioRecording: Invalid audioFile or JSONObject. Aborting upload");
            return false;
        }

        // Check that there is a JWT (JSON Web Token)
        if (getToken() == null) {
            if (!login(context)) {
                Log.e(TAG, "sendFile: no JWT. Aborting upload");
//                Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "sendFile: no JWT. Aborting upload");
//                logger.error("sendFile: no JWT. Aborting upload");

                return false; // Can't upload without JWT, login/register device to get JWT.
            }
        }
        String uploadUrl = prefs.getServerUrl(true) + UPLOAD_AUDIO_API_URL;
        URL cacophonyUploadEndpoint = null;
        try {
            cacophonyUploadEndpoint = new URL(uploadUrl);
            HttpsURLConnection myConnection = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD &&  Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                //  HttpsURLConnection myConnection = (HttpsURLConnection) cacophonyRegisterEndpoint.openConnection();
                //https://stackoverflow.com/questions/26633349/disable-ssl-as-a-protocol-in-httpsurlconnection
                myConnection = NetCipher.getHttpsURLConnection(cacophonyUploadEndpoint);
            }else{
                myConnection = (HttpsURLConnection) cacophonyUploadEndpoint.openConnection();
            }

            myConnection.setRequestMethod("POST");
            myConnection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            myConnection.setRequestProperty("Accept","application/json");
            myConnection.setDoOutput(true);
            myConnection.setDoInput(true);

            JSONObject jsonParam = new JSONObject();
            jsonParam.put("Authorization", getToken());
            jsonParam.put("data", data.toString());
            try {
                jsonParam.put("file", audioFile);
            }catch (Exception ex){
                Log.e(TAG, "File not found, can't upload...");
                return false;
            }

            if (uploading) {
                Log.i(TAG, "Already uploading. Wait until last upload is finished.");
                return false;
            }
            uploading = true;

            DataOutputStream os = new DataOutputStream(myConnection.getOutputStream());
            os.writeBytes(jsonParam.toString());
            os.flush();

            //  Here you read any answer from server.
            BufferedReader serverAnswer = new BufferedReader(new InputStreamReader(myConnection.getInputStream()));
            String responseLine;

            responseLine = serverAnswer.readLine();
            os.close();
            serverAnswer.close();

            Log.i("STATUS", String.valueOf(myConnection.getResponseCode()));
            String status = String.valueOf(myConnection.getResponseCode());
            Log.i("MSG" , myConnection.getResponseMessage());
            String responseCode = String.valueOf(myConnection.getResponseCode());

            myConnection.disconnect();

         //   String responseString = new String(responseLine);


            uploading = false;

        }catch (Exception ex){
            ex.printStackTrace();
            uploading = false;
        }


//        // Building POST request
//        SyncHttpClient client = new SyncHttpClient();
//        client.addHeader("Authorization", getToken());
//        RequestParams params = new RequestParams();
//        params.put("data", data.toString());
//        try {
//            params.put("file", audioFile);
//        } catch (FileNotFoundException e) {
//            Log.e(TAG, "File not found, can't upload...");
////            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "File not found, can't upload...");
////            logger.error("File not found, can't upload...");
//            return false;
//        }
//
//        if (uploading) {
//            Log.i(TAG, "Already uploading. Wait until last upload is finished.");
////            Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "Already uploading. Wait until last upload is finished.");
////            logger.info("Already uploading. Wait until last upload is finished.");
//            return false;
//        }
//        uploading = true;
//
//
//        // Send request
//        // boolean useTestServer = prefs.getUseTestServer();
////        client.post(prefs.getServerUrl(useTestServer) + UPLOAD_AUDIO_API_URL, params, new AsyncHttpResponseHandler() {
//        client.post(prefs.getServerUrl(true) + UPLOAD_AUDIO_API_URL, params, new AsyncHttpResponseHandler() {
//            @Override
//            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
//                // called when response HTTP status is "200 OK"
//                Log.i(TAG, "sendFile: onSuccess: Successful upload.");
////                logger.info("Successful upload");
//                uploadSuccess = true;
//            }
//
//            @Override
//            public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
//                // called when response HTTP status is "4XX" (eg. 401, 403, 404)
//                Log.w(TAG, "sendFile: onSuccess: Failed upload.");
////                logger.error("Failed upload.");
//
//                uploadSuccess = false;
//            }
//        });
////        Log.d(LOG_TAG, "uploadAudioRecording: finished.");
//        //    logger.info("uploadAudioRecording: finished.");
//        uploading = false;

        return uploadSuccess;
    }

    static String getToken() {
        return token;
    }

    private static void setToken(String token) {
        Server.token = token;
    }

    private static void setErrorMessage(String errorMessage){
        Server.errorMessage = errorMessage;
    }

    static String getErrorMessage() {
        return errorMessage;
    }


//    static void broadcastAMessage(Context context, String message){
//        // https://stackoverflow.com/questions/8802157/how-to-use-localbroadcastmanager
//
//        Intent intent = new Intent("event");
//        // You can also include some extra data.
//      //  intent.putExtra("message", "enable_vitals_button");
//        intent.putExtra("message", message);
//        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
//
//    }

//    static boolean registerForAPI16AndAbove(final String group, final Context context) {
//        // Android API 16 and above can use TLSv1.2 so will use https
//        final Prefs prefs = new Prefs(context);
//        //https://code.tutsplus.com/tutorials/android-from-scratch-using-rest-apis--cms-27117
//        // https://stackoverflow.com/questions/42767249/android-post-request-with-json
//        String registerUrl = prefs.getServerUrl(true) + REGISTER_URL;
//        URL cacophonyRegisterEndpoint = null;
//        try {
//            cacophonyRegisterEndpoint = new URL(registerUrl);
//            // Create connection
//            HttpsURLConnection myConnection = null;
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD &&  Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
//                //  HttpsURLConnection myConnection = (HttpsURLConnection) cacophonyRegisterEndpoint.openConnection();
//                //https://stackoverflow.com/questions/26633349/disable-ssl-as-a-protocol-in-httpsurlconnection
//                myConnection = NetCipher.getHttpsURLConnection(cacophonyRegisterEndpoint);
//            }else{
//                myConnection = (HttpsURLConnection) cacophonyRegisterEndpoint.openConnection();
//            }
//
//            myConnection.setRequestMethod("POST");
//            myConnection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
//            myConnection.setRequestProperty("Accept","application/json");
//            myConnection.setDoOutput(true);
//            myConnection.setDoInput(true);
//
//            final String devicename = RandomStringUtils.random(20, true, true);
//            final String password = RandomStringUtils.random(20, true, true);
//
//
//            JSONObject jsonParam = new JSONObject();
//            jsonParam.put("devicename", devicename);
//            jsonParam.put("password", password);
//            jsonParam.put("group", group);
//            DataOutputStream os = new DataOutputStream(myConnection.getOutputStream());
//            os.writeBytes(jsonParam.toString());
//            os.flush();
//
//            //  Here you read any answer from server.
//            BufferedReader serverAnswer = new BufferedReader(new InputStreamReader(myConnection.getInputStream()));
//            String responseLine;
//
//            responseLine = serverAnswer.readLine();
//            os.close();
//            serverAnswer.close();
//
//            Log.i("STATUS", String.valueOf(myConnection.getResponseCode()));
//            String status = String.valueOf(myConnection.getResponseCode());
//            Log.i("MSG" , myConnection.getResponseMessage());
//            String responseCode = String.valueOf(myConnection.getResponseCode());
//
//            myConnection.disconnect();
//
//            String responseString = new String(responseLine);
//
//                JSONObject joRes = new JSONObject(responseString);
//                if (joRes.getBoolean("success")) {
//                    loggedIn = true;
//                    setToken(joRes.getString("token"));
//
//                    // look at web token
//                    String deviceID = Util.getDeviceID(context, getToken());
//                    prefs.setDeviceId(deviceID);
//
//                    prefs.setDeviceName(devicename);
//                    prefs.setGroupName(group);
//                    prefs.setPassword(password);
//
//                    prefs.setDeviceId(deviceID);
//                } else {
//                    // Failed register.
//                    loggedIn = false;
//                }
//
//
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return loggedIn;
//    }

//    static boolean registerForAPI15AndBelow(final String group, final Context context) {
//        // For now can't use https
//        final Prefs prefs = new Prefs(context);
//        SyncHttpClient client = new SyncHttpClient();
//        RequestParams params = new RequestParams();
//
//        final String devicename = RandomStringUtils.random(20, true, true);
//        final String password = RandomStringUtils.random(20, true, true);
//
//        params.put("devicename", devicename);
//        params.put("password", password);
//        params.put("group", group);
//
//        //    final Prefs prefs = new Prefs(context);
//
//        // Sent Post request.
////        boolean useTestServer = prefs.getUseTestServer();
////        client.post(prefs.getServerUrl(useTestServer) + REGISTER_URL, params, new AsyncHttpResponseHandler() {
//        client.post(prefs.getServerUrl(false) + REGISTER_URL, params, new AsyncHttpResponseHandler() {
//            @Override
//            public void onSuccess(int statusCode, Header[] headers, byte[] response) {
//                String responseString = new String(response);
//                try {
//                    JSONObject joRes = new JSONObject(responseString);
//                    if (joRes.getBoolean("success")) {
//                        loggedIn = true;
//                        setToken(joRes.getString("token"));
//
//                        // look at web token
//                        String deviceID = Util.getDeviceID(context, getToken());
//                        prefs.setDeviceId(deviceID);
//
//                        prefs.setDeviceName(devicename);
//                        prefs.setGroupName(group);
//                        prefs.setPassword(password);
//
//                        prefs.setDeviceId(deviceID);
//                    } else {
//                        // Failed register.
//                        loggedIn = false;
//                    }
//                } catch (JSONException ex) {
//                    loggedIn = false;
//                    Log.e(TAG, "Error with parsing register response into a JSON");
////                    Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "Error with parsing register response into a JSON");
////                    logger.error("Error with parsing register response into a JSON");
//                } catch (Exception ex) {
//                    ex.printStackTrace();
////                    Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, ex.getLocalizedMessage());
////                    logger.error(ex.getLocalizedMessage());
//                    Log.e(TAG, ex.getLocalizedMessage());
//                }
//            }
//
//            @Override
//            public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
//                String responseString = new String(errorResponse);
//                try {
//                    JSONObject joRes = new JSONObject(responseString);
//                    JSONArray messages = joRes.getJSONArray("messages");
//                    String firstMessage = (String) messages.get(0);
//                    setErrorMessage(firstMessage);
//                    Log.i(TAG, firstMessage);
////                    logger.info(firstMessage);
//                }catch (Exception ex){
//                    Log.e(TAG, "Error with parsing register errorResponse into a JSON");
////                    Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "Error with parsing register errorResponse into a JSON");
////                    logger.error("Error with parsing register errorResponse into a JSON");
//                }
//                loggedIn = false;
//                Log.e(TAG, "Error with getting response from server");
////                Util.writeLocalLogEntryUsingLogback(context, LOG_TAG, "Error with getting response from server");
////                logger.error("Error with getting response from server");
//            }
//        });
//
//
//        return loggedIn;
//    }
}
