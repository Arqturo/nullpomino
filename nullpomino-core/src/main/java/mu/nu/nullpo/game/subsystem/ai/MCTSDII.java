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
import mu.nu.nullpo.game.subsystem.ai.MCTSD;

import java.util.Random;

import org.apache.log4j.Logger;

/**
 * CommonAI
 */
public class MCTSDII extends MCTSD {
    /** Log */
    static Logger log = Logger.getLogger(MCTSDII.class);

    public Thread thread;

    /*
     * AIOfName
     */
    @Override
    public String getName() {
        return "MCTSDII";
    }

    @Override
    public int MinNumberOfOptions() {
        return 6;
    }

    @Override
    public int MaxDiscardNums() {
        return 4;
    }

    @Override
    public int getMaxThinkDepth() {
        return 2;
    }

    @Override
    public int MinDelay() {
        return 500;
    }

    @Override
    public int MaxDelay() {
        return 500;
    }

    /*
     * Processing of the thread
     */
    public void run() {
        log.info("MCTSDII: Thread start");
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
                    log.debug("MCTSDII: thinkBestPosition Failed", e);
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
        log.info("MCTSDII: Thread end");
    }
}
