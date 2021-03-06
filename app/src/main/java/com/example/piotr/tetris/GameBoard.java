package com.example.piotr.tetris;

import android.content.Context;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;

import java.util.*;


public class GameBoard extends Board {

    private Random random;

    private final Object moveLock = new Object();

    private Block currentBlock;
    private Block nextBlock;

    private final int startX = 4;
    private final int startY = 1;

    public GameBoard(Context context, int xSize, int ySize) {
        super(context, xSize, ySize);

        random = new Random();

        //must be to generate first currentBlock
        nextBlock = new Block(Block.BlockCoords.get(random.nextInt(Block.BlockCoords.getSize())), startX, startY);
    }

    @Override
    public void saveToBundle(Bundle outState){
        super.saveToBundle(outState);
        outState.putSerializable("currentBlock", currentBlock);
        outState.putSerializable("nextBlock", nextBlock);
    }

    @Override
    public void restoreFromBundle(Bundle savedInstanceState){
        super.restoreFromBundle(savedInstanceState);
        currentBlock = (Block)savedInstanceState.getSerializable("currentBlock");
        nextBlock = (Block)savedInstanceState.getSerializable("nextBlock");
    }

    public Block getCurrentBlock(){
        return currentBlock;
    }

    public Block getNextBlock(){
        return nextBlock;
    }

    public Paint getBlockPaint(Block block) {
        return getPaint(block.getValue());
    }

    private enum MoveType{
        CREATE_BLOCK,
        MOVE_DOWN,
        MOVE_LEFT,
        MOVE_RIGHT,
        ROTATE_IN_PLACE,
        ROTATE_WITH_MOVE_LEFT,
        ROTATE_WITH_MOVE_RIGHT
    }

    public boolean checkIfMoveCurrentBlockIsPossible(MoveType moveType) {
        switch(moveType){
            case MOVE_DOWN:
                currentBlock.moveDown();
                break;
            case MOVE_LEFT:
                currentBlock.moveLeft();
                break;
            case MOVE_RIGHT:
                currentBlock.moveRight();
                break;
            case ROTATE_IN_PLACE:
                currentBlock.rotateLeft();
                break;
            case ROTATE_WITH_MOVE_LEFT:
                currentBlock.rotateLeft();
                currentBlock.moveLeft();
                break;
            case ROTATE_WITH_MOVE_RIGHT:
                currentBlock.rotateLeft();
                currentBlock.moveRight();
                break;
        }

        boolean isFree = true;
        int[][] localCoords = currentBlock.getLocalCoords();
        for (int i = 0; i < localCoords.length; ++i) {
            int x = localCoords[i][0] + currentBlock.getX();
            int y = localCoords[i][1] + currentBlock.getY();

            if (x < 0 || x >= getColumns() ||  y < 0 || y >= getRows()) {
                isFree = false;
                break;
            } else {
                if (getField(x, y) != Field.EMPTY) {
                    isFree = false;
                    break;
                }
            }
        }

        switch(moveType){
            case MOVE_DOWN:
                currentBlock.moveUp();
                break;
            case MOVE_LEFT:
                currentBlock.moveRight();
                break;
            case MOVE_RIGHT:
                currentBlock.moveLeft();
                break;
            case ROTATE_IN_PLACE:
                currentBlock.rotateRight();
                break;
            case ROTATE_WITH_MOVE_LEFT:
                currentBlock.rotateRight();
                currentBlock.moveRight();
                break;
            case ROTATE_WITH_MOVE_RIGHT:
                currentBlock.rotateRight();
                currentBlock.moveLeft();
                break;
        }

        return isFree;
    }

    public boolean generateNewBlock(){
        currentBlock = nextBlock;
        if(!checkIfMoveCurrentBlockIsPossible(MoveType.CREATE_BLOCK))
            return false;

        nextBlock = new Block(Block.BlockCoords.get(random.nextInt(Block.BlockCoords.getSize())), startX, startY);

        return true;
    }

    public boolean moveDownCurrentBlock(){
        boolean result;
        synchronized (moveLock){
            result = checkIfMoveCurrentBlockIsPossible(MoveType.MOVE_DOWN);
            if(result)
                currentBlock.moveDown();
        }
        return result;
    }

    public void placeCurrentBlock(){
        synchronized (moveLock){
            int[][] localCoords = currentBlock.getLocalCoords();
            for(int[] fieldCoords : localCoords){
                int x = fieldCoords[0] + currentBlock.getX();
                int y = fieldCoords[1] + currentBlock.getY();

                setField(x, y, currentBlock.getFieldType());
            }
        }
    }

    public int checkAndRemoveFullLines(){
        ArrayList<Integer> linesToRemove = new ArrayList<Integer>();
        linesToRemove.ensureCapacity(getRows());
        for (int y = 0; y < getRows(); ++y){
            boolean toRemove = true;
            for(int x = 0; x < getColumns(); ++x){
                if(getField(x, y) == Field.EMPTY){
                    toRemove = false;
                    break;
                }
            }
            if(toRemove){
                linesToRemove.add(y);
            }
        }

        if(linesToRemove.size() > 0){
            for(int line : linesToRemove){
                for(int y = line; y > 0; --y){
                    for(int x = 0; x < getColumns(); ++x){
                        setField(x, y, getField(x, y - 1));
                    }
                }
            }
        }

        return linesToRemove.size();
    }

    public void moveLeftCurrentBlock() {
        synchronized (moveLock) {
            if (checkIfMoveCurrentBlockIsPossible(MoveType.MOVE_LEFT)) {
                currentBlock.moveLeft();
            }
        }
    }

    public void moveRightCurrentBlock() {
        synchronized (moveLock) {
            if (checkIfMoveCurrentBlockIsPossible(MoveType.MOVE_RIGHT)) {
                currentBlock.moveRight();
            }
        }
    }

    public void rotateLeftCurrentBlock() {
        synchronized (moveLock) {
            if(checkIfMoveCurrentBlockIsPossible(MoveType.ROTATE_IN_PLACE)){

                currentBlock.rotateLeft();

            } else if(checkIfMoveCurrentBlockIsPossible(MoveType.ROTATE_WITH_MOVE_LEFT)){

                currentBlock.rotateLeft();
                currentBlock.moveLeft();

            } else if(checkIfMoveCurrentBlockIsPossible(MoveType.ROTATE_WITH_MOVE_RIGHT)){

                currentBlock.rotateLeft();
                currentBlock.moveRight();
            }
        }
    }
}
