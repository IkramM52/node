package com.example.rpgblock;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private Thread thread;
    private boolean running = false;

    private final int mapWidth = 2000;
    private final int mapHeight = 2000;

    private float cameraX = 0;
    private float cameraY = 0;
    private int screenW, screenH;

    private float playerX = 100, playerY = 100;
    private final int blockSize = 100;
    private final Paint paint = new Paint();
    private boolean upPressed, downPressed, leftPressed, rightPressed;
    private float playerKnockbackX = 0, playerKnockbackY = 0;
    private int playerHP = 200;
    private int playerMaxHP = 200;
    private long playerLastHitTime = 0;
    private long playerStunUntil = 0;

    // Skill charge
    private boolean playerSkillActive = false;
    private float playerSkillDirX = 0, playerSkillDirY = 0;
    private int playerSkillTarget = -1;
    private long playerSkillCDUntil = 0;

    // Skill jump
    private boolean playerJumpActive = false;
    private float playerJumpTargetX = 0, playerJumpTargetY = 0;
    private long playerJumpCDUntil = 0;
    private static final long JUMP_CD = 60000;
    private static final float JUMP_RANGE = 650f;
    private static final float JUMP_AOE = 5 * 100f;
    private static final long JUMP_STUN = 2000;

    private long playerShadowUntil = 0;

    // Skill chain
    private long playerExplodeCDUntil = 0;
    private boolean explodeButtonPressed = false;
    private float explodeBtnX, explodeBtnY, explodeBtnR;
    private boolean playerChainAttackActive = false;
    private int playerChainCount = 0;
    private boolean[] playerChainAttacked = new boolean[21];
    private static final long EXPLODE_CD = 40000;
    private static final float EXPLODE_CHAIN_RANGE = 700f;
    private static final long EXPLODE_CHAIN_STUN = 3000;
    private static final int EXPLODE_CHAIN_DMG = 10;
    private static final int EXPLODE_CHAIN_KNOCKBACK = 100;
    private static final int EXPLODE_CHAIN_REPEAT = 3;

    // Skill missile
    private long playerMissileCDUntil = 0;
    private boolean missileButtonPressed = false;
    private float missileBtnX, missileBtnY, missileBtnR;
    private boolean missileActive = false;
    private float missileX, missileY;
    private int missileTargetIdx = -1;
    private float missileSpeed = 35f;
    private boolean missileIsPlayer = true;
    private static final long MISSILE_CD = 60000;
    private static final float MISSILE_SEARCH_RANGE = 1500f;
    private static final int MISSILE_DMG = 20;
    private static final long MISSILE_STUN = 2000;

    // NPCs
    private static final int NPC_COUNT = 20;
    private float[] npcX = new float[NPC_COUNT];
    private float[] npcY = new float[NPC_COUNT];
    private float[] npcKnockbackX = new float[NPC_COUNT];
    private float[] npcKnockbackY = new float[NPC_COUNT];
    private int[] npcDx = new int[NPC_COUNT];
    private int[] npcDy = new int[NPC_COUNT];
    private long[] npcLastDirChange = new long[NPC_COUNT];
    private int[] npcHP = new int[NPC_COUNT];
    private int[] npcMaxHP = new int[NPC_COUNT];
    private int[] npcColors = {Color.BLUE, Color.CYAN, Color.MAGENTA, Color.YELLOW, Color.LTGRAY, Color.RED, Color.GREEN, Color.DKGRAY, Color.WHITE, Color.BLACK};
    private long[] npcLastHitTime = new long[NPC_COUNT];
    private boolean[] npcAlive = new boolean[NPC_COUNT];
    private long[] npcStunUntil = new long[NPC_COUNT];
    private long[] npcShadowUntil = new long[NPC_COUNT];

    // NPC skills
    private boolean[] npcSkillActive = new boolean[NPC_COUNT];
    private float[] npcSkillDirX = new float[NPC_COUNT];
    private float[] npcSkillDirY = new float[NPC_COUNT];
    private long[] npcSkillCDUntil = new long[NPC_COUNT];
    private int[] npcSkillTarget = new int[NPC_COUNT];

    private boolean[] npcJumpActive = new boolean[NPC_COUNT];
    private float[] npcJumpTargetX = new float[NPC_COUNT];
    private float[] npcJumpTargetY = new float[NPC_COUNT];
    private long[] npcJumpCDUntil = new long[NPC_COUNT];

    private long[] npcExplodeCDUntil = new long[NPC_COUNT];
    private boolean[] npcChainAttackActive = new boolean[NPC_COUNT];
    private int[] npcChainCount = new int[NPC_COUNT];
    private boolean[][] npcChainAttacked = new boolean[NPC_COUNT][21];

    private long[] npcMissileCDUntil = new long[NPC_COUNT];
    private boolean[] npcMissileActive = new boolean[NPC_COUNT];
    private float[] npcMissileX = new float[NPC_COUNT];
    private float[] npcMissileY = new float[NPC_COUNT];
    private int[] npcMissileTargetIdx = new int[NPC_COUNT];
    private boolean[] npcMissileIsPlayerTarget = new boolean[NPC_COUNT];

    private static final long STUN_TIME = 700;
    private static final long SKILL_CD = 30000;
    private static final float CHARGE_RANGE = 350f;
    private static final float CHARGE_SPEED = 60f;
    private static final long HIT_COOLDOWN = 300;

    private final Random random = new Random();

    private boolean skillButtonPressed = false;
    private float skillBtnX, skillBtnY, skillBtnR;

    private boolean jumpButtonPressed = false;
    private float jumpBtnX, jumpBtnY, jumpBtnR;

    private Activity activity;

    public GameView(Context context) {
        super(context);
        if (context instanceof Activity) activity = (Activity) context;
        getHolder().addCallback(this);
        setFocusable(true);

        for (int i = 0; i < NPC_COUNT; i++) {
            npcX[i] = 300 + (i % 5) * 300;
            npcY[i] = 300 + (i / 5) * 300;
            npcHP[i] = 100;
            npcMaxHP[i] = 100;
            npcAlive[i] = true;
            npcSkillTarget[i] = -1;
            npcShadowUntil[i] = 0;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    @Override
    public void run() {
        while (running) {
            Canvas canvas = null;
            try {
                canvas = getHolder().lockCanvas();
                if (canvas != null) {
                    if (screenW == 0 || screenH == 0) {
                        screenW = canvas.getWidth();
                        screenH = canvas.getHeight();
                        skillBtnR = 70;
                        skillBtnX = screenW - skillBtnR - 30;
                        skillBtnY = 30 + skillBtnR;
                        jumpBtnR = 60;
                        jumpBtnX = screenW - jumpBtnR - 30;
                        jumpBtnY = skillBtnY + skillBtnR + 30 + jumpBtnR;
                        explodeBtnR = 55;
                        explodeBtnX = screenW - explodeBtnR - 30;
                        explodeBtnY = jumpBtnY + jumpBtnR + 30 + explodeBtnR;
                        missileBtnR = 55;
                        missileBtnX = screenW - missileBtnR - 30;
                        missileBtnY = explodeBtnY + explodeBtnR + 30 + missileBtnR;
                    }
                    update();
                    drawGame(canvas);
                }
            } finally {
                if (canvas != null) getHolder().unlockCanvasAndPost(canvas);
            }
            try { Thread.sleep(16); } catch (InterruptedException e) {}
        }
    }

    private void update() {
        int speed = 20;
        boolean playerMoving = false;
        long now = System.currentTimeMillis();

        // PLAYER: Movement, stun, skill charge/jump/chain/missile
        if (now < playerStunUntil) {
            // Stun
        } else if (playerSkillActive) {
            playerX += playerSkillDirX * CHARGE_SPEED;
            playerY += playerSkillDirY * CHARGE_SPEED;
            int i = playerSkillTarget;
            if (i >= 0 && i < NPC_COUNT && npcAlive[i] && checkOverlap(playerX, playerY, npcX[i], npcY[i])) {
                npcHP[i] -= 3;
                npcStunUntil[i] = now + STUN_TIME;
                if (npcHP[i] <= 0) npcAlive[i] = false;
                playerSkillCDUntil = now + SKILL_CD;
                playerSkillActive = false;
                playerSkillTarget = -1;
            }
        } else if (playerJumpActive) {
            playerX = playerJumpTargetX;
            playerY = playerJumpTargetY;
            for (int i = 0; i < NPC_COUNT; i++) {
                if (!npcAlive[i]) continue;
                float dx = npcX[i] - playerX, dy = npcY[i] - playerY;
                float dist = (float)Math.sqrt(dx*dx + dy*dy);
                if (dist <= JUMP_AOE) {
                    npcHP[i] -= 15;
                    npcStunUntil[i] = now + JUMP_STUN;
                    float kx = dx / (dist == 0 ? 1 : dist);
                    float ky = dy / (dist == 0 ? 1 : dist);
                    float strength = 60;
                    npcKnockbackX[i] += kx * strength;
                    npcKnockbackY[i] += ky * strength;
                    if (npcHP[i] <= 0) npcAlive[i] = false;
                }
            }
            playerJumpCDUntil = now + JUMP_CD;
            playerJumpActive = false;
        } else if (playerChainAttackActive) {
            int nextIdx = getClosestNpcIdx(playerX, playerY, playerChainAttacked);
            if (nextIdx != -1) {
                float dx = npcX[nextIdx] - playerX, dy = npcY[nextIdx] - playerY;
                float dist = (float)Math.sqrt(dx*dx + dy*dy);
                float nearDist = blockSize * 1.2f;
                playerX = npcX[nextIdx] - (dx / (dist == 0 ? 1 : dist)) * nearDist;
                playerY = npcY[nextIdx] - (dy / (dist == 0 ? 1 : dist)) * nearDist;
                npcHP[nextIdx] -= EXPLODE_CHAIN_DMG;
                npcStunUntil[nextIdx] = now + EXPLODE_CHAIN_STUN;
                npcKnockbackX[nextIdx] += (dx / (dist == 0 ? 1 : dist)) * EXPLODE_CHAIN_KNOCKBACK;
                npcKnockbackY[nextIdx] += (dy / (dist == 0 ? 1 : dist)) * EXPLODE_CHAIN_KNOCKBACK;
                npcShadowUntil[nextIdx] = now + 600;
                if (npcHP[nextIdx] <= 0) npcAlive[nextIdx] = false;
                playerShadowUntil = now + 600;
                playerChainAttacked[nextIdx] = true;
                playerChainCount++;
            }
            if (nextIdx == -1 || playerChainCount >= EXPLODE_CHAIN_REPEAT) {
                playerChainAttackActive = false;
            }
        } else {
            if (upPressed)   { playerY -= speed; playerMoving = true; }
            if (downPressed) { playerY += speed; playerMoving = true; }
            if (leftPressed) { playerX -= speed; playerMoving = true; }
            if (rightPressed){ playerX += speed; playerMoving = true; }
        }

        playerX = Math.max(0, Math.min(playerX, mapWidth - blockSize));
        playerY = Math.max(0, Math.min(playerY, mapHeight - blockSize));

        // PLAYER MISSILE
        if (missileButtonPressed && now > playerMissileCDUntil && !missileActive) {
            float bestDist = Float.MAX_VALUE;
            int bestIdx = -1;
            for (int i = 0; i < NPC_COUNT; i++) {
                if (!npcAlive[i]) continue;
                float dx = npcX[i] - playerX, dy = npcY[i] - playerY;
                float dist = (float)Math.sqrt(dx*dx + dy*dy);
                if (dist < bestDist && dist < MISSILE_SEARCH_RANGE) {
                    bestDist = dist; bestIdx = i;
                }
            }
            if (bestIdx != -1) {
                missileActive = true;
                missileX = playerX + blockSize/2f;
                missileY = playerY + blockSize/2f;
                missileTargetIdx = bestIdx;
                missileIsPlayer = true;
                playerMissileCDUntil = now + MISSILE_CD;
            }
            missileButtonPressed = false;
        }
        if (missileActive && missileTargetIdx != -1 && missileIsPlayer) {
            if (missileTargetIdx >= 0 && missileTargetIdx < NPC_COUNT && npcAlive[missileTargetIdx]) {
                float tx = npcX[missileTargetIdx] + blockSize/2f;
                float ty = npcY[missileTargetIdx] + blockSize/2f;
                float dx = tx - missileX, dy = ty - missileY;
                float dist = (float)Math.sqrt(dx*dx + dy*dy);
                if (dist > 10) {
                    float vx = dx / dist * missileSpeed;
                    float vy = dy / dist * missileSpeed;
                    missileX += vx;
                    missileY += vy;
                }
                if (dist < blockSize/1.5f) {
                    npcHP[missileTargetIdx] -= MISSILE_DMG;
                    npcStunUntil[missileTargetIdx] = now + MISSILE_STUN;
                    if (npcHP[missileTargetIdx] <= 0) npcAlive[missileTargetIdx] = false;
                    missileActive = false;
                }
            } else {
                missileActive = false;
            }
        }

        // ================= NPC =================
        for (int i = 0; i < NPC_COUNT; i++) {
            if (!npcAlive[i]) continue;

            // NPC missile
            if (!npcMissileActive[i] && now > npcMissileCDUntil[i]) {
                boolean targetPlayer = false;
                int bestIdx = -1;
                float bestDist = Float.MAX_VALUE;
                float dxp = playerX - npcX[i], dyp = playerY - npcY[i];
                float distp = (float)Math.sqrt(dxp*dxp + dyp*dyp);
                if (playerHP > 0 && distp < MISSILE_SEARCH_RANGE) { bestDist = distp; targetPlayer = true; }
                for (int j = 0; j < NPC_COUNT; j++) {
                    if (j == i || !npcAlive[j]) continue;
                    float dx2 = npcX[j] - npcX[i], dy2 = npcY[j] - npcY[i];
                    float dist2 = (float)Math.sqrt(dx2*dx2 + dy2*dy2);
                    if (dist2 < bestDist && dist2 < MISSILE_SEARCH_RANGE) {
                        bestDist = dist2;
                        targetPlayer = false;
                        bestIdx = j;
                    }
                }
                if (targetPlayer || bestIdx != -1) {
                    npcMissileActive[i] = true;
                    npcMissileX[i] = npcX[i] + blockSize/2f;
                    npcMissileY[i] = npcY[i] + blockSize/2f;
                    npcMissileTargetIdx[i] = targetPlayer ? -1 : bestIdx;
                    npcMissileIsPlayerTarget[i] = targetPlayer;
                    npcMissileCDUntil[i] = now + MISSILE_CD;
                }
            }
            if (npcMissileActive[i]) {
                float tx, ty;
                if (npcMissileIsPlayerTarget[i]) {
                    tx = playerX + blockSize/2f; ty = playerY + blockSize/2f;
                } else if (npcMissileTargetIdx[i] >= 0 && npcMissileTargetIdx[i] < NPC_COUNT && npcAlive[npcMissileTargetIdx[i]]) {
                    tx = npcX[npcMissileTargetIdx[i]] + blockSize/2f; ty = npcY[npcMissileTargetIdx[i]] + blockSize/2f;
                } else {
                    npcMissileActive[i] = false;
					continue;
                }
                if (npcMissileActive[i]) {
                    float dx = tx - npcMissileX[i], dy = ty - npcMissileY[i];
                    float dist = (float)Math.sqrt(dx*dx + dy*dy);
                    if (dist > 10) {
                        float vx = dx / dist * missileSpeed;
                        float vy = dy / dist * missileSpeed;
                        npcMissileX[i] += vx;
                        npcMissileY[i] += vy;
                    }
                    if (dist < blockSize/1.5f) {
                        if (npcMissileIsPlayerTarget[i]) {
                            playerHP -= MISSILE_DMG;
                            playerStunUntil = now + MISSILE_STUN;
                        } else {
                            npcHP[npcMissileTargetIdx[i]] -= MISSILE_DMG;
                            npcStunUntil[npcMissileTargetIdx[i]] = now + MISSILE_STUN;
                            if (npcHP[npcMissileTargetIdx[i]] <= 0) npcAlive[npcMissileTargetIdx[i]] = false;
                        }
                        npcMissileActive[i] = false;
                    }
                }
            }

            // NPC chain attack
            if (npcChainAttackActive[i]) {
                int nextIdx = getClosestNpcIdx(npcX[i], npcY[i], npcChainAttacked[i], i);
                if (nextIdx == NPC_COUNT) {
                    float dx = playerX - npcX[i], dy = playerY - npcY[i];
                    float dist = (float)Math.sqrt(dx*dx + dy*dy);
                    float nearDist = blockSize * 1.2f;
                    npcX[i] = playerX - (dx / (dist == 0 ? 1 : dist)) * nearDist;
                    npcY[i] = playerY - (dy / (dist == 0 ? 1 : dist)) * nearDist;
                    playerHP -= EXPLODE_CHAIN_DMG;
                    playerStunUntil = now + EXPLODE_CHAIN_STUN;
                    playerKnockbackX += (dx / (dist == 0 ? 1 : dist)) * EXPLODE_CHAIN_KNOCKBACK;
                    playerKnockbackY += (dy / (dist == 0 ? 1 : dist)) * EXPLODE_CHAIN_KNOCKBACK;
                    playerShadowUntil = now + 600;
                    npcChainAttacked[i][NPC_COUNT] = true;
                    npcChainCount[i]++;
                } else if (nextIdx != -1) {
                    float dx = npcX[nextIdx] - npcX[i], dy = npcY[nextIdx] - npcY[i];
                    float dist = (float)Math.sqrt(dx*dx + dy*dy);
                    float nearDist = blockSize * 1.2f;
                    npcX[i] = npcX[nextIdx] - (dx / (dist == 0 ? 1 : dist)) * nearDist;
                    npcY[i] = npcY[nextIdx] - (dy / (dist == 0 ? 1 : dist)) * nearDist;
                    npcHP[nextIdx] -= EXPLODE_CHAIN_DMG;
                    npcStunUntil[nextIdx] = now + EXPLODE_CHAIN_STUN;
                    npcKnockbackX[nextIdx] += (dx / (dist == 0 ? 1 : dist)) * EXPLODE_CHAIN_KNOCKBACK;
                    npcKnockbackY[nextIdx] += (dy / (dist == 0 ? 1 : dist)) * EXPLODE_CHAIN_KNOCKBACK;
                    npcShadowUntil[nextIdx] = now + 600;
                    if (npcHP[nextIdx] <= 0) npcAlive[nextIdx] = false;
                    npcChainAttacked[i][nextIdx] = true;
                    npcChainCount[i]++;
                }
                if ((nextIdx == -1 || npcChainCount[i] >= EXPLODE_CHAIN_REPEAT)) {
                    npcChainAttackActive[i] = false;
                }
            }

            // NPC stun
            if (now < npcStunUntil[i]) {
                npcDx[i] = 0; npcDy[i] = 0;
            } else {
                // === Movement/AI Logic: Cegah saling menumpuk ===
                float bestDist = Float.MAX_VALUE;
                float tx = -1, ty = -1;
                boolean isPlayer = false;
                float dxp = playerX - npcX[i], dyp = playerY - npcY[i];
                float distp = (float)Math.sqrt(dxp*dxp + dyp*dyp);
                if (distp < bestDist && playerHP > 0) {
                    bestDist = distp; tx = playerX; ty = playerY; isPlayer = true;
                }
                for (int j = 0; j < NPC_COUNT; j++) {
                    if (j == i || !npcAlive[j]) continue;
                    float dxn = npcX[j] - npcX[i], dyn = npcY[j] - npcY[i];
                    float distn = (float)Math.sqrt(dxn*dxn + dyn*dyn);
                    // Jangan mendekat terlalu dekat dengan NPC lain (anti merge/menumpuk)
                    if (distn < blockSize * 0.95f) {
                        float nx = (npcX[i] - npcX[j]) / (distn == 0 ? 1 : distn);
                        float ny = (npcY[i] - npcY[j]) / (distn == 0 ? 1 : distn);
                        npcX[i] += nx * 6.5f;
                        npcY[i] += ny * 6.5f;
                    }
                    if (distn < bestDist) {
                        bestDist = distn; tx = npcX[j]; ty = npcY[j]; isPlayer = false;
                    }
                }
                if (bestDist < 600 && now > npcStunUntil[i]) {
                    float dx = tx - npcX[i], dy = ty - npcY[i];
                    if (Math.abs(dx) > 20) npcDx[i] = (int)Math.signum(dx);
                    else npcDx[i] = 0;
                    if (Math.abs(dy) > 20) npcDy[i] = (int)Math.signum(dy);
                    else npcDy[i] = 0;
                } else {
                    if (now - npcLastDirChange[i] > 1000) {
                        int[] dirs = {-1, 0, 1};
                        npcDx[i] = dirs[random.nextInt(3)];
                        npcDy[i] = dirs[random.nextInt(3)];
                        npcLastDirChange[i] = now;
                    }
                }
                npcX[i] += npcDx[i] * 5;
                npcY[i] += npcDy[i] * 5;
                npcX[i] = Math.max(0, Math.min(npcX[i], mapWidth - blockSize));
                npcY[i] = Math.max(0, Math.min(npcY[i], mapHeight - blockSize));
            }
        }

        // Chain attack NPC trigger
        for (int i = 0; i < NPC_COUNT; i++) {
            if (!npcAlive[i]) continue;
            if (!npcChainAttackActive[i] && now > npcExplodeCDUntil[i] && now > npcStunUntil[i]) {
                boolean adaTargetDekat = false;
                float dx = playerX - npcX[i], dy = playerY - npcY[i];
                float dist = (float)Math.sqrt(dx*dx + dy*dy);
                if (npcAlive[i] && playerHP > 0 && dist <= EXPLODE_CHAIN_RANGE) adaTargetDekat = true;
                for (int j = 0; j < NPC_COUNT; j++) {
                    if (j == i || !npcAlive[j]) continue;
                    float dx2 = npcX[j] - npcX[i], dy2 = npcY[j] - npcY[i];
                    float dist2 = (float)Math.sqrt(dx2*dx2 + dy2*dy2);
                    if (dist2 <= EXPLODE_CHAIN_RANGE) { adaTargetDekat = true; break; }
                }
                if (adaTargetDekat && random.nextFloat() < 0.01f) {
                    for (int j = 0; j <= NPC_COUNT; j++) npcChainAttacked[i][j] = false;
                    npcChainAttackActive[i] = true;
                    npcChainCount[i] = 0;
                    npcExplodeCDUntil[i] = now + EXPLODE_CD;
                    npcHP[i] -= 10;
                }
            }
        }

        // Skill charge/jump/chain trigger
        if (skillButtonPressed && !playerSkillActive && now > playerSkillCDUntil && now > playerStunUntil && !playerJumpActive) {
            for (int i = 0; i < NPC_COUNT; i++) {
                if (!npcAlive[i]) continue;
                float dx = npcX[i] - playerX, dy = npcY[i] - playerY;
                float dist = (float)Math.sqrt(dx*dx + dy*dy);
                if (dist < CHARGE_RANGE) {
                    playerSkillDirX = dx / dist;
                    playerSkillDirY = dy / dist;
                    playerSkillActive = true;
                    playerSkillTarget = i;
                    break;
                }
            }
        }
        if (jumpButtonPressed && !playerJumpActive && now > playerJumpCDUntil && now > playerStunUntil && !playerSkillActive) {
            float bestDist = Float.MAX_VALUE;
            float tx = playerX, ty = playerY;
            for (int i = 0; i < NPC_COUNT; i++) {
                if (!npcAlive[i]) continue;
                float dx = npcX[i] - playerX, dy = npcY[i] - playerY;
                float dist = (float)Math.sqrt(dx*dx + dy*dy);
                if (dist < JUMP_RANGE && dist < bestDist) {
                    bestDist = dist;
                    tx = npcX[i];
                    ty = npcY[i];
                }
            }
            if (bestDist == Float.MAX_VALUE) {
                tx = playerX + JUMP_RANGE;
                ty = playerY;
                tx = Math.max(0, Math.min(tx, mapWidth - blockSize));
                ty = Math.max(0, Math.min(ty, mapHeight - blockSize));
            }
            playerJumpTargetX = tx;
            playerJumpTargetY = ty;
            playerJumpActive = true;
        }
        if (explodeButtonPressed && now > playerExplodeCDUntil && now > playerStunUntil) {
            for (int i = 0; i <= NPC_COUNT; i++) playerChainAttacked[i] = false;
            playerChainAttackActive = true;
            playerChainCount = 0;
            playerHP -= 10;
            playerExplodeCDUntil = now + EXPLODE_CD;
            explodeButtonPressed = false;
        }

        cameraX = playerX + blockSize/2 - screenW/2;
        cameraY = playerY + blockSize/2 - screenH/2;
        cameraX = Math.max(0, Math.min(cameraX, mapWidth - screenW));
        cameraY = Math.max(0, Math.min(cameraY, mapHeight - screenH));

        if (playerHP <= 0) {
            running = false;
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
						@Override public void run() { activity.finish(); }
					});
            }
        }
    }

    // Helper functions: getClosestNpcIdx, checkOverlap, dorongUnit (implementasi tetap)

    private int getClosestNpcIdx(float x, float y, boolean[] sudah, int ignoreIdx) {
        float bestDist = Float.MAX_VALUE;
        int bestIdx = -1;
        for (int i = 0; i < NPC_COUNT; i++) {
            if (i == ignoreIdx || sudah[i] || !npcAlive[i]) continue;
            float dx = npcX[i] - x, dy = npcY[i] - y;
            float dist = (float)Math.sqrt(dx*dx + dy*dy);
            if (dist < bestDist && dist < EXPLODE_CHAIN_RANGE) {
                bestDist = dist; bestIdx = i;
            }
        }
        if (!sudah[NPC_COUNT]) {
            float dx = playerX - x, dy = playerY - y;
            float dist = (float)Math.sqrt(dx*dx + dy*dy);
            if (dist < bestDist && dist < EXPLODE_CHAIN_RANGE) return NPC_COUNT;
        }
        return bestIdx;
    }
    private int getClosestNpcIdx(float x, float y, boolean[] sudah) {
        float bestDist = Float.MAX_VALUE;
        int bestIdx = -1;
        for (int i = 0; i < NPC_COUNT; i++) {
            if (sudah[i] || !npcAlive[i]) continue;
            float dx = npcX[i] - x, dy = npcY[i] - y;
            float dist = (float)Math.sqrt(dx*dx + dy*dy);
            if (dist < bestDist && dist < EXPLODE_CHAIN_RANGE) {
                bestDist = dist; bestIdx = i;
            }
        }
        return bestIdx;
    }
    private boolean checkOverlap(float x1, float y1, float x2, float y2) {
        return x1 < x2 + blockSize && x1 + blockSize > x2 &&
			y1 < y2 + blockSize && y1 + blockSize > y2;
    }

    private void drawGame(Canvas canvas) {
        canvas.drawColor(Color.BLACK);

        paint.setColor(Color.DKGRAY);
        int gridGap = blockSize;
        for (int x = 0; x < mapWidth; x += gridGap) {
            for (int y = 0; y < mapHeight; y += gridGap) {
                float gx = x - cameraX, gy = y - cameraY;
                canvas.drawRect(gx, gy, gx + blockSize - 2, gy + blockSize - 2, paint);
            }
        }

        int aliveCount = 0;
        for (int i = 0; i < NPC_COUNT; i++) {
            if (!npcAlive[i]) continue;
            aliveCount++;
            if (System.currentTimeMillis() < npcShadowUntil[i]) {
                paint.setColor(Color.BLACK);
                paint.setStyle(Style.FILL);
                float drawX = npcX[i] - cameraX, drawY = npcY[i] - cameraY;
                canvas.drawRect(drawX+6, drawY+12, drawX+blockSize+6, drawY+blockSize+12, paint);
            }
            paint.setStyle(Style.FILL);
            paint.setColor(npcColors[i % npcColors.length]);
            float drawX = npcX[i] - cameraX, drawY = npcY[i] - cameraY;
            canvas.drawRect(drawX, drawY, drawX + blockSize, drawY + blockSize, paint);
            float hpBarWidth = blockSize * ((float)npcHP[i] / npcMaxHP[i]);
            hpBarWidth = Math.max(0, Math.min(blockSize, hpBarWidth));
            paint.setColor(Color.RED);
            canvas.drawRect(drawX, drawY - 20, drawX + hpBarWidth, drawY - 10, paint);
            if (System.currentTimeMillis() < npcStunUntil[i]) {
                paint.setColor(Color.WHITE);
                canvas.drawRect(drawX, drawY, drawX + blockSize, drawY + blockSize, paint);
            }
        }

        paint.setColor(Color.GREEN);
        float pDrawX = playerX - cameraX, pDrawY = playerY - cameraY;
        if (System.currentTimeMillis() < playerShadowUntil) {
            paint.setColor(Color.BLACK);
            canvas.drawRect(pDrawX+6, pDrawY+12, pDrawX + blockSize+6, pDrawY + blockSize+12, paint);
        }
        paint.setColor(Color.GREEN);
        canvas.drawRect(pDrawX, pDrawY, pDrawX + blockSize, pDrawY + blockSize, paint);
        float playerHpBarWidth = blockSize * ((float)playerHP / playerMaxHP);
        playerHpBarWidth = Math.max(0, Math.min(blockSize, playerHpBarWidth));
        paint.setColor(Color.RED);
        canvas.drawRect(pDrawX, pDrawY - 20, pDrawX + playerHpBarWidth, pDrawY - 10, paint);
        if (System.currentTimeMillis() < playerStunUntil) {
            paint.setColor(Color.WHITE);
            canvas.drawRect(pDrawX, pDrawY, pDrawX + blockSize, pDrawY + blockSize, paint);
        }

        // Draw missile player
        if (missileActive) {
            paint.setColor(Color.rgb(180,220,255));
            canvas.drawRect(missileX - 18 - cameraX, missileY - 8 - cameraY, missileX + 18 - cameraX, missileY + 8 - cameraY, paint);
        }
        // Draw missile NPC
        for (int i = 0; i < NPC_COUNT; i++) {
            if (npcMissileActive[i]) {
                paint.setColor(Color.rgb(255,180,60));
                canvas.drawRect(npcMissileX[i] - 18 - cameraX, npcMissileY[i] - 8 - cameraY, npcMissileX[i] + 18 - cameraX, npcMissileY[i] + 8 - cameraY, paint);
            }
        }

        paint.setColor(Color.WHITE);
        paint.setTextSize(44);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("NPC Alive: " + aliveCount, 32, 56, paint);

        drawButtons(canvas);
    }

    private void drawButtons(Canvas canvas) {
        long now = System.currentTimeMillis();
        boolean skillReady = now > playerSkillCDUntil;
        paint.setColor(skillReady ? Color.rgb(80,200,255) : Color.DKGRAY);
        canvas.drawCircle(skillBtnX, skillBtnY, skillBtnR, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(30);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("CHARGE", skillBtnX, skillBtnY+10, paint);
        if (!skillReady) {
            long msLeft = playerSkillCDUntil - now;
            int detik = (int)(msLeft / 1000) + 1;
            paint.setTextSize(22);
            paint.setColor(Color.YELLOW);
            canvas.drawText("" + detik + "s", skillBtnX, skillBtnY + 38, paint);
        }
        boolean jumpReady = now > playerJumpCDUntil;
        paint.setColor(jumpReady ? Color.rgb(255,210,80) : Color.DKGRAY);
        canvas.drawCircle(jumpBtnX, jumpBtnY, jumpBtnR, paint);
        paint.setColor(Color.BLACK);
        paint.setTextSize(26);
        canvas.drawText("LOMPAT", jumpBtnX, jumpBtnY+10, paint);
        if (!jumpReady) {
            long msLeft = playerJumpCDUntil - now;
            int detik = (int)(msLeft / 1000) + 1;
            paint.setTextSize(20);
            paint.setColor(Color.YELLOW);
            canvas.drawText("" + detik + "s", jumpBtnX, jumpBtnY + 34, paint);
        }
        boolean explodeReady = now > playerExplodeCDUntil;
        paint.setColor(explodeReady ? Color.rgb(255,80,100) : Color.DKGRAY);
        canvas.drawCircle(explodeBtnX, explodeBtnY, explodeBtnR, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(20);
        canvas.drawText("CHAIN", explodeBtnX, explodeBtnY+7, paint);
        if (!explodeReady) {
            long msLeft = playerExplodeCDUntil - now;
            int detik = (int)(msLeft / 1000) + 1;
            paint.setTextSize(16);
            paint.setColor(Color.YELLOW);
            canvas.drawText("" + detik + "s", explodeBtnX, explodeBtnY + 24, paint);
        }
        boolean missileReady = now > playerMissileCDUntil;
        paint.setColor(missileReady ? Color.rgb(140,180,255) : Color.DKGRAY);
        canvas.drawCircle(missileBtnX, missileBtnY, missileBtnR, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(17);
        canvas.drawText("MISSILE", missileBtnX, missileBtnY+7, paint);
        if (!missileReady) {
            long msLeft = playerMissileCDUntil - now;
            int detik = (int)(msLeft / 1000) + 1;
            paint.setTextSize(14);
            paint.setColor(Color.YELLOW);
            canvas.drawText("" + detik + "s", missileBtnX, missileBtnY + 24, paint);
        }
        paint.setColor(Color.GRAY);
        float bx = 100, by = getHeight() - 300;
        canvas.drawRect(bx, by, bx+100, by+100, paint);
        canvas.drawRect(bx, by+200, bx+100, by+100+200, paint);
        canvas.drawRect(bx-100, by+100, bx, by+200, paint);
        canvas.drawRect(bx+100, by+100, bx+200, by+200, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX(), y = event.getY();
        float bx = 100, by = getHeight() - 300;
        boolean pressed = event.getAction() != MotionEvent.ACTION_UP;

        upPressed = pressed && x > bx && x < bx+100 && y > by && y < by+100;
        downPressed = pressed && x > bx && x < bx+100 && y > by+200 && y < by+300;
        leftPressed = pressed && x > bx-100 && x < bx && y > by+100 && y < by+200;
        rightPressed = pressed && x > bx+100 && x < bx+200 && y > by+100 && y < by+200;

        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            float dx = x - skillBtnX, dy = y - skillBtnY;
            skillButtonPressed = (dx*dx + dy*dy <= skillBtnR*skillBtnR);

            float dx2 = x - jumpBtnX, dy2 = y - jumpBtnY;
            jumpButtonPressed = (dx2*dx2 + dy2*dy2 <= jumpBtnR*jumpBtnR);

            float dx3 = x - explodeBtnX, dy3 = y - explodeBtnY;
            explodeButtonPressed = (dx3*dx3 + dy3*dy3 <= explodeBtnR*explodeBtnR);

            float dx4 = x - missileBtnX, dy4 = y - missileBtnY;
            missileButtonPressed = (dx4*dx4 + dy4*dy4 <= missileBtnR*missileBtnR);
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            skillButtonPressed = false;
            jumpButtonPressed = false;
            explodeButtonPressed = false;
            missileButtonPressed = false;
        }
        return true;
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        try { thread.join(); } catch (InterruptedException e) {}
    }
}
