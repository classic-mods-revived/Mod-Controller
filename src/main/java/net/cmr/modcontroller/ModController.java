package net.cmr.modcontroller;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod("modcontroller")
public class ModController {

    public ModController(IEventBus modEventBus) {
        System.out.println("========================================");
        System.out.println("MOD CONTROLLER: Loaded successfully");
        System.out.println("Downloads were handled by ModControllerLocator");
        System.out.println("========================================");
    }
}
