package com.froobworld.farmcontrol.controller;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SkyblockIronGolemKillListenerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void enablesOnlyForSkyblockMode() throws Exception {
        File modeFile = temporaryFolder.newFile("tryb.txt");

        Files.writeString(modeFile.toPath(), "  SkYbLoCk\r\n", StandardCharsets.UTF_8);
        assertTrue(SkyblockIronGolemKillListener.isSkyblockMode(modeFile.toPath()));

        Files.writeString(modeFile.toPath(), "survival\n", StandardCharsets.UTF_8);
        assertFalse(SkyblockIronGolemKillListener.isSkyblockMode(modeFile.toPath()));
    }

    @Test
    public void supportsUtf8BomAndTreatsMissingFileAsDisabled() throws Exception {
        File modeFile = new File(temporaryFolder.getRoot(), "tryb.txt");
        assertFalse(SkyblockIronGolemKillListener.isSkyblockMode(modeFile.toPath()));

        Files.writeString(modeFile.toPath(), "\uFEFFskyblock\n", StandardCharsets.UTF_8);
        assertTrue(SkyblockIronGolemKillListener.isSkyblockMode(modeFile.toPath()));
    }
}
