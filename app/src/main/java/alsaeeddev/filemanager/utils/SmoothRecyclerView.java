package alsaeeddev.filemanager.utils;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import java.lang.reflect.Field;


public class SmoothRecyclerView extends RecyclerView {

    public SmoothRecyclerView(@NonNull Context context) {
        super(context);
        initScroller();
    }

    public SmoothRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initScroller();
    }

    public SmoothRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initScroller();
    }

    private void initScroller() {
        post(() -> {
            try {
                Field field = RecyclerView.class.getDeclaredField("mViewFlinger");
                field.setAccessible(true);
                Object flinger = field.get(this);

                Field scrollerField = flinger.getClass().getDeclaredField("mOverScroller");
                scrollerField.setAccessible(true);

                OverScroller scroller = new OverScroller(getContext(), new DecelerateInterpolator(1.5f));
                scrollerField.set(flinger, scroller);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public boolean fling(int velocityX, int velocityY) {
        //  Use raw user fling for natural scroll
        return super.fling(velocityX, velocityY);
    }
}