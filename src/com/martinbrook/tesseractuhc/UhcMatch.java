package com.martinbrook.tesseractuhc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import com.martinbrook.tesseractuhc.countdown.BorderCountdown;
import com.martinbrook.tesseractuhc.countdown.MatchCountdown;
import com.martinbrook.tesseractuhc.countdown.PVPCountdown;
import com.martinbrook.tesseractuhc.notification.ProximityNotification;
import com.martinbrook.tesseractuhc.notification.UhcNotification;
import com.martinbrook.tesseractuhc.startpoint.LargeGlassStartPoint;
import com.martinbrook.tesseractuhc.startpoint.SmallGlassStartPoint;
import com.martinbrook.tesseractuhc.startpoint.UhcStartPoint;
import com.martinbrook.tesseractuhc.util.FileUtils;
import com.martinbrook.tesseractuhc.util.MatchUtils;

public class UhcMatch {

	private World startingWorld;
	private HashMap<Integer, UhcStartPoint> startPoints = new HashMap<Integer, UhcStartPoint>();
	private Location lastNotifierLocation;
	private Location lastDeathLocation;
	private Location lastEventLocation;
	private Location lastLogoutLocation;

	private ArrayList<String> chatScript;
	private Boolean chatMuted = false;
	private Boolean permaday = false;
	private int permadayTaskId;
	
	private ArrayList<UhcStartPoint> availableStartPoints = new ArrayList<UhcStartPoint>();
	private HashMap<String, UhcParticipant> uhcParticipants = new HashMap<String, UhcParticipant>(32);
	private HashMap<String, UhcTeam> uhcTeams = new HashMap<String, UhcTeam>(32);
	
	private ArrayList<String> launchQueue = new ArrayList<String>();
	public static String DEFAULT_MATCHDATA_FILE = "uhcmatch.yml";
	public static String DEFAULT_TEAMDATA_FILE = "uhcteams.yml";
	public static int GOLD_LAYER = 32;
	public static int DIAMOND_LAYER = 16;
	private ArrayList<UhcParticipant> participantsInMatch = new ArrayList<UhcParticipant>();
	private ArrayList<UhcTeam> teamsInMatch = new ArrayList<UhcTeam>();
	private Calendar matchStartTime;
	private int matchTimer = -1;
	private ArrayList<Location> calculatedStarts = null;
	private boolean pvp = false;
	private int spawnKeeperTask = -1;
	private YamlConfiguration md; // Match data
	private TesseractUHC plugin;
	private Server server;
	private Configuration defaults;
	private ItemStack[] bonusChest = new ItemStack[27];
	private MatchPhase matchPhase = MatchPhase.PRE_MATCH;
	private MatchCountdown matchCountdown;
	private BorderCountdown borderCountdown;
	private ArrayList<UhcPOI> uhcPOIs = new ArrayList<UhcPOI>();
	private int proximityCheckerTask;
	private static int PROXIMITY_THRESHOLD_SQUARED = 10000;
	private HashMap<String, UhcSpectator> uhcSpectators = new HashMap<String, UhcSpectator>();
	private HashMap<String, UhcPlayer> allPlayers = new HashMap<String, UhcPlayer>();

	
	public UhcMatch(TesseractUHC plugin, World startingWorld, Configuration defaults) {

		this.startingWorld = startingWorld;
		this.plugin = plugin;
		this.server = plugin.getServer();
		this.defaults = defaults;
		
		this.loadMatchParameters();
		this.setPermaday(true);
		this.setPVP(false);
		this.setVanish();
		this.enableSpawnKeeper();
		this.enablePlayerListUpdater();
		this.loadTeams();
		
	}
	

	/**
	 * Load match data from the default file. If it does not exist, load defaults.
	 */
	public void loadMatchParameters() { 
		try {
			md = YamlConfiguration.loadConfiguration(FileUtils.getDataFile(startingWorld.getWorldFolder(), DEFAULT_MATCHDATA_FILE, true));
			
		} catch (Exception e) {
			md = new YamlConfiguration();
		}
				
		// Load start points
		startPoints.clear();
		availableStartPoints.clear();
		
		List<String> startData = md.getStringList("starts");
		for (String startDataEntry : startData) {
			String[] data = startDataEntry.split(",");
			if (data.length == 4) {
				try {
					int n = Integer.parseInt(data[0]);
					double x = Double.parseDouble(data[1]);
					double y = Double.parseDouble(data[2]);
					double z = Double.parseDouble(data[3]);
					UhcStartPoint sp = createStartPoint (n, startingWorld, x, y, z, false);
					if (sp == null) {
						adminBroadcast("Duplicate start point: " + n);

					}
				} catch (NumberFormatException e) {
					adminBroadcast("Bad start point definition in match data file: " + startDataEntry);
				}

			} else {
				adminBroadcast("Bad start point definition in match data file: " + startDataEntry);
			}
		}
		
		// Load POIs
		uhcPOIs.clear();
		List<String> poiData = md.getStringList("pois");
		for (String poiDataEntry : poiData) {
			String[] data = poiDataEntry.split(",",5);
			if (data.length == 5) {
				try {
					String world = data[0];
					double x = Double.parseDouble(data[1]);
					double y = Double.parseDouble(data[2]);
					double z = Double.parseDouble(data[3]);
					String name = data[4];
					addPOI(world, x, y, z, name);
				} catch (NumberFormatException e) {
					adminBroadcast("Bad poi definition in match data file: " + poiDataEntry);
				}

			} else {
				adminBroadcast("Bad poi definition in match data file: " + poiDataEntry);
			}
		}
		
		setDefaultMatchParameters();
		
		// Convert saved bonus chest into an ItemStack array
		List<?> data = md.getList("bonuschest");
		
		if (data != null) {
		
			for (int i = 0; i < 27; i++) {
				Object o = data.get(i);
				if (o != null && o instanceof ItemStack)
					bonusChest[i] = (ItemStack) o;
			}
		}
	}
	
	

	/**
	 * Set up a default matchdata object
	 */
	private void setDefaultMatchParameters() {
		
		Map<String, Object> mapDefaults = defaults.getValues(true);
		for (Map.Entry<String, Object> m : mapDefaults.entrySet()) {
			if (!md.contains(m.getKey())) {
				md.set(m.getKey(), m.getValue());
			}
		}
		
		this.saveMatchParameters();
	}

	
	/**
	 * Save start points to the default file
	 * 
	 * @return Whether the operation succeeded
	 */
	public void saveMatchParameters() {
		ArrayList<String> startData = new ArrayList<String>();
		for (UhcStartPoint sp : startPoints.values()) {
			startData.add(sp.getNumber() + "," + sp.getX() + "," + sp.getY() + "," + sp.getZ());
		}
		
		md.set("starts",startData);
		
		ArrayList<String> poiData = new ArrayList<String>();
		for (UhcPOI poi : uhcPOIs) {
			poiData.add(poi.getWorld().getName() + "," + poi.getX() + "," + poi.getY() + "," + poi.getZ() + "," + poi.getName());
		}
		
		md.set("pois",poiData);
		
		try {
			md.save(FileUtils.getDataFile(startingWorld.getWorldFolder(), DEFAULT_MATCHDATA_FILE, false));
		} catch (IOException e) {
			adminBroadcast(TesseractUHC.ALERT_COLOR + "Warning: Could not save match data");
		}
	}


	/**
	 * Reset all match parameters to default values
	 */
	public void resetMatchParameters() {
		startPoints.clear();
		availableStartPoints.clear();
		uhcPOIs.clear();
		md = new YamlConfiguration();
		this.setDefaultMatchParameters();
	}

	/**
	 * Save players and teams to the default location.
	 */
	public void saveTeams() {
		YamlConfiguration teamData = new YamlConfiguration();

		for(Map.Entry<String, UhcTeam> e : this.uhcTeams.entrySet()) {
			ConfigurationSection teamSection = teamData.createSection(e.getValue().getIdentifier());
			
			ArrayList<String> participants = new ArrayList<String>();
			for (UhcParticipant up : e.getValue().getMembers()) participants.add(up.getName());
			
			teamSection.set("name", e.getValue().getName());
			teamSection.set("players", participants);
		}
		
		
		try {
			teamData.save(FileUtils.getDataFile(startingWorld.getWorldFolder(), DEFAULT_TEAMDATA_FILE, false));
		} catch (IOException e) {
			adminBroadcast(TesseractUHC.ALERT_COLOR + "Warning: Could not save team data");

		}
	}
	
	
	/**
	 * Load players and teams from the default location.
	 */
	public void loadTeams() {
		if (!clearTeams()) {
			adminBroadcast(TesseractUHC.ALERT_COLOR + "Warning: Could not remove existing team/player data");
			return;
		}
		
		
		YamlConfiguration teamData;
		try {
			teamData = YamlConfiguration.loadConfiguration(FileUtils.getDataFile(startingWorld.getWorldFolder(), DEFAULT_TEAMDATA_FILE, true));

		} catch (Exception e) {
			return;
		}
		
		for(String teamIdentifier : teamData.getKeys(false)) {
			ConfigurationSection teamSection = teamData.getConfigurationSection(teamIdentifier);
			String teamName = teamSection.getString("name");
			if (!addTeam(teamIdentifier, teamName)) {
				adminBroadcast(TesseractUHC.ALERT_COLOR + "Warning: failed to create team " + teamName);
			} else {
				List<String> teamMembers = teamSection.getStringList("players");
				if (teamMembers == null) {
					adminBroadcast(TesseractUHC.ALERT_COLOR + "Warning: team has no members: " + teamName);
				} else {
					for (String participantName : teamMembers) {
						if (!addParticipant(participantName, teamIdentifier))
							adminBroadcast(TesseractUHC.ALERT_COLOR + "Warning: failed to add player: " + participantName);
					}
				}
			}
		}
	}
	
	public boolean clearTeams() {
		if (matchPhase != MatchPhase.PRE_MATCH) return false;
		
		this.uhcParticipants.clear();
		this.uhcTeams.clear();
		this.teamsInMatch.clear();
		this.participantsInMatch.clear();
		return true;
	}
	
	/**
	 * Send a message to all ops
	 * 
	 * @param string The message to be sent
	 */
	public void adminBroadcast(String string) {
		broadcast(string,Server.BROADCAST_CHANNEL_ADMINISTRATIVE);
	}
	
	/**
	 * Send a message to all players on the server
	 * 
	 * @param string The message to be sent
	 */
	public void broadcast(String string) {
		broadcast(string,Server.BROADCAST_CHANNEL_USERS);
	}

	/**
	 * Send a message to specific players on the server
	 * 
	 * @param string The message to be sent
	 * @param permission The permission level to send the message to
	 */
	private void broadcast(String string, String permission) {
		server.broadcast(string, permission);
	}
	
	/**
	 * Set time to midday, to keep permaday in effect.
	 */
	private void keepPermaday() {
		this.startingWorld.setTime(6000);
	}

	/**
	 * Enables / disables PVP on overworld
	 * 
	 * @param pvp Whether PVP is to be allowed
	 */
	public void setPVP(boolean pvp) {
		this.pvp = pvp;
		startingWorld.setPVP(pvp);

		adminBroadcast(TesseractUHC.OK_COLOR + "PVP has been " + (pvp ? "enabled" : "disabled") + "!");
	
	}
	
	/**
	 * @return Whether PVP is enabled
	 */
	public boolean getPVP() {
		return this.pvp;
	}

	/**
	 * Enables / disables permaday
	 * 
	 * @param p whether permaday is to be on or off
	 */
	public void setPermaday(boolean p) {
		if (p == permaday) return;
		
		this.permaday=p;
	
		adminBroadcast(TesseractUHC.OK_COLOR + "Permaday has been " + (permaday ? "enabled" : "disabled") + "!");
		
		
		if (permaday) {
			startingWorld.setTime(6000);
			permadayTaskId = server.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
				public void run() {
					keepPermaday();
				}
			}, 1200L, 1200L);
			
		} else {
			server.getScheduler().cancelTask(permadayTaskId);
		}
	}
	

	/**
	 * @return Whether permaday is enabled
	 */
	public boolean getPermaday() {
		return this.permaday;
	}



	/**
	 * Try to find a start point from a user-provided search string.
	 * 
	 * @param searchParam The string to search for - a player name, or a start number may be sent
	 * @return The start point, or null if not found.
	 */
	public UhcStartPoint findStartPoint(String searchParam) {
		UhcParticipant up = this.getUhcParticipant(searchParam);
		if (up != null) {
			// Argument matches a participant
			return up.getStartPoint();
			
		} else {
			try {
				int i = Integer.parseInt(searchParam);
				return startPoints.get(i);
			} catch (Exception e) {
				return null;
			}
		}
		
	}

	/**
	 * Set a death location for teleporters
	 * 
	 * @param l The location to be stored
	 */
	public void setLastDeathLocation(Location l) {
		lastDeathLocation = l;
		lastEventLocation = l;
	}

	/**
	 * Set a notification location for teleporters
	 * 
	 * @param l The location to be stored
	 */
	private void setLastNotifierLocation(Location l) {
		lastNotifierLocation = l;
		lastEventLocation = l;
	}

	/**
	 * Set a logout location for teleporters
	 * 
	 * @param l The location to be stored
	 */
	public void setLastLogoutLocation(Location l) {
		lastLogoutLocation = l;
	}
	

	
	/**
	 * Remove all hostile mobs in the overworld
	 */
	public void butcherHostile() {
		for (Entity entity : startingWorld.getEntitiesByClass(LivingEntity.class)) {
			if (entity instanceof Monster || entity instanceof MagmaCube || entity instanceof Slime || entity instanceof EnderDragon
					|| entity instanceof Ghast)
				entity.remove();
		}
	}

	
	/**
	 * Start the match
	 * 
	 * Butcher hostile mobs, turn off permaday, turn on PVP, put all participants in survival and reset all participants.
	 */
	public void startMatch() {
		this.matchCountdown = null;
		matchPhase = MatchPhase.MATCH;
		startingWorld.setTime(0);
		butcherHostile();
		for (UhcParticipant up : this.getUhcParticipants()) up.start();
		setPermaday(false);
		startMatchTimer();
		setVanish();
		broadcast("GO!");
		
		// Set up pvp countdown
		if (getNopvp() > 0) {
			new PVPCountdown(getNopvp(), plugin, this);
		} else {
			setPVP(true);
		}
		enableProximityChecker();
	}
	
	/**
	 * End the match
	 * 
	 * Announce the total match duration
	 */
	public void endMatch() {
		broadcast(matchTimeAnnouncement(true));
		stopMatchTimer();
		matchPhase = MatchPhase.POST_MATCH;
		// Put all players into creative
		for (Player p : server.getOnlinePlayers()) p.setGameMode(GameMode.CREATIVE);
		setVanish();
		disableProximityChecker();
		server.getScheduler().cancelTasks(plugin);

	}
	

	public boolean worldReduce(int nextRadius) {
		return MatchUtils.setWorldRadius(startingWorld,nextRadius);
	}
	
	
	/**
	 * Starts the match timer
	 */
	private void startMatchTimer() {
		matchStartTime = Calendar.getInstance();
		matchTimer = server.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
			public void run() {
				doMatchProgressAnnouncement();
			}
		}, 1200L, 1200L);
	}
	
	/**
	 * Stops the match timer
	 */
	private void stopMatchTimer() {
		if (matchTimer != -1) {
			server.getScheduler().cancelTask(matchTimer);
		}
	}
	
	/**
	 * Display the current match time if it is a multiple of 30.
	 */
	private void doMatchProgressAnnouncement() {
		long matchTime = MatchUtils.getDuration(matchStartTime, Calendar.getInstance()) / 60;
		if (matchTime % 30 == 0 && matchTime > 0) {
			broadcast(matchTimeAnnouncement(false));
		}
	}


	/**
	 * Plays a chat script
	 * 
	 * @param filename The file to read the chat script from
	 * @param muteChat Whether other chat should be muted
	 */
	public void playChatScript(String filename, boolean muteChat) {
		if (muteChat) this.setChatMuted(true);
		chatScript = FileUtils.readFile(filename);
		if (chatScript != null)
			continueChatScript();
	}
	
	/**
	 * Output next line of current chat script, unmuting the chat if it's finished.
	 */
	private void continueChatScript() {
		broadcast(ChatColor.GREEN + chatScript.get(0));
		chatScript.remove(0);
		if (chatScript.size() > 0) {
			server.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
				public void run() {
					continueChatScript();
				}
			}, 30L);
		} else {
			this.setChatMuted(false);
			chatScript = null;
		}
		
	}
	
	/**
	 * Get all participants currently registered with the game
	 * 
	 * @return All registered participants
	 */
	public Collection<UhcParticipant> getUhcParticipants() {
		return uhcParticipants.values();
	}
	
	private UhcTeam createTeam(String identifier, String name, UhcStartPoint startPoint) {
		// Fail if team exists
		if (existsTeam(identifier)) return null;
		
		UhcTeam team = new UhcTeam(identifier, name, startPoint);
		uhcTeams.put(identifier.toLowerCase(), team);
		return team;
	}
	
	/**
	 * Create a new participant and add them to the game
	 * 
	 * @param name The participant's name
	 * @param sp The participant's start point
	 * @return The newly created participant, or null if they already existed
	 */
	private UhcParticipant createParticipant(String name, UhcTeam team) {
		// Fail if player exists
		if (existsUhcParticipant(name)) return null;
		
		UhcParticipant up = new UhcParticipant(name, team, this);
		team.addMember(up);
		uhcParticipants.put(name.toLowerCase(), up);
		return up;
	}
	

	
	/**
	 * Check if a player exists
	 * 
	 * @param name Player name to check (case insensitive)
	 * @return Whether the player exists
	 */
	public boolean existsUhcParticipant(String name) {
		return uhcParticipants.containsKey(name.toLowerCase());
	}
	
	/**
	 * Check if a team exists
	 * 
	 * @param identifier The team identifier to check (case-insensitive)
	 * @return Whether the team exists
	 */
	public boolean existsTeam(String identifier) {
		return uhcTeams.containsKey(identifier.toLowerCase());
	}

	/**
	 * Get a specific UhcTeam by identifier
	 * 
	 * @param name The exact name of the player to be found  (case insensitive)
	 * @return The UhcPlayer, or null if not found
	 */
	public UhcTeam getTeam(String identifier) {
		return uhcTeams.get(identifier.toLowerCase());
	}
	
	/**
	 * Get a specific UhcParticipant by name
	 * 
	 * @param name The exact name of the player to be found  (case insensitive)
	 * @return The UhcParticipant, or null if not found
	 */
	public UhcParticipant getUhcParticipant(String name) {
		return uhcParticipants.get(name.toLowerCase());
	}

	
	/**
	 * Get a specific UhcParticipant matching the given Bukkit Player
	 * 
	 * @param playerToGet The Player to look for
	 * @return The UhcParticipant, or null if not found
	 */
	public UhcParticipant getUhcParticipant(Player playerToGet) {
		return getUhcParticipant(playerToGet.getName());
	}
	
	public UhcParticipant getUhcParticipant(int index) {
		return participantsInMatch.get(index);
	}
	
	/**
	 * Add the team as detailed, and assign them a start point
	 * 
	 * @param identifier The team's short identifier
	 * @param name The full name of the team
	 * @return success or failure
	 */
	public boolean addTeam(String identifier, String name) {
		// Check that there are available start points
		if (!roomForAnotherTeam()) return false;
		
		// Check that the team doesn't exist already 
		if (existsTeam(identifier)) return false;
		
		// Get them a start point
		Random rand = new Random();
		UhcStartPoint start = availableStartPoints.remove(rand.nextInt(availableStartPoints.size()));
		
		// Create the team
		UhcTeam team = createTeam(identifier, name, start);
		start.setTeam(team);
		teamsInMatch.add(team);
		
		start.makeSign();
		start.fillChest(bonusChest);

		return true;
	}
	
	/**
	 * Checks if there is room for another team to be created
	 * 
	 * @return Whether there is another start point
	 */
	public boolean roomForAnotherTeam() {
		return (availableStartPoints.size()>0);
	}

	/**
	 * Create a participant and add them to the specified team
	 * 
	 * @param name The name of the player to add
	 * @param teamIdentifier The team to add them to
	 * @return success or failure
	 */
	public boolean addParticipant(String name, String teamIdentifier) {
		// If player is op, fail
		if (server.getOfflinePlayer(name).isOp()) return false;
		
		// If player already exists, fail 
		if (existsUhcParticipant(name)) return false;
		
		// Get the team
		UhcTeam team = getTeam(teamIdentifier);
		
		// If team doesn't exist, fail
		if (team == null) return false;
		
		// If player is a spectator or an admin, make them not one
		Player p = server.getPlayerExact(name);
		if (p != null) {
			removeSpectator(p);
		} else {
			removeSpectator(name);
		}
		
		// Create the player
		UhcParticipant up = createParticipant(name, team);

		// If player wasn't created, fail
		if (up == null) return false;
		
		participantsInMatch.add(up);
		return true;
	}
	
	/**
	 * Add the supplied player as a team of one, creating the team and assigning it a start point
	 * 
	 * @param name The name of the player to add
	 * @return success or failure
	 */
	public boolean addSoloParticipant (String name) {
		// If player is op, fail
		if (server.getOfflinePlayer(name).isOp()) return false;
		
		// If player already exists, fail 
		if (existsUhcParticipant(name)) return false;
		
		// If player is a spectator or an admin, make them not one
		Player p = server.getPlayerExact(name);
		if (p != null) {
			removeSpectator(p);
		} else {
			removeSpectator(name);
		}
		
		// Create a team of one for the player
		String teamName = name; 
		if (!addTeam(teamName, teamName)) return false;
		
		// Add the new player to the team of one, and return the result
		return addParticipant(name, teamName);
		
	}
	
	
	/**
	 * Launch the specified player only
	 * 
	 * @param p The UhcParticipant to be launched
	 * @return success or failure
	 */
	public boolean launch(UhcParticipant up) {

		// If player already launched, ignore
		if (up.isLaunched()) return false;
		
		// Get the player
		Player p = server.getPlayerExact(up.getName());
		
		// If player not online, return
		if (p == null) return false;
		
		
		sendToStartPoint(p);
		
		up.setLaunched(true);

		return true;


		
	}
	
	/**
	 * Re-teleport the specified player
	 * 
	 * @param p The player to be relaunched
	 */
	public boolean sendToStartPoint(Player p) {
		UhcParticipant up = getUhcParticipant(p);
		if (up == null) return false;
		return (up.sendToStartPoint());
		
	}
	
	/**
	 * Remove the given player, removing them from the match, and their team.
	 * 
	 * The player will be teleported back to spawn if they are still on the server
	 * 
	 * @param name The player to be removed
	 * @return Whether the removal succeeded
	 */
	public boolean removeParticipant(String name) {
		UhcParticipant up = uhcParticipants.remove(name.toLowerCase());
		
		if (up != null) {
			// Remove them from their team
			up.getTeam().removeMember(up);
			
			// Remove them from the match
			participantsInMatch.remove(up);
			
			// If match is ffa, also remove the empty team
			if (isFFA())
				this.removeTeam(name);
			
			
			if (matchPhase == MatchPhase.MATCH) {
				broadcast(ChatColor.GOLD + up.getName() + " has left the match");
				broadcastMatchStatus();
			}
			up.teleport(startingWorld.getSpawnLocation());
			return true;
		} else return false;
	}
	
	/**
	 * Remove the given team, which must be empty, from the match, and free up its start point.
	 * 
	 * @param identifier The team to remove
	 * @return Whether the removal succeeded
	 */
	public boolean removeTeam(String identifier) {
		UhcTeam team = getTeam(identifier);
		
		// If team not found, fail
		if (team == null) return false;
		
		// If team not empty, fail
		if (team.playerCount()>0) return false;
		
		uhcTeams.remove(identifier.toLowerCase());
		teamsInMatch.remove(team);
		
		// Free up the start point
		UhcStartPoint sp = team.getStartPoint();
		sp.setTeam(null);
		sp.makeSign();
		sp.emptyChest();
		availableStartPoints.add(sp);
		
		return true;
	}


	/**
	 * Start the launching phase, and launch all players who have been added to the game
	 */
	public void launchAll() {
		if (matchPhase == MatchPhase.PRE_MATCH) matchPhase = MatchPhase.LAUNCHING;
		disableSpawnKeeper();
		if (isUHC()) setupModifiedRecipes();
		setVanish(); // Update vanish status
		butcherHostile();
		sortParticipantsInMatch();

		// Add all players to the launch queue
		for(UhcParticipant up : getUhcParticipants())
			if (!up.isLaunched()) addToLaunchQueue(up);

		// Begin launching
		launchNext();
	}
	

	private void sortParticipantsInMatch() {
		// Refill the playersInMatch arraylist, sorting players into order.
		participantsInMatch.clear();
		for (UhcTeam team : teamsInMatch)
			for (UhcParticipant up : team.getMembers())
				participantsInMatch.add(up);
		
		
		
	}


	private void launchNext() {
		if (this.launchQueue.size()==0) {
			adminBroadcast(TesseractUHC.OK_COLOR + "Launching complete");
			return;
		}
		
		String playerName = this.launchQueue.remove(0);
		UhcParticipant up = this.getUhcParticipant(playerName);
		launch(up);
		
		server.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
			public void run() {
				launchNext();
			}
		}, 20L);
		
	}

	private void addToLaunchQueue(UhcParticipant up) {
		this.launchQueue.add(up.getName().toLowerCase());
	}
	
	private void enablePlayerListUpdater() {
		server.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
			public void run() {
				runPlayerListUpdater();
			}
		}, 60L, 60L);
	}


	private void runPlayerListUpdater() {
		// Update the player list for all players
		for(Player p : server.getOnlinePlayers()) {
			UhcParticipant up = getUhcParticipant(p);
			if (up != null && !isSpectator(p)) {
				if (!up.isDead())
					setSurvivorPlayerListName(p);
				else
					setDeceasedPlayerListName(p);
			} else {
				setNonPlayerPlayerListName(p);
			}
		}
	}
	

	private void enableSpawnKeeper() {
		spawnKeeperTask = server.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
			public void run() {
				runSpawnKeeper();
			}
		}, 40L, 40L);
	}

	private void disableSpawnKeeper() {
		server.getScheduler().cancelTask(spawnKeeperTask);
	}
	
	private void runSpawnKeeper() {
		for (Player p : server.getOnlinePlayers()) {
			if (!isAdmin(p) && p.getLocation().getY() < 128) {
				p.teleport(startingWorld.getSpawnLocation());
			}
			p.setHealth(20);
			p.setFoodLevel(20);
			p.setExhaustion(0.0F);
			p.setSaturation(5.0F);
		}
	}
	
	private void enableProximityChecker() {
		proximityCheckerTask = server.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
			public void run() {
				runProximityChecker();
			}
		}, 200L, 600L);
	}

	private void disableProximityChecker() {
		server.getScheduler().cancelTask(proximityCheckerTask);
	}
	
	private void runProximityChecker() {
		// Cycle through all UhcPlayers
		for (int i = 0; i < participantsInMatch.size(); i++) {
			UhcParticipant up = participantsInMatch.get(i);

			// Check proximity to other players (only if not on same team).
			int j = i + 1;
			while (j < participantsInMatch.size()) {
				// Check proximity of player up to player j
				UhcParticipant up2 = participantsInMatch.get(j);
				if (up.getTeam() != up2.getTeam()) {
					if (checkProximity(up, up2)) {
						sendAdminNotification(new ProximityNotification(up, up2), server.getPlayerExact(up.getName()).getLocation());
					}
				}
				j++;
			}

			// Check proximity to all POIs. 
			for (UhcPOI poi : uhcPOIs) {
				if (checkProximity(up, poi)) {
					sendAdminNotification(new ProximityNotification(up, poi), server.getPlayerExact(up.getName()).getLocation());
				}
			}
			
		}
	}
	
	private boolean checkProximity(UhcParticipant player, UhcParticipant enemy) {
		Player p1 = server.getPlayerExact(player.getName());
		Player p2 = server.getPlayerExact(enemy.getName());
		if (p1 == null || p2 == null) return false;
		
		if (player.isNearTo(enemy)) {
			if (p1.getLocation().distanceSquared(p2.getLocation()) >= (PROXIMITY_THRESHOLD_SQUARED))
				player.setNearTo(enemy, false);
			return false;
		} else {
			if (p1.getLocation().distanceSquared(p2.getLocation()) < (PROXIMITY_THRESHOLD_SQUARED)) {
				player.setNearTo(enemy, true);
				return true;
			}
			return false;
		}
	}
	
	private boolean checkProximity(UhcParticipant player, UhcPOI poi) {
		Player p1 = server.getPlayerExact(player.getName());
		if (p1 == null) return false;
		
		if (player.isNearTo(poi)) {
			if (p1.getLocation().distanceSquared(poi.getLocation()) >= (PROXIMITY_THRESHOLD_SQUARED))
				player.setNearTo(poi, false);
			return false;
		} else {
			if (p1.getLocation().distanceSquared(poi.getLocation()) < (PROXIMITY_THRESHOLD_SQUARED)) {
				player.setNearTo(poi, true);
				return true;
			}
			return false;
		}
	}
	
	/**
	 * Create a new start point at a given location
	 * 
	 * @param number The start point's number
	 * @param l The location of the start point
	 * @param buildTrough Whether to add a starting trough
	 * @return The created start point
	 */
	private UhcStartPoint createStartPoint(int number, Location l, Boolean buildTrough) {
		// Check there is not already a start point with this number		
		if (startPoints.containsKey(number))
			return null;
		
		UhcStartPoint sp;
		if (this.isFFA())
			sp = new SmallGlassStartPoint(number, l, true);
		else
			sp = new LargeGlassStartPoint(number, l, true);
		
		if (buildTrough) sp.buildStartingTrough();
		
		startPoints.put(number,  sp);
		availableStartPoints.add(sp);
		
		return sp;
	}
	
	/**
	 * Create a new start point at a given location, with optional starting trough
	 * 
	 * @param number The start point's number
	 * @param world The world to create the start point
	 * @param x x coordinate of the start point
	 * @param y y coordinate of the start point
	 * @param z z coordinate of the start point
	 * @param buildTrough Whether to add a starting trough
	 * @return The created start point
	 */
	private UhcStartPoint createStartPoint(int number, World world, Double x, Double y, Double z, Boolean buildTrough) {
		return createStartPoint(number, new Location(world, x, y, z), buildTrough);
	}
	
	/**
	 * Create a new start point at a given location, giving it the next available number
	 * 
	 * @param l The location of the start point
	 * @param buildTrough Whether to add a starting trough
	 * @return The created start point
	 */
	private UhcStartPoint createStartPoint(Location l, Boolean buildTrough) {
		return createStartPoint(getNextAvailableStartNumber(), l, buildTrough);
	}

	/**
	 * Add a new start point at a given location, giving it the next available number.
	 * 
	 * This function will also update the saved match data.
	 * 
	 * @param x x coordinate of the start point
	 * @param y y coordinate of the start point
	 * @param z z coordinate of the start point
	 * @param buildTrough Whether to add a starting trough
	 * @return The created start point
	 */
	public UhcStartPoint addStartPoint(Double x, Double y, Double z, Boolean buildTrough) {
		UhcStartPoint sp = createStartPoint(new Location(startingWorld, x, y, z), buildTrough);
		if (sp != null) this.saveMatchParameters();
		return sp;
	}
	
		
	/**
	 * Determine the lowest unused start number
	 * 
	 * @return The lowest available start point number
	 */
	public int getNextAvailableStartNumber() {
		int n = 1;
		while (startPoints.containsKey(n))
			n++;
		return n;
	}
	

	

	
	/**
	 * @return Whether chat is currently muted
	 */
	public boolean isChatMuted() {
		return chatMuted;
	}
	
	/**
	 * Mute or unmute chat
	 * 
	 * @param muted Status to be set
	 */
	public void setChatMuted(Boolean muted) {
		chatMuted = muted;
	}


	/**
	 * Apply the mining fatigue game mechanic
	 * 
	 * Players who mine stone below a certain depth increase their hunger
	 * 
	 * @param player The player to act upon
	 * @param blockY The Y coordinate of the mined block
	 */
	public void doMiningFatigue(Player player, int blockY) {
		Double exhaustion = 0.0;
		
		if (blockY < DIAMOND_LAYER) {
			exhaustion = this.getMiningFatigueDiamond(); 
		} else if (blockY < GOLD_LAYER) {
			exhaustion = this.getMiningFatigueGold();
		}
		
		if (exhaustion > 0)
			player.setExhaustion((float) (player.getExhaustion() + exhaustion));

				
	}


	/**
	 * @return The number of players still in the match
	 */
	public int countParticipantsInMatch() {
		return participantsInMatch.size();
	}


	/**
	 * @return The number of teams still in the match
	 */
	public int countTeamsInMatch() {
		return teamsInMatch.size();
	}

	
	public void handleParticipantDeath(final UhcParticipant up) {
		server.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
			public void run() {
				processParticipantDeath(up);
			}
		});
		
	}

	/**
	 * Process the death of a player
	 * 
	 * @param up The player who died
	 */
	private void processParticipantDeath(UhcParticipant up) {
		// Set them as dead
		up.setDead(true);
		
		// Reduce survivor counts
		participantsInMatch.remove(up);
		if (isFFA() && countParticipantsInMatch() == 1) {
			processVictory(participantsInMatch.get(0));
			return;
		}
			
			
		UhcTeam team = up.getTeam();
		
		if (team != null && team.aliveCount()<1) {
			teamsInMatch.remove(team);
			if (!isFFA() && countTeamsInMatch() == 1) {
				processVictory(teamsInMatch.get(0));
				return;
			}
		}
		
		if (countParticipantsInMatch() == 0) endMatch();
		
		broadcastMatchStatus();
	}

	private void processVictory(UhcTeam winner) {
		broadcast(ChatColor.GOLD + "The winner is: " + winner.getName() + "!");
		endMatch();
		
	}

	private void processVictory(UhcParticipant winner) {
		broadcast(ChatColor.GOLD + "The winner is: " + winner.getName() + "!");
		endMatch();
		
	}

	/**
	 * Publicly announce how many players are still in the match 
	 */
	private void broadcastMatchStatus() {
		// Make no announcement if match has ended
		if (matchPhase != MatchPhase.POST_MATCH)
			broadcast(matchStatusAnnouncement());
		
	}

	/**
	 * Get the text of a match time announcement
	 * 
	 * @param precise Whether to give a precise time (00:00:00) instead of (xx minutes)
	 * @return Current match time as a nicely-formatted string
	 */
	public String matchTimeAnnouncement(boolean precise) {
		if (matchPhase == MatchPhase.PRE_MATCH || matchPhase == MatchPhase.LAUNCHING)
			return TesseractUHC.MAIN_COLOR + "Match time: " + TesseractUHC.SIDE_COLOR + MatchUtils.formatDuration(0, precise);
		else
			return TesseractUHC.MAIN_COLOR + "Match time: " + TesseractUHC.SIDE_COLOR + MatchUtils.formatDuration(matchStartTime, Calendar.getInstance(), precise);

	}
	/**
	 * Get the text of a match status announcement
	 * 
	 * @return List of remaining players / teams
	 */
	public String matchStatusAnnouncement() {
		if (this.matchPhase == MatchPhase.PRE_MATCH) {
			if (this.isFFA()) {
				int c = countParticipantsInMatch();
				return c + " player" + (c != 1 ? "s have" : " has") + " joined";
			} else {
				int c = countTeamsInMatch();
				return c + " team" + (c != 1 ? "s have" : " has") + " joined";
			}
		}
		if (this.isFFA()) {
			int c = countParticipantsInMatch();
			if (c == 0)
				return "There are no surviving players";
			if (c == 1)
				return "1 surviving player: " + participantsInMatch.get(0).getName();
			
			if (c <= 4) {
				String message = c + " surviving players: ";
				for (UhcParticipant up : participantsInMatch)
					message += up.getName() + ", ";
				
				return message.substring(0, message.length()-2);
			}
			
			return c + " surviving players";

		} else {
			int c = countTeamsInMatch();
			if (c == 0)
				return "There are no surviving teams";
			if (c == 1)
				return "1 surviving team: " + teamsInMatch.get(0).getName();
			
			if (c <= 4) {
				String message = c + " surviving teams: ";
				for (UhcTeam t : teamsInMatch)
					message += t.getName() + ", ";
				
				return message.substring(0, message.length()-2);
			}
			
			return c + " surviving teams";

		}
	}
	



	/**
	 * Show a spectator the contents of a player's inventory.
	 * 
	 * @param spectator The player who is asking to see the inventory
	 * @param player The player being observed
	 */
	public boolean showInventory(Player spectator, Player player) {

		Inventory i = getInventoryView(player);
		if (i == null) return false;
		
		spectator.openInventory(i);
		return true;
	}

	/**
	 * Gets a copy of a player's current inventory, including armor/health/hunger details.
	 *
	 * @author AuthorBlues
	 * @param player The player to be viewed
	 * @return inventory The player's inventory
	 *
	 */
	public Inventory getInventoryView(Player player)
	{

		PlayerInventory pInventory = player.getInventory();
		Inventory inventoryView = Bukkit.getServer().createInventory(null,
			pInventory.getSize() + 9, player.getDisplayName() + "'s Inventory");

		ItemStack[] oldContents = pInventory.getContents();
		ItemStack[] newContents = inventoryView.getContents();

		for (int i = 0; i < oldContents.length; ++i)
			if (oldContents[i] != null) newContents[i] = oldContents[i];

		newContents[oldContents.length + 0] = pInventory.getHelmet();
		newContents[oldContents.length + 1] = pInventory.getChestplate();
		newContents[oldContents.length + 2] = pInventory.getLeggings();
		newContents[oldContents.length + 3] = pInventory.getBoots();

		newContents[oldContents.length + 7] = new ItemStack(Material.APPLE, player.getHealth());
		newContents[oldContents.length + 8] = new ItemStack(Material.COOKED_BEEF, player.getFoodLevel());

		for (int i = 0; i < oldContents.length; ++i)
			if (newContents[i] != null) newContents[i] = newContents[i].clone();

		inventoryView.setContents(newContents);
		return inventoryView;
	}
	

	/**
	 * Set the correct vanish status for all players on the server
	 * 
	 * @param p1
	 */
	public void setVanish() {
		for(Player p : server.getOnlinePlayers()) {
			setVanish(p);
		}
	}

	/**
	 * Set the correct vanish status for the player in relation to all other players
	 * 
	 * @param p The player to update
	 */
	public void setVanish(Player p) {
		for (Player p2 : server.getOnlinePlayers()) {
			setVanish(p, p2);
			setVanish(p2, p);
		}
	}
	
	/**
	 * Set the correct vanish status between two players
	 * 
	 * @param viewer Player viewing
	 * @param viewed Player being viewed
	 */
	public void setVanish(Player viewer, Player viewed) {
		if (viewer == viewed) return;
		
		// A spec should be invisible to a non-spec if the match is launching and not ended
		if (!isSpectator(viewer) && isSpectator(viewed) && (matchPhase == MatchPhase.LAUNCHING || matchPhase == MatchPhase.MATCH)) {
			viewer.hidePlayer(viewed);
		} else {
			viewer.showPlayer(viewed);
		}
	}

	public ArrayList<Location> getCalculatedStarts() {
		return calculatedStarts;
	}

	public void setCalculatedStarts(ArrayList<Location> calculatedStarts) {
		this.calculatedStarts = calculatedStarts;
	}

	public HashMap<Integer, UhcStartPoint> getStartPoints() {
		return startPoints;
	}

	public int countAvailableStartPoints() {
		return availableStartPoints.size();
	}

	public World getStartingWorld() {
		return startingWorld;
	}

	public Location getLastEventLocation() {
		return lastEventLocation;
	}

	public void setLastEventLocation(Location lastEventLocation) {
		this.lastEventLocation = lastEventLocation;
	}

	public Location getLastNotifierLocation() {
		return lastNotifierLocation;
	}

	public Location getLastDeathLocation() {
		return lastDeathLocation;
	}

	public Location getLastLogoutLocation() {
		return lastLogoutLocation;
	}
	

	/**
	 * Set the length of the initial no-PVP period
	 * 
	 * @param nopvp The duration of the no-PVP period, in seconds
	 */
	public void setNopvp(int nopvp) {
		md.set("nopvp", nopvp);
		saveMatchParameters();
	}

	/**
	 * Get the length of the initial no-PVP period
	 * 
	 * @return The duration of the no-PVP period, in seconds
	 */
	public int getNopvp() {
		return md.getInt("nopvp");
	}

	/**
	 * Set the mining fatigue penalties.
	 * 
	 * @param gold Exhaustion penalty to add when mining at the gold layer
	 * @param diamond Exhaustion penalty to add when mining at the diamond layer
	 */
	public void setMiningFatigue(double gold, double diamond) {
		md.set("miningfatigue.gold", gold);
		md.set("miningfatigue.diamond", diamond);
		saveMatchParameters();
	}


	/**
	 * Get the current exhaustion penalty for mining at the gold layer
	 * 
	 * @return Exhaustion penalty to add when mining at the gold layer
	 */
	public double getMiningFatigueGold() {
		return md.getDouble("miningfatigue.gold");
	}

	/**
	 * Get the current exhaustion penalty for mining at the diamond layer
	 * 
	 * @return Exhaustion penalty to add when mining at the diamond layer
	 */
	public double getMiningFatigueDiamond() {
		return md.getDouble("miningfatigue.diamond");
	}

	/**
	 * Set the bonus items dropped in a PVP kill. One of the specified item will be dropped.
	 * 
	 * @param id The item ID to give a pvp killer
	 */
	public void setKillerBonus(int id) { setKillerBonus(id,1); }
	
	/**
	 * Set the bonus items dropped in a PVP kill.
	 * 
	 * @param id The item ID to give a pvp killer
	 * @param quantity The number of items to drop
	 */
	public void setKillerBonus(int id, int quantity) {
		if (id == 0) quantity = 0;
		md.set("killerbonus.id", id);
		md.set("killerbonus.quantity", quantity);
		saveMatchParameters();
		
	}

	/**
	 * Get the bonus items to be dropped by a PVP-killed player in addition to their inventory
	 * 
	 * @return The ItemStack to be dropped
	 */
	public ItemStack getKillerBonus() {
		int id = md.getInt("killerbonus.id");
		int quantity = md.getInt("killerbonus.quantity");
		
		if (id == 0 || quantity == 0) return null;
		
		return new ItemStack(id, quantity);
	}

	/**
	 * Set deathban on/off
	 * 
	 * @param d Whether deathban is to be enabled
	 */
	public void setDeathban(boolean d) {
		md.set("deathban", d);
		this.saveMatchParameters();
		adminBroadcast(TesseractUHC.OK_COLOR + "Deathban has been " + (d ? "enabled" : "disabled") + "!");
	}

	/**
	 * Check whether deathban is in effect
	 * 
	 * @return Whether deathban is enabled
	 */
	public boolean getDeathban() {
		return md.getBoolean("deathban");
	}
	
	
	/**
	 * Set FFA on/off
	 * 
	 * @param d Whether FFA is to be enabled
	 */
	public void setFFA(boolean d) {
		md.set("ffa", d);
		this.saveMatchParameters();
		adminBroadcast(TesseractUHC.OK_COLOR + "FFA has been " + (d ? "enabled" : "disabled") + "!");
	}

	/**
	 * Check whether this is an FFA match
	 * 
	 * @return Whether this is FFA
	 */
	public boolean isFFA() {
		return md.getBoolean("ffa");
	}
	/**
	 * Update the contents of the match "bonus chest"
	 * 
	 * @param p The player
	 */
	public void setBonusChest(ItemStack [] bonusChest) {
		this.bonusChest = bonusChest;
		md.set("bonuschest", bonusChest);
		this.saveMatchParameters();
		
	}


	/**
	 * Get the contents of the match "bonus chest"
	 * 
	 * @return The contents of the bonus chest
	 */
	public ItemStack[] getBonusChest() {
		return bonusChest;
	}

	public MatchPhase getMatchPhase() {
		return matchPhase;
	}

	public void sendNotification(UhcNotification n, Location l) {
		setLastNotifierLocation(l);
		String message = n.formatForPlayers();
		if (message != null) this.broadcast(message);
	}
	
	public void sendAdminNotification(UhcNotification n, Location l) {
		setLastNotifierLocation(l);
		String message = n.formatForStreamers();
		if (message != null) this.adminBroadcast(message);
	}

	public boolean startMatchCountdown(int countLength) {
		if (this.matchCountdown == null && (this.matchPhase == MatchPhase.LAUNCHING || this.matchPhase == MatchPhase.PRE_MATCH)) {
			this.matchCountdown = new MatchCountdown(countLength, plugin, this);
			return true;
		}
		return false;
		
	}
	
	public boolean cancelMatchCountdown() {
		if (this.matchCountdown == null) return false;

		matchCountdown.cancel();
		matchCountdown = null;
		return true;
	}
	
	public boolean cancelBorderCountdown() {
		if (this.borderCountdown == null) return false;

		borderCountdown.cancel();
		borderCountdown = null;
		return true;
	}

	public boolean startBorderCountdown(int countLength, int newRadius) {
		if (this.borderCountdown == null && this.matchPhase == MatchPhase.MATCH) {
			this.borderCountdown = new BorderCountdown(countLength, plugin, this, newRadius);
			return true;
		}
		return false;
	}

	public Collection<UhcTeam> getTeams() {
		return uhcTeams.values();
	}

	
	private void setSurvivorPlayerListName(Player p) {
		String name = p.getName();
		double health = p.getHealth() / 2.0;
		ChatColor color = ChatColor.GREEN;
		if (name.length() > 8) name = name.substring(0, 8);
		if (health <= 5) color = ChatColor.YELLOW;
		if (health <= 2) color = ChatColor.RED; 
		boolean isOdd = (health - Math.floor(health) == 0.5);
		String outputName = (color + name + " - " + (int) health + (isOdd ? ".5" : ""));
		p.setPlayerListName(outputName);
	}
	
	private void setDeceasedPlayerListName(Player p) {
		String name = p.getName();
		if (name.length() > 10) name = name.substring(0, 10);
		p.setPlayerListName(ChatColor.RED + name + " - D");
		
	}

	private void setNonPlayerPlayerListName(Player p) {
		String name = p.getName();
		if (name.length() > 14) name = name.substring(0, 14);
		p.setPlayerListName(ChatColor.DARK_GRAY + name);
	}

	public boolean isUHC() {
		return md.getBoolean("uhc");
	}

	public void setUHC(Boolean d) {
		md.set("uhc", d);
		this.saveMatchParameters();
		adminBroadcast(TesseractUHC.OK_COLOR + "UHC has been " + (d ? "enabled" : "disabled") + "!");
	}

	/**
	 * Modify the relevant recipes for UHC
	 */
	public void setupModifiedRecipes() {
		ItemStack goldenApple = new ItemStack(Material.GOLDEN_APPLE, 1, (short) 0);
		ItemStack glisteringMelon = new ItemStack(Material.SPECKLED_MELON);

		Iterator<Recipe> recipes = Bukkit.recipeIterator();
		while(recipes.hasNext()) {
			Recipe recipe = recipes.next();
			// Find recipe for golden apple
			if(recipe.getResult().equals(goldenApple) || recipe.getResult().equals(glisteringMelon))
				recipes.remove();
			
		}

		ShapedRecipe goldenAppleRecipe = new ShapedRecipe(goldenApple);
		
		goldenAppleRecipe.shape(new String[]{"GGG", "GAG", "GGG"});
		goldenAppleRecipe.setIngredient('G', Material.GOLD_INGOT);
		goldenAppleRecipe.setIngredient('A', Material.APPLE);
		
		server.addRecipe(goldenAppleRecipe);
		
		ShapelessRecipe glisteringMelonRecipe = new ShapelessRecipe(glisteringMelon);
		
		glisteringMelonRecipe.addIngredient(Material.MELON);
		glisteringMelonRecipe.addIngredient(Material.GOLD_BLOCK);
				
		server.addRecipe(glisteringMelonRecipe);
	}

	public void addPOI(Location location, String name) {
		uhcPOIs.add(new UhcPOI(location, name));
		this.saveMatchParameters();
	}
	private void addPOI(String world, double x, double y, double z, String name) {
		addPOI(new Location(server.getWorld(world), x, y, z), name);
	}

	public ArrayList<UhcPOI> getPOIs() {
		return uhcPOIs;
	}

	public boolean isSpectator(Player p) { 
		return uhcSpectators.containsKey(p.getName().toLowerCase()) || p.isOp();
	}
	
	public boolean isInteractingSpectator(Player p) {
		UhcSpectator us = this.getSpectator(p);
		if (us == null) return false;
		return us.isInteracting();
	}
	
	public boolean isNoninteractingSpectator(Player p) {
		return isSpectator(p) && !isInteractingSpectator(p);
	}
	
	public UhcSpectator addSpectator(Player p) {
		UhcSpectator spec = new UhcSpectator(p.getName(), this);
		uhcSpectators.put(p.getName().toLowerCase(), spec);
		p.setGameMode(GameMode.CREATIVE);
		setVanish(p);
		return spec;
	}
	
	public boolean removeSpectator(Player p) {
		if (uhcSpectators.containsKey(p.getName().toLowerCase())) {
			uhcSpectators.remove(p.getName().toLowerCase());
			setVanish(p);
			p.setGameMode(GameMode.SURVIVAL);
			return true;
		} else {
			return false;
		}
	}
	
	private boolean removeSpectator(String name) {
		if (uhcSpectators.containsKey(name.toLowerCase())) {
			uhcSpectators.remove(name.toLowerCase());
			return true;
		} else {
			return false;
		}
	}
	
	public UhcSpectator getSpectator(Player p) { return uhcSpectators.get(p.getName().toLowerCase()); }


	public boolean isAdmin(Player p) {
		return p.isOp(); // Temporary solution only
	}


	public String getPlayerStatusReport() {
		String response = "";
		if (this.isFFA()) {
			Collection<UhcParticipant> allPlayers = getUhcParticipants();
			response += allPlayers.size() + " players (" + countParticipantsInMatch() + " still alive):\n";
			
			for (UhcParticipant up : allPlayers) {
				response += (up.isDead() ? ChatColor.RED + "[D] " : ChatColor.GREEN);
				
				response += up.getName();
				response += " (start point " + (up.getStartPoint().getNumber()) + ")";
				response += "\n";
			}

		} else {
			Collection<UhcTeam> allTeams = getTeams();
			response = ChatColor.GOLD + "" + allTeams.size() + " teams (" + countTeamsInMatch() + " still alive):\n";
			
			for (UhcTeam team : allTeams) {
				response += (team.aliveCount()==0 ? ChatColor.RED + "[D] " : ChatColor.GREEN) + "" +
						ChatColor.ITALIC + team.getName() + ChatColor.GRAY +
						" [" + team.getIdentifier() + "]\n";
				for (UhcParticipant up : team.getMembers()) {
					String health;
					
					Player p = server.getPlayerExact(up.getName());
					if (p != null)
						health = ChatColor.GOLD + "(" + Double.toString(p.getHealth() / 2.0) + ")";
					else
						health = ChatColor.GRAY + "[offline]";
					response += (up.isDead() ? ChatColor.RED + "  D " : ChatColor.GREEN + "  * ")
							+ up.getName() + " " + health + "\n";
				}
			}
		}
		
		
		return response;
	}


	public Server getServer() { return server; }
	
	

}
