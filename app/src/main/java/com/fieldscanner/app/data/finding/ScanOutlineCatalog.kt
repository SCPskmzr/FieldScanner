package com.fieldscanner.app.data.finding

import java.util.Locale

/**
 * Closed, flat catalogue of the 100 product-approved `visual_id` values.
 *
 * Order is normative and matches the appendix of TASK-005 and
 * `docs/artwork/scan-outline-catalog.md`. Each entry's identifier equals its
 * enum name; its artwork asset is `name.lowercase(Locale.US) + ".svg"` under
 * `assets/scan_outlines/` (`CAT` -> `cat.svg`, `PEN_PENCIL` -> `pen_pencil.svg`).
 *
 * [UNKNOWN_OBJECT] is the last entry and the single terminal fallback: unknown,
 * blank, `null` or legacy input resolves to it, and it is the tail of every
 * [fallbackChain]. There is no per-category-generic tier in this model.
 *
 * Only assets that have actually been delivered exist on disk — a missing file
 * is handled by [fallbackChain] and, ultimately, by the renderer's procedural
 * fallback, so naming an entry here never risks a crash. Currently only
 * `cat.svg` is shipped.
 */
enum class ScanVisual {
    PERSON,
    FACE,
    HAND,
    DOG,
    CAT,
    BIRD,
    FISH,
    FARM_ANIMAL,
    SMALL_MAMMAL,
    INSECT,
    BUTTERFLY,
    SPIDER,
    SNAIL,
    REPTILE_AMPHIBIAN,
    BALL,
    TOY_CAR,
    TOY_FIGURE,
    PLUSH_TOY,
    BUILDING_BLOCKS,
    TOY_WEAPON,
    BOARD_GAME,
    CARD,
    PUZZLE,
    PHONE,
    LAPTOP,
    HEADPHONES,
    ELECTRONIC_DEVICE,
    CAR,
    TRUCK,
    BUS,
    BICYCLE,
    MOTORCYCLE,
    TRAIN,
    CONSTRUCTION_VEHICLE,
    AIRCRAFT,
    BOAT,
    LAMP,
    DOOR,
    WINDOW,
    MIRROR,
    FURNITURE,
    BUILDING,
    CUP,
    BOTTLE,
    PLATE_BOWL,
    BOX,
    BAG,
    BACKPACK,
    KEY,
    CLOCK_WATCH,
    GLASSES,
    UMBRELLA,
    COIN,
    SHOE,
    HAT,
    SHIRT,
    PANTS,
    JACKET,
    BOOK,
    PAPER,
    PEN_PENCIL,
    ART_CRAFTS,
    HAND_TOOL,
    METAL_PART,
    FRUIT,
    BREAD,
    SWEET,
    DRINK,
    PREPARED_FOOD,
    CUTLERY,
    POT_PAN,
    REFRIGERATOR,
    VACUUM_CLEANER,
    TOOTHBRUSH,
    JEWELRY,
    SPORTS_EQUIPMENT,
    MUSICAL_INSTRUMENT,
    PLAYGROUND_EQUIPMENT,
    HELMET,
    BALLOON,
    STREET_SIGN,
    TRASH_LITTER,
    BATTERY,
    CABLE_CHARGER,
    TOILET,
    ROCK,
    LEAF,
    FLOWER,
    STICK_BRANCH,
    TREE_PLANT,
    MUSHROOM,
    FIRE,
    WATER,
    SNOW_ICE,
    SAND_SOIL,
    GLASS,
    BARK_WOOD,
    SHELL,
    BONE,

    /** Terminal fallback for unrecognised, malformed, or legacy `visual_id` values. */
    UNKNOWN_OBJECT;

    /** Stable string identifier; equal to the enum name. */
    val id: String get() = name

    /** Local SVG asset file name under `assets/scan_outlines/`. */
    val assetName: String get() = name.lowercase(Locale.US) + ".svg"

    /**
     * Deterministic order in which assets are tried when rendering this visual:
     * the requested asset, then [UNKNOWN_OBJECT]. The renderer keeps its own
     * procedural fallback for the case where neither asset can be loaded.
     */
    fun fallbackChain(): List<ScanVisual> =
        if (this == UNKNOWN_OBJECT) listOf(UNKNOWN_OBJECT) else listOf(this, UNKNOWN_OBJECT)

    companion object {
        /** Parses a raw provider/stored value; unknown or blank input returns `null`. */
        fun fromId(raw: String?): ScanVisual? {
            val normalized = raw?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() } ?: return null
            return values().firstOrNull { it.name == normalized }
        }

        /** Like [fromId] but never fails: unknown values collapse to [UNKNOWN_OBJECT]. */
        fun resolve(raw: String?): ScanVisual = fromId(raw) ?: UNKNOWN_OBJECT
    }
}
