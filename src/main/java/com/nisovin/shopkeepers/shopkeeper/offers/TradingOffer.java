package com.nisovin.shopkeepers.shopkeeper.offers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import com.nisovin.shopkeepers.shopkeeper.SKTradingRecipe;
import com.nisovin.shopkeepers.util.ItemUtils;

/**
 * Stores information about up to two items being traded for another item.
 */
public class TradingOffer extends SKTradingRecipe { // shares its implementation with SKTradingRecipe

	public TradingOffer(ItemStack resultItem, ItemStack item1, ItemStack item2) {
		super(resultItem, item1, item2);
	}

	// //////////
	// STATIC UTILITIES
	// //////////

	public static void saveToConfig(ConfigurationSection config, String node, Collection<TradingOffer> offers) {
		ConfigurationSection offersSection = config.createSection(node);
		int id = 0;
		for (TradingOffer offer : offers) {
			// note: the items are clones
			ItemStack item1 = offer.getItem1();
			ItemStack item2 = offer.getItem2();
			ItemStack resultItem = offer.getResultItem();

			ConfigurationSection offerSection = offersSection.createSection(String.valueOf(id));
			offerSection.set("item1", item1);
			offerSection.set("item2", item2);
			offerSection.set("resultItem", resultItem);
			id++;
		}
	}

	public static List<TradingOffer> loadFromConfig(ConfigurationSection config, String node) {
		List<TradingOffer> offers = new ArrayList<>();
		ConfigurationSection offersSection = config.getConfigurationSection(node);
		if (offersSection != null) {
			for (String key : offersSection.getKeys(false)) {
				ConfigurationSection offerSection = offersSection.getConfigurationSection(key);
				if (offerSection == null) continue; // invalid offer: not a section
				ItemStack resultItem = offerSection.getItemStack("resultItem");
				ItemStack item1 = offerSection.getItemStack("item1");
				ItemStack item2 = offerSection.getItemStack("item2");
				if (ItemUtils.isEmpty(resultItem) || ItemUtils.isEmpty(item1)) continue; // invalid offer
				offers.add(new TradingOffer(resultItem, item1, item2));
			}
		}
		return offers;
	}

	// legacy:

	/*public static void saveToConfigOld(ConfigurationSection config, String node, Collection<TradingOffer> offers) {
		ConfigurationSection offersSection = config.createSection(node);
		int id = 0;
		for (TradingOffer offer : offers) {
			ItemStack resultItem = offer.getResultItem();
			ConfigurationSection offerSection = offersSection.createSection(id + "");
			offerSection.set("item", resultItem);
			String attributes = NMSManager.getProvider().saveItemAttributesToString(resultItem);
			if (attributes != null && !attributes.isEmpty()) {
				offerSection.set("attributes", attributes);
			}
			// legacy: amount was stored separately from the item
			offerSection.set("amount", resultItem.getAmount());
			offerSection.set("item1", offer.getItem1());
			offerSection.set("item2", offer.getItem2());
			// legacy: no attributes were stored for item1 and item2
			id++;
		}
	}*/

	public static List<TradingOffer> loadFromConfigOld(ConfigurationSection config, String node) {
		List<TradingOffer> offers = new ArrayList<>();
		ConfigurationSection offersSection = config.getConfigurationSection(node);
		if (offersSection != null) {
			for (String key : offersSection.getKeys(false)) {
				ConfigurationSection offerSection = offersSection.getConfigurationSection(key);
				if (offerSection == null) continue; // invalid offer: not a section
				ItemStack resultItem = offerSection.getItemStack("item");
				if (resultItem != null) {
					// legacy: the amount was stored separately from the item
					resultItem.setAmount(offerSection.getInt("amount", 1));
				}
				if (ItemUtils.isEmpty(resultItem)) continue; // invalid offer
				ItemStack item1 = offerSection.getItemStack("item1");
				if (ItemUtils.isEmpty(item1)) continue; // invalid offer
				ItemStack item2 = offerSection.getItemStack("item2");
				// legacy: no attributes were stored for item1 and item2
				offers.add(new TradingOffer(resultItem, item1, item2));
			}
		}
		return offers;
	}
}