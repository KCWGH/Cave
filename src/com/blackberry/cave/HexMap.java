package com.blackberry.cave;

import java.util.Vector;
import java.util.Random;

public class HexMap {
    private Vector tiles;
    private HexTile[][] tileMap;
    private int mapRadius = 15;
    private int arraySize;
    private int offset;
    private static final int TOWN_COUNT = 5;
    private static final int MIN_START_AREA = 9;
    private static final int MAX_GENERATION_ATTEMPTS = 8;
    private static final int[] NEIGHBOR_Q = { 1, 1, 0, -1, -1, 0 };
    private static final int[] NEIGHBOR_R = { 0, -1, -1, 0, 1, 1 };
    private Random rand;
    private HexTile startTile;

    public HexMap(long seed) {
        rand = new Random(seed);
        tiles = new Vector();
        offset = mapRadius;
        arraySize = mapRadius * 2 + 1;
        tileMap = new HexTile[arraySize][arraySize];
        for (int q = -mapRadius; q <= mapRadius; q++) {
            int r1 = Math.max(-mapRadius, -q - mapRadius);
            int r2 = Math.min(mapRadius, -q + mapRadius);
            for (int r = r1; r <= r2; r++) {
                HexTile tile = new HexTile(q, r, HexTile.TYPE_GRASS);
                tiles.addElement(tile);
                tileMap[q + offset][r + offset] = tile;
            }
        }
        generateMap();
    }

    private void generateMap() {
        Vector land = null;
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            for (int i = 0; i < tiles.size(); i++)
                ((HexTile) tiles.elementAt(i)).type = rand.nextInt(4);
            land = findLargestLandArea();
            if (land.size() >= MIN_START_AREA)
                break;
        }
        if (land == null || land.size() < MIN_START_AREA) {
            // A radius-two land patch has nineteen connected tiles. Preserve all
            // other terrain; never enable walking over water or mountains.
            for (int i = 0; i < tiles.size(); i++) {
                HexTile tile = (HexTile) tiles.elementAt(i);
                if (getDist(0, 0, tile.q, tile.r) <= 2)
                    tile.type = HexTile.TYPE_GRASS;
            }
            land = findLargestLandArea();
        }
        startTile = (HexTile) land.elementAt(rand.nextInt(land.size()));
        Vector firstTownCandidates = new Vector();
        for (int i = 0; i < land.size(); i++) {
            HexTile tile = (HexTile) land.elementAt(i);
            if (tile != startTile)
                firstTownCandidates.addElement(tile);
        }
        HexTile firstTown = (HexTile) firstTownCandidates.elementAt(rand.nextInt(firstTownCandidates.size()));
        firstTown.type = HexTile.TYPE_TOWN;
        // Only the first town is guaranteed to be in the start land area.
        // Other towns may be on islands reserved for a future sailing system.
        for (int i = 1; i < TOWN_COUNT; i++) {
            HexTile town = getRandomTraversableTile();
            if (town == null)
                throw new IllegalStateException("Map has too few town sites");
            town.type = HexTile.TYPE_TOWN;
        }
    }

    private Vector findLargestLandArea() {
        boolean[][] visited = new boolean[arraySize][arraySize];
        Vector largest = new Vector();
        for (int i = 0; i < tiles.size(); i++) {
            HexTile tile = (HexTile) tiles.elementAt(i);
            if (visited[tile.q + offset][tile.r + offset] || !isTraversable(tile.q, tile.r))
                continue;
            Vector area = new Vector();
            area.addElement(tile);
            visited[tile.q + offset][tile.r + offset] = true;
            for (int head = 0; head < area.size(); head++) {
                HexTile current = (HexTile) area.elementAt(head);
                for (int n = 0; n < NEIGHBOR_Q.length; n++) {
                    HexTile neighbor = getTile(current.q + NEIGHBOR_Q[n], current.r + NEIGHBOR_R[n]);
                    if (neighbor != null && !visited[neighbor.q + offset][neighbor.r + offset]
                            && isTraversable(neighbor.q, neighbor.r)) {
                        visited[neighbor.q + offset][neighbor.r + offset] = true;
                        area.addElement(neighbor);
                    }
                }
            }
            if (area.size() > largest.size())
                largest = area;
        }
        return largest;
    }

    public HexTile getStartTile() { return startTile; }

    public HexTile getTile(int q, int r) {
        int c = q + offset;
        int ro = r + offset;
        if (c < 0 || c >= arraySize || ro < 0 || ro >= arraySize)
            return null;
        return tileMap[c][ro];
    }

    public boolean isTraversable(int q, int r) {
        HexTile t = getTile(q, r);
        if (t == null)
            return false;
        return t.type != HexTile.TYPE_WATER && t.type != HexTile.TYPE_MOUNTAIN;
    }

    public HexTile getRandomTraversableTile() {
        Vector valid = new Vector();
        for (int i = 0; i < tiles.size(); i++) {
            HexTile t = (HexTile) tiles.elementAt(i);
            if (t != startTile && isTraversable(t.q, t.r) && t.type != HexTile.TYPE_TOWN) {
                valid.addElement(t);
            }
        }
        if (valid.isEmpty())
            return null;
        return (HexTile) valid.elementAt(rand.nextInt(valid.size()));
    }

    public void revealArea(int q, int r, int radius) {
        for (int i = 0; i < tiles.size(); i++) {
            HexTile t = (HexTile) tiles.elementAt(i);
            if (getDist(q, r, t.q, t.r) <= radius) {
                t.revealed = true;
            }
        }
    }

    private int getDist(int q1, int r1, int q2, int r2) {
        return (Math.abs(q1 - q2) + Math.abs(q1 + r1 - q2 - r2) + Math.abs(r1 - r2)) / 2;
    }

    public Vector getTiles() {
        return tiles;
    }

    public Vector getReachableTiles(int startQ, int startR, int range) {
        Vector visited = new Vector();
        Vector fringes = new Vector();
        Vector startBox = new Vector();
        startBox.addElement(getTile(startQ, startR));
        fringes.addElement(startBox);

        for (int k = 1; k <= range; k++) {
            Vector currentFringe = (Vector) fringes.elementAt(k - 1);
            Vector nextFringe = new Vector();
            for (int i = 0; i < currentFringe.size(); i++) {
                HexTile t = (HexTile) currentFringe.elementAt(i);
                if (t == null)
                    continue;
                int[][] neighbors = {
                        { t.q + 1, t.r }, { t.q + 1, t.r - 1 }, { t.q, t.r - 1 },
                        { t.q - 1, t.r }, { t.q - 1, t.r + 1 }, { t.q, t.r + 1 }
                };
                for (int n = 0; n < 6; n++) {
                    HexTile neighbor = getTile(neighbors[n][0], neighbors[n][1]);
                    if (neighbor != null && neighbor.revealed && isTraversable(neighbor.q, neighbor.r)) {
                        if (!visited.contains(neighbor) && !containsInFringes(fringes, neighbor)
                                && !nextFringe.contains(neighbor)) {
                            nextFringe.addElement(neighbor);
                        }
                    }
                }
            }
            fringes.addElement(nextFringe);
            for (int i = 0; i < nextFringe.size(); i++) {
                visited.addElement(nextFringe.elementAt(i));
            }
        }
        return visited;
    }

    private boolean containsInFringes(Vector fringes, HexTile t) {
        for (int i = 0; i < fringes.size(); i++) {
            Vector f = (Vector) fringes.elementAt(i);
            if (f.contains(t))
                return true;
        }
        return false;
    }

    public Vector getPath(int startQ, int startR, int endQ, int endR) {
        Vector frontier = new Vector();
        Vector cameFromKeys = new Vector();
        Vector cameFromValues = new Vector();
        HexTile start = getTile(startQ, startR);
        HexTile end = getTile(endQ, endR);
        if (start == null || end == null)
            return null;
        frontier.addElement(start);
        cameFromKeys.addElement(start);
        cameFromValues.addElement(null);
        while (!frontier.isEmpty()) {
            HexTile current = (HexTile) frontier.elementAt(0);
            frontier.removeElementAt(0);
            if (current == end)
                break;
            int[][] neighbors = {
                    { current.q + 1, current.r }, { current.q + 1, current.r - 1 }, { current.q, current.r - 1 },
                    { current.q - 1, current.r }, { current.q - 1, current.r + 1 }, { current.q, current.r + 1 }
            };
            for (int i = 0; i < 6; i++) {
                HexTile next = getTile(neighbors[i][0], neighbors[i][1]);
                if (next != null && next.revealed && isTraversable(next.q, next.r) && !cameFromKeys.contains(next)) {
                    frontier.addElement(next);
                    cameFromKeys.addElement(next);
                    cameFromValues.addElement(current);
                }
            }
        }
        if (!cameFromKeys.contains(end))
            return null;
        Vector path = new Vector();
        HexTile current = end;
        while (current != start) {
            path.insertElementAt(current, 0);
            int idx = cameFromKeys.indexOf(current);
            current = (HexTile) cameFromValues.elementAt(idx);
        }
        return path;
    }
}
