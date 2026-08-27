package com.anonymous.chat.models;

import java.io.Serializable;

public class ServerStats implements Serializable {
    private int online;
    private String db;
    private String netIn;
    private String netOut;
    private String ram;
    private String cpu;
    private String uptime;

    public ServerStats() {}

    public int getOnline() { return online; }
    public void setOnline(int online) { this.online = online; }

    public String getDb() { return db != null ? db : "0 B"; }
    public void setDb(String db) { this.db = db; }

    public String getNetIn() { return netIn != null ? netIn : "0 B"; }
    public void setNetIn(String netIn) { this.netIn = netIn; }

    public String getNetOut() { return netOut != null ? netOut : "0 B"; }
    public void setNetOut(String netOut) { this.netOut = netOut; }

    public String getRam() { return ram != null ? ram : "0 MB"; }
    public void setRam(String ram) { this.ram = ram; }

    public String getCpu() { return cpu != null ? cpu : "0%"; }
    public void setCpu(String cpu) { this.cpu = cpu; }

    public String getUptime() { return uptime != null ? uptime : "0s"; }
    public void setUptime(String uptime) { this.uptime = uptime; }
}
