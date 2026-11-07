// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitlauncher;

import java.util.*;

/** Coordinates use half-cells. An icon always occupies a full 2 × 2 half-cell region. */
final class HomeLayout {
    static final int MAX_Y=2046;
    static final class Cell {
        final int x,y;
        Cell(int x,int y){this.x=x;this.y=y;}
        @Override public boolean equals(Object value){return value instanceof Cell && ((Cell)value).x==x && ((Cell)value).y==y;}
        @Override public int hashCode(){return x*31+y;}
    }
    static Cell snap(float x,float y,int columns,boolean subgrid) {
        int step=subgrid?1:2;
        return new Cell(Math.max(0,Math.min((columns-1)*2,Math.round(x/step)*step)),
                Math.max(0,Math.min(MAX_Y,Math.round(y/step)*step)));
    }
    static boolean overlaps(Cell a,Cell b){return Math.abs(a.x-b.x)<2 && Math.abs(a.y-b.y)<2;}
    static boolean free(Cell cell,Map<String,Cell> positions,String... excluded) {
        for(Map.Entry<String,Cell> entry:positions.entrySet()) {
            boolean skip=false;for(String id:excluded)if(entry.getKey().equals(id)){skip=true;break;}
            if(!skip && overlaps(cell,entry.getValue()))return false;
        }
        return true;
    }
    static Map<String,Cell> resolve(List<String> ids,Map<String,Cell> saved,int columns,boolean subgrid) {
        Map<String,Cell> result=new LinkedHashMap<>();
        // Reserve valid saved spaces before assigning new/unplaced icons.
        for(String id:ids) {
            Cell cell=saved.get(id);
            if(cell!=null && cell.x>=0 && cell.x<columns*2-1 && cell.y>=0 && cell.y<=MAX_Y) {
                Cell snapped=snap(cell.x,cell.y,columns,subgrid);
                if(free(snapped,result))result.put(id,snapped);
            }
        }
        for(String id:ids)if(!result.containsKey(id)) {
            outer:for(int y=0;y<=MAX_Y;y+=2)for(int x=0;x<columns*2;x+=2) {
                Cell cell=new Cell(x,y);
                if(free(cell,result)){result.put(id,cell);break outer;}
            }
        }
        return result;
    }
    /** Return moved positions, or null for an occupied/multiple-overlap drop. A single icon can swap. */
    static Map<String,Cell> drop(String id,Cell target,Map<String,Cell> positions) {
        List<String> collisions=new ArrayList<>();
        for(Map.Entry<String,Cell> entry:positions.entrySet())if(!entry.getKey().equals(id) && overlaps(target,entry.getValue()))collisions.add(entry.getKey());
        Map<String,Cell> changed=new LinkedHashMap<>();
        if(collisions.isEmpty()){changed.put(id,target);return changed;}
        Cell origin=positions.get(id);
        if(origin!=null && !overlaps(origin,target) && collisions.size()==1 && free(origin,positions,id,collisions.get(0))) {
            changed.put(collisions.get(0),origin);changed.put(id,target);return changed;
        }
        return null;
    }
}
