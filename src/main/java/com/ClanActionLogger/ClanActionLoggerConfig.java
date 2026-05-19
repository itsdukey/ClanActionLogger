package com.ClanActionLogger;

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
			keyName = "enableMonitoring",
			name = "Prevent Duplicate Posts",
			description = "Routes logs through a proxy server to prevent duplicate posts from multiple online admins.",
			section = adminSection,
			position = 2,
			warning = "Enabling this routes your clan logs through a remote third-party deduplication proxy server to safely intercept and eliminate double-posts from multiple online admins.\n\n"
					+ "This transmits log text and temporarily exposes your outbound IP address to the external cloud application hosting service.\n\n"
					+ "If disabled, logs flow directly from your client to Discord without cross-admin duplication protection."
	)
	default boolean enableMonitoring() { return false; }

	@ConfigItem(
			keyName = "logKicks",
			name = "Log Kicks & Bans",
			description = "Sends a log when a member is expelled or banned from the clan chat",
			section = adminSection,
			position = 3
	)
	default boolean logKicks() { return true; }

	@ConfigItem(
			keyName = "logInvites",
			name = "Log Invites",
			description = "Sends a log when a member is recruited or invited to the permanent clan roster",
			section = adminSection,
			position = 4
	)
	default boolean logInvites() { return true; }

	@ConfigItem(
			keyName = "logPromotions",
			name = "Log Rank Changes",
			description = "Sends a log when a member is promoted or demoted",
			section = adminSection,
			position = 5
	)
	default boolean logPromotions() { return true; }

	// =========================================================
	// SECTION 2: CLEAN NATIVE SETUP GUIDE (BALANCED LAYOUT)
	// =========================================================
	@ConfigSection(
			name = "📋 Plugin Setup Guide",
			description = "Click to expand or collapse the setup instructions",
			position = 6
	)
	String setupSection = "setupSection";

	@ConfigItem(
			keyName = "displayGuideText",
			name = "<html><body width='175'>"
					+ "<font color='#A0A0A0' face='sans-serif' size='3'>"
					+ "1. Create a Discord text channel.<br>"
					+ "2. Edit Channel > Go to Integrations > Webhooks.<br>"
					+ "3. Copy & Paste the URL into the box above.<br>"
					+ "<hr color='#2A2A2A'>"
					+ "<i>Note: Multi-admin support is active; duplicates filter out automatically.</i><br><br>"
					+ "<i>Real-time only: An admin must be actively online with the plugin enabled to capture events. Clan changes while offline cannot be detected.</i><br><br>"
					+ "</font></body></html>",
			description = "Step-by-step configuration verification checklist",
			position = 0,
			section = setupSection
	)
	default boolean displayGuideText() { return false; }
}