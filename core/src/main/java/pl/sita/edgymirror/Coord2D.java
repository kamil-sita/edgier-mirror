package pl.sita.edgymirror;

import static pl.sita.edgymirror.Const.CHUNK_SIZE;

public class Coord2D extends Pair<Integer, Integer> {

    public Coord2D(Integer integer, Integer integer2) {
        super(integer, integer2);
    }

    public int getXBoundBy(int bounder) {
        return ((getX() % bounder) + bounder) % bounder;
    }

    public int getYBoundBy(int bounder) {
        return ((getY() % bounder) + bounder) % bounder;
    }

    public int getXBoundChunk() {
        return getXBoundBy(CHUNK_SIZE);
    }

    public int getYBoundChunk() {
        return getYBoundBy(CHUNK_SIZE);
    }

}
