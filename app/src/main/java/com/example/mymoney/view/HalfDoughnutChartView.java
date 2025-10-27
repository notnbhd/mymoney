package com.example.mymoney.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.mymoney.R;

public class HalfDoughnutChartView extends View {
    
    private Paint expensePaint;
    private Paint incomePaint;
    private Paint backgroundPaint;
    private RectF arcRect;
    
    private double expenseAmount = 0;
    private double incomeAmount = 0;
    
    private static final float START_ANGLE = 180f; 
    private static final float SWEEP_ANGLE = 180f;
    private static final float STROKE_WIDTH_DP = 24f; // thick
    
    public HalfDoughnutChartView(Context context) {
        super(context);
        init(context);
    }
    
    public HalfDoughnutChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public HalfDoughnutChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        // Initialize paints
        expensePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        expensePaint.setStyle(Paint.Style.STROKE);
        expensePaint.setStrokeWidth(dpToPx(STROKE_WIDTH_DP));
        expensePaint.setStrokeCap(Paint.Cap.ROUND);
        expensePaint.setColor(ContextCompat.getColor(context, R.color.red_expense));
        
        incomePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        incomePaint.setStyle(Paint.Style.STROKE);
        incomePaint.setStrokeWidth(dpToPx(STROKE_WIDTH_DP));
        incomePaint.setStrokeCap(Paint.Cap.ROUND);
        incomePaint.setColor(ContextCompat.getColor(context, R.color.primary_green));
        
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(dpToPx(STROKE_WIDTH_DP));
        backgroundPaint.setStrokeCap(Paint.Cap.ROUND);
        backgroundPaint.setColor(ContextCompat.getColor(context, R.color.light_gray));
        
        arcRect = new RectF();
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        // Set up the rectangle for drawing arcs
        float strokeWidth = dpToPx(STROKE_WIDTH_DP);
        float padding = strokeWidth / 2;
        arcRect.set(
            padding,
            padding,
            w - padding,
            h - padding
        );
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (arcRect.isEmpty()) {
            return;
        }
        
        // Draw background arc (light gray)
        canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE, false, backgroundPaint);
        
        // Calculate total and percentages
        double total = expenseAmount + incomeAmount;
        
        if (total > 0) {
            // Calculate angles based on ratio
            float expenseRatio = (float) (expenseAmount / total);
            float incomeRatio = (float) (incomeAmount / total);
            
            float expenseSweep = SWEEP_ANGLE * expenseRatio;
            float incomeSweep = SWEEP_ANGLE * incomeRatio;
            
            // Draw expense arc (red) - starts from left
            if (expenseAmount > 0) {
                canvas.drawArc(arcRect, START_ANGLE, expenseSweep, false, expensePaint);
            }
            
            // Draw income arc (green) - continues after expense
            if (incomeAmount > 0) {
                canvas.drawArc(arcRect, START_ANGLE + expenseSweep, incomeSweep, false, incomePaint);
            }
        }
    }
    
    /**
     * Set the expense and income amounts to display on the chart
     * @param expense Total expense amount
     * @param income Total income amount
     */
    public void setData(double expense, double income) {
        this.expenseAmount = Math.abs(expense); // Use absolute values
        this.incomeAmount = Math.abs(income);
        invalidate(); // Redraw the view
    }
    
    private float dpToPx(float dp) {
        return dp * getContext().getResources().getDisplayMetrics().density;
    }
}
