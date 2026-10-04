/* Faithful AcademyCraft 1.0.7 DeveloperUI/Common.TreeScreen adapter. GPLv3; see NOTICE. */
package cn.academy.port.client;

import cn.academy.port.AcademyCraft;
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.develop.DeveloperItemEnergy;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.develop.InductionFactors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Real finite developer UI, never a client-side ability or energy authority. */
public class ClassicDeveloperScreen extends Screen {
    private static final Set<String> LEARNABLE=cn.academy.port.SkillAvailability.LEARNABLE;
    private static final ResourceLocation BACK=tex("guis/developer/skill_back"),OUTLINE=tex("guis/developer/skill_outline"),MASK=tex("guis/developer/skill_radial_mask"),LINE=tex("guis/developer/line");
    private static final ClassicHudTimeline.Rgba WHITE=rgba(1,1,1,1),RED=rgba(1,.333,.333,1),CYAN=rgba(161/255d,225/255d,1,1);
    private final ClassicHudFont classicFont=new ClassicHudFont();
    private final boolean skillTreeApp;
    private final Map<String,Hover> hovers=new HashMap<>();
    private String machineToken="",structureSignature="",consoleMode="",lastStatus="";
    private DeveloperType developerType=DeveloperType.PORTABLE;
    private double machineEnergy;
    private BlockPos machineOrigin;
    private String machineNodeName="N/A";
    private boolean released;
    private long created=now(),sentAt,consoleRebuildAt=-1;
    private ClassicDeveloperConsole console;
    private Cover cover;
    private String sentAction="";
    private CompoundTag previousDevelopment;
    private static final class Hover {boolean over;long changed;Hover(long now){changed=now-100;}}
    private static final class Cover {
        final ClassicDeveloperLayout.Node node;final String kind;long changed;boolean ending,requested,received,complete,buttonDisposed;final boolean learned;String message="";double progress;
        Cover(String kind,ClassicDeveloperLayout.Node node){this.kind=kind;this.node=node;this.learned=node!=null&&AcademyClient.state.learned(node.id());changed=now();}
    }
    public ClassicDeveloperScreen(){this(false);}
    /** Classic SkillTreeAppUI initializes Common with developer=null: no machine, learning or level actions. */
    public ClassicDeveloperScreen(boolean appView){super(Component.translatable(appView?"ac.app.skill_tree.name":"screen.academy.developer"));skillTreeApp=appView;}
    public ClassicDeveloperScreen(CompoundTag machine){this();machineToken=machine.getString("session");machineOrigin=BlockPos.of(machine.getLong("origin"));machineNodeName=machine.getString("node_name").isEmpty()?"N/A":machine.getString("node_name");try{developerType=DeveloperType.valueOf(machine.getString("type"));}catch(IllegalArgumentException ignored){}machineEnergy=finiteEnergy(machine.getDouble("energy"));}
    public boolean machineSession(String token){return !machineToken.isEmpty()&&machineToken.equals(token);}
    public void machineUpdate(CompoundTag data){if(machineSession(data.getString("session"))){machineEnergy=finiteEnergy(data.getDouble("energy"));machineNodeName=data.getString("node_name").isEmpty()?"N/A":data.getString("node_name");}}
    private double finiteEnergy(double value){return Double.isFinite(value)?Math.max(0,Math.min(developerType.energy,value)):0;}
    private double energy(){
        if(!machineToken.isEmpty())return machineEnergy;
        if(minecraft==null||minecraft.player==null||!minecraft.player.getMainHandItem().is(AcademyCraft.DEVELOPER.get()))return 0;
        return new DeveloperItemEnergy(minecraft.player.getMainHandItem(),DeveloperType.PORTABLE).energy();
    }
    private boolean resetMode(){return minecraft!=null&&minecraft.player!=null&&AcademyClient.state.hasCategory()&&minecraft.player.getMainHandItem().is(AcademyCraft.MAGNETIC_COIL.get());}
    private boolean developing(){return AcademyClient.development!=null&&AcademyClient.development.getString("state").equals("DEVELOPING");}
    private String signature(){var state=AcademyClient.state;return state.category+":"+state.level+":"+state.experience.keySet()+":"+resetMode();}
    @Override protected void init(){
        ClassicDeveloperTextures.prepare();
        classicFont.use(ClassicHudConfig.SPEC.isLoaded()?ClassicHudConfig.FONT.get():"Microsoft YaHei");
        if(structureSignature.isEmpty())rebuild();
    }
    private void rebuild(){
        structureSignature=signature();created=now();hovers.clear();
        String next=!AcademyClient.state.hasCategory()?"acquire":resetMode()?"reset":"tree";
        if(!next.equals(consoleMode)){consoleMode=next;console=null;sentAction="";}
        if(!next.equals("tree")&&console==null){
            String startup=next.equals("reset")?local("console.override"):local("console.invalid_cat")+(skillTreeApp?"":local("console.learn_hint"));
            console=new ClassicDeveloperConsole(local("console.init",minecraft.player==null?"N/A":minecraft.player.getName().getString()),local("console.boot_failed"),startup,created);
        }
    }
    @Override public void tick(){
        super.tick();long time=now();if(console!=null)console.tick(time);
        CompoundTag dev=AcademyClient.development;
        if(dev!=null&&!dev.equals(previousDevelopment)){previousDevelopment=dev.copy();acceptDevelopment(dev);}
        if(consoleRebuildAt>=0&&time>=consoleRebuildAt){consoleRebuildAt=-1;console=null;consoleMode="";rebuild();}
        if(!structureSignature.equals(signature())&&cover==null&&consoleRebuildAt<0&&(console==null||!console.busy())){rebuild();}
        if(cover!=null&&cover.ending&&time-cover.changed>=200){boolean refresh=cover.complete;cover=null;if(refresh)rebuild();}
        // An invalid server session/action has no start snapshot. Do not strand the original cover forever.
        if(cover!=null&&cover.requested&&!cover.received&&time-sentAt>3000){cover.requested=false;cover.message=translated("screen.academy.developer_server_rejected");}
        if(console!=null&&console.busy()&&!sentAction.isEmpty()&&time-sentAt>3000&&!developing()&&lastStatus.equals("waiting")){
            console.output("\n"+translated("screen.academy.developer_server_rejected")+"\n");console.busy(false);sentAction="";
        }
    }
    private void acceptDevelopment(CompoundTag dev){
        String status=dev.getString("state"),action=dev.getString("action");double progress=ClassicDeveloperTimeline.clamp(dev.getDouble("progress"));
        if(cover!=null&&cover.requested){
            if(status.equals("DEVELOPING")&&expectedAction(cover).equals(action))cover.received=true;
            if(cover.received){cover.progress=progress;cover.message=status.equals("DEVELOPING")?local("progress")+String.format(Locale.ROOT," %.0f%%",progress*100):status.equals("DONE")?local("dev_successful"):status.equals("FAILED")?local("dev_failed"):cover.message;
                if(status.equals("DONE")||status.equals("FAILED")){cover.requested=false;cover.complete=status.equals("DONE");cover.progress=cover.complete?1:progress;}}
        }
        if(console!=null&&!sentAction.isEmpty()){
            if(status.equals("DEVELOPING")&&sentAction.equals(action)){lastStatus="developing";console.replaceProgress(local("console.progress",String.format(Locale.ROOT,"%02d",(int)(progress*100))));}
            else if(lastStatus.equals("developing")&&(status.equals("DONE")||status.equals("FAILED"))){
                boolean reset=sentAction.equals("reset");console.output("\n"+local("console."+(reset?"reset_":"dev_")+(status.equals("DONE")?"succ":"fail")));console.busy(false);sentAction="";consoleRebuildAt=now()+500;
            }
        }
    }
    private static String expectedAction(Cover cover){return cover.kind.equals("skill")?"skill:"+cover.node.id():"level";}
    private void request(String action,String value){
        if(machineToken.isEmpty())AcademyClient.request(action,value);
        else AcademyClient.request(switch(action){case "develop_level"->"machine_level";case "learn"->"machine_learn";case "develop_reset"->"machine_reset";default->action;},machineToken+(value.isEmpty()?"":":"+value));
    }
    @Override public void removed(){super.removed();classicFont.release();if(!machineToken.isEmpty()&&!released){released=true;AcademyClient.request("machine_close",machineToken);}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
        // The source TreeScreen is non-pausing and draws a dim game background, without menu blur.
        graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);graphics.flush();
        ClassicHudCanvas canvas=new ClassicHudCanvas(graphics);canvas.begin();
        try{
            var placement=ClassicDeveloperTimeline.placement(width,height);graphics.pose().pushPose();
            try{
                graphics.pose().translate(placement.x(),placement.y(),0);graphics.pose().scale((float)placement.scale(),(float)placement.scale(),1);
                drawMain(canvas,placement.localX(mouseX),placement.localY(mouseY),mouseX,mouseY);
            }finally{graphics.pose().popPose();}
            if(cover!=null)drawCover(canvas,mouseX,mouseY);
        }finally{canvas.end();}
    }
    private void drawMain(ClassicHudCanvas canvas,double mx,double my,int screenMouseX,int screenMouseY){
        canvas.rect(tex("guis/parent/parent_background_developerright"),118,0,278,187,WHITE);
        canvas.rect(tex("guis/parent/parent_background_developerleft"),4,0,108.5,187,WHITE);
        if(console==null){
            double dx=ClassicDeveloperTimeline.parallax(screenMouseX,width),dy=ClassicDeveloperTimeline.parallax(screenMouseY,height);
            double u,v;
            // SkillTree.scala centers UV at .5 and adds 1/1.01 across the original area.
            u=(dx/10*.01-.5)/1.01+.5;v=(dy/10*.01-.5)/1.01+.5;
            canvas.rect(tex("guis/effect/effect_developer_background"),128,18,257,139,u,v,u+1/1.01,v+1/1.01,WHITE,null,null);
            drawTree(canvas,mx,my,dx,dy);
        }else{
            int line=0;for(String text:console.visibleLines(now()))text(canvas,text,133,23+10*(line++),8,WHITE,false,0);
        }
        canvas.rect(tex("guis/ui/ui_developerright"),118,0,278,187,WHITE);
        canvas.rect(tex(skillTreeApp?"guis/ui/ui_developerleft_skilltree":"guis/ui/ui_developerleft"),4,0,108.5,187,WHITE);
        drawAbility(canvas,mx,my);if(!skillTreeApp)drawMachine(canvas,mx,my);
    }
    private void drawAbility(ClassicHudCanvas canvas,double mx,double my){
        var state=AcademyClient.state;String category=state.hasCategory()?state.category:"nocategory";
        canvas.rect(tex("guis/icons/icon_"+category),6,67.5,32,32,WHITE);
        text(canvas,state.hasCategory()?translated("ac.ability."+category+".name"):"N/A",37,69.5,13,WHITE,false,0);
        double progress=state.levelProgress(SkillCatalog.levelSkillCount(state));if(!state.hasCategory())progress=0;
        canvas.fill(37,80.75,70,1.5,rgba(.4,.4,.4,.3),false);canvas.fill(37,80.75,70*(state.hasCategory()?Math.max(.02,progress):0),1.5,WHITE,false);
        text(canvas,"EXP "+(int)(progress*100)+"%",36,83,8,WHITE,false,0);
        if(!skillTreeApp&&state.hasCategory()&&state.canLevelUp(SkillCatalog.levelSkillCount(state))){
            boolean over=ClassicDeveloperTimeline.hit(mx,my,66,82,48.363636,15.476364);
            canvas.rect(tex("guis/button/button_learn"),66,82,48.363636,15.476364,rgba(1,1,1,over?1:.7));
        }else text(canvas,levelName(state.level),106.421875,83.5,9,rgba(17/255d,119/255d,214/255d,1),false,2);
    }
    private void drawMachine(ClassicHudCanvas canvas,double mx,double my){
        canvas.rect(tex("guis/parent/parent_background_developermachine"),4,0,108.5,187,WHITE);
        if(!machineToken.isEmpty()){
            text(canvas,"Current Node:",8.25,104.5,12,WHITE,false,0);
            canvas.rect(tex("guis/element/element_background300x32"),8.25,114.5,100,16,rgba(1,1,1,.45));
            canvas.rect(tex("guis/icons/icon_node"),15.25,116.5,12,12,rgba(1,1,1,.45));text(canvas,machineNodeName,34.25,116.5,12,rgba(1,1,1,.55),false,0);
        }
        text(canvas,"Power:",8.25,130.5,12,WHITE,false,0);text(canvas,"Sync Rate:",8.25,153.5,12,WHITE,false,0);
        canvas.fill(9.75,145,97*(energy()/developerType.energy),8,rgba(252/255d,197/255d,50/255d,1),false);
        canvas.fill(9.75,167,97*developerType.syncRate,8,rgba(50/255d,164/255d,252/255d,1),false);
        if(ClassicDeveloperTimeline.hit(mx,my,8.25,130.5,100,22))text(canvas,String.format(Locale.ROOT,"%.0f / %.0f IF",energy(),developerType.energy),8.25,179,7,WHITE,false,0);
        if(ClassicDeveloperTimeline.hit(mx,my,8.25,153.5,100,23))text(canvas,(int)(developerType.syncRate*100)+"%  "+local("type_"+developerType.name().toLowerCase(Locale.ROOT)),8.25,179,7,WHITE,false,0);
    }
    private List<ClassicDeveloperLayout.Node> visibleNodes(){var state=AcademyClient.state;return ClassicDeveloperLayout.category(state.category).stream().filter(n->n.enabled()&&(state.level>=n.level()||state.learned(n.id())||n.parent().isEmpty()||state.learned(n.parent()))).toList();}
    private void drawTree(ClassicHudCanvas canvas,double mx,double my,double dx,double dy){
        var state=AcademyClient.state;var nodes=visibleNodes();var category=ClassicDeveloperLayout.category(state.category);long elapsed=now()-created;
        for(int index=0;index<nodes.size();index++){
            var node=nodes.get(index);var parent=category.stream().filter(n->n.id().equals(node.parent())).findFirst();if(parent.isEmpty())continue;
            var reveal=ClassicDeveloperTimeline.reveal(elapsed,index);double alpha=ClassicDeveloperTimeline.parentAlpha(state.learned(node.id()),state.learned(node.parent()))*(state.learned(node.id())?1:.4);
            var edge=ClassicDeveloperTimeline.edge(128+node.x()-dx,18+node.y()-dy,128+parent.get().x()-dx,18+parent.get().y()-dy,reveal.line());
            canvas.quad(LINE,new double[]{edge.x0()-edge.nx(),edge.y0()-edge.ny(),edge.x0()+edge.nx(),edge.y0()+edge.ny(),edge.x1()+edge.nx(),edge.y1()+edge.ny(),edge.x1()-edge.nx(),edge.y1()-edge.ny()},new double[]{0,0,0,1,1,1,1,0},rgba(1,1,1,alpha),null,null);
        }
        for(int index=0;index<nodes.size();index++){
            var node=nodes.get(index);double x=128+node.x()-dx,y=18+node.y()-dy;boolean learned=state.learned(node.id()),over=cover==null&&ClassicDeveloperTimeline.hit(mx,my,x,y,16,16);
            Hover hover=hovers.computeIfAbsent(node.id(),id->new Hover(now()));if(now()-hover.changed>=100&&hover.over!=over){hover.over=over;hover.changed=now();}
            double scale=ClassicDeveloperTimeline.hoverScale(hover.over,now()-hover.changed);double alpha=ClassicDeveloperTimeline.parentAlpha(learned,node.parent().isEmpty()||state.learned(node.parent()));var reveal=ClassicDeveloperTimeline.reveal(elapsed,index);
            double bx=x+8-11.5*scale,by=y+8-11.5*scale;
            canvas.rect(BACK,bx,by,23*scale,23*scale,rgba(1,1,1,alpha*reveal.background()));
            canvas.rect(OUTLINE,x+8-15.5*scale,y+8-15.5*scale,31*scale,31*scale,rgba(.2,.2,.2,alpha*reveal.background()*.6));
            var shader=ClassicDeveloperShaders.icon;if(shader!=null)shader.safeGetUniform("Monochrome").set(learned?0f:1f);
            canvas.rect(resource(node.hintIcon()),x+8-7*scale,y+8-7*scale,14*scale,14*scale,0,0,1,1,rgba(1,1,1,alpha*reveal.icon()),shader!=null?shader:learned?null:ClassicHudShaders.mono,shader!=null?BACK:null);
            if(learned)radial(canvas,OUTLINE,x+8-15.5*scale,y+8-15.5*scale,31*scale,reveal.radial()*state.exp(node.id()),WHITE);
        }
    }
    private void radial(ClassicHudCanvas canvas,ResourceLocation texture,double x,double y,double size,double progress,ClassicHudTimeline.Rgba color){
        var shader=ClassicDeveloperShaders.radial;if(shader!=null){shader.safeGetUniform("Progress").set((float)ClassicDeveloperTimeline.clamp(progress));canvas.rect(texture,x,y,size,size,0,0,1,1,color,shader,MASK);}
        else if(progress>=1)canvas.rect(texture,x,y,size,size,color);
    }
    private void drawCover(ClassicHudCanvas canvas,int mx,int my){
        Cover current=cover;double alpha=ClassicDeveloperTimeline.cover(now()-current.changed,current.ending);
        canvas.fill(0,0,width,height,rgba(0,0,0,alpha),false);double cx=width/2d,cy=height/2d;
        if(current.kind.equals("wireless")){
            canvas.rect(tex("guis/icons/icon_node"),cx-16,cy-38,32,32,rgba(1,1,1,.45));
            text(canvas,"N/A",cx,cy,12,WHITE,false,1);
            separated(canvas,translated("screen.academy.developer_wireless_missing"),cx,cy+16,200,9,rgba(1,1,1,.7));return;
        }
        ResourceLocation icon=current.kind.equals("skill")?resource(current.node.hintIcon()):tex("abilities/condition/any"+(AcademyClient.state.level+1));
        canvas.rect(BACK,cx-25,cy-25,50,50,WHITE);canvas.rect(icon,cx-13.5,cy-13.5,27,27,WHITE);
        radial(canvas,tex("guis/developer/skill_view_outline"+(current.progress==1?"_glow":"")),cx-25,cy-25,50,current.progress,WHITE);
        if(current.kind.equals("level")){
            text(canvas,local("uplevel",levelName(AcademyClient.state.level+1)),cx,cy+23,12,WHITE,false,1);
            text(canvas,local("req")+" "+Math.round(estimated(current)),cx,cy+36,9,WHITE,false,1);
            text(canvas,current.message.isEmpty()?local("level_question"):current.message,cx,cy+46,9,WHITE,false,1);
            if(!current.requested&&!current.complete&&!current.buttonDisposed)drawConfirm(canvas,cx-16,cy+60,mx,my);return;
        }
        var node=current.node;boolean learned=current.learned;
        text(canvas,translated(node.nameKey())+(learned?"":" (LV "+node.level()+")"),cx,cy+23,12,WHITE,true,1);
        if(learned&&!current.requested&&!current.complete){
            text(canvas,local("skill_exp")+ClassicDeveloperTimeline.experiencePercent(AcademyClient.state.exp(node.id()))+"%",cx,cy+35,8,CYAN,false,1);
            separated(canvas,translated(node.descriptionKey()),cx,cy+44,200,9,WHITE);
        }else{
            if(!current.complete)text(canvas,local("skill_not_learned"),cx,cy+35,10,RED,false,1);
            if(skillTreeApp)return;
            drawConditions(canvas,node,cx,cy+45,mx,my);
            String message=current.message.isEmpty()?LEARNABLE.contains(node.id())?local("learn_question",Long.toString(Math.round(estimated(current)))):translated("screen.academy.developer_skill_unported"):current.message;
            separated(canvas,message,cx,cy+60,260,10,rgba(1,1,1,.67));
            if(LEARNABLE.contains(node.id())&&!current.requested&&!current.complete&&!current.buttonDisposed)drawConfirm(canvas,cx-16,cy+75,mx,my);
        }
    }
    private void drawConfirm(ClassicHudCanvas canvas,double x,double y,int mx,int my){boolean over=ClassicDeveloperTimeline.hit(mx,my,x,y,32,16);canvas.rect(tex("guis/developer/button"),x,y,32,16,rgba(1,1,1,over?1:.6));}
    private double estimated(Cover current){return developerType.estimatedConsumption(current.kind.equals("skill")?ClassicRules.learningStimulations(current.node.level()):5*(AcademyClient.state.level+1));}
    private record Condition(ResourceLocation icon,String hint,boolean accepted){}
    private List<Condition> conditions(ClassicDeveloperLayout.Node node){
        var state=AcademyClient.state;var result=new java.util.ArrayList<Condition>();
        for(var condition:node.conditions()){
            if(!condition.get("shouldDisplay").getAsBoolean())continue;
            String kind=condition.get("type").getAsString(),hint;boolean accepted;
            if(kind.equals("developer_type")){
                var minimum=DeveloperType.valueOf(condition.get("minimum").getAsString().toUpperCase(Locale.ROOT));
                hint=translated(condition.get("hintKey").getAsString());accepted=developerType.ordinal()>=minimum.ordinal();
            }else if(kind.equals("skill_dependency")){
                String id=condition.get("id").getAsString();double experience=condition.get("minimum_exp").getAsDouble();
                hint=translated(condition.get("hintNameKey").getAsString())+condition.get("hintSuffix").getAsString();accepted=state.learned(id)&&(float)state.exp(id)>=(float)experience;
            }else if(kind.equals("any_skill_of_level")){
                int level=condition.get("level").getAsInt();hint=local("anyskill",level);accepted=SkillCatalog.ALL.stream().anyMatch(s->s.category().equals(state.category)&&s.level()==level&&state.learned(s.id()));
            }else continue;
            result.add(new Condition(resource(condition.get("icon").getAsString()),hint,accepted));
        }
        return List.copyOf(result);
    }
    private void drawConditions(ClassicHudCanvas canvas,ClassicDeveloperLayout.Node node,double cx,double y,int mx,int my){
        var conditions=conditions(node);int length=conditions.size()*16;double x=cx-length/2d;
        text(canvas,local("req"),x-2,y+3,9,rgba(1,1,1,.67),false,2);
        for(int index=0;index<conditions.size();index++){
            var condition=conditions.get(index);double xx=x+index*16;canvas.rect(condition.icon(),xx,y,14,14,0,0,1,1,WHITE,condition.accepted()?null:ClassicHudShaders.mono,null);
            if(ClassicDeveloperTimeline.hit(mx,my,xx,y,14,14))text(canvas,"("+condition.hint()+")",cx+length/2d+3,y+2,9,condition.accepted()?rgba(1,1,1,.93):rgba(238/255d,88/255d,88/255d,1),false,0);
        }
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0)return super.mouseClicked(mx,my,button);
        if(cover!=null){
            if(cover.ending)return true;
            double cx=width/2d,cy=height/2d;Cover current=cover;
            boolean learned=current.learned;
            double by=cy+(current.kind.equals("level")?60:75);
            if(!current.kind.equals("wireless")&&!skillTreeApp&&!current.requested&&!current.complete&&!current.buttonDisposed&&!learned&&(current.node==null||LEARNABLE.contains(current.node.id()))&&ClassicDeveloperTimeline.hit(mx,my,cx-16,by,32,16)){startCover();return true;}
            if(!current.requested){current.ending=true;current.changed=now();}return true;
        }
        var placement=ClassicDeveloperTimeline.placement(width,height);double x=placement.localX(mx),y=placement.localY(my);
        if(!machineToken.isEmpty()&&ClassicDeveloperTimeline.hit(x,y,8.25,114.5,100,16)){ClassicWirelessClient.openMachine(machineOrigin,machineToken);return true;}
        var state=AcademyClient.state;
        if(!skillTreeApp&&state.hasCategory()&&state.canLevelUp(SkillCatalog.levelSkillCount(state))&&ClassicDeveloperTimeline.hit(x,y,66,82,48.363636,15.476364)){cover=new Cover("level",null);return true;}
        if(console==null){double dx=ClassicDeveloperTimeline.parallax(mx,width),dy=ClassicDeveloperTimeline.parallax(my,height);
            for(var node:visibleNodes())if(ClassicDeveloperTimeline.hit(x,y,128+node.x()-dx,18+node.y()-dy,16,16)){cover=new Cover("skill",node);return true;}}
        return true;
    }
    private void startCover(){
        Cover current=cover;var state=AcademyClient.state;current.buttonDisposed=true;
        if(developing()){current.message=local("dev_developing");return;}
        if(energy()<estimated(current)){current.message=local("noenergy");return;}
        if(current.node!=null){
            if(!LEARNABLE.contains(current.node.id()))return;
            if(state.level<current.node.level()){current.message=local("level_fail",current.node.level());return;}
            var skill=SkillCatalog.find(current.node.category(),current.node.id()).orElseThrow();
            if(!developerType.supportsSkill(skill.level())||!SkillCatalog.canLearn(state,skill)){current.message=local("condition_fail");return;}
        }
        current.requested=true;current.received=false;current.message=local("dev_developing");sentAt=now();previousDevelopment=AcademyClient.development==null?null:AcademyClient.development.copy();
        request(current.node==null?"develop_level":"learn",current.node==null?"":current.node.id());
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_ESCAPE){
            // Canonical DeveloperUI intercepts Escape only for its nested wireless link page.
            if(cover!=null&&cover.kind.equals("wireless")){if(!cover.ending){cover.ending=true;cover.changed=now();}return true;}
            onClose();return true;
        }
        if(cover!=null)return true;
        if(console!=null){
            if(key==GLFW.GLFW_KEY_BACKSPACE){console.backspace();return true;}
            if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){if(console.ready())runCommand(console.submit());return true;}
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean charTyped(char ch,int modifiers){if(console!=null&&cover==null){console.type(ch);return true;}return super.charTyped(ch,modifiers);}
    private void runCommand(String command){
        if(skillTreeApp){console.output(local("console.invalid_command")+"\n");return;}
        String action=consoleMode.equals("reset")?"reset":"learn";
        if(!command.equals(action)){console.output(local("console.invalid_command")+"\n");return;}
        if(developing()){console.output(local("dev_developing")+"\n");return;}
        if(action.equals("reset")){
            if(developerType!=DeveloperType.ADVANCED){console.output(local("console.reset_fail_dev")+"\n");return;}
            boolean factor=minecraft.player!=null&&minecraft.player.getInventory().items.stream().anyMatch(stack->InductionFactors.category(stack).filter(cat->!cat.equals(AcademyClient.state.category)).isPresent());
            if(AcademyClient.state.level<3||!factor||!resetMode()){console.output(local("console.reset_fail_other")+"\n");return;}
        }
        double consumption=developerType.estimatedConsumption(action.equals("reset")?AcademyClient.state.level*10:5*(AcademyClient.state.level+1));
        if(energy()<consumption){console.output(local("noenergy")+"\n");return;}
        console.output(local("console."+(action.equals("reset")?"reset_begin":"dev_begin"))+local("console.progress","00"));
        console.busy(true);sentAt=now();sentAction=action.equals("reset")?"reset":"level";lastStatus="waiting";
        previousDevelopment=AcademyClient.development==null?null:AcademyClient.development.copy();request(action.equals("reset")?"develop_reset":"develop_level","");
    }
    private void separated(ClassicHudCanvas canvas,String text,double x,double y,double wrap,double size,ClassicHudTimeline.Rgba color){
        int row=0;for(String line:ClassicDeveloperText.wrap(text,wrap,value->classicFont.width(value,size,false)))text(canvas,line,x,y+(row++)*size,size,color,false,1);
    }
    private void text(ClassicHudCanvas canvas,String text,double x,double y,double size,ClassicHudTimeline.Rgba color,boolean bold,int alignment){
        double textWidth=alignment==0?0:classicFont.width(text,size,bold);classicFont.draw(canvas,text,x-(alignment==1?textWidth/2:alignment==2?textWidth:0),y,size,color,bold,false);
    }
    private static String local(String key,Object...args){return Component.translatable("ac.skill_tree."+key,args).getString().replace("\\n","\n");}
    private static String translated(String key){return Component.translatable(key).getString();}
    private static String levelName(int level){return Component.translatable("ac.ability.level"+level).getString();}
    private static ResourceLocation tex(String path){return ClassicHudCanvas.texture(path);}
    private static ResourceLocation resource(String path){return ResourceLocation.parse(path);}
    private static ClassicHudTimeline.Rgba rgba(double r,double g,double b,double a){return new ClassicHudTimeline.Rgba(r,g,b,a);}
    private static long now(){return System.nanoTime()/1_000_000;}
}
