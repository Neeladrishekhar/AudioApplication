package com.example.neeladri.audioapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder.AudioSource;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import static com.example.neeladri.audioapplication.FrameRead.downsample;
import static com.example.neeladri.audioapplication.FrameRead.hilbert;

public class CentralActivity extends AppCompatActivity {

    private static final boolean inputFromFile = false;
    OutputStreamWriter outputStreamWriter;

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
//    private int indexRec = 0;

    private static final int PLAYER_CHANNELS = AudioFormat.CHANNEL_OUT_MONO;
    private static final int PLAYER_BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE,
            PLAYER_CHANNELS, AUDIO_ENCODING);    // gives in bytes, will use for short[] size
    private AudioTrack player = null;
    private Thread playingThread = null;
    private boolean isPlaying = false;

//    private GraphView graph = null;
    private LineGraphSeries<DataPoint> graphData = null;
    private int displaySeconds = 50;
    private double lastIndex = 0;

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
    char decimate = 10;		//decimation by a factor of 10
    int samp_rate = SAMPLE_RATE/decimate;	//sampling rate after downsampling
    int window_time_ms = 1500;		// For window length of 1500ms, set window_time_ms to 30
    int hop_time_ms = 200;
    int WINSIZE = (int) Math.ceil((SAMPLE_RATE)*(window_time_ms)*0.001);	// Round WINSIZE(samples) if WINSIZE is not an integer
    int HOPSIZE = (int) Math.ceil((SAMPLE_RATE)*(hop_time_ms)*0.001);	// Round HOPSIZE(samples) if HOPSIZE is not an integer
    int buff16[] = new int[WINSIZE];			// Defining buffer for holding samples that fall within the window.

//    public CentralActivity() throws FileNotFoundException { }

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

        if (ContextCompat.checkSelfPermission(CentralActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(CentralActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
        }

        if (ContextCompat.checkSelfPermission(CentralActivity.this,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(CentralActivity.this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    1);
        }

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

        GraphView graph = (GraphView) findViewById(R.id.graph);
        /* the place where we add data */
        graphData = new LineGraphSeries<>();
        /* attaching the series to the graph */
        graph.addSeries(graphData);

        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
//        graph.getViewport().setMaxX(1000);
        graph.getViewport().setMaxX(displaySeconds);
//        if (inputFromFile) {
//            graph.getViewport().setYAxisBoundsManual(true);
//            graph.getViewport().setMinY(50);
//            graph.getViewport().setMaxY(150);
//        }
        window_time_ms = 500; hop_time_ms = 200;
        graph.getViewport().setScrollable(true);

//        Toast.makeText(getApplicationContext(), String.valueOf(BUFFER_SIZE), Toast.LENGTH_LONG).show();

        //// Only for testing purpose where we want to verify with
//        if (inputFromFile) {

//            indexPlot = 0;
//            while (indexPlot+WINSIZE <= recordedData.size()) {
//                addDataToGraph(indexPlot);
//                indexPlot += HOPSIZE;
//            }
//            try {
//                outputStreamWriter.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
    }

//    @Override
//    protected void onResume() {
//        super.onResume();
//        // we are going to simulate real time with thread that appends data to the graph
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                while (true) {
//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            if (isRecording) {   // to plot only when recording for the time being
//                                 addDataToGraph();
//                            }
//                        }
//                    });
//                    try {
//                        Thread.sleep(50);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//
//                }
//            }
//        }).start();
//    }

    /* adding datapoint to the graph */
//    private double lastX = 0;
    List<Double> fhr=new ArrayList<>(); // we need this variable globally as it is used by the function again and again

    private int indexPlot = 0;
    private void addDataToGraph(int index) {
        // here we add new data to the graph and show only a max of 10 datapoints to the viewport while scrolling to end

        if (index+WINSIZE > recordedData.size()) { return; }
        //////////////////////////////////////////////////////////////////////////////
        //Code translation requirements
        int graphFrame = displaySeconds * 1000 / hop_time_ms;

        // Convert time specifications to samples

        //can be used to test the timing and real time audio processing and stability*/
//        graphData.appendData(new DataPoint(0.001*(window_time_ms + (hop_time_ms*(index/HOPSIZE))), Math.sin(index/HOPSIZE)), true, graphFrame);
//        index += HOPSIZE;
//        return;

        int WINLEN = WINSIZE/decimate;	//parameters in decimated samples
//        int HOPLEN = HOPSIZE/decimate;
        double databuff[] = new double[WINSIZE];				// buffer to store downsampled signal

        //short int sample_start,numb_samples;	// Variables for reading samples that fall within window for 1st frame
//        float hop_time = 0;			// Hop time for printing in output file

//        for (int j = 0;j<WINSIZE ; j++)
//        {
//            buff16[j] = 0;
//        }

        //parameters for ACF

        int acf_jump = 10;		//difference between lags for which acf is calculated
        int min_fhr = 60;	// Minimum and maximum FHR in beats per minute (bpm)
        int max_fhr = 300;
        int lag_min = (int) Math.floor(samp_rate*60.0/max_fhr);
        int lag_max = (int) Math.floor(samp_rate*60.0/min_fhr);
        int acf_len = (int) Math.ceil(1.0*(lag_max-lag_min)/acf_jump);	//length of ACF valarray
        //vector<double> fhr;
//        int count = 0;						// For counting number of frames in wave file.


        // --------------- Converting time specifications to sample end-------------------------------------- //


        // Start reading file frame by frame //
//        while(hop_time<50) // we do not need the while loop as this function is called again and again
//        {
//          take input buffer of length window size
        // input buff 16
        for (int i = 0; i < WINSIZE; i++) {
            buff16[i] = recordedData.get(index + i);
        }

        downsample(buff16, WINSIZE, decimate, databuff);

        //calculating envelope of signal

        //typedef complex<double> cx;
//        int log2n =(int) Math.ceil(Math.log(WINLEN)/Math.log(WINLEN));
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

        //Filtering envelope using the previously defined FIR LPF
        for (int i = 0; i < WINLEN; i++) {
//            data[i] = 0.0;

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

        // Calculating norm
        double norm = 0.0;
        for (int i = 0; i < WINLEN; i++) {
            norm = norm + data[i] * data[i];
        }

        //Computing autocorrelation
        double acf[] = new double[acf_len];
        double acfval;

        for (int i = lag_min; i < lag_max; i = i + acf_jump) {
            acfval = 0;
            for (int j = 0; j < WINLEN - i; j++) {
                acfval = acfval + data[j] * data[i + j];
            }
            acf[(i - lag_min) / acf_jump] = acfval / norm;        //assign this correctly*
        }

        //finging position of max ACF value
        int maxpos = 0;
        for (int i = 1; i < acf_len; i++) {
            if (acf[i] > acf[maxpos]) {
                maxpos = i;
            }
        }

        // FILE * acffile = fopen("ACF.txt","wb");		// Create output file in write mode
        int lagmax = lag_min + maxpos * acf_jump;
        double curr_rate = samp_rate * 60.0 / lagmax;

//        hop_time = hop_time + (float) (hop_time_ms * 0.001); // this is kind of useless because we do not have a while loop any more

        double timeNow = 0.001*(window_time_ms + (hop_time_ms*(index/HOPSIZE)));
        System.out.println("Corr lag: " + lagmax + " at hop time: " + timeNow);
//        if (lastIndex < timeNow) {  // this might skip a few calculations if last one is done
//            graphData.appendData(new DataPoint(timeNow, curr_rate), (timeNow > displaySeconds), graphFrame); lastIndex = timeNow;
//        }
        if ((fhr.isEmpty() || curr_rate - fhr.get(fhr.size() - 1) <= 50)) {
            fhr.add(curr_rate);

            System.out.println("Rate: " + curr_rate);

//                fprintf (outfile,"%.03f	%.02f \n", hop_time, curr_rate);	// Ouput file format: Time-Stamp(sec)   FHR Value
            // Example          : 0.01		1234
            // add data to graph
//            graphData.appendData(new DataPoint(timeNow, curr_rate), (timeNow > displaySeconds), graphFrame);
            if (lastIndex < timeNow) {
                graphData.appendData(new DataPoint(timeNow, curr_rate), (timeNow > displaySeconds), graphFrame); lastIndex = timeNow;
            }
            if (inputFromFile) {
                try {
                    System.out.println("timeNow: " + timeNow + ", curr_rate: " + curr_rate);
                    outputStreamWriter.write(timeNow + "," + curr_rate + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
//        graphData.appendData(new DataPoint(timeNow, curr_rate), (timeNow > displaySeconds), graphFrame);

        //
        // Avoiding to create the ACF file
        // may need to implement code in this part for plotting or storing acf graph data
        //

        //Avoided
        //cout<<"Corr lag: "<<lagmax<<" at hop time: "<<hop_time<<endl;

//        count++;
        // Avoided printing of final count should be the number of frames in the input file //

//        index += HOPSIZE;
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
    private void readAudiofile() throws FileNotFoundException {

        //int minBufferSize = AudioTrack.getMinBufferSize(8000, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);
        String filepath = Environment.getExternalStorageDirectory().getAbsolutePath();
        Toast.makeText(getApplicationContext(), filepath, Toast.LENGTH_LONG).show();
        System.out.println(filepath);
        System.out.println("audio fle read start");
        try {
            //AssetManager am = context.getAssets();
            File srcFile = new File(filepath + File.separator + "Audio" + File.separator + "fhr_rec3.wav" );

            if (srcFile.exists()) {
                System.out.println("file found successfully");
            } else {
                System.out.println("file not found");
            }
            // final File srcFile = new File(Environment.getExternalStorageDirectory()
            //        .getAbsolutePath(),"fhr_rec3.wav");
            FileInputStream in = new FileInputStream(srcFile);
            //ObjectOutputStream output =  new ObjectOutputStream(new FileOutputStream("gilad-OutPut.bin"));
            //short[] aData = new short[BUFFER_SIZE / BytesPerElement];
            System.out.println((int) srcFile.length());
            byte[] buf = new byte[(int) srcFile.length()];
            short[] shortArr = new short[buf.length / 2];
            in.read(buf);
            for (int i = 0; i < buf.length / 2; i++) {
                //output.writeShort( (short)( ( buf[i*2] & 0xff )|( buf[i*2 + 1] << 8 ) ) );
                shortArr[i] = ((short) ((buf[i * 2] & 0xff) | (buf[i * 2 + 1] << 8)));
                recordedData.add(shortArr[i]);
            }

            in.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("audio file read complete");
    }

    private void startRecord() {

        // setting some graph specific variables
//        graphFrame = Integer.parseInt(((EditText) findViewById(R.id.frameNum)).getText().toString());
//        graph.getViewport().setMaxX(graphFrame);
        graphData.resetData(new DataPoint[0]);
        indexPlot = 0; lastIndex = 0;
        recordedData.clear();

        if (inputFromFile) {
            try {   // read the audio file to plot
                readAudiofile();
                String filepath = Environment.getExternalStorageDirectory().getAbsolutePath();
                File writefile = new File(filepath+File.separator+"Audio"+File.separator+"mynewfile.txt");
                FileOutputStream out = new FileOutputStream(writefile);
                outputStreamWriter = new OutputStreamWriter(out);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            isRecording = true; // virtual recording just for testing purposes
            enableButtons();
            new Thread(new Runnable() {
                @Override
                public void run() {
//                    while (indexPlot <= 100 * HOPSIZE) {
                    while (indexPlot+WINSIZE <= recordedData.size()) {
                        final int ind = indexPlot;
//                        new Thread(new Runnable() {
//                            @Override
//                            public void run() {
//                            }
//                        }).start();
                        addDataToGraph(ind);
                        indexPlot += HOPSIZE;
                        try {
                            Thread.sleep(hop_time_ms);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        outputStreamWriter.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
//                    stopRecord();
                    isRecording = false;
                }
            }).start();
            return;
        }
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
        isRecording = false;
        enableButtons();
        recordingThread = null;
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;

//            System.out.println("Short Array captured is " + recordedData.toString());
        }
    }

    private void writeAudioDataSomewhere() {

        short[] sData = new short[BUFFER_SIZE / BytesPerElement];
        // int indexRec = 0;
        while (isRecording) {
            // read the data to the global array
            int read = recorder.read(sData, 0, sData.length);
//            indexRec += read;

            // this part of the code should not be code intensive
            // as here we need to be able to capture the next input for read
            // while not missing an intermediate read which will lead to gaps
            for (int i = 0 ; i < read ; i++) {
                recordedData.add(sData[i]);
            }
            if (indexPlot+WINSIZE <= recordedData.size()) {
                final int ind = indexPlot;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        addDataToGraph(ind);
                    }
                }).start();
                indexPlot += HOPSIZE;
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
