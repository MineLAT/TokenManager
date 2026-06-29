package me.realized.tokenmanager.util.inventory;

import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XPotion;
import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import me.realized.tokenmanager.util.EnumUtil;
import me.realized.tokenmanager.util.NumberUtil;
import me.realized.tokenmanager.util.StringUtil;
import me.realized.tokenmanager.util.compat.CompatUtil;
import me.realized.tokenmanager.util.compat.Items;
import me.realized.tokenmanager.util.compat.Skulls;
import me.realized.tokenmanager.util.compat.SpawnEggs;
import me.realized.tokenmanager.util.compat.Terracottas;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

public final class ItemUtil {

    public static ItemStack loadFromString(final String line) {
        if (line == null || line.isEmpty()) {
            throw new IllegalArgumentException("Line is empty or null");
        }

        final String[] args = line.split(" +");
        String[] materialData = args[0].split(":");
        Material material = Material.matchMaterial(materialData[0]);

        // TEMP: Allow confirm button item loading in 1.13
        if (!CompatUtil.isPre1_13()) {
            if (materialData[0].equalsIgnoreCase("STAINED_CLAY")) {
                material = Material.TERRACOTTA;

                if (materialData.length > 1) {
                    material = Terracottas.from((short) NumberUtil.parseLong(materialData[1]).orElse(0));
                }
            }
        }

        if (material == null) {
            throw new IllegalArgumentException("'" + args[0] + "' is not a valid material");
        }

        ItemStack result = new ItemStack(material, 1);

        if (materialData.length > 1) {
            // Handle potions and spawn eggs switching to NBT in 1.9+
            if (!CompatUtil.isPre1_9()) {
                if (material.name().contains("POTION")) {
                    final List<String> values = Arrays.asList(materialData[1].split("-"));
                    final PotionType type;

                    if ((type = EnumUtil.getByName(values.get(0), PotionType.class)) == null) {
                        throw new IllegalArgumentException("'" + values.get(0) + "' is not a valid PotionType. Available: " + EnumUtil.getNames(PotionType.class));
                    }

                    final PotionMeta meta = (PotionMeta) result.getItemMeta();
                    meta.setBasePotionData(new PotionData(type, values.contains("extended"), values.contains("strong")));
                    result.setItemMeta(meta);
                } else if (CompatUtil.isPre1_13() && material.name().equals("MONSTER_EGG")) {
                    final EntityType type;

                    if ((type = EnumUtil.getByName(materialData[1], EntityType.class)) == null) {
                        throw new IllegalArgumentException("'" + materialData[0] + "' is not a valid EntityType. Available: " + EnumUtil.getNames(EntityType.class));
                    }

                    result = new SpawnEggs(type).toItemStack();
                }
            }

            final OptionalLong value;

            if ((value = NumberUtil.parseLong(materialData[1])).isPresent()) {
                result.setDurability((short) value.getAsLong());
            }
        }

        if (args.length < 2) {
            return result;
        }

        result.setAmount(Integer.parseInt(args[1]));

        if (args.length > 2) {
            for (int i = 2; i < args.length; i++) {
                final String argument = args[i];
                final String[] pair = argument.split(":", 2);

                if (pair.length < 2) {
                    continue;
                }

                applyMeta(result, pair[0], pair[1]);
            }
        }

        return result;
    }


    public static ItemStack loadFromString(final String line, final Consumer<String> errorHandler) {
        ItemStack result;

        try {
            result = loadFromString(line);
        } catch (Exception ex) {
            result = ItemBuilder
                .of(Material.REDSTONE_BLOCK)
                .name("&4&m------------------")
                .lore(
                    "&cThere was an error",
                    "&cwhile loading this",
                    "&citem, please contact",
                    "&can administrator.",
                    "&4&m------------------"
                )
                .build();
            errorHandler.accept(ex.getMessage());
        }

        return result;
    }

    private static void applyMeta(final ItemStack item, final String key, final String value) {
        final ItemMeta meta = item.getItemMeta();

        if (key.equalsIgnoreCase("name")) {
            meta.setDisplayName(StringUtil.color(value.replace("_", " ")));
            item.setItemMeta(meta);
            return;
        }

        if (key.equalsIgnoreCase("lore")) {
            meta.setLore(StringUtil.color(Lists.newArrayList(value.split("\\|")), line -> line.replace("_", " ")));
            item.setItemMeta(meta);
            return;
        }

        if (key.equalsIgnoreCase("unbreakable") && value.equalsIgnoreCase("true")) {
            if (!CompatUtil.isPre1_12()) {
                meta.setUnbreakable(true);
            }

            item.setItemMeta(meta);
            return;
        }

        if (key.equalsIgnoreCase("flags")) {
            final String[] flags = value.split(",");

            for (final String flag : flags) {
                final ItemFlag itemFlag = EnumUtil.getByName(flag, ItemFlag.class);

                if (itemFlag == null) {
                    continue;
                }

                meta.addItemFlags(itemFlag);
            }

            item.setItemMeta(meta);
            return;
        }

        final Optional<Enchantment> enchantment = XEnchantment.of(key).map(XEnchantment::get);

        if (enchantment.isPresent()) {
            item.addUnsafeEnchantment(enchantment.get(), Integer.parseInt(value));
            return;
        }

        if (item.getType().name().contains("POTION")) {
            final Optional<PotionEffectType> effectType = XPotion.of(key).map(XPotion::get);

            if (effectType.isPresent()) {
                final String[] values = value.split(":");
                final PotionMeta potionMeta = (PotionMeta) meta;
                potionMeta.addCustomEffect(new PotionEffect(effectType.get(), Integer.parseInt(values[1]), Integer.parseInt(values[0])), true);
                item.setItemMeta(potionMeta);
                return;
            }
        }

        if (Items.equals(Items.HEAD, item) && (key.equalsIgnoreCase("player") || key.equalsIgnoreCase("owner") || key.equalsIgnoreCase("texture"))) {
            final SkullMeta skullMeta = (SkullMeta) meta;

            // Since Base64 texture strings are much longer than usernames...
            if (value.length() > 16) {
                Skulls.setSkull(skullMeta, value);
            } else {
                skullMeta.setOwner(value);
            }

            item.setItemMeta(skullMeta);
        }

        if (item.getType().name().contains("LEATHER_") && key.equalsIgnoreCase("color")) {
            final LeatherArmorMeta leatherArmorMeta = (LeatherArmorMeta) meta;
            final String[] values = value.split(",");
            leatherArmorMeta.setColor(Color.fromRGB(Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2])));
            item.setItemMeta(leatherArmorMeta);
        }

        if (key.equalsIgnoreCase("custommodeldata") && !CompatUtil.isPre1_14()) {
            meta.setCustomModelData(Integer.parseInt(value));
            item.setItemMeta(meta);
        }
    }

    public static void copyNameLore(final ItemStack from, final ItemStack to) {
        final ItemMeta fromMeta = from.getItemMeta(), toMeta = to.getItemMeta();

        if (fromMeta.hasDisplayName()) {
            toMeta.setDisplayName(fromMeta.getDisplayName());
        }

        if (fromMeta.hasLore()) {
            toMeta.setLore(fromMeta.getLore());
        }

        to.setItemMeta(toMeta);
    }

    private ItemUtil() {}
}
