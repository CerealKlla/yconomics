package com.github.cerealklla.yconomics.shop;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What a Shop listing/cost is priced in -- either a whole category of items (a {@link TagKey}, e.g.
 * "any log") or one specific item (a plain {@link Identifier}), never both (design doc Section 5c,
 * added 2026-10-05).
 *
 * <p>Deliberately <b>not</b> Blueprynts' {@code blueprint.GenericResource} -- that's a closed
 * 2-value enum (Wood/Stone) owned by a construction-focused mod; pulling Yconomics into a dependency
 * on it for an unrelated reason (shop pricing) would be backwards. A tag is just a datapack JSON
 * file, so any mod (Lyfe, Settlemynts, Blueprynts) can reference the exact same tag id here with
 * zero code coupling -- including Blueprynts' own {@code generic_wood}/{@code generic_stone} tags,
 * if a caller wants pricing to line up with Construction Box cost categories.
 *
 * <p><b>Tag folder gotcha</b> (hit for real in Blueprynts, see its own CLAUDE.md, 2026-09-30): this
 * MC version's data-pack tag folders are singular -- {@code data/<ns>/tags/item/<path>.json}, not
 * plural {@code tags/items/}. A plural folder silently loads as an always-empty tag, no error.
 */
public record ShopResource(Optional<TagKey<Item>> tag, Optional<Identifier> itemId, Optional<String> customName) {

    public static final Codec<ShopResource> CODEC = RecordCodecBuilder.create(i -> i.group(
            TagKey.codec(Registries.ITEM).optionalFieldOf("tag").forGetter(ShopResource::tag),
            Identifier.CODEC.optionalFieldOf("item_id").forGetter(ShopResource::itemId),
            Codec.STRING.optionalFieldOf("custom_name").forGetter(ShopResource::customName)
    ).apply(i, ShopResource::new));

    public ShopResource {
        if (tag.isPresent() == itemId.isPresent()) {
            throw new IllegalArgumentException("ShopResource must set exactly one of tag/itemId, not both or neither");
        }
    }

    public static ShopResource ofTag(TagKey<Item> tag) {
        return new ShopResource(Optional.of(tag), Optional.empty(), Optional.empty());
    }

    public static ShopResource ofItem(Identifier itemId) {
        return new ShopResource(Optional.empty(), Optional.of(itemId), Optional.empty());
    }

    /**
     * A listing for one exact crafted-quality variant of a concrete item (2026-10-10, for
     * Settlemynts' per-quality Shop listings -- e.g. two different "Bread" stacks baked at
     * different Cook/structure quality, each independently priced) -- {@code customName} is the
     * stack's real baked display text (e.g. {@code "[3.75] (T5) - Bread"}), matched exactly.
     */
    public static ShopResource ofExactItem(Identifier itemId, String customName) {
        return new ShopResource(Optional.empty(), Optional.of(itemId), Optional.of(customName));
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (tag.isPresent()) {
            return stack.is(tag.get());
        }
        if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId.get())) {
            return false;
        }
        return customName.isEmpty() || customName.get().equals(stack.getHoverName().getString());
    }

    /** A stable key for this resource, for grouping/lookup -- the tag's own id, or the plain item id. Ignores {@link #customName} -- see {@link #sameVariantAs} for an exact-variant-aware comparison. */
    public Identifier key() {
        return tag.map(TagKey::location).orElseGet(itemId::get);
    }

    /**
     * Does {@code this} and {@code other} refer to the same real listing -- added 2026-10-09, real
     * report: a tag-based cost search (Settlemynts' "Logs," {@code generic_wood}) never found a
     * listing a Manage Shop screen had created for a concrete item (e.g. {@code oak_log}, since a box
     * slot is always a concrete stack, never a tag), because the two call sites in this class used
     * to compare {@link #key()} directly -- a tag's key can never equal a different item's key, full
     * stop, even though the item plainly falls under that tag. {@link #key()} itself is unchanged
     * (still used for plain grouping where both sides are already known to be the same kind); this is
     * the listing-lookup-specific comparison that also handles one side being a tag and the other a
     * concrete item.
     *
     * <p>Deliberately ignores {@link #customName} (2026-10-10) -- this is used to find "the listing a
     * generic buy/sell request should transact against," which must keep matching any quality variant
     * of an item (e.g. a generic Planned Inventory "Bread" target buying/selling any Bread it finds) --
     * see {@link #sameVariantAs} for the exact-variant comparison {@code ShopSavedData} needs instead,
     * for adding/removing one specific listing without colliding with a sibling variant's own listing.
     */
    public boolean coversSameListingAs(ShopResource other) {
        if (tag.isPresent() && other.tag.isPresent()) {
            return tag.get().equals(other.tag.get());
        }
        if (itemId.isPresent() && other.itemId.isPresent()) {
            return itemId.get().equals(other.itemId.get());
        }
        if (tag.isPresent() && other.itemId.isPresent()) {
            return matches(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(other.itemId.get())));
        }
        if (itemId.isPresent() && other.tag.isPresent()) {
            return other.matches(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(itemId.get())));
        }
        return false;
    }

    /**
     * Exact-variant equality (2026-10-10, added alongside {@link #ofExactItem}) -- {@code
     * ShopSavedData#setListingPrice}/{@code #removeListing} use this (not {@link #key()}) so two
     * different quality variants of the same item (e.g. "Bread [1.0]" and "Bread [3.75]") are always
     * treated as two distinct listings, never silently overwriting/removing each other.
     */
    public boolean sameVariantAs(ShopResource other) {
        return tag.equals(other.tag) && itemId.equals(other.itemId) && customName.equals(other.customName);
    }
}
