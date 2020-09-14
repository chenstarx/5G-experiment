package com.xjtlu.surf;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.os.Message;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;
import java.util.logging.Logger;


public class MainActivity extends AppCompatActivity {
    private MainActivity currentContext = this;
    TelephonyManager MyTelephonyManager;
    MyPhoneStateListener MyListener;

    Button BtnConnect, BtnSend, BtnClose, BtnSendU;
    TextView MyTextView, TcpStatus, TcpSend, UdpSend, LatiView, LongView, netStateView, SpeedView;

    Socket sendSocket, recvSocket;
    DatagramSocket UDPsocket;
    ExecutorService MyThreadPool;

    OutputStream outputStream;

    Timer MainTimer;
    Timer UMainTimer;
    TimerTask MainTask;
    TimerTask UMainTask;

    PowerManager powerManager;
    WakeLock wakeLock;

    SensorManager sensorManager;
    LocationManager locationManager;

    String networkType = "Unknown";
    // if networkType is detected to be 5G
    // then NRNetworkType will specify whether it is
    // SA, NSA or NSA-mmWave

    String NRNetworkType = "Unclear";

    // final String IP = "192.168.0.4";
    String IP = "101.132.97.148";

    private static final int clientSendPort = 55800;
    private static final int clientRecvPort = 55801;

    private static final int clientSendPortUdp = 55802;

    int dbm = -113;
    int sendNum = 0;
    int recvIndex = 0;
    int UsendNum = 0;

    int socketCounter = 0;

    double height = 0.0;

    double latitude = 0.0;
    double longitude = 0.0;
    double speed = 0.0;

    boolean isTcpSending = false;
    boolean isUdpSending = false;
    private ConnectivityManager connectivityManager;
    private NetworkCapabilities mobileNetwork;

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @SuppressLint({"MissingPermission", "InvalidWakeLockTag"})
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.getGlobal().log(Level.INFO, "Init into creation");
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(
                MainActivity.this,
                new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }
                , 1);

        MyThreadPool = Executors.newCachedThreadPool();

        MyTextView = findViewById(R.id.dbm);
        LatiView = findViewById(R.id.latitude);
        SpeedView = findViewById(R.id.speed);
        LongView = findViewById(R.id.longitude);
        TcpStatus = findViewById(R.id.status);
        TcpSend = findViewById(R.id.sendnum);
        UdpSend = findViewById(R.id.usendnum);
        BtnConnect = findViewById(R.id.connect);
        BtnSend = findViewById(R.id.send);
        BtnClose = findViewById(R.id.close);
        BtnSendU = findViewById(R.id.usend);
        netStateView = findViewById(R.id.netstate);
        MyTelephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        Logger.getGlobal().info("Setting connectivity manager");
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        mobileNetwork = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());

        MyListener = new MyPhoneStateListener();

        SocketConnect ConnectListener = new SocketConnect();
        SocketSend SendListener = new SocketSend();
        SocketClose CloseListener = new SocketClose();
        SocketSendU SendUListener = new SocketSendU();

        MyTelephonyManager.listen(MyListener, MyPhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        MyTelephonyManager.listen(MyListener, MyPhoneStateListener.LISTEN_CELL_INFO);
        // update 0901/20: add listen for cell strengths
        // requires API 30
        MyTelephonyManager.listen(MyListener, MyPhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED);

        BtnConnect.setOnClickListener(ConnectListener);
        BtnSend.setOnClickListener(SendListener);
        BtnClose.setOnClickListener(CloseListener);
        BtnSendU.setOnClickListener(SendUListener);

        powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        wakeLock = powerManager != null ? powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MainWakelock") : null;

        sensorManager.registerListener(pressureListener, sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE), SensorManager.SENSOR_DELAY_NORMAL);

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 0, locationListener);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public Handler MainHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case 0x00:
                    TcpStatus.setText(("TCP Status: Closed"));
                    break;
                case 0x01:
                    TcpStatus.setText(("TCP Status: Connected"));
                    break;
                case 0x02:
                    // specify detailed network type
                    String _networkType = "";
                    if (networkType.contains("5G")) {
                        _networkType = NRNetworkType;
                    } else {
//                        _networkType = networkType;
                        _networkType = NRNetworkType;
                    }
                    MyTextView.setText((Integer.toString(dbm) + "dBm"));
                    netStateView.setText(IP + ", " + _networkType);
                    break;

                case 0x03:
                    BtnSend.setText(("<ON> TCP SEND"));
                    TcpStatus.setText(("TCP Status: Sending"));
                    TcpSend.setText(("TCP Data Sent: " + Integer.toString(sendNum)));
                    break;
                case 0x04:
                    BtnSend.setText(("<OFF> TCP SEND"));
                    TcpStatus.setText(("TCP Status: Connected"));
                    break;
                case 0x05:
                    BtnSend.setText(("<OFF> TCP SEND"));
                    TcpStatus.setText(("TCP Status: Closed"));
                    break;
                case 0x06:
                    TcpStatus.setText(("TCP Status: Network Error"));
                    TcpSend.setText(("TCP Data Sent: " + Integer.toString(sendNum)));
                    break;
                case 0x07:
                    BtnSendU.setText(("<ON> UDP SEND"));
                    UdpSend.setText(("UDP Data Sent: " + Integer.toString(UsendNum)));
                    break;
                case 0x08:
                    BtnSendU.setText(("<OFF> UDP SEND"));
                    break;
                default:
                    break;
            }
            return false;
        }
    });


    public SensorEventListener pressureListener = new SensorEventListener() {

        DecimalFormat dFormat = new DecimalFormat("0.0000");

        int initCounter = 0;

        double initHeight = 0;

        boolean initFlag = false;

        @Override
        public void onSensorChanged(SensorEvent event) {

            if (initCounter < 10 && !initFlag) {

                initHeight += 44330000 * (1 - (Math.pow((Double.parseDouble(dFormat.format(event.values[0])) / 1013.25), (float) 1.0 / 5255.0)));

                initCounter++;

            }

            if (initCounter == 10 && !initFlag) {

                initHeight = initHeight / 10;

                initFlag = true;

            }

            if (initFlag) {

                height = 44330000 * (1 - (Math.pow((Double.parseDouble(dFormat.format(event.values[0])) / 1013.25), (float) 1.0 / 5255.0)));

                height -= initHeight;

            }


        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    public LocationListener locationListener = new LocationListener() {

        @Override
        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onProviderEnabled(String arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onProviderDisabled(String arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onLocationChanged(Location location) {
            // TODO Auto-generated method stub

            latitude = location.getLatitude();

            longitude = location.getLongitude();

            speed = location.getSpeed();

            LatiView.setText((Double.toString(latitude)));

            LongView.setText((Double.toString(longitude)));

            SpeedView.setText(String.format("Speed: %.2a m/s", speed));

        }
    };

    public String getNetworkClass() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Logger.getGlobal().info("Failed to acquire phone state permission");
            return "Unknown";
        }
        int networkType = MyTelephonyManager.getNetworkType();
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return "2G";
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "3G";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "4G";
            case TelephonyManager.NETWORK_TYPE_UNKNOWN:  // no TYPE_NR provided for this version of Android SDK
                if (Build.VERSION.SDK_INT < 29) return "Unknown (API too old)";
                else return "Unknown";
            case 20:  // the value for NR. In this case, we will ignore whether this phone supports API 29
                return "5G";
            default:
                if (Build.VERSION.SDK_INT >= 29) {
//                    try{
//                        Field vfield = TelephonyManager.class.getField("NETWORK_TYPE_NR");
//                        int field = vfield.getInt(null);
//                        if (networkType == field) return "5G";
//                    } catch (NoSuchFieldException | IllegalAccessException e) {
//                        Logger.getGlobal().log(Level.SEVERE, "5G inspection failed under SDK 29 or higher. Should\'nt happen");
//                    }

                }
                return "Unknown";
        }
    }

    public boolean isNRConnected() {
        TelephonyManager telephonyManager = this.MyTelephonyManager;
        try {
            Object obj = Class.forName(telephonyManager.getClass().getName())
                    .getDeclaredMethod("getServiceState", new Class[0]).invoke(telephonyManager, new Object[0]);

            Method[] methods = Class.forName(obj.getClass().getName()).getDeclaredMethods();

            for (Method method : methods) {
                if (method.getName().equals("getNrStatus") || method.getName().equals("getNrState")) {
                    method.setAccessible(true);
                    return ((Integer) method.invoke(obj, new Object[0])).intValue() == 3;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public class MyPhoneStateListener extends PhoneStateListener {
        @Override
        public void onCellInfoChanged(List<CellInfo> cellInfo) {
            super.onCellInfoChanged(cellInfo);
            Message msg = Message.obtain();
            int downSpeed = mobileNetwork.getLinkDownstreamBandwidthKbps();
            IP = Integer.toString(downSpeed);

            CellInfoNr nrholder = null;
            CellInfoLte lteholder = null;
            CellInfoCdma cdmaholder = null;

            if (cellInfo == null){
                System.out.println("Empty cell Info, onCellInfoChanged() return");
                return;
            }

            System.out.println("------ acquiring cell info ------");
            for (CellInfo c : cellInfo) {
                System.out.println(c.getClass().toString());
                if (c instanceof CellInfoNr) {
                    CellInfoNr nrc = (CellInfoNr) c;
                    dbm = nrc.getCellSignalStrength().getDbm();
                    networkType = "5G";
                    nrholder = (CellInfoNr) c;
                } else if (c instanceof CellInfoLte) {
                    lteholder = (CellInfoLte) c;
                } else if (c instanceof CellInfoCdma) {
                    cdmaholder = (CellInfoCdma) c;
                }
            }

            if (nrholder != null) {
                dbm = nrholder.getCellSignalStrength().getDbm();
                networkType = "5G";
            } else if (lteholder != null) {
                dbm = lteholder.getCellSignalStrength().getDbm();
                networkType = "4G";
            } else if (cdmaholder != null) {
                dbm = cdmaholder.getCellSignalStrength().getDbm();
                networkType = "3G";
            }

            System.out.println("------ ending cell info ------");
            msg.what = 0x02;
            MainHandler.sendMessage(msg);
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            super.onSignalStrengthsChanged(signalStrength);
            Message msg = Message.obtain();
            int downSpeed = mobileNetwork.getLinkDownstreamBandwidthKbps();
//            int upSpeed = mobileNetwork.getLinkUpstreamBandwidthKbps();
            IP = Integer.toString(downSpeed);
            try {
                // networkType is a String variable referenced by logger
//                if (isNRConnected()){
//                    networkType = "5G";
//                }else {
//                    networkType = getNetworkClass();
//                }
                Logger.getGlobal().info("acquiring cell info");
                System.out.println("------ acquiring cell info ------");
                for (CellSignalStrength c : signalStrength.getCellSignalStrengths()) {
                    System.err.println(c.getClass().getName());
                    CellSignalStrengthNr nrholder = null;
                    CellSignalStrengthLte lteholder = null;
                    CellSignalStrengthCdma cdmaholder = null;

                    if (c instanceof CellSignalStrengthNr) {
                        nrholder = (CellSignalStrengthNr) c;
                    } else if (c instanceof CellSignalStrengthLte) {
                        lteholder = (CellSignalStrengthLte) c;
                    } else if (c instanceof CellSignalStrengthCdma) {
                        cdmaholder = (CellSignalStrengthCdma) c;
                    }

                    if (nrholder != null) {
                        dbm = nrholder.getDbm();
                        networkType = "5G";
                    } else if (lteholder != null) {
                        dbm = lteholder.getDbm();
                        networkType = "4G";
                    } else if (cdmaholder != null) {
                        dbm = cdmaholder.getDbm();
                        networkType = "3G";
                    }
                }
                System.out.println("------ ending cell info ------");

//                dbm = (Integer) signalStrength.getClass().getMethod("getDbm").invoke(signalStrength);
            } catch (Exception e) {
                System.err.println("dbm updateai fled");
                e.printStackTrace();
            }

            msg.what = 0x02;
            MainHandler.sendMessage(msg);
        }

        @RequiresApi(30)
        @Override
        public void onDisplayInfoChanged(TelephonyDisplayInfo displayInfo) {
            if (ActivityCompat.checkSelfPermission(currentContext, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                Logger.getGlobal().info("Failed to acquire phone state permission");
                System.out.println("Failed to acquire phone state permission");
                return;
            }
            super.onDisplayInfoChanged(displayInfo);
            Logger.getGlobal().info("Display info changed: triggered");

            int overrideNetworkType = displayInfo.getOverrideNetworkType();
            System.out.println(overrideNetworkType);
             switch (overrideNetworkType){
                 case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO:
                     NRNetworkType = "5G-SA";
                     break;
                 case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA:
                     NRNetworkType = "5G-NSA-base";
                     break;
                 case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE:
                     NRNetworkType = "5G-NSA-mmWave";
                     break;
                 default:
                     NRNetworkType = "Unclear-refreshed";
             }
         }
		        
        
    }

    public class SocketConnect implements OnClickListener {
        public void onClick(View v) {
            new Thread(new ConnectThread()).start();
        }
    }

    public class SocketSend implements OnClickListener {
        public void onClick(View v) {
            new Thread(new SendThread()).start();
            new Thread(new RecvThread()).start();
        }
    }

    public class SocketSendU implements OnClickListener {
        public void onClick(View v) { new Thread(new SendUThread()).start(); }
    }

    public class SocketClose implements OnClickListener {
        public void onClick(View v) {
            Message msg = Message.obtain();
            try {
                sendNum = 0;

                if (MainTimer != null && MainTask != null) {

                    MainTimer.purge();
                    MainTimer.cancel();
                    MainTimer = null;
                    MainTask.cancel();
                    MainTask = null;

                }

                if (outputStream != null) {
                    outputStream.close();
                    outputStream = null;
                }

                if (sendSocket != null) {

                    sendSocket.close();

                    sendSocket = null;
                }

                if (recvSocket != null){

                    recvSocket.close();

                    recvSocket = null;
                }

                if (isTcpSending) {
                    wakeLock.release();
                    isTcpSending = false;
                }

                msg.what = 0x05;
                MainHandler.sendMessage(msg);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class ConnectThread implements Runnable {
        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public void run() {
            Message msg = Message.obtain();
            try {

                if (sendSocket == null) {
                    sendSocket = new Socket(IP, clientSendPort);
                    Logger.getGlobal().log(Level.INFO, "send Socket Success");
                }

                if (recvSocket == null) {
                    recvSocket = new Socket(IP, clientRecvPort);
                    Logger.getGlobal().log(Level.INFO, "send Socket Success");
                }
                
                // if not enter catch, the sockets are connected
                msg.what = 0x01;
                MainHandler.sendMessage(msg);

            } catch (IOException e) {
                msg.what = 0x06;
                MainHandler.sendMessage(msg);

                e.printStackTrace();
            }
        }
    }

    public class RecvThread implements Runnable {
        SimpleDateFormat tFormatFile = new SimpleDateFormat("yyyy-MM-dd HHmm", Locale.CHINA);

        String MyFileName = tFormatFile.format(System.currentTimeMillis()) + " Recv.txt";

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public void run() {
            InputStreamReader directIn = null;
            BufferedReader bufferedIn = null;

            if (recvSocket != null) {
                try {
                    directIn = new InputStreamReader(recvSocket.getInputStream());
                    bufferedIn = new BufferedReader(directIn);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            while (true){
                // end the thread when cancel sending
                if (!isTcpSending) break;

                try {
                    if (bufferedIn == null || recvSocket == null){
                        throw new IOException();
                    }

                    String recvData = bufferedIn.readLine();  // todo: check maybe-wrong: buffere invalid

                    long receivedTime = System.currentTimeMillis();

                    if (recvData == "" || recvData == null) {
                        Logger.getGlobal().log(Level.WARNING, "Null Recv");
                        throw new IOException();
                    }

                    String[] splited = recvData.split("; ");
                    Logger.getGlobal().log(Level.WARNING, recvData);

                    int dataIndex = 0;
                    double processTime = 0;
                    long sentTime = 0;

                    try {
                        dataIndex = Integer.parseInt(splited[0]);
                        processTime = Double.parseDouble(splited[1]);
                        sentTime = Long.parseLong(splited[2]);

                    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {

                        Logger.getGlobal().log(Level.SEVERE, "Error with recv: " + recvData);
                        Logger.getGlobal().log(Level.SEVERE, e.getMessage());

                    }

                    double duration = (double) receivedTime - (double) sentTime - processTime;

                    String message = "Index: " + Integer.toString(dataIndex)  
                        + "; ReceivedTime: " + Long.toString(receivedTime) 
                        + "; SentTime: " + Long.toString(sentTime)
                        + "; ProcessTime: " + String.format("%.3f", processTime)
                        + "; Duration: " + String.format("%.3f", duration) + "\r\n";

                    fileWrite(MyFileName, message);
                    Logger.getGlobal().log(Level.INFO, message);

                } catch (IOException e) {
                    try {
                        if (recvSocket == null){
                            Thread.sleep(500);
                            if (!isTcpSending) break;
                            recvSocket = new Socket(IP, clientRecvPort);
                            directIn = new InputStreamReader(recvSocket.getInputStream());
                            bufferedIn = new BufferedReader(directIn);
                        } else {
                            directIn.close();
                            bufferedIn.close();
                            recvSocket.close();
                            recvSocket = null;
                        }
                    } catch (IOException | InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }
    }

    public class SendThread implements Runnable {

        SimpleDateFormat tFormatFile = new SimpleDateFormat("yyyy-MM-dd HHmm", Locale.CHINA);

        SimpleDateFormat tFormat = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);

        String MyFileName = tFormatFile.format(System.currentTimeMillis()) + ".txt";

        DecimalFormat dFormat = new DecimalFormat("0.00");

        DecimalFormat lFormat = new DecimalFormat("0.00000000");

        @Override
        public void run() {

            if (sendSocket != null) {

                if (isTcpSending) {

                    isTcpSending = false;
                    wakeLock.release();

                    if (MainTimer != null && MainTask != null) {
                        MainTimer.purge();
                        MainTimer.cancel();
                        MainTimer = null;

                        MainTask.cancel();
                        MainTask = null;

                        Message msg = Message.obtain();

                        msg.what = 0x04;
                        MainHandler.sendMessage(msg);
                    }

                } else {
                    isTcpSending = true;

                    MainTimer = new Timer();

                    wakeLock.acquire(3600 * 1000);

                    MainTask = new TimerTask() {
                        @Override
                        public void run() {

                            long currentTime = System.currentTimeMillis();

                            Message msg = Message.obtain();

                            try {

                                if (sendSocket != null) {

                                    String message = "Time: " + tFormat.format(currentTime) + "; Timestamp: " + Long.toString(currentTime) + "; CSQ: " + Integer.toString(dbm)
                                    + "; Height: " + dFormat.format(height) + "; Lati: " + lFormat.format(latitude) + "; Long: " + lFormat.format(longitude)
                                    + "; Index: " + Integer.toString(sendNum) + "; Speed: " + dFormat.format(speed) + "; Network: "+ networkType +  "\r\n";

                                    fileWrite(MyFileName, message);

                                    outputStream = sendSocket.getOutputStream();

                                    outputStream.write(message.getBytes("utf-8"));

                                    outputStream.flush();

                                    sendNum ++;

                                    msg.what = 0x03;
                                    MainHandler.sendMessage(msg);

                                } else {

                                    try {
                                        socketCounter ++;
                                        if (socketCounter > 100) {
                                            socketCounter = 0;
                                            sendSocket = new Socket(IP, clientSendPort);
                                            msg.what = 0x01;
                                            MainHandler.sendMessage(msg);

                                            // isTcpSending = true;

                                        } else {

                                            msg.what = 0x06;

                                            MainHandler.sendMessage(msg);
                                        }

                                    } catch (IOException e) {
                                        msg.what = 0x06;
                                        MainHandler.sendMessage(msg);
                                        if (sendSocket != null) {
                                            try {
                                                sendSocket.close();
                                            } catch (IOException eb) {
                                                eb.printStackTrace();
                                            }
                                            sendSocket = null;
                                        }
                                    }
                                }

                            } catch (Exception e) {
                                // Connection Break Because of Network Error
                                // if (isTcpSending) isTcpSending = false;

                                if (outputStream != null) {
                                    try {
                                        outputStream.close();
                                    } catch (IOException ea) {
                                        ea.printStackTrace();
                                    }
                                    outputStream = null;
                                }

                                if (sendSocket != null) {
                                    try {
                                        sendSocket.close();
                                    } catch (IOException eb) {
                                        eb.printStackTrace();
                                    }
                                    sendSocket = null;
                                }

                                msg.what = 0x05;
                                MainHandler.sendMessage(msg);

                                e.printStackTrace();
                            }
                        }
                    };
                    MainTimer.schedule(MainTask, 0, 5);
                }
            }
        }
    }

    public class SendUThread implements Runnable {

        SimpleDateFormat tFormatFile = new SimpleDateFormat("yyyy-MM-dd HHmm", Locale.CHINA);

        SimpleDateFormat tFormat = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);

        String MyFileName = tFormatFile.format(System.currentTimeMillis()) + " UDP.txt";

        DecimalFormat dFormat = new DecimalFormat("0.00");

        DecimalFormat lFormat = new DecimalFormat("0.00000000");

        @Override
        public void run() {

            if (isUdpSending) {

                isUdpSending = false;

                if (UMainTimer != null && UMainTask != null) {

                    UMainTimer.purge();
                    UMainTimer.cancel();
                    UMainTimer = null;

                    UMainTask.cancel();
                    UMainTask = null;

                    Message msg = Message.obtain();

                    msg.what = 0x08;
                    MainHandler.sendMessage(msg);
                }

            } else {

                isUdpSending = true;

                UMainTimer = new Timer();

                UMainTask = new TimerTask() {
                    @Override
                    public void run() {

                        Message msg = Message.obtain();

                        try {

                            if (UDPsocket != null) {
                                String _networkType = "";
                                if (networkType.contains("5G")){
                                    _networkType = NRNetworkType;
                                }else{
//                                    _networkType = networkType;
                                    _networkType = NRNetworkType;

                                }
                                String message = "Time: " + tFormat.format(System.currentTimeMillis()) + "; CSQ: " + Integer.toString(dbm)
                                + "; Height: " + dFormat.format(height) + "; Lati: " + lFormat.format(latitude) + "; Long: " + lFormat.format(longitude) 
                                + "; Speed: " + dFormat.format(speed) + "; Index: " + Integer.toString(UsendNum) + "; Network: "+ _networkType +  "\r\n";
                                
                                fileWrite(MyFileName, message);

                                byte bMessage[] = message.getBytes();

                                InetAddress serverAddress = InetAddress.getByName(IP);

                                DatagramPacket packet = new DatagramPacket(bMessage, bMessage.length, serverAddress, clientSendPortUdp);

                                UDPsocket.send(packet);

                                UsendNum ++;

                                msg.what = 0x07;
                                MainHandler.sendMessage(msg);
                            } else {
                                UDPsocket = new DatagramSocket();
                            }
                        } catch (Exception e) {
                            msg.what = 0x07;
                            MainHandler.sendMessage(msg);
                            e.printStackTrace();
                        }
                    }
                };
                try {
                    UDPsocket = new DatagramSocket();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                UMainTimer.schedule(UMainTask, 0, 5);
            }
        }
    }

    synchronized void fileWrite(String fileName, String data) {

        String filePath = "/mnt/sdcard/surf/";
        String fullFilePath = filePath+fileName;

        try {

            File fileP = new File(filePath);
            if (!fileP.exists()) {
                fileP.mkdir();
            }

            File file=new File(fullFilePath);
            if (!file.exists()) {
                file.createNewFile();
            }

            FileOutputStream fos=new FileOutputStream(file,true);

            fos.write(data.getBytes());

            fos.flush();

        } catch (IOException e) {

            e.printStackTrace();

        }
    }
}