package com.example.piotr.tetris;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.example.piotr.tetris.custom.GameSurfaceView;
import com.example.piotr.tetris.custom.NextBlockPreviewSurfaceView;

import java.util.*;


public class GameFragment extends Fragment implements Runnable {

    private final long frameTimeInMs = 10;

    private OnStateChangeListener listener;
    private Handler uiHandler;
    private GameSurfaceView gameSurfaceView;
    private NextBlockPreviewSurfaceView nextBlockPreviewSurfaceView;

    private volatile int score;
    private volatile int level;
    private final int maxLevel = 10;

    private boolean notEnded;
    private static final Object pauseLock = new Object();
    private boolean paused;


    private enum NextMoveState {
        GENERATE_BLOCK,
        MOVE_BLOCK
    }

    private NextMoveState nextMove;

    private final float blockMoveStartDelay = 500.0f;
    private final float blockMoveStopDelay = 100.0f;
    private final float levelConst = (blockMoveStartDelay - blockMoveStopDelay) / maxLevel;
    private final float blockMoveDelayDelta = -0.02f;
    private float blockMoveDelay;
    private float blockMoveTimeAfterLastMove;
    private final float blockMoveAcceleratedDelay = blockMoveStopDelay / 2;
    private boolean isAccelerated;

    private GameBoard board;

    public GameFragment() {
        paused = true;

        nextMove = NextMoveState.GENERATE_BLOCK;

        isAccelerated = false;
        blockMoveDelay = blockMoveStartDelay;
        blockMoveTimeAfterLastMove = 0.0f;

        score = 0;
        level = 0;
    }

    public void setBoard(GameBoard board){
        this.board = board;
    }

    public void saveToBundle(Bundle outState){
        outState.putInt("score", score);
        outState.putInt("level", level);
        outState.putFloat("blockMoveDelay", blockMoveDelay);
        outState.putSerializable("nextMove", nextMove);
    }

    public void restoreFromBundle(Bundle savedInstanceState) {
        score = savedInstanceState.getInt("score");
        redrawScore();

        level = savedInstanceState.getInt("level");
        redrawLevel();

        blockMoveDelay = savedInstanceState.getFloat("blockMoveDelay");
        nextMove = (NextMoveState)savedInstanceState.getSerializable("nextMove");
        redrawBoard();
        redrawNextBlock();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_game, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState){
        super.onActivityCreated(savedInstanceState);
        gameSurfaceView = (GameSurfaceView)getView().findViewById(R.id.game_surface_view);
        gameSurfaceView.setBoard(board);
        nextBlockPreviewSurfaceView = (NextBlockPreviewSurfaceView)getView().findViewById(R.id.next_block);
        nextBlockPreviewSurfaceView.setBoard(board);
    }

    private void onGameEnd(){
        listener.onGameEnd(score);
        end();
    }

    public void end(){
        resume();
        notEnded = false;
    }


    private void redrawBoard(){
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if(gameSurfaceView != null)
                    gameSurfaceView.invalidate();
            }
        });
    }

    private void redrawNextBlock(){
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if(nextBlockPreviewSurfaceView != null)
                    nextBlockPreviewSurfaceView.invalidate();
            }
        });
    }

    private void redrawScore(){
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                ((TextView)getView().findViewById(R.id.score)).setText(Integer.toString(score));
            }
        });
    }

    private void redrawLevel(){
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                ((TextView)getView().findViewById(R.id.level)).setText(Integer.toString(level));
            }
        });
    }

    @Override
    public void run() {
        notEnded = true;
        while(notEnded){
            synchronized (pauseLock){
                while(paused){
                    try{
                        pauseLock.wait();
                    } catch(InterruptedException e){}
                }
            }

            long lastTime = System.currentTimeMillis();

            switch(nextMove){
                case GENERATE_BLOCK: {
                    boolean isNewBlockGenerated = board.generateNewBlock();
                    if(isNewBlockGenerated){
                        nextMove = NextMoveState.MOVE_BLOCK;
                        redrawNextBlock();
                    } else {
                        onGameEnd();
                    }
                    break;
                }
                case MOVE_BLOCK: {
                    boolean canBlockMoveDown = true;
                    if(isAccelerated){
                        if (blockMoveTimeAfterLastMove > blockMoveAcceleratedDelay) {
                            blockMoveTimeAfterLastMove = 0.0f;
                            canBlockMoveDown = board.moveDownCurrentBlock();
                        }
                    } else {
                        if (blockMoveTimeAfterLastMove > blockMoveDelay) {
                            blockMoveTimeAfterLastMove = 0.0f;
                            canBlockMoveDown = board.moveDownCurrentBlock();
                        }
                    }
                    if(!canBlockMoveDown){
                        board.placeCurrentBlock();
                        int scoreChange = board.checkAndRemoveFullLines();
                        addScore(scoreChange);
                        nextMove = NextMoveState.GENERATE_BLOCK;
                    }
                    break;
                }
            }

            redrawBoard();

            blockMoveTimeAfterLastMove += (float)frameTimeInMs;
            blockMoveDelay += blockMoveDelayDelta;
            if(blockMoveDelay <= blockMoveStopDelay)
                blockMoveDelay = blockMoveStopDelay;

            //levels are from 1 to maxlevel inclusive but we want 0-start level as multiplier so level + 1 - 1
            float nextLevelDelay = blockMoveStartDelay - ((level + 1 - 1) * levelConst);
            if(blockMoveDelay < nextLevelDelay)
                setLevel(level + 1);

            try{
                Thread.sleep(frameTimeInMs - (System.currentTimeMillis() - lastTime));
            } catch(InterruptedException | IllegalArgumentException e){}
        }
    }

    public void pause(){
        synchronized (pauseLock){
            paused = true;
        }
    }

    public void resume(){
        synchronized (pauseLock){
            paused = false;
            blockMoveTimeAfterLastMove = 0.0f;
            pauseLock.notifyAll();
        }
    }

    private void addScore(int scoreChange){
        switch(scoreChange){
            case 1:
                scoreChange = level * 40;
                break;
            case 2:
                scoreChange = level * 100;
                break;
            case 3:
                scoreChange = level * 300;
                break;
            case 4:
                scoreChange = level * 1200;
                break;
        }
        score += scoreChange;
        redrawScore();
    }

    private void setLevel(int newLevel){
        level = newLevel;
        redrawLevel();
    }

    public void rotate() {
        board.rotateLeftCurrentBlock();
    }

    public void moveLeft() {
        board.moveLeftCurrentBlock();
    }

    public void moveRight() {
        board.moveRightCurrentBlock();
    }

    public void setAccelerated(){
        isAccelerated = true;
    }

    public void unsetAccelerated(){
        isAccelerated = false;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mutualOnAttach(context);
    }

    @Override
    public void onAttach(Activity activity){
        super.onAttach(activity);
        mutualOnAttach(activity);
    }

    private void mutualOnAttach(Context context){
        uiHandler = new Handler(context.getMainLooper());
        if (context instanceof OnStateChangeListener) {
            listener = (OnStateChangeListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    public interface OnStateChangeListener {
        void onGameEnd(int score);
    }
}
