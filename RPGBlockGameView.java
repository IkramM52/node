package com.example.blockblast;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Random;

public class RPGBlockGameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    // Map and view
    private static final int MAP_W = 5000, MAP_H = 5000;
    private static final int BLOCK = 64;
    private static final int NPC_COUNT = 40;
    private static final int TURRET_COUNT = 20;

    // --- PLAYER/NPC CONFIGURABLE ---
    public static int PLAYER_HP = 3500;
    public static int NPC_HP = 3000;
    public static int PLAYER_DMG = 80;
    public static int NPC_DMG = 80;
    // --- END CONFIGURABLE ---

    private static final int MISSILE_STUN = 1000;
    private static final int MISSILE_KNOCKBACK = 140;
    private static final int TURRET_FIRE_INTERVAL_FAST = 1000;
    private static final int TURRET_FIRE_INTERVAL_SLOW = 3000;
    private static final int TURRET_HP_LOW = 2020;
    private static final int TURRET_HP_HIGH = 5050;
    private static final float TURRET_RANGE = 1200f;
    private static final float UNIT_MISSILE_RANGE = 500f;
    private static final int ATTACK_CD = 3000;
    private static final int NPC_COLOR = Color.rgb(255,200,100);

    // Summoned minion config
    private static final int MINION_HP = 580;
    private static final int MINION_SIZE = 44;
    private static final int MINION_SPAWN_INTERVAL = 10000; // 10 detik
    private static final int MINION_DMG = 80;
    private static final int MINION_RANGE = 520;
    private static final int MINION_AOE_RADIUS = 100;
    private static final int MINION_KNOCKBACK = 200;
    private static final int MINION_SPEED = 18;
    private static final int MINION_ATTACK_CD = 3000; // 5 detik

    // Turret death stun
    private static final float TURRET_DEATH_STUN_RADIUS = 0 * BLOCK;
    private static final int TURRET_DEATH_STUN_MS = 0;

    // Mega Turret & Skill
    private static final int MEGA_TURRET_SIZE = 320;
    private static final int MEGA_TURRET_HP = 8000;
    private static final int MEGA_MISSILE_COOLDOWN = 20000; // ms
    private static final int MEGA_MISSILE_DMG = 400;
    private static final int MEGA_MISSILE_SPEED = 30;
    private static final int MEGA_MISSILE_EXPLODE_RADIUS = 30 * BLOCK; // 20 blok
    private static final int MEGA_MISSILE_EXPLODE_MIN_DMG = 70;
    private static final int MEGA_MISSILE_EXPLODE_MAX_DMG = MEGA_MISSILE_DMG;
    private static final int KELAGIT_BALL_COOLDOWN = 30000; // ms
    private static final int KELAGIT_BALL_DMG = 400;
    private static final int KELAGIT_BALL_STUN = 2000;
    private static final int KELAGIT_BALL_COUNT = 10;
    private static final int KELAGIT_BALL_RADIUS = 7 * BLOCK;

    private Thread thread;
    private boolean running = false;
    private int screenW, screenH;
    private Paint paint = new Paint();
    private Random rnd = new Random();

    // Camera
    private float camX = 0, camY = 0;

    private class Unit {
        float x, y, vx, vy;
        int color, hp, maxHp, damage;
        boolean isPlayer, isStunned;
        long stunEnd = 0;
        long lastAttack = 0;
        long lastMissile = 0;
        Object attackTarget = null; // Turret, Minion, MegaTurret
        Unit(float x, float y, int color, boolean isPlayer) {
            this.x = x; this.y = y; this.color = color; this.isPlayer = isPlayer;
            if (isPlayer) {
                this.hp = PLAYER_HP;
                this.maxHp = PLAYER_HP;
                this.damage = PLAYER_DMG;
            } else {
                this.hp = NPC_HP;
                this.maxHp = NPC_HP;
                this.damage = NPC_DMG;
            }
            this.vx = 0; this.vy = 0;
            this.isStunned = false;
        }
        void update() {
            if (isStunned && SystemClock.uptimeMillis() < stunEnd) return;
            if (isStunned) isStunned = false;
            x += vx;
            y += vy;
            if (x < 0) x = 0;
            if (y < 0) y = 0;
            if (x > MAP_W-BLOCK) x = MAP_W-BLOCK;
            if (y > MAP_H-BLOCK) y = MAP_H-BLOCK;
        }
        boolean canAttack() {
            return SystemClock.uptimeMillis() - lastAttack > ATTACK_CD;
        }
        void setAttacked() {
            lastAttack = SystemClock.uptimeMillis();
        }
        boolean canMissile() {
            return SystemClock.uptimeMillis() - lastMissile > ATTACK_CD;
        }
        void fireMissile() {
            lastMissile = SystemClock.uptimeMillis();
        }
        boolean isInStunRadius(float cx, float cy, float radius) {
            float dx = (x + BLOCK/2) - cx;
            float dy = (y + BLOCK/2) - cy;
            return dx*dx + dy*dy <= radius*radius;
        }
    }
    private Unit player;
    private ArrayList<Unit> npcs = new ArrayList<>();

    private class Turret {
        float x, y;
        int hp, maxHp;
        long lastFire;
        long lastMinion = 0;
        int fireInterval;
        int missileSpeed;
        boolean alive = true;
        boolean stunnedDeath = false;
        Turret(float x, float y, int hp, int fireInterval, int missileSpeed) {
            this.x = x; this.y = y;
            this.hp = this.maxHp = hp;
            this.lastFire = SystemClock.uptimeMillis();
            this.lastMinion = SystemClock.uptimeMillis();
            this.fireInterval = fireInterval;
            this.missileSpeed = missileSpeed;
        }
        boolean canBeAttacked() {
            return alive && hp > 0;
        }
    }
    private ArrayList<Turret> turrets = new ArrayList<>();

    private class Minion {
        float x, y, vx, vy;
        int hp, maxHp;
        long lastAttack = 0;
        Object target;
        boolean active = true;
        Minion(float x, float y) {
            this.x = x; this.y = y;
            this.hp = this.maxHp = MINION_HP;
        }
        void update() {
            if (hp <= 0) { active = false; return; }
            float nearestDist = 999999f;
            Object nearest = null;
            if (player.hp > 0) {
                float d = dist2(x, y, player.x + BLOCK / 2, player.y + BLOCK / 2);
                if (d < nearestDist) { nearestDist = d; nearest = player; }
            }
            for (Unit npc : npcs) {
                if (npc.hp <= 0) continue;
                float d = dist2(x, y, npc.x + BLOCK / 2, npc.y + BLOCK / 2);
                if (d < nearestDist) { nearestDist = d; nearest = npc; }
            }
            target = nearest;
            if (target != null) {
                float tx, ty;
                if (target instanceof Unit) {
                    Unit tu = (Unit)target;
                    tx = tu.x + BLOCK/2; ty = tu.y + BLOCK/2;
                } else {
                    tx = player.x + BLOCK/2; ty = player.y + BLOCK/2;
                }
                float dx = tx - x, dy = ty - y;
                float len = (float)Math.sqrt(dx*dx+dy*dy);
                if (len > MINION_RANGE/2f) {
                    vx = dx/len * MINION_SPEED;
                    vy = dy/len * MINION_SPEED;
                } else {
                    vx = 0; vy = 0;
                    long cd = (target == player) ? MINION_ATTACK_CD : 2000;
                    if (SystemClock.uptimeMillis() - lastAttack > cd) {
                        areaAttack();
                        lastAttack = SystemClock.uptimeMillis();
                    }
                }
                x += vx;
                y += vy;
                if (x < 0) x = 0;
                if (y < 0) y = 0;
                if (x > MAP_W-MINION_SIZE) x = MAP_W-MINION_SIZE;
                if (y > MAP_H-MINION_SIZE) y = MAP_H-MINION_SIZE;
            }
        }
        void areaAttack() {
            ArrayList<Unit> victims = new ArrayList<>();
            if (player.hp > 0) {
                float d = (float)Math.sqrt(dist2(x, y, player.x + BLOCK/2, player.y + BLOCK/2));
                if (d < MINION_AOE_RADIUS + BLOCK/2) victims.add(player);
            }
            for (Unit npc : npcs) {
                if (npc.hp <= 0) continue;
                float d = (float)Math.sqrt(dist2(x, y, npc.x + BLOCK/2, npc.y + BLOCK/2));
                if (d < MINION_AOE_RADIUS + BLOCK/2) victims.add(npc);
            }
            for (Unit victim : victims) {
                victim.hp -= MINION_DMG;
                if (victim.hp < 0) victim.hp = 0;
                victim.isStunned = true;
                victim.stunEnd = SystemClock.uptimeMillis() + 600;
                float dx = victim.x + BLOCK/2 - x, dy = victim.y + BLOCK/2 - y;
                float len = (float)Math.sqrt(dx*dx+dy*dy);
                if (len > 0) {
                    dx /= len; dy /= len;
                    victim.x += dx * MINION_KNOCKBACK;
                    victim.y += dy * MINION_KNOCKBACK;
                    if (victim.x < 0) victim.x = 0;
                    if (victim.y < 0) victim.y = 0;
                    if (victim.x > MAP_W-BLOCK) victim.x = MAP_W-BLOCK;
                    if (victim.y > MAP_H-BLOCK) victim.y = MAP_H-BLOCK;
                }
            }
        }
    }
    private ArrayList<Minion> minions = new ArrayList<>();

    private class Missile {
        float x, y, vx, vy;
        Object target;
        boolean active;
        boolean isFromUnit;
        Missile(float x, float y, float vx, float vy, Object target, boolean isFromUnit) {
            this.x = x;
            this.y = y;
            this.vx = vx; this.vy = vy;
            this.target = target;
            this.active = true;
            this.isFromUnit = isFromUnit;
        }
        void update() {
            x += vx;
            y += vy;
            if (!active) return;
            if (isFromUnit && target instanceof Minion) {
                Minion m = (Minion) target;
                float cx = m.x + MINION_SIZE/2, cy = m.y + MINION_SIZE/2;
                if (m.hp > 0 && dist2(x, y, cx, cy) < ((MINION_SIZE/2+10)*(MINION_SIZE/2+10))) {
                    m.hp -= (isPlayerMissile() ? PLAYER_DMG : NPC_DMG);
                    if (m.hp < 0) m.hp = 0;
                    this.active = false;
                }
            } else if (isFromUnit && (target instanceof Turret)) {
                Turret t = (Turret) target;
                if (t.hp > 0 && dist2(x, y, t.x + BLOCK / 2, t.y + BLOCK / 2) < (BLOCK * BLOCK)) {
                    t.hp -= (isPlayerMissile() ? PLAYER_DMG : NPC_DMG);
                    if (t.hp < 0) t.hp = 0;
                    this.active = false;
                }
            } else if (isFromUnit && (target instanceof MegaTurret)) {
                MegaTurret mt = (MegaTurret) target;
                if (mt.hp > 0 && dist2(x, y, mt.x + MEGA_TURRET_SIZE/2, mt.y + MEGA_TURRET_SIZE/2) < (MEGA_TURRET_SIZE/2)*(MEGA_TURRET_SIZE/2)) {
                    mt.hp -= (isPlayerMissile() ? PLAYER_DMG : NPC_DMG);
                    if (mt.hp < 0) mt.hp = 0;
                    this.active = false;
                }
            } else if (!isFromUnit && (target instanceof Unit)) {
                Unit u = (Unit) target;
                if (u.hp > 0 && dist2(x, y, u.x + BLOCK / 2, u.y + BLOCK / 2) < (BLOCK * BLOCK)) {
                    u.hp -= MINION_DMG;
                    u.isStunned = true;
                    u.stunEnd = SystemClock.uptimeMillis() + MISSILE_STUN;
                    float dx = u.x + BLOCK / 2 - x, dy = u.y + BLOCK / 2 - y;
                    float len = (float)Math.sqrt(dx * dx + dy * dy);
                    if (len > 0) {
                        dx /= len; dy /= len;
                        u.x += dx * MISSILE_KNOCKBACK; u.y += dy * MISSILE_KNOCKBACK;
                        if (u.x < 0) u.x = 0; if (u.y < 0) u.y = 0;
                        if (u.x > MAP_W-BLOCK) u.x = MAP_W-BLOCK; if (u.y > MAP_H-BLOCK) u.y = MAP_H-BLOCK;
                    }
                    this.active = false;
                }
            }
            if (x < 0 || y < 0 || x > MAP_W || y > MAP_H) active = false;
        }
        boolean isPlayerMissile() {
            // Missile dari player, deteksi dengan target dan kecepatan
            if (target instanceof Turret || target instanceof Minion || target instanceof MegaTurret) {
                if (player != null) {
                    float px = player.x+BLOCK/2, py = player.y+BLOCK/2;
                    return Math.abs(x-px)<BLOCK*2 && Math.abs(y-py)<BLOCK*2;
                }
            }
            return false;
        }
    }
    private ArrayList<Missile> missiles = new ArrayList<>();

    // --- Tambahan Mega Turret & Skill ---
    private MegaTurret megaTurret = null;
    private ArrayList<MegaMissile> megaMissiles = new ArrayList<>();
    private ArrayList<KelagitBall> kelagitBalls = new ArrayList<>();

    private class MegaTurret {
        float x, y;
        int hp, maxHp;
        boolean alive = true;
        long lastMegaMissile = 0;
        long lastKelagit = 0;
        MegaTurret(float x, float y) {
            this.x = x; this.y = y;
            this.hp = this.maxHp = MEGA_TURRET_HP;
        }
    }
    private class MegaMissile {
        float x, y, vx, vy;
        Object target;
        boolean active = true;
        boolean exploded = false;
        long explodeTime = 0;
        MegaMissile(float x, float y, Object target) {
            this.x = x; this.y = y; this.target = target;
            this.explodeTime = 0;
            updateVelocity();
        }
        void updateVelocity() {
            if (target == null) return;
            float tx, ty;
            if (target instanceof Unit) {
                Unit t = (Unit) target;
                tx = t.x + BLOCK/2; ty = t.y + BLOCK/2;
            } else if (target instanceof Minion) {
                Minion m = (Minion) target;
                tx = m.x + MINION_SIZE/2; ty = m.y + MINION_SIZE/2;
            } else if (target instanceof Turret) {
                Turret t = (Turret) target;
                tx = t.x + BLOCK/2; ty = t.y + BLOCK/2;
            } else if (target instanceof MegaTurret) {
                MegaTurret mt = (MegaTurret) target;
                tx = mt.x + MEGA_TURRET_SIZE/2; ty = mt.y + MEGA_TURRET_SIZE/2;
            } else {
                return;
            }
            float dx = tx - x, dy = ty - y;
            float len = (float)Math.sqrt(dx*dx+dy*dy);
            if (len > 0) {
                vx = dx / len * MEGA_MISSILE_SPEED;
                vy = dy / len * MEGA_MISSILE_SPEED;
            }
        }
        void update() {
            if (!active) return;
            if (exploded) {
                if (SystemClock.uptimeMillis() > explodeTime + 500) {
                    active = false;
                }
                return;
            }
            if (target != null) updateVelocity();
            x += vx; y += vy;
            boolean hit = false;
            float tx=0, ty=0;
            if (target instanceof Unit) {
                Unit t = (Unit) target;
                if (t.hp > 0 && dist2(x, y, t.x+BLOCK/2, t.y+BLOCK/2) < BLOCK*BLOCK) {
                    hit = true; tx = t.x+BLOCK/2; ty = t.y+BLOCK/2;
                }
            } else if (target instanceof Minion) {
                Minion m = (Minion) target;
                if (m.hp > 0 && dist2(x, y, m.x+MINION_SIZE/2, m.y+MINION_SIZE/2) < (MINION_SIZE/2+10)*(MINION_SIZE/2+10)) {
                    hit = true; tx = m.x+MINION_SIZE/2; ty = m.y+MINION_SIZE/2;
                }
            } else if (target instanceof Turret) {
                Turret t = (Turret) target;
                if (t.hp > 0 && dist2(x, y, t.x+BLOCK/2, t.y+BLOCK/2) < BLOCK*BLOCK) {
                    hit = true; tx = t.x+BLOCK/2; ty = t.y+BLOCK/2;
                }
            } else if (target instanceof MegaTurret) {
                MegaTurret mt = (MegaTurret) target;
                if (mt.hp > 0 && dist2(x, y, mt.x+MEGA_TURRET_SIZE/2, mt.y+MEGA_TURRET_SIZE/2) < (MEGA_TURRET_SIZE/2)*(MEGA_TURRET_SIZE/2)) {
                    hit = true; tx = mt.x+MEGA_TURRET_SIZE/2; ty = mt.y+MEGA_TURRET_SIZE/2;
                }
            }
            if (hit) {
                explodeTime = SystemClock.uptimeMillis();
                exploded = true;
                for (Unit u : npcs) {
                    if (u.hp <= 0) continue;
                    float dd = (float)Math.sqrt(dist2(tx, ty, u.x+BLOCK/2, u.y+BLOCK/2));
                    if (dd <= MEGA_MISSILE_EXPLODE_RADIUS) {
                        int dmg = MEGA_MISSILE_EXPLODE_MAX_DMG - (int)((MEGA_MISSILE_EXPLODE_MAX_DMG - MEGA_MISSILE_EXPLODE_MIN_DMG) * (dd / MEGA_MISSILE_EXPLODE_RADIUS));
                        u.hp -= dmg;
                        if (u.hp < 0) u.hp = 0;
                    }
                }
                if (player.hp > 0) {
                    float dd = (float)Math.sqrt(dist2(tx, ty, player.x+BLOCK/2, player.y+BLOCK/2));
                    if (dd <= MEGA_MISSILE_EXPLODE_RADIUS) {
                        int dmg = MEGA_MISSILE_EXPLODE_MAX_DMG - (int)((MEGA_MISSILE_EXPLODE_MAX_DMG - MEGA_MISSILE_EXPLODE_MIN_DMG) * (dd / MEGA_MISSILE_EXPLODE_RADIUS));
                        player.hp -= dmg;
                        if (player.hp < 0) player.hp = 0;
                    }
                }
                for (Minion m : minions) {
                    if (!m.active || m.hp <= 0) continue;
                    float dd = (float)Math.sqrt(dist2(tx, ty, m.x+MINION_SIZE/2, m.y+MINION_SIZE/2));
                    if (dd <= MEGA_MISSILE_EXPLODE_RADIUS) {
                        m.hp -= MEGA_MISSILE_EXPLODE_MIN_DMG;
                        if (m.hp < 0) m.hp = 0;
                    }
                }
                if (megaTurret != null && megaTurret.alive && megaTurret.hp > 0) {
                    float dd = (float)Math.sqrt(dist2(tx, ty, megaTurret.x+MEGA_TURRET_SIZE/2, megaTurret.y+MEGA_TURRET_SIZE/2));
                    if (dd <= MEGA_MISSILE_EXPLODE_RADIUS) {
                        megaTurret.hp -= MEGA_MISSILE_EXPLODE_MIN_DMG;
                        if (megaTurret.hp < 0) megaTurret.hp = 0;
                    }
                }
            }
            if (x < 0 || y < 0 || x > MAP_W || y > MAP_H) active = false;
        }
    }
    private class KelagitBall {
        float x, y;
        float vx, vy;
        Object target;
        boolean active = true;
        boolean exploded = false;
        long explodeTime = 0;
        KelagitBall(float x, float y, Object target) {
            this.x = x; this.y = y; this.target = target;
            explodeTime = 0;
            if (target != null) {
                float tx, ty;
                if (target instanceof Unit) {
                    Unit t = (Unit) target;
                    tx = t.x + BLOCK/2; ty = t.y + BLOCK/2;
                } else if (target instanceof Minion) {
                    Minion m = (Minion) target;
                    tx = m.x + MINION_SIZE/2; ty = m.y + MINION_SIZE/2;
                } else if (target instanceof MegaTurret) {
                    MegaTurret mt = (MegaTurret) target;
                    tx = mt.x + MEGA_TURRET_SIZE/2; ty = mt.y + MEGA_TURRET_SIZE/2;
                } else {
                    tx = x; ty = y;
                }
                float dx = tx-x, dy = ty-y;
                float len = (float)Math.sqrt(dx*dx+dy*dy);
                vx = dx/len*13f; vy = dy/len*13f;
            }
        }
        void update() {
            if (!active) return;
            if (exploded) {
                if (SystemClock.uptimeMillis() > explodeTime + 500) active = false;
                return;
            }
            x += vx; y += vy;
            boolean hit = false;
            float tx=0, ty=0;
            if (target instanceof Unit) {
                Unit t = (Unit) target;
                if (t.hp > 0 && dist2(x, y, t.x+BLOCK/2, t.y+BLOCK/2) < BLOCK*BLOCK) {
                    hit = true; tx = t.x+BLOCK/2; ty = t.y+BLOCK/2;
                }
            } else if (target instanceof Minion) {
                Minion m = (Minion) target;
                if (m.hp > 0 && dist2(x, y, m.x+MINION_SIZE/2, m.y+MINION_SIZE/2) < (MINION_SIZE/2+10)*(MINION_SIZE/2+10)) {
                    hit = true; tx = m.x+MINION_SIZE/2; ty = m.y+MINION_SIZE/2;
                }
            } else if (target instanceof MegaTurret) {
                MegaTurret mt = (MegaTurret) target;
                if (mt.hp > 0 && dist2(x, y, mt.x+MEGA_TURRET_SIZE/2, mt.y+MEGA_TURRET_SIZE/2) < (MEGA_TURRET_SIZE/2)*(MEGA_TURRET_SIZE/2)) {
                    hit = true; tx = mt.x+MEGA_TURRET_SIZE/2; ty = mt.y+MEGA_TURRET_SIZE/2;
                }
            }
            if (hit) {
                explodeTime = SystemClock.uptimeMillis();
                exploded = true;
                for (Unit u : npcs) {
                    if (u.hp <= 0) continue;
                    float dd = (float)Math.sqrt(dist2(tx, ty, u.x+BLOCK/2, u.y+BLOCK/2));
                    if (dd <= KELAGIT_BALL_RADIUS) {
                        u.isStunned = true;
                        u.stunEnd = SystemClock.uptimeMillis() + KELAGIT_BALL_STUN;
                        u.hp -= KELAGIT_BALL_DMG;
                        if (u.hp < 0) u.hp = 0;
                    }
                }
                if (player.hp > 0) {
                    float dd = (float)Math.sqrt(dist2(tx, ty, player.x+BLOCK/2, player.y+BLOCK/2));
                    if (dd <= KELAGIT_BALL_RADIUS) {
                        player.isStunned = true;
                        player.stunEnd = SystemClock.uptimeMillis() + KELAGIT_BALL_STUN;
                        player.hp -= KELAGIT_BALL_DMG;
                        if (player.hp < 0) player.hp = 0;
                    }
                }
            }
            if (x < 0 || y < 0 || x > MAP_W || y > MAP_H) active = false;
        }
    }

    public RPGBlockGameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);

        player = new Unit(200, MAP_H/2, Color.rgb(80,180,255), true);
        for (int i = 0; i < NPC_COUNT; i++) {
            npcs.add(new Unit(rnd.nextInt(MAP_W-2*BLOCK)+BLOCK, rnd.nextInt(MAP_H-2*BLOCK)+BLOCK, NPC_COLOR, false));
        }
        for (int i = 0; i < TURRET_COUNT; i++) {
            int t_hp = rnd.nextBoolean() ? TURRET_HP_HIGH : TURRET_HP_LOW;
            int t_fire = rnd.nextBoolean() ? TURRET_FIRE_INTERVAL_FAST : TURRET_FIRE_INTERVAL_SLOW;
            int t_speed = rnd.nextBoolean() ? 12 : 19;
            float tx = rnd.nextInt(MAP_W-BLOCK*2) + BLOCK;
            float ty = rnd.nextInt(MAP_H/2-BLOCK*2) + BLOCK;
            turrets.add(new Turret(tx, ty, t_hp, t_fire, t_speed));
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        thread = new Thread(this);
        thread.start();
    }
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        try { thread.join(); } catch (InterruptedException e) {}
    }
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void run() {
        while (running) {
            Canvas canvas = null;
            try {
                canvas = getHolder().lockCanvas();
                if (canvas != null) {
                    screenW = canvas.getWidth();
                    screenH = canvas.getHeight();
                    updateGame();
                    drawGame(canvas);
                }
            } finally {
                if (canvas != null) getHolder().unlockCanvasAndPost(canvas);
            }
            try { Thread.sleep(20); } catch (InterruptedException e) {}
        }
    }

    private void updateGame() {
        player.update();
        camX = player.x + BLOCK/2 - screenW/2;
        camY = player.y + BLOCK/2 - screenH/2;
        if (camX < 0) camX = 0;
        if (camY < 0) camY = 0;
        if (camX > MAP_W - screenW) camX = MAP_W - screenW;
        if (camY > MAP_H - screenH) camY = MAP_H - screenH;

        // NPC & Player: target objek terdekat (turret/minion/mega turret)
        ArrayList<Object> attackables = new ArrayList<>();
        for (Turret t : turrets) if (t.hp > 0) attackables.add(t);
        for (Minion m : minions) if (m.active && m.hp > 0) attackables.add(m);
        if (megaTurret != null && megaTurret.alive && megaTurret.hp > 0) attackables.add(megaTurret);

        // NPC
        for (Unit npc : npcs) {
            if (npc.hp <= 0) continue;
            Object nearestObj = null;
            float nearestDist = Float.MAX_VALUE;
            float cx = npc.x + BLOCK/2, cy = npc.y + BLOCK/2;
            for (Object obj : attackables) {
                float ox, oy;
                if (obj instanceof Turret) {
                    Turret t = (Turret) obj;
                    ox = t.x + BLOCK/2; oy = t.y + BLOCK/2;
                } else if (obj instanceof MegaTurret) {
                    MegaTurret mt = (MegaTurret) obj;
                    ox = mt.x + MEGA_TURRET_SIZE/2; oy = mt.y + MEGA_TURRET_SIZE/2;
                } else {
                    Minion m = (Minion) obj;
                    ox = m.x + MINION_SIZE/2; oy = m.y + MINION_SIZE/2;
                }
                float d = dist2(cx, cy, ox, oy);
                if (d < nearestDist) { nearestDist = d; nearestObj = obj; }
            }
            npc.attackTarget = nearestObj;

            boolean evade = false;
            for (Missile m : missiles) {
                if (!m.active || m.isFromUnit) continue;
                float dx = m.x - (npc.x + BLOCK/2);
                float dy = m.y - (npc.y + BLOCK/2);
                float dist = (float)Math.sqrt(dx*dx + dy*dy);
                float missileDirX = m.vx, missileDirY = m.vy;
                float dot = dx * missileDirX + dy * missileDirY;
                if (dist < 100 && dot < 0) {
                    float perpX = -missileDirY;
                    float perpY = missileDirX;
                    float len = (float)Math.sqrt(perpX*perpX + perpY*perpY);
                    if (len > 0) {
                        perpX /= len; perpY /= len;
                        npc.vx = perpX * 7;
                        npc.vy = perpY * 7;
                        evade = true;
                        break;
                    }
                }
            }
            if (!evade && nearestObj != null && !npc.isStunned) {
                float ox, oy;
                if (nearestObj instanceof Turret) {
                    Turret t = (Turret) nearestObj;
                    ox = t.x + BLOCK/2; oy = t.y + BLOCK/2;
                } else if (nearestObj instanceof MegaTurret) {
                    MegaTurret mt = (MegaTurret) nearestObj;
                    ox = mt.x + MEGA_TURRET_SIZE/2; oy = mt.y + MEGA_TURRET_SIZE/2;
                } else {
                    Minion m = (Minion) nearestObj;
                    ox = m.x + MINION_SIZE/2; oy = m.y + MINION_SIZE/2;
                }
                float dx = ox - cx, dy = oy - cy;
                float len = (float)Math.sqrt(dx*dx+dy*dy);
                if (len > UNIT_MISSILE_RANGE) {
                    npc.vx = dx / len * 4.1f;
                    npc.vy = dy / len * 4.1f;
                } else {
                    npc.vx = 0; npc.vy = 0;
                    if (npc.canMissile()) {
                        float ms = 16;
                        missiles.add(new Missile(cx, cy, dx/len*ms, dy/len*ms, nearestObj, true));
                        npc.fireMissile();
                    }
                }
            }
            npc.update();
            if (npc.isStunned && SystemClock.uptimeMillis() < npc.stunEnd) {
                npc.vx = 0; npc.vy = 0;
            }
        }

        // Mega Turret INIT
        if (megaTurret == null)
            megaTurret = new MegaTurret(MAP_W / 2 - MEGA_TURRET_SIZE / 2, 100);

        // Mega Turret Skill 1: Mega Missile
        if (megaTurret.alive && megaTurret.hp > 0 && SystemClock.uptimeMillis() - megaTurret.lastMegaMissile > MEGA_MISSILE_COOLDOWN) {
            Object farthest = null;
            float farDist = -1;
            float mx = megaTurret.x + MEGA_TURRET_SIZE/2, my = megaTurret.y + MEGA_TURRET_SIZE/2;
            if (player.hp > 0) {
                float d = dist2(mx, my, player.x + BLOCK/2, player.y + BLOCK/2);
                if (d > farDist) { farDist = d; farthest = player; }
            }
            for (Unit npc : npcs) {
                if (npc.hp <= 0) continue;
                float d = dist2(mx, my, npc.x + BLOCK/2, npc.y + BLOCK/2);
                if (d > farDist) { farDist = d; farthest = npc; }
            }
            for (Minion minion : minions) {
                if (!minion.active || minion.hp <= 0) continue;
                float d = dist2(mx, my, minion.x + MINION_SIZE/2, minion.y + MINION_SIZE/2);
                if (d > farDist) { farDist = d; farthest = minion; }
            }
            if (farthest != null) {
                megaMissiles.add(new MegaMissile(mx, my, farthest));
                megaTurret.lastMegaMissile = SystemClock.uptimeMillis();
            }
        }
        // Mega Turret Skill 2: Bola Kelagit
        if (megaTurret.alive && megaTurret.hp > 0 && SystemClock.uptimeMillis() - megaTurret.lastKelagit > KELAGIT_BALL_COOLDOWN) {
            ArrayList<Object> candidates = new ArrayList<>();
            if (player.hp > 0) candidates.add(player);
            for (Unit npc : npcs) if (npc.hp > 0) candidates.add(npc);
            for (Minion m : minions) if (m.active && m.hp > 0) candidates.add(m);
            Random r = new Random();
            for (int i = 0; i < KELAGIT_BALL_COUNT; i++) {
                if (candidates.size() == 0) break;
                Object target = candidates.get(r.nextInt(candidates.size()));
                kelagitBalls.add(new KelagitBall(megaTurret.x + MEGA_TURRET_SIZE/2, megaTurret.y + MEGA_TURRET_SIZE/2, target));
            }
            megaTurret.lastKelagit = SystemClock.uptimeMillis();
        }
        // Update MegaMissile
        ArrayList<MegaMissile> rmMega = new ArrayList<>();
        for (MegaMissile m : megaMissiles) {
            m.update();
            if (!m.active) rmMega.add(m);
        }
        megaMissiles.removeAll(rmMega);
        // Update KelagitBall
        ArrayList<KelagitBall> rmKelagit = new ArrayList<>();
        for (KelagitBall b : kelagitBalls) {
            b.update();
            if (!b.active) rmKelagit.add(b);
        }
        kelagitBalls.removeAll(rmKelagit);

        // Minion System
        long now = SystemClock.uptimeMillis();
        for (Turret turret : turrets) {
            if (!turret.alive || turret.hp <= 0) continue;
            if (now - turret.lastMinion > MINION_SPAWN_INTERVAL) {
                minions.add(new Minion(turret.x + BLOCK/2 - MINION_SIZE/2, turret.y + BLOCK/2 - MINION_SIZE/2));
                turret.lastMinion = now;
            }
        }
        ArrayList<Minion> deadMinions = new ArrayList<>();
        for (Minion minion : minions) {
            if (!minion.active) { deadMinions.add(minion); continue; }
            minion.update();
        }
        minions.removeAll(deadMinions);

        // Player: attack objek terdekat (turret/minion/mega turret)
        Object nearestObj = null;
        float nearestDist = Float.MAX_VALUE;
        float cx = player.x + BLOCK/2, cy = player.y + BLOCK/2;
        for (Object obj : attackables) {
            float ox, oy;
            if (obj instanceof Turret) {
                Turret t = (Turret) obj;
                ox = t.x + BLOCK/2; oy = t.y + BLOCK/2;
            } else if (obj instanceof MegaTurret) {
                MegaTurret mt = (MegaTurret) obj;
                ox = mt.x + MEGA_TURRET_SIZE/2; oy = mt.y + MEGA_TURRET_SIZE/2;
            } else {
                Minion m = (Minion) obj;
                ox = m.x + MINION_SIZE/2; oy = m.y + MINION_SIZE/2;
            }
            float d = dist2(cx, cy, ox, oy);
            if (d < nearestDist) { nearestDist = d; nearestObj = obj; }
        }
        player.attackTarget = nearestObj;
        if (nearestObj != null && nearestDist < UNIT_MISSILE_RANGE*UNIT_MISSILE_RANGE) {
            if (player.canMissile()) {
                float ox, oy;
                if (nearestObj instanceof Turret) {
                    Turret t = (Turret) nearestObj;
                    ox = t.x + BLOCK/2; oy = t.y + BLOCK/2;
                } else if (nearestObj instanceof MegaTurret) {
                    MegaTurret mt = (MegaTurret) nearestObj;
                    ox = mt.x + MEGA_TURRET_SIZE/2; oy = mt.y + MEGA_TURRET_SIZE/2;
                } else {
                    Minion m = (Minion) nearestObj;
                    ox = m.x + MINION_SIZE/2; oy = m.y + MINION_SIZE/2;
                }
                float dx = ox - cx, dy = oy - cy;
                float len = (float)Math.sqrt(dx*dx+dy*dy);
                float ms = 18;
                missiles.add(new Missile(cx, cy, dx/len*ms, dy/len*ms, nearestObj, true));
                player.fireMissile();
            }
        }

        // Update missiles
        ArrayList<Missile> toRemove = new ArrayList<>();
        for (Missile m : missiles) {
            if (m.active) m.update();
            else toRemove.add(m);
        }
        missiles.removeAll(toRemove);

        // Turrets fire ke unit terdekat
        for (Turret turret : turrets) {
            if (!turret.alive || turret.hp <= 0) {
                if (!turret.stunnedDeath) {
                    turret.stunnedDeath = true;
                    float tx = turret.x + BLOCK/2, ty = turret.y + BLOCK/2;
                    if (player.hp > 0 && player.isInStunRadius(tx, ty, 100*BLOCK)) {
                        player.isStunned = true;
                        player.stunEnd = SystemClock.uptimeMillis() + TURRET_DEATH_STUN_MS;
                    }
                    for (Unit npc : npcs) {
                        if (npc.hp > 0 && npc.isInStunRadius(tx, ty, 100*BLOCK)) {
                            npc.isStunned = true;
                            npc.stunEnd = SystemClock.uptimeMillis() + TURRET_DEATH_STUN_MS;
                        }
                    }
                }
                continue;
            }
            Unit nearest = null;
            float bestDist = Float.MAX_VALUE;
            ArrayList<Unit> units = new ArrayList<>();
            if (player.hp > 0) units.add(player);
            for (Unit npc : npcs) if (npc.hp > 0) units.add(npc);
            for (Unit u : units) {
                float d = dist2(turret.x+BLOCK/2, turret.y+BLOCK/2, u.x+BLOCK/2, u.y+BLOCK/2);
                if (d < bestDist && d < TURRET_RANGE*TURRET_RANGE) {
                    bestDist = d;
                    nearest = u;
                }
            }
            if (nearest != null && SystemClock.uptimeMillis() - turret.lastFire > turret.fireInterval) {
                float dx = nearest.x+BLOCK/2 - (turret.x+BLOCK/2), dy = nearest.y+BLOCK/2 - (turret.y+BLOCK/2);
                float len = (float)Math.sqrt(dx*dx+dy*dy);
                float mspeed = turret.missileSpeed;
                missiles.add(new Missile(turret.x+BLOCK/2, turret.y+BLOCK/2, dx/len*mspeed, dy/len*mspeed, nearest, false));
                turret.lastFire = SystemClock.uptimeMillis();
            }
        }
    }

    private void drawGame(Canvas canvas) {
        canvas.drawColor(Color.rgb(200,220,255));
        canvas.save();
        canvas.translate(-camX, -camY);

        // --- Draw Mega Turret ---
        if (megaTurret != null && megaTurret.alive && megaTurret.hp > 0) {
            paint.setColor(Color.rgb(180, 50, 220));
            canvas.drawRect(megaTurret.x, megaTurret.y, megaTurret.x+MEGA_TURRET_SIZE, megaTurret.y+MEGA_TURRET_SIZE, paint);
            paint.setColor(Color.BLACK);
            canvas.drawRect(megaTurret.x, megaTurret.y, megaTurret.x+MEGA_TURRET_SIZE, megaTurret.y+MEGA_TURRET_SIZE, paint);
            paint.setColor(Color.RED);
            float hpw = megaTurret.hp * MEGA_TURRET_SIZE / (float)megaTurret.maxHp;
            canvas.drawRect(megaTurret.x, megaTurret.y-16, megaTurret.x+hpw, megaTurret.y-8, paint);
        }
        // --- Draw Mega Missiles ---
        for (MegaMissile m : megaMissiles) {
            if (!m.active) continue;
            paint.setColor(Color.MAGENTA);
            canvas.drawCircle(m.x, m.y, 24, paint);
            if (m.exploded) {
                paint.setColor(Color.argb(80,255,0,200));
                canvas.drawCircle(m.x, m.y, MEGA_MISSILE_EXPLODE_RADIUS, paint);
            }
        }
        // --- Draw Kelagit Balls ---
        for (KelagitBall b : kelagitBalls) {
            if (!b.active) continue;
            paint.setColor(Color.BLUE);
            canvas.drawCircle(b.x, b.y, 16, paint);
            if (b.exploded) {
                paint.setColor(Color.argb(60,80,80,255));
                canvas.drawCircle(b.x, b.y, KELAGIT_BALL_RADIUS, paint);
            }
        }
        for (Turret turret : turrets) {
            if (!turret.alive || turret.hp <= 0) continue;
            paint.setColor(Color.rgb(160,60,60));
            canvas.drawRect(turret.x, turret.y, turret.x+BLOCK, turret.y+BLOCK, paint);
            paint.setColor(Color.DKGRAY);
            canvas.drawRect(turret.x+16, turret.y+8, turret.x+BLOCK-16, turret.y+BLOCK-8, paint);
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4);
            canvas.drawRect(turret.x, turret.y, turret.x+BLOCK, turret.y+BLOCK, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(1);
            paint.setColor(Color.RED);
            float hpw = turret.hp * BLOCK / (float)turret.maxHp;
            canvas.drawRect(turret.x, turret.y-16, turret.x+hpw, turret.y-8, paint);
        }

        for (Minion minion : minions) {
            if (!minion.active) continue;
            paint.setColor(Color.rgb(60, 220, 120));
            canvas.drawRect(minion.x, minion.y, minion.x+MINION_SIZE, minion.y+MINION_SIZE, paint);
            paint.setColor(Color.RED);
            canvas.drawRect(minion.x, minion.y-7, minion.x+MINION_SIZE*minion.hp/(float)minion.maxHp, minion.y-3, paint);
            paint.setColor(Color.argb(50, 60, 220, 120));
            canvas.drawCircle(minion.x+MINION_SIZE/2, minion.y+MINION_SIZE/2, MINION_AOE_RADIUS, paint);
        }

        for (Unit npc : npcs) {
            if (npc.hp <= 0) continue;
            paint.setColor(npc.color);
            canvas.drawRect(npc.x, npc.y, npc.x+BLOCK, npc.y+BLOCK, paint);
            if (npc.isStunned) {
                paint.setColor(Color.YELLOW);
                canvas.drawRect(npc.x, npc.y, npc.x+BLOCK, npc.y+8, paint);
            }
            paint.setColor(Color.RED);
            canvas.drawRect(npc.x, npc.y-10, npc.x+BLOCK*npc.hp/(float)npc.maxHp, npc.y-4, paint);
        }
        if (player.hp > 0) {
            paint.setColor(player.color);
            canvas.drawRect(player.x, player.y, player.x+BLOCK, player.y+BLOCK, paint);
            if (player.isStunned) {
                paint.setColor(Color.YELLOW);
                canvas.drawRect(player.x, player.y, player.x+BLOCK, player.y+8, paint);
            }
            paint.setColor(Color.RED);
            canvas.drawRect(player.x, player.y-10, player.x+BLOCK*player.hp/(float)player.maxHp, player.y-4, paint);
        }

        for(Missile m : missiles) {
            if (!m.active) continue;
            paint.setColor(m.isFromUnit ? Color.rgb(60,150,255) : Color.rgb(80,80,80));
            float dx = m.vx, dy = m.vy;
            float len = (float)Math.sqrt(dx*dx+dy*dy);
            if (len == 0) len = 1;
            dx /= len; dy /= len;
            float mx = m.x - 16*dx;
            float my = m.y - 16*dy;
            float ex = m.x + 24*dx;
            float ey = m.y + 24*dy;
            paint.setStrokeWidth(12);
            canvas.drawLine(mx, my, ex, ey, paint);
            paint.setStrokeWidth(1);
        }

        canvas.restore();
    }

    private float dist2(float x1, float y1, float x2, float y2) {
        float dx = x1-x2, dy = y1-y2;
        return dx*dx+dy*dy;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (player.hp <= 0) return true;
        if (player.isStunned && SystemClock.uptimeMillis() < player.stunEnd) return true;
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            float wx = event.getX() + camX;
            float wy = event.getY() + camY;
            float dx = wx - (player.x+BLOCK/2);
            float dy = wy - (player.y+BLOCK/2);
            float len = (float)Math.sqrt(dx*dx+dy*dy);
            if (len > 10) {
                player.vx = dx/len * 8;
                player.vy = dy/len * 8;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            player.vx = 0; player.vy = 0;
        }
        return true;
    }
}
