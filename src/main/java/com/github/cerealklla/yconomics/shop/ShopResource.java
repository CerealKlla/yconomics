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
public record ShopResource(Optional<TagKey<Item>> tag, Optional<Identifier> itemId) {

    public static final Codec<ShopResource> CODEC = RecordCodecBuilder.create(i -> i.group(
            TagKey.codec(Registries.ITEM).optionalFieldOf("tag").forGetter(ShopResource::tag),
            Identifier.CODEC.optionalFieldOf("item_id").forGetter(ShopResource::itemId)
    ).apply(i, ShopResource::new));

    public ShopResource {
        if (tag.isPresent() == itemId.isPresent()) {
            throw new IllegalArgumentException("ShopResource must set exactly one of tag/itemId, not both or neither");
        }
    }

    public static ShopResource ofTag(TagKey<Item> tag) {
        return new ShopResource(Optional.of(tag), Optional.empty());
    }

    public static ShopResource ofItem(Identifier itemId) {
        return new ShopResource(Optional.empty(), Optional.of(itemId));
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (tag.isPresent()) {
            return stack.is(tag.get());
        }
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId.get());
    }

    /** A stable key for this resource, for grouping/lookup -- the tag's own id, or the plain item id. */
    public Identifier key() {
        return tag.map(TagKey::location).orElseGet(itemId::get);
    }
}
