package com.maglink.autologin;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Mod(AutoLogin.MOD_ID)
public class AutoLogin
{
    public static final String MOD_ID = "autologin";
    private static final Path PASSWORD_FILE_PATH = Paths.get(".", "passwords.txt");
    private static final String LOGIN_COMMAND = "login";
    private static final Integer TICKS_PER_SECOND = 20;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<String, String> passwordsMap = new HashMap<String, String>();

    public AutoLogin()
    {
        MinecraftForge.EVENT_BUS.register(new EventHandler());
        readPasswordsFromFile();
    }

    private void readPasswordsFromFile() {
        try {
            if (!Files.exists(PASSWORD_FILE_PATH)) {
                return;
            }

            var fileContent = Files.readString(PASSWORD_FILE_PATH);
            var scanner = new Scanner(fileContent);
            while (scanner.hasNextLine()) {
                var slices = scanner.nextLine().split(": ");
                if (slices.length == 2) {
                    passwordsMap.put(slices[0].trim(), slices[1].trim());
                }
            }
            scanner.close();
        } catch (IOException e) {
            LOGGER.error("Failed to load passwords", e);
        }
    }

    public class EventHandler {
        private Boolean autoLoginEnabled = false;
        private String autoLoginPassword = null;
        private Integer tickNumber = 0;

        @SubscribeEvent
        public void onPlayerNameFormat(PlayerEvent.NameFormat event) {
            var gameSession = Minecraft.getInstance().getGame().getCurrentSession();
            if (gameSession == null) return;
            if (!gameSession.isRemoteServer()) return;

            var player = Minecraft.getInstance().player;
            if (player == null) return;

            var playerName = player.getName().getString();
            if (!event.getUsername().getString().equals(playerName)) {
                return;
            }

            autoLoginPassword = passwordsMap.get(playerName);
            if (autoLoginPassword != null) {
                autoLoginEnabled = true;
                tickNumber = 0;
            }
        }

        @SubscribeEvent
        public void onTick(TickEvent.PlayerTickEvent event) {
            if (!autoLoginEnabled) return;

            tickNumber++;
            if (tickNumber % TICKS_PER_SECOND != 0 && tickNumber > 5) return;
            if (tickNumber > TICKS_PER_SECOND * 15) {
                autoLoginEnabled = false;
                return;
            }

            var connection = Minecraft.getInstance().getConnection();
            if (connection == null) return;

            var stack = new ArrayList<String>(); stack.add(LOGIN_COMMAND);
            if (connection.getCommands().findNode(stack) == null) return;

            if (Minecraft.getInstance().player == null) return;

            Minecraft.getInstance().player.commandUnsigned(LOGIN_COMMAND + " " + autoLoginPassword);
            autoLoginEnabled = false;
        }
    }
}
