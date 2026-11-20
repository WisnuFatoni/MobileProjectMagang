package com.example.fai26.detect;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

/**
 * Menjalankan model YOLOv8 (best.tflite) pada satu bitmap tegak.
 * Panggil {@link #detect(Bitmap, int)} selalu dari thread yang sama: delegate GPU terikat ke thread pembuatnya.
 */
public class YoloDetector implements AutoCloseable {

    private static final String TAG = "YoloDetector";
    private static final String MODEL = "models/best.tflite";
    public static final int SIZE = 640;

    public static final float CONF = 0.17f;
    public static final float IOU = 0.60f;
    public static final int MAX_DET = 300;

    private final Context context;
    private Interpreter interpreter;
    private GpuDelegate gpuDelegate;
    private String backend = "belum dimuat";
    private String backendNama = "";

    private boolean inputNchw;
    private int channels;
    private int anchors;
    private boolean outputChannelsFirst;

    private ByteBuffer input;
    private FloatBuffer inputFloat;
    private float[] inputArr;
    private final float[] lut = new float[256];
    private ByteBuffer output;
    private float[] outFlat;
    private final int[] pixels = new int[SIZE * SIZE];
    private Bitmap canvasBmp;
    private Canvas canvas;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

    public YoloDetector(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Info lengkap backend dan bentuk tensor, untuk log dan diagnosis. */
    public String info() {
        return backend;
    }

    /** "GPU" atau "CPU". */
    public String backendNama() {
        return backendNama;
    }

    private MappedByteBuffer loadModel() throws IOException {
        AssetFileDescriptor fd = context.getAssets().openFd(MODEL);
        try (FileInputStream in = new FileInputStream(fd.getFileDescriptor());
             FileChannel ch = in.getChannel()) {
            return ch.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
        }
    }

    private void init() throws IOException {
        MappedByteBuffer model = loadModel();

        Interpreter it = null;
        try {
            CompatibilityList compat = new CompatibilityList();
            if (compat.isDelegateSupportedOnThisDevice()) {
                gpuDelegate = new GpuDelegate(compat.getBestOptionsForThisDevice());
                Interpreter.Options opt = new Interpreter.Options().addDelegate(gpuDelegate);
                it = new Interpreter(model, opt);
                backend = "GPU";
            }
        } catch (Throwable e) {
            Log.w(TAG, "GPU gagal, pakai CPU: " + e);
            if (gpuDelegate != null) {
                gpuDelegate.close();
                gpuDelegate = null;
            }
            it = null;
        }
        if (it == null) {
            it = new Interpreter(model, new Interpreter.Options().setNumThreads(4));
            backend = "CPU";
        }
        interpreter = it;

        int[] in = interpreter.getInputTensor(0).shape();
        DataType inType = interpreter.getInputTensor(0).dataType();
        int[] out = interpreter.getOutputTensor(0).shape();
        Log.i(TAG, "backend=" + backend + " input=" + java.util.Arrays.toString(in) + " " + inType
                + " output=" + java.util.Arrays.toString(out)
                + " " + interpreter.getOutputTensor(0).dataType());
        if (inType != DataType.FLOAT32) {
            throw new IllegalStateException("Masukan model bukan float32: " + inType);
        }
        inputNchw = in.length == 4 && in[1] == 3;

        // Keluaran [1, 10, 8400] (kanal dulu) atau [1, 8400, 10].
        if (out.length != 3) throw new IllegalStateException("Bentuk keluaran tak dikenal");
        outputChannelsFirst = out[1] < out[2];
        channels = outputChannelsFirst ? out[1] : out[2];
        anchors = outputChannelsFirst ? out[2] : out[1];
        backendNama = backend;
        backend = backend + " in" + java.util.Arrays.toString(in)
                + " out" + java.util.Arrays.toString(out);

        input = ByteBuffer.allocateDirect(4 * SIZE * SIZE * 3).order(ByteOrder.nativeOrder());
        inputFloat = input.asFloatBuffer();
        inputArr = new float[SIZE * SIZE * 3];
        for (int i = 0; i < 256; i++) lut[i] = i / 255f;
        output = ByteBuffer.allocateDirect(4 * channels * anchors).order(ByteOrder.nativeOrder());
        outFlat = new float[channels * anchors];
        canvasBmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(canvasBmp);
    }

    /** Waktu tahap terakhir dalam milidetik: praproses, inferensi, pasca-proses. */
    public final float[] waktuMs = new float[3];

    /**
     * @param mentah  bitmap dari kamera, belum diputar
     * @param rotasi  derajat putar searah jarum jam agar frame tegak (0, 90, 180, 270)
     */
    public List<Detection> detect(Bitmap mentah, int rotasi) throws IOException {
        if (interpreter == null) init();
        long t0 = System.nanoTime();

        int rw = mentah.getWidth();
        int rh = mentah.getHeight();
        boolean tukar = rotasi == 90 || rotasi == 270;
        int fw = tukar ? rh : rw; // ukuran frame tegak
        int fh = tukar ? rw : rh;

        // Letterbox: skala sama, sisa diisi abu-abu (114) seperti saat pelatihan.
        // Rotasi ikut dalam matriks, jadi tidak perlu membuat bitmap putaran baru.
        float scale = Math.min((float) SIZE / fw, (float) SIZE / fh);
        float padX = (SIZE - fw * scale) / 2f;
        float padY = (SIZE - fh * scale) / 2f;
        canvas.drawColor(Color.rgb(114, 114, 114));
        Matrix m = new Matrix();
        m.postTranslate(-rw / 2f, -rh / 2f);
        m.postRotate(rotasi);
        m.postTranslate(fw / 2f, fh / 2f);
        m.postScale(scale, scale);
        m.postTranslate(padX, padY);
        canvas.drawBitmap(mentah, m, paint);
        canvasBmp.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);

        // Konversi ke float memakai larik dan pengisian massal (jauh lebih cepat dari putFloat per elemen).
        final int n = SIZE * SIZE;
        final float[] a = inputArr;
        final float[] t = lut;
        if (inputNchw) {
            for (int i = 0; i < n; i++) {
                int p = pixels[i];
                a[i] = t[(p >> 16) & 0xFF];
                a[n + i] = t[(p >> 8) & 0xFF];
                a[2 * n + i] = t[p & 0xFF];
            }
        } else {
            for (int i = 0, j = 0; i < n; i++, j += 3) {
                int p = pixels[i];
                a[j] = t[(p >> 16) & 0xFF];
                a[j + 1] = t[(p >> 8) & 0xFF];
                a[j + 2] = t[p & 0xFF];
            }
        }
        inputFloat.rewind();
        inputFloat.put(a);
        input.rewind();
        output.rewind();
        long t1 = System.nanoTime();
        interpreter.run(input, output);
        long t2 = System.nanoTime();
        output.rewind();
        output.asFloatBuffer().get(outFlat);

        List<Detection> hasil = YoloPostprocess.decode(outFlat, channels, anchors,
                outputChannelsFirst, SIZE, fw, fh, CONF, IOU, MAX_DET);
        long t3 = System.nanoTime();
        waktuMs[0] = (t1 - t0) / 1e6f;
        waktuMs[1] = (t2 - t1) / 1e6f;
        waktuMs[2] = (t3 - t2) / 1e6f;
        return hasil;
    }

    @Override
    public void close() {
        if (interpreter != null) interpreter.close();
        if (gpuDelegate != null) gpuDelegate.close();
        interpreter = null;
        gpuDelegate = null;
    }
}
