package com.ClanActionLogger;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;

// Clan API Imports
import net.runelite.api.clan.ClanID;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.GameTick;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.util.Text;
import okhttp3.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@PluginDescriptor(
        name = "Clan Action Logger",
        description = "Logs configurable clan actions (kicks, leaves, invites, ranks) straight to a Discord Webhook.",
        tags = {"clan", "chat", "discord", "webhook", "logger", "admin"}
)
public class ClanActionLogger extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClanActionLoggerConfig config;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private ConfigManager configManager;

    // Permanent roster state tracking memory
    private final Map<String, String> clanRosterMemory = new HashMap<>();
    private final Map<String, String> displayNameMemory = new HashMap<>();

    // Context trackers for chat channel kicks/bans
    private String pendingKickAdmin = null;
    private long pendingKickTime = 0;
    private boolean pendingIsBan = false;

    // Multi-second historical memory to prevent race conditions across slow ticks
    private final Map<String, Long> recentChatActions = new HashMap<>();

    // Queues for database fallbacks
    private final Map<String, String> pendingAddsRank = new HashMap<>();
    private final Map<String, Long> pendingAddsTime = new HashMap<>();
    private final Map<String, Long> pendingRemovalsTime = new HashMap<>();

    // Context trackers to prevent false alerts during world hops
    private String lastClanName = null;
    private boolean processedSessionStartup = false;
    private int loginTicksDelay = 0;

    @Provides
    ClanActionLoggerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ClanActionLoggerConfig.class);
    }

    @Override
    protected void shutDown() throws Exception
    {
        clanRosterMemory.clear();
        displayNameMemory.clear();
        recentChatActions.clear();
        pendingAddsRank.clear();
        pendingAddsTime.clear();
        pendingRemovalsTime.clear();
        lastClanName = null;
        processedSessionStartup = false;
        loginTicksDelay = 0;
    }

    /**
     * HARD CONFIG SECURITY: Watches for alterations to your panel field variables and locks immediately if cleared
     */
    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!event.getGroup().equals("clanactionlogger")) return;

        if (event.getKey().equals("targetClanName") || event.getKey().equals("adminAccountNames"))
        {
            if (config.targetClanName().trim().isEmpty() || config.adminAccountNames().trim().isEmpty())
            {
                log.warn("[SAFETY ACTIVATED] Target configurations cleared! Freezing core loops and flushing memory.");
                clanRosterMemory.clear();
                displayNameMemory.clear();
                pendingAddsRank.clear();
                pendingAddsTime.clear();
                pendingRemovalsTime.clear();
                lastClanName = null;
                processedSessionStartup = false;
            }
        }
    }

    /**
     * Automatically sets up a settling delay on login or hops to shield tracking layers from packet jitter
     */
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (config.targetClanName().trim().isEmpty() || config.adminAccountNames().trim().isEmpty()) return;

        if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            processedSessionStartup = false;
        }

        if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.LOGGING_IN)
        {
            loginTicksDelay = 20; // Silences the plugin for ~12 seconds to let data settle fully
            clanRosterMemory.clear();
            displayNameMemory.clear();
            pendingAddsRank.clear();
            pendingAddsTime.clear();
            pendingRemovalsTime.clear();
            lastClanName = null;
        }
    }

    /**
     * Standardizes player names into a lowercase, clean format to guarantee cross-engine matches
     */
    private String standardizeName(String name)
    {
        if (name == null) return "";
        return name.replace("\u00A0", " ").trim().toLowerCase();
    }

    /**
     * Verifies if the currently logged-in account matches the whitelist of authorized admin profiles
     */
    private boolean isAuthorizedAdmin()
    {
        if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null) return false;

        String rawWhitelist = config.adminAccountNames().trim();
        if (rawWhitelist.isEmpty()) return false; // Fail-secure if the whitelist is blank

        String localPlayerName = standardizeName(client.getLocalPlayer().getName());
        String[] authorizedProfiles = rawWhitelist.split(",");

        for (String profile : authorizedProfiles)
        {
            if (standardizeName(profile).equals(localPlayerName))
            {
                return true; // Match found, authorize execution
            }
        }

        return false; // No match found, restrict execution
    }

    /**
     * Verifies if the currently logged-in account is designated to trigger offline audit reports
     */
    private boolean isAuthorizedForOfflineAudit()
    {
        if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null) return false;

        String rawWhitelist = config.offlineAuditAccountNames().trim();
        if (rawWhitelist.isEmpty()) return true; // If blank, default to allowing any authorized admin

        String localPlayerName = standardizeName(client.getLocalPlayer().getName());
        String[] authorizedProfiles = rawWhitelist.split(",");

        for (String profile : authorizedProfiles)
        {
            if (standardizeName(profile).equals(localPlayerName))
            {
                return true; // Match found, authorize offline audit
            }
        }

        return false; // No match found, restrict offline audit
    }

    /**
     * ---------------------------------------------------------
     * ENGINE 1: CHAT PARSING LAYER (Primary Rich Logs)
     * ---------------------------------------------------------
     */
    @Subscribe
    public void onChatMessage(ChatMessage chatMessage)
    {
        // HARD CONSTRAINT GUARD: Verify targets and account authorization
        if (config.targetClanName().trim().isEmpty() || !isAuthorizedAdmin()) return;

        ClanSettings mainClanSettings = client.getClanSettings(ClanID.CLAN);
        if (mainClanSettings == null || !mainClanSettings.getName().equals(config.targetClanName().trim()))
        {
            return; // Drop packet silently if active account belongs to a different clan context
        }

        String message = Text.removeTags(chatMessage.getMessage()).replace("\u00A0", " ");
        long now = System.currentTimeMillis();

        // MALICIOUS USER PROTECTION: If local user leaves or gets expelled, clear configuration instantly
        if (message.contains("You have left the clan.") || message.contains("You have been expelled from the clan."))
        {
            log.info("[SECURITY ALERT] Local player has left or been expelled from the target clan. Purging webhook credentials.");
            configManager.setConfiguration("clanactionlogger", "adminWebhookUrl", "");
            configManager.setConfiguration("clanactionlogger", "enableMonitoring", false);
            if (client.getLocalPlayer() != null)
            {
                String rsnKey = "last_clan_" + standardizeName(client.getLocalPlayer().getName());
                configManager.setConfiguration("clanactionlogger", rsnKey, "");
            }
            return;
        }

        // CHAT DEDUPLICATOR: If this exact raw string message ran within the last 2 seconds, kill it
        String standardizedMessageKey = "msg_" + standardizeName(message);
        if (recentChatActions.containsKey(standardizedMessageKey))
        {
            long lastSeen = recentChatActions.get(standardizedMessageKey);
            if (now - lastSeen < 2000)
            {
                return;
            }
        }

        // 1. Expulsion Detection
        if (config.logKicks() && message.contains(" has expelled ") && message.contains(" from the clan"))
        {
            int expelledIdx = message.indexOf(" has expelled ");
            int fromClanIdx = message.indexOf(" from the clan");
            if (expelledIdx != -1 && fromClanIdx != -1 && fromClanIdx > expelledIdx)
            {
                String admin = message.substring(0, expelledIdx).trim();
                String remainder = message.substring(expelledIdx + " has expelled ".length(), fromClanIdx).trim();

                if (admin.contains("] ")) {
                    admin = admin.substring(admin.lastIndexOf("] ") + 2).trim();
                }
                String target = remainder;
                if (target.contains("] ")) {
                    target = target.substring(target.lastIndexOf("] ") + 2).trim();
                }

                String stdTarget = standardizeName(target);
                String actionKey = "kick_" + stdTarget;

                if (recentChatActions.containsKey(actionKey) && (now - recentChatActions.get(actionKey) < 2000))
                {
                    return;
                }

                sendDiscordWebhook(config.adminWebhookUrl(), "🔨 **Clan Expulsion**", "Staff member **" + admin + "** has expelled **" + target + "** from the clan.");

                recentChatActions.put(stdTarget, now);
                recentChatActions.put(actionKey, now);
                pendingRemovalsTime.remove(stdTarget);
                return;
            }
        }
        // 1.5 Chat Channel Kick/Ban Attempt Detection
        if (message.equals("Attempting to kick player from clan chat...") || message.equals("Attempting to ban player from clan chat..."))
        {
            String senderName = chatMessage.getName() != null ? Text.removeTags(chatMessage.getName()) : "Unknown Admin";
            pendingKickAdmin = senderName;
            pendingIsBan = message.contains("ban");
            pendingKickTime = now;
            return;
        }

        // 1.6 Chat Channel Kick/Ban Execution Detection
        if (config.logKicks() && message.endsWith(" has left.") && pendingKickTime > 0)
        {
            if (now - pendingKickTime < 10000) // 10-second window to account for server tick delay
            {
                String target = message.substring(0, message.indexOf(" has left.")).trim();
                if (target.contains("] ")) {
                    target = target.substring(target.lastIndexOf("] ") + 2).trim();
                }

                String stdTarget = standardizeName(target);
                String action = pendingIsBan ? "Ban" : "Kick";
                String pastTense = pendingIsBan ? "banned" : "kicked";
                String actionKey = "cc_kick_" + stdTarget;

                if (!recentChatActions.containsKey(actionKey) || (now - recentChatActions.get(actionKey) >= 2000))
                {
                    sendDiscordWebhook(config.adminWebhookUrl(), "🔨 **Clan Chat " + action + "**", "Staff member **" + pendingKickAdmin + "** has " + pastTense + " **" + target + "** from the chat channel.");

                    recentChatActions.put(stdTarget, now);
                    recentChatActions.put(actionKey, now);
                }
                pendingKickTime = 0; // Consume the pending action to prevent false positives on regular logouts
                return;
            }
        }
        // 2. Recruitment / Invitation Detection
        if (config.logInvites())
        {
            String splitter = null;
            if (message.contains(" has been recruited to the clan by "))
            {
                splitter = " has been recruited to the clan by ";
            }
            else if (message.contains(" has been invited into the clan by "))
            {
                splitter = " has been invited into the clan by ";
            }

            if (splitter != null)
            {
                int splitterIdx = message.indexOf(splitter);
                if (splitterIdx != -1)
                {
                    String target = message.substring(0, splitterIdx).trim();
                    String admin = message.substring(splitterIdx + splitter.length()).trim();

                    if (admin.endsWith(".")) {
                        admin = admin.substring(0, admin.length() - 1).trim();
                    }
                    if (admin.contains("] ")) {
                        admin = admin.substring(admin.lastIndexOf("] ") + 2).trim();
                    }
                    if (target.contains("] ")) {
                        target = target.substring(target.lastIndexOf("] ") + 2).trim();
                    }

                    String stdTarget = standardizeName(target);
                    String actionKey = "invite_" + stdTarget;

                    if (recentChatActions.containsKey(actionKey) && (now - recentChatActions.get(actionKey) < 2000))
                    {
                        return;
                    }

                    sendDiscordWebhook(config.adminWebhookUrl(), "✨ **Clan Recruitment**", "Staff member **" + admin + "** has successfully recruited **" + target + "** into the clan.");

                    recentChatActions.put(stdTarget, now);
                    recentChatActions.put(actionKey, now);
                    pendingAddsTime.remove(stdTarget);
                    pendingAddsRank.remove(stdTarget);
                    return;
                }
            }
        }

        // 3. Voluntary Roster Leave Departure Detection (Admin Channel Log)
        if (message.contains(" has left the clan"))
        {
            int leaveIdx = message.indexOf(" has left the clan");
            if (leaveIdx != -1)
            {
                String target = message.substring(0, leaveIdx).trim();
                if (target.contains("] ")) {
                    target = target.substring(target.lastIndexOf("] ") + 2).trim();
                }

                String stdTarget = standardizeName(target);
                String actionKey = "roster_leave_" + stdTarget;

                if (recentChatActions.containsKey(actionKey) && (now - recentChatActions.get(actionKey) < 2000))
                {
                    return;
                }

                // Sends rich log directly to the Admin channel, bypassing the fallback safety net
                sendDiscordWebhook(config.adminWebhookUrl(), "🚪 **Clan Roster Departure**", "Member **" + target + "** has voluntarily left the clan.");

                recentChatActions.put(stdTarget, now);
                recentChatActions.put(actionKey, now);
                pendingRemovalsTime.remove(stdTarget);
            }
        }
    }

    /**
     * ---------------------------------------------------------
     * ENGINE 2: TICK POLLING MEMORY (Time-Buffered Fallback Net)
     * ---------------------------------------------------------
     */
    @Subscribe
    public void onGameTick(GameTick event)
    {
        // GUARD 1: Count down warm-up frames before executing any evaluation layers
        if (loginTicksDelay > 0)
        {
            loginTicksDelay--;
            return;
        }

        // HARD CONSTRAINT GUARD 2: Freeze execution entirely if target configurations are missing or user is unauthorized
        if (config.targetClanName().trim().isEmpty() || !isAuthorizedAdmin()) return;

        long now = System.currentTimeMillis();

        // 1. Process database additions that survived the 3-second buffer
        Set<String> addsToExecute = new HashSet<>();
        for (Map.Entry<String, Long> entry : pendingAddsTime.entrySet())
        {
            if (now - entry.getValue() > 3000)
            {
                addsToExecute.add(entry.getKey());
            }
        }
        for (String stdName : addsToExecute)
        {
            String rank = pendingAddsRank.remove(stdName);
            pendingAddsTime.remove(stdName);

            long lastChatTime = recentChatActions.getOrDefault(stdName, 0L);
            if (now - lastChatTime > 5000 && config.logInvites())
            {
                String displayName = displayNameMemory.getOrDefault(stdName, stdName);
                sendDiscordWebhook(config.adminWebhookUrl(), "✨ **Clan Roster Add (Fallback)**", "**" + displayName + "** was permanently added to the roster ledger as a **" + rank + "**.");
            }
        }

        // 2. Process database deletions that survived the 3-second buffer
        Set<String> removalsToExecute = new HashSet<>();
        for (Map.Entry<String, Long> entry : pendingRemovalsTime.entrySet())
        {
            if (now - entry.getValue() > 3000)
            {
                removalsToExecute.add(entry.getKey());
            }
        }
        for (String stdName : removalsToExecute)
        {
            pendingRemovalsTime.remove(stdName);

            long lastChatTime = recentChatActions.getOrDefault(stdName, 0L);
            if (now - lastChatTime > 5000 && config.logKicks())
            {
                String displayName = displayNameMemory.getOrDefault(stdName, stdName);
                sendDiscordWebhook(config.adminWebhookUrl(), "❌ **Clan Roster Remove (Fallback)**", "**" + displayName + "** is no longer listed on the permanent clan roster ledger.");
            }
        }

        ClanSettings mainClanSettings = client.getClanSettings(ClanID.CLAN);

        // CONFIGURATION CONFIRMATION PATHWAY 1: Player leaves clan or is clanless
        if (mainClanSettings == null)
        {
            if (client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null)
            {
                // CHARACTER SCOPED LOCK: Verify history of THIS specific profile character to prevent cross-alt leaks
                String rsnKey = "last_clan_" + standardizeName(client.getLocalPlayer().getName());
                String storedClan = configManager.getConfiguration("clanactionlogger", rsnKey);

                if (storedClan != null && !storedClan.isEmpty() && storedClan.equals(config.targetClanName().trim()))
                {
                    log.info("[SECURITY PURGE] Detected offline clan removal from target clan {}. Revoking credentials.", storedClan);
                    configManager.setConfiguration("clanactionlogger", "adminWebhookUrl", "");
                    configManager.setConfiguration("clanactionlogger", "enableMonitoring", false);
                    configManager.setConfiguration("clanactionlogger", rsnKey, "");
                }
            }

            if (lastClanName != null)
            {
                log.info("[CLAN GUARD] Target clan context lost. Wiping memory ledger for: " + lastClanName);
                clanRosterMemory.clear();
                pendingAddsRank.clear();
                pendingAddsTime.clear();
                pendingRemovalsTime.clear();
                lastClanName = null;
            }
            return;
        }

        String currentClanName = mainClanSettings.getName();

        // TARGET GATEKEEPER: Case-Sensitive verification check
        if (!currentClanName.equals(config.targetClanName().trim()))
        {
            // CHARACTER SCOPED REGISTRATION: Register that this alt character belongs to an unmonitored clan context
            if (client.getLocalPlayer() != null)
            {
                String rsnKey = "last_clan_" + standardizeName(client.getLocalPlayer().getName());
                configManager.setConfiguration("clanactionlogger", rsnKey, currentClanName);
            }
            return;
        }

        String trackingConfigKey = "last_clan_" + standardizeName(client.getLocalPlayer().getName());

        // CONFIGURATION CONFIRMATION PATHWAY 2: Immediate swap without middleman leave step
        if (lastClanName != null && !lastClanName.equalsIgnoreCase(currentClanName))
        {
            log.info("[CLAN GUARD] Switched clans from {} to {}. Muting fallback net!", lastClanName, currentClanName);

            clanRosterMemory.clear();
            pendingAddsRank.clear();
            pendingAddsTime.clear();
            pendingRemovalsTime.clear();
            configManager.setConfiguration("clanactionlogger", trackingConfigKey, currentClanName);
        }

        // CONFIGURATION CONFIRMATION PATHWAY 3: Mapping clean startup state profiles
        if (lastClanName == null)
        {
            log.info("[CLAN GUARD] Initializing silent baseline profile for clan: " + currentClanName);
            configManager.setConfiguration("clanactionlogger", trackingConfigKey, currentClanName);
        }

        lastClanName = currentClanName;

        boolean isInitialLoad = clanRosterMemory.isEmpty();
        boolean rosterChanged = false;

        // OFFLINE DELTA SYNC TRACKER: Runs once per login session inside a valid clan context
        // Added isAuthorizedForOfflineAudit() to limit summary payloads to specified RSNs
        if (isInitialLoad && config.trackOfflineChanges() && isAuthorizedForOfflineAudit() && !processedSessionStartup && client.getGameState() == GameState.LOGGED_IN)
        {
            processedSessionStartup = true;
            String configKey = "offline_roster_" + standardizeName(currentClanName);
            String savedRosterStr = configManager.getConfiguration("clanactionlogger", configKey);

            if (savedRosterStr != null && !savedRosterStr.isEmpty())
            {
                Map<String, String> savedRosterWithRanks = new HashMap<>();
                for (String token : savedRosterStr.split(","))
                {
                    if (!token.trim().isEmpty() && token.contains(":"))
                    {
                        String[] parts = token.split(":", 2);
                        savedRosterWithRanks.put(parts[0].trim(), parts[1].trim());
                    }
                }

                Set<String> currentRoster = new HashSet<>();
                Map<String, String> currentDisplayNames = new HashMap<>();
                Map<String, String> currentRosterWithRanks = new HashMap<>();
                for (ClanMember member : mainClanSettings.getMembers())
                {
                    String std = standardizeName(member.getName());
                    String rank = getReadableRankName(member.getRank(), mainClanSettings);

                    currentRoster.add(std);
                    currentDisplayNames.put(std, member.getName());
                    currentRosterWithRanks.put(std, rank);
                }

                Set<String> offlineAdded = new HashSet<>();
                for (String name : currentRoster)
                {
                    if (!savedRosterWithRanks.containsKey(name))
                    {
                        String displayName = currentDisplayNames.get(name);
                        String rank = currentRosterWithRanks.get(name);
                        offlineAdded.add(displayName + " (" + rank + ")");
                    }
                }

                Set<String> offlineRemoved = new HashSet<>();
                for (String name : savedRosterWithRanks.keySet())
                {
                    if (!currentRoster.contains(name))
                    {
                        String oldDisplay = displayNameMemory.get(name);
                        if (oldDisplay == null) {
                            oldDisplay = name.substring(0, 1).toUpperCase() + name.substring(1);
                        }
                        offlineRemoved.add(oldDisplay);
                    }
                }

                List<String> offlineRankChanges = new ArrayList<>();
                // TOGGLE CHECK: Now evaluates the new trackOfflineRanks option specifically for the login diff
                if (config.trackOfflineRanks() && config.logPromotions())
                {
                    for (String name : currentRoster)
                    {
                        if (savedRosterWithRanks.containsKey(name))
                        {
                            String oldRank = savedRosterWithRanks.get(name);
                            String newRank = currentRosterWithRanks.get(name);
                            if (!oldRank.equals(newRank))
                            {
                                offlineRankChanges.add(currentDisplayNames.get(name) + "  **" + oldRank + "** ➔ **" + newRank + "**");
                            }
                        }
                    }
                }

                if (!offlineAdded.isEmpty() || !offlineRemoved.isEmpty() || !offlineRankChanges.isEmpty())
                {
                    String localAdminName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "Admin";

                    StringBuilder sb = new StringBuilder();
                    sb.append("Clan change since **").append(localAdminName).append("** offline:\\n\\n**Added:**\\n");
                    if (offlineAdded.isEmpty()) sb.append("None\\n");
                    else {
                        for (String name : offlineAdded) sb.append(name).append("\\n");
                    }

                    sb.append("\\n**Left/Removed:**\\n");
                    if (offlineRemoved.isEmpty()) sb.append("None\\n");
                    else {
                        for (String name : offlineRemoved) sb.append(name).append("\\n");
                    }

                    // Only print the visual text section if the list contains processing results
                    if (config.trackOfflineRanks())
                    {
                        sb.append("\\n**Rank Changes:**\\n");
                        if (offlineRankChanges.isEmpty()) sb.append("None");
                        else {
                            for (String change : offlineRankChanges) sb.append(change).append("\\n");
                        }
                    }

                    sendDiscordWebhook(config.adminWebhookUrl(), "📅 **Offline Roster Audit Summary**", sb.toString());
                }
            }
        }

        Set<String> currentIterationMembers = new HashSet<>();

        for (ClanMember member : mainClanSettings.getMembers())
        {
            String displayName = member.getName();
            String stdName = standardizeName(displayName);
            String currentRankName = getReadableRankName(member.getRank(), mainClanSettings);

            currentIterationMembers.add(stdName);
            displayNameMemory.put(stdName, displayName);

            if (!isInitialLoad)
            {
                if (!clanRosterMemory.containsKey(stdName))
                {
                    rosterChanged = true;
                    long lastChatTime = recentChatActions.getOrDefault(stdName, 0L);
                    if (now - lastChatTime > 5000)
                    {
                        pendingAddsRank.put(stdName, currentRankName);
                        pendingAddsTime.put(stdName, now);
                    }
                }
                else
                {
                    String previousRankName = clanRosterMemory.get(stdName);
                    if (previousRankName != null && !previousRankName.equals(currentRankName))
                    {
                        rosterChanged = true;
                        long lastChatTime = recentChatActions.getOrDefault(stdName, 0L);
                        if (now - lastChatTime > 5000 && config.logPromotions())
                        {
                            sendDiscordWebhook(
                                    config.adminWebhookUrl(),
                                    "🔰 **Clan Rank Update**",
                                    "**" + displayName + "**'s rank was updated from **" + previousRankName + "** to **" + currentRankName + "**."
                            );
                        }
                    }
                }
            }

            if (isInitialLoad)
            {
                rosterChanged = true;
            }
            clanRosterMemory.put(stdName, currentRankName);
        }

        if (!isInitialLoad)
        {
            Set<String> trackedNamesSnapshot = new HashSet<>(clanRosterMemory.keySet());
            for (String stdName : trackedNamesSnapshot)
            {
                if (!currentIterationMembers.contains(stdName))
                {
                    clanRosterMemory.remove(stdName);
                    rosterChanged = true;

                    long lastChatTime = recentChatActions.getOrDefault(stdName, 0L);
                    if (now - lastChatTime > 5000)
                    {
                        pendingRemovalsTime.put(stdName, now);
                    }
                }
            }
        }

        // I/O PERFORMANCE LOCK: Saves both usernames and ranks as colon pairs (username:Rank)
        if (rosterChanged)
        {
            StringBuilder serializedList = new StringBuilder();
            for (Map.Entry<String, String> entry : clanRosterMemory.entrySet())
            {
                if (serializedList.length() > 0) serializedList.append(",");
                serializedList.append(entry.getKey()).append(":").append(entry.getValue());
            }
            configManager.setConfiguration("clanactionlogger", "offline_roster_" + standardizeName(currentClanName), serializedList.toString());
        }

        recentChatActions.entrySet().removeIf(entry -> now - entry.getValue() > 30000);
    }

    /**
     * Helper method to safely translate a ClanRank enum into a text title
     */
    private String getReadableRankName(ClanRank rank, ClanSettings settings)
    {
        if (rank == null) return "Guest";

        if (settings != null)
        {
            ClanTitle title = settings.titleForRank(rank);
            if (title != null && title.getName() != null)
            {
                return title.getName();
            }
        }

        return rank.toString().replace("_", " ");
    }

    /**
     * Non-blocking HTTP POST sender with automatic background deduplicator routing
     */
    private void sendDiscordWebhook(String webhookUrl, String title, String description)
    {
        if (webhookUrl == null || webhookUrl.isEmpty())
        {
            return;
        }

        // AUTOMATIC MIDDLEMAN PROXY ROUTING
        String finalTargetUrl = webhookUrl.trim();
        if (config.enableMonitoring() && (finalTargetUrl.contains("discord.com") || finalTargetUrl.contains("discordapp.com")))
        {
            if (!finalTargetUrl.contains("clan-deduplicator.onrender.com"))
            {
                finalTargetUrl = "https://clan-deduplicator.onrender.com/dedupe?target=" + finalTargetUrl;
            }
        }

        // LOCAL INTELLIJ DEBUG LINE
        log.info("[MIDDLEMAN TEST] Sending payload to: " + finalTargetUrl);

        // ESCAPE SAFETIES: Escape quotes and newlines to preserve complete valid JSON formatting
        String escapedTitle = title.replace("\n", "\\n").replace("\"", "\\\"");
        String escapedDescription = description.replace("\n", "\\n").replace("\"", "\\\"");

        String jsonPayload = "{"
                + "\"embeds\": [{"
                + "\"title\": \"" + escapedTitle + "\","
                + "\"description\": \"" + escapedDescription + "\","
                + "\"color\": 16753920"
                + "}]"
                + "}";

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                jsonPayload
        );

        Request request = new Request.Builder()
                .url(finalTargetUrl)
                .post(body)
                .build();

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.error("Error sending clan event log payload to Discord Webhook", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException
            {
                if (!response.isSuccessful())
                {
                    log.error("Discord Webhook endpoint rejected payload with HTTP status code: " + response.code());
                }
                response.close();
            }
        });
    }

    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ClanActionLogger.class);
        net.runelite.client.RuneLite.main(args);
    }
}