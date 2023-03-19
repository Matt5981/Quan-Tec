package org.example.utils;

// This class exists solely to make the webserver and command listener halt for a bit.
public class Halter {
    boolean isLocked;

    public Halter(boolean isLocked){
        this.isLocked = isLocked;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public void setLocked(boolean locked) {
        isLocked = locked;
    }
}
