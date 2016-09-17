package sophia.project;

/**
 * Created by Sophia on 15-06-03.
 */

import android.os.AsyncTask;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

public class MainActivity extends Activity implements SensorEventListener {
    private boolean started = false;
    private GraphicalView mChart;
    private ArrayList<AccelData> sensorData;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private AccelData data;
    private LinearLayout chartContainer;
    private AtomicBoolean sendBool;
    private Button sendButton;
    private Button viewButton;


    // Max size of array with all the coordinates
    private final int MAX_SIZE = 120;

    // The address to post data to rest-api
    private String URL = "http://52.17.119.220:8888/data";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Getting a reference to LinearLayout of the MainActivity Layout
        chartContainer = (LinearLayout) findViewById(R.id.chart_container);
        sendBool = new AtomicBoolean(false);

        viewButton = (Button) findViewById(R.id.viewButton);
        sendButton = (Button) findViewById(R.id.sendButton);

        /*
        ----------------- GET/VIEW---------------------
         */
        viewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewButton.setEnabled(false);
                viewButton.setVisibility(View.GONE);
                sendButton.setEnabled(false);
                sendButton.setVisibility(View.GONE);

                // A timer-thread that runs every 400 milliseconds.
                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        if (started) {
                            new GetData().execute();
                        }
                    }
                }, 0, 400, TimeUnit.MILLISECONDS);
            }
        });


        /*
        -----------------POST/SEND----------------------
        */
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendBool.set(true);
                sendButton.setEnabled(false);
                sendButton.setVisibility(View.GONE);
                viewButton.setEnabled(false);
                viewButton.setVisibility(View.GONE);
            }
        });


        /*
        ------------------START UP OF THE CHART-----------------------
         */
        // The array which stores all coordinates
        sensorData = new ArrayList<AccelData>();
        started = true;

        // This is the values of the startup chart (the first coordinate starts at zero)
        double x = 0;
        double y = 0;
        double z = 0;
        // Milliseconds since January 1, 1970
        long timestamp = System.currentTimeMillis();

        // Creates a new AccelData object with the coordinates and puts the object in array
        data = new AccelData(timestamp, x, y, z);
        sensorData.add(data);
        drawChart(sensorData);

        /*
        ------------------START UP OF THE SENSOR----------------------------
         */

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        // Get Accelerometer sensor
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        started = true;
        if (sendBool.get() == true){
            // register Listener for SensorManager and Accelerometer sensor
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (started == true && sendBool.get() == true) {
            mSensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        started = false;
        if (sendBool.get() == true){
            double x = 0;
            double y = 0;
            double z = 0;
            long timestamp = 0;
            data = new AccelData(timestamp, x, y, z);
            new PostData().execute(data);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /*
        --------->OnSensorChanged
        Creates new object each time accelerometer changes value.
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (started == true && sendBool.get() == true) {
            double x = event.values[0];
            double y = event.values[1];
            double z = event.values[2];
            // Milliseconds since January 1, 1970
            long timestamp = System.currentTimeMillis();
            data = new AccelData(timestamp, x, y, z);

            // Starts async-task to post the new object to server.
            new PostData().execute(data);

            //Adds object to array and removes first item if array becomes to big,
            //this due to that the chart shouldn't get full on screen.
            sensorData.add(data);
            if (sensorData.size() >= MAX_SIZE) {
                sensorData.remove(0);
            }
            // Calls to the draw-function.
            drawChart(sensorData);
        }
    }


    /*
        --------->drawChart
        Creates and draws chart (used chart is from achartengine)
        * Sets the graphical specifics for the chart lines and for the chart frame.
     */
    private void drawChart(ArrayList<AccelData> sensorData) {
        long t = sensorData.get(0).getTimestamp();

        if (sensorData != null || sensorData.size() > 0) {
            XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();

            //Builds a new XY series for each axis in accelerometer.
            XYSeries xSeries = new XYSeries("X");
            XYSeries ySeries = new XYSeries("Y");
            XYSeries zSeries = new XYSeries("Z");

            // Removes any previous series from the "chart-handler" to be able to add new.
            dataset.removeSeries(xSeries);
            dataset.removeSeries(ySeries);
            dataset.removeSeries(zSeries);

            // Clears each serie from previous contents to be able to add new content.
            xSeries.clear();
            ySeries.clear();
            zSeries.clear();

            //Adds new time and coordinate to each axis-serie by looping through the array with all objects.
            for (AccelData data : sensorData) {
                xSeries.add(data.getTimestamp() - t, data.getX());
                ySeries.add(data.getTimestamp() - t, data.getY());
                zSeries.add(data.getTimestamp() - t, data.getZ());
            }

            // Adds each serie to the "chart-handler".
            dataset.addSeries(xSeries);
            dataset.addSeries(ySeries);
            dataset.addSeries(zSeries);

            // Sets up the graphical specifics for each axis.
            XYSeriesRenderer xRenderer = new XYSeriesRenderer();
            xRenderer.setColor(Color.RED);
            xRenderer.setPointStyle(PointStyle.CIRCLE);
            xRenderer.setFillPoints(true);
            xRenderer.setLineWidth(1);
            xRenderer.setDisplayChartValues(false);

            XYSeriesRenderer yRenderer = new XYSeriesRenderer();
            yRenderer.setColor(Color.GREEN);
            yRenderer.setPointStyle(PointStyle.CIRCLE);
            yRenderer.setFillPoints(true);
            yRenderer.setLineWidth(1);
            yRenderer.setDisplayChartValues(false);

            XYSeriesRenderer zRenderer = new XYSeriesRenderer();
            zRenderer.setColor(Color.BLUE);
            zRenderer.setPointStyle(PointStyle.CIRCLE);
            zRenderer.setFillPoints(true);
            zRenderer.setLineWidth(1);
            zRenderer.setDisplayChartValues(false);

            XYMultipleSeriesRenderer multiRenderer = new XYMultipleSeriesRenderer();

            // Sets up the graphical specifics the chart-frame.
            multiRenderer.setXLabels(0);
            multiRenderer.setLabelsColor(Color.RED);
            multiRenderer.setChartTitle("t vs (x,y,z)");
            multiRenderer.setXTitle("Sensor Data");
            multiRenderer.setYTitle("Values of Acceleration");
            multiRenderer.setZoomButtonsVisible(false);

            for (int i = 0; i < sensorData.size(); i++) {

                multiRenderer.addXTextLabel(i + 1, ""
                        + (sensorData.get(i).getTimestamp() - t));
            }

            for (int i = 0; i < 12; i++) {
                multiRenderer.addYTextLabel(i + 1, "" + i);
            }

            multiRenderer.addSeriesRenderer(xRenderer);
            multiRenderer.addSeriesRenderer(yRenderer);
            multiRenderer.addSeriesRenderer(zRenderer);

            // Removes previous content from chartContainer to be able to add new.
            chartContainer.removeView(mChart);

            // Creating a Line Chart.
            mChart = (GraphicalView) ChartFactory.getLineChartView(getBaseContext(), dataset,
                    multiRenderer);

            // Adding the Line Chart to the LinearLayout.
            chartContainer.addView(mChart);

        }
    }

    /*
        ---------------POSTDATA, ONLY FOR SEND DATA TO SERVER-----------------
        * AsyncTask which is running in the background.
        * Makes HTTP post-request to server.
        * Posts all new data to server, using jsonObjects.
     */
    private class PostData extends AsyncTask<AccelData, Void, Void> {
        @Override
        protected Void doInBackground(AccelData... params) {
            JSONObject json = new JSONObject();
            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost(URL);

            try {
                json.put("Timestamp", data.getTimestamp());
                json.put("Xaxis", data.getX());
                json.put("Yaxis", data.getY());
                json.put("Zaxis", data.getZ());
                StringEntity se = new StringEntity(json.toString());
                httppost.setEntity(se);
                // Execute HTTP Post Request
                HttpResponse response = httpclient.execute(httppost);
                Log.w("HTTP Response", response.getStatusLine().toString());

            } catch (Exception e) {  e.printStackTrace();}
            return null;
        }

        protected void onPostExecute(Void... values) {
            Log.e("HTTP Response", "Success");
        }

    }

    /*
        ----------------GETDATA, ONLY FOR GETTING DATA FROM SERVER----------------------
        * A thread for making http get-requests from the server.
        * Converts input to string, and then to an double-array.
     */

    private class GetData extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            InputStream inputStream = null;
            String result = "";
            Double[] tempArr;
            try {
                // create HttpClient
                HttpClient httpclient = new DefaultHttpClient();

                // make GET request to the given URL
                HttpResponse httpResponse = httpclient.execute(new HttpGet(URL));

                // receive response as inputStream
                inputStream = httpResponse.getEntity().getContent();

                // convert inputstream to string and then to a JSONArray
                if (inputStream != null)
                    result = convertInputStreamToString(inputStream);
                JSONArray jsonArray = new JSONArray(result);
                tempArr = new Double[jsonArray.length()];

                for (int i = 0; i < jsonArray.length(); i++) {
                    tempArr[i] = jsonArray.getDouble(i);
                }

                // Sends the array to a function to handle this
                addData(tempArr);


            } catch (Exception e) {
                Log.d("InputStream", e.getLocalizedMessage());
            }
            return null;
        }

        }


    /*
        -----------------HELP FUNCTION TO GETDATA---------------------------
        --------> convertInputStreamToString
        Reads from the get-request and puts it in string.
     */
    private static String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        while ((line = bufferedReader.readLine()) != null)
            result += line;

        inputStream.close();
        return result;

    }

    /*
        -----------------HELP FUNCTION TO GETDATA---------------------------
        ---------> addData
        * Takes the array and puts the content into a new acceldata-object
        * Adds object to array and removes first item if array becomes to big,
          this due to that the chart shouldn't get full on screen.
     */
    public void addData(Double[] tempArr){

        if (tempArr != null) {

            //long timestamp = tempArr[0].longValue();
            long timestamp = System.currentTimeMillis();
            double x = tempArr[1];
            double y = tempArr[2];
            double z = tempArr[3];

            data = new AccelData(timestamp, x, y, z);
            sensorData.add(data);
            System.out.println(sensorData);
            if (sensorData.size() >= MAX_SIZE) {
                sensorData.remove(0);
            }
        }

            // Returns to main-thread to be able to draw the chart with the new values again.
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    drawChart(sensorData);
                }
            });
        }
    }

