package com.ClanActionLogger;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("clanactionlogger")
public interface ClanActionLoggerConfig extends Config
{
	// =========================================================
	// SECTION 1: ADMINISTRATIVE SETUP
	// =========================================================
	@ConfigSection(
			name = "Administrative Setup",
			description = "Settings for routing and core clan identification",
			position = 0
	)
	String adminSetupSection = "adminSetupSection";

	@ConfigItem(
			keyName = "targetClanName",
			name = "Clan Name (Case Sensitive)",
			description = "The exact name of the clan you want to monitor. Must match capitalization perfectly.",
			section = adminSetupSection,
			position = 1
	)
	default String targetClanName() { return ""; }

	@ConfigItem(
			keyName = "adminAccountNames",
			name = "Your RSN Here",
			description = "Authorizes the plugin to report events for the target clan only when you are logged into these specific username(s). Separate multiple names with commas.",
			section = adminSetupSection,
			position = 2
	)
	default String adminAccountNames() { return ""; }

	@ConfigItem(
			keyName = "adminWebhookUrl",
			name = "Discord Webhook URL",
			description = "Paste your plain Discord webhook URL directly here for logs regarding kicks, invites, and ranks.",
			section = adminSetupSection,
			position = 3,
			secret = true
	)
	default String adminWebhookUrl() { return ""; }

	@ConfigItem(
			keyName = "enableMonitoring",
			name = "Prevent Duplicate Posts",
			description = "Routes logs through a proxy server to prevent duplicate posts from multiple online admins.",
			section = adminSetupSection,
			position = 4,
			warning = "Enabling this routes your clan logs through a remote third-party deduplication proxy server to safely intercept and eliminate double-posts from multiple online admins.\n\n"
					+ "This transmits log text and temporarily exposes your outbound IP address to the external cloud application hosting service.\n\n"
					+ "If disabled, logs flow directly from your client to Discord without cross-admin duplication protection."
	)
	default boolean enableMonitoring() { return false; }

	// =========================================================
	// SECTION 2: ONLINE TRACKING
	// =========================================================
	@ConfigSection(
			name = "Online Tracking",
			description = "Settings for real-time live chat logs",
			position = 5
	)
	String onlineTrackingSection = "onlineTrackingSection";

	@ConfigItem(
			keyName = "logKicks",
			name = "Log Kicks & Bans",
			description = "Sends a log when a member is expelled or banned from the clan chat",
			section = onlineTrackingSection,
			position = 6
	)
	default boolean logKicks() { return false; }

	@ConfigItem(
			keyName = "logInvites",
			name = "Log Invites",
			description = "Sends a log when a member is recruited or invited to the permanent clan roster",
			section = onlineTrackingSection,
			position = 7
	)
	default boolean logInvites() { return false; }

	@ConfigItem(
			keyName = "logPromotions",
			name = "Log Rank Changes",
			description = "Sends a log when a member is promoted or demoted",
			section = onlineTrackingSection,
			position = 8
	)
	default boolean logPromotions() { return false; }

	// =========================================================
	// SECTION 3: OFFLINE TRACKING
	// =========================================================
	@ConfigSection(
			name = "Offline Tracking",
			description = "Settings for tracking changes while admins are offline",
			position = 9
	)
	String offlineTrackingSection = "offlineTrackingSection";

	@ConfigItem(
			keyName = "offlineAuditAccountNames",
			name = "RSN(s) used for Offline Audit",
			description = "Comma-separated list of usernames that will trigger the offline audit summary on login. (recommend only using one) leaving this blank will trigger a offline update webhook output for every account you log into, within the clan stated above",
			section = offlineTrackingSection,
			position = 10
	)
	default String offlineAuditAccountNames() { return ""; }

	@ConfigItem(
			keyName = "trackOfflineChanges",
			name = "Track Offline Roster Changes",
			description = "Compares the clan roster on login to detect who joined or left while you were offline.",
			section = offlineTrackingSection,
			position = 11
	)
	default boolean trackOfflineChanges() { return false; }

	@ConfigItem(
			keyName = "trackOfflineRanks",
			name = "Track Offline Rank Changes",
			description = "Compares member ranks on login to detect promotions or demotions while you were offline.",
			section = offlineTrackingSection,
			position = 12
	)
	default boolean trackOfflineRanks() { return false; }

	// =========================================================
	// SECTION 4: GUIDE - ADMINISTRATIVE SETUP
	// =========================================================
	@ConfigSection(
			name = "📋 Guide: Admin Setup",
			description = "Click to expand setup instructions for routing and webhooks",
			position = 13,
			closedByDefault = true
	)
	String guideAdminSection = "guideAdminSection";

	@ConfigItem(
			keyName = "guideAdminText",
			name = "<html><body width='175'>"
					+ "<font color='#A0A0A0' face='sans-serif' size='3'>"
					+ "1. Create a Discord text channel.<br>"
					+ "2. Edit Channel > Go to Integrations > Webhooks.<br>"
					+ "3. Copy & Paste the URL into the box above.<br>"
					+ "4. Enter your exact Clan Name into the Target box (Case Sensitive).<br>"
					+ "5. Enter the RSN of YOUR ACCOUNT that's in the clan you want to monitor, multiple names supported, separated by commas.<br>"
					+ "<hr color='#2A2A2A'>"
					+ "<i>Optional: Prevent Duplicate Posts routes logs through third party server to remove duplicate posts in discord by multiple admins running plugin Simultaneously</i>"
					+ "</font></body></html>",
			description = "Step-by-step configuration verification checklist",
			position = 14,
			section = guideAdminSection
	)
	default boolean guideAdminText() { return false; }

	// =========================================================
	// SECTION 5: GUIDE - ONLINE TRACKING
	// =========================================================
	@ConfigSection(
			name = "📋 Guide: Online Tracking",
			description = "Click to expand instructions for real-time live chat logs",
			position = 15,
			closedByDefault = true
	)
	String guideOnlineSection = "guideOnlineSection";

	@ConfigItem(
			keyName = "guideOnlineText",
			name = "<html><body width='175'>"
					+ "<font color='#A0A0A0' face='sans-serif' size='3'>"
					+ "Controls real-time notifications sent to Discord when an event occurs in-game.<br><br>"
					+ "<b>Log Kicks & Bans:</b> Triggers when an admin kicks or bans someone from the Clan Chat channel, or expels them from the permanent roster.<br><br>"
					+ "<b>Log Invites:</b> Triggers when a player is successfully recruited or invited into the clan.<br><br>"
					+ "<b>Log Rank Changes:</b> Triggers when a member is promoted or demoted."
					+ "</font></body></html>",
			description = "Explanation of real-time tracking features",
			position = 16,
			section = guideOnlineSection
	)
	default boolean guideOnlineText() { return false; }

	// =========================================================
	// SECTION 6: GUIDE - OFFLINE TRACKING
	// =========================================================
	@ConfigSection(
			name = "📋 Guide: Offline Tracking",
			description = "Click to expand instructions for offline audit tracking",
			position = 17,
			closedByDefault = true
	)
	String guideOfflineSection = "guideOfflineSection";

	@ConfigItem(
			keyName = "guideOfflineText",
			name = "<html><body width='175'>"
					+ "<font color='#A0A0A0' face='sans-serif' size='3'>"
					+ "<i>Compares live rosters against your last saved session cache upon login to compile a summary of changes made while admins were offline.</i><br><br>"
					+ "<b>RSN(s) for Offline Audit:</b> Specify an account name to limit who triggers the audit on login. If left blank, the first authorized admin to log in will trigger the report.<br><br>"
					+ "<b>Track Roster Changes:</b> Reports members who joined or left the clan entirely while you were offline.<br><br>"
					+ "<b>Track Rank Changes:</b> Reports members whose ranks were adjusted while you were offline."
					+ "</font></body></html>",
			description = "Explanation of offline audit features",
			position = 18,
			section = guideOfflineSection
	)
	default boolean guideOfflineText() { return false; }
}