package unsa.st.com.registry;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import unsa.st.com.ShortcutTerminal;
import unsa.st.com.item.TerminalPanelItem;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShortcutTerminal.MODID);

    // ===== Tier 0: raw starting materials =====
    /** Raw silicon chunk, base of all electronics. */
    public static final DeferredItem<Item> RAW_SILICON = ITEMS.register("raw_silicon",
            () -> new Item(new Item.Properties()));
    /** Purified silicon wafer. */
    public static final DeferredItem<Item> SILICON_WAFER = ITEMS.register("silicon_wafer",
            () -> new Item(new Item.Properties()));
    /** Etched wafer carrying tiny logic circuits. */
    public static final DeferredItem<Item> LOGIC_WAFER = ITEMS.register("logic_wafer",
            () -> new Item(new Item.Properties()));
    /** Single logic chip cut from a wafer. */
    public static final DeferredItem<Item> LOGIC_CHIP = ITEMS.register("logic_chip",
            () -> new Item(new Item.Properties()));
    /** Refined metal plate used in casings. */
    public static final DeferredItem<Item> REFINED_IRON = ITEMS.register("refined_iron",
            () -> new Item(new Item.Properties()));
    /** Conductive wiring. */
    public static final DeferredItem<Item> COPPER_WIRE = ITEMS.register("copper_wire",
            () -> new Item(new Item.Properties()));
    /** Basic circuit board. */
    public static final DeferredItem<Item> CIRCUIT_BOARD = ITEMS.register("circuit_board",
            () -> new Item(new Item.Properties()));
    /** Advanced board with redstone signal processing. */
    public static final DeferredItem<Item> ADVANCED_CIRCUIT = ITEMS.register("advanced_circuit",
            () -> new Item(new Item.Properties()));
    /** Processing unit (legacy tier item; superseded by processor_l1-l9 ladder). */
    public static final DeferredItem<Item> PROCESSING_UNIT = ITEMS.register("processing_unit",
            () -> new Item(new Item.Properties()));
    /** Data storage cell. */
    public static final DeferredItem<Item> MEMORY_BANK = ITEMS.register("memory_bank",
            () -> new Item(new Item.Properties()));
    /** Glass display component. */
    public static final DeferredItem<Item> DISPLAY_SCREEN = ITEMS.register("display_screen",
            () -> new Item(new Item.Properties()));
    /** Power coil for stable energy. */
    public static final DeferredItem<Item> POWER_COIL = ITEMS.register("power_coil",
            () -> new Item(new Item.Properties()));

    // ===== Processor L1-L9 (produce by lithography machine only) =====
    /** Ladder of processors produced by the lithography machine, shared texture. */
    public static final java.util.Map<Integer, DeferredItem<Item>> PROCESSORS = new java.util.HashMap<>();
    static {
        for (int l = 1; l <= 9; l++) {
            final int lvl = l;
            PROCESSORS.put(l, ITEMS.register("processor_l" + lvl,
                    () -> new Item(new Item.Properties())));
        }
    }

    // ===== Lithography masks L1-L9 (permanent blueprints, shared texture) =====
    public static final java.util.Map<Integer, DeferredItem<Item>> LITHO_MASKS = new java.util.HashMap<>();
    static {
        for (int l = 1; l <= 9; l++) {
            final int lvl = l;
            LITHO_MASKS.put(l, ITEMS.register("lithography_mask_l" + lvl,
                    () -> new Item(new Item.Properties())));
        }
    }

    // ===== Terminal motherboard (replaces terminal_core) =====
    public static final DeferredItem<Item> TERMINAL_MOTHERBOARD = ITEMS.register("terminal_motherboard",
            () -> new Item(new Item.Properties()));

    // ===== Final products =====
    public static final DeferredItem<Item> TERMINAL_PANEL = ITEMS.register("terminal_panel",
            () -> new TerminalPanelItem(new Item.Properties()));
}