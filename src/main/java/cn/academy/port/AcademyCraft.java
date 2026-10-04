package cn.academy.port;
import net.minecraft.world.item.Item;
import cn.academy.port.develop.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
@Mod("academy")
public final class AcademyCraft {
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("academy");
    public static final DeferredItem<Item> DEVELOPER=ITEMS.register("portable_developer",()->new DeveloperItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> INDUCTION_FACTOR=ITEMS.register("induction_factor",()->new InductionFactorItem(new Item.Properties()));
    public static final DeferredItem<Item> MAGNETIC_COIL=ITEMS.registerSimpleItem("magnetic_coil",new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> COIN=ITEMS.register("coin",()->new CoinItem(new Item.Properties()));
    public static final DeferredItem<Item> NEEDLE=ITEMS.registerSimpleItem("needle",new Item.Properties());
    public AcademyCraft(IEventBus bus,ModContainer container) {bus.addListener(AcademyConfig::loading);bus.addListener(AcademyConfig::reloading);bus.addListener(AcademyConfig::unloading);cn.academy.port.terminal.TerminalModule.register(bus);cn.academy.port.tutorial.TutorialModule.register(bus,container);container.registerConfig(ModConfig.Type.SERVER,cn.academy.port.skill.ClassicMineOreAdapter.SPEC,"academy-mine-detection-server.toml");cn.academy.port.wireless.ClassicWirelessDevices.register(bus);cn.academy.port.fusion.ClassicFusion.register(bus,container);cn.academy.port.former.ClassicMetalFormer.register(bus);cn.academy.port.skill.ElectromasterEntities.register(bus);cn.academy.port.skill.MeltdownerLateEntities.register(bus);container.registerConfig(ModConfig.Type.SERVER,cn.academy.port.skill.ClassicMetalTargets.SPEC,"academy-electromaster-server.toml");cn.academy.port.solar.ClassicSolarGenerators.register(bus);cn.academy.port.phasegen.ClassicPhaseGenerators.register(bus);cn.academy.port.wind.ClassicWindGenerators.register(bus);cn.academy.port.bridge.ClassicEnergyBridges.register(bus);cn.academy.port.cat.ClassicCatEngines.register(bus);cn.academy.port.display.ClassicDisplayItems.register(bus);cn.academy.port.interferer.ClassicAbilityInterferers.register(bus);cn.academy.port.energy.ClassicEnergyItems.register(bus);cn.academy.port.machine.MachineDevelopers.register(bus);cn.academy.port.survival.ClassicMaterials.register(bus,container);container.registerConfig(ModConfig.Type.SERVER,AcademyConfig.SPEC);container.registerConfig(ModConfig.Type.CLIENT,ClassicHudConfig.SPEC);cn.academy.port.hook.ClassicHooks.register(bus);ITEMS.register(bus);bus.addListener(AcademyNetwork::register);bus.addListener(this::capabilities);bus.addListener(this::creative);NeoForge.EVENT_BUS.register(AcademyGameplay.class);}
    private void capabilities(RegisterCapabilitiesEvent event){event.registerItem(Capabilities.EnergyStorage.ITEM,(stack,context)->new DeveloperItemEnergy(stack,DeveloperType.PORTABLE).forgeEnergy(),DEVELOPER.get());}
    private void creative(BuildCreativeModeTabContentsEvent event) {if(event.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES){event.accept(DEVELOPER);var charged=DEVELOPER.get().getDefaultInstance();new DeveloperItemEnergy(charged,DeveloperType.PORTABLE).energy(10000);event.accept(charged);event.accept(NEEDLE);event.accept(cn.academy.port.hook.ClassicHooks.MAGHOOK);event.accept(COIN);event.accept(MAGNETIC_COIL);event.accept(cn.academy.port.skill.MeltdownerLateEntities.SILBARN);for(var category:DevelopmentActions.CATEGORIES)event.accept(InductionFactors.stack(category));}}
}
