package com.blackberry.cave;

import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.Color;
import net.rim.device.api.system.Bitmap;
import java.util.Vector;

public class HexRenderer {
    private static final boolean FOREST_OVERHANG_TRIAL = true;
    private static final boolean MOUNTAIN_OVERHANG_TRIAL = true;
    private int hexRadius = 70;
    private int originX = 320;
    private int originY = 240;

    private double sqrt3 = 1.73205081;
    private int r;

    private int[] hexXOffsets = new int[6];
    private int[] hexYOffsets = new int[6];

    // Polygon points for drawing
    private int[] xPts = new int[6];
    private int[] yPts = new int[6];
    private int[] rangeXOffsets = new int[6];
    private int[] rangeYOffsets = new int[6];

    // Scratch arrays to avoid allocation
    private int[] scratchPoint = new int[2];
    private HexMap minimapBoundsMap;
    private int minimapMinX, minimapMinY, minimapMaxX, minimapMaxY;

    private Bitmap imgGrass;
    private Bitmap imgForest;
    private Bitmap imgForestTrees;
    private Bitmap imgForestClearing;
    private int[] forestAlpha;
    private int[] forestTarget;
    private boolean forestInitialized;
    private Vector forestDecorations = new Vector();
    private Vector terrainDecorations = new Vector();
    private Bitmap imgWater;
    private Bitmap imgMountain;
    private Bitmap imgMountainPeaks;
    private Bitmap imgTown;
    private Bitmap[] imgPorts = new Bitmap[6];
    private Bitmap imgCloud;

    public HexRenderer() {
        imgGrass = Bitmap.getBitmapResource("tile_grass.png");
        imgForest = Bitmap.getBitmapResource(FOREST_OVERHANG_TRIAL ? "tile_forest_floor.png" : "tile_forest.png");
        if (FOREST_OVERHANG_TRIAL) {
            imgForestTrees = Bitmap.getBitmapResource("forest_dense.png");
            imgForestClearing = Bitmap.getBitmapResource("forest_clearing.png");
        }
        imgWater = Bitmap.getBitmapResource("tile_water.png");
        imgMountain = Bitmap.getBitmapResource(MOUNTAIN_OVERHANG_TRIAL ? "tile_mountain_floor.png" : "tile_mountain.png");
        if (MOUNTAIN_OVERHANG_TRIAL) imgMountainPeaks = Bitmap.getBitmapResource("mountain_peaks.png");
        imgTown = Bitmap.getBitmapResource("tile_town.png");
        String[] directions = { "e", "ne", "nw", "w", "sw", "se" };
        for (int i = 0; i < directions.length; i++)
            imgPorts[i] = Bitmap.getBitmapResource("tile_port_" + directions[i] + ".png");
        imgCloud = Bitmap.getBitmapResource("tile_cloud.png");

        r = hexRadius;

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

    public void prepareTerrain(HexMap map) {
        forestDecorations.removeAllElements();
        terrainDecorations.removeAllElements();
        Vector tiles = map.getTiles();
        for (int i = 0; i < tiles.size(); i++) {
            HexTile tile = (HexTile) tiles.elementAt(i);
            if (!(FOREST_OVERHANG_TRIAL && tile.type == HexTile.TYPE_FOREST)
                    && !(MOUNTAIN_OVERHANG_TRIAL && tile.type == HexTile.TYPE_MOUNTAIN)) continue;
            int index = terrainDecorations.size();
            while (index > 0) {
                HexTile previous = (HexTile) terrainDecorations.elementAt(index - 1);
                // World Y depends only on axial r; q breaks ties left to right.
                if (previous.r < tile.r || (previous.r == tile.r && previous.q <= tile.q)) break;
                index--;
            }
            terrainDecorations.insertElementAt(tile, index);
        }
        for (int i = 0; i < terrainDecorations.size(); i++) {
            HexTile tile = (HexTile) terrainDecorations.elementAt(i);
            if (tile.type == HexTile.TYPE_FOREST) forestDecorations.addElement(tile);
        }
        forestAlpha = new int[forestDecorations.size()];
        forestTarget = new int[forestDecorations.size()];
        forestInitialized = false;
    }

    boolean syncForestOccupancy(Party party) {
        if (!FOREST_OVERHANG_TRIAL) return false;
        boolean pending = false;
        for (int i = 0; i < forestDecorations.size(); i++) {
            HexTile tile = (HexTile) forestDecorations.elementAt(i);
            int target = 0;
            for (int j = 0; j < party.members.size(); j++) {
                Unit unit = (Unit) party.members.elementAt(j);
                if (unit.q == tile.q && unit.r == tile.r) { target = 255; break; }
            }
            forestTarget[i] = target;
            if (!forestInitialized) forestAlpha[i] = target;
            if (forestAlpha[i] != target) pending = true;
        }
        forestInitialized = true;
        return pending;
    }

    boolean animateForest() {
        boolean changed = false;
        if (!FOREST_OVERHANG_TRIAL) return false;
        for (int i = 0; i < forestAlpha.length; i++) {
            int value = forestAlpha[i], target = forestTarget[i];
            if (value == target) continue;
            forestAlpha[i] = value < target ? Math.min(target, value + 26) : Math.max(target, value - 26);
            changed = true;
        }
        return changed;
    }

    public void drawTerrainDecorations(Graphics g, int screenW, int screenH) {
        int forestIndex = -1;
        for (int i = 0; i < terrainDecorations.size(); i++) {
            HexTile tile = (HexTile) terrainDecorations.elementAt(i);
            boolean mountain = tile.type == HexTile.TYPE_MOUNTAIN;
            if (!mountain) forestIndex++;
            if (!tile.revealed) continue;
            Bitmap decoration = mountain ? imgMountainPeaks : imgForestTrees;
            int width = decoration.getWidth(), height = decoration.getHeight();
            getHexScreenPoint(tile.q, tile.r, scratchPoint);
            int x = scratchPoint[0] - width / 2, y = scratchPoint[1] - (mountain ? 140 : 115);
            // Cull by the decoration bounds, including crowns above the base hex.
            if (x + width <= 0 || x >= screenW || y + height <= 0 || y >= screenH) continue;
            if (mountain) {
                g.drawBitmap(x, y, width, height, decoration, 0, 0);
                continue;
            }
            int alpha = forestAlpha[forestIndex];
            int previousAlpha = g.getGlobalAlpha();
            try {
                if (alpha < 255) {
                    g.setGlobalAlpha(previousAlpha * (255 - alpha) / 255);
                    g.drawBitmap(x, y, width, height, imgForestTrees, 0, 0);
                }
                if (alpha > 0) {
                    g.setGlobalAlpha(previousAlpha * alpha / 255);
                    g.drawBitmap(x, y, width, height, imgForestClearing, 0, 0);
                }
            } finally { g.setGlobalAlpha(previousAlpha); }
        }
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

    public void drawMap(Graphics g, HexMap map, int screenW, int screenH) {
        boolean antialias = g.isDrawingStyleSet(Graphics.DRAWSTYLE_ANTIALIASED);
        g.setDrawingStyle(Graphics.DRAWSTYLE_ANTIALIASED, true);
        try {
            Vector tiles = map.getTiles();
            int size = tiles.size();
            int halfWidth = getHexHalfWidth();
            for (int i = 0; i < size; i++) {
                HexTile t = (HexTile) tiles.elementAt(i);
                getHexScreenPoint(t.q, t.r, scratchPoint);
                int cx = scratchPoint[0];
                int cy = scratchPoint[1];
                if (cx + halfWidth < 0 || cx - halfWidth > screenW || cy + r < 0 || cy - r > screenH)
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
                    sprite = t.port ? imgPorts[t.portDirection] : imgTown;
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

    public HexTile getTileAtScreenPoint(HexMap map, int x, int y) {
        Vector tiles = map.getTiles();
        int halfWidth = getHexHalfWidth();
        HexTile nearest = null;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < tiles.size(); i++) {
            HexTile tile = (HexTile) tiles.elementAt(i);
            getHexScreenPoint(tile.q, tile.r, scratchPoint);
            int dx = Math.abs(x - scratchPoint[0]);
            int dy = Math.abs(y - scratchPoint[1]);
            if (dx > halfWidth || dy > hexRadius
                    || (dy > hexRadius / 2
                    && (long) dx * hexRadius > 2L * halfWidth * (hexRadius - dy)))
                continue;
            long distance = (long) dx * dx + (long) dy * dy;
            if (distance < bestDistance) {
                nearest = tile;
                bestDistance = distance;
            }
        }
        return nearest;
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

    public void drawQuestMarker(Graphics g, int q, int r) {
        getHexScreenPoint(q, r, scratchPoint);
        int x = scratchPoint[0], y = scratchPoint[1] - 28;
        g.setColor(0xFF00FF);
        g.drawRect(x - 8, y - 8, 16, 16);
        g.drawLine(x - 10, y, x + 10, y);
        g.drawLine(x, y - 10, x, y + 10);
    }

    public void drawMinimap(Graphics g, HexMap map, Party party, Vector enemies,
            QuestCampaign quest, int cursorQ, int cursorR, int x, int y, int side, int alpha) {
        if (alpha <= 0 || side < 16) return;
        int oldAlpha = g.getGlobalAlpha();
        try {
            g.setGlobalAlpha(alpha / 2); g.setColor(Color.BLACK); g.fillRect(x,y,side,side);
            g.setGlobalAlpha(alpha); g.setColor(0x777777); g.drawRect(x,y,side,side);
            Vector tiles = map.getTiles();
            if (tiles.isEmpty()) return;
            if (minimapBoundsMap != map) {
                minimapMinX = Integer.MAX_VALUE; minimapMinY = Integer.MAX_VALUE;
                minimapMaxX = Integer.MIN_VALUE; minimapMaxY = Integer.MIN_VALUE;
                for (int i = 0; i < tiles.size(); i++) {
                    HexTile t = (HexTile) tiles.elementAt(i);
                    getHexWorldPoint(t.q, t.r, scratchPoint);
                    minimapMinX = Math.min(minimapMinX, scratchPoint[0]);
                    minimapMaxX = Math.max(minimapMaxX, scratchPoint[0]);
                    minimapMinY = Math.min(minimapMinY, scratchPoint[1]);
                    minimapMaxY = Math.max(minimapMaxY, scratchPoint[1]);
                }
                minimapBoundsMap = map;
            }
            int minX = minimapMinX, minY = minimapMinY;
            int maxX = minimapMaxX, maxY = minimapMaxY;
            double scale=Math.min((side-12.0)/Math.max(1,maxX-minX),(side-12.0)/Math.max(1,maxY-minY));
            int startX=x+(side-roundCoordinate((maxX-minX)*scale))/2;
            int startY=y+(side-roundCoordinate((maxY-minY)*scale))/2;
            for(int i=0;i<tiles.size();i++) {
                HexTile t=(HexTile)tiles.elementAt(i); if(!t.revealed) continue;
                minimapPoint(t.q,t.r,scale,minX,minY,startX,startY);
                g.setColor(t.port?0x00FFFF:t.type==HexTile.TYPE_TOWN?0xFFD700:t.type==HexTile.TYPE_WATER?0x3366DD
                        :t.type==HexTile.TYPE_MOUNTAIN?0x888888:t.type==HexTile.TYPE_FOREST?0x006400:0x228B22);
                int dot=t.type==HexTile.TYPE_TOWN?3:2;
                g.fillRect(scratchPoint[0]-1,scratchPoint[1]-1,dot,dot);
            }
            for(int i=0;i<enemies.size();i++) {
                Unit u=(Unit)enemies.elementAt(i); HexTile t=map.getTile(u.q,u.r);
                if(u.curHp<=0 || t==null || !t.revealed) continue;
                minimapPoint(u.q,u.r,scale,minX,minY,startX,startY);
                g.setColor(Color.RED); g.fillRect(scratchPoint[0]-1,scratchPoint[1]-1,3,3);
            }
            if (quest != null && quest.getState() == QuestCampaign.BOSS_ACTIVE) {
                minimapPoint(quest.getBossQ(), quest.getBossR(), scale, minX, minY, startX, startY);
                g.setColor(0xFF00FF);
                g.fillRect(scratchPoint[0]-2, scratchPoint[1]-2, 5, 5);
            }
            Unit active=party.getActiveMember();
            for(int pass=0;pass<2;pass++) for(int i=0;i<party.members.size();i++) {
                Unit u=(Unit)party.members.elementAt(i); if((u==active)!=(pass==1)) continue;
                minimapPoint(u.q,u.r,scale,minX,minY,startX,startY);
                g.setColor(u.curHp<=0?0x888888:u==active?Color.WHITE:Color.CYAN);
                g.fillRect(scratchPoint[0]-1,scratchPoint[1]-1,3,3);
            }
            minimapPoint(cursorQ,cursorR,scale,minX,minY,startX,startY);
            g.setColor(Color.YELLOW); g.drawRect(scratchPoint[0]-2,scratchPoint[1]-2,5,5);
        } finally { g.setGlobalAlpha(oldAlpha); }
    }

    private void minimapPoint(int q,int r,double scale,int minX,int minY,int x,int y) {
        getHexWorldPoint(q,r,scratchPoint);
        scratchPoint[0]=x+roundCoordinate((scratchPoint[0]-minX)*scale);
        scratchPoint[1]=y+roundCoordinate((scratchPoint[1]-minY)*scale);
    }

    private void drawPolyOutline(Graphics g) {
        for (int i = 0; i < 5; i++) {
            g.drawLine(xPts[i], yPts[i], xPts[i + 1], yPts[i + 1]);
        }
        g.drawLine(xPts[5], yPts[5], xPts[0], yPts[0]);
    }
}
