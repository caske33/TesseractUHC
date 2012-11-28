package com.martinbrook.tesseractuhc.notification;

import org.bukkit.ChatColor;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;

import com.martinbrook.tesseractuhc.UhcPlayer;

public class HealingNotification extends UhcNotification {
	private UhcPlayer player;
	private int healAmount;
	private RegainReason cause;



	public HealingNotification(UhcPlayer player, int healAmount, RegainReason cause) {
		super();
		this.player = player;
		this.healAmount = healAmount;
		this.cause = cause;
	}


	@Override
	public String formatForPlayers() {
		if (cause == RegainReason.MAGIC)
			return ChatColor.AQUA + player.getName() + " gained " + (healAmount / 2.0) + " hearts through magic";
		if (cause == RegainReason.MAGIC_REGEN)
			return ChatColor.AQUA + player.getName() + " regenerated " + (healAmount / 2.0) + " hearts through magic";

		return ChatColor.AQUA + player.getName() + " mysteriously regained health";
	}
}
