package com.grapefruitsettings;
import android.view.*;
import android.widget.*;
final class LegacyWidgets {
    private LegacyWidgets(){}
    static void replace(View view){
        if(!(view instanceof ViewGroup))return;ViewGroup group=(ViewGroup)view;
        for(int i=0;i<group.getChildCount();i++){
            View old=group.getChildAt(i);CompoundButton replacement=null;
            if(old instanceof Switch && !(old instanceof LegacySwitch))replacement=new LegacySwitch(old.getContext());
            else if(old instanceof CheckBox && !(old instanceof LegacyCheckBox))replacement=new LegacyCheckBox(old.getContext());
            if(replacement!=null){replacement.setId(old.getId());replacement.setEnabled(old.isEnabled());replacement.setFocusable(old.isFocusable());replacement.setClickable(old.isClickable());replacement.setContentDescription(old.getContentDescription());replacement.setText(((CompoundButton)old).getText());replacement.setChecked(((CompoundButton)old).isChecked());ViewGroup.LayoutParams params=old.getLayoutParams();group.removeViewAt(i);group.addView(replacement,i,params);}else replace(old);
        }
    }
}
