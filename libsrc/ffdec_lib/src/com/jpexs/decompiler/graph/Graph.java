/*
 *  Copyright (C) 2010-2015 JPEXS, All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.jpexs.decompiler.graph;

import com.jpexs.decompiler.flash.BaseLocalData;
import com.jpexs.decompiler.flash.FinalProcessLocalData;
import com.jpexs.decompiler.flash.action.Action;
import com.jpexs.decompiler.flash.ecma.EcmaScript;
import com.jpexs.decompiler.flash.helpers.GraphTextWriter;
import com.jpexs.decompiler.graph.model.AndItem;
import com.jpexs.decompiler.graph.model.BreakItem;
import com.jpexs.decompiler.graph.model.ContinueItem;
import com.jpexs.decompiler.graph.model.DoWhileItem;
import com.jpexs.decompiler.graph.model.ExitItem;
import com.jpexs.decompiler.graph.model.ForItem;
import com.jpexs.decompiler.graph.model.IfItem;
import com.jpexs.decompiler.graph.model.IntegerValueItem;
import com.jpexs.decompiler.graph.model.LocalData;
import com.jpexs.decompiler.graph.model.LogicalOpItem;
import com.jpexs.decompiler.graph.model.LoopItem;
import com.jpexs.decompiler.graph.model.NotItem;
import com.jpexs.decompiler.graph.model.OrItem;
import com.jpexs.decompiler.graph.model.ScriptEndItem;
import com.jpexs.decompiler.graph.model.SwitchItem;
import com.jpexs.decompiler.graph.model.TernarOpItem;
import com.jpexs.decompiler.graph.model.UniversalLoopItem;
import com.jpexs.decompiler.graph.model.WhileItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author JPEXS
 */
public class Graph {

    public List<GraphPart> heads;

    protected GraphSource code;

    private final List<Integer> alternateEntries;

    public static final int SOP_USE_STATIC = 0;

    public static final int SOP_SKIP_STATIC = 1;

    public static final int SOP_REMOVE_STATIC = 2;

    public Graph(GraphSource code, List<Integer> alternateEntries) {
        this.code = code;
        this.alternateEntries = alternateEntries;

    }

    public void init(BaseLocalData localData) throws InterruptedException {
        if (heads != null) {
            return;
        }
        heads = makeGraph(code, new ArrayList<GraphPart>(), alternateEntries);
        int time = 1;
        List<GraphPart> ordered = new ArrayList<>();
        List<GraphPart> visited = new ArrayList<>();
        for (GraphPart head : heads) {
            time = head.setTime(time, ordered, visited);
        }
    }

    protected static void populateParts(GraphPart part, List<GraphPart> allParts) {
        if (allParts.contains(part)) {
            return;
        }
        allParts.add(part);
        for (GraphPart p : part.nextParts) {
            populateParts(p, allParts);
        }
    }

    public GraphPart deepCopy(GraphPart part, List<GraphPart> visited, List<GraphPart> copies) {
        if (visited == null) {
            visited = new ArrayList<>();
        }
        if (copies == null) {
            copies = new ArrayList<>();
        }
        if (visited.contains(part)) {
            return copies.get(visited.indexOf(part));
        }
        visited.add(part);
        GraphPart copy = new GraphPart(part.start, part.end);
        copy.path = part.path;
        copies.add(copy);
        copy.nextParts = new ArrayList<>();
        for (int i = 0; i < part.nextParts.size(); i++) {
            copy.nextParts.add(deepCopy(part.nextParts.get(i), visited, copies));
        }
        for (int i = 0; i < part.refs.size(); i++) {
            copy.refs.add(deepCopy(part.refs.get(i), visited, copies));
        }
        return copy;
    }

    public void resetGraph(GraphPart part, List<GraphPart> visited) {
        if (visited.contains(part)) {
            return;
        }
        visited.add(part);
        int pos = 0;
        for (GraphPart p : part.nextParts) {
            if (!visited.contains(p)) {
                p.path = part.path.sub(pos, p.end);
            }
            resetGraph(p, visited);
            pos++;
        }
    }

    private void getReachableParts(GraphPart part, List<GraphPart> ret, List<Loop> loops) {
        getReachableParts(part, ret, loops, true);
    }

    private void getReachableParts(GraphPart part, List<GraphPart> ret, List<Loop> loops, boolean first) {

        if (first) {
            for (Loop l : loops) {
                l.reachableMark = 0;
            }
        }
        Loop currentLoop = null;

        for (Loop l : loops) {
            if ((l.phase == 1) || (l.reachableMark == 1)) {
                if (l.loopContinue == part) {
                    return;
                }
                if (l.loopBreak == part) {
                    return;
                }
                if (l.loopPreContinue == part) {
                    return;
                }
            }
            if (l.reachableMark == 0) {
                if (l.loopContinue == part) {
                    l.reachableMark = 1;
                    currentLoop = l;
                }
            }
        }

        List<GraphPart> newparts = new ArrayList<>();
        loopnext:
        for (GraphPart next : part.nextParts) {
            for (Loop l : loops) {
                if ((l.phase == 1) || (l.reachableMark == 1)) {
                    if (l.loopContinue == next) {
                        continue loopnext;
                    }
                    if (l.loopBreak == next) {
                        continue loopnext;
                    }
                    if (l.loopPreContinue == next) {
                        continue loopnext;
                    }
                }

            }
            if (!ret.contains(next)) {
                newparts.add(next);
            }
        }

        ret.addAll(newparts);
        for (GraphPart next : newparts) {
            getReachableParts(next, ret, loops);
        }

        if (currentLoop != null) {
            if (currentLoop.loopBreak != null) {
                if (!ret.contains(currentLoop.loopBreak)) {
                    ret.add(currentLoop.loopBreak);
                    currentLoop.reachableMark = 2;
                    getReachableParts(currentLoop.loopBreak, ret, loops);
                }
            }
        }
    }

    /* public GraphPart getNextCommonPart(GraphPart part, List<Loop> loops) {
     return getNextCommonPart(part, new ArrayList<GraphPart>(),loops);
     }*/
    public GraphPart getNextCommonPart(BaseLocalData localData, GraphPart part, List<Loop> loops) throws InterruptedException {
        return getCommonPart(localData, part.nextParts, loops);
    }

    public GraphPart getCommonPart(BaseLocalData localData, List<GraphPart> parts, List<Loop> loops) throws InterruptedException {
        if (parts.isEmpty()) {
            return null;
        }

        List<GraphPart> loopContinues = new ArrayList<>();//getLoopsContinues(loops);
        for (Loop l : loops) {
            if (l.phase == 1) {
                loopContinues.add(l.loopContinue);
            }
        }

        for (GraphPart p : parts) {
            if (loopContinues.contains(p)) {
                break;
            }
            boolean common = true;
            for (GraphPart q : parts) {
                if (q == p) {
                    continue;
                }
                if (!q.leadsTo(localData, this, code, p, loops)) {
                    common = false;
                    break;
                }
            }
            if (common) {
                return p;
            }
        }
        List<List<GraphPart>> reachable = new ArrayList<>();
        for (GraphPart p : parts) {
            List<GraphPart> r1 = new ArrayList<>();
            getReachableParts(p, r1, loops);
            r1.add(p);
            reachable.add(r1);
        }
        List<GraphPart> first = reachable.get(0);
        for (GraphPart p : first) {
            /*if (ignored.contains(p)) {
             continue;
             }*/
            p = checkPart(null, localData, p, null);
            if (p == null) {
                continue;
            }
            boolean common = true;
            for (List<GraphPart> r : reachable) {
                if (!r.contains(p)) {
                    common = false;
                    break;
                }
            }
            if (common) {
                return p;
            }
        }
        return null;
    }

    public GraphPart getMostCommonPart(BaseLocalData localData, List<GraphPart> parts, List<Loop> loops) throws InterruptedException {
        if (parts.isEmpty()) {
            return null;
        }

        Set<GraphPart> s = new HashSet<>(parts); //unique
        parts = new ArrayList<>(s); //make local copy

        List<GraphPart> loopContinues = new ArrayList<>();//getLoopsContinues(loops);
        for (Loop l : loops) {
            if (l.phase == 1) {
                loopContinues.add(l.loopContinue);
                loopContinues.add(l.loopPreContinue);
            }
        }

        for (GraphPart p : parts) {
            if (loopContinues.contains(p)) {
                break;
            }
            boolean common = true;
            for (GraphPart q : parts) {
                if (q == p) {
                    continue;
                }
                if (!q.leadsTo(localData, this, code, p, loops)) {
                    common = false;
                    break;
                }
            }
            if (common) {
                return p;
            }
        }

        loopi:
        for (int i = 0; i < parts.size(); i++) {
            for (int j = 0; j < parts.size(); j++) {
                if (j == i) {
                    continue;
                }
                if (parts.get(i).leadsTo(localData, this, code, parts.get(j), loops)) {
                    parts.remove(i);
                    i--;
                    continue loopi;
                }
            }
        }
        List<List<GraphPart>> reachable = new ArrayList<>();
        for (GraphPart p : parts) {
            List<GraphPart> r1 = new ArrayList<>();
            getReachableParts(p, r1, loops);
            r1.add(0, p);
            reachable.add(r1);
        }
        ///List<GraphPart> first = reachable.get(0);
        int commonLevel;
        Map<GraphPart, Integer> levelMap = new HashMap<>();
        for (List<GraphPart> first : reachable) {
            int maxclevel = 0;
            Set<GraphPart> visited = new HashSet<>();
            for (GraphPart p : first) {
                if (loopContinues.contains(p)) {
                    break;
                }
                if (visited.contains(p)) {
                    continue;
                }
                visited.add(p);
                boolean common = true;
                commonLevel = 1;
                for (List<GraphPart> r : reachable) {
                    if (r == first) {
                        continue;
                    }
                    if (r.contains(p)) {
                        commonLevel++;
                    }
                }
                if (commonLevel <= maxclevel) {
                    continue;
                }
                maxclevel = commonLevel;
                if (levelMap.containsKey(p)) {
                    if (levelMap.get(p) > commonLevel) {
                        commonLevel = levelMap.get(p);
                    }
                }
                levelMap.put(p, commonLevel);
                if (common) {
                    //return p;
                }
            }
        }
        for (int i = reachable.size() - 1; i >= 2; i--) {
            for (GraphPart p : levelMap.keySet()) {
                if (levelMap.get(p) == i) {
                    return p;
                }
            }
        }
        for (GraphPart p : levelMap.keySet()) {
            if (levelMap.get(p) == parts.size()) {
                return p;
            }
        }
        return null;
    }

    public GraphPart getNextNoJump(GraphPart part, BaseLocalData localData) {
        while (code.get(part.start).isJump()) {
            part = part.getSubParts().get(0).nextParts.get(0);
        }
        /*localData = prepareBranchLocalData(localData);
         TranslateStack st = new TranslateStack();
         List<GraphTargetItem> output=new ArrayList<>();
         GraphPart startPart = part;
         for (int i = part.start; i <= part.end; i++) {
         GraphSourceItem src = code.get(i);
         if (src.isJump()) {
         part = part.nextParts.get(0);
         if(st.isEmpty()){
         startPart = part;
         }
         i = part.start - 1;
         continue;
         }
         try{
         src.translate(localData, st, output, SOP_USE_STATIC, "");
         }catch(Exception ex){
         return startPart;
         }
         if(!output.isEmpty()){
         return startPart;
         }
         }*/
        return part;
    }

    public static List<GraphTargetItem> translateViaGraph(BaseLocalData localData, String path, GraphSource code, List<Integer> alternateEntries, int staticOperation) throws InterruptedException {
        Graph g = new Graph(code, alternateEntries);
        g.init(localData);
        return g.translate(localData, staticOperation, path);
    }

    public List<GraphTargetItem> translate(BaseLocalData localData, int staticOperation, String path) throws InterruptedException {
        List<GraphPart> allParts = new ArrayList<>();
        for (GraphPart head : heads) {
            populateParts(head, allParts);
        }
        TranslateStack stack = new TranslateStack();
        List<Loop> loops = new ArrayList<>();
        getLoops(localData, heads.get(0), loops, null);
        /*System.out.println("<loops>");
         for (Loop el : loops) {
         System.out.println(el);
         }
         System.out.println("</loops>");*/
        getPrecontinues(path, localData, null, heads.get(0), allParts, loops, null);
        /*System.err.println("<loopspre>");
         for (Loop el : loops) {
         System.err.println(el);
         }
         System.err.println("</loopspre>");//*/

        List<GraphTargetItem> ret = printGraph(localData, stack, allParts, null, heads.get(0), null, loops, staticOperation, path);
        processIfs(ret);
        finalProcessStack(stack, ret);
        finalProcessAll(ret, 0, new FinalProcessLocalData());
        return ret;

    }

    public void finalProcessStack(TranslateStack stack, List<GraphTargetItem> output) {
    }

    private void finalProcessAll(List<GraphTargetItem> list, int level, FinalProcessLocalData localData) {
        finalProcess(list, level, localData);
        for (GraphTargetItem item : list) {
            if (item instanceof Block) {
                List<List<GraphTargetItem>> subs = ((Block) item).getSubs();
                for (List<GraphTargetItem> sub : subs) {
                    finalProcessAll(sub, level + 1, localData);
                }
            }
        }
    }

    protected void finalProcess(List<GraphTargetItem> list, int level, FinalProcessLocalData localData) {

        //For detection based on debug line information
        Set<Integer> removeFromList = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {

            if (list.get(i) instanceof ForItem) {
                ForItem fori = (ForItem) list.get(i);
                int exprLine = fori.getLine();
                if (exprLine > 0) {
                    List<GraphTargetItem> forFirstCommands = new ArrayList<>();
                    for (int j = i - 1; j >= 0; j--) {
                        if (list.get(j).getLine() == exprLine) {
                            forFirstCommands.add(0, list.get(j));
                            removeFromList.add(j);
                        } else {
                            break;
                        }
                    }
                    fori.firstCommands.addAll(0, forFirstCommands);
                }
            }

            if (list.get(i) instanceof WhileItem) {
                WhileItem whi = (WhileItem) list.get(i);
                int whileExprLine = whi.getLine();
                if (whileExprLine > 0) {
                    List<GraphTargetItem> forFirstCommands = new ArrayList<>();
                    List<GraphTargetItem> forFinalCommands = new ArrayList<>();

                    for (int j = i - 1; j >= 0; j--) {
                        if (list.get(j).getLine() == whileExprLine) {
                            forFirstCommands.add(0, list.get(j));
                            removeFromList.add(j);
                        } else {
                            break;
                        }
                    }
                    for (int j = whi.commands.size() - 1; j >= 0; j--) {
                        if (whi.commands.get(j).getLine() == whileExprLine) {
                            forFinalCommands.add(0, whi.commands.remove(j));
                        } else {
                            break;
                        }
                    }
                    if (!forFirstCommands.isEmpty() || !forFinalCommands.isEmpty()) {
                        GraphTargetItem lastExpr = whi.expression.remove(whi.expression.size() - 1);
                        forFirstCommands.addAll(whi.expression);
                        list.set(i, new ForItem(whi.src, whi.loop, forFirstCommands, lastExpr, forFinalCommands, whi.commands));
                    }
                }
            }
        }

        for (int i = list.size() - 1; i >= 0; i--) {
            if (removeFromList.contains(i)) {
                list.remove(i);
            }
        }
    }

    private void processIfs(List<GraphTargetItem> list) {
        //if(true) return;
        for (int i = 0; i < list.size(); i++) {
            GraphTargetItem item = list.get(i);
            if (item instanceof Block) {
                List<List<GraphTargetItem>> subs = ((Block) item).getSubs();
                for (List<GraphTargetItem> sub : subs) {
                    processIfs(sub);
                }
            }
            if ((item instanceof LoopItem) && (item instanceof Block)) {
                List<List<GraphTargetItem>> subs = ((Block) item).getSubs();
                for (List<GraphTargetItem> sub : subs) {
                    processIfs(sub);
                    checkContinueAtTheEnd(sub, ((LoopItem) item).loop);
                }
            }
            if (item instanceof IfItem) {
                IfItem ifi = (IfItem) item;
                List<GraphTargetItem> onTrue = ifi.onTrue;
                List<GraphTargetItem> onFalse = ifi.onFalse;
                if ((!onTrue.isEmpty()) && (!onFalse.isEmpty())) {
                    if (onTrue.get(onTrue.size() - 1) instanceof ContinueItem) {
                        if (onFalse.get(onFalse.size() - 1) instanceof ContinueItem) {
                            if (((ContinueItem) onTrue.get(onTrue.size() - 1)).loopId == ((ContinueItem) onFalse.get(onFalse.size() - 1)).loopId) {
                                onTrue.remove(onTrue.size() - 1);
                                list.add(i + 1, onFalse.remove(onFalse.size() - 1));
                            }
                        }
                    }
                }

                if ((!onTrue.isEmpty()) && (!onFalse.isEmpty())) {
                    GraphTargetItem last = onTrue.get(onTrue.size() - 1);
                    if ((last instanceof ExitItem) || (last instanceof ContinueItem) || (last instanceof BreakItem)) {
                        list.addAll(i + 1, onFalse);
                        onFalse.clear();
                    }
                }

                if ((!onTrue.isEmpty()) && (!onFalse.isEmpty())) {
                    if (onFalse.get(onFalse.size() - 1) instanceof ExitItem) {
                        if (onTrue.get(onTrue.size() - 1) instanceof ContinueItem) {
                            list.add(i + 1, onTrue.remove(onTrue.size() - 1));
                        }
                    }
                }
            }
        }

        //Same continues in onTrue and onFalse gets continue on parent level
    }

    protected List<GraphPart> getLoopsContinuesPreAndBreaks(List<Loop> loops) {
        List<GraphPart> ret = new ArrayList<>();
        for (Loop l : loops) {
            if (l.loopContinue != null) {
                ret.add(l.loopContinue);
            }
            if (l.loopPreContinue != null) {
                ret.add(l.loopPreContinue);
            }
            if (l.loopBreak != null) {
                ret.add(l.loopBreak);
            }
        }
        return ret;
    }

    protected List<GraphPart> getLoopsContinuesAndPre(List<Loop> loops) {
        List<GraphPart> ret = new ArrayList<>();
        for (Loop l : loops) {
            if (l.loopContinue != null) {
                ret.add(l.loopContinue);
            }
            if (l.loopPreContinue != null) {
                ret.add(l.loopPreContinue);
            }
        }
        return ret;
    }

    protected List<GraphPart> getLoopsContinues(List<Loop> loops) {
        List<GraphPart> ret = new ArrayList<>();
        for (Loop l : loops) {
            if (l.loopContinue != null) {
                ret.add(l.loopContinue);
            }
            /*if (l.loopPreContinue != null) {
             ret.add(l.loopPreContinue);
             }*/
        }
        return ret;
    }

    protected GraphTargetItem checkLoop(GraphPart part, List<GraphPart> stopPart, List<Loop> loops) {
        if (stopPart.contains(part)) {
            return null;
        }
        for (Loop l : loops) {
            if (l.loopContinue == part) {
                return (new ContinueItem(null, l.id));
            }
            if (l.loopBreak == part) {
                return (new BreakItem(null, l.id));
            }
        }
        return null;
    }

    private void checkContinueAtTheEnd(List<GraphTargetItem> commands, Loop loop) {
        if (!commands.isEmpty()) {
            int i = commands.size() - 1;
            for (; i >= 0; i--) {
                if (commands.get(i) instanceof ContinueItem) {
                    continue;
                }
                if (commands.get(i) instanceof BreakItem) {
                    continue;
                }
                break;
            }
            if (i < commands.size() - 1) {
                for (int k = i + 2; k < commands.size(); k++) {
                    commands.remove(k);
                }
            }
            if (commands.get(commands.size() - 1) instanceof ContinueItem) {
                if (((ContinueItem) commands.get(commands.size() - 1)).loopId == loop.id) {
                    commands.remove(commands.size() - 1);
                }
            }
        }
    }

    protected boolean isEmpty(List<GraphTargetItem> output) {
        if (output.isEmpty()) {
            return true;
        }
        if (output.size() == 1) {
            if (output.get(0) instanceof MarkItem) {
                return true;
            }
        }
        return false;
    }

    protected List<GraphTargetItem> check(GraphSource code, BaseLocalData localData, List<GraphPart> allParts, TranslateStack stack, GraphPart parent, GraphPart part, List<GraphPart> stopPart, List<Loop> loops, List<GraphTargetItem> output, Loop currentLoop, int staticOperation, String path) throws InterruptedException {
        return null;
    }

    protected GraphPart checkPart(TranslateStack stack, BaseLocalData localData, GraphPart part, List<GraphPart> allParts) {
        return part;
    }

    //@SuppressWarnings("unchecked")
    protected GraphTargetItem translatePartGetStack(BaseLocalData localData, GraphPart part, TranslateStack stack, int staticOperation) throws InterruptedException {
        stack = (TranslateStack) stack.clone();
        translatePart(localData, part, stack, staticOperation, null);
        return stack.pop();
    }

    protected List<GraphTargetItem> translatePart(BaseLocalData localData, GraphPart part, TranslateStack stack, int staticOperation, String path) throws InterruptedException {
        List<GraphPart> sub = part.getSubParts();
        List<GraphTargetItem> ret = new ArrayList<>();
        int end;
        for (GraphPart p : sub) {
            if (p.end == -1) {
                p.end = code.size() - 1;
            }
            if (p.start == code.size()) {
                continue;
            } else if (p.end == code.size()) {
                p.end--;
            }
            end = p.end;
            int start = p.start;
            ret.addAll(code.translatePart(part, localData, stack, start, end, staticOperation, path));
        }
        return ret;
    }

    private void markBranchEnd(List<GraphTargetItem> items) {
        if (!items.isEmpty()) {
            if (items.get(items.size() - 1) instanceof BreakItem) {
                return;
            }
            if (items.get(items.size() - 1) instanceof ContinueItem) {
                return;
            }
            if (items.get(items.size() - 1) instanceof ExitItem) {
                return;
            }
        }
        items.add(new MarkItem("finish"));
    }

    private static GraphTargetItem getLastNoEnd(List<GraphTargetItem> list) {
        if (list.isEmpty()) {
            return null;
        }
        if (list.get(list.size() - 1) instanceof ScriptEndItem) {
            if (list.size() >= 2) {
                return list.get(list.size() - 2);
            }
            return list.get(list.size() - 1);
        }
        return list.get(list.size() - 1);
    }

    private static void removeLastNoEnd(List<GraphTargetItem> list) {
        if (list.isEmpty()) {
            return;
        }
        if (list.get(list.size() - 1) instanceof ScriptEndItem) {
            if (list.size() >= 2) {
                list.remove(list.size() - 2);
            }
            return;
        }
        list.remove(list.size() - 1);
    }

    protected List<GraphTargetItem> printGraph(BaseLocalData localData, TranslateStack stack, List<GraphPart> allParts, GraphPart parent, GraphPart part, List<GraphPart> stopPart, List<Loop> loops, int staticOperation, String path) throws InterruptedException {
        List<GraphPart> visited = new ArrayList<>();
        return printGraph(visited, localData, stack, allParts, parent, part, stopPart, loops, null, staticOperation, path, 0);
    }

    protected GraphTargetItem checkLoop(LoopItem loopItem, BaseLocalData localData, List<Loop> loops) {
        return loopItem;
    }

    private void getPrecontinues(String path, BaseLocalData localData, GraphPart parent, GraphPart part, List<GraphPart> allParts, List<Loop> loops, List<GraphPart> stopPart) throws InterruptedException {
        markLevels(path, localData, part, allParts, loops);
        //Note: this also marks part as precontinue when there is if
        /*
         while(k<10){
         if(k==7){
         trace(a);
         }else{
         trace(b);
         }
         //precontinue
         k++;
         }

         */
        looploops:
        for (Loop l : loops) {
            if (l.loopContinue != null) {
                Set<GraphPart> uniqueRefs = new HashSet<>();
                uniqueRefs.addAll(l.loopContinue.refs);
                if (uniqueRefs.size() == 2) { //only one path - from precontinue
                    List<GraphPart> uniqueRefsList = new ArrayList<>(uniqueRefs);
                    if (uniqueRefsList.get(0).discoveredTime > uniqueRefsList.get(1).discoveredTime) { //latch node is discovered later
                        part = uniqueRefsList.get(0);
                    } else {
                        part = uniqueRefsList.get(1);
                    }
                    if (part == l.loopContinue) {
                        continue looploops;
                    }

                    while (part.refs.size() == 1) {
                        if (part.refs.get(0).nextParts.size() != 1) {
                            continue looploops;
                        }

                        part = part.refs.get(0);
                        if (part == l.loopContinue) {
                            break;
                        }
                    }
                    if (part.level == 0 && part != l.loopContinue) {
                        l.loopPreContinue = part;
                    }
                }
            }
        }
        /*clearLoops(loops);
         getPrecontinues(parent, part, loops, stopPart, 0, new ArrayList<GraphPart>());
         clearLoops(loops);*/
    }

    private void markLevels(String path, BaseLocalData localData, GraphPart part, List<GraphPart> allParts, List<Loop> loops) throws InterruptedException {
        clearLoops(loops);
        markLevels(path, localData, part, allParts, loops, new ArrayList<GraphPart>(), 1, new ArrayList<GraphPart>(), 0);
        clearLoops(loops);
    }

    private void markLevels(String path, BaseLocalData localData, GraphPart part, List<GraphPart> allParts, List<Loop> loops, List<GraphPart> stopPart, int level, List<GraphPart> visited, int recursionLevel) throws InterruptedException {
        boolean debugMode = false;
        if (stopPart == null) {
            stopPart = new ArrayList<>();
        }
        if (recursionLevel > allParts.size() + 1) {
            Logger.getLogger(Graph.class.getName()).log(Level.WARNING, "{0} : markLevels max recursion level reached", path);
            return;
        }

        if (debugMode) {
            System.err.println("markLevels " + part);
        }
        if (stopPart.contains(part)) {
            return;
        }
        for (Loop el : loops) {
            if ((el.phase == 2) && (el.loopContinue == part)) {
                return;
            }
            if (el.phase != 1) {
                if (debugMode) {
                    //System.err.println("ignoring "+el);
                }
                continue;
            }
            if (el.loopContinue == part) {
                return;
            }
            if (el.loopPreContinue == part) {
                return;
            }
            if (el.loopBreak == part) {
                return;
            }
        }

        if (visited.contains(part)) {
            part.level = 0;
        } else {
            visited.add(part);
            part.level = level;
        }

        boolean isLoop = false;
        Loop currentLoop = null;
        for (Loop el : loops) {
            if ((el.phase == 0) && (el.loopContinue == part)) {
                isLoop = true;
                currentLoop = el;
                el.phase = 1;
                break;
            }
        }

        List<GraphPart> nextParts = checkPrecoNextParts(part);
        if (nextParts == null) {
            nextParts = part.nextParts;
        }

        if (nextParts.size() == 2) {
            GraphPart next = getCommonPart(localData, nextParts, loops);//part.getNextPartPath(new ArrayList<GraphPart>());
            List<GraphPart> stopParts2 = new ArrayList<>();  //stopPart);
            if (next != null) {
                stopParts2.add(next);
            } else if (!stopPart.isEmpty()) {
                stopParts2.add(stopPart.get(stopPart.size() - 1));
            }
            if (next != nextParts.get(0)) {
                markLevels(path, localData, nextParts.get(0), allParts, loops, next == null ? stopPart : stopParts2, level + 1, visited, recursionLevel + 1);
            }
            if (next != nextParts.get(1)) {
                markLevels(path, localData, nextParts.get(1), allParts, loops, next == null ? stopPart : stopParts2, level + 1, visited, recursionLevel + 1);
            }
            if (next != null) {
                markLevels(path, localData, next, allParts, loops, stopPart, level, visited, recursionLevel + 1);
            }
        }

        if (nextParts.size() > 2) {
            GraphPart next = getMostCommonPart(localData, nextParts, loops);
            List<GraphPart> vis = new ArrayList<>();
            for (GraphPart p : nextParts) {
                if (vis.contains(p)) {
                    continue;
                }
                List<GraphPart> stopPart2 = new ArrayList<>(); //(stopPart);
                if (next != null) {
                    stopPart2.add(next);
                } else if (!stopPart.isEmpty()) {
                    stopPart2.add(stopPart.get(stopPart.size() - 1));
                }
                for (GraphPart p2 : nextParts) {
                    if (p2 == p) {
                        continue;
                    }
                    if (!stopPart2.contains(p2)) {
                        stopPart2.add(p2);
                    }
                }
                if (next != p) {
                    markLevels(path, localData, p, allParts, loops, stopPart2, level + 1, visited, recursionLevel + 1);
                    vis.add(p);
                }
            }
            if (next != null) {
                markLevels(path, localData, next, allParts, loops, stopPart, level, visited, recursionLevel + 1);
            }
        }

        if (nextParts.size() == 1) {
            markLevels(path, localData, nextParts.get(0), allParts, loops, stopPart, level, visited, recursionLevel + 1);
        }

        for (GraphPart t : part.throwParts) {
            if (!visited.contains(t)) {
                List<GraphPart> stopPart2 = new ArrayList<>();
                List<GraphPart> cmn = new ArrayList<>();
                cmn.add(part);
                cmn.add(t);
                GraphPart next = getCommonPart(localData, cmn, loops);
                if (next != null) {
                    stopPart2.add(next);
                } else {
                    stopPart2 = stopPart;
                }

                markLevels(path, localData, t, allParts, loops, stopPart2, level, visited, recursionLevel + 1);
            }
        }

        if (isLoop) {
            if (currentLoop.loopBreak != null) {
                currentLoop.phase = 2;
                markLevels(path, localData, currentLoop.loopBreak, allParts, loops, stopPart, level, visited, recursionLevel + 1);
            }
        }
    }

    private void clearLoops(List<Loop> loops) {
        for (Loop l : loops) {
            l.phase = 0;
        }
    }

    private void getLoops(BaseLocalData localData, GraphPart part, List<Loop> loops, List<GraphPart> stopPart) throws InterruptedException {
        clearLoops(loops);
        getLoops(localData, part, loops, stopPart, true, 1, new ArrayList<GraphPart>());
        clearLoops(loops);
    }

    private void getLoops(BaseLocalData localData, GraphPart part, List<Loop> loops, List<GraphPart> stopPart, boolean first, int level, List<GraphPart> visited) throws InterruptedException {
        boolean debugMode = false;

        if (stopPart == null) {
            stopPart = new ArrayList<>();
        }
        if (part == null) {
            return;
        }

        part = checkPart(null, localData, part, null);
        if (part == null) {
            return;
        }
        if (!visited.contains(part)) {
            visited.add(part);
        }

        if (debugMode) {
            System.err.println("getloops: " + part);
        }
        List<GraphPart> loopContinues = getLoopsContinues(loops);
        Loop lastP1 = null;
        for (Loop el : loops) {
            if ((el.phase == 1) && el.loopBreak == null) { //break not found yet
                if (el.loopContinue != part) {
                    lastP1 = el;

                } else {
                    lastP1 = null;
                }

            }
        }
        if (lastP1 != null) {
            if (lastP1.breakCandidates.contains(part)) {
                lastP1.breakCandidates.add(part);
                lastP1.breakCandidatesLevels.add(level);
                return;
            } else {
                List<GraphPart> loopContinues2 = new ArrayList<>(loopContinues);
                loopContinues2.remove(lastP1.loopContinue);
                List<Loop> loops2 = new ArrayList<>(loops);
                loops2.remove(lastP1);
                if (!part.leadsTo(localData, this, code, lastP1.loopContinue, loops2)) {
                    if (lastP1.breakCandidatesLocked == 0) {
                        if (debugMode) {
                            System.err.println("added breakCandidate " + part + " to " + lastP1);
                        }

                        lastP1.breakCandidates.add(part);
                        lastP1.breakCandidatesLevels.add(level);
                        return;
                    }
                }
            }
        }

        for (Loop el : loops) {
            if (el.loopContinue == part) {
                return;
            }
        }

        if (stopPart.contains(part)) {
            return;
        }
        part.level = level;

        boolean isLoop = part.leadsTo(localData, this, code, part, loops);
        Loop currentLoop = null;
        if (isLoop) {
            currentLoop = new Loop(loops.size(), part, null);
            currentLoop.phase = 1;
            loops.add(currentLoop);
            loopContinues.add(part);
        }

        if (part.nextParts.size() == 2) {

            List<GraphPart> nps = new ArrayList<>(part.nextParts);
            /*for(int i=0;i<nps.size();i++){
             nps.set(i,getNextNoJump(nps.get(i),localData));
             }
             if(nps.get(0) == nps.get(1)){
             nps = part.nextParts;
             }*/
            nps = part.nextParts;
            GraphPart next = getCommonPart(localData, nps, loops);//part.getNextPartPath(loopContinues);
            List<GraphPart> stopPart2 = new ArrayList<>(stopPart);
            if (next != null) {
                stopPart2.add(next);
            }
            if (next != nps.get(0)) {
                getLoops(localData, nps.get(0), loops, stopPart2, false, level + 1, visited);
            }
            if (next != nps.get(1)) {
                getLoops(localData, nps.get(1), loops, stopPart2, false, level + 1, visited);
            }
            if (next != null) {
                getLoops(localData, next, loops, stopPart, false, level, visited);
            }
        }
        if (part.nextParts.size() > 2) {
            GraphPart next = getNextCommonPart(localData, part, loops);

            for (GraphPart p : part.nextParts) {
                List<GraphPart> stopPart2 = new ArrayList<>(stopPart);
                if (next != null) {
                    stopPart2.add(next);
                }
                for (GraphPart p2 : part.nextParts) {
                    if (p2 == p) {
                        continue;
                    }
                    if (!stopPart2.contains(p2)) {
                        stopPart2.add(p2);
                    }
                }
                if (next != p) {
                    getLoops(localData, p, loops, stopPart2, false, level + 1, visited);
                }
            }
            if (next != null) {
                getLoops(localData, next, loops, stopPart, false, level, visited);
            }
        }
        if (part.nextParts.size() == 1) {
            getLoops(localData, part.nextParts.get(0), loops, stopPart, false, level, visited);
        }

        List<Loop> loops2 = new ArrayList<>(loops);
        for (Loop l : loops2) {
            l.breakCandidatesLocked++;
        }
        for (GraphPart t : part.throwParts) {
            if (!visited.contains(t)) {
                getLoops(localData, t, loops, stopPart, false, level, visited);
            }
        }
        for (Loop l : loops2) {
            l.breakCandidatesLocked--;
        }

        if (isLoop) {
            GraphPart found;
            Map<GraphPart, Integer> removed = new HashMap<>();
            do {
                found = null;
                for (int i = 0; i < currentLoop.breakCandidates.size(); i++) {
                    GraphPart ch = checkPart(null, localData, currentLoop.breakCandidates.get(i), null);
                    if (ch == null) {
                        currentLoop.breakCandidates.remove(i);
                        i--;
                    }
                }
                loopcand:
                for (GraphPart cand : currentLoop.breakCandidates) {
                    for (GraphPart cand2 : currentLoop.breakCandidates) {
                        if (cand == cand2) {
                            continue;
                        }
                        if (cand.leadsTo(localData, this, code, cand2, loops)) {
                            int lev1 = Integer.MAX_VALUE;
                            int lev2 = Integer.MAX_VALUE;
                            for (int i = 0; i < currentLoop.breakCandidates.size(); i++) {
                                if (currentLoop.breakCandidates.get(i) == cand) {
                                    if (currentLoop.breakCandidatesLevels.get(i) < lev1) {
                                        lev1 = currentLoop.breakCandidatesLevels.get(i);
                                    }
                                }
                                if (currentLoop.breakCandidates.get(i) == cand2) {
                                    if (currentLoop.breakCandidatesLevels.get(i) < lev2) {
                                        lev2 = currentLoop.breakCandidatesLevels.get(i);
                                    }
                                }
                            }
                            if (lev1 <= lev2) {
                                found = cand2;
                            } else {
                                found = cand;
                            }
                            break loopcand;
                        }
                    }
                }
                if (found != null) {
                    int maxlevel = 0;
                    while (currentLoop.breakCandidates.contains(found)) {
                        int ind = currentLoop.breakCandidates.indexOf(found);
                        currentLoop.breakCandidates.remove(ind);
                        int lev = currentLoop.breakCandidatesLevels.remove(ind);
                        if (lev > maxlevel) {
                            maxlevel = lev;
                        }
                    }
                    if (removed.containsKey(found)) {
                        if (removed.get(found) > maxlevel) {
                            maxlevel = removed.get(found);
                        }
                    }
                    removed.put(found, maxlevel);
                }
            } while ((found != null) && (currentLoop.breakCandidates.size() > 1));

            Map<GraphPart, Integer> count = new HashMap<>();
            GraphPart winner = null;
            int winnerCount = 0;
            for (GraphPart cand : currentLoop.breakCandidates) {

                if (!count.containsKey(cand)) {
                    count.put(cand, 0);
                }
                count.put(cand, count.get(cand) + 1);
                boolean otherBreakCandidate = false;
                for (Loop el : loops) {
                    if (el == currentLoop) {
                        continue;
                    }
                    if (el.breakCandidates.contains(cand)) {
                        otherBreakCandidate = true;
                        break;
                    }
                }
                if (otherBreakCandidate) {
                } else if (count.get(cand) > winnerCount) {
                    winnerCount = count.get(cand);
                    winner = cand;
                } else if (count.get(cand) == winnerCount) {
                    if (cand.path.length() < winner.path.length()) {
                        winner = cand;
                    }
                }
            }
            for (int i = 0; i < currentLoop.breakCandidates.size(); i++) {
                GraphPart cand = currentLoop.breakCandidates.get(i);
                if (cand != winner) {
                    int lev = currentLoop.breakCandidatesLevels.get(i);
                    if (removed.containsKey(cand)) {
                        if (removed.get(cand) > lev) {
                            lev = removed.get(cand);
                        }
                    }
                    removed.put(cand, lev);
                }
            }
            currentLoop.loopBreak = winner;
            currentLoop.phase = 2;
            boolean start = false;
            for (int l = 0; l < loops.size(); l++) {
                Loop el = loops.get(l);
                if (start) {
                    el.phase = 1;
                }
                if (el == currentLoop) {
                    start = true;
                }
            }
            List<GraphPart> removedVisited = new ArrayList<>();
            for (GraphPart r : removed.keySet()) {
                if (removedVisited.contains(r)) {
                    continue;
                }
                getLoops(localData, r, loops, stopPart, false, removed.get(r), visited);
                removedVisited.add(r);
            }
            start = false;
            for (int l = 0; l < loops.size(); l++) {
                Loop el = loops.get(l);
                if (el == currentLoop) {
                    start = true;
                }
                if (start) {
                    el.phase = 2;
                }
            }
            getLoops(localData, currentLoop.loopBreak, loops, stopPart, false, level, visited);
        }
    }

    protected List<GraphTargetItem> printGraph(List<GraphPart> visited, BaseLocalData localData, TranslateStack stack, List<GraphPart> allParts, GraphPart parent, GraphPart part, List<GraphPart> stopPart, List<Loop> loops, List<GraphTargetItem> ret, int staticOperation, String path, int recursionLevel) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        if (stopPart == null) {
            stopPart = new ArrayList<>();
        }
        if (recursionLevel > allParts.size() + 1) {
            throw new TranslateException("printGraph max recursion level reached.");
        }
        if (visited.contains(part)) {
            //return new ArrayList<GraphTargetItem>();
        } else {
            visited.add(part);
        }
        if (ret == null) {
            ret = new ArrayList<>();
        }
        //try {
        boolean debugMode = false;

        if (debugMode) {
            System.err.println("PART " + part + " nextsize:" + part.nextParts.size());
        }

        /*while (((part != null) && (part.getHeight() == 1)) && (code.size() > part.start) && (code.get(part.start).isJump())) {  //Parts with only jump in it gets ignored

         if (part == stopPart) {
         return ret;
         }
         GraphTargetItem lop = checkLoop(part.nextParts.get(0), stopPart, loops);
         if (lop == null) {
         part = part.nextParts.get(0);
         } else {
         break;
         }
         }*/
        if (part == null) {
            return ret;
        }
        part = checkPart(stack, localData, part, allParts);
        if (part == null) {
            return ret;
        }

        if (part.ignored) {
            return ret;
        }

        List<GraphPart> loopContinues = getLoopsContinues(loops);
        boolean isLoop = false;
        Loop currentLoop = null;
        for (Loop el : loops) {
            if ((el.loopContinue == part) && (el.phase == 0)) {
                currentLoop = el;
                currentLoop.phase = 1;
                isLoop = true;
                break;
            }
        }

        if (debugMode) {
            System.err.println("loopsize:" + loops.size());
        }
        for (int l = loops.size() - 1; l >= 0; l--) {
            Loop el = loops.get(l);
            if (el == currentLoop) {
                if (debugMode) {
                    System.err.println("ignoring current loop " + el);
                }
                continue;
            }
            if (el.phase != 1) {
                if (debugMode) {
                    //System.err.println("ignoring loop "+el);
                }
                continue;
            }
            if (el.loopBreak == part) {
                if (currentLoop != null) {
                    currentLoop.phase = 0;
                }
                if (debugMode) {
                    System.err.println("Adding break");
                }
                ret.add(new BreakItem(null, el.id));
                return ret;
            }
            if (el.loopPreContinue == part) {
                if (currentLoop != null) {
                    currentLoop.phase = 0;
                }
                if (debugMode) {
                    System.err.println("Adding precontinue");
                }
                ret.add(new ContinueItem(null, el.id));
                return ret;
            }
            if (el.loopContinue == part) {
                if (currentLoop != null) {
                    currentLoop.phase = 0;
                }
                if (debugMode) {
                    System.err.println("Adding continue");
                }
                ret.add(new ContinueItem(null, el.id));
                return ret;
            }
        }

        if (stopPart.contains(part)) {
            if (currentLoop != null) {
                currentLoop.phase = 0;
            }
            switch (part.stopPartType) {
                case AND_OR:
                    part.setAndOrStack(stack); //Save stack for later use
                    break;

                case COMMONPART:
                    part.setCommonPartStack(stack); //Save stack for later use
                    break;

                case NONE:
                    break;
            }
            return ret;
        }

        if ((part != null) && (code.size() <= part.start)) {
            ret.add(new ScriptEndItem());
            return ret;
        }
        List<GraphTargetItem> currentRet = ret;
        UniversalLoopItem loopItem = null;
        if (isLoop) {
            loopItem = new UniversalLoopItem(null, currentLoop);
            //loopItem.commands=printGraph(visited, localData, stack, allParts, parent, part, stopPart, loops);
            currentRet.add(loopItem);
            loopItem.commands = new ArrayList<>();
            currentRet = loopItem.commands;
            //return ret;
        }

        boolean parseNext = true;

        //****************************DECOMPILING PART*************
        List<GraphTargetItem> output = new ArrayList<>();

        List<GraphPart> parts = new ArrayList<>();
        if (part instanceof GraphPartMulti) {
            parts = ((GraphPartMulti) part).parts;
        } else {
            parts.add(part);
        }
        for (GraphPart p : parts) {
            int end = p.end;
            int start = p.start;

            output.addAll(code.translatePart(p, localData, stack, start, end, staticOperation, path));
            if ((end >= code.size() - 1) && p.nextParts.isEmpty()) {
                output.add(new ScriptEndItem());
            }
        }

        //Assuming part with two nextparts is an IF

        /* //If with both branches empty
         if (part.nextParts.size() == 2) {
         if (part.nextParts.get(0) == part.nextParts.get(1)) {
         if (!stack.isEmpty()) {
         GraphTargetItem expr = stack.pop();
         if (expr instanceof LogicalOpItem) {
         expr = ((LogicalOpItem) expr).invert();
         } else {
         expr = new NotItem(null, expr);
         }
         output.add(new IfItem(null, expr, new ArrayList<GraphTargetItem>(), new ArrayList<GraphTargetItem>()));
         }
         part.nextParts.remove(0);
         }
         }*/
        if (parseNext) {
            List<GraphTargetItem> retCheck = check(code, localData, allParts, stack, parent, part, stopPart, loops, output, currentLoop, staticOperation, path);
            if (retCheck != null) {
                if (!retCheck.isEmpty()) {
                    currentRet.addAll(retCheck);
                }
                parseNext = false;
                //return ret;
            } else {
                currentRet.addAll(output);
            }
        }

        /**
         * AND / OR detection
         */
        if (parseNext && part.nextParts.size() == 2) {
            if ((stack.size() >= 2) && (stack.get(stack.size() - 1) instanceof NotItem) && (((NotItem) (stack.get(stack.size() - 1))).getOriginal().getNotCoerced() == stack.get(stack.size() - 2).getNotCoerced())) {
                GraphPart sp0 = getNextNoJump(part.nextParts.get(0), localData);
                GraphPart sp1 = getNextNoJump(part.nextParts.get(1), localData);
                boolean reversed = false;
                loopContinues = getLoopsContinues(loops);
                loopContinues.add(part);//???
                if (sp1.leadsTo(localData, this, code, sp0, loops)) {
                } else if (sp0.leadsTo(localData, this, code, sp1, loops)) {
                    reversed = true;
                }
                GraphPart next = reversed ? sp0 : sp1;
                GraphTargetItem ti;
                if ((ti = checkLoop(next, stopPart, loops)) != null) {
                    currentRet.add(ti);
                } else {
                    List<GraphPart> stopPart2 = new ArrayList<>(stopPart);
                    GraphPart andOrStopPart = reversed ? sp1 : sp0;
                    andOrStopPart.stopPartType = GraphPart.StopPartType.AND_OR;
                    stopPart2.add(andOrStopPart);
                    printGraph(visited, localData, stack, allParts, parent, next, stopPart2, loops, null, staticOperation, path, recursionLevel + 1);
                    stack = andOrStopPart.andOrStack; // Use stack that was stored upon reaching AND_OR stopPart
                    GraphTargetItem second = stack.pop();
                    GraphTargetItem first = stack.pop();
                    andOrStopPart.stopPartType = GraphPart.StopPartType.NONE; // Reset stopPartType

                    if (!reversed) {
                        AndItem a = new AndItem(null, first, second);
                        stack.push(a);
                        a.firstPart = part;
                        if (second instanceof AndItem) {
                            a.firstPart = ((AndItem) second).firstPart;
                        }
                        if (second instanceof OrItem) {
                            a.firstPart = ((OrItem) second).firstPart;
                        }
                    } else {
                        OrItem o = new OrItem(null, first, second);
                        stack.push(o);
                        o.firstPart = part;
                        if (second instanceof AndItem) {
                            o.firstPart = ((AndItem) second).firstPart;
                        }
                        if (second instanceof OrItem) {
                            o.firstPart = ((OrItem) second).firstPart;
                        }
                    }
                    next = reversed ? sp1 : sp0;
                    if ((ti = checkLoop(next, stopPart, loops)) != null) {
                        currentRet.add(ti);
                    } else {
                        currentRet.addAll(printGraph(visited, localData, stack, allParts, parent, next, stopPart, loops, null, staticOperation, path, recursionLevel + 1));
                    }
                }
                parseNext = false;
                //return ret;
            } else if ((stack.size() >= 2) && (stack.get(stack.size() - 1).getNotCoerced() == stack.get(stack.size() - 2).getNotCoerced())) {
                GraphPart sp0 = getNextNoJump(part.nextParts.get(0), localData);
                GraphPart sp1 = getNextNoJump(part.nextParts.get(1), localData);
                boolean reversed = false;
                loopContinues = getLoopsContinues(loops);
                loopContinues.add(part);//???
                if (sp1.leadsTo(localData, this, code, sp0, loops)) {
                } else if (sp0.leadsTo(localData, this, code, sp1, loops)) {
                    reversed = true;
                }
                GraphPart next = reversed ? sp0 : sp1;
                GraphTargetItem ti;
                if ((ti = checkLoop(next, stopPart, loops)) != null) {
                    currentRet.add(ti);
                } else {
                    List<GraphPart> stopPart2 = new ArrayList<>(stopPart);
                    GraphPart andOrStopPart = reversed ? sp1 : sp0;
                    andOrStopPart.stopPartType = GraphPart.StopPartType.AND_OR;
                    stopPart2.add(andOrStopPart);
                    printGraph(visited, localData, stack, allParts, parent, next, stopPart2, loops, null, staticOperation, path, recursionLevel + 1);
                    stack = andOrStopPart.andOrStack; // Use stack that was stored upon reaching AND_OR stopPart
                    GraphTargetItem second = stack.pop();
                    GraphTargetItem first = stack.pop();
                    andOrStopPart.stopPartType = GraphPart.StopPartType.NONE; // Reset stopPartType

                    if (reversed) {
                        AndItem a = new AndItem(null, first, second);
                        stack.push(a);
                        a.firstPart = part;
                        if (second instanceof AndItem) {
                            a.firstPart = ((AndItem) second).firstPart;
                        }
                        if (second instanceof OrItem) {
                            a.firstPart = ((OrItem) second).firstPart;
                        }
                    } else {
                        OrItem o = new OrItem(null, first, second);
                        stack.push(o);
                        o.firstPart = part;
                        if (second instanceof OrItem) {
                            o.firstPart = ((OrItem) second).firstPart;
                        }
                        if (second instanceof AndItem) {
                            o.firstPart = ((AndItem) second).firstPart;
                        }
                    }

                    next = reversed ? sp1 : sp0;
                    if ((ti = checkLoop(next, stopPart, loops)) != null) {
                        currentRet.add(ti);
                    } else {
                        currentRet.addAll(printGraph(visited, localData, stack, allParts, parent, next, stopPart, loops, null, staticOperation, path, recursionLevel + 1));
                    }
                }
                parseNext = false;
                //return ret;
            }
        }
//********************************END PART DECOMPILING

        if (parseNext) {

            if (false && part.nextParts.size() > 2) {//alchemy direct switch
                GraphPart next = getMostCommonPart(localData, part.nextParts, loops);
                List<GraphPart> vis = new ArrayList<>();
                GraphTargetItem switchedItem = stack.pop();
                List<GraphTargetItem> caseValues = new ArrayList<>();
                List<List<GraphTargetItem>> caseCommands = new ArrayList<>();
                List<GraphTargetItem> defaultCommands = new ArrayList<>();
                List<Integer> valueMappings = new ArrayList<>();
                Loop swLoop = new Loop(loops.size(), null, next);
                swLoop.phase = 1;
                loops.add(swLoop);
                boolean first = false;
                int pos = 0;
                for (GraphPart p : part.nextParts) {

                    if (!first) {
                        caseValues.add(new IntegerValueItem(null, pos++));
                        if (vis.contains(p)) {
                            valueMappings.add(caseCommands.size() - 1);
                            continue;
                        }

                        valueMappings.add(caseCommands.size());
                    }
                    List<GraphPart> stopPart2 = new ArrayList<>();
                    if (next != null) {
                        stopPart2.add(next);
                    } else if (!stopPart.isEmpty()) {
                        stopPart2.add(stopPart.get(stopPart.size() - 1));
                    }
                    for (GraphPart p2 : part.nextParts) {
                        if (p2 == p) {
                            continue;
                        }
                        if (!stopPart2.contains(p2)) {
                            stopPart2.add(p2);
                        }
                    }
                    if (next != p) {
                        if (first) {
                            defaultCommands = printGraph(visited, prepareBranchLocalData(localData), stack, allParts, part, p, stopPart2, loops, null, staticOperation, path, recursionLevel + 1);
                        } else {
                            caseCommands.add(printGraph(visited, prepareBranchLocalData(localData), stack, allParts, part, p, stopPart2, loops, null, staticOperation, path, recursionLevel + 1));
                        }
                        vis.add(p);
                    }
                    first = false;
                }
                SwitchItem sw = new SwitchItem(null, swLoop, switchedItem, caseValues, caseCommands, defaultCommands, valueMappings);
                currentRet.add(sw);
                swLoop.phase = 2;
                if (next != null) {
                    currentRet.addAll(printGraph(visited, localData, stack, allParts, part, next, stopPart, loops, null, staticOperation, path, recursionLevel + 1));
                }
            } //else
            GraphPart nextOnePart = null;
            if (part.nextParts.size() == 2) {
                GraphTargetItem expr = stack.pop();
                if (expr instanceof LogicalOpItem) {
                    expr = ((LogicalOpItem) expr).invert();
                } else {
                    expr = new NotItem(null, expr);
                }
                if (staticOperation != SOP_USE_STATIC) {
                    if (expr.isCompileTime()) {
                        boolean doJump = EcmaScript.toBoolean(expr.getResult());
                        if (doJump) {
                            nextOnePart = part.nextParts.get(0);
                        } else {
                            nextOnePart = part.nextParts.get(1);
                        }
                        if (staticOperation == SOP_REMOVE_STATIC) {
                            //TODO
                        }
                    }
                }
                if (nextOnePart == null) {

                    List<GraphPart> nps;
                    /*nps = new ArrayList<>(part.nextParts);
                     for(int i=0;i<nps.size();i++){
                     nps.set(i,getNextNoJump(nps.get(i),localData));
                     }
                     if(nps.get(0) == nps.get(1)){
                     nps = part.nextParts;
                     }*/
                    nps = part.nextParts;
                    GraphPart next = getCommonPart(localData, nps, loops);

                    TranslateStack trueStack = (TranslateStack) stack.clone();
                    TranslateStack falseStack = (TranslateStack) stack.clone();
                    int trueStackSizeBefore = trueStack.size();
                    int falseStackSizeBefore = falseStack.size();
                    List<GraphTargetItem> onTrue = new ArrayList<>();
                    boolean isEmpty = nps.get(0) == nps.get(1);

                    if (isEmpty) {
                        next = nps.get(0);
                    }

                    List<GraphPart> stopPart2 = new ArrayList<>(stopPart);
                    GraphPart.CommonPartStack commonPartStack = null;
                    if ((!isEmpty) && (next != null)) {
                        commonPartStack = next.new CommonPartStack();
                        if (next.commonPartStacks == null) {
                            next.commonPartStacks = new ArrayList<>();
                        }
                        next.stopPartType = GraphPart.StopPartType.COMMONPART;
                        stopPart2.add(next);
                    }
                    if (!isEmpty) {
                        if (next != null) {
                            next.commonPartStacks.add(commonPartStack);
                            commonPartStack.isTrueStack = true; //stopPart must know it needs to store trueStack
                        }
                        onTrue = printGraph(visited, prepareBranchLocalData(localData), trueStack, allParts, part, nps.get(1), stopPart2, loops, null, staticOperation, path, recursionLevel + 1);
                    }
                    List<GraphTargetItem> onFalse = new ArrayList<>();

                    if (!isEmpty) {
                        if (next != null) {
                            commonPartStack.isTrueStack = false; //stopPart must know it needs to store falseStack
                        }
                        onFalse = printGraph(visited, prepareBranchLocalData(localData), falseStack, allParts, part, nps.get(0), stopPart2, loops, null, staticOperation, path, recursionLevel + 1);
                    }

                    /* if there is a stopPart (next), then Graph will be further analyzed starting from the stopPart:
                     * trueStack and falseStack must be set equal to corresponding stack that was built upon reaching stopPart. */
                    if ((!isEmpty) && (next != null)) {
                        if ((commonPartStack.trueStack != null) && (commonPartStack.falseStack != null)) {
                            trueStack = commonPartStack.trueStack;
                            falseStack = commonPartStack.falseStack;
                        }
                        next.commonPartStacks.remove(next.commonPartStacks.size() - 1);
                        if (next.commonPartStacks.isEmpty()) {
                            next.stopPartType = GraphPart.StopPartType.NONE; // reset StopPartType
                        }
                    }

                    if (isEmpty(onTrue) && isEmpty(onFalse) && (trueStack.size() == trueStackSizeBefore + 1) && (falseStack.size() == falseStackSizeBefore + 1)) {
                        stack.push(new TernarOpItem(null, expr, trueStack.pop(), falseStack.pop()));
                    } else {
                        currentRet.add(new IfItem(null, expr, onTrue, onFalse));
                    }
                    if (next != null) {
                        if (trueStack.size() != trueStackSizeBefore || falseStack.size() != falseStackSizeBefore) {
                            // it's a hack, because duplicates all instructions in the next part, but better than EmptyStackException
                            onTrue = printGraph(visited, localData, trueStack, allParts, part, next, stopPart, loops, null, staticOperation, path, recursionLevel + 1);
                            onFalse = printGraph(visited, localData, falseStack, allParts, part, next, stopPart, loops, null, staticOperation, path, recursionLevel + 1);
                            if (isEmpty(onTrue) && isEmpty(onFalse) && (trueStack.size() == trueStackSizeBefore + 1) && (falseStack.size() == falseStackSizeBefore + 1)) {
                                stack.push(new TernarOpItem(null, expr, trueStack.pop(), falseStack.pop()));
                            } else {
                                currentRet.add(new IfItem(null, expr, onTrue, onFalse));
                            }
                        } else {
                            printGraph(visited, localData, stack, allParts, part, next, stopPart, loops, currentRet, staticOperation, path, recursionLevel + 1);
                        }
                        //currentRet.addAll();
                    }
                }
            }  //else
            if (part.nextParts.size() == 1) {
                nextOnePart = part.nextParts.get(0);
            }
            if (nextOnePart != null) {
                printGraph(visited, localData, stack, allParts, part, part.nextParts.get(0), stopPart, loops, currentRet, staticOperation, path, recursionLevel + 1);
            }

        }
        if (isLoop) {

            LoopItem li = loopItem;
            boolean loopTypeFound = false;

            boolean hasContinue = false;
            processIfs(loopItem.commands);
            checkContinueAtTheEnd(loopItem.commands, currentLoop);
            List<ContinueItem> continues = loopItem.getContinues();
            for (ContinueItem c : continues) {
                if (c.loopId == currentLoop.id) {
                    hasContinue = true;
                    break;
                }
            }
            if (!hasContinue) {
                if (currentLoop.loopPreContinue != null) {
                    List<GraphPart> stopContPart = new ArrayList<>();
                    stopContPart.add(currentLoop.loopContinue);
                    GraphPart precoBackup = currentLoop.loopPreContinue;
                    currentLoop.loopPreContinue = null;
                    loopItem.commands.addAll(printGraph(visited, localData, new TranslateStack(), allParts, null, precoBackup, stopContPart, loops, null, staticOperation, path, recursionLevel + 1));
                }
            }

            //Loop with condition at the beginning (While)
            if (!loopTypeFound && (!loopItem.commands.isEmpty())) {
                if (loopItem.commands.get(0) instanceof IfItem) {
                    IfItem ifi = (IfItem) loopItem.commands.get(0);

                    List<GraphTargetItem> bodyBranch = null;
                    boolean inverted = false;
                    boolean breakpos2 = false;
                    if ((ifi.onTrue.size() == 1) && (ifi.onTrue.get(0) instanceof BreakItem)) {
                        BreakItem bi = (BreakItem) ifi.onTrue.get(0);
                        if (bi.loopId == currentLoop.id) {
                            bodyBranch = ifi.onFalse;
                            inverted = true;
                        }
                    } else if ((ifi.onFalse.size() == 1) && (ifi.onFalse.get(0) instanceof BreakItem)) {
                        BreakItem bi = (BreakItem) ifi.onFalse.get(0);
                        if (bi.loopId == currentLoop.id) {
                            bodyBranch = ifi.onTrue;
                        }
                    } else if (loopItem.commands.size() == 2 && (loopItem.commands.get(1) instanceof BreakItem)) {
                        BreakItem bi = (BreakItem) loopItem.commands.get(1);
                        if (bi.loopId == currentLoop.id) {
                            bodyBranch = ifi.onTrue;
                            breakpos2 = true;
                        }
                    }
                    if (bodyBranch != null) {
                        int index = ret.indexOf(loopItem);
                        ret.remove(index);
                        List<GraphTargetItem> exprList = new ArrayList<>();
                        GraphTargetItem expr = ifi.expression;
                        if (inverted) {
                            if (expr instanceof LogicalOpItem) {
                                expr = ((LogicalOpItem) expr).invert();
                            } else {
                                expr = new NotItem(null, expr);
                            }
                        }
                        exprList.add(expr);
                        List<GraphTargetItem> commands = new ArrayList<>();
                        commands.addAll(bodyBranch);
                        loopItem.commands.remove(0);
                        if (breakpos2) {
                            loopItem.commands.remove(0); //remove that break too
                        }
                        commands.addAll(loopItem.commands);
                        checkContinueAtTheEnd(commands, currentLoop);
                        List<GraphTargetItem> finalComm = new ArrayList<>();
                        if (currentLoop.loopPreContinue != null) {
                            GraphPart backup = currentLoop.loopPreContinue;
                            currentLoop.loopPreContinue = null;
                            List<GraphPart> stopPart2 = new ArrayList<>(stopPart);
                            stopPart2.add(currentLoop.loopContinue);
                            finalComm = printGraph(visited, localData, new TranslateStack(), allParts, null, backup, stopPart2, loops, null, staticOperation, path, recursionLevel + 1);
                            currentLoop.loopPreContinue = backup;
                            checkContinueAtTheEnd(finalComm, currentLoop);
                        }
                        if (!finalComm.isEmpty()) {
                            ret.add(index, li = new ForItem(expr.src, currentLoop, new ArrayList<GraphTargetItem>(), exprList.get(exprList.size() - 1), finalComm, commands));
                        } else {
                            ret.add(index, li = new WhileItem(expr.src, currentLoop, exprList, commands));
                        }

                        loopTypeFound = true;
                    }
                }
            }

            //Loop with condition at the end (Do..While)
            if (!loopTypeFound && (!loopItem.commands.isEmpty())) {
                if (loopItem.commands.get(loopItem.commands.size() - 1) instanceof IfItem) {
                    IfItem ifi = (IfItem) loopItem.commands.get(loopItem.commands.size() - 1);
                    List<GraphTargetItem> bodyBranch = null;
                    boolean inverted = false;
                    if ((ifi.onTrue.size() == 1) && (ifi.onTrue.get(0) instanceof BreakItem)) {
                        BreakItem bi = (BreakItem) ifi.onTrue.get(0);
                        if (bi.loopId == currentLoop.id) {
                            bodyBranch = ifi.onFalse;
                            inverted = true;
                        }
                    } else if ((ifi.onFalse.size() == 1) && (ifi.onFalse.get(0) instanceof BreakItem)) {
                        BreakItem bi = (BreakItem) ifi.onFalse.get(0);
                        if (bi.loopId == currentLoop.id) {
                            bodyBranch = ifi.onTrue;
                        }
                    }
                    if (bodyBranch != null) {
                        //Condition at the beginning
                        int index = ret.indexOf(loopItem);
                        ret.remove(index);
                        List<GraphTargetItem> exprList = new ArrayList<>();
                        GraphTargetItem expr = ifi.expression;
                        if (inverted) {
                            if (expr instanceof LogicalOpItem) {
                                expr = ((LogicalOpItem) expr).invert();
                            } else {
                                expr = new NotItem(null, expr);
                            }
                        }

                        checkContinueAtTheEnd(bodyBranch, currentLoop);

                        List<GraphTargetItem> commands = new ArrayList<>();

                        if (!bodyBranch.isEmpty()) {
                            ret.add(index, loopItem);
                            /*
                             loopItem.commands.remove(loopItem.commands.size() - 1);
                             exprList.addAll(loopItem.commands);
                             commands.addAll(bodyBranch);
                             exprList.add(expr);
                             checkContinueAtTheEnd(commands, currentLoop);
                             ret.add(index, li = new WhileItem(null, currentLoop, exprList, commands));*/
                        } else {
                            loopItem.commands.remove(loopItem.commands.size() - 1);
                            commands.addAll(loopItem.commands);
                            commands.addAll(bodyBranch);
                            exprList.add(expr);
                            checkContinueAtTheEnd(commands, currentLoop);
                            ret.add(index, li = new DoWhileItem(null, currentLoop, commands, exprList));

                        }
                        loopTypeFound = true;
                    }
                }
            }

            if (!loopTypeFound) {
                if (currentLoop.loopPreContinue != null) {
                    loopTypeFound = true;
                    GraphPart backup = currentLoop.loopPreContinue;
                    currentLoop.loopPreContinue = null;
                    List<GraphPart> stopPart2 = new ArrayList<>(stopPart);
                    stopPart2.add(currentLoop.loopContinue);
                    List<GraphTargetItem> finalComm = printGraph(visited, localData, new TranslateStack(), allParts, null, backup, stopPart2, loops, null, staticOperation, path, recursionLevel + 1);
                    currentLoop.loopPreContinue = backup;
                    checkContinueAtTheEnd(finalComm, currentLoop);

                    if (!finalComm.isEmpty()) {
                        if (finalComm.get(finalComm.size() - 1) instanceof IfItem) {
                            IfItem ifi = (IfItem) finalComm.get(finalComm.size() - 1);
                            boolean ok = false;
                            boolean invert = false;
                            if (((ifi.onTrue.size() == 1) && (ifi.onTrue.get(0) instanceof BreakItem) && (((BreakItem) ifi.onTrue.get(0)).loopId == currentLoop.id))
                                    && ((ifi.onTrue.size() == 1) && (ifi.onFalse.get(0) instanceof ContinueItem) && (((ContinueItem) ifi.onFalse.get(0)).loopId == currentLoop.id))) {
                                ok = true;
                                invert = true;
                            }
                            if (((ifi.onTrue.size() == 1) && (ifi.onTrue.get(0) instanceof ContinueItem) && (((ContinueItem) ifi.onTrue.get(0)).loopId == currentLoop.id))
                                    && ((ifi.onTrue.size() == 1) && (ifi.onFalse.get(0) instanceof BreakItem) && (((BreakItem) ifi.onFalse.get(0)).loopId == currentLoop.id))) {
                                ok = true;
                            }
                            if (ok) {
                                finalComm.remove(finalComm.size() - 1);
                                int index = ret.indexOf(loopItem);
                                ret.remove(index);
                                List<GraphTargetItem> exprList = new ArrayList<>(finalComm);
                                GraphTargetItem expr = ifi.expression;
                                if (invert) {
                                    if (expr instanceof LogicalOpItem) {
                                        expr = ((LogicalOpItem) expr).invert();
                                    } else {
                                        expr = new NotItem(null, expr);
                                    }
                                }
                                exprList.add(expr);
                                ret.add(index, li = new DoWhileItem(null, currentLoop, loopItem.commands, exprList));
                            }
                        }
                    }
                }
            }

            if (!loopTypeFound) {
                checkContinueAtTheEnd(loopItem.commands, currentLoop);
            }
            currentLoop.phase = 2;

            GraphTargetItem replaced = checkLoop(li, localData, loops);
            if (replaced != li) {
                int index = ret.indexOf(li);
                ret.remove(index);
                if (replaced != null) {
                    ret.add(index, replaced);
                }
            }

            if (currentLoop.loopBreak != null) {
                ret.addAll(printGraph(visited, localData, stack, allParts, part, currentLoop.loopBreak, stopPart, loops, null, staticOperation, path, recursionLevel + 1));
            }
        }

        return ret;

    }

    protected void checkGraph(List<GraphPart> allBlocks) {
    }

    private List<GraphPart> makeGraph(GraphSource code, List<GraphPart> allBlocks, List<Integer> alternateEntries) throws InterruptedException {
        HashMap<Integer, List<Integer>> refs = code.visitCode(alternateEntries);
        List<GraphPart> ret = new ArrayList<>();
        boolean[] visited = new boolean[code.size()];
        ret.add(makeGraph(null, new GraphPath(), code, 0, 0, allBlocks, refs, visited));
        for (int pos : alternateEntries) {
            GraphPart e1 = new GraphPart(-1, -1);
            e1.path = new GraphPath("e");
            ret.add(makeGraph(e1, new GraphPath("e"), code, pos, pos, allBlocks, refs, visited));
        }
        checkGraph(allBlocks);
        return ret;
    }

    protected int checkIp(int ip) {
        return ip;
    }

    private GraphPart makeGraph(GraphPart parent, GraphPath path, GraphSource code, int startip, int lastIp, List<GraphPart> allBlocks, HashMap<Integer, List<Integer>> refs, boolean[] visited2) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        int ip = startip;
        for (GraphPart p : allBlocks) {
            if (p.start == ip) {
                p.refs.add(parent);
                return p;
            }
        }
        GraphPart g;
        GraphPart ret = new GraphPart(ip, -1);
        ret.path = path;
        GraphPart part = ret;
        while (ip < code.size()) {
            if (visited2[ip] || ((ip != startip) && (refs.get(ip).size() > 1))) {
                part.end = lastIp;
                GraphPart found = null;
                for (GraphPart p : allBlocks) {
                    if (p.start == ip) {
                        found = p;
                        break;
                    }
                }

                allBlocks.add(part);

                if (found != null) {
                    part.nextParts.add(found);
                    found.refs.add(part);
                    break;
                } else {
                    GraphPart gp = new GraphPart(ip, -1);
                    gp.path = path;
                    part.nextParts.add(gp);
                    gp.refs.add(part);
                    part = gp;
                }
            }

            ip = checkIp(ip);
            lastIp = ip;
            GraphSourceItem ins = code.get(ip);
            if (ins.isIgnored()) {
                ip++;
                continue;
            }
            if (ins instanceof GraphSourceItemContainer) {
                GraphSourceItemContainer cnt = (GraphSourceItemContainer) ins;
                if (ins instanceof Action) { //TODO: Remove dependency of AVM1
                    long endAddr = ((Action) ins).getAddress() + cnt.getHeaderSize();
                    for (long size : cnt.getContainerSizes()) {
                        endAddr += size;
                    }
                    ip = code.adr2pos(endAddr);
                }
                continue;
            } else if (ins.isExit()) {
                part.end = ip;
                allBlocks.add(part);
                break;
            } else if (ins.isJump()) {
                part.end = ip;
                allBlocks.add(part);
                ip = ins.getBranches(code).get(0);
                part.nextParts.add(g = makeGraph(part, path, code, ip, lastIp, allBlocks, refs, visited2));
                g.refs.add(part);
                break;
            } else if (ins.isBranch()) {
                part.end = ip;

                allBlocks.add(part);
                List<Integer> branches = ins.getBranches(code);
                for (int i = 0; i < branches.size(); i++) {
                    part.nextParts.add(g = makeGraph(part, path.sub(i, ip), code, branches.get(i), ip, allBlocks, refs, visited2));
                    g.refs.add(part);
                }
                break;
            }
            ip++;
        }
        if ((part.end == -1) && (ip >= code.size())) {
            if (part.start == code.size()) {
                part.end = code.size();
                allBlocks.add(part);
            } else {
                part.end = ip - 1;
                for (GraphPart p : allBlocks) {
                    if (p.start == ip) {
                        p.refs.add(part);
                        part.nextParts.add(p);
                        allBlocks.add(part);
                        return ret;
                    }
                }
                GraphPart gp = new GraphPart(ip, ip);
                allBlocks.add(gp);
                gp.refs.add(part);
                part.nextParts.add(gp);
                allBlocks.add(part);
            }
        }
        return ret;
    }

    /**
     * String used to indent line when converting to string
     */
    public static final String INDENTOPEN = "INDENTOPEN";

    /**
     * String used to unindent line when converting to string
     */
    public static final String INDENTCLOSE = "INDENTCLOSE";

    /**
     * Converts list of TreeItems to string
     *
     * @param tree List of TreeItem
     * @param writer
     * @param localData
     * @return String
     * @throws java.lang.InterruptedException
     */
    public static GraphTextWriter graphToString(List<GraphTargetItem> tree, GraphTextWriter writer, LocalData localData) throws InterruptedException {
        for (GraphTargetItem ti : tree) {
            if (!ti.isEmpty()) {
                ti.toStringSemicoloned(writer, localData).newLine();
            }
        }
        return writer;
    }

    public BaseLocalData prepareBranchLocalData(BaseLocalData localData) {
        return localData;
    }

    protected List<GraphPart> checkPrecoNextParts(GraphPart part) {
        return null;
    }

    protected GraphPart makeMultiPart(GraphPart part) {
        List<GraphPart> parts = new ArrayList<>();
        do {
            parts.add(part);
            if (part.nextParts.size() == 1 && part.nextParts.get(0).refs.size() == 1) {
                part = part.nextParts.get(0);
            } else {
                part = null;
            }
        } while (part != null);
        if (parts.size() > 1) {
            GraphPartMulti ret = new GraphPartMulti(parts);
            ret.refs.addAll(parts.get(0).refs);
            ret.nextParts.addAll(parts.get(parts.size() - 1).nextParts);
            return ret;
        } else {
            return parts.get(0);
        }
    }

    protected List<GraphSourceItem> getPartItems(GraphPart part) {
        List<GraphSourceItem> ret = new ArrayList<>();
        do {
            for (int i = 0; i < part.getHeight(); i++) {
                if (part.getPosAt(i) < code.size()) {
                    if (part.getPosAt(i) < 0) {
                        continue;
                    }
                    GraphSourceItem s = code.get(part.getPosAt(i));
                    if (!s.isJump()) {
                        ret.add(s);
                    }
                }
            }
            if (part.nextParts.size() == 1 && part.nextParts.get(0).refs.size() == 1) {
                part = part.nextParts.get(0);
            } else {
                part = null;
            }
        } while (part != null);
        return ret;
    }
}
