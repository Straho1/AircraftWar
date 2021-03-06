package com.hitsz.aircraftwar.UI;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.android.material.bottomappbar.BottomAppBarTopEdgeTreatment;
import com.hitsz.aircraftwar.ImageManager;
import com.hitsz.aircraftwar.MainActivity;
import com.hitsz.aircraftwar.MusicService;
import com.hitsz.aircraftwar.aircraft.AbstractAircraft;
import com.hitsz.aircraftwar.aircraft.Boss;
import com.hitsz.aircraftwar.aircraft.EliteEnemy;
import com.hitsz.aircraftwar.aircraft.HeroAircraft;
import com.hitsz.aircraftwar.aircraft.MobEnemy;
import com.hitsz.aircraftwar.aircraft.strategy.DirectBallistic;
import com.hitsz.aircraftwar.aircraft.strategy.DoubleBullet;
import com.hitsz.aircraftwar.basic.AbstractFlyingObject;
import com.hitsz.aircraftwar.bullet.AbstractBullet;
import com.hitsz.aircraftwar.factory.AbstractEnemyFactory;
import com.hitsz.aircraftwar.factory.AbstractPropFactory;
import com.hitsz.aircraftwar.factory.BloodSupplyFactory;
import com.hitsz.aircraftwar.factory.BombSupplyFactory;
import com.hitsz.aircraftwar.factory.FireSupplyFactory;
import com.hitsz.aircraftwar.prop.AbstractProp;
import com.hitsz.aircraftwar.prop.BloodSupplyProp;
import com.hitsz.aircraftwar.prop.BombSupplyProp;
import com.hitsz.aircraftwar.prop.FireSupplyProp;
import com.hitsz.aircraftwar.prop.strategy.ChangeBallistic;
import com.hitsz.aircraftwar.prop.strategy.IncreaseShootNum;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class GameTemplateView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    int backGroundTop = 0;

    /**
     * ????????????(ms)?????????????????????
     */
    int timeInterval = 40;

    final HeroAircraft heroAircraft = HeroAircraft.getSingleton();
    AbstractEnemyFactory enemyFactory;
    final List<AbstractAircraft> enemyAircrafts = new LinkedList<>();
    final List<AbstractBullet> heroBullets = new LinkedList<>();
    final List<AbstractBullet> enemyBullets = new LinkedList<>();
    AbstractPropFactory propFactory;
    final List<AbstractProp> propSupply = new LinkedList<>();
    int enemyMaxNumber = 5;

    boolean gameOverFlag = false;
    public int score = 0;
    boolean bossExit = false;
    int bossNum = 0;
    int bossScoreThreshold = 500;
    int bossVanishScore = 0;
    int time = 0;
    /**
     * ?????????ms)
     * ?????????????????????????????????????????????
     */
    int enemyCycleDuration = 600;
    int heroCycleDuration = 400;
    int enemyCycleTime = 0;
    int heroCycleTime = 0;
    boolean bgmStart;
    MusicService bgmThread;
    MusicService bossBgmThread;
    MusicService bombBgmThread;
    MusicService bulletHitBgmThread;
    MusicService gameOverBgmThread;
    MusicService getSupplyBgmThread;

    double eliteEnemyProbability = 0.2;
    double ratio = 1;

    private SurfaceHolder mSurfaceHolder;
    private Paint mPaint;
    private Canvas canvas;
    private Thread thread;

    public GameTemplateView(Context context, boolean bgmStart) {
        super(context);
        mPaint = new Paint();
        mSurfaceHolder = this.getHolder();
        mSurfaceHolder.addCallback(this);
        this.setFocusable(true);
        this.bgmStart = bgmStart;
        if (bgmStart) {
            bgmThread = new MusicService("bgm", 2);
            bgmThread.playMusic();
        }
        thread = new Thread(this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            double x = event.getX();
            double y = event.getY();
            if (x < 0 || x > MainActivity.WINDOW_WIDTH || y < 0 || y > MainActivity.WINDOW_HEIGHT) {
                // ????????????
                return false;
            }
            heroAircraft.setLocation(x, y);
        }
        return true;
    }

    //***********************
    //      Action ?????????
    //***********************

    public final boolean enemyTimeCountAndNewCycleJudge() {
        enemyCycleTime += timeInterval;
        if (enemyCycleTime >= enemyCycleDuration && enemyCycleTime - timeInterval < enemyCycleTime) {
            // ?????????????????????
            enemyCycleTime %= enemyCycleDuration;
            return true;
        } else {
            return false;
        }
    }

    public final boolean heroTimeCountAndNewCycleJudge() {
        heroCycleTime += timeInterval;
        if (heroCycleTime >= heroCycleDuration && heroCycleTime - timeInterval < heroCycleTime) {
            // ?????????????????????
            heroCycleTime %= heroCycleDuration;
            return true;
        } else {
            return false;
        }
    }

    /**
     * ?????????????????????????????????
     * @param probability
     * @return
     */
    public final boolean isCreate(double probability) {
        Random r = new Random();
        double t = r.nextDouble();
        if (t <= probability) {
            return true;
        }
        else {
            return false;
        }
    }

    public abstract void createEnemy();
    public abstract void difficultyIncrease();
    public final boolean isThreshold() {
        if (score > bossScoreThreshold * (bossNum+1) + bossVanishScore) {
            return true;
        }
        else {
            return false;
        }
    }

    public final void heroShootAction() {
        heroBullets.addAll(heroAircraft.executeStrategy(heroAircraft));
    }

    public final void enemyShootAction() {
        for (AbstractAircraft enemy : enemyAircrafts) {
            if (EliteEnemy.class.isAssignableFrom(enemy.getClass())) {
                enemyBullets.addAll(enemy.executeStrategy(enemy));
            }
            else if (Boss.class.isAssignableFrom(enemy.getClass())) {
                enemyBullets.addAll(enemy.executeStrategy(enemy));
            }
        }
    }

    public final void bulletsMoveAction() {
        for (AbstractBullet bullet : heroBullets) {
            bullet.forward();
        }
        for (AbstractBullet bullet : enemyBullets) {
            bullet.forward();
        }
    }

    public final void aircraftsMoveAction() {
        for (AbstractAircraft enemyAircraft : enemyAircrafts) {
            enemyAircraft.forward();
        }
    }

    public final void propSupplyMoveAction() {
        for (AbstractProp propsupply: propSupply) {
            propsupply.forward();
        }
    }


    /**
     * ???????????????
     * 1. ??????????????????
     * 2. ????????????/????????????
     * 3. ??????????????????
     */
    public final void crashCheckAction() {
        // TODO ????????????????????????
        for (AbstractBullet bullet : enemyBullets) {
            if (bullet.notValid()) {
                continue;
            }
            if (heroAircraft.notValid()) {
                // ????????????????????????????????????????????????????????????
                break;
            }
            if (heroAircraft.crash(bullet)) {
                // ??????????????????????????????
                // ??????????????????????????????
                heroAircraft.decreaseHp(bullet.getPower());
                bullet.vanish();
                if (heroAircraft.notValid()) {
                    // ?????????????????????????????????
                    break;
                }
            }
        }
        // ????????????????????????
        for (AbstractBullet bullet : heroBullets) {
            if (bullet.notValid()) {
                continue;
            }
            for (AbstractAircraft enemyAircraft : enemyAircrafts) {
                if (enemyAircraft.notValid()) {
                    // ????????????????????????????????????????????????
                    // ???????????????????????????????????????????????????
                    continue;
                }
                if (enemyAircraft.crash(bullet)) {
                    // ??????????????????????????????
                    // ???????????????????????????
                    if (bgmStart) {
                        bulletHitBgmThread = new MusicService("bullet_hit", 1);
                        bulletHitBgmThread.playMusic();
                    }
                    enemyAircraft.decreaseHp(bullet.getPower());
                    bullet.vanish();
                    if (enemyAircraft.notValid()) {
                        // TODO ?????????????????????????????????
                        if (!MobEnemy.class.isAssignableFrom(enemyAircraft.getClass())) {
                            Random r = new Random();
                            int t = r.nextInt(10);
                            if (t==1 || t == 9) {
                                propFactory = new BloodSupplyFactory();
                                propSupply.add(propFactory.creatProp(enemyAircraft));
                            }
                            else if (t == 3 || t == 7) {
                                propFactory = new FireSupplyFactory();
                                propSupply.add(propFactory.creatProp(enemyAircraft));
                            }
                            else if (t == 5) {
                                propFactory = new BombSupplyFactory();
                                BombSupplyProp bombSupplyProp = (BombSupplyProp) propFactory.creatProp(enemyAircraft);
                                propSupply.add(bombSupplyProp);
                            }
                        }
                        if (MobEnemy.class.isAssignableFrom(enemyAircraft.getClass())) {
                            score += 10;
                        }
                        else if (EliteEnemy.class.isAssignableFrom(enemyAircraft.getClass())) {
                            score += 20;
                        }
                        else {
                            score += 30;
                            bossNum += 1;
                            bossVanishScore = score;
                            if (bgmStart) {
                                bossBgmThread.stopMusic();
                            }
                        }
                    }
                }
                // ????????? ??? ?????? ??????????????????
                if (enemyAircraft.crash(heroAircraft) || heroAircraft.crash(enemyAircraft)) {
                    enemyAircraft.vanish();
                    heroAircraft.decreaseHp(Integer.MAX_VALUE);
                }
            }
        }

        // Todo: ?????????????????????????????????
        for (AbstractProp prop : propSupply) {
            if (prop.notValid()) {
                continue;
            }
            if (heroAircraft.notValid()) {
                // ????????????????????????????????????????????????????????????
                break;
            }
            if (heroAircraft.crash(prop)) {
                // ????????????????????????
                if (bgmStart) {
                    getSupplyBgmThread = new MusicService("get_supply", 1);
                    getSupplyBgmThread.playMusic();
                }
                if (BloodSupplyProp.class.isAssignableFrom(prop.getClass())) {
                    ((BloodSupplyProp) prop).increaseHp(heroAircraft);
                }
                else if (FireSupplyProp.class.isAssignableFrom(prop.getClass())) {
                    /**
                     * ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                     * ???????????????????????????????????????????????????????????????????????????????????????????????????
                     * ?????????????????????????????????????????????????????????????????????????????????????????????
                     */
                    if (DirectBallistic.class.isAssignableFrom(heroAircraft.getFireStrategy().getClass())) {
                        ((FireSupplyProp) prop).setPropStrategy(new IncreaseShootNum());
                        ((FireSupplyProp) prop).executeStrategy(heroAircraft);
                    }
                    else if (DoubleBullet.class.isAssignableFrom(heroAircraft.getFireStrategy().getClass())) {
                        ((FireSupplyProp) prop).setPropStrategy(new ChangeBallistic());
                        ((FireSupplyProp) prop).executeStrategy(heroAircraft);
                    }
                    else {
                        ((FireSupplyProp) prop).setPropStrategy(new ChangeBallistic());
                        ((FireSupplyProp) prop).executeStrategy(heroAircraft);
                    }
                }
                else if (BombSupplyProp.class.isAssignableFrom(prop.getClass())) {
                    if (bgmStart) {
                        bombBgmThread = new MusicService("bomb_explosion", 1);
                        bombBgmThread.playMusic();
                    }
                    for (AbstractAircraft enemy : enemyAircrafts) {
                        if (!enemy.notValid()) {
                            ((BombSupplyProp) prop).addEnemy(enemy);
                            if (EliteEnemy.class.isAssignableFrom(enemy.getClass())) {
                                score += 20;
                            }
                            else if (MobEnemy.class.isAssignableFrom(enemy.getClass())) {
                                score += 10;
                            }
                            else {
                                if (enemy.getHp() == 100) {
                                    score += 30;
                                }
                            }
                        }
                    }
                    for (AbstractBullet bullet : enemyBullets) {
                        if (!bullet.notValid()) {
                            ((BombSupplyProp) prop).addEnemy(bullet);
                        }
                    }
                    ((BombSupplyProp) prop).executeBombSupply();
                }
                prop.vanish();
                if (heroAircraft.notValid()) {
                    // ?????????????????????????????????
                    break;
                }
            }
        }
    }

    /**
     * ????????????
     * 1. ?????????????????????
     * 2. ?????????????????????
     * 3. ?????????????????????
     * <p>
     * ????????????????????????????????????????????????
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public final void postProcessAction() {
        enemyBullets.removeIf(AbstractFlyingObject::notValid);
        heroBullets.removeIf(AbstractFlyingObject::notValid);
        enemyAircrafts.removeIf(AbstractFlyingObject::notValid);
        propSupply.removeIf(AbstractFlyingObject::notValid);
    }


    //***********************
    //      Paint ?????????
    //***********************

    /**
     * ??????draw??????
     * ??????????????????draw???????????????????????????
     *
     */
    public final void draw() {
        if(mSurfaceHolder == null){
            return;
        }
        canvas = mSurfaceHolder.lockCanvas();
        // ????????????,????????????
        canvas.drawBitmap(ImageManager.BACKGROUND_IMAGE, 0, this.backGroundTop-MainActivity.WINDOW_HEIGHT, mPaint);
        canvas.drawBitmap(ImageManager.BACKGROUND_IMAGE, 0, this.backGroundTop, mPaint);
        this.backGroundTop += 1;
        if (this.backGroundTop == MainActivity.WINDOW_HEIGHT) {
            this.backGroundTop = 0;
        }

        // ?????????????????????????????????
        // ????????????????????????????????????
        paintImageWithPositionRevised(canvas, enemyBullets);
        paintImageWithPositionRevised(canvas, heroBullets);

        paintImageWithPositionRevised(canvas, enemyAircrafts);
        paintImageWithPositionRevised(canvas, propSupply);

        canvas.drawBitmap(ImageManager.HERO_IMAGE, heroAircraft.getLocationX() - ImageManager.HERO_IMAGE.getWidth() / 2,
                heroAircraft.getLocationY() - ImageManager.HERO_IMAGE.getHeight() / 2, mPaint);

        //????????????????????????
        paintScoreAndLife(canvas);
        canvas.save();

        mSurfaceHolder.unlockCanvasAndPost(canvas);
    }

    public final void paintImageWithPositionRevised(Canvas canvas, List<? extends AbstractFlyingObject> objects) {
        if (objects.size() == 0) {
            return;
        }

        for (AbstractFlyingObject object : objects) {
            Bitmap image = object.getImage();
            assert image != null : objects.getClass().getName() + " has no image! ";
            canvas.drawBitmap(image, object.getLocationX() - image.getWidth() / 2,
                    object.getLocationY() - image.getHeight() / 2, mPaint);
        }
    }

    public final void paintScoreAndLife(Canvas canvas) {
        int x = 10;
        int y = 25;
        mPaint.setTextSize(40);
        mPaint.setColor(Color.rgb(235, 161, 1));
        canvas.drawText("SCORE:" + this.score, x, y, mPaint);
        y = y + 20;
        canvas.drawText("LIFE:" + this.heroAircraft.getHp(), x, y, mPaint);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        if (!gameOverFlag) {
            thread.start();
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
        gameOverFlag = true;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void run() {
        while (!gameOverFlag) {
            synchronized (mSurfaceHolder){
                time += timeInterval;
                enemyCycleTime += 1;
                heroCycleTime += 1;

                // ?????????????????????????????????
                if (enemyTimeCountAndNewCycleJudge()) {
                    this.createEnemy();
                    enemyShootAction();
                }
                if (heroTimeCountAndNewCycleJudge()) {
                    heroShootAction();
                }

                // ????????????
                this.bulletsMoveAction();

                // ????????????
                this.aircraftsMoveAction();

                //????????????
                this.propSupplyMoveAction();

                // ????????????
                this.crashCheckAction();

                // ?????????
                this.postProcessAction();

                //????????????
                this.difficultyIncrease();

                //????????????
                this.draw();

                // ??????????????????
                if (heroAircraft.getHp() <= 0) {
                    if (bgmStart) {
                        gameOverBgmThread = new MusicService("game_over", 1);
                        gameOverBgmThread.playMusic();
                        bgmThread.stopMusic();
                        if (bossExit) {
                            bossBgmThread.stopMusic();
                        }
                    }
                    gameOverFlag = true;
                    //??????

                    System.out.println("Game Over!");
                }
            }
            try {
                Thread.sleep(timeInterval);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
