package com.grapefruitlauncher;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class WorkspaceModelTest {
    @Test public void packsManyAppsIntoPagesWithoutOverlaps(){
        List<String> ids=new ArrayList<>();for(int i=0;i<80;i++)ids.add("app"+i);
        Map<String,HomeLayout.Cell> result=WorkspaceModel.resolve(ids,Collections.emptyMap(),4,4,false);
        assertEquals(80,result.size());assertEquals(5,WorkspaceModel.pages(result,4));
        for(Map.Entry<String,HomeLayout.Cell> a:result.entrySet())for(Map.Entry<String,HomeLayout.Cell> b:result.entrySet())if(!a.getKey().equals(b.getKey()))assertFalse(HomeLayout.overlaps(a.getValue(),b.getValue()));
    }
    @Test public void keepsLegacyGapsAndFinePositionsInsidePageBounds(){
        Map<String,HomeLayout.Cell> saved=new LinkedHashMap<>();saved.put("a",new HomeLayout.Cell(1,7));saved.put("b",new HomeLayout.Cell(4,12));
        Map<String,HomeLayout.Cell> result=WorkspaceModel.resolve(Arrays.asList("a","b","c"),saved,4,4,true);
        assertEquals(new HomeLayout.Cell(1,6),result.get("a"));assertEquals(new HomeLayout.Cell(4,12),result.get("b"));assertEquals(2,WorkspaceModel.pages(result,4));
        assertEquals(new HomeLayout.Cell(1,7),saved.get("a"));
    }
    @Test public void resizingAndFineCellReservationsNeverOverlap(){
        List<String> ids=Arrays.asList("a","b","c","d","e","f","g");Map<String,HomeLayout.Cell> saved=new LinkedHashMap<>();saved.put("a",new HomeLayout.Cell(1,1));saved.put("b",new HomeLayout.Cell(10,20));
        Map<String,HomeLayout.Cell> result=WorkspaceModel.resolve(ids,saved,2,2,true);assertEquals(ids.size(),result.size());
        for(Map.Entry<String,HomeLayout.Cell> a:result.entrySet()){assertTrue(a.getValue().x<=2);assertTrue(a.getValue().y%4<=2);for(Map.Entry<String,HomeLayout.Cell> b:result.entrySet())if(!a.getKey().equals(b.getKey()))assertFalse(HomeLayout.overlaps(a.getValue(),b.getValue()));}
    }
}
