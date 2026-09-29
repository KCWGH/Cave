package com.blackberry.cave;

import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.Color;
import net.rim.device.api.system.Bitmap;
import java.util.Vector;

public class HexRenderer {
    private int hexRadius = 70;
    private int originX = 320;
    private int originY = 240;

    private double sqrt3 = 1.73205081;
    private int r;
    private int width, height;

    private int[] hexXOffsets = new int[6];
    private int[] hexYOffsets = new int[6];

    // Polygon points for drawing
    private int[] xPts = new int[6];
    private int[] yPts = new int[6];
    private int[] rangeXOffsets = new int[6];
    private int[] rangeYOffsets = new int[6];

    // Scratch arrays to avoid allocation
    private int[] scratchPoint = new int[2];

    private Bitmap imgGrass;
    private Bitmap imgForest;
    private Bitmap imgWater;
    private Bitmap imgMountain;
    private Bitmap imgTown;
    private Bitmap imgCloud;

    public HexRenderer() {
        imgGrass = Bitmap.getBitmapResource("tile_grass.png");
        imgForest = Bitmap.getBitmapResource("tile_forest.png");
        imgWater = Bitmap.getBitmapResource("tile_water.png");
        imgMountain = Bitmap.getBitmapResource("tile_mountain.png");
        imgTown = Bitmap.getBitmapResource("tile_town.png");
        imgCloud = Bitmap.getBitmapResource("tile_cloud.png");

        r = hexRadius;
        width = (int) (r * sqrt3);
        height = 2 * r;

        // Pre-calculate hex offsets
        for (int i = 0; i < 6; i++) {
            double angle_rad = Math.PI * (60 * i - 30) / 180.0;
            hexXOffsets[i] = roundCoordinate(r * Math.cos(angle_rad));
            hexYOffsets[i] = roundCoordinate(r * Math.sin(angle_rad));
            rangeXOffsets[i] = roundCoordinate((r - 5) * Math.cos(angle_rad));
            rangeYOffsets[i] = roundCoordinate((r - 5) * Math.sin(angle_rad));
        }
    }

    public void setOrigin(int x, int y) {
        this.originX = x;
        this.originY = y;
    }

    public int getHexHalfWidth() {
        return roundCoordinate(hexRadius * sqrt3 / 2.0);
    }

    public int getHexRadius() {
        return hexRadius;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public void drawMap(Graphics g, HexMap map) {
        boolean antialias = g.isDrawingStyleSet(Graphics.DRAWSTYLE_ANTIALIASED);
        g.setDrawingStyle(Graphics.DRAWSTYLE_ANTIALIASED, true);
        try {
            int screenW = 640;
            int screenH = 480;
            Vector tiles = map.getTiles();
            int size = tiles.size();
            for (int i = 0; i < size; i++) {
                HexTile t = (HexTile) tiles.elementAt(i);
                getHexScreenPoint(t.q, t.r, scratchPoint);
                int cx = scratchPoint[0];
                int cy = scratchPoint[1];
                if (cx + width < 0 || cx - width > screenW || cy + height < 0 || cy - height > screenH)
                    continue;
                drawHex(g, t, cx, cy);
            }
        } finally {
            g.setDrawingStyle(Graphics.DRAWSTYLE_ANTIALIASED, antialias);
        }
    }

    private void drawHex(Graphics g, HexTile t, int cx, int cy) {
        Bitmap sprite = null;
        if (!t.revealed) {
            sprite = imgCloud;
        } else {
            switch (t.type) {
                case HexTile.TYPE_GRASS:
                    sprite = imgGrass;
                    break;
                case HexTile.TYPE_FOREST:
                    sprite = imgForest;
                    break;
                case HexTile.TYPE_WATER:
                    sprite = imgWater;
                    break;
                case HexTile.TYPE_MOUNTAIN:
                    sprite = imgMountain;
                    break;
                case HexTile.TYPE_TOWN:
                    sprite = imgTown;
                    break;
            }
        }

        setHexPoints(cx, cy, hexXOffsets, hexYOffsets);
        if (sprite != null) {
            // Identity texture mapping uses 15.16 fixed point (1.0 == 65536).
            // Filling the polygon lets AA cover the actual texture boundary too.
            g.drawTexturedPath(xPts, yPts, null, null, cx - r, cy - r,
                    65536, 0, 0, 65536, sprite);
        }
        g.setColor(0x222222);
        drawPolyOutline(g);
    }

    public void drawRange(Graphics g, Vector tiles) {
        if (tiles == null)
            return;
        boolean antialias = g.isDrawingStyleSet(Graphics.DRAWSTYLE_ANTIALIASED);
        int alpha = g.getGlobalAlpha();
        g.setDrawingStyle(Graphics.DRAWSTYLE_ANTIALIASED, true);
        g.setGlobalAlpha(100);
        g.setColor(0x0066FF);
        try {
            int size = tiles.size();
            for (int i = 0; i < size; i++) {
                HexTile t = (HexTile) tiles.elementAt(i);
                if (!t.revealed)
                    continue;
                getHexScreenPoint(t.q, t.r, scratchPoint);
                setHexPoints(scratchPoint[0], scratchPoint[1], rangeXOffsets, rangeYOffsets);
                g.drawFilledPath(xPts, yPts, null, null);
            }
        } finally {
            g.setGlobalAlpha(alpha);
            g.setDrawingStyle(Graphics.DRAWSTYLE_ANTIALIASED, antialias);
        }
    }

    private static int roundCoordinate(double value) {
        return (int) Math.floor(value + 0.5);
    }

    private void setHexPoints(int cx, int cy, int[] xOffsets, int[] yOffsets) {
        for (int i = 0; i < 6; i++) {
            xPts[i] = cx + xOffsets[i];
            yPts[i] = cy + yOffsets[i];
        }
    }

    // Allocation-free version
    public void getHexWorldPoint(int q, int r, int[] out) {
        out[0] = (int) (hexRadius * (sqrt3 * q + sqrt3 / 2.0 * r));
        out[1] = (int) (hexRadius * (1.5 * r));
    }

    // Allocation-free version
    public void getHexScreenPoint(int q, int r, int[] out) {
        getHexWorldPoint(q, r, out);
        out[0] += originX;
        out[1] += originY;
    }

    public void drawSelector(Graphics g, int q, int r) {
        getHexScreenPoint(q, r, scratchPoint);
        setHexPoints(scratchPoint[0], scratchPoint[1], hexXOffsets, hexYOffsets);
        boolean antialias = g.isDrawingStyleSet(Graphics.DRAWSTYLE_ANTIALIASED);
        g.setDrawingStyle(Graphics.DRAWSTYLE_ANTIALIASED, true);
        g.setColor(Color.YELLOW);
        try {
            drawPolyOutline(g);
        } finally {
            g.setDrawingStyle(Graphics.DRAWSTYLE_ANTIALIASED, antialias);
        }
    }

    public void drawMinimap(Graphics g, HexMap map, Party party, int x, int y, int alpha) {
        if (alpha <= 0)
            return;

        int mw = 100;
        int mh = 100;

        g.setGlobalAlpha(alpha / 2);
        g.setColor(Color.BLACK);
        g.fillRect(x, y, mw, mh);

        g.setGlobalAlpha(alpha);
        g.setColor(0x444444);
        g.drawRect(x, y, mw, mh);

        Vector tiles = map.getTiles();
        int size = tiles.size();
        int centerX = x + mw / 2;
        int centerY = y + mh / 2;
        int scale = 4;

        for (int i = 0; i < size; i++) {
            HexTile t = (HexTile) tiles.elementAt(i);
            if (!t.revealed)
                continue;

            int tx = centerX + (int) (scale * (1.732 * t.q + 1.732 / 2.0 * t.r) / 10.0);
            int ty = centerY + (int) (scale * (1.5 * t.r) / 10.0);

            if (tx < x || tx >= x + mw || ty < y || ty >= y + mh)
                continue;

            int color = 0x222222;
            switch (t.type) {
                case HexTile.TYPE_GRASS:
                    color = 0x228B22;
                    break;
                case HexTile.TYPE_FOREST:
                    color = 0x006400;
                    break;
                case HexTile.TYPE_WATER:
                    color = 0x0000CD;
                    break;
                case HexTile.TYPE_MOUNTAIN:
                    color = 0x808080;
                    break;
                case HexTile.TYPE_TOWN:
                    color = 0xFFFF00;
                    break;
            }
            g.setColor(color);
            g.fillRect(tx, ty, 2, 2);
        }

        Unit active = party.getActiveMember();
        if (active != null) {
            int px = centerX + (int) (scale * (1.732 * active.q + 1.732 / 2.0 * active.r) / 10.0);
            int py = centerY + (int) (scale * (1.5 * active.r) / 10.0);
            if (px >= x && px < x + mw && py >= y && py < y + mh) {
                g.setColor(Color.WHITE);
                g.fillRect(px - 1, py - 1, 4, 4);
            }
        }
        g.setGlobalAlpha(255);
    }

    private void drawPolyOutline(Graphics g) {
        for (int i = 0; i < 5; i++) {
            g.drawLine(xPts[i], yPts[i], xPts[i + 1], yPts[i + 1]);
        }
        g.drawLine(xPts[5], yPts[5], xPts[0], yPts[0]);
    }
}
