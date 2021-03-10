package com.nisovin.shopkeepers.util;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * ItemStack definitions for test cases.
 *
 */
public class TestItemStacks {

	public static List<ItemStack> createAllItemStacks() {
		return Arrays.asList(
				createItemStackNull(),
				createItemStackAir(),
				createItemStackBasic(),
				createItemStackBasicWithSize(),
				createItemStackBasicTool(),
				createItemStackDisplayName(),
				createItemStackComplete(),
				createItemStackUncommonMeta(),
				createItemStackBasicTileEntity(),
				createItemStackTileEntityDisplayName()
		);
	}

	public static ItemStack createItemStackNull() {
		return null;
	}

	public static ItemStack createItemStackAir() {
		return new ItemStack(Material.AIR);
	}

	public static ItemStack createItemStackBasic() {
		return new ItemStack(Material.STONE);
	}

	public static ItemStack createItemStackBasicWithSize() {
		return new ItemStack(Material.STONE, 10);
	}

	public static ItemStack createItemStackBasicTool() {
		ItemStack itemStack = new ItemStack(Material.DIAMOND_SWORD);
		return itemStack;
	}

	public static ItemStack createItemStackDisplayName() {
		ItemStack itemStack = new ItemStack(Material.DIAMOND_SWORD);
		ItemMeta itemMeta = itemStack.getItemMeta();
		itemMeta.setDisplayName(ChatColor.RED + "Custom Name");
		itemStack.setItemMeta(itemMeta);
		return itemStack;
	}

	public static ItemStack createItemStackComplete() {
		ItemStack itemStack = new ItemStack(Material.DIAMOND_SWORD);
		ItemMeta itemMeta = itemStack.getItemMeta();
		itemMeta.setDisplayName(ChatColor.RED + "Custom Name");
		itemMeta.setLore(Arrays.asList(ChatColor.GREEN + "lore1", "lore2"));
		itemMeta.addEnchant(Enchantment.DURABILITY, 1, true);
		itemMeta.addEnchant(Enchantment.DAMAGE_ALL, 2, true);
		itemMeta.setCustomModelData(1);
		itemMeta.setLocalizedName("loc name");
		itemMeta.setUnbreakable(true);
		itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		itemMeta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(new UUID(1L, 1L), "attack speed bonus", 2, Operation.ADD_NUMBER, EquipmentSlot.HAND));
		itemMeta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(new UUID(2L, 2L), "attack speed bonus 2", 0.5, Operation.MULTIPLY_SCALAR_1, EquipmentSlot.OFF_HAND));
		itemMeta.addAttributeModifier(Attribute.GENERIC_MAX_HEALTH, new AttributeModifier(new UUID(3L, 3L), "attack speed bonus", 2, Operation.ADD_NUMBER, EquipmentSlot.HAND));
		((Damageable) itemMeta).setDamage(2);
		// note: this data ends up getting stored in an arbitrary order internally
		PersistentDataContainer customTags = itemMeta.getPersistentDataContainer();
		customTags.set(new NamespacedKey("some_plugin", "some-key"), PersistentDataType.STRING, "some value");
		PersistentDataContainer customContainer = customTags.getAdapterContext().newPersistentDataContainer();
		customContainer.set(new NamespacedKey("inner_plugin", "inner-key"), PersistentDataType.FLOAT, 0.3F);
		customTags.set(new NamespacedKey("some_plugin", "some-other-key"), PersistentDataType.TAG_CONTAINER, customContainer);
		itemStack.setItemMeta(itemMeta);
		return itemStack;
	}

	public static ItemStack createItemStackUncommonMeta() {
		ItemStack itemStack = new ItemStack(Material.LEATHER_CHESTPLATE);
		LeatherArmorMeta itemMeta = (LeatherArmorMeta) itemStack.getItemMeta();
		itemMeta.setDisplayName(ChatColor.RED + "Custom Name");
		itemMeta.setColor(Color.BLUE);
		itemStack.setItemMeta(itemMeta);
		return itemStack;
	}

	public static ItemStack createItemStackBasicTileEntity() {
		ItemStack itemStack = new ItemStack(Material.CHEST);
		return itemStack;
	}

	public static ItemStack createItemStackTileEntityDisplayName() {
		ItemStack itemStack = new ItemStack(Material.CHEST);
		ItemMeta itemMeta = itemStack.getItemMeta();
		itemMeta.setDisplayName(ChatColor.RED + "Custom Name");
		itemStack.setItemMeta(itemMeta);
		return itemStack;
	}

	private TestItemStacks() {
	}
}
