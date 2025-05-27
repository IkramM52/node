package com.example.nolambdagame;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.os.Handler;
import android.view.*;
import android.widget.Toast;
import java.util.*;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GameView gv = new GameView(this);
        setContentView(gv);
    }

    // ==================== GAME VIEW ====================
    public static class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

        // ========== FINAL CONFIG ==========
        public static final int PLAYER_HP_MAX = 550;
        public static final int NPC_HP_MAX = 85;
        public static final int NPC_COUNT = 30;
        public static final int PLAYER_SPEED = 10;
        public static final int NPC_SPEED = 6;
        public static final int BULLET_SPEED = 17;
        public static final int NPC_CD = 700;
        public static final int PLAYER_CD = 250;
        public static final int BULLET_DMG = 13;
        public static final int NPC_ATTACK_RANGE = 300;
        public static final int TURRET_CD = 800;
        public static final int TURRET_RANGE = 270;
        public static final int TURRET_DMG = 18;
        public static final int TURRET_HP = 100;
        public static final int BASE_SIZE_INIT = 110; // kecil
        public static final int BASE_SIZE_MAX = 210;
        public static final int BASE_GROWTH = 1;
        public static final int BASE_GROWTH_INTERVAL = 1200;
        public static final int MAP_W = 2700;
        public static final int MAP_H = 2200;
        public static final int PLAYER_ACCURACY = 92; // 0-100
        public static final int NPC_ACCURACY = 76; // 0-100
        public static final int NPC_TERRITORY_CD = 10000; // 10 detik
        public static final int NPC_MIN_SEPARATION = 70;

        // Tambahan untuk akurasi lempar wilayah
        public static final int NPC_TERRITORY_THROW_ACCURACY = 25; // 0-100, makin kecil makin sering meleset

        Player player;
        ArrayList<Unit> npcs = new ArrayList<>();
        ArrayList<Bullet> bullets = new ArrayList<>();
        ArrayList<Territory> territories = new ArrayList<>();
        boolean gameOver = false;
        String gameOverText = "";
        int width, height;
        float stickCX, stickCY, stickX, stickY, stickBaseR, stickR;
        boolean moving = false;
        Handler handler = new Handler();
        Thread gameThread;
        SurfaceHolder holder;
        long lastFrame;

        // Buttons
        Rect atkBtn = new Rect();
        Paint atkBtnPaint = new Paint();
        Paint atkBtnText = new Paint();

        int camX = 0, camY = 0;
        Random rand = new Random();

        public GameView(Context ctx) {
            super(ctx);
            holder = getHolder();
            holder.addCallback(this);
            setFocusable(true);
            atkBtnPaint.setColor(Color.argb(180, 255, 60, 60));
            atkBtnText.setColor(Color.WHITE);
            atkBtnText.setTextSize(32f);
            atkBtnText.setTextAlign(Paint.Align.CENTER);
        }

        void newGame() {
            npcs.clear();
            bullets.clear();
            territories.clear();
            int midY = MAP_H / 2;
            int midX = MAP_W / 2;
            for (int i = 0; i < NPC_COUNT; i++) {
                int t = (i % 2) + 1;
                int px = t == 1 ? 400 : MAP_W - 400;
                int py = midY - 300 + i * 100;
                npcs.add(new Unit(px, py, t));
            }
            player = new Player(midX, midY, 0);

            for (Unit npc : npcs) {
                npc.territoryCD = 0;
                throwTerritoryLowAccuracy(npc, player.x, player.y); // awal lempar, tetap random
            }
            gameOver = false;
            gameOverText = "";
        }

        // Lempar wilayah dengan akurasi rendah dan area kecil
        void throwTerritoryLowAccuracy(Unit npc, int tx, int ty) {
            // Hapus territory lama punya npc ini
            for (int i = territories.size() - 1; i >= 0; i--) {
                Territory ter = territories.get(i);
                if (ter.owner == npc) {
                    territories.remove(i);
                }
            }
            // Hitung apakah lemparan meleset
            boolean miss = rand.nextInt(100) >= NPC_TERRITORY_THROW_ACCURACY;
            int missRange = 440; // seberapa jauh bisa meleset
            int ox = 0, oy = 0;
            if (miss) {
                // Melenceng banyak (acak 220..440 ke segala arah)
                double a = rand.nextDouble() * Math.PI * 2.0;
                int r = 220 + rand.nextInt(missRange - 220);
                ox = (int) (Math.cos(a) * r);
                oy = (int) (Math.sin(a) * r);
            } else {
                // Masih tetap random sedikit meski tepat
                ox = rand.nextInt(60) - 30;
                oy = rand.nextInt(60) - 30;
            }
            int fx = tx + ox;
            int fy = ty + oy;
            // Clamp ke area map
            if (fx < 0) fx = 0;
            if (fx > MAP_W) fx = MAP_W;
            if (fy < 0) fy = 0;
            if (fy > MAP_H) fy = MAP_H;

            Territory terBaru = new Territory(fx, fy, npc.team, npc);
            terBaru.size = BASE_SIZE_INIT + rand.nextInt(18); // kecil
            terBaru.turret = new Turret(fx, fy, npc.team);
            territories.add(terBaru);
        }

        @Override
        public void run() {
            while (!gameOver && holder.getSurface().isValid()) {
                long now = System.currentTimeMillis();
                long dt = now - lastFrame;
                if (dt < 16) {
                    try {
                        Thread.sleep(16 - dt);
                    } catch (Exception e) {}
                }
                lastFrame = now;
                updateGame(dt);
                drawGame();
            }
        }

        void updateGame(long dt) {
            if (!gameOver) {
                Unit closestNpc = null;
                double minDist = 999999;
                for (int i = 0; i < npcs.size(); i++) {
                    Unit u = npcs.get(i);
                    if (u.hp > 0) {
                        double d = Math.hypot(u.x - player.x, u.y - player.y);
                        if (d < minDist) {
                            minDist = d;
                            closestNpc = u;
                        }
                    }
                }
                if (!moving && closestNpc != null) {
                    int dxAuto = closestNpc.x - player.x;
                    int dyAuto = closestNpc.y - player.y;
                    double l = Math.sqrt(dxAuto * dxAuto + dyAuto * dyAuto);
                    if (l > 0) {
                        float dxf = (float) (dxAuto / l), dyf = (float) (dyAuto / l);
                        player.move(dxf, dyf);
                    }
                } else {
                    player.move(dx, dy);
                }
                player.updateCooldown(dt);
                camX = player.x - width / 2;
                camY = player.y - height / 2;
                if (closestNpc != null && minDist < PLAYER_ATTACK_RANGE() && player.attackCD <= 0) {
                    if (rand.nextInt(100) < PLAYER_ACCURACY) {
                        double tx = closestNpc.x - player.x;
                        double ty = closestNpc.y - player.y;
                        double l = Math.sqrt(tx * tx + ty * ty);
                        float ddx = (float) (tx / l), ddy = (float) (ty / l);
                        bullets.add(new Bullet(player.x, player.y, ddx, ddy, 0, player));
                    }
                    player.attackCD = PLAYER_CD;
                }
            }

            boolean foundPlayer = false;
            for (int i = 0; i < npcs.size(); i++) {
                Unit u = npcs.get(i);
                if (u.hp > 0 && player.hp > 0 && u.team != player.team) {
                    double d = Math.hypot(player.x - u.x, player.y - u.y);
                    if (d < 500) {
                        foundPlayer = true;
                        break;
                    }
                }
            }

            for (int i = 0; i < npcs.size(); i++) {
                Unit u = npcs.get(i);
                if (u.hp <= 0) continue;

                // --- Lempar Territory Akurasi Rendah
                u.territoryCD -= dt;
                if (u.territoryCD <= 0) {
                    // Cari target: player, npc lawan, turret lawan
                    int tx = player.x, ty = player.y;
                    int bestDist = (int)Math.hypot(player.x-u.x, player.y-u.y);
                    if (player.hp <= 0) {
                        for (int j=0;j<npcs.size();j++) {
                            Unit v = npcs.get(j);
                            if (v != u && v.team != u.team && v.hp > 0) {
                                int d = (int)Math.hypot(v.x-u.x, v.y-u.y);
                                if (d < bestDist) {
                                    bestDist = d; tx = v.x; ty = v.y;
                                }
                            }
                        }
                        for (int j=0;j<territories.size();j++) {
                            Territory t = territories.get(j);
                            if (t.turret != null && t.team != u.team && t.turret.hp > 0) {
                                int d = (int)Math.hypot(t.turret.x-u.x, t.turret.y-u.y);
                                if (d < bestDist) {
                                    bestDist = d; tx = t.turret.x; ty = t.turret.y;
                                }
                            }
                        }
                    }
                    throwTerritoryLowAccuracy(u, tx, ty);
                    u.territoryCD = NPC_TERRITORY_CD;
                }

                Entity target = null;
                double minDist = 999999;
                if (foundPlayer && player.hp > 0 && u.team != player.team) {
                    target = player;
                    minDist = Math.hypot(player.x - u.x, player.y - u.y);

                    double angleToPlayer = Math.atan2(player.y - u.y, player.x - u.x);
                    double targetX = player.x - Math.cos(angleToPlayer) * 150;
                    double targetY = player.y - Math.sin(angleToPlayer) * 150;

                    double separationX = 0, separationY = 0;
                    int count = 0;
                    for (int j = 0; j < npcs.size(); j++) {
                        if (i == j) continue;
                        Unit other = npcs.get(j);
                        if (other.hp > 0 && other.team == u.team) {
                            double dist = Math.hypot(u.x - other.x, u.y - other.y);
                            if (dist < NPC_MIN_SEPARATION) {
                                separationX += (u.x - other.x);
                                separationY += (u.y - other.y);
                                count++;
                            }
                        }
                    }
                    double sepLength = Math.sqrt(separationX * separationX + separationY * separationY);
                    float sepDX = 0, sepDY = 0;
                    if (count > 0 && sepLength > 0.1) {
                        sepDX = (float) (separationX / sepLength * 0.7);
                        sepDY = (float) (separationY / sepLength * 0.7);
                    }
                    int dx = (int) (targetX - u.x + sepDX * 20);
                    int dy = (int) (targetY - u.y + sepDY * 20);
                    double l = Math.sqrt(dx * dx + dy * dy);
                    if (l > 2) {
                        u.x += (int) (dx / l * NPC_SPEED);
                        u.y += (int) (dy / l * NPC_SPEED);
                    }
                } else {
                    if (player.hp > 0 && u.team != player.team) {
                        double d = Math.hypot(player.x - u.x, player.y - u.y);
                        if (d < minDist) {
                            minDist = d;
                            target = player;
                        }
                    }
                    for (int j = 0; j < npcs.size(); j++) {
                        Unit v = npcs.get(j);
                        if (v == u) continue;
                        if (v.team != u.team && v.hp > 0) {
                            double d = Math.hypot(v.x - u.x, v.y - u.y);
                            if (d < minDist) {
                                minDist = d;
                                target = v;
                            }
                        }
                    }
                    for (int j = 0; j < territories.size(); j++) {
                        Territory t = territories.get(j);
                        if (t.turret != null && t.team != u.team && t.turret.hp > 0) {
                            double d = Math.hypot(t.turret.x - u.x, t.turret.y - u.y);
                            if (d < minDist) {
                                minDist = d;
                                target = t.turret;
                            }
                        }
                    }
                    if (target != null && minDist > 35) {
                        int dx = target.x - u.x, dy = target.y - u.y;
                        double l = Math.sqrt(dx * dx + dy * dy);
                        if (l > 0) {
                            u.x += (int) (dx / l * NPC_SPEED);
                            u.y += (int) (dy / l * NPC_SPEED);
                        }
                    }
                }
                if (target != null && minDist < NPC_ATTACK_RANGE && u.attackCD <= 0) {
                    if (rand.nextInt(100) < NPC_ACCURACY) {
                        double tx = target.x - u.x, ty = target.y - u.y;
                        double l = Math.sqrt(tx * tx + ty * ty);
                        float ddx = (float) (tx / l), ddy = (float) (ty / l);
                        bullets.add(new Bullet(u.x, u.y, ddx, ddy, u.team, u));
                    }
                    u.attackCD = NPC_CD;
                }
                u.updateCooldown(dt);
            }

            for (Territory ter : territories) {
                ter.updateTurret();
                ter.grow(dt);
            }
            for (Territory ter : territories) {
                ter.updateAttack(npcs, player, bullets, territories);
            }

            for (int i = bullets.size() - 1; i >= 0; i--) {
                Bullet b = bullets.get(i);
                b.move();
                if (b.shooter != player && player.hp > 0 &&
					Math.hypot(player.x - b.x, player.y - b.y) < 26) {
                    player.hp -= b.dmg;
                    bullets.remove(i);
                    continue;
                }
                boolean bulletRemoved = false;
                for (int j = 0; j < npcs.size(); j++) {
                    Unit u = npcs.get(j);
                    if (u.hp > 0 && b.shooter != u &&
						Math.hypot(u.x - b.x, u.y - b.y) < 22) {
                        u.hp -= b.dmg;
                        u.bulletAlert(b);
                        bullets.remove(i);
                        bulletRemoved = true;
                        break;
                    }
                }
                if (bulletRemoved) continue;
                for (int j = 0; j < territories.size(); j++) {
                    Territory ter = territories.get(j);
                    if (ter.turret != null && b.shooter != ter.turret &&
						Math.hypot(ter.turret.x - b.x, ter.turret.y - b.y) < 24) {
                        ter.turret.hp -= b.dmg;
                        bullets.remove(i);
                        bulletRemoved = true;
                        break;
                    }
                }
                if (bulletRemoved) continue;
                if (b.x < 0 || b.x > MAP_W || b.y < 0 || b.y > MAP_H) {
                    bullets.remove(i);
                }
            }
            for (int i = npcs.size() - 1; i >= 0; i--)
                if (npcs.get(i).hp <= 0) npcs.remove(i);
            for (int i = 0; i < territories.size(); i++) {
                Territory ter = territories.get(i);
                if (ter.turret != null && ter.turret.hp <= 0) ter.turret = null;
            }
            if (player.hp <= 0) {
                gameOver = true;
                gameOverText = "Player Mati!";
                handler.post(showLose);
            }
            if (npcs.size() == 0) {
                gameOver = true;
                gameOverText = "Semua NPC Kalah!";
                handler.post(showWin);
            }
        }

        void drawGame() {
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c == null) return;
                c.drawColor(Color.rgb(40, 40, 40));
                c.save();
                c.translate(-camX, -camY);

                Paint bg = new Paint();
                bg.setColor(Color.rgb(70, 80, 100));
                c.drawRect(-20, -20, MAP_W + 20, MAP_H + 20, bg);

                for (Territory ter : territories) ter.draw(c);
                for (Unit u : npcs) u.draw(c);
                player.draw(c);
                for (Bullet b : bullets) b.draw(c);

                c.restore();
                drawUI(c);
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }
        }

        void drawUI(Canvas c) {
            Paint t = new Paint();
            t.setColor(Color.WHITE);
            t.setTextSize(36f);
            c.drawText("Player HP: " + player.hp, 30, 50, t);
            c.drawText("NPC: " + npcs.size(), width - 260, 50, t);
            if (gameOver) {
                Paint p = new Paint();
                p.setColor(Color.YELLOW);
                p.setTextSize(60f);
                c.drawText(gameOverText, width / 2 - 180, height / 2, p);
            }
            Paint s = new Paint();
            s.setColor(Color.argb(120, 200, 200, 200));
            c.drawCircle(stickCX, stickCY, stickBaseR, s);
            s.setColor(Color.argb(200, 255, 255, 255));
            c.drawCircle(stickX, stickY, stickR, s);
            c.drawRect(atkBtn, atkBtnPaint);
            c.drawText("ATK", atkBtn.centerX(), atkBtn.centerY() + 12, atkBtnText);
        }

        float dx = 0, dy = 0;

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int pointerCount = e.getPointerCount();
            for (int i = 0; i < pointerCount; i++) {
                float x = e.getX(i), y = e.getY(i);
                if (x < width / 2) {
                    if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                        stickX = stickCX;
                        stickY = stickCY;
                        dx = dy = 0;
                        moving = false;
                    } else {
                        float vx = x - stickCX, vy = y - stickCY;
                        float l = (float) Math.sqrt(vx * vx + vy * vy);
                        if (l > stickBaseR) {
                            vx = vx / l * stickBaseR;
                            vy = vy / l * stickBaseR;
                        }
                        stickX = stickCX + vx;
                        stickY = stickCY + vy;
                        dx = vx / stickBaseR;
                        dy = vy / stickBaseR;
                        moving = l > 10;
                    }
                } else {
                    if (atkBtn.contains((int) x, (int) y) && e.getActionMasked() == MotionEvent.ACTION_DOWN)
                        player.tryAttack = true;
                    if (e.getActionMasked() == MotionEvent.ACTION_UP)
                        player.tryAttack = false;
                }
            }
            return true;
        }

        Runnable showWin = new Runnable() {
            public void run() {
                Toast.makeText(getContext(), "Kamu Menang!", Toast.LENGTH_LONG).show();
            }
        };
        Runnable showLose = new Runnable() {
            public void run() {
                Toast.makeText(getContext(), "Kamu Kalah!", Toast.LENGTH_LONG).show();
            }
        };

        @Override
        public void surfaceCreated(SurfaceHolder s) {
            width = getWidth();
            height = getHeight();
            stickBaseR = width / 8;
            stickR = stickBaseR / 2.5f;
            stickCX = stickBaseR + 24;
            stickCY = height - stickBaseR - 24;
            stickX = stickCX;
            stickY = stickCY;
            int rb = (int) stickBaseR;
            atkBtn.set(width - rb * 2 - 30, height - rb * 2 - 30, width - 30, height - 30);
            newGame();
            lastFrame = System.currentTimeMillis();
            gameThread = new Thread(this);
            gameThread.start();
        }

        @Override
        public void surfaceChanged(SurfaceHolder s, int f, int w, int h) {}
        @Override
        public void surfaceDestroyed(SurfaceHolder s) { gameOver = true; }

        public static class Entity {
            int x, y, hp, team;
            Entity() {}
            Entity(int x, int y, int hp, int team) {
                this.x = x; this.y = y; this.hp = hp; this.team = team;
            }
        }

        public static class Unit extends Entity {
            long attackCD = 0;
            long bulletAlertCD = 0;
            long territoryCD = 0;
            public Unit(int x, int y, int team) {
                super(x, y, NPC_HP_MAX, team);
            }
            void updateCooldown(long dt) {
                if (attackCD > 0) attackCD -= dt;
                if (bulletAlertCD > 0) bulletAlertCD -= dt;
                if (territoryCD > 0) territoryCD -= dt;
            }
            void bulletAlert(Bullet b) {
                bulletAlertCD = 800;
                double tx = x - b.x, ty = y - b.y;
                double l = Math.sqrt(tx * tx + ty * ty);
                if (l > 0) {
                    x += (int) (tx / l * 38);
                    y += (int) (ty / l * 38);
                }
            }
            void draw(Canvas c) {
                Paint p = new Paint();
                if (team == 1) p.setColor(Color.RED);
                else p.setColor(Color.BLUE);
                c.drawCircle(x, y, 26, p);
                p.setColor(Color.GREEN);
                c.drawRect(x - 20, y - 36, x - 20 + hp * 42 / NPC_HP_MAX, y - 30, p);
            }
        }

        public static class Player extends Entity {
            int attackCD = 0;
            boolean tryAttack = false;
            public Player(int x, int y, int team) {
                super(x, y, PLAYER_HP_MAX, team);
            }
            void move(float dx, float dy) {
                if (dx == 0 && dy == 0) return;
                double l = Math.sqrt(dx * dx + dy * dy);
                if (l > 0.1) {
                    x += (int) (dx * PLAYER_SPEED);
                    y += (int) (dy * PLAYER_SPEED);
                    if (x < 0) x = 0;
                    if (x > MAP_W) x = MAP_W;
                    if (y < 0) y = 0;
                    if (y > MAP_H) y = MAP_H;
                }
            }
            void updateCooldown(long dt) {
                if (attackCD > 0) attackCD -= dt;
            }
            void draw(Canvas c) {
                Paint p = new Paint();
                p.setColor(Color.YELLOW);
                c.drawRect(x - 22, y - 22, x + 22, y + 22, p);
                p.setColor(Color.GREEN);
                c.drawRect(x - 24, y - 38, x - 24 + hp * 48 / PLAYER_HP_MAX, y - 32, p);
            }
        }

        public static class Bullet extends Entity {
            float dx, dy;
            int dmg;
            Entity shooter;
            public Bullet(int x, int y, float dx, float dy, int team, Entity shooter) {
                this.x = x;
                this.y = y;
                this.dx = dx;
                this.dy = dy;
                this.dmg = BULLET_DMG;
                this.shooter = shooter;
                this.team = team;
            }
            void move() {
                x += dx * BULLET_SPEED;
                y += dy * BULLET_SPEED;
            }
            void draw(Canvas c) {
                Paint p = new Paint();
                p.setColor(Color.WHITE);
                c.drawCircle(x, y, 10, p);
            }
        }

        public static class Turret extends Entity {
            long attackCD = 0;
            public Turret(int x, int y, int team) {
                super(x, y, TURRET_HP, team);
            }
            void updateCooldown(long dt) {
                if (attackCD > 0) attackCD -= dt;
            }
            void draw(Canvas c) {
                Paint p = new Paint();
                if (team == 1) p.setColor(Color.rgb(255, 80, 180));
                else p.setColor(Color.rgb(60, 255, 255));
                c.drawRect(x - 28, y - 28, x + 28, y + 28, p);
                p.setColor(Color.GREEN);
                c.drawRect(x - 26, y - 38, x - 26 + hp * 52 / TURRET_HP, y - 32, p);
            }
        }

        public static class Territory {
            int x, y, team;
            int size = BASE_SIZE_INIT;
            Turret turret;
            Unit owner;
            long growTimer = 0;
            public Territory(int x, int y, int team, Unit owner) {
                this.x = x;
                this.y = y;
                this.team = team;
                this.owner = owner;
            }
            void updateTurret() {
                if (turret == null) turret = new Turret(x, y, team);
            }
            void grow(long dt) {
                growTimer += dt;
                if (growTimer >= BASE_GROWTH_INTERVAL && size < BASE_SIZE_MAX) {
                    size += BASE_GROWTH;
                    growTimer = 0;
                }
            }
            void updateAttack(ArrayList<Unit> npcs, Player player, ArrayList<Bullet> bullets, ArrayList<Territory> allTerritories) {
                if (turret == null) return;
                turret.updateCooldown(16);
                if (player != null && player.hp > 0 && player.team != team &&
					inArea(player.x, player.y) &&
					Math.hypot(player.x - turret.x, player.y - turret.y) < TURRET_RANGE
					&& turret.attackCD <= 0) {
                    double tx = player.x - turret.x, ty = player.y - turret.y;
                    double l = Math.sqrt(tx * tx + ty * ty);
                    float ddx = (float) (tx / l), ddy = (float) (ty / l);
                    bullets.add(new Bullet(turret.x, turret.y, ddx, ddy, team, turret));
                    turret.attackCD = TURRET_CD;
                    return;
                }
                for (int i = 0; i < npcs.size(); i++) {
                    Unit u = npcs.get(i);
                    if (u.hp > 0 && u.team != team && inArea(u.x, u.y) &&
						Math.hypot(u.x - turret.x, u.y - turret.y) < TURRET_RANGE
						&& turret.attackCD <= 0) {
                        double tx = u.x - turret.x, ty = u.y - turret.y;
                        double l = Math.sqrt(tx * tx + ty * ty);
                        float ddx = (float) (tx / l), ddy = (float) (ty / l);
                        bullets.add(new Bullet(turret.x, turret.y, ddx, ddy, team, turret));
                        turret.attackCD = TURRET_CD;
                        return;
                    }
                }
                for (int i = 0; i < allTerritories.size(); i++) {
                    Territory t = allTerritories.get(i);
                    if (t.turret != null && t.team != team && t.turret.hp > 0 &&
						inArea(t.turret.x, t.turret.y) &&
						Math.hypot(t.turret.x - turret.x, t.turret.y - turret.y) < TURRET_RANGE
						&& turret.attackCD <= 0) {
                        double tx = t.turret.x - turret.x, ty = t.turret.y - turret.y;
                        double l = Math.sqrt(tx * tx + ty * ty);
                        float ddx = (float) (tx / l), ddy = (float) (ty / l);
                        bullets.add(new Bullet(turret.x, turret.y, ddx, ddy, team, turret));
                        turret.attackCD = TURRET_CD;
                        return;
                    }
                }
            }
            boolean inArea(int px, int py) {
                return Math.abs(px - x) < size / 2 && Math.abs(py - y) < size / 2;
            }
            void draw(Canvas c) {
                Paint p = new Paint();
                if (team == 1) p.setColor(Color.argb(80, 255, 0, 80));
                else p.setColor(Color.argb(80, 0, 220, 255));
                c.drawRect(x - size / 2, y - size / 2, x + size / 2, y + size / 2, p);
                if (turret != null) turret.draw(c);
            }
        }

        int PLAYER_ATTACK_RANGE() { return 160; }
    }
}
