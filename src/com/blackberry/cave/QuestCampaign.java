package com.blackberry.cave;

import java.util.Vector;

// One campaign objective with primitive state that can be saved in C14.
public final class QuestCampaign {
    public static final int OFFERED = 0;
    public static final int HUNTING = 1;
    public static final int REPORT_READY = 2;
    public static final int BOSS_ACTIVE = 3;
    public static final int COMPLETE = 4;
    private static final int REQUIRED_KILLS = 3;
    private static final int REPORT_GOLD = 30;
    private static final int REPORT_XP = 20;

    private final int homeQ;
    private final int homeR;
    private final boolean[] contributors;
    private int state = OFFERED;
    private int kills;
    private int bossQ;
    private int bossR;
    private String statusText;

    public QuestCampaign(HexTile home, Party party) {
        if (home == null || party == null) throw new IllegalArgumentException("Quest home required");
        homeQ = home.q;
        homeR = home.r;
        contributors = new boolean[party.members.size()];
        refreshStatusText();
    }

    void writeSave(java.io.DataOutputStream out) throws java.io.IOException {
        out.writeInt(state); out.writeInt(kills); out.writeInt(bossQ); out.writeInt(bossR);
        for (int i = 0; i < contributors.length; i++) out.writeBoolean(contributors[i]);
    }

    static QuestCampaign readSave(java.io.DataInputStream in, HexMap map, Party party)
            throws java.io.IOException {
        QuestCampaign quest = new QuestCampaign(map.getFirstTownTile(), party);
        quest.state = in.readInt(); quest.kills = in.readInt();
        quest.bossQ = in.readInt(); quest.bossR = in.readInt();
        for (int i = 0; i < quest.contributors.length; i++) quest.contributors[i] = in.readBoolean();
        if (quest.state < OFFERED || quest.state > COMPLETE || quest.kills < 0
                || quest.kills > REQUIRED_KILLS
                || (quest.state == OFFERED && quest.kills != 0)
                || (quest.state == HUNTING && quest.kills >= REQUIRED_KILLS)
                || (quest.state >= REPORT_READY && quest.kills != REQUIRED_KILLS)
                || (quest.state >= BOSS_ACTIVE && !map.isTraversable(quest.bossQ, quest.bossR)))
            throw new java.io.IOException("Invalid quest");
        quest.refreshStatusText();
        return quest;
    }

    public int getState() { return state; }
    public int getRequiredKills() { return REQUIRED_KILLS; }
    public int getBossQ() { return bossQ; }
    public int getBossR() { return bossR; }

    public boolean isHome(HexTile town) {
        return town != null && town.type == HexTile.TYPE_TOWN
                && town.q == homeQ && town.r == homeR;
    }

    public boolean accept(HexTile town) {
        if (state != OFFERED || !isHome(town)) return false;
        state = HUNTING;
        refreshStatusText();
        return true;
    }

    public boolean recordMapWin(Vector participants, Party party) {
        if (state != HUNTING || participants == null || party == null) return false;
        for (int i = 0; i < contributors.length; i++) {
            Unit member = (Unit) party.members.elementAt(i);
            if (participants.contains(member)) contributors[i] = true;
        }
        kills++;
        if (kills >= REQUIRED_KILLS) state = REPORT_READY;
        refreshStatusText();
        return true;
    }

    public boolean report(Unit reporter, Party party, HexTile town, HexTile bossSite) {
        if (state != REPORT_READY || !isHome(town) || reporter == null || party == null
                || !party.members.contains(reporter) || reporter.curHp <= 0 || reporter.isMoving
                || reporter.q != homeQ || reporter.r != homeR || bossSite == null
                || bossSite.type == HexTile.TYPE_TOWN
                || bossSite.type == HexTile.TYPE_WATER
                || bossSite.type == HexTile.TYPE_MOUNTAIN) return false;
        if (reporter.getGold() > Long.MAX_VALUE - REPORT_GOLD) return false;
        for (int i = 0; i < contributors.length; i++) {
            if (!contributors[i]) continue;
            Unit member = (Unit) party.members.elementAt(i);
            if (member.getExperience() > Long.MAX_VALUE - REPORT_XP) return false;
        }
        // All checks precede changes; this method is called on the UI thread.
        reporter.addGold(REPORT_GOLD);
        for (int i = 0; i < contributors.length; i++)
            if (contributors[i]) ((Unit) party.members.elementAt(i)).addExperience(REPORT_XP);
        bossQ = bossSite.q;
        bossR = bossSite.r;
        state = BOSS_ACTIVE;
        refreshStatusText();
        return true;
    }

    public boolean completeBoss(int q, int r) {
        if (state != BOSS_ACTIVE || q != bossQ || r != bossR) return false;
        state = COMPLETE;
        refreshStatusText();
        return true;
    }

    public String getStatusText() {
        return statusText;
    }

    private void refreshStatusText() {
        if (state == OFFERED) statusText = "Quest at first town (" + homeQ + "," + homeR + ").";
        else if (state == HUNTING) statusText = "Map enemies defeated: " + kills + "/" + REQUIRED_KILLS;
        else if (state == REPORT_READY) statusText = "Report at first town (" + homeQ + "," + homeR + ").";
        else if (state == BOSS_ACTIVE) statusText = "Boss at (" + bossQ + "," + bossR + ").";
        else statusText = "Campaign complete.";
    }
}
