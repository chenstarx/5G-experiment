package com.xjtlu.surf;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;
import java.util.logging.Logger;


public class MainActivity extends AppCompatActivity {

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

    // final String IP = "192.168.0.4";
    final String IP = "101.132.97.148";

    private static final int clientSendPort = 55800;
    private static final int clientRecvPort = 55801;

    int dbm = -113;
    int sendNum = 0;
    int recvIndex = 0;
    int UsendNum = 0;

    int socketCounter = 0;

    double height = 0.0;

    double latitude = 0.0;
    double longitude = 0.0;
    double speed = 0.0;

    boolean BtnSendFlag = false;
    boolean BtnSendUFlag = false;

    @SuppressLint({"MissingPermission", "InvalidWakeLockTag"})
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.getGlobal().log(Level.INFO, "Init into creation");
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(
            MainActivity.this,
            new String[] {
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

        MyTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        MyListener = new MyPhoneStateListener();

        SocketConnect ConnectListener = new SocketConnect();
        SocketSend SendListener = new SocketSend();
        SocketClose CloseListener = new SocketClose();
        SocketSendU SendUListener = new SocketSendU();

        MyTelephonyManager.listen(MyListener, MyPhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

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
            switch (msg.what){
                case 0x00:
                    TcpStatus.setText(("TCP Status: Closed"));
                    break;
                case 0x01:
                    TcpStatus.setText(("TCP Status: Connected"));
                    break;
                case 0x02:
                    MyTextView.setText((Integer.toString(dbm) + "dBm"));
                    netStateView.setText(IP + ", " + networkType);
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

                initHeight += 44330000*(1 - ( Math.pow ( ( Double.parseDouble(dFormat.format(event.values[0]))/1013.25), (float) 1.0/5255.0 )));

                initCounter ++;

            }

            if (initCounter == 10 && !initFlag) {

                initHeight = initHeight / 10;

                initFlag = true;

            }

            if (initFlag) {

                height = 44330000 * ( 1 - (Math.pow((Double.parseDouble(dFormat.format(event.values[0]))/1013.25), (float)1.0/5255.0)));

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
        public void onLocationChanged (Location location) {
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
                if (Build.VERSION.SDK_INT < 29) return "5G or Unknown";
                else return "Unknown";
            default:
                if (Build.VERSION.SDK_INT >= 29) {
                    try{
                        Field vfield = TelephonyManager.class.getField("NETWORK_TYPE_NR");
                        int field = vfield.getInt(null);
                        if (networkType == field) return "5G";
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        Logger.getGlobal().log(Level.SEVERE, "5G inspection failed under SDK 29 or higher. Should\'nt happen");
                    }
                }
                return "Unknown";
        }
    }

    public class MyPhoneStateListener extends PhoneStateListener {
        @Override
        public void onSignalStrengthsChanged (SignalStrength signalStrength) {

            super.onSignalStrengthsChanged(signalStrength);

            Message msg = Message.obtain();

            try {
                networkType = getNetworkClass();

                dbm = (Integer) signalStrength.getClass().getMethod("getDbm").invoke(signalStrength);
                /* The one-line code above equals to
                Method theMethod = null;
                theMethod = signalStrength.getClass().getMethod("getDbm");
                dbm = theMethod.invoke(signalStrength); */
            } catch (Exception e) {
                e.printStackTrace();
            }

            msg.what = 0x02;
            MainHandler.sendMessage(msg);
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

                if (BtnSendFlag) {
                    wakeLock.release();
                    BtnSendFlag = false;
                }

                msg.what = 0x05;
                MainHandler.sendMessage(msg);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class ConnectThread implements Runnable {
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

        @Override
        public void run() {
            InputStreamReader directIn = null;
            BufferedReader bufferedIn = null;

            try {
                directIn = new InputStreamReader(recvSocket.getInputStream());
                bufferedIn = new BufferedReader(directIn);
            } catch (IOException e) {
                e.printStackTrace();
            }

            while (true){
                try {
                    if (bufferedIn == null || recvSocket == null){
                        throw new IOException();
                    }

                    String recvData = bufferedIn.readLine();  // todo: check maybe-wrong: buffere invalid

                    double receivedTime = (double) System.currentTimeMillis();

                    if (recvData == null){
                        Logger.getGlobal().log(Level.WARNING, "Null recv");
                        throw new IOException();
                    }

                    String[] splited = recvData.replace("; ", ": ").replace("\n", "").split(": ");
                    Logger.getGlobal().log(Level.WARNING, recvData);

                    int dataIndex = 0;
                    double processTime = 0;
                    double sentTime = 0;

                    try {
                        dataIndex = Integer.parseInt(splited[1].replace(" ", ""));
                        processTime = Double.parseDouble(splited[3].replace(" ", ""));
                        sentTime = Double.parseDouble(splited[5].replace(" ", ""));

                    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {

                        Logger.getGlobal().log(Level.SEVERE, "Error with recv: " + recvData);
                        Logger.getGlobal().log(Level.SEVERE, e.getMessage());

                    }

                    double duration = receivedTime - sentTime - processTime;

                    String message = "Index: " + Integer.toString(dataIndex) 
                        + "; Duration: " + String.format("%.3f", duration) 
                        + "; ReceivedTime: " + Long.toString((long)receivedTime) + "\r\n";

                    fileWrite(MyFileName, message);
                    Logger.getGlobal().log(Level.INFO, message);

                } catch (IOException e) {
                    try {
                        if (recvSocket != null){
                            directIn.close();
                            bufferedIn.close();
                            recvSocket.close();
                            recvSocket = null;
                        }

                        if (socketCounter > 99){  // before sender resets
                            recvSocket = new Socket(IP, clientRecvPort);
                            directIn = new InputStreamReader(recvSocket.getInputStream());
                            bufferedIn = new BufferedReader(directIn);
                        }

                    } catch (IOException e1) {
                        System.err.println("socket IOException handling failed");
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

                if (BtnSendFlag) {

                    BtnSendFlag = false;

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
                    BtnSendFlag = true;

                    MainTimer = new Timer();

                    wakeLock.acquire(3600 * 1000);

                    MainTask = new TimerTask() {
                        @Override
                        public void run() {
                            sendNum ++;

                            long currentTime = System.currentTimeMillis();

                            String message = "Time: " + tFormat.format(currentTime) + "; Timestamp: " + Long.toString(currentTime) + "; CSQ: " + Integer.toString(dbm)
                                    + "; Height: " + dFormat.format(height) + "; Lati: " + lFormat.format(latitude) + "; Long: " + lFormat.format(longitude)
                                    + "; Index: " + Integer.toString(sendNum) + "; Speed: " + dFormat.format(speed) + "; Network: "+ networkType +  "\r\n";

                            fileWrite(MyFileName, message);

                            Message msg = Message.obtain();

                            try {

                                if (sendSocket != null) {

                                    outputStream = sendSocket.getOutputStream();

                                    outputStream.write(message.getBytes("utf-8"));

                                    outputStream.flush();

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

                                            BtnSendFlag = true;

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
                                //Connection Break Because of Network Error
                                if (BtnSendFlag) BtnSendFlag = false;

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

            if (BtnSendUFlag) {

                BtnSendUFlag = false;

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

                BtnSendUFlag = true;

                UMainTimer = new Timer();

                UMainTask = new TimerTask() {
                    @Override
                    public void run() {
                        UsendNum++;

                        Message msg = Message.obtain();

                        String message = "Time: " + tFormat.format(System.currentTimeMillis()) + "; CSQ: " + Integer.toString(dbm)
                                + "; Height: " + dFormat.format(height) + "; Lati: " + lFormat.format(latitude) + "; Long: " + lFormat.format(longitude) 
                                + "; Speed: " + dFormat.format(speed) + "; Index: " + Integer.toString(UsendNum) + "; Network: "+ networkType +  "\r\n";

                        fileWrite(MyFileName, message);

                        byte bMessage[] = message.getBytes();

                        try {

                            if (UDPsocket != null) {

                                InetAddress serverAddress = InetAddress.getByName("101.132.97.148");

                                DatagramPacket packet = new DatagramPacket(bMessage, bMessage.length, serverAddress, 7671);

                                UDPsocket.send(packet);

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