package com.ClanActionLogger;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;

// Clan API Imports
import net.runelite.api.clan.ClanID;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.GameTick;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.util.Text;
import okhttp3.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
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

	// Multi-second historical memory to prevent race conditions across slow ticks
	private final Map<String, Long> recentChatActions = new HashMap<>();

	// Queues for database fallbacks
	private final Map<String, String> pendingAddsRank = new HashMap<>();
	private final Map<String, Long> pendingAddsTime = new HashMap<>();
	private final Map<String, Long> pendingRemovalsTime = new HashMap<>();

	// Context tracker to prevent webhook storms on clan switches
	private String lastClanName = null;

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
	 * ---------------------------------------------------------
	 * ENGINE 1: CHAT PARSING LAYER (Primary Rich Logs)
	 * ---------------------------------------------------------
	 */
	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		String message = Text.removeTags(chatMessage.getMessage()).replace("\u00A0", " ");
		long now = System.currentTimeMillis();

		// MALICIOUS USER PROTECTION: If local user leaves or gets expelled, clear configuration instantly
		if (message.contains("You have left the clan.") || message.contains("You have been expelled from the clan."))
		{
			log.info("[SECURITY ALERT] Local player has left or been expelled from the clan. Purging webhook credentials.");
			configManager.setConfiguration("clanactionlogger", "adminWebhookUrl", "");
			configManager.setConfiguration("clanactionlogger", "enableMonitoring", false);
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

		// CONFIGURATION CONFIRMATION PATHWAY 1: Player leaves clan (Settings evaluate to null)
		if (mainClanSettings == null)
		{
			if (lastClanName != null)
			{
				log.info("[CLAN GUARD] Clan context lost (Left Clan). Wiping memory ledger for: " + lastClanName);
				clanRosterMemory.clear();
				pendingAddsRank.clear();
				pendingAddsTime.clear();
				pendingRemovalsTime.clear();

				// BACKUP SECURITY PURGE: If player leaves clan while actively remaining logged into the game world
				if (client.getGameState() == GameState.LOGGED_IN)
				{
					log.info("[SECURITY PURGE] Actively logged-in player lost clan affiliation. Revoking config credentials.");
					configManager.setConfiguration("clanactionlogger", "adminWebhookUrl", "");
					configManager.setConfiguration("clanactionlogger", "enableMonitoring", false);
				}

				lastClanName = null;
			}
			return;
		}

		// GUARD: Detect if player switched clans or if a new clan context loaded
		String currentClanName = mainClanSettings.getName();

		// CONFIGURATION CONFIRMATION PATHWAY 2: Immediate swap without middleman leave step
		if (lastClanName != null && !lastClanName.equalsIgnoreCase(currentClanName))
		{
			log.info("[CLAN GUARD] Switched clans from {} to {}. Muting fallback net!", lastClanName, currentClanName);

			clanRosterMemory.clear();
			pendingAddsRank.clear();
			pendingAddsTime.clear();
			pendingRemovalsTime.clear();
		}

		// CONFIGURATION CONFIRMATION PATHWAY 3: Mapping clean startup state profiles
		if (lastClanName == null)
		{
			log.info("[CLAN GUARD] Initializing silent baseline profile for clan: " + currentClanName);
		}

		lastClanName = currentClanName;

		boolean isInitialLoad = clanRosterMemory.isEmpty();
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

					long lastChatTime = recentChatActions.getOrDefault(stdName, 0L);
					if (now - lastChatTime > 5000)
					{
						pendingRemovalsTime.put(stdName, now);
					}
				}
			}
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

		String jsonPayload = "{"
				+ "\"embeds\": [{"
				+ "\"title\": \"" + title + "\","
				+ "\"description\": \"" + description + "\","
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