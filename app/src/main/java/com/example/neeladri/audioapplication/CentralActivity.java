package com.example.neeladri.audioapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder.AudioSource;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.List;

import static com.example.neeladri.audioapplication.FrameRead.downsample;
import static com.example.neeladri.audioapplication.FrameRead.hilbert;

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
    private ArrayList<Short> recordedData = new ArrayList<>();

    private static final int PLAYER_CHANNELS = AudioFormat.CHANNEL_OUT_MONO;
    private static final int PLAYER_BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE,
            PLAYER_CHANNELS, AUDIO_ENCODING);    // gives in bytes, will use for short[] size
    private AudioTrack player = null;
    private Thread playingThread = null;
    private boolean isPlaying = false;
// COMMENTED BY SHREYANSH TODO
//    private GraphView graph = null;
//    private LineGraphSeries<DataPoint> graphData = null;
//    private int displaySeconds = 50;

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

    int numtaps = 121;	//Number of FIR taps for Hamming window
    double fir[] = new double[numtaps];	//FIR LPF using Hamming window with cutoff freq = 50Hz
    char decimate = 50;		//decimation by a factor of 10
    int samp_rate = SAMPLE_RATE/decimate;	//sampling rate after downsampling
    int window_time_ms = 1500;		// For window length of 1500ms, set window_time_ms to 30
    int WINSIZE = (int) Math.ceil((SAMPLE_RATE)*(window_time_ms)*0.001);	// Round WINSIZE(samples) if WINSIZE is not an integer
    int buff16[] = new int[WINSIZE];			// Defining buffer for holding samples that fall within the window.


    DrawView drawView;

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

        drawView = (DrawView) findViewById(R.id.graph);

        setButtonHandlers();
        enableButtons();



        // Designing filter to remove noise with frequency cut-off = 50Hz
        int middle = (numtaps-1)/2;
        double fc = 50.0/samp_rate;

        for(int i=0; i<numtaps; i++){
            if(i!=middle){
                fir[i]=Math.sin(2*Math.PI*fc*(i-middle))/(Math.PI*(i-middle))*(0.54-0.46*Math.cos(Math.PI*i/middle));
            }
            else fir[i] = 2*fc;
//            System.out.println(i + " " + fir[i]);
        }

        //COMMENTED BY SHREYANSH TODO
/*        GraphView graph = (GraphView) findViewById(R.id.graph);
        graphData = new LineGraphSeries<>();
        graph.addSeries(graphData);

        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(1000);
        graph.getViewport().setMaxX(displaySeconds);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(120);
        graph.getViewport().setMaxY(170);
        graph.getViewport().setScrollable(true);*/

        Toast.makeText(getApplicationContext(), String.valueOf(BUFFER_SIZE), Toast.LENGTH_LONG).show();
    }

    private int indexPlot = 0;

    @Override
    protected void onResume() {
        super.onResume();
        /* we are going to simulate real time with thread that appends data to the graph */
        /*new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isRecording) {   // to plot only when recording for the time being
                                addDataToGraph();
                            }
                        }
                    });
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            }
        }).start();*/
    }
    List<Double> fhr=new ArrayList<>(); // we need this variable globally as it is used by the function again and again


    private void addDataToGraph() {
        if (indexPlot+WINSIZE > recordedData.size()) { return; }

        int hop_time_ms = 200;
        //COMMENTED BY SHREYANSH TODO
        //int graphFrame = displaySeconds * 1000 / hop_time_ms;

        int HOPSIZE = (int) Math.ceil((SAMPLE_RATE)*(hop_time_ms)*0.001);	// Round HOPSIZE(samples) if HOPSIZE is not an integer

        int WINLEN = WINSIZE/decimate;	//parameters in decimated samples
        double databuff[] = new double[WINSIZE];				// buffer to store downsampled signal

        int acf_jump = 10;		//difference between lags for which acf is calculated
        int min_fhr = 60;	// Minimum and maximum FHR in beats per minute (bpm)
        int max_fhr = 300;
        int lag_min = (int) Math.floor(samp_rate*60.0/max_fhr);
        int lag_max = (int) Math.floor(samp_rate*60.0/min_fhr);
        int acf_len = (int) Math.ceil(1.0*(lag_max-lag_min)/acf_jump);	//length of ACF valarray

        for (int i = 0; i < WINSIZE; i++) {
            buff16[i] = recordedData.get(indexPlot + i);
        }

        downsample(buff16, WINSIZE, decimate, databuff);

        int log2n = (int) Math.ceil(Math.log(WINLEN) / Math.log(2));
        int n = 1 << log2n;
        ComplexNumber a[] = new ComplexNumber[n];
        ComplexNumber b[] = new ComplexNumber[n];
        ComplexNumber c[] = new ComplexNumber[n];

        for (int i = 0; i < n; i++) {
            if (i < WINLEN) {
                a[i] = new ComplexNumber(databuff[i], 0);
            } else a[i] = new ComplexNumber(0, 0);
        }

        hilbert(a, b, c, log2n);
        double env[] = new double[WINLEN];
        for (int i = 0; i < WINLEN; i++) {
            env[i] = c[i].mod();
        }

        double data[] = new double[WINLEN];

        for (int i = 0; i < WINLEN; i++) {
            if (i < numtaps) {
                for (int j = i; j >= 0 && j <= i; j--) {
                    data[i] = data[i] + env[j] * fir[i - j];
                }
            } else {
                for (int j = i; j > i - numtaps && j <= i; j--) {
                    data[i] = data[i] + env[j] * fir[i - j];
                }
            }

        }

        double norm = 0.0;
        for (int i = 0; i < WINLEN; i++) {
            norm = norm + data[i] * data[i];
        }

        double acf[] = new double[acf_len];
        double acfval;

        for (int i = lag_min; i < lag_max; i = i + acf_jump) {
            acfval = 0;
            for (int j = 0; j < WINLEN - i; j++) {
                acfval = acfval + data[j] * data[i + j];
            }
            acf[(i - lag_min) / acf_jump] = acfval / norm;        //assign this correctly*
        }

        int maxpos = 0;
        for (int i = 1; i < acf_len; i++) {
            if (acf[i] > acf[maxpos]) {
                maxpos = i;
            }
        }

        int lagmax = lag_min + maxpos * acf_jump;
        double curr_rate = samp_rate * 60.0 / lagmax;

        System.out.println("Corr lag: " + lagmax + " at hop time: " + 0.001*(window_time_ms + (hop_time_ms*(indexPlot/HOPSIZE))));
        double timeNow = 0.001*(window_time_ms + (hop_time_ms*(indexPlot/HOPSIZE)));

        if ((fhr.isEmpty() || curr_rate - fhr.get(fhr.size() - 1) <= 50) && curr_rate > 100) {
            fhr.add(curr_rate);

            System.out.println("Rate: " + curr_rate);

            //COMMENTED BY SHREYANSH TODO
            //graphData.appendData(new DataPoint(timeNow, curr_rate), (timeNow > displaySeconds), graphFrame);
            drawView.appendPoint((float) timeNow*10.0f,(float) curr_rate);
        }

        indexPlot += HOPSIZE;
    }

    private void setButtonHandlers() {
        (findViewById(R.id.btnStart)).setOnClickListener(btnClick);
        (findViewById(R.id.btnStop)).setOnClickListener(btnClick);
        (findViewById(R.id.btnPlay)).setOnClickListener(btnClick);
        (findViewById(R.id.btnPause)).setOnClickListener(btnClick);
    }

    private void enableButtons() {
        findViewById(R.id.btnStart).setEnabled(!isRecording);
        findViewById(R.id.btnStop).setEnabled(isRecording);
        findViewById(R.id.btnPlay).setEnabled(!isPlaying);
        findViewById(R.id.btnPause).setEnabled(isPlaying);
    }

    private void startRecord() {

        indexPlot = 0;
        recordedData.clear();
        recorder = new AudioRecord(AudioSource.MIC,
                SAMPLE_RATE, RECORDER_CHANNELS,
                AUDIO_ENCODING, BUFFER_SIZE);
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
        if (recorder != null) {
            isRecording = false;
            enableButtons();
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
        }
    }

    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    addDataToGraph();
                }
            });
        }
    };
    Thread mThread;
    private void writeAudioDataSomewhere() {
        short[] sData = new short[BUFFER_SIZE / BytesPerElement];
        while (isRecording) {
            int read = recorder.read(sData, 0, sData.length);

            for (int i = 0 ; i < read ; i++) {
                recordedData.add(sData[i]);
                if(indexPlot + WINSIZE <= recordedData.size()){
                    mThread = new Thread(runnable);
                    mThread.start();
/*                    while(mThread.getState()!=Thread.State.TERMINATED){
                    }*/
                }
            }
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
