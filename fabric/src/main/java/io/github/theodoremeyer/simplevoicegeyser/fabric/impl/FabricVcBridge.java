package io.github.theodoremeyer.simplevoicegeyser.fabric.impl;

import io.github.theodoremeyer.simplevoicegeyser.core.svc.VoiceChatBridge;
import io.github.theodoremeyer.simplevoicegeyser.fabric.SvgMod;

public class FabricVcBridge extends VoiceChatBridge {

    public FabricVcBridge() {
        SvgMod.injectBridge(this);
    }
}
