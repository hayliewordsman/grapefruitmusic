package com.grapefruitlauncher;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class HomeLayoutTest {
    private HomeLayout.Cell cell(int x,int y){return new HomeLayout.Cell(x,y);}
    private Map<String,HomeLayout.Cell> layout(){Map<String,HomeLayout.Cell> p=new LinkedHashMap<>();p.put("a",cell(0,0));p.put("b",cell(4,4));p.put("c",cell(2,6));return p;}
    @Test public void savedGapsSurviveResolutionAndANewIconUsesAnEmptySpace(){
        Map<String,HomeLayout.Cell> old=layout(),placed=HomeLayout.resolve(Arrays.asList("a","b","c","d"),old,4,false);
        for(String id:old.keySet())assertEquals(old.get(id),placed.get(id));assertEquals(cell(2,0),placed.get("d"));
    }
    @Test public void emptyDropMovesOnlyTheSelectedIcon(){
        Map<String,HomeLayout.Cell> old=layout(),changed=HomeLayout.drop("a",cell(6,8),old);
        assertEquals(1,changed.size());assertEquals(cell(6,8),changed.get("a"));assertEquals(cell(0,0),old.get("a"));
    }
    @Test public void occupiedDropSwapsAnExistingIconButRejectsANewShortcut(){
        Map<String,HomeLayout.Cell> old=layout(),changed=HomeLayout.drop("a",old.get("b"),old);
        assertEquals(cell(0,0),changed.get("b"));assertEquals(cell(4,4),changed.get("a"));assertNull(HomeLayout.drop("new",old.get("b"),old));
    }
    @Test public void subgridNeverOverlapsAdjacentIcons(){
        Map<String,HomeLayout.Cell> old=new LinkedHashMap<>();old.put("a",cell(0,0));old.put("b",cell(2,0));old.put("c",cell(4,0));
        assertNull(HomeLayout.drop("a",cell(1,0),old));assertNull(HomeLayout.drop("new",cell(3,0),old));
        assertNotNull(HomeLayout.drop("a",cell(1,3),old));
    }
    @Test public void optionalSubgridUsesHalfCellsWithoutDestroyingFinePositions(){
        Map<String,HomeLayout.Cell> saved=new LinkedHashMap<>();saved.put("a",cell(1,1));saved.put("b",cell(4,1));
        assertEquals(cell(1,1),HomeLayout.resolve(Arrays.asList("a","b"),saved,4,true).get("a"));
        assertEquals(cell(2,2),HomeLayout.resolve(Arrays.asList("a","b"),saved,4,false).get("a"));
        assertEquals(cell(1,1),saved.get("a"));assertEquals(cell(1,1),HomeLayout.resolve(Arrays.asList("a","b"),saved,4,true).get("a"));
    }
    @Test public void resizingRetainsEveryIconWithinBoundsWithoutCollisions(){
        Map<String,HomeLayout.Cell> saved=layout();saved.put("d",cell(14,0));List<String> ids=Arrays.asList("a","b","c","d");
        Map<String,HomeLayout.Cell> compact=HomeLayout.resolve(ids,saved,2,false);assertEquals(4,compact.size());
        for(HomeLayout.Cell p:compact.values())assertTrue(p.x>=0 && p.x<=2);
        for(String a:ids)for(String b:ids)if(!a.equals(b))assertFalse(HomeLayout.overlaps(compact.get(a),compact.get(b)));
        assertEquals(cell(14,0),saved.get("d"));
    }
    @Test public void snapStaysInsideTheAvailableColumns(){
        assertEquals(cell(0,0),HomeLayout.snap(-10,-2,4,true));assertEquals(cell(6,4),HomeLayout.snap(999,3.8f,4,false));
        assertEquals(cell(3,5),HomeLayout.snap(3.1f,4.7f,4,true));
    }
}
