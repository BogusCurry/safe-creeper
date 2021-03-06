package com.timvisee.safecreeper.block.state;

import com.timvisee.safecreeper.block.SCBlockLocation;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SCContainerBlockState extends SCBlockState {

    private List<ItemStack> contents = new ArrayList<ItemStack>();
    private int containerSize;

    /**
     * Constructor
     *
     * @param b Container block
     */
    public SCContainerBlockState(Block b) {
        // Construct the parent class
        super(b);

        // Get the block state
        BlockState bs = b.getState();

        // Make sure the block is an instance of an inventory holder
        if(!(bs instanceof InventoryHolder))
            return;

        // Get the inventory and inventory holder
        InventoryHolder ih = (InventoryHolder) b.getState();
        Inventory inv = ih.getInventory();

        // Store the contents
        this.contents.clear();
        for(ItemStack entry : inv.getContents())
            if(entry != null)
                this.contents.add(entry.clone());
            else
                this.contents.add(null);

        // Store the container size
        this.containerSize = inv.getSize();
    }

    /**
     * Consturctor
     *
     * @param loc           Container location
     * @param type          Container block material
     * @param data          Container block data value
     * @param contents      Container contents
     * @param containerSize Container contents size (inventory size)
     */
    public SCContainerBlockState(SCBlockLocation loc, Material type, byte data, List<ItemStack> contents, int containerSize) {
        // Construct the parent class
        super(loc, type, data);

        // Store the contents and the container size
        this.contents = contents;
        this.containerSize = containerSize;
    }

    /**
     * Load the data in a configuration section
     *
     * @param configSection Configuration section to store the data in
     */
    public static SCContainerBlockState load(ConfigurationSection configSection) {
        // Make sure the param is not null
        if(configSection == null)
            return null;

        // Get the block location
        ConfigurationSection locSection = configSection.getConfigurationSection("loc");
        SCBlockLocation loc = SCBlockLocation.load(locSection);

        // Create a variable for the block material
        Material type;

        // Load the material if the proper key is available
        if(configSection.isString("type"))
            type = Material.getMaterial(configSection.getString("type"));

        else if(configSection.isInt("typeId"))
            //noinspection deprecation
            type = Material.getMaterial(configSection.getInt("typeId"));

        else {
            // Show an error message, and return null
            System.out.println("Failed to load stored block state, type is missing.");
            return null;
        }

        // Get the block type ID and data
        byte data = (byte) configSection.getInt("data", 0);

        // Get the contents and the contents size of the container
        @SuppressWarnings("unchecked")
        List<ItemStack> contents = (List<ItemStack>) configSection.getList("containerContents", new ArrayList<ItemStack>());
        int containerSize = configSection.getInt("containerSize");

        // Make sure the contents list is not null
        if(contents == null)
            return null;

        // Construct the container state and return the instance
        return new SCContainerBlockState(loc, type, data, contents, containerSize);
    }

    /**
     * Get the Inventory Holder instance
     *
     * @return Inventory Holder instance
     */
    public InventoryHolder getInventoryHolder() {
        return (InventoryHolder) getBlock().getState();
    }

    /**
     * Get the container contents
     *
     * @return Container contents
     */
    public List<ItemStack> getContents() {
        return this.contents;
    }

    /**
     * Set the container contents
     *
     * @param contents Container contents
     */
    public void setContents(ItemStack[] contents) {
        // Store the contents
        this.contents.clear();
        for(ItemStack entry : contents)
            if(entry != null)
                this.contents.add(entry.clone());
            else
                this.contents.add(null);
    }

    /**
     * Set the container contents
     *
     * @param contents Container contents
     */
    public void setContents(List<ItemStack> contents) {
        this.contents = contents;
    }

    /**
     * Clear the container contents
     */
    public void clearContents() {
        this.contents.clear();
    }

    /**
     * Get the container size
     *
     * @return Container size
     */
    public int getContainerSize() {
        return this.containerSize;
    }

    /**
     * Get the block state type
     */
    public SCBlockStateType getStateType() {
        return SCBlockStateType.CONTAINER_BLOCK;
    }

    /**
     * Apply the block state to the block
     *
     * @return True if succeed
     */
    public boolean apply() {
        if(!super.apply())
            return false;

        // Get the inventory holder and the inventory
        final InventoryHolder ih = getInventoryHolder();
        final Inventory inv = ih.getInventory();

        // Make sure the inventory holder and the inventory instance are not null
        if(inv == null)
            return false;

        // Put the item back in the chest
        for(int i = 0; i < inv.getSize(); i++)
            inv.setItem(i, this.contents.get(i));

        // Return true
        return true;
    }

    /**
     * Save the data in a configuration section
     *
     * @param configSection Configuration section to store the data in
     */
    public void save(ConfigurationSection configSection) {
        // Make sure the param is not null
        if(configSection == null)
            return;

        // Save the main data from the parent class
        super.save(configSection);

        // Store the container contents
        configSection.set("containerContents", this.contents);
        configSection.set("containerSize", this.containerSize);
    }
}
