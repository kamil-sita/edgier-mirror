package pl.sita.edgymirror;

import static pl.sita.edgymirror.Const.CHUNK_SIZE;

public class Chunk {

    public GameObject[][] TOP_DATA = new GameObject[CHUNK_SIZE][CHUNK_SIZE];
    public GameObject[][] BOT_DATA = new GameObject[CHUNK_SIZE][CHUNK_SIZE];

    public GameObject getData(boolean top, int x, int y) {
        if (top) {
            return TOP_DATA[y][x];
        } else {
            return BOT_DATA[y][x];
        }
    }

}
