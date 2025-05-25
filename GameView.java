package com.example.rpgblock;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private Thread thread;
    private boolean running = false;

    // Map
    private final int mapWidth = 2000;
    private final int mapHeight = 2000;

    // Kamera (viewport)
    private float cameraX = 0;
    private float cameraY = 0;
    private int screenW, screenH;

    // Player
    private float playerX = 100, playerY = 100;
    private final int blockSize = 100;
    private final Paint paint = new Paint();
    private boolean upPressed, downPressed, leftPressed, rightPressed;
    private float playerKnockbackX = 0, playerKnockbackY = 0;
    private int playerHP = 200;
    private int playerMaxHP = 200;
    private long playerLastHitTime = 0;
    private long playerStunUntil = 0;

    // Player skill charge
    private boolean playerSkillActive = false;
    private float playerSkillDirX = 0, playerSkillDirY = 0;
    private int playerSkillTarget = -1;
    private long playerSkillCDUntil = 0;

    // Player skill jump
    private boolean playerJumpActive = false;
    private float playerJumpTargetX = 0, playerJumpTargetY = 0;
    private long playerJumpCDUntil = 0;
    private static final long JUMP_CD = 60000; // 60 detik
    private static final float JUMP_RANGE = 650f;
    private static final float JUMP_AOE = 5 * 100f; // 5 blok, 500px
    private static final long JUMP_STUN = 2000; // 2 detik

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

    // NPC charge skill
    private boolean[] npcSkillActive = new boolean[NPC_COUNT];
    private float[] npcSkillDirX = new float[NPC_COUNT];
    private float[] npcSkillDirY = new float[NPC_COUNT];
    private long[] npcSkillCDUntil = new long[NPC_COUNT];
    private int[] npcSkillTarget = new int[NPC_COUNT];

    // NPC jump skill
    private boolean[] npcJumpActive = new boolean[NPC_COUNT];
    private float[] npcJumpTargetX = new float[NPC_COUNT];
    private float[] npcJumpTargetY = new float[NPC_COUNT];
    private long[] npcJumpCDUntil = new long[NPC_COUNT];

    // Skill & stun constants
    private static final long STUN_TIME = 700; // ms, stun biasa
    private static final long SKILL_CD = 30000; // ms, CD skill charge player & npc = 30 detik
    private static final float CHARGE_RANGE = 350f;
    private static final float CHARGE_SPEED = 60f;
    private static final long HIT_COOLDOWN = 300; // ms

    private final Random random = new Random();

    // Tombol skill player (posisi & status)
    private boolean skillButtonPressed = false;
    private float skillBtnX, skillBtnY, skillBtnR;

    // Tombol skill jump
    private boolean jumpButtonPressed = false;
    private float jumpBtnX, jumpBtnY, jumpBtnR;

    // Activity reference for finishing app on HP=0
    private Activity activity;

    public GameView(Context context) {
        super(context);
        if (context instanceof Activity) activity = (Activity) context;
        getHolder().addCallback(this);
        setFocusable(true);

        // Inisialisasi NPC
        for (int i = 0; i < NPC_COUNT; i++) {
            npcX[i] = 300 + (i % 5) * 300;
            npcY[i] = 300 + (i / 5) * 300;
            npcHP[i] = 100;
            npcMaxHP[i] = 100;
            npcAlive[i] = true;
            npcSkillTarget[i] = -1;
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
                        skillBtnR = 100;
                        skillBtnX = screenW - skillBtnR - 40;
                        skillBtnY = screenH - skillBtnR - 40;
                        jumpBtnR = 100;
                        jumpBtnX = screenW - jumpBtnR - 40 - 240;
                        jumpBtnY = screenH - jumpBtnR - 40;
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

		// PLAYER: Movement, stun, skill charge
		if (now < playerStunUntil) {
			// Player stun, tidak bisa bergerak
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
			// Efek lompat: stun, terpental, dan -15 HP untuk semua unit dalam 5 blok dari titik lompat (termasuk player)
			for (int i = 0; i < NPC_COUNT; i++) {
				if (!npcAlive[i]) continue;
				float dx = npcX[i] - playerX, dy = npcY[i] - playerY;
				float dist = (float)Math.sqrt(dx*dx + dy*dy);
				if (dist <= JUMP_AOE) {
					npcHP[i] -= 15;
					npcStunUntil[i] = now + JUMP_STUN;
					// Knockback/terpental ke luar dari pusat lompat
					float kx = dx / (dist == 0 ? 1 : dist);
					float ky = dy / (dist == 0 ? 1 : dist);
					float strength = 60;
					npcKnockbackX[i] += kx * strength;
					npcKnockbackY[i] += ky * strength;
					if (npcHP[i] <= 0) npcAlive[i] = false;
				}
			}
			// Player sendiri juga kena jika ada unit lain sangat dekat (opsional, sesuai permintaan)
			// Kalau mau player juga stun, hp, dan terpental jika dia sendiri dalam radius
			// playerHP -= 15; playerStunUntil = now + JUMP_STUN;
			playerJumpCDUntil = now + JUMP_CD;
			playerJumpActive = false;
		} else {
			if (upPressed)   { playerY -= speed; playerMoving = true; }
			if (downPressed) { playerY += speed; playerMoving = true; }
			if (leftPressed) { playerX -= speed; playerMoving = true; }
			if (rightPressed){ playerX += speed; playerMoving = true; }
		}

		playerX = Math.max(0, Math.min(playerX, mapWidth - blockSize));
		playerY = Math.max(0, Math.min(playerY, mapHeight - blockSize));

		// --- NPC: Movement, stun, skills, and mutual hunting ---
		for (int i = 0; i < NPC_COUNT; i++) {
			if (!npcAlive[i]) continue;
			// Jump skill
			if (npcJumpActive[i]) {
				npcX[i] = npcJumpTargetX[i];
				npcY[i] = npcJumpTargetY[i];
				// Efek lompat: stun, terpental, dan -15 HP untuk semua unit dalam 5 blok (termasuk player dan NPC lain, kecuali pengguna skill)
				// Player
				float dx = playerX - npcX[i], dy = playerY - npcY[i];
				float dist = (float)Math.sqrt(dx*dx + dy*dy);
				if (dist <= JUMP_AOE) {
					playerHP -= 15;
					playerStunUntil = now + JUMP_STUN;
					float kx = dx / (dist == 0 ? 1 : dist);
					float ky = dy / (dist == 0 ? 1 : dist);
					float strength = 60;
					playerKnockbackX += kx * strength;
					playerKnockbackY += ky * strength;
				}
				// NPC lain
				for (int j = 0; j < NPC_COUNT; j++) {
					if (j == i || !npcAlive[j]) continue;
					float dx2 = npcX[j] - npcX[i], dy2 = npcY[j] - npcY[i];
					float dist2 = (float)Math.sqrt(dx2*dx2 + dy2*dy2);
					if (dist2 <= JUMP_AOE) {
						npcHP[j] -= 15;
						npcStunUntil[j] = now + JUMP_STUN;
						float kx = dx2 / (dist2 == 0 ? 1 : dist2);
						float ky = dy2 / (dist2 == 0 ? 1 : dist2);
						float strength = 60;
						npcKnockbackX[j] += kx * strength;
						npcKnockbackY[j] += ky * strength;
						if (npcHP[j] <= 0) npcAlive[j] = false;
					}
				}
				npcJumpCDUntil[i] = now + JUMP_CD;
				npcJumpActive[i] = false;
				continue;
			}
            if (now < npcStunUntil[i]) {
                npcDx[i] = 0; npcDy[i] = 0;
            } else if (npcSkillActive[i]) {
                npcX[i] += npcSkillDirX[i] * CHARGE_SPEED;
                npcY[i] += npcSkillDirY[i] * CHARGE_SPEED;
                // Charge hit player
                if (checkOverlap(playerX, playerY, npcX[i], npcY[i])) {
                    playerHP -= 3;
                    playerStunUntil = now + STUN_TIME;
                    npcSkillCDUntil[i] = now + SKILL_CD;
                    npcSkillActive[i] = false;
                }
                // Charge hit NPC lain
                for (int j = 0; j < NPC_COUNT; j++) {
                    if (j == i || !npcAlive[j]) continue;
                    if (checkOverlap(npcX[i], npcY[i], npcX[j], npcY[j])) {
                        npcHP[j] -= 3;
                        npcStunUntil[j] = now + STUN_TIME;
                        if (npcHP[j] <= 0) npcAlive[j] = false;
                        npcSkillCDUntil[i] = now + SKILL_CD;
                        npcSkillActive[i] = false;
                    }
                }
            } else {
                // AI: cari target terdekat (player atau NPC lain)
                float bestDist = Float.MAX_VALUE;
                float tx = -1, ty = -1;
                boolean isPlayer = false;
                // Cari player
                float dxp = playerX - npcX[i], dyp = playerY - npcY[i];
                float distp = (float)Math.sqrt(dxp*dxp + dyp*dyp);
                if (distp < bestDist && playerHP > 0) {
                    bestDist = distp; tx = playerX; ty = playerY; isPlayer = true;
                }
                // Cari NPC lain
                for (int j = 0; j < NPC_COUNT; j++) {
                    if (j == i || !npcAlive[j]) continue;
                    float dxn = npcX[j] - npcX[i], dyn = npcY[j] - npcY[i];
                    float distn = (float)Math.sqrt(dxn*dxn + dyn*dyn);
                    if (distn < bestDist) {
                        bestDist = distn; tx = npcX[j]; ty = npcY[j]; isPlayer = false;
                    }
                }
                // Kejar target terdekat
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

        // --- DORONG & HP antar unit ---
        for (int i = 0; i < NPC_COUNT; i++) {
            if (!npcAlive[i]) continue;
            // NPC-NPC dorong & HP (pakai cooldown per NPC)
            for (int j = i + 1; j < NPC_COUNT; j++) {
                if (!npcAlive[j]) continue;
                if (checkOverlap(npcX[i], npcY[i], npcX[j], npcY[j])) {
                    dorongUnit(i, j, npcX, npcY, npcKnockbackX, npcKnockbackY);
                    boolean aMoving = (npcDx[i] != 0 || npcDy[i] != 0);
                    boolean bMoving = (npcDx[j] != 0 || npcDy[j] != 0);
                    if (now - npcLastHitTime[j] >= HIT_COOLDOWN && aMoving && !bMoving) {
                        npcHP[j]--;
                        npcLastHitTime[j] = now;
                        if (npcHP[j] <= 0) npcAlive[j] = false;
                        npcStunUntil[j] = now + STUN_TIME;
                    }
                    else if (now - npcLastHitTime[i] >= HIT_COOLDOWN && !aMoving && bMoving) {
                        npcHP[i]--;
                        npcLastHitTime[i] = now;
                        if (npcHP[i] <= 0) npcAlive[i] = false;
                        npcStunUntil[i] = now + STUN_TIME;
                    }
                }
            }
            // Player <-> NPC
            if (checkOverlap(playerX, playerY, npcX[i], npcY[i])) {
                boolean npcMoving = (npcDx[i] != 0 || npcDy[i] != 0);
                if (now - playerLastHitTime >= HIT_COOLDOWN && !playerMoving && npcMoving && !playerSkillActive) {
                    playerHP--;
                    playerLastHitTime = now;
                    playerStunUntil = now + STUN_TIME;
                } else if (now - npcLastHitTime[i] >= HIT_COOLDOWN && playerMoving && !npcMoving && !npcSkillActive[i]) {
                    npcHP[i]--;
                    npcLastHitTime[i] = now;
                    if (npcHP[i] <= 0) npcAlive[i] = false;
                    npcStunUntil[i] = now + STUN_TIME;
                }
                float dx = playerX - npcX[i];
                float dy = playerY - npcY[i];
                float dist = (float)Math.sqrt(dx*dx+dy*dy);
                if (dist == 0) dist = 1;
                float nx = dx / dist, ny = dy / dist;
                float strength = 20;
                playerKnockbackX += nx * strength;
                playerKnockbackY += ny * strength;
                npcKnockbackX[i] -= nx * strength;
                npcKnockbackY[i] -= ny * strength;
            }
        }

        playerX += playerKnockbackX; playerY += playerKnockbackY;
        playerKnockbackX *= 0.8f; playerKnockbackY *= 0.8f;
        if (Math.abs(playerKnockbackX) < 0.1f) playerKnockbackX = 0;
        if (Math.abs(playerKnockbackY) < 0.1f) playerKnockbackY = 0;

        for (int i = 0; i < NPC_COUNT; i++) {
            if (!npcAlive[i]) continue;
            npcX[i] += npcKnockbackX[i]; npcY[i] += npcKnockbackY[i];
            npcKnockbackX[i] *= 0.8f; npcKnockbackY[i] *= 0.8f;
            if (Math.abs(npcKnockbackX[i]) < 0.1f) npcKnockbackX[i] = 0;
            if (Math.abs(npcKnockbackY[i]) < 0.1f) npcKnockbackY[i] = 0;
        }

        // Aktifkan skill charge player jika tombol ditekan, tidak sedang stun, tidak sedang charge, tidak cooldown
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

        // Aktifkan skill jump player jika tombol jump ditekan
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

        // Aktifkan skill NPC: charge/jump otomatis
        for (int i = 0; i < NPC_COUNT; i++) {
            if (!npcAlive[i] || now < npcSkillCDUntil[i] || npcSkillActive[i] || now < npcStunUntil[i] || npcJumpActive[i]) continue;
            // Cari target terdekat (player/NPC lain)
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
                if (distn < bestDist) {
                    bestDist = distn; tx = npcX[j]; ty = npcY[j]; isPlayer = false;
                }
            }
            float dx = tx - npcX[i], dy = ty - npcY[i];
            // Prioritas jump jika dalam range
            if (now > npcJumpCDUntil[i] && bestDist < JUMP_RANGE && random.nextFloat() < 0.01f) {
                npcJumpTargetX[i] = tx;
                npcJumpTargetY[i] = ty;
                npcJumpActive[i] = true;
            } else if (bestDist < CHARGE_RANGE) {
                float dist = (float)Math.sqrt(dx*dx + dy*dy);
                if (dist > 0) {
                    npcSkillDirX[i] = dx / dist;
                    npcSkillDirY[i] = dy / dist;
                    npcSkillActive[i] = true;
                    npcSkillTarget[i] = isPlayer ? -1 : getNpcIndexByPosition(tx, ty);
                }
            }
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

    private int getNpcIndexByPosition(float x, float y) {
        for (int i = 0; i < NPC_COUNT; i++) {
            if (Math.abs(npcX[i] - x) < 0.1f && Math.abs(npcY[i] - y) < 0.1f) return i;
        }
        return -1;
    }

    private boolean checkOverlap(float x1, float y1, float x2, float y2) {
        return x1 < x2 + blockSize && x1 + blockSize > x2 &&
            y1 < y2 + blockSize && y1 + blockSize > y2;
    }

    private void dorongUnit(int i, int j, float[] xs, float[] ys, float[] kx, float[] ky) {
        float dx = xs[i] - xs[j];
        float dy = ys[i] - ys[j];
        float dist = (float)Math.sqrt(dx*dx+dy*dy);
        if (dist == 0) dist = 1;
        float nx = dx / dist, ny = dy / dist;
        float strength = 20;
        kx[i] += nx * strength;
        ky[i] += ny * strength;
        kx[j] -= nx * strength;
        ky[j] -= ny * strength;
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
        canvas.drawRect(pDrawX, pDrawY, pDrawX + blockSize, pDrawY + blockSize, paint);
        float playerHpBarWidth = blockSize * ((float)playerHP / playerMaxHP);
        playerHpBarWidth = Math.max(0, Math.min(blockSize, playerHpBarWidth));
        paint.setColor(Color.RED);
        canvas.drawRect(pDrawX, pDrawY - 20, pDrawX + playerHpBarWidth, pDrawY - 10, paint);
        if (System.currentTimeMillis() < playerStunUntil) {
            paint.setColor(Color.WHITE);
            canvas.drawRect(pDrawX, pDrawY, pDrawX + blockSize, pDrawY + blockSize, paint);
        }

        paint.setColor(Color.WHITE);
        paint.setTextSize(48);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("NPC Alive: " + aliveCount, 32, 56, paint);

        drawButtons(canvas);
    }

    private void drawButtons(Canvas canvas) {
        paint.setColor(Color.GRAY);
        float bx = 100, by = getHeight() - 300;
        canvas.drawRect(bx, by, bx+100, by+100, paint);
        canvas.drawRect(bx, by+200, bx+100, by+100+200, paint);
        canvas.drawRect(bx-100, by+100, bx, by+200, paint);
        canvas.drawRect(bx+100, by+100, bx+200, by+200, paint);

        long now = System.currentTimeMillis();
        boolean skillReady = now > playerSkillCDUntil;
        paint.setColor(skillReady ? Color.rgb(80,200,255) : Color.DKGRAY);
        canvas.drawCircle(skillBtnX, skillBtnY, skillBtnR, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(40);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("SKILL", skillBtnX, skillBtnY+15, paint);
        if (!skillReady) {
            long msLeft = playerSkillCDUntil - now;
            int detik = (int)(msLeft / 1000) + 1;
            paint.setTextSize(36);
            paint.setColor(Color.YELLOW);
            canvas.drawText("" + detik + "s", skillBtnX, skillBtnY + 60, paint);
        }

        boolean jumpReady = now > playerJumpCDUntil;
        paint.setColor(jumpReady ? Color.rgb(255,210,80) : Color.DKGRAY);
        canvas.drawCircle(jumpBtnX, jumpBtnY, jumpBtnR, paint);
        paint.setColor(Color.BLACK);
        paint.setTextSize(38);
        canvas.drawText("LOMPAT", jumpBtnX, jumpBtnY+15, paint);
        if (!jumpReady) {
            long msLeft = playerJumpCDUntil - now;
            int detik = (int)(msLeft / 1000) + 1;
            paint.setTextSize(36);
            paint.setColor(Color.YELLOW);
            canvas.drawText("" + detik + "s", jumpBtnX, jumpBtnY + 60, paint);
        }
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
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            skillButtonPressed = false;
            jumpButtonPressed = false;
        }
        return true;
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        try { thread.join(); } catch (InterruptedException e) {}
    }
}
