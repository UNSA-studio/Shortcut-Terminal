package unsa.st.com.testing;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import unsa.st.com.block.*;
import unsa.st.com.compute.*;
import unsa.st.com.menu.*;
import unsa.st.com.registry.*;

/** Real server tests, only discovered by NeoForge's development GameTest runner. */
@GameTestHolder("shortcutterminal")
@PrefixGameTestTemplate(false)
public class GuiRegressionGameTests {
    private static final BlockPos POS = new BlockPos(4, 1, 4);
    private static void fill(AssemblyBenchBlockEntity bench) {
        bench.setItem(0, new ItemStack(ModItems.IRON_SHELL.get()));
        bench.setItem(1, new ItemStack(ModItems.TERMINAL_MOTHERBOARD.get()));
        bench.setItem(2, new ItemStack(ModItems.PROCESSORS.get(4).get()));
        bench.setItem(3, new ItemStack(ModItems.RAM_MODULES.get(2).get()));
        bench.setItem(4, new ItemStack(ModItems.SSD_MODULES.get(3).get()));
        bench.setItem(5, new ItemStack(ModItems.DISPLAY_SCREEN.get()));
        bench.setItem(6, new ItemStack(ModItems.BONDING_GLUE.get()));
    }
    @GameTest(template = "empty")
    public static void assemblyTransactionAndShiftClick(GameTestHelper h) {
        h.setBlock(POS, ModBlocks.ASSEMBLY_BENCH.get());
        AssemblyBenchBlockEntity bench = h.getBlockEntity(POS);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos real = h.absolutePos(POS);
        player.setPos(real.getX() + .5, real.getY(), real.getZ() + .5);
        AssemblyBenchMenu menu = new AssemblyBenchMenu(1, player.getInventory(), bench, real);
        h.assertTrue(menu.slots.size() == 44, "Server assembly menu must have 8 + 36 slots");
        h.assertFalse(menu.slots.get(1).mayPlace(new ItemStack(ModItems.TERMINAL_MOTHERBOARD.get())), "Board cannot precede shell");
        h.assertTrue(ProcessorCapability.levelOfProcessorItem(new ItemStack(ModItems.LITHO_MASKS.get(9).get())) == -1,
                "A mask must not masquerade as a CPU");
        h.assertFalse(menu.clickMenuButton(player, 0), "Incomplete assembly must not consume anything");
        fill(bench);
        h.assertTrue(bench.getItem(7).isEmpty(), "No unpaid output preview may exist");
        h.assertFalse(menu.slots.get(0).mayPickup(player), "Cannot pull shell out from under installed components");
        h.assertTrue(menu.clickMenuButton(player, 0), "Assemble transaction must succeed");
        ItemStack panel = bench.getItem(7);
        h.assertTrue(ProcessorCapability.getInstalledLevel(panel) == 4, "Installed CPU must survive assembly");
        h.assertTrue(HardwareSpec.getRamMb(panel) == 4096, "Installed RAM must survive assembly");
        h.assertTrue(HardwareSpec.getSsdGb(panel) == 1024, "Installed SSD must survive assembly");
        for (int i = 0; i < 7; i++) h.assertTrue(bench.getItem(i).isEmpty(), "Each input consumed exactly once");
        h.assertFalse(menu.clickMenuButton(player, 0), "Repeated button packet cannot duplicate output");
        h.assertFalse(menu.quickMoveStack(player, 7).isEmpty(), "Shift-click must transfer a paid output");
        h.assertTrue(bench.getItem(7).isEmpty(), "Shift-click must empty output without regenerating it");
        h.assertTrue(menu.quickMoveStack(player, 7).isEmpty(), "Second shift-click produces nothing");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void clientMenusDoNotRequireABlockEntity(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var assemblyBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        var lithoBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            assemblyBuf.writeBlockPos(BlockPos.ZERO);
            lithoBuf.writeBlockPos(BlockPos.ZERO);
            var assembly = new AssemblyBenchMenu(1, player.getInventory(), assemblyBuf);
            var litho = new LithographyMachineMenu(2, player.getInventory(), lithoBuf);
            h.assertTrue(assembly.slots.size() == 44, "Client assembly inventory must not vanish before chunk load");
            h.assertTrue(litho.slots.size() == 42, "Client lithography inventory must always match server indices");
            h.assertTrue(litho.getProgress() == 0 && !litho.isWorking(), "Client progress is menu data, not an unsynced BE");
            h.succeed();
        } finally { assemblyBuf.release(); lithoBuf.release(); }
    }
    @GameTest(template = "empty")
    public static void lithographyFuelOutputAndLocking(GameTestHelper h) {
        h.setBlock(POS, ModBlocks.LITHOGRAPHY_MACHINE.get());
        LithographyMachineBlockEntity machine = h.getBlockEntity(POS);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (x != 0 || z != 0) h.setBlock(POS.offset(x, 0, z), ModBlocks.MACHINE_CASING.get());
        }
        h.assertTrue(machine.isFormed(), "3x3 machine should form");
        machine.setItem(0, new ItemStack(ModItems.LOGIC_WAFER.get(), 3));
        machine.setItem(1, new ItemStack(ModItems.LITHO_MASKS.get(9).get()));
        machine.setItem(2, new ItemStack(ModItems.POWER_COIL.get()));
        BlockPos real = h.absolutePos(POS);
        machine.serverTick(h.getLevel(), real);
        h.assertTrue(machine.isWorking(), "Valid inputs must start processing");
        h.assertFalse(machine.canPlaceItem(1, new ItemStack(ModItems.LITHO_MASKS.get(1).get())), "Working mask slot must be locked");
        for (int i = 1; i < 400; i++) machine.serverTick(h.getLevel(), real);
        int output = 0; for (int i = 3; i < 6; i++) output += machine.getItem(i).getCount();
        h.assertTrue(output == 10, "One wafer must yield exactly 10 CPUs without spilling");
        h.assertTrue(machine.data.get(2) == 1, "One coil must retain energy for second batch");
        h.assertTrue(machine.getItem(0).getCount() == 2 && machine.getItem(1).getCount() == 1, "Consume wafer, retain permanent mask");
        for (int i = 0; i < 20; i++) machine.serverTick(h.getLevel(), real);
        h.assertFalse(machine.isWorking(), "Full output must pause processing");
        for (int i = 3; i < 6; i++) machine.removeItem(i, 64);
        for (int i = 0; i < 400; i++) machine.serverTick(h.getLevel(), real);
        output = 0; for (int i = 3; i < 6; i++) output += machine.getItem(i).getCount();
        h.assertTrue(output == 10 && machine.data.get(2) == 0, "Second batch must consume remaining coil energy");
        for (int i = 3; i < 6; i++) machine.removeItem(i, 64);
        for (int i = 0; i < 20; i++) machine.serverTick(h.getLevel(), real);
        h.assertFalse(machine.isWorking(), "No third batch without another coil");
        h.assertTrue(machine.getItem(0).getCount() == 1, "Missing fuel must not consume another wafer");
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void cpuAndTotalRamPolicy(GameTestHelper h) {
        for (int level = 1; level <= 9; level++) for (int seed = 0; seed < 256; seed++) {
            int[] batch = YieldTable.rollBatch(level, new java.util.Random(seed));
            h.assertTrue(batch.length == 10, "Batch size must not vary with random seed");
            for (int actual : batch) h.assertTrue(actual >= Math.max(1, level - 2) && actual <= level,
                    "Quality failures may downgrade only once, by one or two levels");
        }
        h.assertTrue(WindowResourcePolicy.CPU_ERROR.equals(WindowResourcePolicy.creationError(2, 2, 20, 200)), "CPU is hard limit");
        h.assertTrue(WindowResourcePolicy.RAM_ERROR.equals(WindowResourcePolicy.creationError(2, 8, 161, 200)), "Sum of all windows above 80% must block");
        h.assertTrue(WindowResourcePolicy.creationError(2, 8, 160, 200) == null, "Exactly 80% is not above 80%");
        for (int i = 0; i < 32; i++) {
            int visible = WindowResourcePolicy.visibleTabCount(304);
            int first = WindowResourcePolicy.firstVisibleTab(i, 0, visible, 32);
            h.assertTrue(i >= first && i < first + visible, "All 32 CPU windows must remain reachable at small resolution");
        }
        h.succeed();
    }
}