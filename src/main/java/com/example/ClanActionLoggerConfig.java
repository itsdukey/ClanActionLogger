package com.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("clanactionlogger")
public interface ClanActionLoggerConfig extends Config
{
	// =========================================================
	// SECTION 1: ADMINISTRATIVE ACTIONS
	// =========================================================
	@ConfigSection(
			name = "Administrative Actions",
			description = "Settings for tracking core roster changes like ranks and kicks",
			position = 0
	)
	String adminSection = "adminSection";

	@ConfigItem(
			keyName = "adminWebhookUrl",
			name = "Admin Webhook URL",
			description = "Paste your plain Discord webhook URL directly here for logs regarding kicks, invites, and ranks.",
			section = adminSection,
			position = 1,
			secret = true
	)
	default String adminWebhookUrl() { return ""; }

	@ConfigItem(
			keyName = "logKicks",
			name = "Log Kicks & Bans",
			description = "Sends a log when a member is expelled or banned from the clan chat",
			section = adminSection,
			position = 2
	)
	default boolean logKicks() { return true; }

	@ConfigItem(
			keyName = "logInvites",
			name = "Log Invites",
			description = "Sends a log when a member is recruited or invited to the permanent clan roster",
			section = adminSection,
			position = 3
	)
	default boolean logInvites() { return true; }

	@ConfigItem(
			keyName = "logPromotions",
			name = "Log Rank Changes",
			description = "Sends a log when a member is promoted or demoted",
			section = adminSection,
			position = 4
	)
	default boolean logPromotions() { return true; }

	// =========================================================
	// SECTION 2: CLEAN NATIVE SETUP GUIDE
	// =========================================================
	@ConfigSection(
			name = "<html><body width='180'>"
					+ "<font color='#A0A0A0' face='sans-serif' size='3'>"
					+ "<b>📋 PLUGIN SETUP GUIDE</b><br><br>"
					+ "1. Create a dedicated text channel in your Discord server.<br><br>"
					+ "2. Go to Channel Settings -> Integrations -> Webhooks and create a webhook.<br><br>"
					+ "3. Click <b>Copy Webhook URL</b>.<br><br>"
					+ "4. Paste your plain webhook URL directly into the <b>Admin Webhook URL</b> box above.<br><br>"
					+ "<hr color='#333333'>"
					+ "<i><b>Note:</b> Multiple admins can run this plugin together. Background servers handle duplication automatically with zero configuration needed.</i>"
					+ "</font></body></html>",
			description = "Configuration guide for your server webhooks",
			position = 5
	)
	String setupSection = "setupSection";
}