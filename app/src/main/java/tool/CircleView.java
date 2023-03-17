package tool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;

/**
 * 水波圖
 */
public class CircleView extends View {

    private Context mContext;

    private int mScreenWidth;
    private int mScreenHeight;

    private Paint mRingPaint;
    private Paint mCirclePaint;
    private Paint mWavePaint;
    private Paint flowPaint;
    private Paint leftPaint;

    private int mRingSTROKEWidth = 25;
    private int mCircleSTROKEWidth = 15;

    private int mCircleColor = Color.WHITE;
    private int mRingColor = Color.argb(255, 0, 187, 255);
    private int mWaveColor = Color.argb(0, 0, 0, 0);

    private Handler mHandler;
    private long c = 0L;
    private boolean mStarted = false;
    private final float f = 0.033F;
    private int mAlpha = 255;// 透明度
    private float mAmplitude = 10.0F; // 振幅
    private float mWaterLevel = 0.1F;// 水高(0~1)
    private Path mPath;

    public CircleView(Context context) {
        super(context);
        mContext = context;
        init(mContext);
    }

    public CircleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init(mContext);
    }

    public CircleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        init(mContext);
    }

    public void setmWaterLevel(float mWaterLevel) {
        if (mWaterLevel >= 0.99f)mWaterLevel = 0.99f;
        if(mWaterLevel <= 0)mWavePaint.setColor(Color.argb(0, 0, 0, 0)); // 低於等於 0% 不顯示
        else if(mWaterLevel <= 0.2f) mWavePaint.setColor(Color.argb(255, 255, 0, 0)); // 低於等於 20% 顯示紅色
        else if (mWaterLevel <= 0.5f) mWavePaint.setColor(Color.argb(255, 255, 136, 0)); // 低於等於 50% 顯示橘色
        else if (mWaterLevel <= 0.8f) mWavePaint.setColor(Color.argb(255, 255, 187, 0)); // 低於等於 80% 顯示黃色
        else if(mWaterLevel > 0.8f)  mWavePaint.setColor(Color.argb(255, 0, 255, 0)); // 大於 80% 顯示綠色
        this.mWaterLevel = mWaterLevel;
    }

    private void init(Context context) {
        mRingPaint = new Paint();
        mRingPaint.setColor(mRingColor);
        mRingPaint.setAlpha(mAlpha);
        mRingPaint.setStyle(Paint.Style.STROKE);
        mRingPaint.setAntiAlias(true);
        mRingPaint.setStrokeWidth(mRingSTROKEWidth);

        mCirclePaint = new Paint();
        mCirclePaint.setColor(mCircleColor);
        mCirclePaint.setAlpha(mAlpha);
        mCirclePaint.setStyle(Paint.Style.STROKE);
        mCirclePaint.setAntiAlias(true);
        mCirclePaint.setStrokeWidth(mCircleSTROKEWidth);

        flowPaint = new Paint();
        flowPaint.setColor(mCircleColor);
        flowPaint.setAlpha(mAlpha);
        flowPaint.setStyle(Paint.Style.FILL);
        flowPaint.setAntiAlias(true);
        flowPaint.setTextSize(36);

        leftPaint = new Paint();
        leftPaint.setColor(mCircleColor);
        leftPaint.setAlpha(mAlpha);
        leftPaint.setStyle(Paint.Style.FILL);
        leftPaint.setAntiAlias(true);
        leftPaint.setTextSize(36);

        mWavePaint = new Paint();
        mWavePaint.setStrokeWidth(1.0F);
        mWavePaint.setColor(mWaveColor);
        mWavePaint.setAlpha(mAlpha);
        mPath = new Path();

        mHandler = new Handler() {
            @Override
            public void handleMessage(android.os.Message msg) {
                if (msg.what == 0) {
                    invalidate();
                    if (mStarted) {
                        // 不斷發消息給自己，使自己不斷被重繪
                        mHandler.sendEmptyMessageDelayed(0, 60L);
                    }
                }
            }
        };
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = measure(widthMeasureSpec, true);
        int height = measure(heightMeasureSpec, false);
        if (width < height) {
            setMeasuredDimension(width, width);
        } else {
            setMeasuredDimension(height, height);
        }

    }

    private int measure(int measureSpec, boolean isWidth) {
        int result;
        int mode = MeasureSpec.getMode(measureSpec);
        int size = MeasureSpec.getSize(measureSpec);
        int padding = isWidth ? getPaddingLeft() + getPaddingRight()
                : getPaddingTop() + getPaddingBottom();
        if (mode == MeasureSpec.EXACTLY) {
            result = size;
        } else {
            result = isWidth ? getSuggestedMinimumWidth()
                    : getSuggestedMinimumHeight();
            result += padding;
            if (mode == MeasureSpec.AT_MOST) {
                if (isWidth) {
                    result = Math.max(result, size);
                } else {
                    result = Math.min(result, size);
                }
            }
        }
        return result;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mScreenWidth = w;
        mScreenHeight = h;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 得到控件的寬高
        int width = getWidth();
        int height = getHeight();
        setBackgroundColor(Color.argb(0, 0, 0, 0));
        // 計算當前油量線和水平中線的距離
        float centerOffset = Math.abs(mScreenWidth / 2 * mWaterLevel
                - mScreenWidth / 4);
        // 計算油量線和與水平中線的角度
        float horiAngle = (float) (Math.asin(centerOffset / (mScreenWidth / 4)) * 180 / Math.PI);
        // 扇形的起始角度和掃過角度
        float startAngle, sweepAngle;
        if (mWaterLevel > 0.5F) {
            startAngle = 360F - horiAngle;
            sweepAngle = 180F + 2 * horiAngle;
        } else {
            startAngle = horiAngle;
            sweepAngle = 180F - 2 * horiAngle;
        }

        // 如果未開始（未調用startWave方法）,繪制一個扇形
        if ((!mStarted) || (mScreenWidth == 0) || (mScreenHeight == 0)) {
            // 繪制,即水面靜止時的高度
            RectF oval = new RectF(mScreenWidth / 4, mScreenHeight / 4,
                    mScreenWidth * 3 / 4, mScreenHeight * 3 / 4);
            canvas.drawArc(oval, startAngle, sweepAngle, false, mWavePaint);
            return;
        }

        // 繪制,即水面靜止時的高度
        RectF oval = new RectF(mScreenWidth / 4, mScreenHeight / 4,
                mScreenWidth * 3 / 4, mScreenHeight * 3 / 4);

        canvas.drawArc(oval, startAngle, sweepAngle, false, mWavePaint);

        if (this.c >= 8388607L) {
            this.c = 0L;
        }
        // 每次onDraw時c都會自增
        c = (1L + c);
        float f1 = mScreenHeight * (1.0F - (0.25F + mWaterLevel / 2))
                - mAmplitude;
        // 當前油量線的長度
        float waveWidth = (float) Math.sqrt(mScreenWidth * mScreenWidth / 16
                - centerOffset * centerOffset);
        // 與圓半徑的偏移量
        float offsetWidth = mScreenWidth / 4 - waveWidth;

        int top = (int) (f1 + mAmplitude);
        mPath.reset();
        // 起始振動X坐標，結束振動X坐標
        int startX, endX;
        if (mWaterLevel > 0.50F) {
            startX = (int) (mScreenWidth / 4 + offsetWidth);
            endX = (int) (mScreenWidth / 2 + mScreenWidth / 4 - offsetWidth);
        } else {
            startX = (int) (mScreenWidth / 4 + offsetWidth - mAmplitude);
            endX = (int) (mScreenWidth / 2 + mScreenWidth / 4 - offsetWidth + mAmplitude);
        }
        // 波浪效果
        while (startX < endX) {
            int startY = (int) (f1 - mAmplitude
                    * Math.sin(Math.PI
                    * (2.0F * (startX + this.c * width * this.f))
                    / width));
            canvas.drawLine(startX, startY, startX, top, mWavePaint);
            startX++;
        }
        canvas.drawCircle(mScreenWidth / 2, mScreenHeight / 2, mScreenWidth / 4
                + mRingSTROKEWidth / 2, mRingPaint);

        canvas.drawCircle(mScreenWidth / 2, mScreenHeight / 2,
                mScreenWidth / 4, mCirclePaint);
        canvas.restore();
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.progress = (int) c;
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        c = ss.progress;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // 關閉硬件加速，防止異常unsupported operation exception
        this.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }
    public void startWave() {
        if (!mStarted) {
            this.c = 0L;
            mStarted = true;
            this.mHandler.sendEmptyMessage(0);
        }
    }
    public void stopWave() {
        if (mStarted) {
            this.c = 0L;
            mStarted = false;
            this.mHandler.removeMessages(0);
        }
    }

    static class SavedState extends BaseSavedState {
        int progress;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            progress = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(progress);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

}