package com.yiyi45;

import android.content.Context;
import android.content.Intent;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;

import com.uk.tsl.rfid.asciiprotocol.AsciiCommander;
import com.uk.tsl.rfid.asciiprotocol.DeviceProperties;
import com.uk.tsl.rfid.asciiprotocol.commands.FactoryDefaultsCommand;
import com.uk.tsl.rfid.asciiprotocol.device.ConnectionState;
import com.uk.tsl.rfid.asciiprotocol.device.IAsciiTransport;
import com.uk.tsl.rfid.asciiprotocol.device.ObservableReaderList;
import com.uk.tsl.rfid.asciiprotocol.device.Reader;
import com.uk.tsl.rfid.asciiprotocol.device.ReaderManager;
import com.uk.tsl.rfid.asciiprotocol.device.TransportType;
import com.uk.tsl.rfid.asciiprotocol.enumerations.QuerySession;
import com.uk.tsl.rfid.asciiprotocol.enumerations.TriState;
import com.uk.tsl.rfid.asciiprotocol.parameters.AntennaParameters;
import com.uk.tsl.rfid.asciiprotocol.responders.LoggerResponder;
import com.uk.tsl.utils.Observable;

public class RFIDAsciiProtocol extends CordovaPlugin {
    private static final String TAG = "RFIDAsciiProtocol";
    
	// All of the reader inventory tasks are handled by this class
	private InventoryModel mModel;

    // The Reader currently in use
    private Reader mReader = null;

    public RFIDAsciiProtocol() {
    }

    private void init() {
        mGenericModelHandler = new GenericHandler(this);

        Activity activity = cordova.getActivity();
        
		// Ensure the shared instance of AsciiCommander exists
        AsciiCommander.createSharedInstance(activity.getApplicationContext());
    	AsciiCommander commander = getCommander();

        // Ensure that all existing responders are removed
        commander.clearResponders();

		// Add the LoggerResponder - this simply echoes all lines received from the reader to the log
        // and passes the line onto the next responder
        // This is added first so that no other responder can consume received lines before they are logged.
        commander.addResponder(new LoggerResponder());

        // Add a synchronous responder to handle synchronous commands
        commander.addSynchronousResponder();

        // Create the single shared instance for this ApplicationContext
        ReaderManager.create(activity.getApplicationContext());

        // Add observers for changes
        ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().addObserver(mRemovedObserver);

        //Create a (custom) model and configure its commander and handler
        mModel = new InventoryModel();
        mModel.setCommander(getCommander());
        mModel.setHandler(mGenericModelHandler);
        mModel.setEnabled(true);

        // Register to receive notifications from the AsciiCommander
        LocalBroadcastManager.getInstance(activity).registerReceiver(mCommanderMessageReceiver, new IntentFilter(AsciiCommander.STATE_CHANGED_NOTIFICATION));
        // The ReaderManager needs to know about Activity lifecycle changes
        ReaderManager.sharedInstance().onResume();

        // The Activity may start with a reader already connected (perhaps by another App)
        // Update the ReaderList which will add any unknown reader, firing events appropriately
        ReaderManager.sharedInstance().updateList();
    }

    //----------------------------------------------------------------------------------------------
    // ReaderList Observers
    //----------------------------------------------------------------------------------------------

    Observable.Observer<Reader> mRemovedObserver = new Observable.Observer<Reader>()
    {
        @Override
        public void update(Observable<? extends Reader> observable, Reader reader)
        {
            // Was the current Reader removed
            if( reader == mReader)
            {
                mReader = null;

                // Stop using the old Reader
                getCommander().setReader(mReader);
            }
        }
    };

    //----------------------------------------------------------------------------------------------
	// Model notifications
	//----------------------------------------------------------------------------------------------

    private static class GenericHandler extends WeakHandler<RFIDAsciiProtocol>
    {
        public GenericHandler(RFIDAsciiProtocol t)
        {
            super(t);
        }

        @Override
        public void handleMessage(Message msg, RFIDAsciiProtocol t)
        {
			try {
				switch (msg.what) {
				case ModelBase.MESSAGE_NOTIFICATION:
					// Examine the message for prefix
					String message = (String)msg.obj;
					if( message.startsWith("BC:")) {
                        // Barcode Handler
                        final PluginResult result = new PluginResult(PluginResult.Status.OK, message);
                        result.setKeepCallback(true);
                        t.callback.sendPluginResult(result);
					}
					break;
					
				default:
					break;
				}
			} catch (Exception e) {
			}
			
		}
    };
    
    // The handler for model messages
    private static GenericHandler mGenericModelHandler;

    //----------------------------------------------------------------------------------------------
	// AsciiCommander message handling
	//----------------------------------------------------------------------------------------------

    /**
     * @return the current AsciiCommander
     */
    protected AsciiCommander getCommander()
    {
        return AsciiCommander.sharedInstance();
    }

    //
    // Handle the messages broadcast from the AsciiCommander
    //
    private BroadcastReceiver mCommanderMessageReceiver = new BroadcastReceiver() {
    	@Override
    	public void onReceive(Context context, Intent intent) {
            if( getCommander().isConnected() )
            {    			
            	mModel.resetDevice();
                mModel.updateConfiguration();
            }
    	}
    };



    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

    public CallbackContext callback = null;
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if("init".equals(action)) {
            init();
        }
        else if ("isConnected".equals(action)) {
            final PluginResult result = new PluginResult(PluginResult.Status.OK, String.valueOf(isConnected()));
            callbackContext.sendPluginResult(result);
        }
        else if ("connect".equals(action)) {
            callback = callbackContext;
            connect();
        }
        else if("disconnect".equals(action)) {
            disconnect();
        }
        else if("scan".equals(action)) {
            scan();
        }
        return true;
    }

    private boolean isConnected() {
        return mReader != null && mReader.isConnected();
    }

    private void connect() {
        cordova.setActivityResultCallback(this);
        Context context = cordova.getActivity().getApplicationContext();
        Intent selectIntent = new Intent(context, DeviceListActivity.class);
        cordova.startActivityForResult(this, selectIntent, DeviceListActivity.SELECT_DEVICE_REQUEST);
    }

    private void disconnect() {
        if( mReader != null )
        {
            mReader.disconnect();
            mReader = null;
        }
    }

    private void scan() {
        try {
            // Perform a transponder scan
            mModel.scan();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //
    // Handle Intent results
    //
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode)
        {
            case DeviceListActivity.SELECT_DEVICE_REQUEST:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK)
                {
                    int readerIndex = data.getExtras().getInt(EXTRA_DEVICE_INDEX);
                    Reader chosenReader = ReaderManager.sharedInstance().getReaderList().list().get(readerIndex);

                    int action = data.getExtras().getInt(EXTRA_DEVICE_ACTION);

                    // If already connected to a different reader then disconnect it
                    if( mReader != null )
                    {
                        if( action == DeviceListActivity.DEVICE_CHANGE || action == DeviceListActivity.DEVICE_DISCONNECT)
                        {
                            mReader.disconnect();
                            if(action == DeviceListActivity.DEVICE_DISCONNECT)
                            {
                                mReader = null;
                            }
                        }
                    }

                    // Use the Reader found
                    if( action == DeviceListActivity.DEVICE_CHANGE || action == DeviceListActivity.DEVICE_CONNECT)
                    {
                        mReader = chosenReader;
                        getCommander().setReader(mReader);
                    }

                    final PluginResult result = new PluginResult(PluginResult.Status.OK, "Connected");
                    result.setKeepCallback(true);
                    callback.sendPluginResult(result);
                    return;
                }
                break;
        }
        final PluginResult result = new PluginResult(PluginResult.Status.OK, "Failed");
        result.setKeepCallback(true);
        callback.sendPluginResult(result);
    }
}
