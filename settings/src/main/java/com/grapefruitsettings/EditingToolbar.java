package com.grapefruitsettings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Uses two rows when four editing controls cannot fit at the current width and font size. */
final class EditingToolbar extends ViewGroup {
    interface Action {void perform(int action);}
    private final int gap,minCellWidth;
    private int columns,rowHeight;
    EditingToolbar(Context context,Palette palette,Action action){
        super(context);gap=palette.dp(8);minCellWidth=palette.dp(64);
        String[] labels={"Select all","Copy","Cut","Paste"};
        for(int i=0;i<labels.length;i++){
            final int id=i;TextView button=palette.chip(labels[i],false,v->action.perform(id));
            button.setTextSize(12);button.setPadding(palette.dp(8),palette.dp(10),palette.dp(8),palette.dp(10));
            addView(button);
        }
    }
    @Override protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int preferred=minCellWidth;
        for(int i=0;i<getChildCount();i++){
            TextView button=(TextView)getChildAt(i);
            preferred=Math.max(preferred,Math.max(button.getMinimumHeight(),(int)Math.ceil(button.getPaint().measureText(button.getText().toString()))+button.getPaddingLeft()+button.getPaddingRight()));
        }
        int width=resolveSize(preferred*4+gap*3,widthSpec);
        columns=width>=preferred*4+gap*3?4:width>=preferred*2+gap?2:1;
        int cell=Math.max(0,(width-gap*(columns-1))/columns);rowHeight=0;
        for(int i=0;i<getChildCount();i++){
            View button=getChildAt(i);button.measure(MeasureSpec.makeMeasureSpec(cell,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            rowHeight=Math.max(rowHeight,button.getMeasuredHeight());
        }
        int rows=(getChildCount()+columns-1)/columns;
        setMeasuredDimension(width,resolveSize(rows*rowHeight+(rows-1)*gap,heightSpec));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        int cell=Math.max(0,(getWidth()-gap*(columns-1))/columns);
        for(int i=0;i<getChildCount();i++){
            int x=(i%columns)*(cell+gap),y=(i/columns)*(rowHeight+gap);
            getChildAt(i).layout(x,y,x+cell,y+rowHeight);
        }
    }
}
