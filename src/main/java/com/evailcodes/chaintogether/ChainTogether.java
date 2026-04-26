package com.evailcodes.chaintogether;

import com.evailcodes.chaintogether.config.ChainConfig;
import com.evailcodes.chaintogether.handler.ChainHandler;
import com.evailcodes.chaintogether.network.ChainPacketHandler;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChainTogether implements ModInitializer {
    public static final String MODID = "chaintogether";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        ChainConfig.load();
        ChainPacketHandler.registerPayloads();
        ChainHandler.register();

        LOGGER.info("ChainTogether initialized!");
    }
}
