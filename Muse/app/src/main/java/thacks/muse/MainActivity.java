

package thacks.muse;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

import java.util.concurrent.atomic.AtomicReference;

import com.choosemuse.libmuse.Accelerometer;
import com.choosemuse.libmuse.AnnotationData;
import com.choosemuse.libmuse.ConnectionState;
import com.choosemuse.libmuse.Eeg;
import com.choosemuse.libmuse.LibmuseVersion;
import com.choosemuse.libmuse.MessageType;
import com.choosemuse.libmuse.Muse;
import com.choosemuse.libmuse.MuseArtifactPacket;
import com.choosemuse.libmuse.MuseConfiguration;
import com.choosemuse.libmuse.MuseConnectionListener;
import com.choosemuse.libmuse.MuseConnectionPacket;
import com.choosemuse.libmuse.MuseDataListener;
import com.choosemuse.libmuse.MuseDataPacket;
import com.choosemuse.libmuse.MuseDataPacketType;
import com.choosemuse.libmuse.MuseFileFactory;
import com.choosemuse.libmuse.MuseFileReader;
import com.choosemuse.libmuse.MuseFileWriter;
import com.choosemuse.libmuse.MuseListener;
import com.choosemuse.libmuse.MuseManagerAndroid;
import com.choosemuse.libmuse.MuseVersion;
import com.choosemuse.libmuse.Result;
import com.choosemuse.libmuse.ResultLevel;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.bluetooth.BluetoothAdapter;


import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

/**
 * This example will illustrate how to connect to a Muse headband,
 * register for and receive EEG data and disconnect from the headband.
 * Saving EEG data to a .muse file is also covered.
 *
 * For instructions on how to pair your headband with your Android device
 * please see:
 * http://developer.choosemuse.com/hardware-firmware/bluetooth-connectivity/developer-sdk-bluetooth-connectivity-2
 *
 * Usage instructions:
 * 1. Pair your headband if necessary.
 * 2. Run this project.
 * 3. Turn on the Muse headband.
 * 4. Press "Refresh". It should display all paired Muses in the Spinner drop down at the
 *    top of the screen.  It may take a few seconds for the headband to be detected.
 * 5. Select the headband you want to connect to and press "Connect".
 * 6. You should see EEG and accelerometer data as well as connection status,
 *    version information and relative alpha values appear on the screen.
 * 7. You can pause/resume data transmission with the button at the bottom of the screen.
 * 8. To disconnect from the headband, press "Disconnect"
 */
public class MainActivity extends Activity implements OnClickListener {

    /**
     * Tag used for logging purposes.
     */
    int ii = 0;
    double country [][] = new double [200][4];
    double hiphop [][] = new double [200][4];
    double pop [][] = new double [200][4];
    double edm [][] = new double [200][4];
    double rnb [][] = new double [200][4];
    double classical [][] = new double [200][4];
    private String url;
    int musicInt = 0;
    private final String TAG = "TestLibMuseAndroid";
    ImageView album;
    TextView view1, view2;
    boolean connect = false;
    /**
     * The MuseManager is how you detect Muse headbands and receive notifications
     * when the list of available headbands changes.
     */
    private MuseManagerAndroid manager;

    /**
     * A Muse refers to a Muse headband.  Use this to connect/disconnect from the
     * headband, register listeners to receive EEG data and get headband
     * configuration and version information.
     */
    private Muse muse;

    /**
     * The ConnectionListener will be notified whenever there is a change in
     * the connection state of a headband, for example when the headband connects
     * or disconnects.
     *
     * Note that ConnectionListener is an inner class at the bottom of this file
     * that extends MuseConnectionListener.
     */
    private ConnectionListener connectionListener;

    /**
     * The DataListener is how you will receive EEG (and other) data from the
     * headband.
     *
     * Note that DataListener is an inner class at the bottom of this file
     * that extends MuseDataListener.
     */
    private DataListener dataListener;

    /**
     * Data comes in from the headband at a very fast rate; 220Hz, 256Hz or 500Hz,
     * depending on the type of headband and the preset configuration.  We buffer the
     * data that is read until we can update the UI.
     *
     * The stale flags indicate whether or not new data has been received and the buffers
     * hold the values of the last data packet received.  We are displaying the EEG, ALPHA_RELATIVE
     * and ACCELEROMETER values in this example.
     *
     * Note: the array lengths of the buffers are taken from the comments in
     * MuseDataPacketType, which specify 3 values for accelerometer and 6
     * values for EEG and EEG-derived packets.
     */
    private final double[] eegBuffer = new double[6];
    private boolean eegStale;

    /**
     * We will be updating the UI using a handler instead of in packet handlers because
     * packets come in at a very high frequency and it only makes sense to update the UI
     * at about 60fps. The update functions do some string allocation, so this reduces our memory
     * footprint and makes GC pauses less frequent/noticeable.
     */
    private final Handler handler = new Handler();

    /**
     * In the UI, the list of Muses you can connect to is displayed in a Spinner object for this example.
     * This spinner adapter contains the MAC addresses of all of the headbands we have discovered.
     */
    private ArrayAdapter<String> spinnerAdapter;

    /**
     * It is possible to pause the data transmission from the headband.  This boolean tracks whether
     * or not the data transmission is enabled as we allow the user to pause transmission in the UI.
     */
    private boolean dataTransmission = true;

    /**
     * To save data to a file, you should use a MuseFileWriter.  The MuseFileWriter knows how to
     * serialize the data packets received from the headband into a compact binary format.
     * To read the file back, you would use a MuseFileReader.
     */
    private final AtomicReference<MuseFileWriter> fileWriter = new AtomicReference<>();

    /**
     * We don't want file operations to slow down the UI, so we will defer those file operations
     * to a handler on a separate thread.
     */
    private final AtomicReference<Handler> fileHandler = new AtomicReference<>();


    //--------------------------------------
    // Lifecycle / Connection code


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We need to set the context on MuseManagerAndroid before we can do anything.
        // This must come before other LibMuse API calls as it also loads the library.
        manager = MuseManagerAndroid.getInstance();
        manager.setContext(this);

        Log.i(TAG, "LibMuse version=" + LibmuseVersion.instance().getString());

        WeakReference<MainActivity> weakActivity =
                new WeakReference<MainActivity>(this);
        // Register a listener to receive connection state changes.
        connectionListener = new ConnectionListener(weakActivity);
        // Register a listener to receive data from a Muse.
        dataListener = new DataListener(weakActivity);
        // Register a listener to receive notifications of what Muse headbands
        // we can connect to.
        manager.setMuseListener(new MuseL(weakActivity));

        // Muse 2016 (MU-02) headbands use Bluetooth Low Energy technology to
        // simplify the connection process.  This requires access to the COARSE_LOCATION
        // or FINE_LOCATION permissions.  Make sure we have these permissions before
        // proceeding.
        ensurePermissions();

        // Load and initialize our UI.
        initUI();

        // Start up a thread for asynchronous file operations.
        // This is only needed if you want to do File I/O.
        fileThread.start();

        // Start our asynchronous updates of the UI.

//        handler.post(tickUi);
    }

    protected void onPause() {
        super.onPause();
        // It is important to call stopListening when the Activity is paused
        // to avoid a resource leak from the LibMuse library.
        manager.stopListening();
    }

    public boolean isBluetoothEnabled() {
        return BluetoothAdapter.getDefaultAdapter().isEnabled();
    }

    @Override
    public void onClick(View v) {

        if (v.getId() == R.id.refresh) {
            // The user has pressed the "Refresh" button.
            // Start listening for nearby or paired Muse headbands. We call stopListening
            // first to make sure startListening will clear the list of headbands and start fresh.
            manager.stopListening();
            manager.startListening();

        } else if (v.getId() == R.id.connect) {

            // The user has pressed the "Connect" button to connect to
            // the headband in the spinner.

            // Listening is an expensive operation, so now that we know
            // which headband the user wants to connect to we can stop
            // listening for other headbands.
            manager.stopListening();

            List<Muse> availableMuses = manager.getMuses();
            Spinner musesSpinner = (Spinner) findViewById(R.id.muses_spinner);

            // Check that we actually have something to connect to.
            if (availableMuses.size() < 1 || musesSpinner.getAdapter().getCount() < 1) {
                Log.w(TAG, "There is nothing to connect to");
            } else {

                // Cache the Muse that the user has selected.
                muse = availableMuses.get(musesSpinner.getSelectedItemPosition());
                // Unregister all prior listeners and register our data listener to
                // receive the MuseDataPacketTypes we are interested in.  If you do
                // not register a listener for a particular data type, you will not
                // receive data packets of that type.
                muse.unregisterAllListeners();
                muse.registerConnectionListener(connectionListener);
                muse.registerDataListener(dataListener, MuseDataPacketType.EEG);
                muse.registerDataListener(dataListener, MuseDataPacketType.ALPHA_RELATIVE);
                muse.registerDataListener(dataListener, MuseDataPacketType.ACCELEROMETER);
                muse.registerDataListener(dataListener, MuseDataPacketType.BATTERY);
                muse.registerDataListener(dataListener, MuseDataPacketType.DRL_REF);
                muse.registerDataListener(dataListener, MuseDataPacketType.QUANTIZATION);

                // Initiate a connection to the headband and stream the data asynchronously.
                muse.runAsynchronously();
            }

        } else if (v.getId() == R.id.disconnect) {

            // The user has pressed the "Disconnect" button.
            // Disconnect from the selected Muse.
            if (muse != null) {
                muse.disconnect();
            }

        }
        else if (v.getId() == R.id.album){
            if (musicInt<6){
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setData(Uri.parse(url));
                startActivity(intent);
            }
        }
        else {
            if (connect){
                handler.post(tickUi);
                Intent musicplayer = new Intent (this, musichandler.class);
                startService(musicplayer);
            }
            else{
                Toast.makeText(MainActivity.this,"You must connect to a device first.",Toast.LENGTH_SHORT).show();
            }
        }
    }

    //--------------------------------------
    // Permissions

    /**
     * The ACCESS_COARSE_LOCATION permission is required to use the
     * Bluetooth Low Energy library and must be requested at runtime for Android 6.0+
     * On an Android 6.0 device, the following code will display 2 dialogs,
     * one to provide context and the second to request the permission.
     * On an Android device running an earlier version, nothing is displayed
     * as the permission is granted from the manifest.
     *
     * If the permission is not granted, then Muse 2016 (MU-02) headbands will
     * not be discovered and a SecurityException will be thrown.
     */
    private void ensurePermissions() {

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            // We don't have the ACCESS_COARSE_LOCATION permission so create the dialogs asking
            // the user to grant us the permission.

            DialogInterface.OnClickListener buttonListener =
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which){
                            dialog.dismiss();
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                    0);
                        }
                    };

            // This is the context dialog which explains to the user the reason we are requesting
            // this permission.  When the user presses the positive (I Understand) button, the
            // standard Android permission dialog will be displayed (as defined in the button
            // listener above).
            AlertDialog introDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.permission_dialog_title)
                    .setMessage(R.string.permission_dialog_description)
                    .setPositiveButton(R.string.permission_dialog_understand, buttonListener)
                    .create();
            introDialog.show();
        }
    }


    //--------------------------------------
    // Listeners

    /**
     * You will receive a callback to this method each time a headband is discovered.
     * In this example, we update the spinner with the MAC address of the headband.
     */
    public void museListChanged() {
        final List<Muse> list = manager.getMuses();
        spinnerAdapter.clear();
        for (Muse m : list) {
            spinnerAdapter.add(m.getName() + " - " + m.getMacAddress());
        }
    }

    /**
     * You will receive a callback to this method each time there is a change to the
     * connection state of one of the headbands.
     * @param p     A packet containing the current and prior connection states
     * @param muse  The headband whose state changed.
     */
    public void receiveMuseConnectionPacket(final MuseConnectionPacket p, final Muse muse) {

        final ConnectionState current = p.getCurrentConnectionState();

        // Format a message to show the change of connection state in the UI.
        final String status = p.getPreviousConnectionState() + " -> " + current;
        Log.i(TAG, status);

        // Update the UI with the change in connection state.
        handler.post(new Runnable() {
            @Override
            public void run() {

                final TextView statusText = (TextView) findViewById(R.id.con_status);
                statusText.setText(status);

                final MuseVersion museVersion = muse.getMuseVersion();
                //final TextView museVersionText = (TextView) findViewById(R.id.version);
                // If we haven't yet connected to the headband, the version information
                // will be null.  You have to connect to the headband before either the
                // MuseVersion or MuseConfiguration information is known.
                if (museVersion != null) {
                    final String version = museVersion.getFirmwareType() + " - "
                            + museVersion.getFirmwareVersion() + " - "
                            + museVersion.getProtocolVersion();
                    //museVersionText.setText(version);
                } else {
                  //  museVersionText.setText(R.string.undefined);
                }
            }
        });

        if (current == ConnectionState.DISCONNECTED) {
            Log.i(TAG, "Muse disconnected:" + muse.getName());
            // Save the data file once streaming has stopped.
            saveFile();
            // We have disconnected from the headband, so set our cached copy to null.
            this.muse = null;
            connect = false;
        }
        if(current == ConnectionState.CONNECTED){
            connect = true;
        }
    }

    /**
     * You will receive a callback to this method each time the headband sends a MuseDataPacket
     * that you have registered.  You can use different listeners for different packet types or
     * a single listener for all packet types as we have done here.
     * @param p     The data packet containing the data from the headband (eg. EEG data)
     * @param muse  The headband that sent the information.
     */
    public void receiveMuseDataPacket(final MuseDataPacket p, final Muse muse) {
        writeDataPacketToFile(p);

        // valuesSize returns the number of data values contained in the packet.
        final long n = p.valuesSize();
        switch (p.packetType()) {
            case EEG:
                assert(eegBuffer.length >= n);
                getEegChannelValues(eegBuffer,p);
                eegStale = true;
                break;
//            case ACCELEROMETER:
//                assert(accelBuffer.length >= n);
//                getAccelValues(p);
//                accelStale = true;
//                break;
//            case ALPHA_RELATIVE:
//                assert(alphaBuffer.length >= n);
//                getEegChannelValues(alphaBuffer,p);
//                alphaStale = true;
//                break;
            case BATTERY:
            case DRL_REF:
            case QUANTIZATION:
            default:
                break;
        }
    }

    /**
     * You will receive a callback to this method each time an artifact packet is generated if you
     * have registered for the ARTIFACTS data type.  MuseArtifactPackets are generated when
     * eye blinks are detected, the jaw is clenched and when the headband is put on or removed.
     * @param p     The artifact packet with the data from the headband.
     * @param muse  The headband that sent the information.
     */
    public void receiveMuseArtifactPacket(final MuseArtifactPacket p, final Muse muse) {
    }

    /**
     * Helper methods to get different packet values.  These methods simply store the
     * data in the buffers for later display in the UI.
     *
     * getEegChannelValue can be used for any EEG or EEG derived data packet type
     * such as EEG, ALPHA_ABSOLUTE, ALPHA_RELATIVE or HSI_PRECISION.  See the documentation
     * of MuseDataPacketType for all of the available values.
     * Specific packet types like ACCELEROMETER, GYRO, BATTERY and DRL_REF have their own
     * getValue methods.
     */
    private void getEegChannelValues(double[] buffer, MuseDataPacket p) {
        buffer[0] = p.getEegChannelValue(Eeg.EEG1);
        buffer[1] = p.getEegChannelValue(Eeg.EEG2);
        buffer[2] = p.getEegChannelValue(Eeg.EEG3);
        buffer[3] = p.getEegChannelValue(Eeg.EEG4);
        buffer[4] = p.getEegChannelValue(Eeg.AUX_LEFT);
        buffer[5] = p.getEegChannelValue(Eeg.AUX_RIGHT);
    }

//    private void getAccelValues(MuseDataPacket p) {
//        accelBuffer[0] = p.getAccelerometerValue(Accelerometer.X);
//        accelBuffer[1] = p.getAccelerometerValue(Accelerometer.Y);
//        accelBuffer[2] = p.getAccelerometerValue(Accelerometer.Z);
//    }


    //--------------------------------------
    // UI Specific methods

    /**
     * Initializes the UI of the example application.
     */
    private void initUI() {
        setContentView(R.layout.activity_main);
        Button refreshButton = (Button) findViewById(R.id.refresh);
        refreshButton.setOnClickListener(this);
        Button connectButton = (Button) findViewById(R.id.connect);
        connectButton.setOnClickListener(this);
        Button disconnectButton = (Button) findViewById(R.id.disconnect);
        disconnectButton.setOnClickListener(this);
        Button startmuse=(Button)findViewById(R.id.startmuse);
        startmuse.setOnClickListener(this);

        spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
        Spinner musesSpinner = (Spinner) findViewById(R.id.muses_spinner);
        musesSpinner.setAdapter(spinnerAdapter);

        //album image for switch
        album=(ImageView)findViewById(R.id.album);
        album.setOnClickListener(this);

        //textviews
        view1=(TextView)findViewById(R.id.textView);
        view2=(TextView)findViewById(R.id.con_status);

        Typeface font = Typeface.createFromAsset(getAssets(), "fonts/moonlight.ttf");
        refreshButton.setTypeface(font);
        connectButton.setTypeface(font);
        disconnectButton.setTypeface(font);
        startmuse.setTypeface(font);
        view1.setTypeface(font);
        view2.setTypeface(font);
    }

    /**
     * The runnable that is used to update the UI at 60Hz.
     *
     * We update the UI from this Runnable instead of in packet handlers
     * because packets come in at high frequency -- 220Hz or more for raw EEG
     * -- and it only makes sense to update the UI at about 60fps. The update
     * functions do some string allocation, so this reduces our memory
     * footprint and makes GC pauses less frequent/noticeable.
     */
    private final Runnable tickUi = new Runnable() {

        @Override
        public void run() {

                    updateEeg(ii);
                    ii++;
                    if (ii == 200) {
                        musicInt++;
                        ii = 0;
                    }
                    if(musicInt < 6){
                        handler.postDelayed(tickUi, 100);
                    }
                    else{
                        bestMusic(averageDB());
                    }
        }
    };

    /**
     * The following methods update the TextViews in the UI with the data
     * from the buffers.
     */
//    private void updateAccel() {
//        TextView acc_x = (TextView)findViewById(R.id.acc_x);
//        TextView acc_y = (TextView)findViewById(R.id.acc_y);
//        TextView acc_z = (TextView)findViewById(R.id.acc_z);
//        acc_x.setText(String.format("%6.2f", accelBuffer[0]));
//        acc_z.setText(String.format("%6.2f", accelBuffer[2]));
//    }

    private void updateEeg(int ii) {
       // TextView tp9 = (TextView)findViewById(R.id.eeg_tp9);
        //TextView fp1 = (TextView)findViewById(R.id.eeg_af7);
        //TextView fp2 = (TextView)findViewById(R.id.eeg_af8);
        //TextView tp10 = (TextView)findViewById(R.id.eeg_tp10);
        //tp9.setText(String.format("%6.2f", eegBuffer[0]));
        //fp1.setText(String.format("%6.2f", eegBuffer[1]));
        //fp2.setText(String.format("%6.2f", eegBuffer[2]));
        //tp10.setText(String.format("%6.2f", eegBuffer[3]));
        Log.i("amessage", "Made it here");
        switch(musicInt){
            case 0:
                Log.i("amessage", musicInt+"Made it to hiphop:"+ ii);
                hiphop[ii][0] = eegBuffer[0];
                hiphop[ii][1] = eegBuffer[1];
                hiphop[ii][2] = eegBuffer[2];
                hiphop[ii][3] = eegBuffer[3];
                Log.i("amessage", hiphop[ii][3] + "");
                break;
            case 1:
                Log.i("amessage", musicInt+"Made it to pop:"+ ii);
                pop[ii][0] = eegBuffer[0];
                pop[ii][1] = eegBuffer[1];
                pop[ii][2] = eegBuffer[2];
                pop[ii][3] = eegBuffer[3];
                Log.i("amessage", pop[ii][3] + "");
                break;
            case 2:
                Log.i("amessage", musicInt+"Made it to pop:"+ ii);
                country[ii][0] = eegBuffer[0];
                country[ii][1] = eegBuffer[1];
                country[ii][2] = eegBuffer[2];
                country[ii][3] = eegBuffer[3];
                Log.i("amessage", country[ii][3] + "");
                break;
            case 3:
                Log.i("amessage", musicInt+"Made it to pop:"+ ii);
                edm[ii][0] = eegBuffer[0];
                edm[ii][1] = eegBuffer[1];
                edm[ii][2] = eegBuffer[2];
                edm[ii][3] = eegBuffer[3];
                Log.i("amessage", edm[ii][3]+ "");
                break;
            case 4:
                Log.i("amessage", musicInt+"Made it to pop:"+ ii);
                rnb[ii][0] = eegBuffer[0];
                rnb[ii][1] = eegBuffer[1];
                rnb[ii][2] = eegBuffer[2];
                rnb[ii][3] = eegBuffer[3];
                Log.i("amessage", rnb[ii][3]+ "");
                break;
            case 5:
                Log.i("amessage", musicInt+"Made it to classical");
                classical[ii][0] = eegBuffer[0];
                classical[ii][1] = eegBuffer[1];
                classical[ii][2] = eegBuffer[2];
                classical[ii][3] = eegBuffer[3];
                Log.i("amessage", classical[ii][3] + "");
                break;
        }
        }
    public int averageDB() {
        int hiphopSum = 0;
        int popSum = 0;
        int countrySum = 0;
        int rnbSum = 0;
        int edmSum = 0;
        int classicalSum = 0;
        int averages[] = new int[6];
        //Hip-Hop
        for (int j = 0; j < hiphop.length; j++) {
            hiphopSum += hiphop[j][0];
            hiphopSum += hiphop[j][1];
            hiphopSum += hiphop[j][2];
            hiphopSum += hiphop[j][3];
        }
        averages[0] = hiphopSum / (hiphop.length * 4);
        //Pop
        for (int j = 0; j < pop.length; j++) {
            popSum += pop[j][0];
            popSum += pop[j][1];
            popSum += pop[j][2];
            popSum += pop[j][3];
        }
        averages[1]  = popSum / (pop.length * 4);
        //Country
        for (int j = 0; j < country.length; j++) {
            countrySum += country[j][0];
            countrySum += country[j][1];
            countrySum += country[j][2];
            countrySum += country[j][3];
        }
        averages[2]  = countrySum / (country.length * 4);
        //rnb
        for (int j = 0; j < rnb.length; j++) {
            rnbSum += rnb[j][0];
            rnbSum += rnb[j][1];
            rnbSum += rnb[j][2];
            rnbSum += rnb[j][3];
        }
        averages[3]  = rnbSum / (rnb.length * 4);
        //edm
        for (int j = 0; j < edm.length; j++) {
            edmSum += edm[j][0];
            edmSum += edm[j][1];
            edmSum += edm[j][2];
            edmSum += edm[j][3];
        }
        averages[4]  = edmSum / (edm.length * 4);
        //classical
        for (int j = 0; j < classical.length; j++) {
            classicalSum += classical[j][0];
            classicalSum += classical[j][1];
            classicalSum += classical[j][2];
            classicalSum += classical[j][3];
        }
        averages[5]  = classicalSum / (classical.length * 4);
        for (int k = 0;k<averages.length;k++){
            Log.i("Success!", "Value is"+averages[k]);
        }
        int minValue = 0;
        for (int i = 1; i < averages.length; i++) {
            if (averages[i] < minValue) {
                minValue = i;
            }
        }
        return minValue;
    }

    public void bestMusic(int musicNum) {
        String result="HipHop";
        url="https://open.spotify.com/user/spotify/playlist/37i9dQZF1DWY6tYEFs22tT";
        if (musicNum == 0)
            album.setBackgroundResource(R.drawable.hiphop);
        else if (musicNum == 1){
            album.setBackgroundResource(R.drawable.pop);
            result="Pop";
            url="https://open.spotify.com/user/spotify/playlist/37i9dQZF1DXbpmT3HUTsZm";
        }
        else if (musicNum == 2){
            result="Country";
            album.setBackgroundResource(R.drawable.country);
            url="https://open.spotify.com/user/spotify/playlist/37i9dQZF1DWSK8os4XIQBk";
        }

        else if (musicNum == 3){
            result="RnB";
            album.setBackgroundResource(R.drawable.rb);
            url="https://open.spotify.com/user/spotify/playlist/37i9dQZF1DX2WkIBRaChxW";
        }

        else if (musicNum == 4){
            result="EDM";
            album.setBackgroundResource(R.drawable.edm);
            url="https://open.spotify.com/user/digsterchile/playlist/5AF1pplkAt4P3ojx8JMk6i";
        }

        else if (musicNum == 5){
            result="Classical";
            album.setBackgroundResource(R.drawable.classical);
            url="https://open.spotify.com/user/spotify/playlist/37i9dQZF1DWWEJlAGA9gs0";
        }
        Toast.makeText(MainActivity.this,"Success! The ideal music genre for you is "+result+".",Toast.LENGTH_SHORT).show();
    }

    //--------------------------------------
    // File I/O

    /**
     * We don't want to block the UI thread while we write to a file, so the file
     * writing is moved to a separate thread.
     */
    private final Thread fileThread = new Thread() {
        @Override
        public void run() {
            Looper.prepare();
            fileHandler.set(new Handler());
            final File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            final File file = new File(dir, "new_muse_file.muse" );
            // MuseFileWriter will append to an existing file.
            // In this case, we want to start fresh so the file
            // if it exists.
            if (file.exists()) {
                file.delete();
            }
            Log.i(TAG, "Writing data to: " + file.getAbsolutePath());
            fileWriter.set(MuseFileFactory.getMuseFileWriter(file));
            Looper.loop();
        }
    };

    /**
     * Writes the provided MuseDataPacket to the file.  MuseFileWriter knows
     * how to write all packet types generated from LibMuse.
     * @param p     The data packet to write.
     */
    private void writeDataPacketToFile(final MuseDataPacket p) {
        Handler h = fileHandler.get();
        if (h != null) {
            h.post(new Runnable() {
                @Override
                public void run() {
                    fileWriter.get().addDataPacket(0, p);
                }
            });
        }
    }

    /**
     * Flushes all the data to the file and closes the file writer.
     */
    private void saveFile() {
        Handler h = fileHandler.get();
        if (h != null) {
            h.post(new Runnable() {
                @Override public void run() {
                    MuseFileWriter w = fileWriter.get();
                    // Annotation strings can be added to the file to
                    // give context as to what is happening at that point in
                    // time.  An annotation can be an arbitrary string or
                    // may include additional AnnotationData.
                    w.addAnnotationString(0, "Disconnected");
                    w.flush();
                    w.close();
                }
            });
        }
    }

    /**
     * Reads the provided .muse file and prints the data to the logcat.
     * @param name  The name of the file to read.  The file in this example
     *              is assumed to be in the Environment.DIRECTORY_DOWNLOADS
     *              directory.
     */
    private void playMuseFile(String name) {

        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(dir, name);

        final String tag = "Muse File Reader";

        if (!file.exists()) {
            Log.w(tag, "file doesn't exist");
            return;
        }

        MuseFileReader fileReader = MuseFileFactory.getMuseFileReader(file);

        // Loop through each message in the file.  gotoNextMessage will read the next message
        // and return the result of the read operation as a Result.
        Result res = fileReader.gotoNextMessage();
        while (res.getLevel() == ResultLevel.R_INFO && !res.getInfo().contains("EOF")) {

            MessageType type = fileReader.getMessageType();
            int id = fileReader.getMessageId();
            long timestamp = fileReader.getMessageTimestamp();

            Log.i(tag, "type: " + type.toString() +
                  " id: " + Integer.toString(id) +
                  " timestamp: " + String.valueOf(timestamp));

            switch(type) {
                // EEG messages contain raw EEG data or DRL/REF data.
                // EEG derived packets like ALPHA_RELATIVE and artifact packets
                // are stored as MUSE_ELEMENTS messages.
                case EEG:
                case BATTERY:
                case ACCELEROMETER:
                case QUANTIZATION:
                case GYRO:
                case MUSE_ELEMENTS:
                    MuseDataPacket packet = fileReader.getDataPacket();
                    Log.i(tag, "data packet: " + packet.packetType().toString());
                    break;
                case VERSION:
                    MuseVersion version = fileReader.getVersion();
                    Log.i(tag, "version" + version.getFirmwareType());
                    break;
                case CONFIGURATION:
                    MuseConfiguration config = fileReader.getConfiguration();
                    Log.i(tag, "config" + config.getBluetoothMac());
                    break;
                case ANNOTATION:
                    AnnotationData annotation = fileReader.getAnnotation();
                    Log.i(tag, "annotation" + annotation.getData());
                    break;
                default:
                    break;
            }

            // Read the next message.
            res = fileReader.gotoNextMessage();
        }
    }

    //--------------------------------------
    // Listener translators
    //
    // Each of these classes extend from the appropriate listener and contain a weak reference
    // to the activity.  Each class simply forwards the messages it receives back to the Activity.
    class MuseL extends MuseListener {
        final WeakReference<MainActivity> activityRef;

        MuseL(final WeakReference<MainActivity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void museListChanged() {
            activityRef.get().museListChanged();
        }
    }

    class ConnectionListener extends MuseConnectionListener {
        final WeakReference<MainActivity> activityRef;

        ConnectionListener(final WeakReference<MainActivity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseConnectionPacket(final MuseConnectionPacket p, final Muse muse) {
            activityRef.get().receiveMuseConnectionPacket(p, muse);
        }
    }

    class DataListener extends MuseDataListener {
        final WeakReference<MainActivity> activityRef;

        DataListener(final WeakReference<MainActivity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseDataPacket(final MuseDataPacket p, final Muse muse) {
            activityRef.get().receiveMuseDataPacket(p, muse);
        }

        @Override
        public void receiveMuseArtifactPacket(final MuseArtifactPacket p, final Muse muse) {
            activityRef.get().receiveMuseArtifactPacket(p, muse);
        }
    }
}
