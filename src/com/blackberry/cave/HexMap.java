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
    private HexTile firstTownTile;

    void writeSave(java.io.DataOutputStream out) throws java.io.IOException {
        out.writeInt(startTile.q); out.writeInt(startTile.r);
        out.writeInt(firstTownTile.q); out.writeInt(firstTownTile.r);
        out.writeInt(tiles.size());
        for (int i = 0; i < tiles.size(); i++) {
            HexTile tile = (HexTile) tiles.elementAt(i);
            out.writeByte(tile.type); out.writeBoolean(tile.revealed);
            out.writeBoolean(tile.port);
        }
    }

    static HexMap readSave(java.io.DataInputStream in, int version) throws java.io.IOException {
        HexMap map = new HexMap(0, false);
        int sq = in.readInt(), sr = in.readInt(), tq = in.readInt(), tr = in.readInt();
        if (in.readInt() != map.tiles.size()) throw new java.io.IOException("Invalid map size");
        for (int i = 0; i < map.tiles.size(); i++) {
            HexTile tile = (HexTile) map.tiles.elementAt(i);
            tile.type = in.readUnsignedByte(); tile.revealed = in.readBoolean();
            tile.port = version >= 2 && in.readBoolean();
            if (tile.type > HexTile.TYPE_TOWN) throw new java.io.IOException("Invalid terrain");
            if (tile.port && tile.type != HexTile.TYPE_TOWN) throw new java.io.IOException("Invalid port");
        }
        map.startTile = map.getTile(sq, sr); map.firstTownTile = map.getTile(tq, tr);
        if (!map.isTraversable(sq, sr) || map.firstTownTile == null
                || map.firstTownTile.type != HexTile.TYPE_TOWN)
            throw new java.io.IOException("Invalid map landmarks");
        for (int i = 0; i < map.tiles.size(); i++) {
            HexTile tile = (HexTile) map.tiles.elementAt(i);
            if (!tile.port) continue;
            boolean coast = false;
            for (int d = 0; d < 6; d++) {
                HexTile next = map.getTile(tile.q + NEIGHBOR_Q[d], tile.r + NEIGHBOR_R[d]);
                if (next != null && next.type == HexTile.TYPE_WATER) coast = true;
            }
            if (!coast) throw new java.io.IOException("Port without sea");
        }
        map.cachePortDirections();
        return map;
    }

    public HexMap(long seed) {
        this(seed, true);
    }

    private HexMap(long seed, boolean generate) {
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
        if (generate) generateMap();
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
        firstTownTile = firstTown;
        // Only the first town is guaranteed to be in the start land area.
        // Other towns may be on islands reserved for a future sailing system.
        for (int i = 1; i < TOWN_COUNT; i++) {
            HexTile town = getRandomTraversableTile();
            if (town == null)
                throw new IllegalStateException("Map has too few town sites");
            town.type = HexTile.TYPE_TOWN;
        }
        generatePorts();
        cachePortDirections();
    }

    private void cachePortDirections() {
        for (int i = 0; i < tiles.size(); i++) {
            HexTile port = (HexTile) tiles.elementAt(i);
            if (!port.port) continue;
            int bestScore = -1;
            for (int direction = 0; direction < 6; direction++) {
                int score = portSeaScore(port, direction);
                if (score > bestScore) {
                    bestScore = score;
                    port.portDirection = direction;
                }
            }
        }
    }

    private int portSeaScore(HexTile port, int direction) {
        HexTile first = getTile(port.q + NEIGHBOR_Q[direction], port.r + NEIGHBOR_R[direction]);
        if (first == null || first.type != HexTile.TYPE_WATER) return -1;
        Vector sea = new Vector();
        sea.addElement(first);
        int score = 0;
        for (int head = 0; head < sea.size(); head++) {
            HexTile current = (HexTile) sea.elementAt(head);
            score += 4 - getDist(port.q, port.r, current.q, current.r);
            for (int n = 0; n < 6; n++) {
                HexTile next = getTile(current.q + NEIGHBOR_Q[n], current.r + NEIGHBOR_R[n]);
                if (next == null || next.type != HexTile.TYPE_WATER || sea.contains(next)
                        || getDist(port.q, port.r, next.q, next.r) > 3) continue;
                int dq = next.q - port.q, dr = next.r - port.r;
                int x = 2 * dq + dr;
                int dot = x * (2 * NEIGHBOR_Q[direction] + NEIGHBOR_R[direction])
                        + 3 * dr * NEIGHBOR_R[direction];
                // A 120-degree seaward cone in the same axial projection as the renderer.
                if (dot > 0 && dot * dot >= x * x + 3 * dr * dr) sea.addElement(next);
            }
        }
        return score;
    }

    private void generatePorts() {
        Vector home = getConnectedLandTiles(startTile.q, startTile.r);
        for (int i = 0; i < home.size(); i++) {
            HexTile port = (HexTile) home.elementAt(i);
            if (port == startTile || port.type == HexTile.TYPE_TOWN) continue;
            for (int n = 0; n < 6; n++) {
                HexTile sea = getTile(port.q + NEIGHBOR_Q[n], port.r + NEIGHBOR_R[n]);
                if (sea == null || home.contains(sea) || sea.type == HexTile.TYPE_TOWN) continue;
                Vector frontier = new Vector(), parents = new Vector();
                frontier.addElement(sea); parents.addElement(null);
                HexTile destination = null, fallback = null;
                for (int head = 0; head < frontier.size(); head++) {
                    HexTile current = (HexTile) frontier.elementAt(head);
                    if (isTraversable(current.q, current.r)
                            && getDist(port.q, port.r, current.q, current.r) >= 4) {
                        destination = current;
                        break;
                    }
                    if (fallback == null && current.type == HexTile.TYPE_MOUNTAIN
                            && getDist(port.q, port.r, current.q, current.r) >= 4) {
                        boolean adjacentHome = false;
                        for (int d = 0; d < 6; d++)
                            if (home.contains(getTile(current.q + NEIGHBOR_Q[d], current.r + NEIGHBOR_R[d])))
                                adjacentHome = true;
                        if (!adjacentHome) fallback = current;
                    }
                    for (int d = 0; d < 6; d++) {
                        HexTile next = getTile(current.q + NEIGHBOR_Q[d], current.r + NEIGHBOR_R[d]);
                        if (next != null && next.type != HexTile.TYPE_TOWN
                                && !home.contains(next) && !frontier.contains(next)) {
                            frontier.addElement(next); parents.addElement(current);
                        }
                    }
                }
                if (destination == null) destination = fallback;
                if (destination == null) continue;
                HexTile current = (HexTile) parents.elementAt(frontier.indexOf(destination));
                while (current != null) {
                    current.type = HexTile.TYPE_WATER;
                    current = (HexTile) parents.elementAt(frontier.indexOf(current));
                }
                port.type = HexTile.TYPE_TOWN; port.port = true;
                destination.type = HexTile.TYPE_TOWN; destination.port = true;
                return;
            }
        }
        throw new IllegalStateException("No connected port sites");
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
    public HexTile getFirstTownTile() { return firstTownTile; }

    // Reachable land regardless of fog; useful for placing guaranteed objectives.
    public Vector getConnectedLandTiles(int q, int r) {
        Vector connected = new Vector();
        HexTile start = getTile(q, r);
        if (start == null || !isTraversable(q, r)) return connected;
        boolean[][] visited = new boolean[arraySize][arraySize];
        visited[q + offset][r + offset] = true;
        connected.addElement(start);
        for (int head = 0; head < connected.size(); head++) {
            HexTile tile = (HexTile) connected.elementAt(head);
            for (int i = 0; i < NEIGHBOR_Q.length; i++) {
                int nextQ = tile.q + NEIGHBOR_Q[i];
                int nextR = tile.r + NEIGHBOR_R[i];
                HexTile next = getTile(nextQ, nextR);
                if (next == null || visited[nextQ + offset][nextR + offset]
                        || !isTraversable(nextQ, nextR)) continue;
                visited[nextQ + offset][nextR + offset] = true;
                connected.addElement(next);
            }
        }
        return connected;
    }

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
        for (int dq = -radius; dq <= radius; dq++)
            for (int dr = -radius; dr <= radius; dr++) {
                HexTile t = getTile(q + dq, r + dr);
                if (t != null && getDist(q, r, t.q, t.r) <= radius)
                    t.revealed = true;
            }
    }

    private int getDist(int q1, int r1, int q2, int r2) {
        return (Math.abs(q1 - q2) + Math.abs(q1 + r1 - q2 - r2) + Math.abs(r1 - r2)) / 2;
    }

    public Vector getTiles() {
        return tiles;
    }

    private boolean navigable(HexTile tile, boolean sailing) {
        return sailing ? tile.type == HexTile.TYPE_WATER || tile.port : isTraversable(tile.q, tile.r);
    }

    public Vector getReachableTiles(int startQ, int startR, int range, boolean sailing) {
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
                if (sailing && t.port && (t.q != startQ || t.r != startR)) continue;
                for (int n = 0; n < 6; n++) {
                    HexTile neighbor = getTile(t.q + NEIGHBOR_Q[n], t.r + NEIGHBOR_R[n]);
                    if (neighbor != null && neighbor.revealed && navigable(neighbor, sailing)) {
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

    public Vector getPath(int startQ, int startR, int endQ, int endR, boolean sailing) {
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
            if (sailing && current.port && current != start) continue;
            for (int i = 0; i < 6; i++) {
                HexTile next = getTile(current.q + NEIGHBOR_Q[i], current.r + NEIGHBOR_R[i]);
                if (next != null && next.revealed && navigable(next, sailing) && !cameFromKeys.contains(next)) {
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
