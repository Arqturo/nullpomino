/*
    Copyright (c) 2010, NullNoname
    All rights reserved.

    Redistribution and use in source and binary forms, with or without
    modification, are permitted provided that the following conditions are met:

        * Redistributions of source code must retain the above copyright
          notice, this list of conditions and the following disclaimer.
        * Redistributions in binary form must reproduce the above copyright
          notice, this list of conditions and the following disclaimer in the
          documentation and/or other materials provided with the distribution.
        * Neither the name of NullNoname nor the names of its
          contributors may be used to endorse or promote products derived from
          this software without specific prior written permission.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
    AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
    IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
    ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
    LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
    CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
    SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
    INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
    CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
    ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
    POSSIBILITY OF SUCH DAMAGE.
*/

package mu.nu.nullpo.game.subsystem.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import mu.nu.nullpo.game.component.Controller;
import mu.nu.nullpo.game.component.Field;
import mu.nu.nullpo.game.component.Piece;
import mu.nu.nullpo.game.component.WallkickResult;
import mu.nu.nullpo.game.play.GameEngine;
import mu.nu.nullpo.game.play.GameManager;

import org.apache.log4j.Logger;

/**
 * CommonAI
 */
public class MCTSD extends BasicAI implements Runnable {
    /** Log */
    static Logger log = Logger.getLogger(MCTSD.class);

    /*
     * AIOfName
     */
    @Override
    public String getName() {
        return "MCTSD";
    }

    /**
     * Search for the best choice
     * 
     * @param engine   The GameEngine that owns this AI
     * @param playerID Player ID
     */
    /* THINK BEST MOVE */

    public void thinkBestPosition(GameEngine engine, int playerID) {
        bestHold = false;
        bestX = 0;
        bestY = 0;
        bestRt = 0;
        bestXSub = 0;
        bestYSub = 0;
        bestRtSub = -1;
        bestPts = 0;
        forceHold = false;

        Piece pieceNow = engine.nowPieceObject;
        int nowX = engine.nowPieceX;
        int nowY = engine.nowPieceY;
        boolean holdOK = engine.isHoldOK();
        boolean holdEmpty = false;
        Piece pieceHold = engine.holdPieceObject;
        Piece pieceNext = engine.getNextObject(engine.nextPieceCount);
        if (pieceHold == null) {
            holdEmpty = true;
        }
        Field fld = new Field(engine.field);

        for (int depth = 0; depth < getMaxThinkDepth(); depth++) {
            for (int rt = 0; rt < Piece.DIRECTION_COUNT; rt++) {
                // Peace for now
                int minX = pieceNow.getMostMovableLeft(nowX, nowY, rt, engine.field);
                int maxX = pieceNow.getMostMovableRight(nowX, nowY, rt, engine.field);

                int options = MinNumberOfOptions();
                int discard = MaxDiscardNums();
                List<Integer> xValues = new ArrayList<>();
                for (int x = minX; x <= maxX; x++) {
                    xValues.add(x);
                }

                Collections.shuffle(xValues);

                // elements
                while (xValues.size() > options) {
                    for (int i = 0; i < discard && !xValues.isEmpty(); i++) {
                        int x = xValues.remove(0); // Take the first element in the shuffled list
                    }

                    // Shuffle again to ensure randomness in subsequent iterations
                    Collections.shuffle(xValues);
                }

                for (int x : xValues) {
                    fld.copy(engine.field);
                    int y = pieceNow.getBottom(x, nowY, rt, fld);

                    if (!pieceNow.checkCollision(x, y, rt, fld)) {
                        // As it is
                        int pts = thinkMain(engine, x, y, rt, -1, fld, pieceNow, pieceNext, pieceHold, depth);

                        if (pts >= bestPts) {
                            bestHold = false;
                            bestX = x;
                            bestY = y;
                            bestRt = rt;
                            bestXSub = x;
                            bestYSub = y;
                            bestRtSub = -1;
                            bestPts = pts;
                        }

                        if ((depth > 0) || (bestPts <= 10) || (pieceNow.id == Piece.PIECE_T)) {
                            // Left shift
                            fld.copy(engine.field);
                            if (!pieceNow.checkCollision(x - 1, y, rt, fld)
                                    && pieceNow.checkCollision(x - 1, y - 1, rt, fld)) {
                                pts = thinkMain(engine, x - 1, y, rt, -1, fld, pieceNow, pieceNext, pieceHold, depth);

                                if (pts > bestPts) {
                                    bestHold = false;
                                    bestX = x;
                                    bestY = y;
                                    bestRt = rt;
                                    bestXSub = x - 1;
                                    bestYSub = y;
                                    bestRtSub = -1;
                                    bestPts = pts;
                                }
                            }

                            // Right shift
                            fld.copy(engine.field);
                            if (!pieceNow.checkCollision(x + 1, y, rt, fld)
                                    && pieceNow.checkCollision(x + 1, y - 1, rt, fld)) {
                                pts = thinkMain(engine, x + 1, y, rt, -1, fld, pieceNow, pieceNext, pieceHold, depth);

                                if (pts > bestPts) {
                                    bestHold = false;
                                    bestX = x;
                                    bestY = y;
                                    bestRt = rt;
                                    bestXSub = x + 1;
                                    bestYSub = y;
                                    bestRtSub = -1;
                                    bestPts = pts;
                                }
                            }

                            // Leftrotation
                            if (!engine.isRotateButtonDefaultRight() || engine.ruleopt.rotateButtonAllowReverse) {
                                int rot = pieceNow.getRotateDirection(-1, rt);
                                int newX = x;
                                int newY = y;
                                fld.copy(engine.field);
                                pts = 0;

                                if (!pieceNow.checkCollision(x, y, rot, fld)) {
                                    pts = thinkMain(engine, x, y, rot, rt, fld, pieceNow, pieceNext, pieceHold, depth);
                                } else if ((engine.wallkick != null) && (engine.ruleopt.rotateWallkick)) {
                                    boolean allowUpward = (engine.ruleopt.rotateMaxUpwardWallkick < 0) ||
                                            (engine.nowUpwardWallkickCount < engine.ruleopt.rotateMaxUpwardWallkick);
                                    WallkickResult kick = engine.wallkick.executeWallkick(x, y, -1, rt, rot,
                                            allowUpward, pieceNow, fld, null);

                                    if (kick != null) {
                                        newX = x + kick.offsetX;
                                        newY = y + kick.offsetY;
                                        pts = thinkMain(engine, newX, newY, rot, rt, fld, pieceNow, pieceNext,
                                                pieceHold, depth);
                                    }
                                }

                                if (pts > bestPts) {
                                    bestHold = false;
                                    bestX = x;
                                    bestY = y;
                                    bestRt = rt;
                                    bestXSub = newX;
                                    bestYSub = newY;
                                    bestRtSub = rot;
                                    bestPts = pts;
                                }
                            }

                            // Rightrotation
                            if (engine.isRotateButtonDefaultRight() || engine.ruleopt.rotateButtonAllowReverse) {
                                int rot = pieceNow.getRotateDirection(1, rt);
                                int newX = x;
                                int newY = y;
                                fld.copy(engine.field);
                                pts = 0;

                                if (!pieceNow.checkCollision(x, y, rot, fld)) {
                                    pts = thinkMain(engine, x, y, rot, rt, fld, pieceNow, pieceNext, pieceHold, depth);
                                } else if ((engine.wallkick != null) && (engine.ruleopt.rotateWallkick)) {
                                    boolean allowUpward = (engine.ruleopt.rotateMaxUpwardWallkick < 0) ||
                                            (engine.nowUpwardWallkickCount < engine.ruleopt.rotateMaxUpwardWallkick);
                                    WallkickResult kick = engine.wallkick.executeWallkick(x, y, 1, rt, rot,
                                            allowUpward, pieceNow, fld, null);

                                    if (kick != null) {
                                        newX = x + kick.offsetX;
                                        newY = y + kick.offsetY;
                                        pts = thinkMain(engine, newX, newY, rot, rt, fld, pieceNow, pieceNext,
                                                pieceHold, depth);
                                    }
                                }

                                if (pts > bestPts) {
                                    bestHold = false;
                                    bestX = x;
                                    bestY = y;
                                    bestRt = rt;
                                    bestXSub = newX;
                                    bestYSub = newY;
                                    bestRtSub = rot;
                                    bestPts = pts;
                                }
                            }

                            // 180-degree rotation
                            if (engine.ruleopt.rotateButtonAllowDouble) {
                                int rot = pieceNow.getRotateDirection(2, rt);
                                int newX = x;
                                int newY = y;
                                fld.copy(engine.field);
                                pts = 0;

                                if (!pieceNow.checkCollision(x, y, rot, fld)) {
                                    pts = thinkMain(engine, x, y, rot, rt, fld, pieceNow, pieceNext, pieceHold, depth);
                                } else if ((engine.wallkick != null) && (engine.ruleopt.rotateWallkick)) {
                                    boolean allowUpward = (engine.ruleopt.rotateMaxUpwardWallkick < 0) ||
                                            (engine.nowUpwardWallkickCount < engine.ruleopt.rotateMaxUpwardWallkick);
                                    WallkickResult kick = engine.wallkick.executeWallkick(x, y, 2, rt, rot,
                                            allowUpward, pieceNow, fld, null);

                                    if (kick != null) {
                                        newX = x + kick.offsetX;
                                        newY = y + kick.offsetY;
                                        pts = thinkMain(engine, newX, newY, rot, rt, fld, pieceNow, pieceNext,
                                                pieceHold, depth);
                                    }
                                }

                                if (pts > bestPts) {
                                    bestHold = false;
                                    bestX = x;
                                    bestY = y;
                                    bestRt = rt;
                                    bestXSub = newX;
                                    bestYSub = newY;
                                    bestRtSub = rot;
                                    bestPts = pts;
                                }
                            }
                        }
                    }
                }

                if (pieceHold == null) {
                    pieceHold = engine.getNextObject(engine.nextPieceCount);
                }
                // Hold Peace
                if ((holdOK == true) && (pieceHold != null) && (depth == 0)) {
                    int spawnX = engine.getSpawnPosX(engine.field, pieceHold);
                    int spawnY = engine.getSpawnPosY(pieceHold);
                    int minHoldX = pieceHold.getMostMovableLeft(spawnX, spawnY, rt, engine.field);
                    int maxHoldX = pieceHold.getMostMovableRight(spawnX, spawnY, rt, engine.field);

                    for (int x = minHoldX; x <= maxHoldX; x++) {
                        fld.copy(engine.field);
                        int y = pieceHold.getBottom(x, spawnY, rt, fld);

                        if (!pieceHold.checkCollision(x, y, rt, fld)) {
                            Piece pieceNext2 = engine.getNextObject(engine.nextPieceCount);
                            if (holdEmpty)
                                pieceNext2 = engine.getNextObject(engine.nextPieceCount + 1);

                            int pts = thinkMain(engine, x, y, rt, -1, fld, pieceHold, pieceNext2, null, depth);

                            if (pts > bestPts) {
                                bestHold = true;
                                bestX = x;
                                bestY = y;
                                bestRt = rt;
                                bestRtSub = -1;
                                bestPts = pts;
                            }
                        }
                    }
                }
            }

            if (bestPts > 0)
                break;
        }

        thinkLastPieceNo++;

        // System.out.println("X:" + bestX + " Y:" + bestY + " R:" + bestRt + " H:" +
        // bestHold + " Pts:" + bestPts);
    }
    /* THINK BEST MOVE */

    /**
     * MaximumCompromise levelGet the
     * 
     * @return MaximumCompromise level
     */
    public int getMaxThinkDepth() {
        return 2;
    }

    public int MinNumberOfOptions() {
        return 3;
    }

    public int MaxDiscardNums() {
        return 7;
    }

    public int MinDelay() {
        return 1000;
    }

    public int MaxDelay() {
        return 1000;
    }

    public int getRandomDelay() {
        int min = MinDelay();
        int max = MaxDelay();

        Random random = new Random();

        return random.nextInt(max) + min;
    }

    /*
     * Processing of the thread
     */
    public void run() {
        log.info("MCTSD: Thread start");
        threadRunning = true;

        while (threadRunning) {
            if (thinkRequest) {
                thinkRequest = false;
                thinking = true;
                try {
                    long startTime = System.currentTimeMillis();
                    thinkBestPosition(gEngine, gEngine.playerID);
                    long endTime = System.currentTimeMillis();
                    long executionTime = endTime - startTime;
                    System.out.println("Execution time: " + executionTime + " ms");
                } catch (Throwable e) {
                    log.debug("MCTSD: thinkBestPosition Failed", e);
                }
                thinking = false;
            }

            if (thinkDelay > 0) {
                try {
                    Thread.sleep(thinkDelay);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        threadRunning = false;
        log.info("MCTSD: Thread end");
    }
}
