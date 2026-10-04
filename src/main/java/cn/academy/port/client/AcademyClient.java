package cn.academy.port.client;
import cn.academy.port.*;
import cn.academy.port.core.AbilityProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class AcademyClient {
    public static AbilityProgress state=new AbilityProgress();
    private static final ClassicActivationClientState activationState=new ClassicActivationClientState();
    public static net.minecraft.nbt.CompoundTag development;
    public static final KeyMapping TOGGLE=new KeyMapping("key.academy.toggle",GLFW.GLFW_KEY_V,"key.categories.academy");
    public static final KeyMapping SWITCH_PRESET=new KeyMapping("key.academy.switch_preset",GLFW.GLFW_KEY_C,"key.categories.academy");
    public static final KeyMapping EDIT_PRESET=new KeyMapping("key.academy.edit_preset",GLFW.GLFW_KEY_N,"key.categories.academy");
    public static final KeyMapping[] SLOTS={
        new KeyMapping("key.academy.ability_0",com.mojang.blaze3d.platform.InputConstants.Type.MOUSE,GLFW.GLFW_MOUSE_BUTTON_LEFT,"key.categories.academy"),
        new KeyMapping("key.academy.ability_1",com.mojang.blaze3d.platform.InputConstants.Type.MOUSE,GLFW.GLFW_MOUSE_BUTTON_RIGHT,"key.categories.academy"),
        new KeyMapping("key.academy.ability_2",GLFW.GLFW_KEY_R,"key.categories.academy"),
        new KeyMapping("key.academy.ability_3",GLFW.GLFW_KEY_F,"key.categories.academy")
    };
    private static final ClassicActivationKey activation=new ClassicActivationKey();
    private static final ClassicInputLatch switchLatch=new ClassicInputLatch(),editLatch=new ClassicInputLatch();
    private static final ClientSessionGuard session=new ClientSessionGuard();
    private static Object ownedConnection;
    private static ClassicClientInputBinding binding;
    private static int[] overrideKeys=new int[0];
    private static String mappingSignature="";
    private static boolean deathFenced;
    private static long singleInputCounter,singleInputEpoch,serverSingleEpoch;
    private static boolean down(KeyMapping mapping){return physicalDown(sourceKey(mapping.getKey()));}
    private static int sourceKey(com.mojang.blaze3d.platform.InputConstants.Key key){return key.getType()==com.mojang.blaze3d.platform.InputConstants.Type.MOUSE?(key.getValue()>=0&&key.getValue()<=GLFW.GLFW_MOUSE_BUTTON_LAST?ClassicPhysicalKeys.mouse(key.getValue()):0):key.getType()==com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM?ClassicPhysicalKeys.keysym(key.getValue()):sourceScancode(key.getValue());}
    private static int glfwForScan(int scan){if(scan<0)return -1;for(int glfw:ClassicPhysicalKeys.supportedKeysyms().keySet())if(GLFW.glfwGetKeyScancode(glfw)==scan)return glfw;for(int glfw=305;glfw<=314;glfw++)if(GLFW.glfwGetKeyScancode(glfw)==scan)return glfw;for(int glfw:new int[]{161,162})if(GLFW.glfwGetKeyScancode(glfw)==scan)return glfw;return -1;}
    private static int sourceScancode(int scan){int glfw=glfwForScan(scan);return glfw<0?ClassicPhysicalKeys.scancode(scan):ClassicPhysicalKeys.keysym(glfw);}
    private static boolean nativeKey(int glfw){return glfw>=GLFW.GLFW_KEY_SPACE&&glfw<=GLFW.GLFW_KEY_LAST&&(ClassicPhysicalKeys.supportedKeysyms().containsKey(glfw)||glfw==GLFW.GLFW_KEY_KP_EQUAL||glfw==GLFW.GLFW_KEY_WORLD_1||glfw==GLFW.GLFW_KEY_WORLD_2||glfw>=GLFW.GLFW_KEY_F16&&glfw<=GLFW.GLFW_KEY_F25);}
    private static boolean physicalDown(int key){var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return false;if(key<0){int button=key+100;return button>=0&&button<=GLFW.GLFW_MOUSE_BUTTON_LAST&&GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),button)==GLFW.GLFW_PRESS;}int glfw=ClassicPhysicalKeys.glfw(key);if(nativeKey(glfw))return com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow().getWindow(),glfw);int scan=ClassicPhysicalKeys.scan(key);if(scan>=0){int actual=glfwForScan(scan);return nativeKey(actual)&&GLFW.glfwGetKey(mc.getWindow().getWindow(),actual)==GLFW.GLFW_PRESS;}return false;}
    private static long now(){return net.minecraft.Util.getMillis();}
    public static KeyMapping skillKey(String id){for(int slot=0;slot<SLOTS.length;slot++)if(state.presets.currentSkill(slot).equals(id))return SLOTS[slot];return null;}
    public static boolean delegatePresent(int slot){return binding!=null&&slot>=0&&slot<SLOTS.length&&binding.ownsDefault(slot);}
    public static boolean delegateActive(int slot){return binding!=null&&slot>=0&&slot<SLOTS.length&&binding.activeDefault(slot);}
    public static boolean activationHeld(){return activation.held();}
    public static long activationHeldMillis(){return activation.heldMillis(now());}
    public static boolean displayNumbers(){return activation.displayNumbers(now());}
    private static String mappings(){StringBuilder value=new StringBuilder();for(int slot=0;slot<SLOTS.length;slot++)value.append(state.presets.currentSkill(slot)).append(':').append(sourceKey(SLOTS[slot].getKey())).append(';');return value.toString();}
    private static ClassicClientInputBinding createBinding(Minecraft mc){
        final Object level=mc.level,player=mc.player,connection=mc.getConnection();
        return new ClassicClientInputBinding(new ClassicClientInputBinding.Host(){
            public boolean available(){return mc.level==level&&mc.player==player&&mc.getConnection()==connection&&level!=null&&player!=null;}
            public boolean inGame(){return available()&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();}
            public boolean endTickAllowed(){return inGame()&&!mc.isPaused();}
            public int actorId(){return mc.player==null?-1:mc.player.getId();}public long ownerEpoch(){return serverSingleEpoch;}
            public boolean categoryActivated(){return state.isActivated();}
            public boolean canUseAbility(){return state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress();}
            public boolean terminalOpen(){return mc.screen!=null;}
            public boolean physicalDown(int key){return AcademyClient.physicalDown(key);}
            public boolean registeredMapping(String skill){return cn.academy.port.preset.PresetSkills.registeredMapping(state,skill);}
            public boolean eligible(ClassicClientInputBinding.Node node){return inGame()&&state.canUse(node.skill)&&cn.academy.port.preset.PresetSkills.mappedUsable(state,node.skill)&&SkillCatalog.enabled(state.category,node.skill);}
            public String mappedSkill(int slot){return state.presets.currentSkill(slot);}public int mappedKey(int slot){return sourceKey(SLOTS[slot].getKey());}public int slotCount(){return SLOTS.length;}
            public void rawActivation(boolean desired){activationState.activate(state,false,()->{},value->request("activate_state",Boolean.toString(value)));}
            public void replaceOverrides(int[] keys){overrideKeys=keys.clone();}
            public long down(ClassicClientInputBinding.Node node){return press(node);}
            public void up(ClassicClientInputBinding.Node node,long input){terminal(node,input,false);}
            public void abort(ClassicClientInputBinding.Node node,long input){terminal(node,input,true);}
            public boolean cancelPending(ClassicClientInputBinding.Node node,long input){if(input<=0||cn.academy.port.core.TargetedContextTermination.Skill.find(node.skill)==null)return false;request("pending_context_abort",node.skill+":"+input);if(node.skill.equals("flashing"))ClassicTeleporterFinalEffects.endLocal(node.skill);else if(node.skill.equals("storm_wing"))ClassicVectorCombatEffects.abortLocal(node.skill);else if(node.skill.equals("vec_deviation"))ClassicVectorStarterEffects.endLocal(node.skill);else if(node.skill.equals("vec_reflection"))ClassicVectorFinalEffects.endLocal(node.skill);return true;}
            public boolean railgunCharges(ClassicClientInputBinding.Node node){return node.input()>=0&&(mc.player.getMainHandItem().is(net.minecraft.world.item.Items.IRON_INGOT)||mc.player.getMainHandItem().is(net.minecraft.world.item.Items.IRON_BLOCK));}
            // Source resets charge locally; this scoped modern cancellation protects the independent server timer.
            public void railgunEnd(ClassicClientInputBinding.Node node,boolean abort){request("railgun_abort","");}
            public void direction(ClassicClientInputBinding.ContextIdentity context,int direction,ClassicClientInputBinding.Edge edge,boolean silent){
                if(context.skill.equals("flashing"))ClassicTeleporterFinalEffects.direction(context.input,context.token,direction,edge,silent);
                else if(!silent)request("storm_direction",context.node.slot+":"+context.input+":"+(edge==ClassicClientInputBinding.Edge.DOWN?direction-1:-1));
            }
            public void localContextEnd(ClassicClientInputBinding.ContextIdentity context){if(context.skill.equals("flashing"))ClassicTeleporterFinalEffects.terminateContext(context.input,context.token);else if(context.skill.equals("storm_wing"))ClassicVectorCombatEffects.abortLocal(context.skill);else if(context.skill.equals("vec_deviation"))ClassicVectorStarterEffects.terminateContext(context.input,context.token);else if(context.skill.equals("vec_reflection"))ClassicVectorFinalEffects.terminateContext(context.input,context.token);}
            public void contextAbort(cn.academy.port.core.TargetedContextTermination.Request identity){request(cn.academy.port.core.TargetedContextTermination.ACTION,identity.wire());}
            public void modernCleanup(java.util.Collection<ClassicClientInputBinding.Node> defaults){for(var node:defaults)cleanupModern(node.skill);}
            public void rejectedSingle(ClassicClientInputBinding.Node node,long input,long capturedOwnerEpoch){if(node.skill.equals("ground_shock"))ClassicGroundShockEffects.abortLocal(input,capturedOwnerEpoch);if(node.skill.equals("charging"))ClassicChargingEffects.endLocal(input,capturedOwnerEpoch);if(node.skill.equals("mag_movement")||node.skill.equals("mag_manip"))ClassicMagneticEffects.abortLocal(node.skill,input,capturedOwnerEpoch);if(ClassicFirstSkillEffects.ownsSingleKey(node.skill))ClassicFirstSkillEffects.retireLocal(node.skill,input,capturedOwnerEpoch);}
        });
    }
    /** Existing prepress close is an explicit modern policy, not a source delegate callback. */
    private static long press(ClassicClientInputBinding.Node node){
        String skill=node.skill;int slot=node.slot;
        if(!skill.equals("flashing")&&ClassicTeleporterFinalEffects.delegateActive("flashing")){var owned=binding==null?null:binding.context("flashing");if(owned!=null)ClassicTeleporterFinalEffects.hideLocal("flashing");}
        var legacy=cn.academy.port.core.LegacySingleKeyProtocol.Skill.find(skill);
        if(legacy!=null){
            if(singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            long input=++singleInputCounter;
            if(skill.equals("ground_shock"))ClassicGroundShockEffects.startLocal(input);
            if(ClassicFirstSkillEffects.ownsSingleKey(skill))ClassicFirstSkillEffects.startLocal(skill,input);
            request(cn.academy.port.core.LegacySingleKeyProtocol.PRESS,new cn.academy.port.core.LegacySingleKeyProtocol.StartRequest(slot,legacy,input,node.actorId(),node.ownerEpoch()).wire());
            return input;
        }
        long input=0;
        if(ClassicVectorCombatEffects.owns(skill))input=ClassicVectorCombatEffects.startLocal(skill);
        if(ClassicVectorFinalEffects.owns(skill))input=ClassicVectorFinalEffects.startLocal(skill);
        if(ClassicVectorStarterEffects.owns(skill))input=ClassicVectorStarterEffects.startLocal(skill);
        if(skill.equals("body_intensify"))input=ClassicBodyIntensifyEffects.startLocal();
        if(skill.equals("thunder_clap"))input=ClassicThunderClapEffects.startLocal();
        if(ClassicMeltdownerStarterEffects.owns(skill))input=ClassicMeltdownerStarterEffects.startLocal(skill);
        if(ClassicMeltdownerBeamEffects.owns(skill))input=ClassicMeltdownerBeamEffects.startLocal(skill);
        if(ClassicMeltdownerLateEffects.owns(skill))input=ClassicMeltdownerLateEffects.startLocal(skill);
        if(ClassicTeleporterProgressionEffects.owns(skill))input=ClassicTeleporterProgressionEffects.startLocal(skill);
        if(ClassicTeleporterFinalEffects.owns(skill))input=ClassicTeleporterFinalEffects.startLocal(skill);
        var coin=skill.equals("railgun")?ClassicCoinEffects.attempt():java.util.OptionalLong.empty();
        if(input>0)request("slot_press_token",slot+":"+input);else if(coin.isPresent())request("coin_attempt",Long.toString(coin.getAsLong()));else request("slot_press",Integer.toString(slot));return coin.isPresent()?-1:input;
    }
    private static final java.util.Set<String> TOKEN_TERMINALS=java.util.Set.of("dir_blast","blood_retro","plasma_cannon","vec_accel","jet_engine","electron_missile","penetrate_teleport","mark_teleport","flesh_ripping","shift_tp");
    /** Captured node skill/input choose callbacks; no current-preset identity resolution here. */
    private static void terminal(ClassicClientInputBinding.Node node,long input,boolean abort){
        String skill=node.skill;
        if(ClassicVectorCombatEffects.owns(skill)){if(abort)ClassicVectorCombatEffects.abortLocal(skill);else ClassicVectorCombatEffects.releaseLocal(skill);}
        if(ClassicVectorFinalEffects.owns(skill)){if(abort)ClassicVectorFinalEffects.endLocal(skill);else ClassicVectorFinalEffects.releaseLocal(skill);}
        if(ClassicVectorStarterEffects.owns(skill)){if(abort)ClassicVectorStarterEffects.endLocal(skill);else ClassicVectorStarterEffects.releaseLocal(skill);}
        if(ClassicMeltdownerStarterEffects.owns(skill))ClassicMeltdownerStarterEffects.endLocal(skill);
        if(ClassicMeltdownerBeamEffects.owns(skill))ClassicMeltdownerBeamEffects.endLocal(skill);
        if(ClassicMeltdownerLateEffects.owns(skill)){if(abort)ClassicMeltdownerLateEffects.endLocal(skill);else ClassicMeltdownerLateEffects.releaseLocal(skill);}
        if(ClassicTeleporterProgressionEffects.owns(skill))ClassicTeleporterProgressionEffects.endLocal(skill);
        if(ClassicTeleporterFinalEffects.owns(skill))ClassicTeleporterFinalEffects.endLocal(skill);
        if(skill.equals("ground_shock"))ClassicGroundShockEffects.abortLocal(input);
        if(skill.equals("body_intensify")&&abort)ClassicBodyIntensifyEffects.abortLocal();
        if(skill.equals("thunder_clap"))ClassicThunderClapEffects.abortLocal();
        if(skill.equals("mag_movement")||abort&&skill.equals("mag_manip"))ClassicMagneticEffects.abortLocal(skill,input);
        if(ClassicFirstSkillEffects.ownsSingleKey(skill))ClassicFirstSkillEffects.endLocal(skill,input,abort);
        var legacy=cn.academy.port.core.LegacySingleKeyProtocol.Skill.find(skill);
        if(legacy!=null){if(input>0&&!singleInputRetired(skill,input)&&node.actorId()>=0&&singleOwnerEpochMatches(node.ownerEpoch())){long token=node.acceptedToken();if(token>0)request(abort?cn.academy.port.core.LegacySingleKeyProtocol.ABORT:cn.academy.port.core.LegacySingleKeyProtocol.RELEASE,new cn.academy.port.core.LegacySingleKeyProtocol.AcceptedRequest(legacy,input,node.actorId(),node.ownerEpoch(),token).wire());else request(abort?cn.academy.port.core.LegacySingleKeyProtocol.PENDING_ABORT:cn.academy.port.core.LegacySingleKeyProtocol.PENDING_RELEASE,new cn.academy.port.core.LegacySingleKeyProtocol.PendingRequest(legacy,input,node.actorId(),node.ownerEpoch()).wire());}}
        else if(TOKEN_TERMINALS.contains(skill)){if(input>0)request(abort?"slot_abort_token":"slot_release_token",node.slot+":"+input);}
        else request(abort?"slot_abort":"slot_release",Integer.toString(node.slot));
    }
    private static void cleanupModern(String skill){
        if(ClassicVectorCombatEffects.owns(skill)&&!skill.equals("storm_wing"))ClassicVectorCombatEffects.abortLocal(skill);
        if(skill.equals("plasma_cannon"))ClassicVectorFinalEffects.endLocal(skill);
        if(skill.equals("vec_accel"))ClassicVectorStarterEffects.endLocal(skill);
        if(ClassicMeltdownerStarterEffects.owns(skill))ClassicMeltdownerStarterEffects.endLocal(skill);
        if(ClassicMeltdownerBeamEffects.owns(skill))ClassicMeltdownerBeamEffects.endLocal(skill);
        if(ClassicMeltdownerLateEffects.owns(skill))ClassicMeltdownerLateEffects.endLocal(skill);
        if(ClassicTeleporterProgressionEffects.owns(skill))ClassicTeleporterProgressionEffects.endLocal(skill);
        if(ClassicTeleporterFinalEffects.owns(skill))ClassicTeleporterFinalEffects.endLocal(skill);
        if(skill.equals("ground_shock"))ClassicGroundShockEffects.abortLocal();
        if(skill.equals("body_intensify"))ClassicBodyIntensifyEffects.abortLocal();
        if(skill.equals("thunder_clap"))ClassicThunderClapEffects.abortLocal();
        if(java.util.Set.of("mag_movement","mag_manip").contains(skill))ClassicMagneticEffects.abortLocal(skill);
    }
    /** Snapshot fallback keeps accepted context V ownership/order independent from cosmetic visibility. */
    private static void modernSnapshotFallback(){if(binding!=null)binding.modernSnapshotFallback();mappingSignature=mappings();}
    private static void discardBinding(){singleInputEpoch++;serverSingleEpoch=0;var old=binding;binding=null;if(old!=null)old.discard();overrideKeys=new int[0];}
    private static void clearEffects(){ClassicFirstSkillEffects.clearSingleKeyContexts();ClassicCoinEffects.clear();ClassicGroundShockEffects.clear();ClassicMagneticEffects.clear();ClassicThunderBoltEffects.clear();ClassicMineDetectEffects.clear();ClassicBodyIntensifyEffects.clear();ClassicThunderClapEffects.clear();ClassicMeltdownerStarterEffects.clear();ClassicMeltdownerBeamEffects.clear();ClassicMeltdownerLateEffects.clear();ClassicTeleporterProgressionEffects.clear();ClassicTeleporterFinalEffects.clear();ClassicVectorStarterEffects.clear();ClassicVectorCombatEffects.clear();ClassicVectorFinalEffects.clear();}
    private static void synchronizeSession(Minecraft mc){boolean changed=session.synchronize(mc.level,mc.player)||ownedConnection!=mc.getConnection();if(changed){discardBinding();ownedConnection=mc.getConnection();cn.academy.port.client.achievements.ClassicAchievementClient.clear();cn.academy.port.client.tutorial.TutorialClient.clear();cn.academy.port.client.terminal.TerminalClient.clear();state=new AbilityProgress();activationState.resetSession(state);development=null;clearEffects();activation.replaceSession(down(TOGGLE),now());switchLatch.replaceSession(down(SWITCH_PRESET));editLatch.replaceSession(down(EDIT_PRESET));deathFenced=false;}if(binding==null&&mc.player!=null&&mc.level!=null&&mc.player.isAlive()){binding=createBinding(mc);binding.runtime().updateDefaultGroup();binding.establishSessionFence();mappingSignature=mappings();}}
    public static void editPreset(int preset,int slot,String skill){if(state.presets.edit(preset,slot,skill,id->cn.academy.port.preset.PresetSkills.selectable(state,id))){if(binding!=null)binding.presetEdit();mappingSignature=mappings();request("preset_edit",preset+":"+slot+":"+skill);}}
    /** These hooks are invoked after the existing effect receiver has accepted its local start/end. */
    public static void acceptedContextStart(String skill,long input,long token,boolean active){if(binding!=null)binding.acceptedStart(skill,input,token,active);}
    public static void acceptedContextActive(String skill,long input,long token){if(binding!=null)binding.acceptedActive(skill,input,token);}
    public static boolean acceptedContextEnd(String skill,long input,long token){return binding!=null&&binding.acceptedEnd(skill,input,token);}
    public static boolean contextStartCancelled(String skill,long input,long token){return binding==null||binding.contextStartCancelled(skill,input,token);}
    public static void acceptedSingleStart(String skill,long input,long token){if(binding!=null)binding.acceptedSingleStart(skill,input,token);}
    public static void acceptedSingleEnd(String skill,long input,long token){if(binding!=null)binding.acceptedSingleEnd(skill,input,token);}
    public static boolean singleStartAllowed(String skill,long input,long token){return binding!=null&&binding.singleStartAllowed(skill,input,token);}
    public static boolean singleTerminalAllowed(String skill,long input,long token){return binding!=null&&binding.singleTerminalAllowed(skill,input,token);}
    public static boolean singleInputRetired(String skill,long input){return binding==null||binding.singleInputRetired(skill,input);}
    public static boolean legacySingleStartAllowed(String skill){return binding!=null&&!binding.discarded()&&!binding.hasPositiveSingleOwnership(skill);}
    public static boolean singleTokenConflicts(String skill,long input,long token){return binding==null||binding.singleTokenConflicts(skill,input,token);}
    public static long inputSessionEpoch(){return singleInputEpoch;}
    public static long serverSingleEpoch(){return serverSingleEpoch;}
    public static boolean singleOwnerEpochMatches(long epoch){return epoch>0&&epoch==serverSingleEpoch;}
    public static boolean sameInputSessionEpoch(long epoch){var mc=Minecraft.getInstance();return epoch==singleInputEpoch&&binding!=null&&!binding.discarded()&&mc.level!=null&&mc.player!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&mc.getConnection()==ownedConnection;}
    private static void receiveSingleRejection(net.minecraft.nbt.CompoundTag data){
        if(!data.contains("input",net.minecraft.nbt.Tag.TAG_LONG)||!data.contains("skill",net.minecraft.nbt.Tag.TAG_STRING)||!data.hasUUID("entity_uuid")||!data.contains("entity",net.minecraft.nbt.Tag.TAG_INT)||!data.contains("owner_epoch",net.minecraft.nbt.Tag.TAG_LONG)||!singleOwnerEpochMatches(data.getLong("owner_epoch")))return;
        var identity=cn.academy.port.core.LegacySingleKeyProtocol.Skill.find(data.getString("skill"));long input=data.getLong("input");
        var mc=Minecraft.getInstance();if(identity==null||input<=0||mc.player==null||mc.player.getId()!=data.getInt("entity")||!mc.player.getUUID().equals(data.getUUID("entity_uuid")))return;
        var tag=data.copy();final Object level=mc.level,player=mc.player,connection=mc.getConnection();long epoch=inputSessionEpoch();long ownerEpoch=data.getLong("owner_epoch");
        mc.execute(()->{if(mc.level==level&&mc.player==player&&mc.getConnection()==connection&&sameInputSessionEpoch(epoch)&&singleOwnerEpochMatches(ownerEpoch)&&binding!=null)binding.rejectedSingle(identity.id(),tag.getLong("input"));});
    }
    public static void acceptedRailgunCoin(){if(binding!=null)binding.railgunCoinAccepted();}
    @SubscribeEvent public static void activationObserved(cn.academy.port.api.AbilityActivateEvent event){var mc=Minecraft.getInstance();if(binding!=null&&mc.level!=null&&mc.level.isClientSide&&event.getEntity()==mc.player&&event.state==state)binding.observerActivate();}
    @SubscribeEvent public static void deactivationObserved(cn.academy.port.api.AbilityDeactivateEvent event){var mc=Minecraft.getInstance();if(binding!=null&&mc.level!=null&&mc.level.isClientSide&&event.getEntity()==mc.player&&event.state==state)binding.observerDeactivate();}
    public static void request(String action,String value) {PacketDistributor.sendToServer(new AcademyNetwork.Request(action,value));}
    public static void receive(AcademyNetwork.ClientData packet) {
        var minecraft=Minecraft.getInstance();synchronizeSession(minecraft);var t=packet.data();if(t==null)return;
        switch(t.getString("kind")) {
            case "single_key_rejected" -> receiveSingleRejection(t);
            case "terminal_state","terminal_installed","terminal_app_installed","terminal_install_effect","terminal_open","terminal_app","terminal_frequency" -> cn.academy.port.client.terminal.TerminalClient.receive(t);
            case "tutorial_open" -> cn.academy.port.client.tutorial.TutorialClient.open(t);
            case "tutorial_state","tutorial_activate" -> cn.academy.port.client.tutorial.TutorialClient.receive(t);
            case "classic_achievements" -> cn.academy.port.client.achievements.ClassicAchievementClient.receive(t);
            case "development" -> development=t.copy();
            case "activation_event" -> {if(minecraft.player!=null&&minecraft.level!=null){Boolean active=AcademyNetwork.activationEventState(t,minecraft.player.getId());if(active!=null)cn.academy.port.api.AbilityActivationLifecycle.post(minecraft.player,state,active);}}
            case "state" -> {long epoch=t.contains("single_key_owner_epoch",net.minecraft.nbt.Tag.TAG_LONG)?t.getLong("single_key_owner_epoch"):0;if(epoch>0){if(serverSingleEpoch>0&&epoch<serverSingleEpoch)return;if(serverSingleEpoch>0&&epoch!=serverSingleEpoch&&binding!=null)binding.retireSingleOwnerEpoch();serverSingleEpoch=epoch;}if(minecraft.getSingleplayerServer()==null)SkillCatalog.applyConfigurationSnapshot(t.getCompound("skill_configuration"));activationState.receive(state,t,next->state=next,AcademyClient::modernSnapshotFallback);}
            case "developer" -> minecraft.setScreen(new DeveloperScreen());
            case "developer_machine" -> minecraft.setScreen(new DeveloperScreen(t));
            case "developer_machine_update" -> {if(minecraft.screen instanceof DeveloperScreen screen)screen.machineUpdate(t);}
            case "developer_machine_close" -> {if(minecraft.screen instanceof DeveloperScreen screen&&screen.machineSession(t.getString("session")))minecraft.setScreen(null);}
            case "charge" -> ClassicEffects.addCharge(t.getInt("entity"));
            case "thunder_bolt" -> ClassicThunderBoltEffects.receive(t);
            case "mine_detect" -> ClassicMineDetectEffects.receive(t);
            case "body_intensify_start","body_intensify_end" -> ClassicBodyIntensifyEffects.receive(t);
            case "thunder_clap_start","thunder_clap_end" -> ClassicThunderClapEffects.receive(t);
            case "scatter_bomb_start","scatter_bomb_ball","scatter_bomb_ray","scatter_bomb_end","light_shield_start","light_shield_end" -> ClassicMeltdownerStarterEffects.receive(t);
            case "meltdowner_start","meltdowner_end","meltdowner_ray","meltdowner_reflection","mine_ray_basic_start","mine_ray_basic_end","mine_ray_basic_particles","mine_ray_expert_start","mine_ray_expert_end","mine_ray_expert_particles","mine_ray_luck_start","mine_ray_luck_end","mine_ray_luck_particles" -> ClassicMeltdownerBeamEffects.receive(t);
            case "ray_barrage_start","ray_barrage_scatter","ray_barrage_preray","ray_barrage_end","jet_engine_start","jet_engine_mark_end","jet_engine_trigger","jet_engine_step","jet_engine_end","electron_missile_start","electron_missile_ball","electron_missile_ray","electron_missile_update","electron_missile_end" -> ClassicMeltdownerLateEffects.receive(t);
            case "mag_movement_start","mag_movement_update","mag_movement_end","mag_manip_start","mag_manip_end","mag_manip_perform" -> ClassicMagneticEffects.receive(t);
            case "charging_start","charging_end" -> ClassicChargingEffects.receive(t);
            case "dir_blast_prepare","dir_blast_abort","dir_blast_perform","storm_wing_start","storm_wing_state","storm_wing_end","blood_retro_start","blood_retro_end","blood_retro_perform" -> ClassicVectorCombatEffects.receive(t);
            case "vec_reflection_start","vec_reflection_end","vec_reflection_entity","vec_reflection_wave","plasma_cannon_start","plasma_cannon_end","plasma_cannon_go","plasma_cannon_position","plasma_cannon_ready" -> ClassicVectorFinalEffects.receive(t);
            case "vec_accel_start","vec_accel_end","vec_accel_perform","vec_deviation_start","vec_deviation_end","vec_deviation_stop","vec_deviation_sound" -> ClassicVectorStarterEffects.receive(t);
            case "ground_shock_start","ground_shock_abort","ground_shock" -> ClassicGroundShockEffects.receive(t);
            case "location_teleport_list","location_teleport_end","shift_tp_start","shift_tp_end","shift_tp_placed","shift_tp_critical","flashing_start","flashing_perform","flashing_end" -> ClassicTeleporterFinalEffects.receive(t);
            case "penetrate_teleport_start","penetrate_teleport_end","mark_teleport_start","mark_teleport_end","flesh_ripping_start","flesh_ripping_end" -> ClassicTeleporterProgressionEffects.receive(t);
            case "radiation_mark","dir_shock_prepare","dir_shock_abort","dir_shock","electron_bomb_charge","electron_bomb","threatening_teleport_start","threatening_teleport_abort","threatening_teleport" -> ClassicFirstSkillEffects.receive(t);
            case "coin_toss","coin_end" -> ClassicCoinEffects.receive(t);
            case "railgun","arc" -> {
                var origin=new Vec3(t.getDouble("x"),t.getDouble("y"),t.getDouble("z"));var direction=new Vec3(t.getDouble("dx"),t.getDouble("dy"),t.getDouble("dz"));
                boolean rail=t.getString("kind").equals("railgun");if(rail)ClassicEffects.addRailgun(origin,direction,t.getDouble("length"));else ClassicEffects.addArc(origin,direction,t.getDouble("length"));
                if(minecraft.level!=null)minecraft.level.playLocalSound(origin.x,origin.y,origin.z,SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy",rail?"em.railgun":"em.arc_weak")),SoundSource.PLAYERS,.5f,1,false);
            }
            default -> {}
        }
    }
    /** Sole ability-key poll/dispatch authority, at source START. Auxiliary V/C/N timing stays separate. */
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        var mc=Minecraft.getInstance();synchronizeSession(mc);if(mc.player==null||mc.level==null)return;
        if(!mc.player.isAlive()){if(!deathFenced){discardBinding();clearEffects();activation.replaceSession(down(TOGGLE),now());switchLatch.replaceSession(down(SWITCH_PRESET));editLatch.replaceSession(down(EDIT_PRESET));deathFenced=true;}return;}
        deathFenced=false;state.tickCooldowns();if(binding==null)return;
        String current=mappings();if(!current.equals(mappingSignature)){mappingSignature=current;binding.requestFlush();}
        binding.tickStart();for(var key:SLOTS)while(key.consumeClick()){};
        var switchTransition=switchLatch.update(down(SWITCH_PRESET),mc.screen==null&&state.activated);
        var editTransition=editLatch.update(down(EDIT_PRESET),mc.screen==null&&state.hasCategory());
        while(SWITCH_PRESET.consumeClick()){};while(EDIT_PRESET.consumeClick()){};while(TOGGLE.consumeClick()){};
        boolean activate=activation.update(down(TOGGLE),mc.screen==null,now());if(mc.screen!=null)return;
        if(activate&&state.hasCategory()){binding.activateKeyDown();return;}
        if(editTransition.press()){binding.presetEdit();mc.setScreen(new PresetEditScreen());return;}
        if(switchTransition.press()){int next=(state.presets.current()+1)%cn.academy.port.core.SkillPresets.MAX_PRESETS;state.presets.switchTo(next);binding.presetSwitch();mappingSignature=mappings();request("preset_switch",Integer.toString(next));}
    }
    /** Modern END adapter runs after all ordinary effect/context Post ticks. */
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void finishContextDisposals(ClientTickEvent.Post event){if(binding!=null)binding.finishLocalDisposals();}
    private static boolean overrides(KeyMapping vanilla){int key=sourceKey(vanilla.getKey());for(int own:overrideKeys)if(own==key)return true;return false;}
    /** Source ControlOverrider suppresses vanilla mappings for bound delegates, even on cooldown. */
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOW) public static void suppressVanillaKeys(ClientTickEvent.Pre event){
        var mc=Minecraft.getInstance();synchronizeSession(mc);if(mc.player==null||mc.screen!=null)return;
        for(var key:mc.options.keyMappings){
            boolean own=key==TOGGLE||key==SWITCH_PRESET||key==EDIT_PRESET;for(var slot:SLOTS)own|=key==slot;
            if(!own&&overrides(key)){key.setDown(false);while(key.consumeClick()){};}
        }
    }
    /** Callback-level mouse suppression also blocks vanilla before its click queue is populated. */
    @SubscribeEvent public static void suppressVanillaMouse(InputEvent.MouseButton.Pre event){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null||!state.activated)return;
        int source=ClassicPhysicalKeys.mouse(event.getButton());for(int key:overrideKeys)if(key==source){event.setCanceled(true);return;}
    }
    @SubscribeEvent public static void suppressVanillaInteraction(InputEvent.InteractionKeyMappingTriggered event){
        if(Minecraft.getInstance().screen==null&&overrides(event.getKeyMapping())){event.setCanceled(true);event.setSwingHand(false);}
    }
    // Replace AcademyClient.hud's placeholder implementation after merging the presets bridge.
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(mc.player==null)return;
        var keys=new java.util.ArrayList<ClassicAbilityHud.KeyHint>();
        for(int slot=0;slot<SLOTS.length;slot++){
            if(!delegatePresent(slot))continue;
            String skill=state.presets.currentSkill(slot);
            var key=SLOTS[slot].getKey();
            int mouse=key.getType()==com.mojang.blaze3d.platform.InputConstants.Type.MOUSE?key.getValue():-1;
            var visual=delegateActive(slot)?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.IDLE;
            if(skill.equals("railgun")){
                if(ClassicCoinEffects.hasPendingAttempt())visual=ClassicCoinEffects.ready()?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.CHARGE;
                else visual=binding!=null&&binding.railgunTicks(slot)!=-1?ClassicAbilityHud.DelegateVisual.CHARGE:ClassicAbilityHud.DelegateVisual.IDLE;
            }
            if(skill.equals("ground_shock")){
                int preparing=ClassicGroundShockEffects.localPrepareTicks();
                visual=preparing<0?ClassicAbilityHud.DelegateVisual.IDLE:preparing<5?ClassicAbilityHud.DelegateVisual.CHARGE:ClassicAbilityHud.DelegateVisual.ACTIVE;
            }
            if(ClassicMeltdownerStarterEffects.owns(skill))visual=ClassicMeltdownerStarterEffects.delegateActive(skill)?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.IDLE;
            if(ClassicMeltdownerBeamEffects.owns(skill))visual=ClassicMeltdownerBeamEffects.delegateActive(skill)?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.IDLE;
            if(ClassicVectorStarterEffects.owns(skill))visual=ClassicVectorStarterEffects.active(skill)?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.IDLE;
            if(ClassicVectorCombatEffects.owns(skill))visual=ClassicVectorCombatEffects.active(skill)?(skill.equals("storm_wing")&&!ClassicVectorCombatEffects.stormActive()?ClassicAbilityHud.DelegateVisual.CHARGE:ClassicAbilityHud.DelegateVisual.ACTIVE):ClassicAbilityHud.DelegateVisual.IDLE;
            if(ClassicVectorFinalEffects.owns(skill))visual=ClassicVectorFinalEffects.charging()&&skill.equals("plasma_cannon")?ClassicAbilityHud.DelegateVisual.CHARGE:ClassicVectorFinalEffects.active(skill)?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.IDLE;
            keys.add(new ClassicAbilityHud.KeyHint("def",slot,skill,SLOTS[slot].getTranslatedKeyMessage(),mouse,
                    visual,state.cooldowns.getOrDefault(skill,0),state.cooldownMaximum(skill)));
        }
        if(binding!=null)for(var node:binding.winningNodes())if(node.kind==ClassicClientInputBinding.Kind.DIRECTION){String letter=node.key==ClassicPhysicalKeys.W?"W":node.key==ClassicPhysicalKeys.A?"A":node.key==ClassicPhysicalKeys.S?"S":"D";keys.add(new ClassicAbilityHud.KeyHint(node.group,node.direction,node.group.equals(ClassicClientInputBinding.FLASH_GROUP)?letter.toLowerCase(java.util.Locale.ROOT):node.skill,net.minecraft.network.chat.Component.literal(letter),-1,node.context.applying()==node.direction?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.IDLE,0,1));}
        String hint=binding==null?"":binding.runtime().activationHintTranslated(TOGGLE.getTranslatedKeyMessage().getString(),key->net.minecraft.network.chat.Component.translatable(key).getString()).orElse("");
        // Source GroundShock context provides captured predicted CP cost.
        ClassicAbilityHud.render(event.getGuiGraphics(),state,new ClassicAbilityHud.Inputs(
                activationHeld(),ClassicGroundShockEffects.consumptionHint()+ClassicMagneticEffects.consumptionHint()+ClassicTeleporterFinalEffects.consumptionHint()+ClassicVectorStarterEffects.consumptionHint()+ClassicVectorCombatEffects.consumptionHint()+ClassicVectorFinalEffects.consumptionHint(),state.presets.current(),hint,keys));
    }

    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {AcademyNetwork.clientReceiver=AcademyClient::receive;event.enqueueWork(()->{
            ClassicEnergyUnitProperties.register();
            net.minecraft.client.renderer.item.ItemProperties.register(AcademyCraft.DEVELOPER.get(),ResourceLocation.fromNamespaceAndPath("academy","charge_icon"),(stack,level,entity,seed)->{int damage=(int)Math.round(13*(1-new cn.academy.port.develop.DeveloperItemEnergy(stack,cn.academy.port.develop.DeveloperType.PORTABLE).energy()/10000));return damage<3?2f:damage>10?0f:1f;});
            net.minecraft.client.renderer.item.ItemProperties.register(AcademyCraft.INDUCTION_FACTOR.get(),ResourceLocation.fromNamespaceAndPath("academy","category"),(stack,level,entity,seed)->cn.academy.port.develop.InductionFactors.category(stack).map(cat->(float)cn.academy.port.develop.DevelopmentActions.CATEGORIES.indexOf(cat)).orElse(0f));
        });}
        @SubscribeEvent public static void developerRenderers(EntityRenderersEvent.RegisterRenderers event){ClassicDeveloperRendering.registerRenderers(event);}
        @SubscribeEvent public static void developerModels(RegisterClientReloadListenersEvent event){ClassicDeveloperRendering.registerReloadListeners(event);}
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {cn.academy.port.client.terminal.TerminalClient.registerKeys(event);event.register(TOGGLE);event.register(SWITCH_PRESET);event.register(EDIT_PRESET);for(var key:SLOTS)event.register(key);}
    }
}
