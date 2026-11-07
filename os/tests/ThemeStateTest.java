package com.android.settings.grapefruit;
public final class ThemeStateTest {
    public static void main(String[] args){
        ThemeState state=new ThemeState(0,0,"old","old");
        for(int i=0;i<4;i++){
            state=state.select(i,"revision-"+i);
            if(state.theme!=i || state.mediaTheme!=i || !state.revision.equals(state.mediaRevision))throw new AssertionError("Nonatomic universal/media selection");
        }
        for(int invalid:new int[]{-1,4,Integer.MAX_VALUE}){
            try{state.select(invalid,"bad");throw new AssertionError("Accepted invalid theme");}catch(IllegalArgumentException expected){}
        }
        System.out.println("Theme state: four atomic universal/media selections and invalid input rejection passed");
    }
}
