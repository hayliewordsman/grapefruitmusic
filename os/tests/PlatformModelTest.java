package org.grapefruit.platform;
import java.util.Arrays;
import java.util.List;

public final class PlatformModelTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] ignored){
        // Includes both JP5S orientations, odd widths, different densities and narrow windows.
        for(int width=7;width<=4096;width++){
            int previous=0,min=width,max=0;
            for(int i=0;i<7;i++){
                int left=NavigationGeometry.boundary(width,i),right=NavigationGeometry.boundary(width,i+1);
                check(left==previous && right>=left,"Gap or overlap");previous=right;
                min=Math.min(min,right-left);max=Math.max(max,right-left);
            }
            check(previous==width && max-min<=1,"Unequal slots or uncovered pixels");
            check(NavigationGeometry.boundary(width,3)+NavigationGeometry.boundary(width,4)==width,"Home off center");
        }
        check(Arrays.equals(NavigationGeometry.SLOTS,new String[]{"screenshot","volume_down","back","home","recent_apps","volume_up","spacer"}),"Slot ordering");
        check(TileOrder.parse(null).size()==TileOrder.IDS.length,"Default tiles");
        check(TileOrder.parse("").isEmpty(),"An intentionally empty tile list must stay empty");
        check(TileOrder.parse("wifi,wifi,unknown,,battery").equals(Arrays.asList("wifi","battery")),"Migration sanitization");
        List<String> changed=TileOrder.parse("battery,wifi");changed.add("brightness");
        check(TileOrder.parse(TileOrder.join(changed)).equals(changed),"Reorder/add persistence");
        for(int i=0;i<4;i++){
            Palette p=new Palette(i);check(p.text!=p.background && p.accent!=p.control,"Palette contrast roles");
        }
        check(new Palette(2).background==0xff000000,"Pitch Black background");
        check(new Palette(3).background==0xff07162b,"Victory Blue background");
        System.out.println("Platform model: 4090 display widths, tile persistence and four palettes passed");
    }
}
