/* AcademyCraft1.0.7 StormWingContext adaptation, GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.core.AbilityProgress;
public final class StormWingSession {
 public static final String ID="storm_wing";public static final int LEVEL=3,CHARGE=0,ACTIVE=1;public static final float EXPERIENCE=.00005F;
 private final AbilityProgress state;private final float cp,overload,speed,charge;private boolean live=true;private int phase,ticks;
 private StormWingSession(AbilityProgress s){state=s;double e=s.exp(ID);cp=VectorCombatRules.stormCp(e);overload=VectorCombatRules.stormOverload(e);speed=VectorCombatRules.stormSpeed(e);charge=VectorCombatRules.stormCharge(e);}
 public static StormWingSession begin(AbilityProgress s){return VectorCombatRules.eligible(s,ID,LEVEL)?new StormWingSession(s):null;}
 /** Source order consumes while already active, then changes charge state; transition tick is free. */
 public boolean tick(boolean creative){if(!live)return false;if(phase==ACTIVE){state.addExperience(ID,EXPERIENCE);if(!state.consumeSkill(ID,cp,overload,creative)){live=false;return false;}}if(ticks<Integer.MAX_VALUE)ticks++;if(phase==CHARGE&&ticks>charge){phase=ACTIVE;ticks=0;}return true;}
 public void terminate(boolean gameplay){if(gameplay)state.setCooldown(ID,VectorCombatRules.stormCooldown(state.exp(ID)));live=false;}
 public boolean active(){return live;}public int phase(){return phase;}public int ticks(){return ticks;}public AbilityProgress state(){return state;}public float cp(){return cp;}public float overload(){return overload;}public float speed(){return speed;}public float charge(){return charge;}public boolean softBreak(){return(float)state.exp(ID)<.15F;}public boolean masteryBurst(){return(float)state.exp(ID)==1F;}
}
