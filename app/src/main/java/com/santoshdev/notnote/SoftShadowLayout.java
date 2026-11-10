package com.santoshdev.notnote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class SoftShadowLayout extends FrameLayout {
    private Paint shadowPaint;
    private RectF rectF;
    private float cornerRadius;

    public SoftShadowLayout(Context context) {
        super(context);
        init();
    }

    public SoftShadowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        float density = getResources().getDisplayMetrics().density;
        
        // Main Capsule ke liye radius aur shadow settings
        cornerRadius = 24f * density; 
        float shadowRadius = 16f * density;
        float dy = 6f * density;
        int shadowColor = Color.parseColor("#15000000"); // Soft shadow

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.WHITE); 
        shadowPaint.setShadowLayer(shadowRadius, 0f, dy, shadowColor);

        rectF = new RectF();
        
        // Padding adjust taaki shadow cut na ho
        int pad = (int) (shadowRadius + dy + (2 * density)); 
        setPadding(pad, pad, pad, pad);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rectF.set(getPaddingLeft(), getPaddingTop(), w - getPaddingRight(), h - getPaddingBottom());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, shadowPaint);
        super.onDraw(canvas);
    }
}
