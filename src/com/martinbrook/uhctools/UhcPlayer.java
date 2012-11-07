package com.martinbrook.uhctools;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public class UhcPlayer {
	private String name;
	private boolean launched = false;
	private Location startPoint;
	
	public UhcPlayer(Player p) {
		this.name = p.getName();
	}
	
	
	public String getName() {
		return name;
	}


	public void setName(String name) {
		this.name = name;
	}


	public boolean isLaunched() {
		return launched;
	}


	public void setLaunched(boolean launched) {
		this.launched = launched;
	}


	public Location getStartPoint() {
		return startPoint;
	}


	public void setStartPoint(Location startPoint) {
		this.startPoint = startPoint;
	}





	

}