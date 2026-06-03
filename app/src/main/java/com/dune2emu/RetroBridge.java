package com.dune2emu;

public class RetroBridge {
    static {
        System.loadLibrary("dune2emu");
    }
    
    public native boolean initEmulator(String romPath, String saveDir);
    public native boolean setSurface(Object surface);
    public native void startEmulation();
    public native void stopEmulation();
    public native void setButtonState(int player, int button, boolean pressed);
    public native boolean saveState(int slot);
    public native boolean loadState(int slot);
    
    // Стриминг видео
    public native byte[] getVideoFrame();
}
