package com.grapefruitmusic.ui;

import android.content.*;
import android.content.ClipboardManager;
import android.graphics.*;
import android.os.Build;
import android.text.*;
import android.text.method.PasswordTransformationMethod;
import android.util.AttributeSet;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import java.text.BreakIterator;
import java.util.*;

/**
 * Android 4.x editing without Editor's platform action bar, insertion popup or selection handles.
 * EditText still owns the text buffer, input connection, filters, IME and hardware-key editing.
 * Newer Android uses its normal editor. This class and all call sites belong only to Blue.
 */
public final class BlueEditText extends EditText {
    private final boolean legacy;
    private final GestureDetector gestures;
    private final int slop;
    private final Paint handlePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private GrapefruitDialog actions;
    private float downX,downY,lastX,lastY;
    private int draggedHandle,anchor;
    private boolean wordGesture,moved;

    public BlueEditText(Context c){this(c,null);}
    public BlueEditText(Context c,AttributeSet attrs){this(c,attrs,android.R.attr.editTextStyle);}
    public BlueEditText(Context c,AttributeSet attrs,int style){this(c,attrs,style,Build.VERSION.SDK_INT<21);}
    // Exercise the exact legacy path on supported desktop test runtimes, which start at API 23.
    BlueEditText(Context c,boolean legacy){this(c,null,android.R.attr.editTextStyle,legacy);}
    private BlueEditText(Context c,AttributeSet attrs,int style,boolean legacy){
        super(c,attrs,style);this.legacy=legacy;slop=ViewConfiguration.get(c).getScaledTouchSlop();
        gestures=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public void onLongPress(MotionEvent e){
                if(draggedHandle!=0)return;
                selectWord(offset(e.getX(),e.getY()));anchor=getSelectionStart();wordGesture=true;
                disallowScroll(true);performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            @Override public boolean onDoubleTap(MotionEvent e){
                selectWord(offset(e.getX(),e.getY()));anchor=getSelectionStart();wordGesture=true;return true;
            }
        });
    }

    @Override public boolean onTouchEvent(MotionEvent event){
        if(!legacy)return super.onTouchEvent(event);
        if(!isEnabled())return false;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            dismissActions();wordGesture=false;moved=false;downX=lastX=event.getX();downY=lastY=event.getY();
            draggedHandle=hitHandle(downX,downY);requestFocus();setPressed(true);
            if(draggedHandle!=0)disallowScroll(true);
        }
        gestures.onTouchEvent(event);
        if(action==MotionEvent.ACTION_MOVE){
            if(Math.abs(event.getX()-downX)>slop||Math.abs(event.getY()-downY)>slop)moved=true;
            int at=offset(event.getX(),event.getY());
            if(draggedHandle==1)setSelection(floorBoundary(Math.min(at,Math.max(0,getSelectionEnd()-1))),getSelectionEnd());
            else if(draggedHandle==2)setSelection(getSelectionStart(),ceilBoundary(Math.max(at,Math.min(length(),getSelectionStart()+1))));
            else if(wordGesture)setSelection(Math.min(anchor,at),Math.max(anchor,at));
            else if(moved)scrollText(lastX-event.getX(),lastY-event.getY());
            lastX=event.getX();lastY=event.getY();invalidate();
        }else if(action==MotionEvent.ACTION_UP){
            setPressed(false);disallowScroll(false);
            if(wordGesture||draggedHandle!=0)showActions();
            else if(!moved){setSelection(offset(event.getX(),event.getY()));performClick();showKeyboard();}
            draggedHandle=0;
        }else if(action==MotionEvent.ACTION_CANCEL){
            setPressed(false);disallowScroll(false);draggedHandle=0;wordGesture=false;
        }
        // Never send legacy touch gestures into android.widget.Editor: that would reopen the
        // platform handle/paste windows even if its selection ActionMode callback is disabled.
        return true;
    }
    @Override public boolean performClick(){return super.performClick();}
    @Override public boolean performLongClick(){
        if(!legacy)return super.performLongClick();if(!isEnabled())return false;
        requestFocus();if(getSelectionStart()==getSelectionEnd())selectWord(Math.max(0,getSelectionStart()));
        showActions();return true;
    }
    @Override public boolean showContextMenu(){if(!legacy)return super.showContextMenu();showActions();return true;}
    @Override public ActionMode startActionMode(ActionMode.Callback callback){
        if(!legacy)return super.startActionMode(callback);post(this::showActions);return null;
    }
    @Override public boolean onTextContextMenuItem(int id){
        if(!legacy)return super.onTextContextMenuItem(id);
        Editable text=getText();int start=Math.max(0,Math.min(getSelectionStart(),getSelectionEnd())),end=Math.max(start,Math.max(getSelectionStart(),getSelectionEnd()));end=Math.min(end,text.length());start=Math.min(start,end);
        ClipboardManager clipboard=(ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if(id==android.R.id.selectAll){clearComposingText();setSelection(0,length());invalidate();return true;}
        if(id==android.R.id.copy||id==android.R.id.cut){
            if(password()||start==end||clipboard==null||id==android.R.id.cut&&!editable())return false;
            try{clipboard.setPrimaryClip(ClipData.newPlainText("",text.subSequence(start,end).toString()));}catch(SecurityException unavailable){return false;}
            if(id==android.R.id.cut){clearComposingText();text.delete(start,end);setSelection(start);}invalidate();return true;
        }
        if(id==android.R.id.paste){
            CharSequence clip=clipboardText();if(!editable()||clip==null)return false;
            clearComposingText();int oldLength=text.length();text.replace(start,end,clip.toString());
            // Filters (numeric inputs, maximum lengths) can reject or shorten the pasted text.
            setSelection(Math.min(length(),Math.max(start,start+text.length()-(oldLength-(end-start)))));return true;
        }
        return false;
    }
    private boolean editable(){return isEnabled()&&getKeyListener()!=null;}
    private boolean password(){
        if(getTransformationMethod() instanceof PasswordTransformationMethod)return true;
        int type=getInputType(),kind=type&InputType.TYPE_MASK_CLASS,variation=type&InputType.TYPE_MASK_VARIATION;
        return kind==InputType.TYPE_CLASS_TEXT&&(variation==InputType.TYPE_TEXT_VARIATION_PASSWORD||variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD||variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                ||kind==InputType.TYPE_CLASS_NUMBER&&variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }
    private CharSequence clipboardText(){
        ClipboardManager clipboard=(ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);if(clipboard==null)return null;
        try{ClipData clip=clipboard.getPrimaryClip();if(clip==null||clip.getItemCount()==0)return null;ClipData.Item item=clip.getItemAt(0);return item.getText()!=null?item.getText():item.getUri()!=null?item.getUri().toString():null;}
        catch(SecurityException unavailable){return null;}
    }
    private void showActions(){
        if(!legacy||!isEnabled()||getWindowToken()==null||actions!=null&&actions.isShowing())return;
        List<String> labels=new ArrayList<String>();List<Integer> ids=new ArrayList<Integer>();
        boolean selected=getSelectionStart()!=getSelectionEnd();
        if(selected&&!password()){
            if(editable()){labels.add("Cut");ids.add(android.R.id.cut);}labels.add("Copy");ids.add(android.R.id.copy);
        }
        if(editable()&&clipboardText()!=null){labels.add("Paste");ids.add(android.R.id.paste);}
        if(length()>0){labels.add("Select all");ids.add(android.R.id.selectAll);}
        if(labels.isEmpty())return;
        actions=Ui.dialog(getContext()).setTitle("Edit text").setItems(labels.toArray(new String[0]),(dialog,index)->onTextContextMenuItem(ids.get(index))).create();
        actions.setOnDismissListener(dialog->{if(actions==dialog)actions=null;post(()->{if(actions==null&&getWindowToken()!=null){requestFocus();showKeyboard();invalidate();}});});actions.show();
    }
    private void dismissActions(){GrapefruitDialog dialog=actions;actions=null;if(dialog!=null)dialog.dismiss();}
    private void showKeyboard(){if(editable()){InputMethodManager input=(InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);if(input!=null)input.showSoftInput(this,InputMethodManager.SHOW_IMPLICIT);}}
    private int offset(float x,float y){return floorBoundary(getOffsetForPosition(x,y));}
    private int floorBoundary(int at){at=Math.max(0,Math.min(length(),at));return at>0&&at<length()&&Character.isLowSurrogate(getText().charAt(at))&&Character.isHighSurrogate(getText().charAt(at-1))?at-1:at;}
    private int ceilBoundary(int at){at=Math.max(0,Math.min(length(),at));return at>0&&at<length()&&Character.isLowSurrogate(getText().charAt(at))&&Character.isHighSurrogate(getText().charAt(at-1))?at+1:at;}
    private void selectWord(int at){
        clearComposingText();if(length()==0){setSelection(0);return;}String text=getText().toString();at=Math.max(0,Math.min(at,text.length()-1));
        if(at>0&&Character.isLowSurrogate(text.charAt(at))&&Character.isHighSurrogate(text.charAt(at-1)))at--;
        BreakIterator words=BreakIterator.getWordInstance(Locale.getDefault());words.setText(text);
        int start=words.isBoundary(at)?at:words.preceding(at),end=words.following(at);
        setSelection(start==BreakIterator.DONE?0:start,end==BreakIterator.DONE?text.length():end);invalidate();
    }
    private void disallowScroll(boolean disallow){if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(disallow);}
    private void scrollText(float dx,float dy){
        Layout layout=getLayout();if(layout==null)return;float widest=0;for(int n=0;n<layout.getLineCount();n++)widest=Math.max(widest,layout.getLineMax(n));
        int maxX=Math.max(0,(int)Math.ceil(widest)-getWidth()+getCompoundPaddingLeft()+getCompoundPaddingRight());
        int maxY=Math.max(0,layout.getHeight()-getHeight()+getCompoundPaddingTop()+getCompoundPaddingBottom());
        int x=Math.max(0,Math.min(maxX,getScrollX()+Math.round(dx))),y=Math.max(0,Math.min(maxY,getScrollY()+Math.round(dy)));
        if(x!=getScrollX()||y!=getScrollY()){disallowScroll(true);scrollTo(x,y);}
    }
    PointF handleAt(int offset){
        Layout layout=getLayout();if(layout==null)return null;int at=Math.max(0,Math.min(length(),offset)),line=layout.getLineForOffset(at);float radius=Ui.dp(getContext(),5);
        return new PointF(Math.max(radius,Math.min(getWidth()-radius,layout.getPrimaryHorizontal(at)+getCompoundPaddingLeft()-getScrollX())),Math.max(radius,Math.min(getHeight()-radius,layout.getLineBottom(line)+getTotalPaddingTop()-getScrollY()+radius)));
    }
    private int hitHandle(float x,float y){
        if(!hasFocus()||getSelectionStart()<0||getSelectionStart()==getSelectionEnd())return 0;
        PointF a=handleAt(getSelectionStart()),b=handleAt(getSelectionEnd());if(a==null||b==null)return 0;
        float da=(x-a.x)*(x-a.x)+(y-a.y)*(y-a.y),db=(x-b.x)*(x-b.x)+(y-b.y)*(y-b.y),radius=Ui.dp(getContext(),24);
        return Math.min(da,db)>radius*radius?0:da<db?1:2;
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(!legacy||!hasFocus()||getSelectionStart()<0||getSelectionStart()==getSelectionEnd())return;
        handlePaint.setColor(Ui.accent(getContext()));
        for(int at:new int[]{getSelectionStart(),getSelectionEnd()}){PointF p=handleAt(at);if(p!=null)canvas.drawCircle(p.x+getScrollX(),p.y+getScrollY(),Ui.dp(getContext(),5),handlePaint);}
    }
    @Override protected void onDetachedFromWindow(){dismissActions();super.onDetachedFromWindow();}
}
