package com.ClanActionLogger;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("clanactionlogger")
public interface ClanActionLoggerConfig extends Config
{
	// =========================================================
	// SECTION 0: WARNING NOTICE
	// =========================================================
	@ConfigSection(
			name = "⚠️ Setup Notice",
			description = "Important information regarding plugin setup",
			position = 0
	)
	String noticeSection = "noticeSection";

	@ConfigItem(
			keyName = "setupNotice",
			name = "<html><font color='#e0a32d'>Please read the guides below<br>to properly set up the plugin.</font></html>",
			description = "Scroll to the bottom of the panel to view the setup instructions.",
			section = noticeSection,
			position = 1
	)
	default boolean setupNotice() { return false; }

	// =========================================================
	// SECTION 1: ADMINISTRATIVE SETUP
	// =========================================================
	@ConfigSection(
			name = "Admin Setup (Required)",
			description = "Settings for routing and core clan identification",
			position = 2
	)
	String adminSetupSection = "adminSetupSection";

	@ConfigItem(
			keyName = "targetClanName",
			name = "Clan Name (Case Sensitive)",
			description = "<html>The exact name of the clan you want to monitor.<br>Must match capitalization perfectly.</html>",
			section = adminSetupSection,
			position = 3
	)
	default String targetClanName() { return ""; }

	@ConfigItem(
			keyName = "adminAccountNames",
			name = "Your RSN Here",
			description = "<html>Authorizes the plugin to report events for the target clan only<br>when you are logged into these specific username(s).<br>Separate multiple names with commas.</html>",
			section = adminSetupSection,
			position = 4
	)
	default String adminAccountNames() { return ""; }

	@ConfigItem(
			keyName = "adminWebhookUrl",
			name = "Discord Webhook URL",
			description = "<html>Paste your plain Discord webhook URL directly here<br>for logs regarding kicks, invites, and ranks.</html>",
			section = adminSetupSection,
			position = 5,
			secret = true
	)
	default String adminWebhookUrl() { return ""; }

	@ConfigItem(
			keyName = "enableMonitoring",
			name = "Multi-User Support (optional)",
			description = "<html>Routes logs through a proxy server to prevent<br>duplicate posts from multiple online admins.</html>",
			section = adminSetupSection,
			position = 6,
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
			position = 7
	)
	String onlineTrackingSection = "onlineTrackingSection";

	@ConfigItem(
			keyName = "logKicks",
			name = "Log Kicks & Bans",
			description = "<html>Sends a log when a member is expelled<br>or banned from the clan chat.</html>",
			section = onlineTrackingSection,
			position = 8
	)
	default boolean logKicks() { return false; }

	@ConfigItem(
			keyName = "logInvites",
			name = "Log Invites",
			description = "<html>Sends a log when a member is recruited<br>or invited to the permanent clan roster.</html>",
			section = onlineTrackingSection,
			position = 9
	)
	default boolean logInvites() { return false; }

	@ConfigItem(
			keyName = "logPromotions",
			name = "Log Rank Changes",
			description = "<html>Sends a log when a member is<br>promoted or demoted.</html>",
			section = onlineTrackingSection,
			position = 10
	)
	default boolean logPromotions() { return false; }

	// =========================================================
	// SECTION 3: OFFLINE TRACKING
	// =========================================================
	@ConfigSection(
			name = "Offline Tracking",
			description = "Settings for tracking changes while admins are offline",
			position = 11
	)
	String offlineTrackingSection = "offlineTrackingSection";

	@ConfigItem(
			keyName = "offlineAuditAccountNames",
			name = "RSN(s) used for Offline Audit",
			description = "<html>Comma-separated list of usernames that will trigger the offline audit summary on login.<br>(recommend only using one)<br><br>Leaving this blank will trigger an offline update webhook output<br>for every account you log into, within the clan stated above.</html>",
			section = offlineTrackingSection,
			position = 12
	)
	default String offlineAuditAccountNames() { return ""; }

	@ConfigItem(
			keyName = "trackOfflineChanges",
			name = "Track Offline Roster Changes",
			description = "<html>Compares the clan roster on login to detect<br>who joined or left while you were offline.</html>",
			section = offlineTrackingSection,
			position = 13
	)
	default boolean trackOfflineChanges() { return false; }

	@ConfigItem(
			keyName = "trackOfflineRanks",
			name = "Track Offline Rank Changes",
			description = "<html>Compares member ranks on login to detect<br>promotions or demotions while you were offline.</html>",
			section = offlineTrackingSection,
			position = 14
	)
	default boolean trackOfflineRanks() { return false; }

	// =========================================================
	// SECTION 4: GUIDE - OPTIONS EXPLANATION
	// =========================================================
	@ConfigSection(
			name = "📋 Guide: Admin Options",
			description = "Click to expand explanations for the administrative settings",
			position = 15,
			closedByDefault = true
	)
	String guideOptionsSection = "guideOptionsSection";

	@ConfigItem(
			keyName = "guideOptionsText",
			name = "<html><body width='175'>"
					+ "<font color='#A0A0A0' face='sans-serif' size='3'>"
					+ "These settings verify your authorization and link the plugin to Discord.<br><br>"
					+ "<b>Clan Name:</b> <i>(Required)</i><br> Locks the plugin to only monitor events for this exact clan.<br><br>"
					+ "<b>Your RSN Here:</b> <i>(Required)</i> Authorizes the plugin to execute only when logged into these specific account(s).<br><br>"
					+ "<b>Discord Webhook URL:</b> <i>(Required)</i> The destination channel where your logs will be delivered.<br><br>"
					+ "<b>Multi-User Support:</b> <i>(Optional)</i> Routes logs through a 3rd party server to prevent duplicate posts when multiple admins are online."
					+ "</font></body></html>",
			description = "Explanation of administrative setup fields",
			position = 16,
			section = guideOptionsSection
	)
	default boolean guideOptionsText() { return false; }

	// =========================================================
	// SECTION 5: GUIDE - ADMINISTRATIVE SETUP
	// =========================================================
	@ConfigSection(
			name = "📋 Guide: Admin Setup",
			description = "Click to expand setup instructions for routing and webhooks",
			position = 17,
			closedByDefault = true
	)
	String guideAdminSection = "guideAdminSection";

	@ConfigItem(
			keyName = "guideAdminText",
			name = "<html><body width='175'>"
					+ "<font color='#A0A0A0' face='sans-serif' size='3'>"
					+ "<b><center>Setup Steps:</center></b><br>"
					+ "<center><b>If you are not Discord Owner/Admin, Contact them for the webhook url and skip to step 3</b></center><br>"
					+ "<center><b><font color='#e0a32d'>Discord Admins</font></b></center><br>"
					+ "1. Create a Discord text channel.<br><br>"
					+ "2. Edit Channel > Go to Integrations > Webhooks.<br><br>"
					+ "<center><b><font color='#e0a32d'>Everyone Else</font></b></center><br>"
					+ "<center>Start Here with the link provided by your Discord Admin</center><br><br>"
					+ "3. Copy & Paste the URL into the box above.<br><br>"
					+ "4. Enter your exact Clan Name into the Target box (Case Sensitive).<br><br>"
					+ "5. Enter the <b>RSN</b> of&nbsp;<b>YOUR ACCOUNT</b> that's in the clan you want to monitor, multiple names supported, separated by commas.<br><br>"
					+ "<center><b><i><font color='#e0a32d'>Optional but recommended</font></i></b></center><br>"
					+ "6. Turn on multi-user support to prevent duplicate Discord webhook messages, this works by sending logs to a third party server to de-duplicate, prior to sending to discord.<br><br>"
					+ "This setting runs on a 60 second window, so if you have 2+ clients and/or users running this plugin in your clan and you do any of the configurable actions, it'll protect from double posting for 60 seconds, on matching actions."
					+ "</font></body></html>",
			description = "Step-by-step configuration verification checklist",
			position = 18,
			section = guideAdminSection
	)
	default boolean guideAdminText() { return false; }

	// =========================================================
	// SECTION 6: GUIDE - ONLINE TRACKING
	// =========================================================
	@ConfigSection(
			name = "📋 Guide: Online Tracking",
			description = "Click to expand instructions for real-time live chat logs",
			position = 19,
			closedByDefault = true
	)
	String guideOnlineSection = "guideOnlineSection";

	@ConfigItem(
			keyName = "guideOnlineText",
			name = "<html><body width='175'>"
					+ "<font color='#A0A0A0' face='sans-serif' size='3'>"
					+ "These options will send discord notifications when you are logged into the Game & Clan on the RSN you entered in \"Admin setup\" .<br><br>"
					+ "<b>Log Kicks & Bans:</b> Sends Discord notification when an Admin kicks, bans, or expels someone from the Clan Chat channel.<br><br>"
					+ "<b>Log Invites:</b> Sends Discord notification when a player is successfully invited into the clan.<br><br>"
					+ "<b>Log Rank Changes:</b> Sends Discord notification when a member is promoted or demoted."
					+ "</font></body></html>",
			description = "Explanation of real-time tracking features",
			position = 20,
			section = guideOnlineSection
	)
	default boolean guideOnlineText() { return false; }

	// =========================================================
	// SECTION 7: GUIDE - OFFLINE TRACKING
	// =========================================================
	@ConfigSection(
			name = "📋 Guide: Offline Tracking",
			description = "Click to expand instructions for offline audit tracking",
			position = 21,
			closedByDefault = true
	)
	String guideOfflineSection = "guideOfflineSection";

	@ConfigItem(
			keyName = "guideOfflineText",
			name = "<html><body width='175'>"
					+ "<font color='#A0A0A0' face='sans-serif' size='3'>"
					+ "Compares your Clan List against your last login session to make a comparison of changes since you were last online.<br><br>"
					+ "<b>RSN(s) for Offline Audit:</b> Specify account(s) name to limit who triggers the audit on login. If left blank, Any account you log into listed in \"Admin Setup\" will trigger an audit.<br><br>"
					+ "<b>Track Roster Changes:</b> Reports members who joined or left the clan entirely while you were offline. <i>*This does not track who removed them while offline</i><br><br>"
					+ "<b>Track Rank Changes:</b> Reports members whose ranks were adjusted while you were offline. * <i>This does not track who made changes while offline</>"
					+ "</font></body></html>",
			description = "Explanation of offline audit features",
			position = 22,
			section = guideOfflineSection
	)
	default boolean guideOfflineText() { return false; }
}