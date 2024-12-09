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
public class SBasic extends DummyAI implements Runnable {
    /** Log */
    static Logger log = Logger.getLogger(SBasic.class);

    /** After that I was groundedX-coordinate */
    public int bestXSub;

    /** After that I was groundedY-coordinate */
    public int bestYSub;

    /** After that I was groundedDirection(-1: None) */
    public int bestRtSub;

    /** The best moveEvaluation score */
    public int bestPts;

    /** Delay the move for changecount */
    public int delay;

    /** The GameEngine that owns this AI */
    public GameEngine gEngine;

    /** The GameManager that owns this AI */
    public GameManager gManager;

    /** When true,To threadThink routineInstructing the execution of the */
    public boolean thinkRequest;

    /** true when thread is executing the think routine. */
    public boolean thinking;

    /** To stop a thread time */
    public int thinkDelay;

    /** When true,Running thread */
    public volatile boolean threadRunning;

    /** Thread for executing the think routine */
    public Thread thread;

    /*
     * AIOfName
     */
    @Override
    public String getName() {
        return "SBASIC";
    }

    /*
     * Called at initialization
     */
    @Override
    public void init(GameEngine engine, int playerID) {
        delay = 0;
        gEngine = engine;
        gManager = engine.owner;
        thinkRequest = false;
        thinking = false;
        threadRunning = false;

        if (((thread == null) || !thread.isAlive()) && (engine.aiUseThread)) {
            thread = new Thread(this, "AI_" + playerID);
            thread.setDaemon(true);
            thread.start();
            thinkDelay = engine.aiThinkDelay;
            thinkCurrentPieceNo = 0;
            thinkLastPieceNo = 0;
        }
    }

    /*
     * End processing
     */
    @Override
    public void shutdown(GameEngine engine, int playerID) {
        if ((thread != null) && (thread.isAlive())) {
            thread.interrupt();
            threadRunning = false;
            thread = null;
        }
    }

    /*
     * Called whenever a new piece is spawned
     */
    @Override
    public void newPiece(GameEngine engine, int playerID) {
        if (!engine.aiUseThread) {
            thinkBestPosition(engine, playerID);
        } else {
            thinkRequest = true;
            thinkCurrentPieceNo++;
        }
    }

    /*
     * Called at the start of each frame
     */
    @Override
    public void onFirst(GameEngine engine, int playerID) {
    }

    /*
     * Called after every frame
     */
    @Override
    public void onLast(GameEngine engine, int playerID) {
    }

    /*
     * Set button input states
     */
    @Override
    public void setControl(GameEngine engine, int playerID, Controller ctrl) {
        if ((engine.nowPieceObject != null) && (engine.stat == GameEngine.Status.MOVE) && (delay >= engine.aiMoveDelay)
                && (engine.statc[0] > 0) &&
                (!engine.aiUseThread || (threadRunning && !thinking && (thinkCurrentPieceNo <= thinkLastPieceNo)))) {
            int input = 0; // button input data
            Piece pieceNow = engine.nowPieceObject;
            int nowX = engine.nowPieceX;
            int nowY = engine.nowPieceY;
            int rt = pieceNow.direction;
            Field fld = engine.field;
            boolean pieceTouchGround = pieceNow.checkCollision(nowX, nowY + 1, fld);

            if ((bestHold || forceHold) && engine.isHoldOK()) {
                // Hold
                input |= Controller.BUTTON_BIT_D;
            } else {
                // rotation
                if (rt != bestRt) {
                    int lrot = engine.getRotateDirection(-1);
                    int rrot = engine.getRotateDirection(1);

                    if ((Math.abs(rt - bestRt) == 2) && (engine.ruleopt.rotateButtonAllowDouble)
                            && !ctrl.isPress(Controller.BUTTON_E)) {
                        input |= Controller.BUTTON_BIT_E;
                    } else if (!ctrl.isPress(Controller.BUTTON_B) && engine.ruleopt.rotateButtonAllowReverse &&
                            !engine.isRotateButtonDefaultRight() && (bestRt == rrot)) {
                        input |= Controller.BUTTON_BIT_B;
                    } else if (!ctrl.isPress(Controller.BUTTON_B) && engine.ruleopt.rotateButtonAllowReverse &&
                            engine.isRotateButtonDefaultRight() && (bestRt == lrot)) {
                        input |= Controller.BUTTON_BIT_B;
                    } else if (!ctrl.isPress(Controller.BUTTON_A)) {
                        input |= Controller.BUTTON_BIT_A;
                    }
                }

                // Whether reachable position
                int minX = pieceNow.getMostMovableLeft(nowX, nowY, rt, fld);
                int maxX = pieceNow.getMostMovableRight(nowX, nowY, rt, fld);

                if (((bestX < minX - 1) || (bestX > maxX + 1) || (bestY < nowY)) && (rt == bestRt)) {
                    // Again because it is thought unreachable
                    // thinkBestPosition(engine, playerID);
                    thinkRequest = true;
                    // thinkCurrentPieceNo++;
                    // System.out.println("rethink c:" + thinkCurrentPieceNo + " l:" +
                    // thinkLastPieceNo);
                } else {
                    // If you are able to reach
                    if ((nowX == bestX) && (pieceTouchGround) && (rt == bestRt)) {
                        // Groundrotation
                        if (bestRtSub != -1) {
                            bestRt = bestRtSub;
                            bestRtSub = -1;
                        }
                        // Shift move
                        if (bestX != bestXSub) {
                            bestX = bestXSub;
                            bestY = bestYSub;
                        }
                    }

                    if (nowX > bestX) {
                        // Left
                        if (!ctrl.isPress(Controller.BUTTON_LEFT) || (engine.aiMoveDelay >= 0))
                            input |= Controller.BUTTON_BIT_LEFT;
                    } else if (nowX < bestX) {
                        // Right
                        if (!ctrl.isPress(Controller.BUTTON_RIGHT) || (engine.aiMoveDelay >= 0))
                            input |= Controller.BUTTON_BIT_RIGHT;
                    } else if ((nowX == bestX) && (rt == bestRt)) {
                        // Funnel
                        if ((bestRtSub == -1) && (bestX == bestXSub)) {
                            if (engine.ruleopt.harddropEnable && !ctrl.isPress(Controller.BUTTON_UP))
                                input |= Controller.BUTTON_BIT_UP;
                            else if (engine.ruleopt.softdropEnable || engine.ruleopt.softdropLock)
                                input |= Controller.BUTTON_BIT_DOWN;
                        } else {
                            if (engine.ruleopt.harddropEnable && !engine.ruleopt.harddropLock
                                    && !ctrl.isPress(Controller.BUTTON_UP))
                                input |= Controller.BUTTON_BIT_UP;
                            else if (engine.ruleopt.softdropEnable && !engine.ruleopt.softdropLock)
                                input |= Controller.BUTTON_BIT_DOWN;
                        }
                    }
                }
            }

            delay = 0;
            ctrl.setButtonBit(input);
        } else {
            delay++;
            ctrl.setButtonBit(0);
        }
    }

    /**
     * Search for the best choice
     * 
     * @param engine   The GameEngine that owns this AI
     * @param playerID Player ID
     */
    /* THINK BEST MOVE */

    public void thinkBestPosition(GameEngine engine, int playerID) {
        resetBestPosition();

        // Retrieve pieces and initial setup
        Piece pieceNow = engine.nowPieceObject;
        int nowX = engine.nowPieceX;
        int nowY = engine.nowPieceY;
        boolean holdOK = engine.isHoldOK();
        boolean holdEmpty = (engine.holdPieceObject == null);
        Piece pieceHold = engine.holdPieceObject;
        Piece pieceNext = engine.getNextObject(engine.nextPieceCount);
        Field fld = new Field(engine.field);

        // Loop through depths and rotations
        for (int depth = 0; depth < getMaxThinkDepth(); depth++) {
            for (int rt = 0; rt < Piece.DIRECTION_COUNT; rt++) {
                handlePieceMovement(engine, fld, pieceNow, nowX, nowY, rt, pieceNext, pieceHold, depth);
                handleHoldPieceMovement(engine, fld, pieceHold, rt, depth, holdOK, holdEmpty);
            }

            if (bestPts > 0)
                break; // Early exit if a good position is found
        }

        thinkLastPieceNo++;
    }

    // Reset the best position variables
    private void resetBestPosition() {
        bestHold = false;
        bestX = 0;
        bestY = 0;
        bestRt = 0;
        bestXSub = 0;
        bestYSub = 0;
        bestRtSub = -1;
        bestPts = 0;
        forceHold = false;
    }

    // Handle piece movement and scoring
    private void handlePieceMovement(GameEngine engine, Field fld, Piece pieceNow, int nowX, int nowY, int rt,
            Piece pieceNext, Piece pieceHold, int depth) {
        int minX = pieceNow.getMostMovableLeft(nowX, nowY, rt, engine.field);
        int maxX = pieceNow.getMostMovableRight(nowX, nowY, rt, engine.field);

        System.out.println("Left " + minX + "right " + maxX);

        for (int x = minX; x <= maxX; x++) {
            fld.copy(engine.field);
            int y = pieceNow.getBottom(x, nowY, rt, fld);

            if (!pieceNow.checkCollision(x, y, rt, fld)) {
                evaluatePiecePosition(engine, fld, pieceNow, x, y, rt, pieceNext, pieceHold, depth);
                evaluateSubMovements(engine, fld, pieceNow, x, y, rt, pieceNext, pieceHold, depth);
            }
        }
    }

    // Evaluate the main position for a piece
    private void evaluatePiecePosition(GameEngine engine, Field fld, Piece pieceNow, int x, int y, int rt,
            Piece pieceNext, Piece pieceHold, int depth) {
        int pts = thinkMain(engine, x, y, rt, -1, fld, pieceNow, pieceNext, pieceHold, depth);
        if (pts >= bestPts) {
            updateBestPosition(x, y, rt, -1, pts);
        }
    }

    // Evaluate sub-movements like left/right shifts and rotations
    private void evaluateSubMovements(GameEngine engine, Field fld, Piece pieceNow, int x, int y, int rt,
            Piece pieceNext, Piece pieceHold, int depth) {
        // Left Shift
        evaluateShift(engine, fld, pieceNow, x - 1, y, rt, pieceNext, pieceHold, depth);

        // Right Shift
        evaluateShift(engine, fld, pieceNow, x + 1, y, rt, pieceNext, pieceHold, depth);

        // Left Rotation
        evaluateRotation(engine, fld, pieceNow, x, y, rt, -1, pieceNext, pieceHold, depth);

        // Right Rotation
        evaluateRotation(engine, fld, pieceNow, x, y, rt, 1, pieceNext, pieceHold, depth);

        // 180-degree Rotation
        if (engine.ruleopt.rotateButtonAllowDouble) {
            evaluateRotation(engine, fld, pieceNow, x, y, rt, 2, pieceNext, pieceHold, depth);
        }
    }

    // Evaluate a shift (left or right)
    private void evaluateShift(GameEngine engine, Field fld, Piece pieceNow, int newX, int y, int rt,
            Piece pieceNext, Piece pieceHold, int depth) {
        if (!pieceNow.checkCollision(newX, y, rt, fld) && pieceNow.checkCollision(newX, y - 1, rt, fld)) {
            int pts = thinkMain(engine, newX, y, rt, -1, fld, pieceNow, pieceNext, pieceHold, depth);
            if (pts > bestPts) {
                updateBestPosition(newX, y, rt, -1, pts);
            }
        }
    }

    // Evaluate a rotation (left, right, or 180-degree)
    private void evaluateRotation(GameEngine engine, Field fld, Piece pieceNow, int x, int y, int rt,
            int rotationDirection, Piece pieceNext, Piece pieceHold, int depth) {
        int rot = pieceNow.getRotateDirection(rotationDirection, rt);
        int newX = x;
        int newY = y;

        fld.copy(engine.field);
        int pts = 0;

        if (!pieceNow.checkCollision(x, y, rot, fld)) {
            pts = thinkMain(engine, x, y, rot, rt, fld, pieceNow, pieceNext, pieceHold, depth);
        } else if (engine.wallkick != null && engine.ruleopt.rotateWallkick) {
            WallkickResult kick = engine.wallkick.executeWallkick(x, y, rotationDirection, rt, rot,
                    engine.ruleopt.rotateMaxUpwardWallkick >= 0, pieceNow, fld, null);
            if (kick != null) {
                newX = x + kick.offsetX;
                newY = y + kick.offsetY;
                pts = thinkMain(engine, newX, newY, rot, rt, fld, pieceNow, pieceNext, pieceHold, depth);
            }
        }

        if (pts > bestPts) {
            updateBestPosition(newX, newY, rt, rot, pts);
        }
    }

    // Update the best position found so far
    private void updateBestPosition(int x, int y, int rt, int rot, int pts) {
        bestHold = false;
        bestX = x;
        bestY = y;
        bestRt = rt;
        bestXSub = x;
        bestYSub = y;
        bestRtSub = rot;
        bestPts = pts;
    }

    // Handle the hold piece logic
    private void handleHoldPieceMovement(GameEngine engine, Field fld, Piece pieceHold, int rt,
            int depth, boolean holdOK, boolean holdEmpty) {
        if (holdOK && pieceHold != null && depth == 0) {
            int spawnX = engine.getSpawnPosX(engine.field, pieceHold);
            int spawnY = engine.getSpawnPosY(pieceHold);
            int minHoldX = pieceHold.getMostMovableLeft(spawnX, spawnY, rt, engine.field);
            int maxHoldX = pieceHold.getMostMovableRight(spawnX, spawnY, rt, engine.field);

            for (int x = minHoldX; x <= maxHoldX; x++) {
                fld.copy(engine.field);
                int y = pieceHold.getBottom(x, spawnY, rt, fld);

                if (!pieceHold.checkCollision(x, y, rt, fld)) {
                    Piece pieceNext2 = (holdEmpty) ? engine.getNextObject(engine.nextPieceCount + 1)
                            : engine.getNextObject(engine.nextPieceCount);
                    int pts = thinkMain(engine, x, y, rt, -1, fld, pieceHold, pieceNext2, null, depth);

                    if (pts > bestPts) {
                        updateBestPosition(x, y, rt, -1, pts);
                        bestHold = true;
                    }
                }
            }
        }
    }

    /* THINK BEST MOVE */

    /**
     * Think routine
     * 
     * @param engine    GameEngine
     * @param x         X-coordinate
     * @param y         Y-coordinate
     * @param rt        Direction
     * @param rtOld     Direction before rotation (-1: None)
     * @param fld       Field (Can be modified without problems)
     * @param piece     Piece
     * @param nextpiece NEXTPeace
     * @param holdpiece HOLDPeace(nullMay be)
     * @param depth     Compromise level (ranges from 0 through getMaxThinkDepth-1)
     * @return Evaluation score
     */
    public int thinkMain(GameEngine engine, int x, int y, int rt, int rtOld, Field fld, Piece piece, Piece nextpiece,
            Piece holdpiece, int depth) {
        int pts = 0;

        // Add points for being adjacent to other blocks
        if (piece.checkCollision(x - 1, y, fld))
            pts += 1;
        if (piece.checkCollision(x + 1, y, fld))
            pts += 1;
        if (piece.checkCollision(x, y - 1, fld))
            pts += 100;

        // Number of holes and valleys needing an I piece (before placement)
        int holeBefore = fld.getHowManyHoles();
        int lidBefore = fld.getHowManyLidAboveHoles();
        int needIValleyBefore = fld.getTotalValleyNeedIPiece();
        // Field height (before placement)
        int heightBefore = fld.getHighestBlockY();
        // T-Spin flag
        boolean tspin = false;
        if ((piece.id == Piece.PIECE_T) && (rtOld != -1) && (fld.isTSpinSpot(x, y, piece.big))) {
            tspin = true;
        }

        // Place the piece
        if (!piece.placeToField(x, y, rt, fld)) {
            return 0;
        }

        // Line clear
        int lines = fld.checkLine();
        if (lines > 0) {
            fld.clearLine();
            fld.downFloatingBlocks();
        }

        // All clear
        boolean allclear = fld.isEmpty();
        if (allclear)
            pts += 500000;

        // Field height (after clears)
        int heightAfter = fld.getHighestBlockY();

        // Danger flag
        boolean danger = (heightAfter <= 12);

        // Additional points for lower placements
        if ((!danger) && (depth == 0))
            pts += y * 10;
        else
            pts += y * 20;

        // LinescountAdditional points in
        if ((lines == 1) && (!danger) && (depth == 0) && (heightAfter >= 16) && (holeBefore < 3) && (!tspin)
                && (engine.combo < 1)) {
            return 0;
        }
        if ((!danger) && (depth == 0)) {
            if (lines == 1)
                pts += 10;
            if (lines == 2)
                pts += 50;
            if (lines == 3)
                pts += 100;
            if (lines >= 4)
                pts += 100000;
        } else {
            if (lines == 1)
                pts += 5000;
            if (lines == 2)
                pts += 10000;
            if (lines == 3)
                pts += 30000;
            if (lines >= 4)
                pts += 100000;
        }

        if ((lines < 4) && (!allclear)) {
            // Number of holes and valleys needing an I piece (after placement)
            int holeAfter = fld.getHowManyHoles();
            int lidAfter = fld.getHowManyLidAboveHoles();
            int needIValleyAfter = fld.getTotalValleyNeedIPiece();

            if (holeAfter > holeBefore) {
                // Demerits for new holes
                pts -= (holeAfter - holeBefore) * 10;
                if (depth == 0)
                    return 0;
            } else if (holeAfter < holeBefore) {
                // Add points for reduction in number of holes
                if (!danger)
                    pts += (holeBefore - holeAfter) * 5;
                else
                    pts += (holeBefore - holeAfter) * 10;
            }

            if (lidAfter > lidBefore) {
                // Is riding on top of the holeBlockIncreasing the deduction
                if (!danger)
                    pts -= (lidAfter - lidBefore) * 10;
                else
                    pts -= (lidAfter - lidBefore) * 20;
            } else if (lidAfter < lidBefore) {
                // Add points for reduction in number blocks above holes
                if (!danger)
                    pts += (lidBefore - lidAfter) * 10;
                else
                    pts += (lidBefore - lidAfter) * 20;
            }

            if ((tspin) && (lines >= 1)) {
                // T-Spin bonus
                pts += 100000 * lines;
            }

            if ((needIValleyAfter > needIValleyBefore) && (needIValleyAfter >= 2)) {
                // 2One or moreIDeduction and make a hole type is required
                pts -= (needIValleyAfter - needIValleyBefore) * 10;
                if (depth == 0)
                    return 0;
            } else if (needIValleyAfter < needIValleyBefore) {
                // Add points for reduction in number of holes
                if ((depth == 0) && (!danger))
                    pts += (needIValleyBefore - needIValleyAfter) * 10;
                else
                    pts += (needIValleyBefore - needIValleyAfter) * 20;
            }

            if (heightBefore < heightAfter) {
                // Add points for reducing the height
                if ((depth == 0) && (!danger))
                    pts += (heightAfter - heightBefore) * 10;
                else
                    pts += (heightAfter - heightBefore) * 20;
            } else if (heightBefore > heightAfter) {
                // Demerits for increase in height
                if ((depth > 0) || (danger))
                    pts -= (heightBefore - heightAfter) * 4;
            }

            // Combo bonus
            if ((lines >= 1) && (engine.comboType != GameEngine.COMBO_TYPE_DISABLE)) {
                pts += lines * engine.combo * 100;
            }
        }

        return pts;
    }

    /**
     * MaximumCompromise levelGet the
     * 
     * @return MaximumCompromise level
     */
    public int getMaxThinkDepth() {
        return 2;
    }

    /*
     * Processing of the thread
     */
    public void run() {
        log.info("SBasic: Thread start");
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
                    System.out.println("" + executionTime + ",");

                } catch (Throwable e) {
                    log.debug("SBasic: thinkBestPosition Failed", e);
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
        log.info("SBasic: Thread end");
    }
}
