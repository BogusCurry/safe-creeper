package com.timvisee.safecreeper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.io.OutputStream;

import java.util.logging.Logger;

import net.slipcor.pvparena.PVPArena;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.garbagemule.MobArena.MobArena;
import com.garbagemule.MobArena.MobArenaHandler;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.timvisee.safecreeper.Metrics.Graph;
import com.timvisee.safecreeper.api.SafeCreeperApi;
import com.timvisee.safecreeper.command.CommandHandler;
import com.timvisee.safecreeper.entity.SCLivingEntityReviveManager;
import com.timvisee.safecreeper.handler.TVNLibHandler;
import com.timvisee.safecreeper.listener.*;
import com.timvisee.safecreeper.manager.*;
import com.timvisee.safecreeper.task.SCDestructionRepairRepairTask;
import com.timvisee.safecreeper.task.SCDestructionRepairSaveDataTask;
import com.timvisee.safecreeper.task.SCUpdateCheckerTask;
import com.timvisee.safecreeper.util.SCFileUpdater;
import com.timvisee.safecreeper.util.UpdateChecker;

public class SafeCreeper extends JavaPlugin {
	
	// Safe Creeper static instance
	public static SafeCreeper instance;
	
	// Loggers
	private SCLogger logger;
	
	// Listeners
	private final SCBlockListener blockListener = new SCBlockListener();
	private final SCEntityListener entityListener = new SCEntityListener();
	private final SCPlayerListener playerListener = new SCPlayerListener();
	private final SCHangingListener hangingListener = new SCHangingListener();
	private final SCTVNLibListener tvnlListener = new SCTVNLibListener();
	private final SCWeatherListener weatherListener = new SCWeatherListener();
	private final SCWorldListener worldListener = new SCWorldListener();
	
	// Config file and folder paths
	private File globalConfigFile = new File("plugins/SafeCreeper/global.yml");
	private File worldConfigsFolder = new File("plugins/SafeCreeper/worlds");
	
	// Managers and Handlers
	private TVNLibHandler tvnlHandler;
	private PermissionsManager pm;
	private SCConfigManager cm = null;
	private DestructionRepairManager drm;
	private SCLivingEntityReviveManager lerm;
	private MobArenaHandler maHandler;
	private CorruptionManager corHandler;
	private SCStaticsManager statics = new SCStaticsManager();
	
	// Update Checker
	private UpdateChecker uc = null;
	
	// Debug Mode
	boolean debug = false;
	
	// Variable to disable the other explosions for a little, little while (otherwise some explosions are going to be looped)
	public boolean disableOtherExplosions = false;
	
	/**
	 * Constructor
	 */
	public SafeCreeper() {
		// Define the Safe Creeper static instance variable
		instance = this;
	}
	
	/**
	 * On enable method, called when plugin is being enabled
	 */
	public void onEnable() {
		long t = System.currentTimeMillis();
		
		// Define the plugin manager
		PluginManager pm = getServer().getPluginManager();
		
		// Setup the file paths
		globalConfigFile = new File(getConfig().getString("GlobalConfigFilePath", globalConfigFile.getPath()));
		worldConfigsFolder = new File(getConfig().getString("WorldConfigsFolderPath", worldConfigsFolder.getPath()));

		// Setup the Safe Creeper logger
		setupSCLogger();
		
		// Check if all the config file exists
		try {
			checkConigFilesExist();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
		
		// Setup the config manager before all other managers, to make the file updater work
	    setupConfigManager();
		
		// Initialize the update checker
		setupUpdateChecker();
		
		// Remove all (old) update files
		getUpdateChecker().removeUpdateFiles();
		
		// Check if any update exists
		if(getConfig().getBoolean("updateChecker.enabled", true)) {
			if(uc.isNewVersionAvailable()) {
				final String newVer = uc.getNewestVersion();
				System.out.println("[SafeCreeper] New Safe Creeper version available: v" + newVer);
				
				// Auto install updates if enabled
				if(getConfig().getBoolean("updateChecker.autoInstallUpdates", true) || getUpdateChecker().isImportantUpdateAvailable()) {
					if(!uc.isNewVersionCompatibleWithCurrentBukkit()) {
						System.out.println("[SafeCreeper] The newest Safe Creeper version is not compatible with the current Bukkit version!");
						System.out.println("[SafeCreeper] Please update to Bukkit " + uc.getRequiredBukkitVersion() + " or higher!");
					} else {
						// Check if already update installed
						if(getUpdateChecker().isUpdateDownloaded())
							System.out.println("[SafeCreeper] Safe Creeper update installed, server reload required!");
						else {
							// Download the update and show some status messages
							System.out.println("[SafeCreeper] Automaticly installing SafeCreeper update...");
							getUpdateChecker().downloadUpdate();
							System.out.println("[SafeCreeper] Safe Creeper update installed, reload required!");
						}
					}
				} else {
					// Auto installing updates not enabled, show a status message
					System.out.println("[SafeCreeper] Use '/sc installupdate' to automaticly install the new update!");
				}
			}
		}
		
		// Schedule update checker task
		FileConfiguration config = getConfig();
		if(config.getBoolean("tasks.updateChecker.enabled", true)) {
			int taskInterval = (int) config.getDouble("tasks.updateChecker.interval", 3600) * 20;
			
			// Schedule the update checker task
			getServer().getScheduler().scheduleSyncRepeatingTask(this, new SCUpdateCheckerTask(getConfig(), getUpdateChecker()), taskInterval, taskInterval);
		} else {
			// Show an warning in the console
			getSCLogger().info("Scheduled task 'updateChecker' disabled in the config file!");
		}
		
		// Setup the API
		setupApi();
		
		// Update all existing config files if they aren't up-to-date
		((SCFileUpdater) new SCFileUpdater()).updateFiles();
		
		// Setup TVNativeLib
		setupTVNLibHandler();
		
		// Setup managers and handlers
	    setupPermissionsManager();
	    setupDestructionRepairManager();
	    setupLivingEntityReviveManager();
	    setupMobArenaHandler();
	    setupPVPArena();
	    setupFactions();
	    setupCorruptionManager();
		setupMetrics();
		
		// Load destruction repair data
		getDestructionRepairManager().load();
		
		// Register event listeners
		pm.registerEvents(this.blockListener, this);
		pm.registerEvents(this.entityListener, this);
		pm.registerEvents(this.hangingListener, this);
		pm.registerEvents(this.playerListener, this);
		pm.registerEvents(this.weatherListener, this);
		pm.registerEvents(this.worldListener, this);
		
		// Register the TVNLibListener if the TVNLib listener plugin is installed
		if(getTVNLibHandler().isEnabled())
			pm.registerEvents(this.tvnlListener, this);
		
		/* // Test - Beginning of custom mob abilities!
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
				public void run() {
					List<Player> onlinePlayers = Arrays.asList(getServer().getOnlinePlayers());
					if(onlinePlayers.size() > 0) {
						for(Player p : onlinePlayers) {
							//Player p = onlinePlayers.get(0);
							for(LivingEntity e : p.getWorld().getLivingEntities()) {
								if(e instanceof Creature) {
									/*Creature c = (Creature) e;
									c.setTarget(p);* /
									
									//c.launchProjectile();
									
									if(getLivingEntityManager().isSCLivingEntity(e)) {
										SCLivingEntity scle = getLivingEntityManager().getLivingEntity(e);
										
										if(scle.getLivingEntity().getLocation().distance(p.getLocation()) > 15)
											continue;
										
										scle.shootProjectile(p);
									}
								}
							}
						}
					}
				}
			}, 20, 20);*/
		
		// Task to repair blocks from the destruction repair manager// Schedule update checker task
		if(config.getBoolean("tasks.destructionRepairRepair.enabled", true)) {
			int taskInterval = (int) config.getDouble("tasks.destructionRepairRepair.interval", 1) * 20;
			
			// Schedule the task
			getServer().getScheduler().scheduleSyncRepeatingTask(this, new SCDestructionRepairRepairTask(getDestructionRepairManager()), taskInterval, taskInterval);
		} else {
			// Show an warning in the console
			getSCLogger().info("Scheduled task 'destructionRepairRepair' disabled in the config file!");
		}
		
		// Task to save the destruction repair data
		if(config.getBoolean("tasks.destructionRepairSave.enabled", true)) {
			int taskInterval = (int) config.getDouble("tasks.destructionRepairSave.interval", 300) * 20;
			
			// Schedule the task
			getServer().getScheduler().scheduleSyncRepeatingTask(this, new SCDestructionRepairSaveDataTask(getDestructionRepairManager()), taskInterval, taskInterval);
		} else {
			// Show an warning in the console
			getSCLogger().info("Scheduled task 'destructionRepairSave' disabled in the config file!");
		}
		
		// Plugin sucesfuly enabled, show console message
		PluginDescriptionFile pdfFile = getDescription();
		
		// Calculate the load duration
		long duration = System.currentTimeMillis() - t;
		
		getSCLogger().info("Safe Creeper v" + pdfFile.getVersion() + " enabled, took " + String.valueOf(duration) + " ms!");
	}
	
	/**
	 * On disable method, called when plugin is being disabled
	 */
	public void onDisable() {
		// Save the destruction repair data
		getDestructionRepairManager().save();
		
		// Cancel all running Safe Creeper tasks
		getSCLogger().info("Cancelling all Safe Creeper tasks...");
		SafeCreeper.instance.getServer().getScheduler().cancelTasks(SafeCreeper.instance);
		getSCLogger().info("All Safe Creeper tasks cancelled!");
		
		// If any update was downloaded, install the update
		if(getUpdateChecker().isUpdateDownloaded())
			getUpdateChecker().installUpdate();
		
		// Remove all update files
		getUpdateChecker().removeUpdateFiles();
		
		// Plugin disabled, show console message
		getSCLogger().info("Safe Creeper Disabled");
	}
    
	/**
	 * Fetch the Safe Creeper version from the plugin.yml file
	 * @return Fetch the Safe Creeper version from the plugin.yml file
	 */
	public String getVersion() {
		return getDescription().getVersion();
	}
	
	/**
	 * Set up the Safe Creeper API layer
	 */
	public void setupApi() {
		// Setup API
		SafeCreeperApi.setPlugin(this);
	}
	
	/**
	 * Set up the update checker
	 */
	public void setupUpdateChecker() {
		this.uc = new UpdateChecker();
	}
	
	/**
	 * Get the update checker instance
	 * @return Update checker instance
	 */
	public UpdateChecker getUpdateChecker() {
		return this.uc;
	}
	
	/**
	 * Set up the Safe Creeper logger
	 */
	public void setupSCLogger() {
		this.logger = new SCLogger(Logger.getLogger("Minecraft"));
	}
	
	/**
	 * Get the Safe Creeper logger instance
	 * @return Safe Creeper logger instance
	 */
	public SCLogger getSCLogger() {
		return this.logger;
	}
	
	/**
	 * Set up the TVNLib handler
	 */
	public void setupTVNLibHandler() {
		// Setup TVNLib Handler
		this.tvnlHandler = new TVNLibHandler(this);
	}
	
	/**
	 * Get the TVNLib handler insatnce
	 * @return TVNLib handler instance
	 */
	public TVNLibHandler getTVNLibHandler() {
		return this.tvnlHandler;
	}
	
	/**
	 * Set up the config manager
	 */
	public void setupConfigManager() {
		this.cm = new SCConfigManager(this, globalConfigFile, worldConfigsFolder);
	}
	
	/**
	 * Get the config manager instance
	 * @return
	 */
	public SCConfigManager getConfigManager() {
		return this.cm;
	}
	
	/**
	 * Setup the permissions manager
	 */
	public void setupPermissionsManager() {
		// Setup the permissions manager
		this.pm = new PermissionsManager(this.getServer(), this);
		this.pm.setup();
	}
	
	/**
	 * Get the permissions manager
	 * @return permissions manager
	 */
	public PermissionsManager getPermissionsManager() {
		return this.pm;
	}
	
	/**
	 * Setup the destruction repair manager
	 */
	public void setupDestructionRepairManager() {
		// Setup the  destruction repair manager
		this.drm = new DestructionRepairManager();
	}
	
	/**
	 * Get the destruction repair manager
	 * @return destruction repair manager
	 */
	public DestructionRepairManager getDestructionRepairManager() {
		return this.drm;
	}
	
	/**
	 * Get the statics manager instnace
	 * @return
	 */
	public SCStaticsManager getStaticsManager() {
		return this.statics;
	}

	/**
	 * Get the World Guard plugin instance
	 * @return
	 */
    protected WorldGuardPlugin getWorldGuard() {
        Plugin wg = getServer().getPluginManager().getPlugin("WorldGuard");
 
        // WorldGuard may not be loaded
        if (wg == null || !(wg instanceof WorldGuardPlugin))
            return null;
        return (WorldGuardPlugin) wg;
    }
    
    /**
     * Check if World Guard is enabled on the server
     * @return
     */
    public boolean worldGuardEnabled() {
    	return (getWorldGuard() != null);
    }
   
    /**
     * Set up the MobArena hook
     */
    public void setupMobArenaHandler() {
    	// MobArena has to be installed/enabled
    	if(!getServer().getPluginManager().isPluginEnabled("MobArena")) {
    		getSCLogger().info("Disabling MobArena usage, plugin not found.");
    		return;
    	}
    	
    	try {
    		// Try to get the MobArenap plugin
    		Plugin maPlugin = (MobArena) getServer().getPluginManager().getPlugin("MobArena");
	        
	        if (maPlugin == null) {
	        	getSCLogger().info("Unable to hook into MobArena, plugin not found!");
	            return;
	        }
	        
	        // Hooked into MobArena, show a status message
	        maHandler = new MobArenaHandler();
	        getSCLogger().info("Hooked into MobArena!");
	        
    	} catch(NoClassDefFoundError ex) {
    		// Unable to hook into MobArena, show warning/error message.
    		getSCLogger().info("Error while hooking into MobArena!");
    		return;
    	} catch(Exception ex) {
    		// Unable to hook into MobArena, show warning/error message.
    		getSCLogger().info("Error while hooking into MobArena!");
    		return;
    	}
    }
    
    /**
     * Get the MobArena handler
     * @return
     */
    public MobArenaHandler getMobArenaHandler() {
    	return this.maHandler;
    }
   
    /**
     * Setup the PVPArena hook
     */
    public void setupPVPArena() {
    	// PVP Arena has to be installed/enabled
    	if(!getServer().getPluginManager().isPluginEnabled("pvparena")) {
    		getSCLogger().info("Disabling PVPArena usage, plugin not found.");
    		return;
    	}
    	
    	try {
    		// Try to get the PVPArena plugin
    		Plugin paPlugin = (PVPArena) getServer().getPluginManager().getPlugin("pvparena");
	        
    		// The plugin variable may not be null
	        if (paPlugin == null) {
	        	getSCLogger().info("Unable to hook into PVPArena, plugin not found!");
	            return;
	        }
	        
	        // Hooked into PVPArena, show status message
	        getSCLogger().info("Hooked into PVPArena!");
	        
    	} catch(NoClassDefFoundError ex) {
    		// Unable to hook into PVPArena, show warning/error message.
    		getSCLogger().info("Error while hooking into PVPArena!");
    		return;
    	} catch(Exception ex) {
    		// Unable to hook into PVPArena, show warning/error message.
    		getSCLogger().info("Error while hooking into PVPArena!");
    		return;
    	} 
    }
   
    /**
     * Setup the Factions hook
     */
    public void setupFactions() {
    	// Factions has to be installed/enabled
    	if(!getServer().getPluginManager().isPluginEnabled("Factions")) {
    		getSCLogger().info("Disabling Factions usage, plugin not found.");
    		return;
    	}
    	
    	try {
    		// Get the Factions plugin
    		Plugin fPlugin = (Plugin) getServer().getPluginManager().getPlugin("Factions");
	        
    		// The factions plugin may not ben ull
	        if (fPlugin == null) {
	        	getSCLogger().info("Unable to hook into Factions, plugin not found!");
	            return;
	        }
	        
	        // Hooked into Factions, show status message
	        getSCLogger().info("Hooked into Factions!");
	        
    	} catch(NoClassDefFoundError ex) {
    		// Unable to hook into Factions, show warning/error message.
    		getSCLogger().info("Error while hooking into Factions!");
    		return;
    	} catch(Exception ex) {
    		// Unable to hook into Factions, show warning/error message.
    		getSCLogger().info("Error while hooking into Factions!");
    		return;
    	}
    }
    
    /**
     * Set up the Corruption manager
     */
    public void setupCorruptionManager() {
    	this.corHandler = new CorruptionManager();
    }
    
    /**
     * Get the Corruption handler
     * @return Corruption manager
     */
    public CorruptionManager getCorruptionHandler() {
    	return this.corHandler;
    }
    
    /**
     * Set up the living 
     */
    public void setupLivingEntityReviveManager() {
    	this.lerm = new SCLivingEntityReviveManager();
    }
    
    /**
     * Get the living entity revive manager instance
     * @return Living entity revive manager instance
     */
    public SCLivingEntityReviveManager getLivingEntityReviveManager() {
    	return this.lerm;
    }
	
    /**
     * Check if the config file exists
     * @throws Exception
     */
	public void checkConigFilesExist() throws Exception {
		if(!getDataFolder().exists()) {
			getSCLogger().info("Creating new Safe Creeper folder");
			getDataFolder().mkdirs();
		}
		File configFile = new File(getDataFolder(), "config.yml");
		if(!configFile.exists()) {
			getSCLogger().info("Generating new config file");
			copyFile(getResource("res/config.yml"), configFile);
		}
		if(!globalConfigFile.exists()) {
			getSCLogger().info("Generating new global file");
			copyFile(getResource("res/global.yml"), globalConfigFile);
		}
		if(!worldConfigsFolder.exists()) {
			getSCLogger().info("Generating new 'worlds' folder");
			worldConfigsFolder.mkdirs();
			copyFile(getResource("res/worlds/world_example.yml"), new File(worldConfigsFolder, "world_example.yml"));
			copyFile(getResource("res/worlds/world_example2.yml"), new File(worldConfigsFolder, "world_example2.yml"));
		}
	}
	
	/**
	 * Copy a file
	 * @param in Input stream (file)
	 * @param file File to copy the file to
	 */
	private void copyFile(InputStream in, File file) {
	    try {
	        OutputStream out = new FileOutputStream(file);
	        byte[] buf = new byte[1024];
	        int len;
	        while((len=in.read(buf))>0){
	            out.write(buf,0,len);
	        }
	        out.close();
	        in.close();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}
	
	/**
	 * Set up metrics
	 */
	public void setupMetrics() {
		if(!getConfig().getBoolean("statistics.enabled", true)) {
			getSCLogger().info("MCStats.org Statistics disabled!");
			return;
		}
		
		// Metrics / MCStats.org
		try {
		    Metrics metrics = new Metrics(this);
		    
		    // Add graph for nerfed creepers
		    // Construct a graph, which can be immediately used and considered as valid
		    Graph graph = metrics.createGraph("Activities Nerfed by Safe Creeper");
		    // Creeper explosions Nerfed
		    graph.addPlotter(new Metrics.Plotter("Creeper Explosions") {
	            @Override
	            public int getValue() {
	            	int i = statics.getCreeperExplosionsNerfed();
	            	statics.setCreeperExplosionNerved(0);
	            	return i;
	            }
		    });
		    graph.addPlotter(new Metrics.Plotter("TNT Explosions") {
	            @Override
	            public int getValue() {
	            	int i = statics.getTNTExplosionsNerfed();
	            	statics.setTNTExplosionNerved(0);
	            	return i;
	            }
		    });
		    graph.addPlotter(new Metrics.Plotter("TNT Damage") {
	            @Override
	            public int getValue() {
	            	int i = statics.getTNTDamageNerfed();
	            	statics.setTNTDamageNerved(0);
	            	return i;
	            }
		    });
		    // Used permissions systems
		    Graph graph2 = metrics.createGraph("Permisison Plugin Usage");
		    graph2.addPlotter(new Metrics.Plotter(getPermissionsManager().getUsedPermissionsSystemType().getName()) {
	            @Override
	            public int getValue() {
	            	return 1;
	            }
		    });
		    
		    // Start metrics
		    metrics.start();
		    
		    // Show a status message
		    getSCLogger().info("MCStats.org Statistics enabled.");
		} catch (IOException e) {
		    // Failed to submit the stats :-(
			e.printStackTrace();
		}
	}

	/**
	 * On command method, called when a command ran on the server
	 */
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		// Run the command trough the command handler
		CommandHandler ch = new CommandHandler();
		return ch.onCommand(sender, cmd, commandLabel, args);
	}
}
