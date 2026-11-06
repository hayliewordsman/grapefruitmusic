// SPDX-License-Identifier: GPL-3.0-or-later
package com.grapefruitlauncher;

import com.grapefruitlauncher.launcher2.CellLayout;
import java.util.*;

/** Persistent half-cell coordinates, arranged into Launcher2 pages without losing saved gaps. */
final class WorkspaceModel {
    static Map<String,HomeLayout.Cell> resolve(List<String> ids,Map<String,HomeLayout.Cell> saved,int columns,int rows,boolean fine){
        Map<String,HomeLayout.Cell> result=new LinkedHashMap<>();
        int pageUnits=rows*2;
        for(String id:ids){
            HomeLayout.Cell from=saved.get(id);if(from==null || from.x<0 || from.x>columns*2-2 || from.y<0 || from.y>HomeLayout.MAX_Y)continue;
            int page=from.y/pageUnits;
            HomeLayout.Cell local=HomeLayout.snap(from.x,Math.min(pageUnits-2,from.y%pageUnits),columns,fine);
            HomeLayout.Cell candidate=new HomeLayout.Cell(local.x,page*pageUnits+local.y);
            if(HomeLayout.free(candidate,result))result.put(id,candidate);
        }
        int[] empty=new int[2];
        for(String id:ids)if(!result.containsKey(id)){
            for(int page=0;page*pageUnits<=HomeLayout.MAX_Y;page++){
                // Full-grid placement uses a full-cell occupancy matrix; fine saved cells reserve
                // every full cell they intersect, so automatic placement never overlaps them.
                boolean[][] occupied=new boolean[columns][rows];
                for(HomeLayout.Cell cell:result.values())if(cell.y/pageUnits==page){
                    int localY=cell.y%pageUnits;
                    for(int x=cell.x/2;x<=Math.min(columns-1,(cell.x+1)/2);x++)
                        for(int y=localY/2;y<=Math.min(rows-1,(localY+1)/2);y++)occupied[x][y]=true;
                }
                if(CellLayout.findVacantCell(empty,1,1,columns,rows,occupied)){
                    result.put(id,new HomeLayout.Cell(empty[0]*2,page*pageUnits+empty[1]*2));break;
                }
            }
        }
        return result;
    }
    static int pages(Map<String,HomeLayout.Cell> positions,int rows){int pages=1;for(HomeLayout.Cell c:positions.values())pages=Math.max(pages,c.y/(rows*2)+1);return pages;}
}
