package com.example;

import net.runelite.client.externalplugins.ExternalPluginManager;

public class ClanActionLoggerTest
{
	public static void main(String[] args) throws Exception
	{
		// Update this to match your actual main plugin class name
		ExternalPluginManager.loadBuiltin(ClanActionLogger.class);
		ClanActionLogger.main(args);
	}
}