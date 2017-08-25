package com.example.neeladri.audioapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;

public class DrawView extends View {
    Paint paint ;
    Path path;
    Bitmap cache;
    float lastX, lastY;

    public DrawView(Context context){
        this(context, null);
    }

    public DrawView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DrawView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaints();
    }

    private void initPaints() {
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.RED);
        paint.setStrokeWidth(2);

        lastX = 0.0f;
        lastY = 0.0f;
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(w, h);
        setMeasuredDimension(size, size);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        if (cache != null)
            canvas.drawBitmap(cache, 0, 0, paint);
    }

    public void appendPoint(float x, float y){
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0)
            return;

        if (cache == null) {
            cache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(cache);
        canvas.drawLine(lastX, lastY, x, y, paint);
        lastX = x;
        lastY = y;
        postInvalidate();
    }


}