package com.blackberry.cave;

public class HexTile {
    public int q;
    public int r;
    public int type;
    public boolean revealed = false;
    public boolean port = false;
    int portDirection;

    public static final int TYPE_GRASS = 0;
    public static final int TYPE_FOREST = 1;
    public static final int TYPE_WATER = 2;
    public static final int TYPE_MOUNTAIN = 3;
    public static final int TYPE_TOWN = 4;

    public HexTile(int q, int r, int type) {
        this.q = q;
        this.r = r;
        this.type = type;
    }
}
