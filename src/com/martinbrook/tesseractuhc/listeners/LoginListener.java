package com.martinbrook.tesseractuhc.listeners;

import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.martinbrook.tesseractuhc.MatchPhase;
import com.martinbrook.tesseractuhc.UhcMatch;
import com.martinbrook.tesseractuhc.UhcParticipant;
import com.martinbrook.tesseractuhc.UhcPlayer;

public class LoginListener implements Listener {
	private UhcMatch m;
	public LoginListener(UhcMatch m) { this.m = m; }


	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		UhcPlayer pl = m.getPlayer(e.getPlayer());

		// If player is op, set them as a spectator
		if (e.getPlayer().isOp()) {
			pl.makeSpectator();
			return;
		}

		// Normal player. Set their vanish correctly
		pl.setVanish();
		
				
		// If the match has not yet started, try to launch the player if necessary
		if (m.getMatchPhase() == MatchPhase.LAUNCHING) {
			// If player is in the match, make sure they are launched. If not, put them at spawn.
			if (pl.isParticipant()) m.launch(pl.getParticipant());
			else pl.teleport(m.getStartingWorld().getSpawnLocation(), null);
			return;
		}
		
		// If match is over, put player in creative, do nothing else
		if (m.getMatchPhase() == MatchPhase.POST_MATCH) {
			e.getPlayer().setGameMode(GameMode.CREATIVE);
			return;
		}

	}


	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		m.setLastLogoutLocation(e.getPlayer().getLocation());
	}
	

	
	@EventHandler
	public void onLogin(PlayerLoginEvent e) {
		// Get a uhcplayer if possible
		UhcParticipant up = m.getUhcParticipant(e.getPlayer());
		
		// If a registered player would be prevented from logging in due to the server being full or them not being whitelisted,
		// let them in anyway.
		if (up != null && (e.getResult() == PlayerLoginEvent.Result.KICK_FULL || e.getResult() == PlayerLoginEvent.Result.KICK_WHITELIST))
			e.allow();
		
		// If player not allowed to login, do no more
		if (e.getResult() != PlayerLoginEvent.Result.ALLOWED) return;
			
		// If player is op, do no more
		if (e.getPlayer().isOp()) return;
		
		// If match isn't in progress, do nothing
		if (m.getMatchPhase() != MatchPhase.MATCH) return;

		// If player was not launched, don't allow them in.
		if (up == null || !up.isLaunched()) {
			e.disallow(PlayerLoginEvent.Result.KICK_OTHER, "The match has already started");
			return;
		}
		
		// If player has died, don't allow them in, if deathban is in effect.
		if (m.getDeathban() && up.isDead()) {
			e.disallow(PlayerLoginEvent.Result.KICK_OTHER, "Dead players cannot rejoin!");
			return;
		}
	}
	
	
	
	
	
}
