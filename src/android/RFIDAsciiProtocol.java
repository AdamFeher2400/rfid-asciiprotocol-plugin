package com.yiyi45;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;

import com.uk.tsl.rfid.DeviceListActivity;
import com.uk.tsl.rfid.ModelBase;
import com.uk.tsl.rfid.WeakHandler;
import com.uk.tsl.rfid.asciiprotocol.AsciiCommander;
import com.uk.tsl.rfid.asciiprotocol.device.ConnectionState;
import com.uk.tsl.rfid.asciiprotocol.device.IAsciiTransport;
import com.uk.tsl.rfid.asciiprotocol.device.ObservableReaderList;
import com.uk.tsl.rfid.asciiprotocol.device.Reader;
import com.uk.tsl.rfid.asciiprotocol.device.ReaderManager;
import com.uk.tsl.rfid.asciiprotocol.device.TransportType;
import com.uk.tsl.rfid.asciiprotocol.enumerations.QuerySession;
import com.uk.tsl.rfid.asciiprotocol.responders.LoggerResponder;
import com.uk.tsl.rfid.samples.inventory.InventoryModel;
import com.uk.tsl.utils.Observable;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;

import static com.uk.tsl.rfid.DeviceListActivity.EXTRA_DEVICE_ACTION;
import static com.uk.tsl.rfid.DeviceListActivity.EXTRA_DEVICE_INDEX;

public class RFIDAsciiProtocol extends CordovaPlugin {
  // All of the reader inventory tasks are handled by this class
  private InventoryModel mModel;

  // The Reader currently in use
  private Reader mReader = null;
  private boolean mIsSelectingReader = false;

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
    ReaderManager.sharedInstance().getReaderList().readerAddedEvent().removeObserver(mAddedObserver);
    ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().addObserver(mRemovedObserver);

    //Create a (custom) model and configure its commander and handler
    mModel = new InventoryModel();
    mModel.setCommander(getCommander());
    mModel.setHandler(mGenericModelHandler);

    if (mModel.getCommand() != null) {
      mModel.getCommand().setQuerySession(QuerySession.SESSION_0);
      mModel.updateConfiguration();
    }

    onResume(true);
  }

  //----------------------------------------------------------------------------------------------
  // ReaderList Observers
  //----------------------------------------------------------------------------------------------

  Observable.Observer<Reader> mAddedObserver = (observable, reader) -> {
    // See if this newly added Reader should be used
    AutoSelectReader(true);
  };

  Observable.Observer<Reader> mRemovedObserver = (observable, reader) -> {
    // Was the current Reader removed
    if (reader == mReader) {
      mReader = null;

      // Stop using the old Reader
      getCommander().setReader(mReader);
    }
  };

  //----------------------------------------------------------------------------------------------
  // Model notifications
  //----------------------------------------------------------------------------------------------

  private static class GenericHandler extends WeakHandler<RFIDAsciiProtocol> {
    public GenericHandler(RFIDAsciiProtocol t) {
      super(t);
    }

    @Override
    public void handleMessage(Message msg, RFIDAsciiProtocol t) {
      try {
        switch (msg.what) {
          case ModelBase.MESSAGE_NOTIFICATION:
            // Examine the message for prefix
            String message = (String) msg.obj;
            if (message.startsWith("BC:")) {
              // Barcode Handler
              final PluginResult result = new PluginResult(PluginResult.Status.OK, message);
              result.setKeepCallback(true);
              t.callback.sendPluginResult(result);
            } else if(!message.startsWith("ER:")) {
              // RFID Chip Handler
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
  }

  ;

  // The handler for model messages
  private static GenericHandler mGenericModelHandler;

  //----------------------------------------------------------------------------------------------
  // AsciiCommander message handling
  //----------------------------------------------------------------------------------------------

  /**
   * @return the current AsciiCommander
   */
  protected AsciiCommander getCommander() {
    return AsciiCommander.sharedInstance();
  }

  //
  // Handle the messages broadcast from the AsciiCommander
  //
  private BroadcastReceiver mCommanderMessageReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (getCommander().isConnected()) {
        mModel.resetDevice();
        mModel.updateConfiguration();

        final PluginResult result = new PluginResult(PluginResult.Status.OK, "ER:Connected");
        result.setKeepCallback(true);
        callback.sendPluginResult(result);
      }
    }
  };


  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
  }

  public CallbackContext callback = null;

  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
    if ("init".equals(action)) {
      init();
    } else if ("isConnected".equals(action)) {
      final PluginResult result = new PluginResult(PluginResult.Status.OK, String.valueOf(isConnected()));
      callbackContext.sendPluginResult(result);
    } else if ("connect".equals(action)) {
      callback = callbackContext;
      connect();
    } else if ("disconnect".equals(action)) {
      disconnect();
    } else if ("scan".equals(action)) {
      scan();
    }
    return true;
  }

  private boolean isConnected() {
    return getCommander().isConnected();
  }

  private void connect() {
    mIsSelectingReader = true;

    cordova.setActivityResultCallback(this);
    Context context = cordova.getActivity().getApplicationContext();
    Intent selectIntent = new Intent(context, DeviceListActivity.class);
    cordova.startActivityForResult(this, selectIntent, DeviceListActivity.SELECT_DEVICE_REQUEST);
  }

  private void disconnect() {
    if (mReader != null) {
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

  @Override
  public void onPause(boolean multitasking) {
    super.onPause(multitasking);

    mModel.setEnabled(false);

    // Unregister to receive notifications from the AsciiCommander
    LocalBroadcastManager.getInstance(cordova.getActivity()).unregisterReceiver(mCommanderMessageReceiver);

    // Disconnect from the reader to allow other Apps to use it
    // unless pausing when USB device attached or using the DeviceListActivity to select a Reader
    if (!mIsSelectingReader && !ReaderManager.sharedInstance().didCauseOnPause() && mReader != null) {
      mReader.disconnect();
    }

    ReaderManager.sharedInstance().onPause();
  }

  @Override
  public void onResume(boolean multitasking) {
    super.onResume(multitasking);

    mModel.setEnabled(true);

    // Register to receive notifications from the AsciiCommander
    LocalBroadcastManager.getInstance(cordova.getActivity()).registerReceiver(mCommanderMessageReceiver, new IntentFilter(AsciiCommander.STATE_CHANGED_NOTIFICATION));

    // Remember if the pause/resume was caused by ReaderManager - this will be cleared when ReaderManager.onResume() is called
    boolean readerManagerDidCauseOnPause = ReaderManager.sharedInstance().didCauseOnPause();

    // The ReaderManager needs to know about Activity lifecycle changes
    ReaderManager.sharedInstance().onResume();

    // The Activity may start with a reader already connected (perhaps by another App)
    // Update the ReaderList which will add any unknown reader, firing events appropriately
    ReaderManager.sharedInstance().updateList();

    // Locate a Reader to use when necessary
    AutoSelectReader(!readerManagerDidCauseOnPause);

    mIsSelectingReader = false;
  }

  //
  // Handle Intent results
  //
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case DeviceListActivity.SELECT_DEVICE_REQUEST:
        // When DeviceListActivity returns with a device to connect
        if (resultCode == Activity.RESULT_OK) {
          int readerIndex = data.getExtras().getInt(EXTRA_DEVICE_INDEX);
          Reader chosenReader = ReaderManager.sharedInstance().getReaderList().list().get(readerIndex);

          int action = data.getExtras().getInt(EXTRA_DEVICE_ACTION);

          // If already connected to a different reader then disconnect it
          if (mReader != null) {
            if (action == DeviceListActivity.DEVICE_CHANGE || action == DeviceListActivity.DEVICE_DISCONNECT) {
              mReader.disconnect();
              if (action == DeviceListActivity.DEVICE_DISCONNECT) {
                mReader = null;
              }
            }
          }

          // Use the Reader found
          if (action == DeviceListActivity.DEVICE_CHANGE || action == DeviceListActivity.DEVICE_CONNECT) {
            mReader = chosenReader;
            getCommander().setReader(mReader);
            final PluginResult result = new PluginResult(PluginResult.Status.OK, "ER:Connecting");
            result.setKeepCallback(true);
            callback.sendPluginResult(result);
          }
        }
        break;
    }
  }

  private void AutoSelectReader(boolean attemptReconnect) {
    ObservableReaderList readerList = ReaderManager.sharedInstance().getReaderList();
    Reader usbReader = null;
    if (readerList.list().size() >= 1) {
      // Currently only support a single USB connected device so we can safely take the
      // first CONNECTED reader if there is one
      for (Reader reader : readerList.list()) {
        IAsciiTransport transport = reader.getActiveTransport();
        if (reader.hasTransportOfType(TransportType.USB)) {
          usbReader = reader;
          break;
        }
      }
    }

    if (mReader == null) {
      if (usbReader != null) {
        // Use the Reader found, if any
        mReader = usbReader;
        getCommander().setReader(mReader);
      }
    } else {
      // If already connected to a Reader by anything other than USB then
      // switch to the USB Reader
      IAsciiTransport activeTransport = mReader.getActiveTransport();
      if (activeTransport != null && activeTransport.type() != TransportType.USB && usbReader != null) {
        mReader.disconnect();

        mReader = usbReader;

        // Use the Reader found, if any
        getCommander().setReader(mReader);
      }
    }

    // Reconnect to the chosen Reader
    if (mReader != null && (mReader.getActiveTransport() == null || mReader.getActiveTransport().connectionStatus().value() == ConnectionState.DISCONNECTED)) {
      // Attempt to reconnect on the last used transport unless the ReaderManager is cause of OnPause (USB device connecting)
      if (attemptReconnect) {
        if (mReader.allowMultipleTransports() || mReader.getLastTransportType() == null) {
          // Reader allows multiple transports or has not yet been connected so connect to it over any available transport
          mReader.connect();
        } else {
          // Reader supports only a single active transport so connect to it over the transport that was last in use
          mReader.connect(mReader.getLastTransportType());
        }

        if(callback != null) {
          final PluginResult result = new PluginResult(PluginResult.Status.OK, "ER:Connecting");
          result.setKeepCallback(true);
          callback.sendPluginResult(result);
        }
      }
    }
  }
}
