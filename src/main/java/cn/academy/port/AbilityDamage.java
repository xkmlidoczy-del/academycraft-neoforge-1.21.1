package cn.academy.port;
import cn.academy.port.api.SkillAttackEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
public final class AbilityDamage {
    private AbilityDamage() {}
    public static boolean attack(ServerPlayer player,String skill,Entity target,double rawDamage) {return attack(player,skill,target,rawDamage,false);}
    public static boolean attack(ServerPlayer player,String skill,Entity target,double rawDamage,boolean ignoreArmor) {
        var event=new SkillAttackEvent(player,skill,target,rawDamage);NeoForge.EVENT_BUS.post(event);double global=AcademyConfig.DAMAGE_SCALE.get(),local=1;int separator=skill.indexOf('.');if(separator>0)local=AcademyConfig.consumptionConfig().optionalFloat(skill.substring(0,separator),skill.substring(separator+1),"damage_scale");double amount=global==1&&local==1?event.amount:(float)global*(float)local*(float)event.amount;
        if(!Double.isFinite(amount)||amount<=0)return false;
        if(target instanceof Player&&!AcademyConfig.ATTACK_PLAYERS.get())return false;
        if(target instanceof net.minecraft.world.entity.decoration.HangingEntity&&!AcademyConfig.contextTerrain(target.level(),skill))return false;
        return target.hurt(new SkillDamageSource(player,skill,ignoreArmor),(float)amount);
    }
    public static final class SkillDamageSource extends DamageSource {
        public final String skill;
        private final boolean ignoreArmor;
        public SkillDamageSource(ServerPlayer player,String skill,boolean ignoreArmor) {super(player.damageSources().playerAttack(player).typeHolder(),player);this.skill=skill;this.ignoreArmor=ignoreArmor;}
        @Override public boolean is(TagKey<DamageType> tag) {return ignoreArmor&&tag.equals(DamageTypeTags.BYPASSES_ARMOR)||super.is(tag);}
        @Override public Component getLocalizedDeathMessage(LivingEntity target) {return Component.translatable("death.attack.ac_skill",target.getDisplayName(),getEntity().getDisplayName(),Component.translatable("ac.ability."+skill+".name"));}
    }
}
