package com.example.neeladri.audioapplication;

import android.Manifest;
import android.content.SyncStatusObserver;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder.AudioSource;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.VectorEnabledTintResources;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Vector;

public class CentralActivity extends AppCompatActivity {

    private static final int SAMPLE_RATE = 44100;
    private static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE,
            RECORDER_CHANNELS, AUDIO_ENCODING);    // gives in bytes, will use for short[] size
    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private boolean isRecording = false;

    private static final int BytesPerElement = 2;
//    private ArrayList<short[]> recordedData = new ArrayList<>();
    private ArrayList<Short> recordedData = new ArrayList<>();
    private int indexRec = 0;

    private static final int PLAYER_CHANNELS = AudioFormat.CHANNEL_OUT_MONO;
    private static final int PLAYER_BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE,
            PLAYER_CHANNELS, AUDIO_ENCODING);    // gives in bytes, will use for short[] size
    private AudioTrack player = null;
    private Thread playingThread = null;
    private boolean isPlaying = false;

//    private GraphView graph = null;
    private LineGraphSeries<DataPoint> graphData = null;
    private int graphFrame = 1000;

    private View.OnClickListener btnClick = new View.OnClickListener(){
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnStart: {
                    pauseRecord(); startRecord(); break;
                }
                case R.id.btnStop: {
                    stopRecord(); break;
                }
                case R.id.btnPlay: {
                    stopRecord(); playRecord(); break;
                }
                case R.id.btnPause: {
                    pauseRecord(); break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_central);

        if (ContextCompat.checkSelfPermission(CentralActivity.this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(CentralActivity.this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    1);
        }

        setButtonHandlers();
        enableButtons();

        GraphView graph = (GraphView) findViewById(R.id.graph);
        /* the place where we add data */
        graphData = new LineGraphSeries<>();
        /* attaching the series to the graph */
        graph.addSeries(graphData);

        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(1000);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(-1000);
        graph.getViewport().setMaxY(1000);

        Toast.makeText(getApplicationContext(), String.valueOf(BUFFER_SIZE), Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        /* we are going to simulate real time with thread that appends data to the graph */
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
//                            if (isRecording) {   // to plot only when recording for the time being
                            addDataToGraph();
//                            }
                        }
                    });
                    try {
                        Thread.sleep(0,2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            }
        }).start();
    }

    /* adding datapoint to the graph */
    private double lastX = 0;
    private int indexPlot = 0;
    private void addDataToGraph() {
        /* here we add new data to the graph and show only a max of 10 datapoints to the viewport while scrolling to end */
        if (indexPlot >= recordedData.size()) {
            return;
        }
        lastX = indexPlot*(1000/SAMPLE_RATE);
        graphData.appendData(new DataPoint(indexPlot, recordedData.get(indexPlot)), true, graphFrame);
        indexPlot += 1;
    }

    //
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        switch (requestCode) {
//            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO: {
//
//            }
//        }
//    }

    private void setButtonHandlers() {
        ((Button) findViewById(R.id.btnStart)).setOnClickListener(btnClick);
        ((Button) findViewById(R.id.btnStop)).setOnClickListener(btnClick);
        ((Button) findViewById(R.id.btnPlay)).setOnClickListener(btnClick);
        ((Button) findViewById(R.id.btnPause)).setOnClickListener(btnClick);
    }

    private void enableButtons() {
        findViewById(R.id.btnStart).setEnabled(!isRecording);
        findViewById(R.id.btnStop).setEnabled(isRecording);
        findViewById(R.id.btnPlay).setEnabled(!isPlaying);
        findViewById(R.id.btnPause).setEnabled(isPlaying);
    }
//
//    private AudioRecord getAudioRecorder() {
//        for (int rate : new int[] { 8000, 11025, 22050, 44100 } ) {
//            for (int format : new int[] {AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT}) {
//                for (int chan : new int[] {AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO}) {
//                    try {
//                        System.out.println("Attempting rate " + rate + "Hz, bits: " + format + ", channel: " + chan);
//                        int bufferSize = AudioRecord.getMinBufferSize(rate, chan, format);
//                        if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
//                            // check if we can instantiate and have a success
//
//                            if (recorder != null) {
//                                recorder.release();
//                                recorder = null;
//                            }
//                            recorder = new AudioRecord(AudioSource.DEFAULT, rate, chan, format, bufferSize);
//
//                            if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
//                                BUFFER_SIZE = bufferSize;
//                                return recorder;
//                            }
//                        }
//                    } catch (Exception e) {
//                        System.out.println(rate + "Exception, keep trying." + e);
//                    }
//                }
//            }
//        }
//        return null;
//    }

    private void startRecord() {

        // setting some graph specific variables
//        graphFrame = Integer.parseInt(((EditText) findViewById(R.id.frameNum)).getText().toString());
//        graph.getViewport().setMaxX(graphFrame);
        graphData.resetData(new DataPoint[0]);
        indexPlot = 0;
//        if (recorder != null) {
//            recorder.release();
//            recorder = null;
//        }

//        recorder = getAudioRecorder();
        recorder = new AudioRecord(AudioSource.MIC,
                SAMPLE_RATE, RECORDER_CHANNELS,
                AUDIO_ENCODING, BUFFER_SIZE);    // we will create a short[] and 1 short is 16bit = 2 * 8bit(1 byte)
//                AUDIO_ENCODING, 100000);    // we will create a short[] and 1 short is 16bit = 2 * 8bit(1 byte)

        /*** For debugging purposes
        if (recorder == null) {
            Toast.makeText(getApplicationContext(), "recorder is still null", Toast.LENGTH_LONG).show();
            return;
        } else if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {

        } else {
            Toast.makeText(getApplicationContext(), "recorder is uninitialized", Toast.LENGTH_LONG).show();
            return;
        }
        */
        recorder.startRecording();
        isRecording = true;
        enableButtons();
        recordingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                writeAudioDataSomewhere();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    private void stopRecord() {
        // stops recording audio
        if (recorder != null) {
            isRecording = false;
            enableButtons();
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;

//            System.out.println("Short Array captured is " + recordedData.toString());
        }
    }

    private void writeAudioDataSomewhere() {

        short[] sData = new short[BUFFER_SIZE / BytesPerElement];
        recordedData.clear(); indexRec = 0;
        while (isRecording) {
            // read the data to the global array
            int read = recorder.read(sData, 0, sData.length);
            indexRec += read;

            // this part of the code should not be code intensive
            // as here we need to be able to capture the next input for read
            // while not missing an intermediate read which will lead to gaps
            for (int i = 0 ; i < read ; i++) {
                recordedData.add(sData[i]);
            }
//            short[] index = new short[] {(short) read};
//            recordedData.add(index);
//            recordedData.add(sData);
        }
    }

    private void playRecord() {
        // plays the recorded audio
        player = new AudioTrack(AudioManager.STREAM_MUSIC,
                SAMPLE_RATE, PLAYER_CHANNELS, AUDIO_ENCODING,
                PLAYER_BUFFER_SIZE, AudioTrack.MODE_STREAM);

        player.play();
        isPlaying = true;
        enableButtons();
        playingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                playAudioSomehow();
            }
        }, "AudioPlayer Thread");
        playingThread.start();

    }

    private void pauseRecord() {
        // pauses the recorded audio
        if (player != null) {
            Toast.makeText(getApplicationContext(), String.valueOf(BUFFER_SIZE)+" "+String.valueOf(PLAYER_BUFFER_SIZE)+" "+String.valueOf(recordedData.size()), Toast.LENGTH_LONG).show();
            isPlaying = false;
            enableButtons();
            player.stop();
            player.release();
            player = null;
            playingThread = null;
        }
    }

    private void playAudioSomehow() {
        short[] recordBuffer = new short[recordedData.size()];
        for (int i = 0 ; i < recordBuffer.length ; i++ ) {
            recordBuffer[i] = recordedData.get(i);
        }


        int played = 0;
        while (isPlaying && played < recordBuffer.length) {
            if (recordBuffer.length - played >= PLAYER_BUFFER_SIZE) {
                player.write(recordBuffer, played, PLAYER_BUFFER_SIZE);
                played += PLAYER_BUFFER_SIZE;
            } else {
                player.write(recordBuffer, played, recordBuffer.length - played);
                played += recordBuffer.length - played;
            }
        }
    }
}
