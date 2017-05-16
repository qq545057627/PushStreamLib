package com.yxd.live.recording.bean;

public class BitratesDta {
    public int id;
    public String name;
    public int w;
    public int h;
    public int vbr;
    public int fps;
    public int abr;
    public boolean choose;
    public int gop;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getW() {
        return w;
    }

    public void setW(int w) {
        this.w = w;
    }

    public int getH() {
        return h;
    }

    public void setH(int h) {
        this.h = h;
    }

    public int getVbr() {
        return vbr;
    }

    public void setVbr(int vbr) {
        this.vbr = vbr;
    }

    public int getFps() {
        return fps;
    }

    public void setFps(int fps) {
        this.fps = fps;
    }

    public int getAbr() {
        return abr;
    }

    public void setAbr(int abr) {
        this.abr = abr;
    }

    public boolean isChoose() {
        return choose;
    }

    public void setChoose(boolean choose) {
        this.choose = choose;
    }

    public int getGop() {
        return gop;
    }

    public void setGop(int gop) {
        this.gop = gop;
    }
}